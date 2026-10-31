#!/usr/bin/env python3
"""Every translation has exactly the English strings, with the same placeholders.

For each `res/values-<qualifiers>/strings.xml` next to a `res/values/strings.xml` (the English),
fails on:
- a string or plural English has and the translation lacks (missing key);
- a key the translation has and English lacks (extra key, often a renamed string left behind);
- a key English marks `translatable="false"` (it must not be copied into a translation);
- text not in Unicode NFC (a letter plus a combining mark, as some editors save Vietnamese):
  Plum Sans, Serif and Mono have every precomposed Vietnamese letter but not all the combining
  marks, so such text falls back to another font letter by letter;
- a string whose placeholders (`%1$s`, `%2$d`, a bare `%s`) differ from English's;
- a `<string>` translated as a `<plurals>` or the other way round;
- a plural without an `other` item, an item of an unknown quantity, an item using a placeholder
  English's `other` lacks, or an `other` whose placeholders differ from English's `other`.
A language may need fewer or more quantities than English (Japanese has only `other`, so its
`one` drops the number); that is why only `other` must match exactly.

docs/conventions/translation.md explains how translators work with it.

Usage: translations.py [FOLDER ...]   (no folder: the whole repository)
"""

import os
import re
import sys
import unicodedata
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from guardlib import Reporter, die, files_from_args, line_of, read_text  # noqa: E402

NAME = "translations"
# A language folder only: values-xx, values-xxx, either with -rYY or -r419, or BCP 47's
# values-b+sr+Latn. Other qualifiers (values-night, -v31, -land, -sw600dp) hold no translation,
# and "car" and "hdr" are a UI mode and a colour mode, not languages.
TRANSLATION = re.compile(
    r"(^|/)res/values-"
    r"(?:(?!(?:car|hdr)/)[a-z]{2,3}(?:-r(?:[A-Z]{2}|[0-9]{3}))?|b\+[A-Za-z]{2,3}(?:\+[A-Za-z0-9]+)*)"
    r"/strings\.xml$"
)
# %% is a literal percent; %n is a line break: neither takes an argument.
PLACEHOLDER = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")
QUANTITIES = {"zero", "one", "two", "few", "many", "other"}


def placeholders(text):
    found = []
    for m in PLACEHOLDER.finditer(text or ""):
        if m.group(2) in ("%", "n"):
            continue
        found.append(f"%{m.group(1) + '$' if m.group(1) else ''}{m.group(2)}")
    return sorted(found)


def text_of(element):
    return "".join(element.itertext())


def read_resources(full_path, shown):
    """{name: (kind, value, attrs, line)}: value is a str for a string, {quantity: str} for a plural."""
    text = read_text(full_path)
    try:
        root = ET.fromstring(text)
    except ET.ParseError as e:
        die(NAME, f"{shown}: not valid XML ({e})")
    lines = {}
    for m in re.finditer(r'<(string|plurals)\s[^>]*?name="([^"]+)"', text):
        lines.setdefault(m.group(2), line_of(text, m.start()))
    out = {}
    for el in root:
        if el.tag not in ("string", "plurals") or "name" not in el.attrib:
            continue
        name = el.attrib["name"]
        if el.tag == "string":
            value = text_of(el)
        else:
            value = {}
            for item in el.findall("item"):
                value[item.attrib.get("quantity", "")] = text_of(item)
        out[name] = (el.tag, value, el.attrib, lines.get(name, 0))
    return out


def check(report, english, translated, shown):
    for name, (kind, value, attrs, line) in translated.items():
        if name not in english:
            report.fail(shown, line, f"'{name}' is not in English's strings.xml (renamed or removed there?)")
            continue
        texts = [value] if kind == "string" else list(value.values())
        if any(unicodedata.normalize("NFC", t) != t for t in texts):
            report.fail(shown, line, f"'{name}' is not in NFC: save it with precomposed letters")
        e_kind, e_value, e_attrs, _ = english[name]
        if e_attrs.get("translatable") == "false":
            report.fail(shown, line, f"'{name}' is translatable=\"false\" in English: delete it here")
            continue
        if kind != e_kind:
            report.fail(shown, line, f"'{name}' is a <{kind}> here but a <{e_kind}> in English")
            continue
        if kind == "string":
            if placeholders(value) != placeholders(e_value):
                report.fail(shown, line, f"'{name}' has placeholders {placeholders(value)}, English has {placeholders(e_value)}")
            continue
        english_other = placeholders(e_value.get("other", ""))
        if "other" not in value:
            report.fail(shown, line, f"plural '{name}' has no quantity=\"other\" item")
        for quantity, item in sorted(value.items()):
            if quantity not in QUANTITIES:
                report.fail(shown, line, f"plural '{name}' has an unknown quantity \"{quantity}\"")
            elif quantity == "other" and placeholders(item) != english_other:
                report.fail(shown, line, f"plural '{name}' other has placeholders {placeholders(item)}, English's other has {english_other}")
            elif not set(placeholders(item)) <= set(english_other):
                report.fail(shown, line, f"plural '{name}' {quantity} uses {placeholders(item)}, English's other has only {english_other}")
    first_line = 1
    for name, (_, _, attrs, _) in english.items():
        if attrs.get("translatable") != "false" and name not in translated:
            report.fail(shown, first_line, f"'{name}' is missing (English has it)")


def main():
    report = Reporter(NAME)
    try:
        files = files_from_args(sys.argv[1:])
    except FileNotFoundError as e:
        die(NAME, f"no such file or folder: {e}")
    english_cache = {}
    for full, shown in files:
        if not TRANSLATION.search(shown):
            continue
        english_path = os.path.join(os.path.dirname(os.path.dirname(full)), "values", "strings.xml")
        if not os.path.isfile(english_path):
            report.fail(shown, 0, "no values/strings.xml beside this translation")
            continue
        if english_path not in english_cache:
            english_cache[english_path] = read_resources(english_path, english_path)
        check(report, english_cache[english_path], read_resources(full, shown), shown)
    report.finish()


if __name__ == "__main__":
    main()
