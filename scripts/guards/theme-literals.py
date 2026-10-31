#!/usr/bin/env python3
"""No colour or size literals outside the theme (docs/conventions/ui.md).

In Kotlin main sources outside core-ui's theme package, fails on:
- colours: `Color(0x...)`, `Color(<number>...)` (negative too), any 8-digit hex literal such as
  `0xFF0E0F11` (so `val brand = 0xFF0E0F11; Color(brand)` is caught too), named colours such as
  `Color.Red` (`Color.Transparent` and `Color.Unspecified` are fine), `android.graphics.Color`
  constants other than `TRANSPARENT`, its `rgb`/`argb` with literals and `parseColor`, and a
  string that is only a colour (`"#RRGGBB"`, `"#AARRGGBB"`);
- sizes: `<number>.dp` (`0.dp` is fine), `<number>.sp`, `<number>.em`, `(<number>).dp`,
  `Dp(<number>)` and `TextUnit(<number>, ...)`, named `value =` included.
One report per line. Test sources are exempt.
"""

import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "theme-literals"

THEME_DIR = re.compile(r"(^|/)core-ui/src/main/java/com/plumemu/coreui/theme/")

# Matched against the source with comments and string contents blanked.
NUMBER = r"[-+]?\s*(?:0[xX][0-9A-Fa-f_]+|[0-9][0-9_]*(?:\.[0-9]+)?|\.[0-9]+)[fFuUL]*"

CODE_PATTERNS = [
    (re.compile(r"\bColor\(\s*(?:[a-z]+\s*=\s*)?" + NUMBER + r"[^)\n]*\)?"), "colour literal"),
    (re.compile(r"(?<![\w.])0[xX][0-9A-Fa-f](?:_?[0-9A-Fa-f]){7}(?![0-9A-Fa-f_])[uUL]*"), "colour literal"),
    (re.compile(r"\bColor\.(?!Transparent\b|Unspecified\b)([A-Z][a-z][A-Za-z]*)\b"), "named colour"),
    (re.compile(r"\bColor\.(?!TRANSPARENT\b)([A-Z][A-Z_]+)\b"), "android.graphics.Color constant"),
    (re.compile(r"\bColor\.(?:rgb|argb)\(\s*" + NUMBER + r"[^)\n]*\)?"), "colour literal"),
    (re.compile(r"\bColor\.parseColor\("), "colour parsed from a string"),
    (re.compile(r"(?<![\w.])(?!0\.dp\b)\d+(?:\.\d+)?[fF]?\.(?:dp|sp|em)\b"), "size literal"),
    (re.compile(r"\(\s*" + NUMBER + r"\s*\)\.(?:dp|sp|em)\b"), "size literal"),
    (re.compile(r"\b(?:Dp|TextUnit)\(\s*(?:value\s*=\s*)?" + NUMBER), "size literal"),
]

# Matched against the source with only comments blanked: a string that is nothing but a colour.
STRING_PATTERNS = [
    (re.compile(r"\"#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6})\""), "colour string"),
]


def main(argv):
    report = guardlib.Reporter(NAME)
    for full, rel in guardlib.files_from_args(argv):
        if not guardlib.is_kotlin_main_source(rel) or THEME_DIR.search(rel):
            continue
        code, bare = guardlib.strip_kotlin(guardlib.read_text(full))
        lines = code.splitlines()
        found = {}  # line -> first finding; one report per line keeps the output readable
        for text, patterns in ((bare, CODE_PATTERNS), (code, STRING_PATTERNS)):
            for pattern, kind in patterns:
                for match in pattern.finditer(text):
                    number = guardlib.line_of(code, match.start())
                    found.setdefault(number, f"{kind} '{' '.join(match.group(0).split())}'")
        for number in sorted(found):
            report.fail(rel, number, f"{found[number]} outside the theme: use a core-ui token  "
                                     f"| {lines[number - 1].strip()}")
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
