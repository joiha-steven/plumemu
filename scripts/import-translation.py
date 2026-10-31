#!/usr/bin/env python3
"""Brings a corrected translation from the public repository back into the app.

    python3 -I scripts/import-translation.py ~/GitHub/plumemu/translations/vi/strings.xml
    python3 -I scripts/import-translation.py FILE --lang vi     a file saved anywhere else

The language is the name of the file's folder (translations/<lang>/), the suffix of the app's
`app/src/main/res/values-<lang>/` folder; scripts/export-translations.py wrote it that way. English
is a language like the others (owner, 2026-10-26): `en` maps to `res/values/`. The file comes from
a stranger's issue or pull request, so it is checked before it is used: UTF-8, at most 2 MB, no
DOCTYPE or entity declarations, a <resources> holding only <string>, <plurals> and <string-array>.
It is then normalised (LF line ends, Unicode NFC, one final newline) and checked by
scripts/guards/translations.py; when the guard fails, the app's file is put back as it was.

  - A translation is written over the app's file as it is, comments included.
  - English is merged instead: the public copy lacks the translatable="false" strings and their
    comments, so only the changed elements are replaced in `res/values/strings.xml`; its keys must
    be exactly the master's translatable ones. The guard then checks every translation against
    the new English (a changed placeholder fails all of them). The changed keys are printed:
    every translation of them needs a look.

Refused: a language the app does not have yet (docs/conventions/translation.md › Adding a
language). Nothing is committed. Run `scripts/verify.sh --quick` before the commit, as for any
change.
Exit 0 imported, 1 refused or the guard failed, 2 usage error.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
import unicodedata
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
GUARD = os.path.join(ROOT, "scripts", "guards", "translations.py")
MAX_BYTES = 2 * 1024 * 1024
LANGUAGE = re.compile(r"^[a-z]{2,3}(?:-r(?:[A-Z]{2}|[0-9]{3}))?$")
ALLOWED_ELEMENTS = {"string", "plurals", "string-array"}


class ImportRefused(Exception):
    pass


def checked_text(path):
    if os.path.getsize(path) > MAX_BYTES:
        raise ImportRefused(f"{path} is over {MAX_BYTES // (1024 * 1024)} MB")
    with open(path, "rb") as f:
        raw = f.read()
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError as e:
        raise ImportRefused(f"{path} is not UTF-8 ({e})") from None
    if re.search(r"<!(?:DOCTYPE|ENTITY)", text, re.IGNORECASE):
        raise ImportRefused(f"{path} declares a DOCTYPE or an entity; strings.xml never does")
    text = unicodedata.normalize("NFC", text.replace("\r\n", "\n").replace("\r", "\n"))
    text = text.rstrip("\n") + "\n"
    try:
        root = ET.fromstring(text.encode("utf-8"))
    except ET.ParseError as e:
        raise ImportRefused(f"{path} is not valid XML: {e}") from None
    if root.tag != "resources":
        raise ImportRefused(f"{path}: the root is <{root.tag}>, not <resources>")
    others = sorted({child.tag for child in root if child.tag not in ALLOWED_ELEMENTS})
    if others:
        raise ImportRefused(f"{path}: <resources> holds {', '.join(others)}; only strings belong there")
    return text


def texts(text):
    """name -> the element's whole text, nested tags and plural items included."""
    return {e.get("name"): ET.tostring(e, encoding="unicode").strip() for e in ET.fromstring(text.encode("utf-8"))}


def element_source(text, name):
    """The exact source of the element called name: its opening tag to its closing tag."""
    match = re.search(
        r'<(string|plurals|string-array)\b[^>]*\bname\s*=\s*"' + re.escape(name) + r'"[^>]*?(?:/>|>.*?</\1>)',
        text,
        re.DOTALL,
    )
    return match


def english_merged(master, public):
    """The master with each element whose text the public English copy changed replaced by it."""
    master_root = ET.fromstring(master.encode("utf-8"))
    translatable = [e.get("name") for e in master_root if e.get("translatable") != "false"]
    public_names = [e.get("name") for e in ET.fromstring(public.encode("utf-8"))]
    if sorted(public_names) != sorted(translatable):
        missing = sorted(set(translatable) - set(public_names))
        extra = sorted(set(public_names) - set(translatable))
        raise ImportRefused(
            "the English keys differ from the app's (a fix changes text only): "
            + "; ".join(filter(None, [
                "missing " + ", ".join(missing[:10]) if missing else "",
                "not expected " + ", ".join(extra[:10]) if extra else "",
            ]))
        )
    old, new = texts(master), texts(public)
    merged = master
    for name in translatable:
        if old[name] == new[name]:
            continue
        here, there = element_source(merged, name), element_source(public, name)
        if not here or not there:
            raise ImportRefused(f"cannot find <{name}> as one element in both files")
        merged = merged[:here.start()] + there.group(0) + merged[here.end():]
    return merged


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("file", help="the corrected strings.xml")
    parser.add_argument("--lang", help="language folder suffix (default: the file's folder name)")
    args = parser.parse_args()
    source = os.path.abspath(os.path.expanduser(args.file))
    lang = args.lang or os.path.basename(os.path.dirname(source))
    if not os.path.isfile(source):
        print(f"import-translation: no such file: {source}", file=sys.stderr)
        sys.exit(2)
    if not LANGUAGE.match(lang):
        print(f"import-translation: \"{lang}\" is not a language folder suffix; pass --lang", file=sys.stderr)
        sys.exit(2)
    folder = "values" if lang == "en" else f"values-{lang}"
    target = os.path.join(RES, folder, "strings.xml")
    shown = os.path.relpath(target, ROOT)

    try:
        if not os.path.isfile(target):
            raise ImportRefused(
                f"the app has no {shown}; a new language is added by hand "
                "(docs/conventions/translation.md › Adding a language)"
            )
        text = checked_text(source)
        with open(target, encoding="utf-8") as f:
            before = f.read()
        if lang == "en":
            text = english_merged(before, text)
    except ImportRefused as e:
        print(f"import-translation: refused: {e}", file=sys.stderr)
        sys.exit(1)

    if text == before:
        print(f"import-translation: {shown} is already the same; nothing to do")
        return
    with open(target, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)

    # New English text is checked against every translation, a translation against English.
    guard = subprocess.run([sys.executable, "-I", GUARD, RES if lang == "en" else target], cwd=ROOT)
    if guard.returncode != 0:
        with open(target, "w", encoding="utf-8", newline="\n") as f:
            f.write(before)
        print(f"import-translation: FAIL: the translations guard refused it; {shown} is back as it was",
              file=sys.stderr)
        sys.exit(1)

    old, new = texts(before), texts(text)
    changed = sorted(name for name in new if old.get(name) != new[name])
    for name in changed:
        print(f"  changed: {name}")
    if not changed:
        print("  no string's text changed (comments, order or spacing only)")
    if lang == "en" and changed:
        print("  English changed: look at these keys in every values-<lang>/strings.xml too.")
    print(f"import-translation: {shown} updated, {len(changed)} strings changed. "
          "Run scripts/verify.sh --quick before the commit.")

if __name__ == "__main__":
    main()
