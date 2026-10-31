#!/usr/bin/env python3
"""Regex patterns Android's engine refuses, in Kotlin main sources.

Android compiles `Regex` and `Pattern` with ICU, which is stricter than the JVM: an unescaped `}`
outside a character class is a syntax error there, while the JVM reads it as a literal, so every
JVM test passes and the app fails on the device. `TitleCleaner`'s `...|\\{([^{}]*)}` made every
scan fail on Android 6 and on API 37 (fixed in aecc40d,
docs/testing/2026-10-22-identification-speed.md).

What is read as a pattern:
- the first argument of `Regex(...)` (also `Regex(pattern = ...)`) and `Pattern.compile(...)`:
  every string literal of it, joined by `+` (`Regex(\"\"\"a\"\"\" + \"\"\"b\"\"\")`), and every
  name in it that is a string `val` / `const val` of the same file (`Regex(DOMAIN)`);
- a literal or such a name before `.toRegex()` / `.toPattern()`;
- inside those, `$NAME` and `${NAME}` take the value of that `val` of the same file (`$MARKER`),
  so a constant is checked where it is put into a pattern; any other template counts as a letter.
A violation is reported at the line of the literal that holds the bracket, the constant's own
line when it came from one.

Fails on a `}` that is not escaped, not inside a character class and not closing a quantifier.
An unescaped `]` there is a warning only: Android 6 and the API 37 image accept it (DiscMarkers
and scraper's TitleMatch use it and load on both, StaticInitialisersTest), but `\\]` says what
is meant. Passes:
- `\\]`, `\\}` (escaped) and anything inside `[...]` (nested classes too), `\\Q...\\E`;
- quantifiers `{n}`, `{n,}`, `{n,m}`;
- the braces of `\\p{..}`, `\\P{..}`, `\\x{..}`, `\\N{..}`.

Known limits, all rare in this code base:
- false alarm, on the safe side: a `]` first in a class (`[]}]`) closes the class here, so the `}`
  after it is reported; write `[\\]}]`;
- not read: `Pattern.matches(...)`, `String.matches(...)`, `split(Regex)` built elsewhere, the
  pattern given by name after other named arguments (`Regex(option = X, pattern = p)`),
  constants of another file, and patterns built at run time. The instrumented
  `StaticInitialisersTest` (library androidTest) is the second line of defence for those.
Test sources are exempt.
"""

import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "android-regex"

# Matched against the source with comments and string contents blanked.
CALL = re.compile(r"\b(?:Regex|Pattern\.compile)\s*\(\s*(?:pattern\s*=\s*)?")
LITERAL_CONVERSION = re.compile(r"\"\s*\.\s*(?:toRegex|toPattern)\s*\(")
NAME_CONVERSION = re.compile(r"\b([A-Za-z_]\w*)\s*\.\s*(?:toRegex|toPattern)\s*\(")
STRING_VAL = re.compile(r"\bval\s+([A-Za-z_]\w*)\s*(?::\s*String\s*)?=\s*(?=\")")
IDENTIFIER = re.compile(r"[A-Za-z_]\w*")
TEMPLATE = re.compile(r"\$(?:\{\s*([A-Za-z_]\w*)\s*\}|\{([^}]*)\}|([A-Za-z_]\w*))")
QUANTIFIER = re.compile(r"\{\d+(?:,\d*)?\}")
BRACED_ESCAPES = "pPxN"
ESCAPES = {"n": "\n", "t": "\t", "r": "\r", "b": "\b", "\\": "\\", '"': '"', "'": "'", "$": "$"}
MAX_DEPTH = 8  # constants referring to constants


class Source:
    """One Kotlin file: its text, the stripped copies and its string constants."""

    def __init__(self, code, bare):
        self.code = code
        self.bare = bare
        self.constants = {}  # name -> opening offset of its expression
        for m in STRING_VAL.finditer(bare):
            self.constants[m.group(1)] = m.end()

    def line(self, offset):
        return guardlib.line_of(self.code, offset)

    def literal(self, start):
        """(text, raw, end) of the string literal whose opening quote is at `start`."""
        bare = self.bare
        if bare.startswith('"""', start):
            close = bare.find('"""', start + 3)
            close = len(bare) if close < 0 else close
            end = close + 3
            while end < len(bare) and bare[end] == '"':
                end += 1
            return self.code[start + 3:close], True, end
        close = bare.find('"', start + 1)
        close = len(bare) if close < 0 else close
        return self.code[start + 1:close], False, close + 1

    def literal_value(self, start, depth):
        """[(char, line)] of the literal at `start`, escapes read and templates filled in."""
        text, raw, _ = self.literal(start)
        first_line = self.line(start)
        out = []
        newlines = 0
        i = 0
        while i < len(text):
            ch = text[i]
            if ch == "$":
                m = TEMPLATE.match(text, i)
                if m:
                    name = m.group(1) or m.group(3)
                    if m.group(2) is not None and m.group(2).strip() == "'$'":
                        out.append(("$", first_line + newlines))
                    elif name in self.constants and depth < MAX_DEPTH:
                        out += self.expression_value(self.constants[name], depth + 1)
                    else:
                        out.append(("x", first_line + newlines))
                    i = m.end()
                    continue
            if ch == "\\" and not raw and i + 1 < len(text):
                nxt = text[i + 1]
                if nxt == "u" and re.match(r"[0-9a-fA-F]{4}", text[i + 2:i + 6]):
                    out.append((chr(int(text[i + 2:i + 6], 16)), first_line + newlines))
                    i += 6
                    continue
                out.append((ESCAPES.get(nxt, nxt), first_line + newlines))
                i += 2
                continue
            out.append((ch, first_line + newlines))
            if ch == "\n":
                newlines += 1
            i += 1
        return out

    def expression_value(self, start, depth, stop_at_comma=False):
        """[(char, line)] of the string expression from `start`: literals and known names joined
        by `+`, up to the end of the expression (or a top-level `,` / `)` for an argument)."""
        bare = self.bare
        out = []
        i = start
        level = 0
        expect_operand = True
        while i < len(bare):
            ch = bare[i]
            if ch.isspace():
                i += 1
            elif ch == '"':
                if not expect_operand and not stop_at_comma:
                    break
                out += self.literal_value(i, depth)
                i = self.literal(i)[2]
                expect_operand = False
            elif ch == "+":
                expect_operand = True
                i += 1
            elif ch in "([{":
                if not stop_at_comma:
                    break
                level += 1
                i += 1
            elif ch in ")]}" or ch == ",":
                if level == 0 or not stop_at_comma:
                    break
                if ch != ",":
                    level -= 1
                i += 1
            else:
                m = IDENTIFIER.match(bare, i)
                if not m:
                    if not stop_at_comma:
                        break
                    i += 1
                    continue
                if not expect_operand and not stop_at_comma:
                    break
                name = m.group(0)
                if name in self.constants and depth < MAX_DEPTH and level == 0:
                    out += self.expression_value(self.constants[name], depth + 1)
                elif out or stop_at_comma:
                    out.append(("x", self.line(i)))
                i = m.end()
                expect_operand = False
        return out


def stray_brackets(pattern):
    """Indices of each `]` or `}` that ICU may refuse: not escaped, outside a class, not a quantifier's."""
    found = []
    depth = 0  # character class nesting
    i = 0
    while i < len(pattern):
        ch = pattern[i]
        if ch == "\\":
            nxt = pattern[i + 1:i + 2]
            if nxt == "Q":
                end = pattern.find("\\E", i + 2)
                i = len(pattern) if end < 0 else end + 2
                continue
            if depth == 0 and nxt and nxt in BRACED_ESCAPES and pattern[i + 2:i + 3] == "{":
                end = pattern.find("}", i + 3)
                i = len(pattern) if end < 0 else end + 1
                continue
            i += 2
            continue
        if ch == "[":
            depth += 1
        elif depth and ch == "]":
            depth -= 1
        elif depth == 0 and ch == "{":
            m = QUANTIFIER.match(pattern, i)
            if m:
                i = m.end()
                continue
        elif depth == 0 and ch in "]}":
            found.append(i)
        i += 1
    return found


def patterns(source):
    """[(char, line)] of every pattern in the file (see the module doc)."""
    bare = source.bare
    for m in CALL.finditer(bare):
        yield source.expression_value(m.end(), 0, stop_at_comma=True)
    for m in LITERAL_CONVERSION.finditer(bare):
        closing = m.start()
        start = bare.rfind('"""', 0, closing - 2) if bare.startswith('"""', closing - 2) else bare.rfind('"', 0, closing)
        if start >= 0:
            yield source.literal_value(start, 0)
    for m in NAME_CONVERSION.finditer(bare):
        if m.group(1) in source.constants:
            yield source.expression_value(source.constants[m.group(1)], 1)


def check(reporter, path, code, bare):
    source = Source(code, bare)
    failed, warned = set(), set()
    for chars in patterns(source):
        text = "".join(ch for ch, _ in chars)
        for index in stray_brackets(text):
            ch, line = chars[index]
            if ch == "}" and line not in failed:
                failed.add(line)
                reporter.fail(path, line, "regex has an unescaped '}' outside a character class; Android's ICU "
                                          "refuses it (the JVM does not): write \\}")
            elif ch == "]" and line not in warned:
                warned.add(line)
    for line in sorted(warned - failed):
        reporter.warn(path, line, "regex has an unescaped ']' outside a character class; ICU accepts it "
                                  "today, but write \\] to say what is meant")


def main(args):
    reporter = guardlib.Reporter(NAME)
    try:
        files = guardlib.files_from_args(args)
    except FileNotFoundError as missing:
        guardlib.die(NAME, f"no such file or folder: {missing}")
    for full, shown in files:
        if not guardlib.is_kotlin_main_source(shown):
            continue
        code, bare = guardlib.strip_kotlin(guardlib.read_text(full))
        check(reporter, shown, code, bare)
    reporter.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
