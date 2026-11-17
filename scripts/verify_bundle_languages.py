#!/usr/bin/env python3
"""Check picker language resources in a bundle-generated base/master APK.

Run against an English-targeted APK set's base/master APK, not a universal APK.
This checks packaging; it does not certify Play delivery or runtime UI behavior.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--aapt2", required=True, type=Path)
    parser.add_argument("--locales", type=Path, default=Path("app/src/main/res/xml/locales_config.xml"))
    args = parser.parse_args()
    expected = {
        node.attrib["{http://schemas.android.com/apk/res/android}name"]
        for node in ET.parse(args.locales).getroot()
    }
    aliases = {"iw": "he", "in": "id", "ji": "yi"}
    probes = {"title_wallpapers", "title_backup", "title_app_language"}
    found = {key: set() for key in probes}
    output = subprocess.check_output(
        [str(args.aapt2), "dump", "resources", str(args.apk)], text=True
    )
    current = None
    for line in output.splitlines():
        resource = re.match(r"\s*resource \S+ string/(\S+)", line)
        if resource:
            current = resource.group(1)
        elif re.match(r"\s*resource ", line):
            current = None
        elif current in found:
            configuration = re.match(r"\s*\(([^)]*)\) ", line)
            if configuration:
                qualifier = configuration.group(1)
                # English is the application's default resource language.
                if not qualifier:
                    found[current].add("en")
                else:
                    locale = re.match(r"([a-z]{2,3})(?:-r([A-Z]{2}))?(?:-|$)", qualifier)
                    if locale:
                        language = aliases.get(locale.group(1), locale.group(1))
                        found[current].add(language + ("-" + locale.group(2) if locale.group(2) else ""))
    missing = {key: sorted(expected - values) for key, values in found.items()}
    passed = all(not values for values in missing.values())
    print(json.dumps({
        "passed": passed,
        "apk": str(args.apk),
        "sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        "declared_languages": sorted(expected),
        "missing_locales_by_app_resource": missing,
        "boundary": "App-specific resource probes in a bundle-generated base/master APK; not runtime or Play attestation",
    }, indent=2))
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
