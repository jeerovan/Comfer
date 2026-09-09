#!/usr/bin/env python3
"""Run notification tests on an emulator or an isolated physical-device install."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("serial", help="Explicit ADB device serial")
parser.add_argument("--physical", action="store_true", help="Use the separate notificationtest app; preserve the installed launcher")
parser.add_argument("--skip-build", action="store_true")
args = parser.parse_args()
sdk = Path(os.environ.get("ANDROID_SDK_ROOT", os.environ.get("ANDROID_HOME", "/Volumes/JS/Android/sdk")))
adb = [str(sdk / "platform-tools/adb"), "-s", args.serial]

def run(command):
    return subprocess.check_output(command, cwd=ROOT, text=True, stderr=subprocess.STDOUT)

emulator = run(adb + ["shell", "getprop", "ro.kernel.qemu"]).strip() == "1"
api = int(run(adb + ["shell", "getprop", "ro.build.version.sdk"]).strip())
if not emulator and not args.physical:
    sys.exit("Physical devices require --physical, which uses an isolated test application ID.")
if args.physical and api < 27:
    sys.exit("Physical-device tests require API 27+ for package-scoped grant restoration.")
variant = "notificationTest" if args.physical else "debug"
task_variant = "NotificationTest" if args.physical else "Debug"
package = "com.jeerovan.comfer.notificationtest" if args.physical else "com.jeerovan.comfer"
reports = ROOT / "validation-artifacts/notifications" / args.serial
reports.mkdir(parents=True, exist_ok=True)
if not args.skip_build:
    with (reports / "build.txt").open("w") as log:
        subprocess.run([str(ROOT / "gradlew"), f"-PcomferTestBuildType={variant}",
                        f":app:test{task_variant}UnitTest", f":app:assemble{task_variant}",
                        f":app:assemble{task_variant}AndroidTest", ":notification-fixtures:assembleMailDebug",
                        ":notification-fixtures:assembleChatDebug", f":app:lint{task_variant}"],
                       cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
for apk in [f"app/build/outputs/apk/{variant}/app-{variant}.apk",
            f"app/build/outputs/apk/androidTest/{variant}/app-{variant}-androidTest.apk",
            "notification-fixtures/build/outputs/apk/mail/debug/notification-fixtures-mail-debug.apk",
            "notification-fixtures/build/outputs/apk/chat/debug/notification-fixtures-chat-debug.apk"]:
    run(adb + ["install", "-r", str(ROOT / apk)])
if api >= 33:
    for fixture_package in ["com.jeerovan.fixtures.mail", "com.jeerovan.fixtures.chat"]:
        run(adb + ["shell", "pm", "grant", fixture_package, "android.permission.POST_NOTIFICATIONS"])
classes = ",".join("com.jeerovan.comfer." + name for name in
                   ["NotificationListenerIntegrationTest", "NotificationInboxLayoutTest", "NotificationInboxRotationTest", "NotificationNavigationGestureTest", "NotificationSwipeAnimationTest", "NotificationHistoryCipherTest"])
command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "timeout_msec", "90000", "-e", "class", classes,
                 f"{package}.test/androidx.test.runner.AndroidJUnitRunner"]
lines = []
with (reports / "instrumentation.txt").open("w", buffering=1) as log:
    with subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True) as process:
        for line in process.stdout:
            lines.append(line)
            log.write(line)
            if line.startswith("INSTRUMENTATION_STATUS: test="):
                print(line.strip(), flush=True)
        exit_code = process.wait()
output = "".join(lines)
if exit_code:
    sys.exit(f"Instrumentation exited {exit_code}; see {reports / 'instrumentation.txt'}")
if not re.search(r"OK \(\d+ tests?\)", output) or "FAILURES!!!" in output:
    sys.exit(f"Notification tests failed; see {reports / 'instrumentation.txt'}")
run(adb + ["pull", f"/sdcard/Android/data/{package}/files/notification-portrait.png",
           str(reports / "portrait.png")])
result = re.search(r"OK \(\d+ tests?\)", output).group(0)
print(f"API {api}: {result}. Reports: {reports}")
