#!/usr/bin/env python3
"""Verify an installed Comfer APK and cold-start it for a manual device check.

Run after an in-place APK update. Does not install, uninstall, or clear app data.
Requires adb on PATH and an explicit device serial. Test app data is retained.
"""

import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


PACKAGE = "com.jeerovan.comfer"
TEST_PACKAGES = (PACKAGE + ".notificationtest", PACKAGE + ".notificationtest.test", PACKAGE + ".test")


def read(adb, *arguments):
    return subprocess.check_output(adb + list(arguments), text=True,
                                   stderr=subprocess.STDOUT, timeout=20).strip()


def verify_apk(adb, apk):
    paths = read(adb, "shell", "pm", "path", PACKAGE).splitlines()
    base = [line.removeprefix("package:") for line in paths if line.endswith("/base.apk")]
    if len(base) != 1:
        raise RuntimeError("The regular Comfer installation could not be identified")
    installed = read(adb, "shell", "sha256sum", base[0]).split()[0]
    expected = hashlib.sha256(apk.read_bytes()).hexdigest()
    if installed != expected:
        raise RuntimeError("Installed Comfer differs from the supplied APK; install the intended build in place first")
    return expected


def verify_ui(xml, description=None):
    root = ET.fromstring(xml)

    def visible(node):
        bounds = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
        return len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]

    def matches(node, clickable_parent=False):
        belongs = node.get("package") == PACKAGE
        clickable = belongs and node.get("enabled") == "true" and node.get("clickable") == "true"
        expected = (node.get("content-desc") == description or node.get("text") == description) if description else True
        if belongs and expected and visible(node) and (clickable or clickable_parent):
            return True
        return any(matches(child, clickable_parent or clickable) for child in node)

    if not matches(root):
        raise RuntimeError(f"The regular app's visible, interactive control was not found: {description or 'any control'}")


def verify_foreground(dump):
    lines = dump.splitlines()
    home = re.compile(re.escape(PACKAGE) + r"/(?:\.|" + re.escape(PACKAGE) + r"\.)MainActivity\b")
    resumed = next((line for line in lines
                    if ("mResumedActivity" in line or "topResumedActivity" in line)
                    and home.search(line)), None)
    if resumed is None:
        raise RuntimeError("The regular Comfer app is not foregrounded; check for a crash or launcher overlay")

    # Samsung can report Home as resumed while its task exposes a stale Recents
    # or Settings window. Check the resumed activity's record, not other tasks.
    record = re.search(r"ActivityRecord\{(\S+)", resumed)
    if record:
        for index, line in enumerate(lines):
            if "* Hist #" not in line or "ActivityRecord{" + record[1] + " " not in line:
                continue
            indent = len(line) - len(line.lstrip())
            for detail in lines[index + 1:]:
                if detail.strip() and len(detail) - len(detail.lstrip()) <= indent:
                    break
                if re.search(r"\b(?:occludesParent|mOccludesParent)=false\b", detail):
                    raise RuntimeError("Comfer Home exposes other apps through a corrupted task; recreate its Home task")
            break


def prepare(adb, apk, description):
    installed = [line.removeprefix("package:") for line in read(adb, "shell", "pm", "list", "packages").splitlines()]
    variants = sorted(package for package in installed if package == PACKAGE or package.startswith(PACKAGE + "."))
    for package in variants:
        dump = read(adb, "shell", "dumpsys", "package", package)
        version = re.search(r"^\s*versionName=(.*)$", dump, re.MULTILINE)
        print(f"Installed: {package} ({version[1] if version else 'test runner'})", flush=True)

    # Reject a stale/wrong APK before changing any running app state.
    digest = verify_apk(adb, apk)
    for package in TEST_PACKAGES:
        if package in installed:
            read(adb, "shell", "am", "force-stop", package)
    # Stopping the default launcher can immediately relaunch it. Recreate the
    # Home task as well, so a successful start cannot reuse that broken task.
    launch = read(adb, "shell", "am", "start", "-S", "-W", "-f", "0x10008000",
                  "-a", "android.intent.action.MAIN",
                  "-c", "android.intent.category.HOME", "-n", PACKAGE + "/.MainActivity")
    if "Status: ok" not in launch:
        raise RuntimeError("Comfer did not launch successfully: " + launch)

    remote = f"/sdcard/comfer-manual-check-{time.monotonic_ns()}.xml"
    try:
        read(adb, "shell", "uiautomator", "dump", remote)
        verify_ui(read(adb, "exec-out", "cat", remote), description)
        verify_foreground(read(adb, "shell", "dumpsys", "activity", "activities"))
        if not read(adb, "shell", "pidof", PACKAGE):
            raise RuntimeError("Comfer exited after launch")
    finally:
        read(adb, "shell", "rm", "-f", remote)
    print(f"Ready: regular Comfer, installed APK SHA-256 {digest}")
    print("Cold launch and visible interactive UI verified; app data retained.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--control-description", help="Localized accessibility label of the control to check")
    args = parser.parse_args()
    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")
    try:
        prepare(["adb", "-s", args.serial], args.apk, args.control_description)
    except (RuntimeError, subprocess.SubprocessError, ET.ParseError) as error:
        print(f"Device check failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
