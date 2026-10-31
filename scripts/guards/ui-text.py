#!/usr/bin/env python3
"""No hard-coded UI text in Kotlin main sources (docs/conventions/i18n.md).

Fails on a string literal with letters
- anywhere in the arguments of `Text(...)`, `BasicText(...)`, `ClickableText(...)`,
  `showSnackbar(...)` or `Toast.makeText(...)`, over several lines too, so
  `Text(if (a) "Yes" else "No")` and `Text(stringResource(...) + " (beta)")` are caught;
  strings passed to `Modifier.testTag(...)` or `Log.x(...)` inside them are not visible and pass;
- passed as a named argument `text`, `contentDescription`, `stateDescription`, `paneTitle`,
  `onClickLabel` or `onLongClickLabel` (`= "..."`, all read aloud by TalkBack) to any call
  (a declaration such as `val text = "..."` is not UI text and passes).
Every word a person sees comes from strings.xml. Strings without letters (empty, a separator,
only a `$template`) pass. One report per line. Test sources are exempt.
"""

import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "ui-text"

# Matched against the source with comments and string contents blanked, so only real code counts.
CALL = re.compile(r"\b(?:Text|BasicText|ClickableText|showSnackbar|Toast\.makeText)\s*\(")
NAMED_ARGUMENT = re.compile(r"\b(?:text|contentDescription|stateDescription|paneTitle|onClickLabel|onLongClickLabel)\s*=\s*(?=\")")
DECLARATION_BEFORE = re.compile(r"\b(?:val|var)\s+$")
# Calls inside a text call whose string arguments are never shown.
NOT_VISIBLE = re.compile(r"(?:^|\.)(?:testTag|key)$|^Log\.[a-z]+$")

TEMPLATE = re.compile(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*")


def closing_paren(bare, opening):
    depth = 0
    for i in range(opening, len(bare)):
        if bare[i] == "(":
            depth += 1
        elif bare[i] == ")":
            depth -= 1
            if depth == 0:
                return i
    return len(bare)


def literals(code, bare, start, end):
    """(offset, text) of each string literal opening between start and end."""
    i = start
    while i < end:
        if bare[i] != '"':
            i += 1
            continue
        if bare.startswith('"""', i):
            close = bare.find('"""', i + 3)
            close = len(bare) if close < 0 else close
            yield i, code[i + 3:close]
            i = close + 3
        else:
            close = bare.find('"', i + 1)
            close = len(bare) if close < 0 else close
            yield i, code[i + 1:close]
            i = close + 1


def enclosing_callee(bare, start, offset):
    """Name of the innermost call around `offset` that opened after `start`, e.g. `Modifier.testTag`."""
    depth = 0
    for i in range(offset - 1, start - 1, -1):
        if bare[i] == ")":
            depth += 1
        elif bare[i] == "(":
            if depth == 0:
                m = re.search(r"([\w.]+)\s*$", bare[:i])
                return m.group(1) if m else ""
            depth -= 1
    return ""


def has_words(literal):
    return any(ch.isalpha() for ch in TEMPLATE.sub("", literal))


def main(argv):
    report = guardlib.Reporter(NAME)
    for full, rel in guardlib.files_from_args(argv):
        if not guardlib.is_kotlin_main_source(rel):
            continue
        code, bare = guardlib.strip_kotlin(guardlib.read_text(full))
        found = {}  # line -> literal; one report per line
        for call in CALL.finditer(bare):
            opening = call.end() - 1
            for offset, literal in literals(code, bare, opening, closing_paren(bare, opening)):
                if has_words(literal) and not NOT_VISIBLE.search(enclosing_callee(bare, opening, offset)):
                    found.setdefault(guardlib.line_of(code, offset), literal)
        for match in NAMED_ARGUMENT.finditer(bare):
            if DECLARATION_BEFORE.search(bare[max(0, match.start() - 40):match.start()]):
                continue
            for offset, literal in literals(code, bare, match.end(), match.end() + 1):
                if has_words(literal):
                    found.setdefault(guardlib.line_of(code, offset), literal)
        for number in sorted(found):
            literal = " ".join(found[number].split())
            shown = literal if len(literal) <= 40 else literal[:37] + "..."
            report.fail(rel, number, f'hard-coded text "{shown}": use stringResource(R.string....)')
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
