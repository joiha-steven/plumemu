"""Shared helpers for the guards in this folder.

Every guard is a small script that can run on its own:

    python3 scripts/guards/<guard>.py            # scan the repository
    python3 scripts/guards/<guard>.py PATH ...   # scan only these files or folders

It prints one line per violation as `path:line: message`, warnings as
`path:line: warning: message`, and ends with `<guard>: PASS` or `<guard>: FAIL (...)`.
Exit code 0 means pass, 1 means at least one violation, 2 means the guard itself could not run.
"""

import os
import re
import subprocess
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

# The self-test fixtures break the rules on purpose; a scan of the whole repository skips them.
FIXTURES_PREFIX = "scripts/guards/tests/fixtures/"

BINARY_EXTENSIONS = {
    ".ttf", ".otf", ".woff", ".woff2", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ico",
    ".jar", ".zip", ".apk", ".aab", ".pdf", ".jks", ".keystore", ".so", ".dex", ".class",
}

# Source sets that hold tests; everything else under src/ is main code.
_TEST_SOURCE_SET = re.compile(r"(^|/)src/(test|androidTest|testFixtures|screenshotTest)[A-Za-z0-9]*/")


class Reporter:
    """Collects violations and warnings and prints them in the shared format."""

    def __init__(self, name):
        self.name = name
        self.failures = 0
        self.warnings = 0

    def fail(self, path, line, message):
        self.failures += 1
        print(f"{path}:{line}: {message}" if line else f"{path}: {message}")

    def warn(self, path, line, message):
        self.warnings += 1
        print(f"{path}:{line}: warning: {message}" if line else f"{path}: warning: {message}")

    def info(self, message):
        print(message)

    def finish(self):
        extra = f", {self.warnings} warning(s)" if self.warnings else ""
        if self.failures:
            print(f"{self.name}: FAIL ({self.failures} violation(s){extra})")
            sys.exit(1)
        print(f"{self.name}: PASS{' (' + extra[2:] + ')' if extra else ''}")
        sys.exit(0)


def die(name, message):
    """The guard cannot do its job (missing tool, unreadable input): exit 2, never pass."""
    print(f"{name}: ERROR: {message}")
    sys.exit(2)


def repo_files(include_fixtures=False):
    """Tracked files plus new files not yet added (ignored files excluded), relative to the root.

    New files count so that a coding agent sees a violation before it forgets to add the file.
    """
    out = subprocess.run(
        ["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
        cwd=REPO_ROOT, capture_output=True, check=True,
    ).stdout.decode("utf-8")
    seen = set()
    files = []
    for path in out.split("\0"):
        if not path or path in seen or (path.startswith(FIXTURES_PREFIX) and not include_fixtures):
            continue
        seen.add(path)
        if os.path.isfile(os.path.join(REPO_ROOT, path)):
            files.append(path)
    return files


def files_from_args(args, include_fixtures=False):
    """Files named on the command line (folders are walked); none means the whole repository.

    Returns a list of (full_path, shown_path): read the first, report the second.
    """
    if not args:
        return [(os.path.join(REPO_ROOT, p), p) for p in repo_files(include_fixtures)]
    files = []
    for arg in args:
        if os.path.isdir(arg):
            for root, dirs, names in os.walk(arg):
                dirs[:] = sorted(d for d in dirs if d not in (".git", "build", ".gradle"))
                for name in sorted(names):
                    path = os.path.join(root, name)
                    # A copy of the repository (the pre-commit snapshot) skips the fixtures too.
                    if include_fixtures or not norm(os.path.relpath(path, arg)).startswith(FIXTURES_PREFIX):
                        files.append(path)
        elif os.path.isfile(arg):
            files.append(arg)
        else:
            raise FileNotFoundError(arg)
    return [(f, norm(os.path.normpath(f))) for f in files]


def norm(path):
    return path.replace(os.sep, "/")


def is_binary(full_path):
    if os.path.splitext(full_path)[1].lower() in BINARY_EXTENSIONS:
        return True
    with open(full_path, "rb") as handle:
        return b"\0" in handle.read(8192)


def is_test_source(path):
    return bool(_TEST_SOURCE_SET.search(norm(path)))


def is_kotlin_main_source(path):
    p = norm(path)
    return p.endswith(".kt") and ("/src/" in p or p.startswith("src/")) and not is_test_source(p)


def read_text(full_path):
    with open(full_path, encoding="utf-8") as handle:
        return handle.read()


def line_of(text, offset):
    return text.count("\n", 0, offset) + 1


def strip_kotlin(src, comments=None, strings=None):
    """Returns (code, bare) with the same length and line breaks as `src`.

    `comments` and `strings`, when given as lists, receive the (start, end) offsets of every
    comment and every string literal (char literals excluded).

    code: comments blanked, string literals kept.
    bare: comments and the contents of string and char literals blanked; quotes kept.
    Good enough for Kotlin and Gradle Kotlin scripts; not a full lexer.
    """
    n = len(src)
    code = list(src)
    bare = list(src)

    def blank(arr, start, end):
        for k in range(start, end):
            if arr[k] != "\n":
                arr[k] = " "

    def skip_template(j):
        # j is just after "${"; returns the index just after the matching "}".
        depth = 1
        while j < n and depth:
            ch = src[j]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
            elif ch == '"':
                j = end_of_string(j) - 1
            j += 1
        return j

    def end_of_string(i):
        raw = src.startswith('"""', i)
        j = i + (3 if raw else 1)
        while j < n:
            if raw and src.startswith('"""', j):
                j += 3
                while j < n and src[j] == '"':
                    j += 1
                return j
            ch = src[j]
            if not raw and ch == "\\":
                j += 2
            elif not raw and ch == '"':
                return j + 1
            elif not raw and ch == "\n":
                return j  # unterminated: stop at the line end rather than eat the file
            elif src.startswith("${", j):
                j = skip_template(j + 2)
            else:
                j += 1
        return n

    char_literal = re.compile(r"'(\\u[0-9a-fA-F]{4}|\\.|[^'\\\n])'")
    i = 0
    while i < n:
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            blank(code, i, j)
            blank(bare, i, j)
            if comments is not None:
                comments.append((i, j))
            i = j
        elif src.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if src.startswith("/*", j):
                    depth, j = depth + 1, j + 2
                elif src.startswith("*/", j):
                    depth, j = depth - 1, j + 2
                else:
                    j += 1
            blank(code, i, j)
            blank(bare, i, j)
            if comments is not None:
                comments.append((i, j))
            i = j
        elif src[i] == '"':
            j = end_of_string(i)
            if strings is not None:
                strings.append((i, j))
            quote = 3 if src.startswith('"""', i) else 1
            close = quote if j - i >= 2 * quote and src[j - 1] == '"' else 0
            blank(bare, i + quote, j - close)
            i = j
        elif src[i] == "'":
            m = char_literal.match(src, i)
            if m:
                blank(bare, i + 1, m.end() - 1)
                i = m.end()
            else:
                i += 1
        else:
            i += 1
    return "".join(code), "".join(bare)


# --- Comments of every file kind a public-copy check cares about ---------------------------------
COMMENT_EXTENSIONS = (".kt", ".kts", ".xml", ".pro")
_XML_COMMENT = re.compile(r"<!\[CDATA\[.*?\]\]>|<!--.*?-->", re.DOTALL)
_PRO_COMMENT = re.compile(r"(?:(?<=\s)|^)#[^\n]*", re.MULTILINE)


def comment_spans(path, src):
    """Returns (comments, strings): lists of (start, end) offsets of the comments in `src` and,
    for Kotlin, of its string literals. `path` picks the language by extension: Kotlin and
    Kotlin script (`//`, nested `/* */`, KDoc), XML (`<!-- -->`), ProGuard (`#`)."""
    ext = os.path.splitext(path)[1].lower()
    if ext in (".kt", ".kts"):
        comments, strings = [], []
        strip_kotlin(src, comments, strings)
        return comments, strings
    if ext == ".xml":
        return [m.span() for m in _XML_COMMENT.finditer(src) if m.group().startswith("<!--")], []
    if ext == ".pro":
        return [m.span() for m in _PRO_COMMENT.finditer(src)], []
    return [], []


def normalized_lines(path, src):
    """The code of `src` with comments removed and whitespace normalised, as [(line, text)].

    Kotlin: whitespace outside string literals collapses to one space and a logical line ends at
    a line break outside a string; string literals stay verbatim. XML and ProGuard: each line is
    trimmed, comments (and the blanks around them) become one space, empty lines are dropped.
    Two versions of a file with equal results differ in comments and whitespace only.
    """
    comments, strings = comment_spans(path, src)
    kotlin = os.path.splitext(path)[1].lower() in (".kt", ".kts")
    if not kotlin:
        out = list(src)
        for start, end in reversed(comments):
            while start > 0 and src[start - 1] in " \t":
                start -= 1
            while end < len(src) and src[end] in " \t":
                end += 1
            out[start:end] = " " + "\n" * src.count("\n", start, end)
        text = "".join(out)
        return [(n, l.strip()) for n, l in enumerate(text.split("\n"), 1) if l.strip()]
    chars = list(src)
    for start, end in comments:
        for k in range(start, end):
            if chars[k] != "\n":
                chars[k] = " "
    in_string = [False] * len(src)
    for start, end in strings:
        for k in range(start, end):
            in_string[k] = True
    lines, cur, first, line, pending = [], [], None, 1, False
    for k, ch in enumerate(chars):
        if in_string[k]:
            if pending and cur:
                cur.append(" ")
            pending = False
            first = first or line
            cur.append(ch)
        elif ch == "\n":
            text = "".join(cur).strip()
            if text:
                lines.append((first, text))
            cur, first, pending = [], None, False
        elif ch.isspace():
            pending = True
        else:
            if pending and cur:
                cur.append(" ")
            pending = False
            first = first or line
            cur.append(ch)
        if ch == "\n":
            line += 1
    text = "".join(cur).strip()
    if text:
        lines.append((first, text))
    return lines
