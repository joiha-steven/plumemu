#!/usr/bin/env python3
"""File and function length (docs/conventions/code.md).

Kotlin files: fail over 600 lines, warn over 400 (main and test sources).
Functions with a block body: fail over 100 lines, warn over 60 (main sources only; long test
functions are usually tables of cases). A function is measured from its `fun` line to the line
of its closing brace, found by matching braces outside comments and strings.
"""

import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "code-length"

FILE_FAIL, FILE_WARN = 600, 400
FUNCTION_FAIL, FUNCTION_WARN = 100, 60

FUN = re.compile(r"\bfun\b(?!\s+interface\b)")
# Declarations that end a body-less `fun` (abstract or in an interface) before any `{` is seen.
DECLARATION_END = re.compile(r"\b(?:fun|val|var|class|object|interface|init)\b")


def body_start(bare, start):
    """Index of the `{` opening the body of the function declared at `start`, or None."""
    depth = 0
    i = start + 3
    n = len(bare)
    while i < n:
        ch = bare[i]
        if ch in "(<[":
            depth += 1
        elif ch in ")>]":
            depth = max(depth - 1, 0)
        elif depth == 0:
            if ch == "{":
                return i
            if ch == "}" or (ch == "=" and bare[i + 1:i + 2] not in ("=", ">")):
                return None  # end of an enclosing block, or an expression body
            if DECLARATION_END.match(bare, i) and not bare[i - 1].isalnum():
                return None
        if ch == "-" and bare[i + 1:i + 2] == ">":
            i += 2  # `->` in a function type is not a closing angle bracket
            continue
        i += 1
    return None


def body_end(bare, opening):
    depth = 0
    for i in range(opening, len(bare)):
        if bare[i] == "{":
            depth += 1
        elif bare[i] == "}":
            depth -= 1
            if depth == 0:
                return i
    return None


def function_name(bare, start):
    m = re.match(r"fun\s*(?:<[^>]*>\s*)?([\w.`]+)", bare[start:start + 200])
    return m.group(1) if m else "(anonymous)"


def main(argv):
    report = guardlib.Reporter(NAME)
    for full, rel in guardlib.files_from_args(argv):
        if not rel.endswith(".kt"):
            continue
        text = guardlib.read_text(full)
        lines = text.count("\n") + (0 if text.endswith("\n") or not text else 1)
        if lines > FILE_FAIL:
            report.fail(rel, 1, f"file has {lines} lines (limit {FILE_FAIL}): split it")
        elif lines > FILE_WARN:
            report.warn(rel, 1, f"file has {lines} lines (split at {FILE_WARN}, fails at {FILE_FAIL})")
        if guardlib.is_test_source(rel):
            continue
        _, bare = guardlib.strip_kotlin(text)
        for match in FUN.finditer(bare):
            opening = body_start(bare, match.start())
            closing = body_end(bare, opening) if opening is not None else None
            if closing is None:
                continue
            first = guardlib.line_of(bare, match.start())
            length = guardlib.line_of(bare, closing) - first + 1
            name = function_name(bare, match.start())
            if length > FUNCTION_FAIL:
                report.fail(rel, first, f"function {name} has {length} lines (limit {FUNCTION_FAIL})")
            elif length > FUNCTION_WARN:
                report.warn(rel, first, f"function {name} has {length} lines "
                                        f"(split at {FUNCTION_WARN}, fails at {FUNCTION_FAIL})")
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
