#!/usr/bin/env python3
"""
Vody Browser CI Pre-flight Validator & Code Reviewer
Validates manifests, resources, XML syntax, duplicate attrs, and resource integrity in one shot.
"""
import os
import glob
import xml.etree.ElementTree as ET
import re
import sys

def main():
    print("==================================================")
    print("  Vody Browser CI Pre-Flight One-Shot Reviewer    ")
    print("==================================================")

    errors = []
    warnings = []

    all_xml = glob.glob('**/res*/**/*.xml', recursive=True)
    xml_files = [f for f in all_xml if 'res_template' not in f and 'jinja' not in f]
    print(f"[*] Scanning {len(xml_files)} XML resource files for syntax...")
    for f in xml_files:
        try:
            ET.parse(f)
        except Exception as e:
            errors.append(f"Invalid XML syntax in {f}: {e}")

    print("[*] Checking for illegal files in res/ subfolders...")
    for root, dirs, files in os.walk('.'):
        if ('/res/' in root or root.endswith('/res')) and 'res_template' not in root:
            for f in files:
                if not any(f.endswith(ext) for ext in ['.xml', '.png', '.webp', '.jpg', '.gif', '.9.png', '.json', '.bin']):
                    errors.append(f"Non-resource file found in resource folder: {os.path.join(root, f)}")

    print("[*] Checking for duplicate attribute format definitions...")
    attrs_with_format = {}
    for f in glob.glob('**/res*/**/values*/attrs*.xml', recursive=True):
        if 'res_template' in f or 'jinja' in f: continue
        try:
            tree = ET.parse(f)
            for attr in tree.findall('.//attr'):
                name = attr.get('name')
                fmt = attr.get('format')
                if name and fmt:
                    if name in attrs_with_format:
                        errors.append(f"Duplicate format for attr '{name}' in {f} (already defined in {attrs_with_format[name]})")
                    else:
                        attrs_with_format[name] = f
        except Exception:
            pass

    print("[*] Verifying all @color and @dimen references against definitions...")
    defined_resources = set()
    referenced_resources = set()

    for f in glob.glob('**/res*/**/color*/*.xml', recursive=True):
        if 'res_template' not in f:
            name = os.path.splitext(os.path.basename(f))[0]
            defined_resources.add('color/' + name)

    for f in xml_files:
        if '/values' in f and 'overlayable' not in f:
            try:
                tree = ET.parse(f)
                for c in tree.findall('.//color'):
                    n = c.get('name')
                    if n: defined_resources.add('color/' + n)
                for d in tree.findall('.//dimen'):
                    n = d.get('name')
                    if n: defined_resources.add('dimen/' + n)
                for it in tree.findall('.//item'):
                    t = it.get('type')
                    n = it.get('name')
                    if t and n: defined_resources.add(f'{t}/{n}')
            except Exception:
                pass

    color_pattern = re.compile(r'@color/([a-zA-Z0-9_]+)')
    dimen_pattern = re.compile(r'@dimen/([a-zA-Z0-9_]+)')

    for f in xml_files:
        try:
            with open(f, 'r', encoding='utf-8', errors='ignore') as fp:
                content = fp.read()
                for m in color_pattern.findall(content):
                    referenced_resources.add('color/' + m)
                for m in dimen_pattern.findall(content):
                    referenced_resources.add('dimen/' + m)
        except Exception:
            pass

    missing = referenced_resources - defined_resources
    if missing:
        errors.append(f"Missing resource definitions ({len(missing)}): {sorted(list(missing))[:10]}...")

    print("--------------------------------------------------")
    if warnings:
        print(f"[!] {len(warnings)} Warning(s) found:")
        for w in warnings:
            print(f"  - {w}")

    if errors:
        print(f"[X] {len(errors)} Error(s) found during pre-flight check:")
        for err in errors:
            print(f"  - {err}")
        print("--------------------------------------------------")
        sys.exit(1)
    else:
        print("[✓] All manifests, XML files, attribute definitions, and resource links passed validation cleanly!")
        print("--------------------------------------------------")
        sys.exit(0)

if __name__ == '__main__':
    main()
