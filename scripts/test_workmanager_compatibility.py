#!/usr/bin/env python3
"""Compare real and missing JobScheduler reflection in a disposable, isolated build.

Uses the existing notificationTest variant (no production Firebase/signing).
Never mutates production source or installs over com.jeerovan.comfer.
Requires adb, JAVA_HOME and local.properties; pass an API-34+ emulator serial.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
from datetime import datetime, timezone


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    assert not output.exists() or not any(output.iterdir()), "Use a new evidence directory"
    output.mkdir(parents=True, exist_ok=True)
    package = "com.jeerovan.comfer.notificationtest"
    test_package = package + ".test"
    adb = ["adb", "-s", args.serial]

    def read(*command):
        return subprocess.check_output(command, text=True, timeout=30).strip()

    assert read(*adb, "shell", "getprop", "ro.kernel.qemu") == "1", "Use an emulator"
    assert int(read(*adb, "shell", "getprop", "ro.build.version.sdk")) >= 34
    for name in [package, test_package]:
        assert not read(*adb, "shell", "pm", "list", "packages", name), f"Already installed: {name}"
    metadata = {"serial": args.serial, "started_utc": datetime.now(timezone.utc).isoformat(),
                "fingerprint": read(*adb, "shell", "getprop", "ro.build.fingerprint"), "phases": {}}
    (output / "run.json").write_text(json.dumps(metadata, indent=2) + "\n")

    with tempfile.TemporaryDirectory(prefix="comfer-namespace-") as temporary:
        checkout = Path(temporary)
        paths = subprocess.check_output(
            ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"], cwd=repo
        ).decode().split("\0")
        for relative in filter(None, paths):
            source = repo / relative
            if source.is_file():
                target = checkout / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, target)
        # SDK discovery only; ignored signing, Firebase and reporting files are not copied.
        shutil.copy2(repo / "local.properties", checkout / "local.properties")
        app_source = checkout / "app/src/main/java/com/jeerovan/comfer/ComferApp.kt"
        original = app_source.read_text()
        needle = 'JobScheduler::class.java.getMethod("forNamespace", String::class.java)'
        assert original.count(needle) == 1, "Reflection preflight changed; review harness"

        def run(log, command, timeout=600):
            with (output / log).open("w") as stream:
                result = subprocess.run(command, cwd=checkout, stdout=stream,
                                        stderr=subprocess.STDOUT, timeout=timeout)
            if result.returncode:
                raise RuntimeError(f"Command failed ({result.returncode}); see {output / log}")

        for mode in ["healthy", "missing"]:
            print(f"Building and testing {mode} namespace lookup", flush=True)
            app_source.write_text(original if mode == "healthy" else original.replace(
                needle, needle.replace('"forNamespace"', '"comferMissingNamespaceFixture"')))
            run(f"{mode}-build.txt", ["./gradlew", ":app:assembleNotificationTest",
                ":app:assembleNotificationTestAndroidTest", "-PcomferTestBuildType=notificationTest"])
            apk = checkout / "app/build/outputs/apk/notificationTest/app-notificationTest.apk"
            tests = checkout / "app/build/outputs/apk/androidTest/notificationTest/app-notificationTest-androidTest.apk"
            phase = {"app_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                     "test_sha256": hashlib.sha256(tests.read_bytes()).hexdigest(),
                     "reflection_method": "forNamespace" if mode == "healthy" else "comferMissingNamespaceFixture"}
            metadata["phases"][mode] = phase
            try:
                run(f"{mode}-install.txt", [*adb, "install", str(apk)])
                run(f"{mode}-install-test.txt", [*adb, "install", str(tests)])
                run(f"{mode}-instrumentation.txt", [*adb, "shell", "am", "instrument", "-w", "-r",
                    "-e", "namespaceMode", mode, "-e", "class",
                    "com.jeerovan.comfer.WorkManagerCompatibilityTest",
                    test_package + "/androidx.test.runner.AndroidJUnitRunner"], timeout=180)
                result = (output / f"{mode}-instrumentation.txt").read_text()
                phase["passed"] = "OK (1 test)" in result
                # A direct launch also exercises a fresh ordinary app process after instrumentation.
                run(f"{mode}-cold-launch.txt", [*adb, "shell", "am", "start", "-W", "-n",
                    package + "/com.jeerovan.comfer.MainActivity"])
            finally:
                for label, command in {
                    "crash": ["logcat", "-b", "crash", "-d"],
                    "logcat": ["logcat", "-b", "main", "-b", "system", "-d"],
                    "exit-info": ["shell", "dumpsys", "activity", "exit-info", package],
                    "jobscheduler": ["shell", "dumpsys", "jobscheduler"],
                    "window": ["shell", "dumpsys", "window"],
                }.items():
                    run(f"{mode}-{label}.txt", [*adb, *command], timeout=60)
                for name in [test_package, package]:
                    if read(*adb, "shell", "pm", "list", "packages", name):
                        run(f"{mode}-remove-{name}.txt", [*adb, "uninstall", name])
                (output / "run.json").write_text(json.dumps(metadata, indent=2) + "\n")
            assert phase.get("passed"), f"Instrumentation failed; inspect {mode} evidence"
    print(f"Both controlled runs passed. Evidence: {output}", flush=True)


if __name__ == "__main__":
    main()
