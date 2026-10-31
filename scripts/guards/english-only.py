#!/usr/bin/env python3
"""English only (RULES R24): no letters outside basic Latin, except in translation folders.

Text files may use basic ASCII plus the typographic punctuation and symbols in ALLOWED_CHARS.
Anything else (Vietnamese diacritics, CJK, Cyrillic, emoji, ...) fails with its Unicode name.
Skipped: `res/values-<lang>/` translation folders, the public repository's `translations/<lang>/`
copies of them, binaries and fonts.
"""

import re
import sys
import unicodedata

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "english-only"

# Punctuation and symbols the documents use. Add one here only for typography, never letters.
ALLOWED_CHARS = set(
    "\u00a0"  # no-break space
    "©«»·×°±"
    "–—‘’“”…‹›•−"
    "←↑→↓↔⇒≈≠≤≥"
    "─│┌┐└┘├┤┬┴┼"
    "►◄▲▼✓"
    "✕"  # PlayStation's Cross button, named in the confirm option (RULES R35)
)

# Proper names spelled with letters outside basic Latin; each stays exactly as written.
ALLOWED_WORDS = ("Daijishō",)

SKIP = re.compile(r"(^|/)res/values-[^/]+/|^translations/[^/]+/")


def check_line(report, path, number, line):
    for word in ALLOWED_WORDS:
        line = line.replace(word, "")
    for column, ch in enumerate(line, start=1):
        if ord(ch) > 127 and ch not in ALLOWED_CHARS:
            name = unicodedata.name(ch, "unnamed")
            report.fail(path, number, f"col {column}: U+{ord(ch):04X} '{ch}' {name}")
            return  # one report per line is enough to find it


def main(argv):
    report = guardlib.Reporter(NAME)
    for full, rel in guardlib.files_from_args(argv):
        if SKIP.search(rel):
            continue
        if guardlib.is_binary(full):
            continue
        try:
            text = guardlib.read_text(full)
        except UnicodeDecodeError:
            report.fail(rel, 0, "not valid UTF-8 (if it is binary, add its extension to "
                                "BINARY_EXTENSIONS in guardlib.py)")
            continue
        for number, line in enumerate(text.splitlines(), start=1):
            check_line(report, rel, number, line)
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
