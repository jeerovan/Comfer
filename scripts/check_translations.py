#!/usr/bin/env python3
"""Check shipped Android locale coverage and Java-format arguments (stdlib only)."""
from pathlib import Path
import argparse
import collections
import re
import sys
import xml.etree.ElementTree as ET

FORMAT = re.compile(r'%(?:(\d+)\$)?[-#+ 0,(<]*\d*(?:\.\d+)?([a-zA-Z%])')
ANDROID = '{http://schemas.android.com/apk/res/android}'
LOCALE_ALIASES = {'iw': 'he', 'in': 'id', 'ji': 'yi'}

def arguments(text):
    result = collections.Counter()
    implicit = 0
    for match in FORMAT.finditer(text):
        index, kind = match.groups()
        if kind in ('%', 'n'):
            continue
        if index is None:
            implicit += 1
            index = str(implicit)
        result[(index, kind)] += 1
    return result

def strings(directory):
    values, errors = {}, []
    for path in sorted(directory.glob('*.xml')):
        try:
            nodes = ET.parse(path).getroot()
        except ET.ParseError as exc:
            errors.append(f'{path}: {exc}')
            continue
        for node in nodes:
            if node.tag not in ('string', 'plurals', 'string-array'):
                continue
            key = node.get('name')
            if key in values:
                errors.append(f'{directory.name}: duplicate {key}')
            values[key] = node
    return values, errors

def check(root):
    base, errors = strings(root / 'values')
    required = {k: v for k, v in base.items() if v.get('translatable') != 'false'}
    directories = sorted(d for d in root.glob('values-*') if (d / 'strings.xml').exists())
    declared = {n.get(ANDROID + 'name') for n in ET.parse(root / 'xml/locales_config.xml').getroot()}
    folder_locales = {d.name.removeprefix('values-').replace('-r', '-') for d in directories}
    shipped = {'en'} | {LOCALE_ALIASES.get(locale, locale) for locale in folder_locales}
    for legacy, modern in LOCALE_ALIASES.items():
        if modern in folder_locales:
            errors.append(f'Use values-{legacy} for Android resource lookup, with {modern} in the language picker')
    if declared != shipped:
        errors.append(f'Locale picker mismatch: missing={sorted(shipped-declared)}, extra={sorted(declared-shipped)}')
    for directory in directories:
        local, local_errors = strings(directory)
        errors.extend(local_errors)
        for key, node in required.items():
            if key not in local:
                errors.append(f'{directory.name}: missing {key}')
                continue
            translated = local[key]
            text = ''.join(translated.itertext()).strip()
            if not text:
                errors.append(f'{directory.name}: empty {key}')
            if node.tag != translated.tag:
                errors.append(f'{directory.name}: wrong resource type {key}')
            if node.tag == 'string' and arguments(''.join(node.itertext())) != arguments(text):
                errors.append(f'{directory.name}: format arguments differ for {key}')
            if re.search(r'ZXQ\s*\d+|<2[a-z-]+>|\[TRANSLATE|<0x[0-9a-f]+>|&\s*#\s*160\s*;|@\s*info\b', text, re.IGNORECASE):
                errors.append(f'{directory.name}: untranslated marker in {key}')
    return errors, len(directories), len(required)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--res', type=Path, default=Path(__file__).resolve().parents[1] / 'app/src/main/res')
    args = parser.parse_args()
    errors, locales, count = check(args.res)
    for error in errors:
        print(error)
    print(f'{"FAIL" if errors else "PASS"}: {locales} translated locales, {count} required resources, {len(errors)} errors')
    return bool(errors)

if __name__ == '__main__':
    sys.exit(main())
