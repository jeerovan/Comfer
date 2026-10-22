from pathlib import Path
import hashlib
import sys
import tempfile
import unittest
from unittest.mock import patch


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_manual_check as checker


class ManualCheckTests(unittest.TestCase):
    def test_wrong_apk_is_rejected_before_stopping_apps(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "build.apk"
            apk.write_bytes(b"new build")
            with patch.object(checker, "read", side_effect=[
                "package:com.jeerovan.comfer", "versionName=56.0",
                "package:/data/app/comfer/base.apk", "0" * 64 + " base.apk",
            ]) as read:
                with self.assertRaisesRegex(RuntimeError, "differs"):
                    checker.prepare(["adb", "-s", "device"], apk, "Workspace")
                self.assertFalse(any("force-stop" in call.args for call in read.call_args_list))

    def test_matching_apk_is_verified_by_content(self):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "build.apk"
            apk.write_bytes(b"actual build")
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            with patch.object(checker, "read", side_effect=[
                "package:/data/app/comfer/base.apk", digest + " base.apk",
            ]):
                self.assertEqual(digest, checker.verify_apk(["adb"], apk))

    def test_cold_launch_root_is_retried_before_reading_ui(self):
        self.check_cold_launch_root(recovers=True)

    def test_unavailable_root_fails_after_bounded_retries(self):
        self.check_cold_launch_root(recovers=False)

    def check_cold_launch_root(self, recovers):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "build.apk"
            apk.write_bytes(b"actual build")
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            dumps = 0
            ui_reads = 0

            def device_read(adb, *args):
                nonlocal dumps, ui_reads
                if args == ("shell", "pm", "list", "packages"):
                    return "package:com.jeerovan.comfer"
                if args[:3] == ("shell", "dumpsys", "package"):
                    return "versionName=57.0"
                if args[:3] == ("shell", "pm", "path"):
                    return "package:/data/app/comfer/base.apk"
                if args[:2] == ("shell", "sha256sum"):
                    return digest + " base.apk"
                if args[:3] == ("shell", "am", "start"):
                    return "Status: ok"
                if args[:3] == ("shell", "uiautomator", "dump"):
                    dumps += 1
                    return ("UI hierchary dumped to: " + args[3] if recovers and dumps > 1
                            else "ERROR: null root node returned by UiTestAutomationBridge.")
                if args[:2] == ("exec-out", "cat"):
                    ui_reads += 1
                    self.assertTrue(recovers and dumps > 1, "No XML exists after a null-root response")
                    return '''<hierarchy><node package="com.jeerovan.comfer"
                        content-desc="Workspace" clickable="true" enabled="true"
                        bounds="[0,0][100,100]" /></hierarchy>'''
                if args[:3] == ("shell", "dumpsys", "activity"):
                    return "mResumedActivity: com.jeerovan.comfer/.MainActivity"
                if args[:2] == ("shell", "pidof"):
                    return "1234"
                if args[:3] == ("shell", "rm", "-f"):
                    return ""
                self.fail(f"Unexpected device call: {args}")

            with patch.object(checker, "read", side_effect=device_read) as read, \
                    patch.object(checker.time, "sleep"):
                if recovers:
                    checker.prepare(["adb"], apk, "Workspace")
                    self.assertEqual((dumps, ui_reads), (2, 1))
                else:
                    with self.assertRaisesRegex(RuntimeError, "null root node"):
                        checker.prepare(["adb"], apk, "Workspace")
                    self.assertEqual((dumps, ui_reads), (3, 0))
                self.assertTrue(any(call.args[1:4] == ("shell", "rm", "-f")
                                    for call in read.call_args_list))

    def test_icon_description_can_belong_to_clickable_parent(self):
        checker.verify_ui('''<hierarchy><node package="com.jeerovan.comfer"
            clickable="true" enabled="true" bounds="[0,0][100,100]">
            <node package="com.jeerovan.comfer" content-desc="Workspace"
                clickable="false" bounds="[10,10][90,90]" />
            </node></hierarchy>''', "Workspace")

    def test_other_launcher_or_noninteractive_snapshot_is_rejected(self):
        for package, clickable in (("com.sec.android.app.launcher", "true"),
                                   ("com.jeerovan.comfer", "false")):
            with self.subTest(package=package, clickable=clickable):
                with self.assertRaisesRegex(RuntimeError, "not found"):
                    checker.verify_ui(f'''<hierarchy><node package="{package}"
                        content-desc="Workspace" clickable="{clickable}"
                        enabled="true" bounds="[0,0][100,100]" /></hierarchy>''', "Workspace")

    def test_test_build_or_recents_cannot_count_as_regular_foreground(self):
        for activity in ("com.jeerovan.comfer.notificationtest/.MainActivity",
                         "com.sec.android.app.launcher/.RecentsActivity"):
            with self.assertRaisesRegex(RuntimeError, "not foregrounded"):
                checker.verify_foreground("mResumedActivity: " + activity)
        checker.verify_foreground("mResumedActivity: com.jeerovan.comfer/.MainActivity")

    def test_foreground_home_that_exposes_other_apps_is_rejected(self):
        dump = """    mResumedActivity: ActivityRecord{abc u0 com.jeerovan.comfer/.MainActivity t1}
      * Hist #0: ActivityRecord{abc u0 com.jeerovan.comfer/.MainActivity t1}
          state=RESUMED stopped=false
          occludesParent=false noDisplay=false
          mOccludesParent=false mOrientation=-1
          mVisibleRequested=true mVisible=true
  Stack #2: type=recents
"""
        with self.assertRaisesRegex(RuntimeError, "exposes other apps"):
            checker.verify_foreground(dump)

    def test_healthy_home_is_not_confused_with_another_transparent_activity(self):
        dump = """    mResumedActivity: ActivityRecord{abc u0 com.jeerovan.comfer/.MainActivity t1}
      * Hist #0: ActivityRecord{abc u0 com.jeerovan.comfer/.MainActivity t1}
          occludesParent=true noDisplay=false
          mOccludesParent=true mOrientation=-1
  Stack #2: type=standard
      * Hist #0: ActivityRecord{def u0 com.android.settings/.Settings t2}
          occludesParent=false noDisplay=false
          mOccludesParent=false mOrientation=-1
"""
        checker.verify_foreground(dump)

    def test_settings_activity_does_not_count_as_home(self):
        with self.assertRaisesRegex(RuntimeError, "not foregrounded"):
            checker.verify_foreground("mResumedActivity: com.jeerovan.comfer/.SettingsActivity")


if __name__ == "__main__":
    unittest.main()
