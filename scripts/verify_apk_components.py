#!/usr/bin/env python3
"""Verify that every manifest application component has a DEX definition."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET


ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
COMPONENT_TAGS = ("activity", "service", "receiver", "provider")


def resolve_apkanalyzer(explicit: str | None) -> Path:
    if explicit:
        return Path(explicit)
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if android_home:
        candidate = Path(android_home) / "cmdline-tools/latest/bin/apkanalyzer"
        if candidate.is_file():
            return candidate
    raise SystemExit("Pass --apkanalyzer or set ANDROID_HOME/ANDROID_SDK_ROOT")


def run(apkanalyzer: Path, *arguments: str) -> str:
    result = subprocess.run(
        [str(apkanalyzer), *arguments],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout


def qualify(package_name: str, class_name: str) -> str:
    if class_name.startswith("."):
        return package_name + class_name
    if "." not in class_name:
        return f"{package_name}.{class_name}"
    return class_name


def manifest_components(manifest_text: str) -> set[str]:
    root = ET.fromstring(manifest_text)
    package_name = root.attrib["package"]
    application = root.find("application")
    if application is None:
        return set()

    names: set[str] = set()
    application_name = application.attrib.get(ANDROID_NS + "name")
    if application_name:
        names.add(qualify(package_name, application_name))
    for tag in COMPONENT_TAGS:
        for component in application.findall(tag):
            class_name = component.attrib.get(ANDROID_NS + "name")
            if class_name:
                names.add(qualify(package_name, class_name))
    return names


def dex_classes(package_text: str) -> set[str]:
    classes: set[str] = set()
    for line in package_text.splitlines():
        if line.startswith("C "):
            classes.add(line.rsplit(maxsplit=1)[-1])
    return classes


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--apkanalyzer")
    args = parser.parse_args()

    if not args.apk.is_file():
        parser.error(f"APK does not exist: {args.apk}")
    apkanalyzer = resolve_apkanalyzer(args.apkanalyzer)
    components = manifest_components(run(apkanalyzer, "manifest", "print", str(args.apk)))
    classes = dex_classes(
        run(apkanalyzer, "dex", "packages", "--defined-only", str(args.apk))
    )
    missing = sorted(components - classes)
    if missing:
        print("Missing manifest component classes:", file=sys.stderr)
        for class_name in missing:
            print(f"- {class_name}", file=sys.stderr)
        return 1

    print(f"Verified {len(components)} manifest component classes in {args.apk}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
