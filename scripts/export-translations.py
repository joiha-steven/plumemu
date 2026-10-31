#!/usr/bin/env python3
"""Copies Plumemu's strings into the public repository's `translations/` folder.

People read and correct the translations there without the source being published (owner,
2026-10-26; docs/conventions/translation.md › Public translations):

    python3 -I scripts/export-translations.py                 into ~/GitHub/plumemu/translations
    python3 -I scripts/export-translations.py --to DIR        into DIR (a scratch copy)

What it writes, and nothing else:
  translations/en/strings.xml       app/src/main/res/values/strings.xml, the English master,
                                    without the strings marked translatable="false" (key caps,
                                    separators, font and licence names) and without a comment
                                    that only described those strings;
  translations/<lang>/strings.xml   app/src/main/res/values-<lang>/strings.xml as it is (vi, es,
                                    ja, ko, zh, zh-rTW today); <lang> is the folder's suffix, so
                                    scripts/import-translation.py maps it straight back.

There is no marker for strings of features not yet released, so every translatable string goes
out. A language folder in DIR whose source folder is gone is reported, never deleted. Nothing is
committed or pushed: the owner looks at the public repository's diff first.
Exit 0 written, 1 a check failed (nothing written), 2 usage error.
"""
from __future__ import annotations

import argparse
import os
import re
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
DEFAULT_TO = os.path.expanduser("~/GitHub/plumemu/translations")
# Language folders only, as scripts/guards/translations.py reads them (values-xx, values-xx-rYY).
LANGUAGE_FOLDER = re.compile(r"^values-([a-z]{2,3}(?:-r(?:[A-Z]{2}|[0-9]{3}))?)$")
NOT_LANGUAGES = {"car", "hdr"}
ELEMENT_START = re.compile(r"^\s*<(string|plurals|string-array)\b([^>]*)>")
NOT_TRANSLATABLE = re.compile(r'\btranslatable\s*=\s*"false"')


class ExportError(Exception):
    pass


def units(text):
    """Splits a strings.xml into (kind, lines, name, translatable) units, one per line or element.

    kind is "blank", "comment" (a whole comment, however many lines), "element" (a whole
    <string>, <plurals> or <string-array>) or "other" (the XML header, <resources>).
    """
    lines = text.splitlines(keepends=True)
    result = []
    i = 0
    while i < len(lines):
        line = lines[i]
        stripped = line.strip()
        if not stripped:
            result.append(("blank", [line], None, True))
            i += 1
        elif stripped.startswith("<!--"):
            end = i
            while "-->" not in lines[end]:
                end += 1
                if end == len(lines):
                    raise ExportError(f"line {i + 1}: a comment that never closes")
            result.append(("comment", lines[i:end + 1], None, True))
            i = end + 1
        elif ELEMENT_START.match(line):
            tag, attributes = ELEMENT_START.match(line).groups()
            name = re.search(r'\bname\s*=\s*"([^"]+)"', attributes)
            if not name:
                raise ExportError(f"line {i + 1}: <{tag}> without a name")
            end = i
            while f"</{tag}>" not in lines[end] and not lines[end].rstrip().endswith("/>"):
                end += 1
                if end == len(lines):
                    raise ExportError(f"line {i + 1}: <{tag} name=\"{name.group(1)}\"> never closes")
            translatable = not NOT_TRANSLATABLE.search(attributes)
            result.append(("element", lines[i:end + 1], name.group(1), translatable))
            i = end + 1
        else:
            result.append(("other", [line], None, True))
            i += 1
    return result


def english_for_translators(text):
    """The English master without its translatable="false" strings and their own comments."""
    parsed = ET.fromstring(text.encode("utf-8"))
    expected = {e.get("name") for e in parsed if e.get("translatable") == "false"}
    parts = units(text)
    dropped = {u[2] for u in parts if u[0] == "element" and not u[3]}
    if dropped != expected:
        raise ExportError(
            "the line reader and the XML parser disagree on the translatable=\"false\" strings: "
            + ", ".join(sorted(dropped ^ expected))
        )
    keep = [not (kind == "element" and not translatable) for kind, _, _, translatable in parts]
    # A run of comments describes the elements after it, up to the next comment or blank line.
    # When every one of those is dropped, the comments go too; otherwise they stay.
    i = 0
    while i < len(parts):
        if parts[i][0] != "comment":
            i += 1
            continue
        start = i
        while i < len(parts) and parts[i][0] == "comment":
            i += 1
        described = []
        j = i
        while j < len(parts) and parts[j][0] == "element":
            described.append(j)
            j += 1
        if described and not any(keep[k] for k in described):
            for k in range(start, i):
                keep[k] = False
    out = "".join("".join(lines) for (kind, lines, _, _), kept in zip(parts, keep) if kept)
    # Dropping a whole group leaves two blank lines, or one before </resources>.
    out = re.sub(r"\n(?:[ \t]*\n){2,}", "\n\n", out)
    out = re.sub(r"\n[ \t]*\n(\s*</resources>)", r"\n\1", out)
    left = ET.fromstring(out.encode("utf-8"))
    if any(e.get("translatable") == "false" for e in left):
        raise ExportError("a translatable=\"false\" string is still in the exported English")
    if len(left) != len(parsed) - len(expected):
        raise ExportError(f"expected {len(parsed) - len(expected)} strings, wrote {len(left)}")
    return out


def language_folders():
    found = []
    for folder in sorted(os.listdir(RES)):
        match = LANGUAGE_FOLDER.match(folder)
        if match and match.group(1) not in NOT_LANGUAGES:
            path = os.path.join(RES, folder, "strings.xml")
            if os.path.isfile(path):
                found.append((match.group(1), path))
    return found


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--to", default=DEFAULT_TO, help=f"target folder (default {DEFAULT_TO})")
    args = parser.parse_args()
    target = os.path.abspath(os.path.expanduser(args.to))
    if not os.path.isdir(os.path.dirname(target)):
        print(f"export-translations: {os.path.dirname(target)} does not exist", file=sys.stderr)
        sys.exit(2)

    try:
        files = {"en": english_for_translators(read(os.path.join(RES, "values", "strings.xml")))}
        for lang, path in language_folders():
            text = read(path)
            if NOT_TRANSLATABLE.search(text):
                raise ExportError(f"{os.path.relpath(path, ROOT)} holds a translatable=\"false\" string")
            ET.fromstring(text.encode("utf-8"))
            files[lang] = text
    except (ExportError, ET.ParseError, OSError) as e:
        print(f"export-translations: FAIL: {e}", file=sys.stderr)
        sys.exit(1)

    for lang, text in files.items():
        folder = os.path.join(target, lang)
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, "strings.xml"), "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        count = len(ET.fromstring(text.encode("utf-8")))
        print(f"{os.path.join(lang, 'strings.xml')}: {count} strings")
    for folder in sorted(os.listdir(target)):
        if os.path.isdir(os.path.join(target, folder)) and folder not in files:
            print(f"{folder}/: no such language in the app any more; left as it is")
    print(f"export-translations: wrote {len(files)} files to {target}")


if __name__ == "__main__":
    main()
