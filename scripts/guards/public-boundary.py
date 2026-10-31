#!/usr/bin/env python3
"""Guard public-boundary: every file is on exactly one side of the public source copy.

`scripts/public-paths.txt` lists `public <glob>` and `private <glob>` entries (the format is
described at the top of that file). This guard fails when

  * a file matches no entry         ("unclassified")
  * an entry matches no file        ("dead entry")

Overlap rule: the LAST matching entry decides, as in .gitignore. A broad entry goes first and
its exceptions after it, so a file matching both a public and a private entry is not an error:
it takes the side of the later line. An exception placed before its broad entry is shadowed, and
that shows up as a dead entry only when it matches nothing at all; keep exceptions below.

Glob syntax (gitignore-like): `*` and `?` stay inside one folder, `**` crosses folders, `[abc]`
is a character set; a pattern without a `/` (apart from a trailing one) matches a name at any
depth; a leading or inner `/` anchors it to the repository root; a pattern that names a folder
matches everything under it.

    python3 scripts/guards/public-boundary.py            # whole repository
    python3 scripts/guards/public-boundary.py PATH ...   # only these files (no dead-entry check)
    python3 scripts/guards/public-boundary.py --sides    # also print `<side> <path>` per file
    python3 scripts/guards/public-boundary.py --summary  # hit counts per module and pattern

Both halves always run (`--content` is still accepted and does nothing). Content half: in every file the list puts on the public
side, with extension .kt, .kts, .xml or .pro, the COMMENT text (Kotlin `//` and nested `/* */`
including KDoc, never string or char literals; XML `<!-- -->`; ProGuard `#`) must not contain:

  date      an ISO date or month (`2026-10-23`, `2026-10`)
  owner     the word "owner" as project provenance. Allowed: "lifecycle owner", "view model
            store owner", "saved state registry owner" (Android's own owner types, also when
            written LifecycleOwner and the like, which the word boundary already skips), the
            phrases "owner of this/the/a/that/each scope|composition|coroutine|job|context|
            lock|reference|file|process|window|view|activity|task|cache|resource|entry|slot|
            node" (see OWNER_ALLOWED). "ownership" is a different word and never matches.
  rule      a citation of the private RULES file: the word RULES, "rule R12", or an R-number
            from R4 up. R1 to R3 pass (gamepad buttons) unless the line also says RULES/rule.
            Allowed: R8 as Android's shrinker (a bare "R8"; "(R8)" or "R4/R8" is a citation),
            Cloudflare R2, and `R.string`-style resource references (no digits after the R).
  link      a path or file name (`*.md`, `*.sh`, `*.py`) the list classifies private, or, for
            a bare file name, one that only private files carry (CLAUDE.md, RULES.md). URLs
            are skipped.
  agent     Claude, viet-code, soat-loi, tim-trong-code, or "agent" in the AI sense. Allowed:
            "user agent" / User-Agent, "agent" next to Java/Android/instrumentation words
            (see AGENT_ALLOWED).
  step      a plan step code or the private plan itself: ROADMAP, "step I3b", "E1b step 2d",
            a bare code with a lowercase suffix (`S5c`, `I3b`), "no board", "the plan",
            "plan's", "plan ›". A bare "step 2" is an ordinary sequence and passes; so do
            R.id-style hex such as `0xE1b` (no word boundary) and codes without a suffix.

Markdown and settings files. A public `.md` file (or the replacement the copy writes in its place,
see REPLACED) is checked line by line, code blocks included, for the same owner, rule and link
patterns; dates are fine there, and `owner` inside an inline code span (`ownerOf`) passes. A link
or path is resolved against the file's own folder as well as the repository root. In `.toml`,
`.properties` and `.gradle.kts`-style `#` comment lines every pattern applies as in code comments.
Working notes (feature pages, SPEC, ARCHITECTURE) are private, so no check covers them.

There is no escape marker: fix the text. `--summary` prints the hit counts per top-level module
and per pattern instead of the hit lines (the exit code still tells pass or fail).
"""

import collections
import os
import posixpath
import re
import subprocess
import sys

import guardlib

NAME = "public-boundary"
LIST_PATH = "scripts/public-paths.txt"


def glob_to_regex(glob):
    """Regex for one gitignore-like glob; matches a path or any folder prefix of it."""
    anchored = "/" in glob.rstrip("/")
    glob = glob.rstrip("/").lstrip("/")
    out = []
    i = 0
    while i < len(glob):
        ch = glob[i]
        if glob.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
            continue
        if glob.startswith("**", i):
            out.append(".*")
            i += 2
            continue
        if ch == "*":
            out.append("[^/]*")
        elif ch == "?":
            out.append("[^/]")
        elif ch == "[":
            end = glob.find("]", i + 2)
            if end < 0:
                out.append(re.escape(ch))
            else:
                body = glob[i + 1:end]
                if body.startswith("!"):
                    body = "^" + body[1:]
                out.append("[" + body.replace("\\", "\\\\") + "]")
                i = end
        else:
            out.append(re.escape(ch))
        i += 1
    prefix = "" if anchored else "(?:.*/)?"
    return re.compile("^" + prefix + "".join(out) + "(?:/.*)?$")


def parse_entries(text, reporter, shown):
    """Returns [(line_number, side, glob, regex)]; a malformed line is a violation."""
    entries = []
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.split(" #", 1)[0].strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or parts[0] not in ("public", "private"):
            reporter.fail(shown, number, "expected `public <glob>` or `private <glob>`")
            continue
        entries.append((number, parts[0], parts[1].strip(), glob_to_regex(parts[1].strip())))
    return entries


def classify(path, entries):
    """The side of the last entry matching `path`, with that entry; None when none matches."""
    side = None
    for entry in entries:
        if entry[3].match(path):
            side = entry
    return side


def find_list():
    here = os.path.join(os.getcwd(), LIST_PATH)
    return here if os.path.isfile(here) else os.path.join(guardlib.REPO_ROOT, LIST_PATH)


# --- Content half ---------------------------------------------------------------------------------
DATE = re.compile(r"(?<![\d-])(?:19|20)\d\d-(?:0[1-9]|1[0-2])(?:-(?:0[1-9]|[12]\d|3[01]))?(?![\d-])")

OWNER = re.compile(r"\bowners?\b", re.IGNORECASE)
# Phrases that use "owner" in its ordinary programming sense. Derived from real hits; keep narrow.
OWNER_ALLOWED = re.compile(
    r"(?:lifecycle|view[- ]?model[- ]?store|saved[- ]?state[- ]?registry|activity result registry|"
    r"on back pressed dispatcher|back pressed dispatcher|menu host)\s+owners?\b"
    r"|\bowners? of (?:this|the|a|that|each) (?:scope|composition|coroutine|job|context|lock|reference|"
    r"file|process|window|view|activity|task|cache|resource|entry|slot|node)\b",
    re.IGNORECASE,
)

RULES_WORD = re.compile(r"\bRULES?\b")
RULE_REF = re.compile(r"\b[Rr]ules? R\d{1,2}\b")
R_NUMBER = re.compile(r"(?<![\w.])R(\d{1,2})(?![\w.]*\()(?!\d)")
# R8 is also Android's code shrinker: bare "R8" passes, "(R8)" or "R4/R8" cites a rule.
R8_CITED = re.compile(r"[(,;]\s*R8\b|\bR\d{1,2}/R8\b|\bR8\s*[),;/]")

AGENT = re.compile(r"\bClaude\b|\bviet-code\w*|\bsoat-loi\b|\btim-trong-code\b|(?i:\bagents?\b)")
AGENT_ALLOWED = re.compile(
    r"\buser[- ]?agents?\b|\bandroid agents?\b|\bjava agents?\b|\binstrumentation agents?\b|"
    r"\bagent(?:s)? (?:attach|jar|library)", re.IGNORECASE)

PATH_TOKEN = re.compile(r"[\w.\-]+(?:/[\w.\-*]*)+|[\w\-]+\.(?:md|sh|py|txt)\b")
URL = re.compile(r"\b[a-zA-Z][a-zA-Z0-9+.\-]*://\S*")

# Plan step codes ("step I3b", "E1b step 2d", "S5c") and the private plan itself ("the plan's rule",
# "plan › API", ROADMAP). A bare "step 2" or "step 1" stays free: it is an ordinary sequence.
PLAN_CODE = re.compile(
    r"\bROADMAP\b"
    r"|\b[Ss]teps? (?:[A-IS]\d+[a-z]?\d?|\d+[a-z]\d?)\b"
    r"|\b[A-IS]\d+[a-z]\d?\b"
    r"|\bno board\b"
    r"|\b[Tt]he plan\b|\bplan's\b|\bplan\s*(?:›|:|,\s*\")"
)

# The files the copy writes from scripts/public/<name> in place of the repository's own. Keep in step
# with REPLACED in publish-source.py (a test compares the two).
REPLACED = {
    ".gitignore": "gitignore",
    "scripts/README.md": "scripts-README.md",
    "docs/README.md": "docs-README.md",
}
PUBLIC_TEXT_DIR = "scripts/public"
HASH_COMMENT_EXTENSIONS = (".toml", ".properties")
MD_LINK = re.compile(r"\]\(([^)\s]+)")
INLINE_CODE = re.compile(r"`[^`\n]*`")

PATTERNS = ("date", "owner", "rule", "link", "agent", "step")


def _universe(root):
    """Every file under `root` (the folder holding the list), as repo-relative paths."""
    if os.path.isdir(os.path.join(root, ".git")):
        try:
            out = subprocess.run(["git", "ls-files", "-z", "--cached", "--others", "--exclude-standard"],
                                 cwd=root, capture_output=True, check=True).stdout.decode("utf-8")
            return [p for p in out.split("\0") if p]
        except (subprocess.CalledProcessError, OSError):
            pass
    found = []
    for base, dirs, names in os.walk(root):
        dirs[:] = [d for d in dirs if d not in (".git", "build", ".gradle", "node_modules")]
        for name in names:
            found.append(guardlib.norm(os.path.relpath(os.path.join(base, name), root)))
    return found


class Linker:
    """Decides whether a path-like token in a comment names a private file."""

    def __init__(self, entries, root):
        self.entries = entries
        self.by_name = collections.defaultdict(set)
        self.top_level = set()
        for path in _universe(root):
            self.top_level.add(path.split("/", 1)[0])
            winner = classify(path, entries)
            if winner:
                self.by_name[os.path.basename(path)].add(winner[1])

    def names_top_level(self, token):
        """True when `token` starts with a top-level name of the repository (so it is root-relative)."""
        return token.split("/", 1)[0] in self.top_level

    def private_path(self, path):
        """True when the repository-relative `path` is on the private side."""
        winner = classify(path, self.entries)
        return bool(winner) and winner[1] == "private"

    def private_token(self, token):
        token = token.rstrip(".,;:)")
        while token.startswith(("./", "../")):
            token = token.split("/", 1)[1]
        if not token or token.startswith("/"):
            return False
        if "/" not in token:
            sides = self.by_name.get(token)
            return bool(sides) and sides == {"private"}
        winner = classify(token, self.entries)
        return bool(winner) and winner[1] == "private"


def hits_in_line(text, linker):
    """The pattern names found in one line of comment text."""
    found = []
    if DATE.search(text):
        found.append(("date", "ISO date"))
    if any(not OWNER_ALLOWED.match(text, m.start()) and not _inside_allowed(OWNER_ALLOWED, text, m)
           for m in OWNER.finditer(text)):
        found.append(("owner", "\"owner\" (project provenance)"))
    rule = RULES_WORD.search(text) or RULE_REF.search(text)
    if rule:
        found.append(("rule", f"citation of private RULES (`{rule.group()}`)"))
    else:
        for m in R_NUMBER.finditer(text):
            if int(m.group(1)) >= 4 and not (m.group(1) == "8" and not R8_CITED.search(text)):
                found.append(("rule", f"R-number citation (`{m.group()}`)"))
                break
    plan = PLAN_CODE.search(text)
    if plan:
        found.append(("step", f"plan step or private plan (`{plan.group()}`)"))
    bare = URL.sub(" ", text)
    for m in PATH_TOKEN.finditer(bare):
        if linker.private_token(m.group()):
            found.append(("link", f"reference to a private file (`{m.group().rstrip('.,;:)')}`)"))
            break
    if any(not _inside_allowed(AGENT_ALLOWED, text, m) for m in AGENT.finditer(text)):
        found.append(("agent", "agent/Claude wording"))
    return found


def md_hits_in_line(text, folder, linker):
    """Pattern names found in one line of a public Markdown file: owner, rule, link."""
    found = []
    prose = INLINE_CODE.sub(" ", text)
    if any(not _inside_allowed(OWNER_ALLOWED, prose, m) for m in OWNER.finditer(prose)):
        found.append(("owner", "\"owner\" (project provenance)"))
    bare = URL.sub(" ", text)
    rule = RULES_WORD.search(bare) or RULE_REF.search(bare)
    if rule:
        found.append(("rule", f"citation of private RULES (`{rule.group()}`)"))
    else:
        for m in R_NUMBER.finditer(INLINE_CODE.sub(" ", bare)):
            if int(m.group(1)) >= 4 and not (m.group(1) == "8" and not R8_CITED.search(text)):
                found.append(("rule", f"R-number citation (`{m.group()}`)"))
                break
    tokens = [m.group(1).split("#", 1)[0] for m in MD_LINK.finditer(bare)] + \
        [m.group() for m in PATH_TOKEN.finditer(bare)]
    for token in tokens:
        token = token.rstrip(".,;:)")
        if not token:
            continue
        resolved = posixpath.normpath(posixpath.join(folder, token)) if folder else token
        from_folder = not linker.names_top_level(token) and not resolved.startswith("..") and linker.private_path(resolved)
        if linker.private_token(token) or from_folder:
            found.append(("link", f"reference to a private file (`{token}`)"))
            break
    return found


def scan_markdown(shown, text, linker):
    """Yields (line_number, pattern, reason) for every hit in a public Markdown file."""
    folder = posixpath.dirname(shown)
    for number, line in enumerate(text.split("\n"), 1):
        for pattern, reason in md_hits_in_line(line, folder, linker):
            yield number, pattern, reason


def scan_hash_comments(text, linker):
    """Yields (line_number, pattern, reason) for `#` comment lines of .toml / .properties files."""
    for number, line in enumerate(text.split("\n"), 1):
        if line.lstrip().startswith("#"):
            for pattern, reason in hits_in_line(line, linker):
                yield number, pattern, reason


def _inside_allowed(allowed, text, match):
    return any(a.start() <= match.start() and match.end() <= a.end() for a in allowed.finditer(text))


def scan_content(path, text, linker):
    """Yields (line_number, pattern, reason) for every hit in the comments of one file."""
    comments, _ = guardlib.comment_spans(path, text)
    for start, end in comments:
        first = guardlib.line_of(text, start)
        for offset, line in enumerate(text[start:end].split("\n")):
            for pattern, reason in hits_in_line(line, linker):
                yield first + offset, pattern, reason


def print_summary(counts):
    modules = sorted({m for m, _ in counts})
    print(f"{'module':<16}" + "".join(f"{p:>8}" for p in PATTERNS) + f"{'total':>8}")
    totals = collections.Counter()
    for module in modules:
        row = [counts[(module, p)] for p in PATTERNS]
        totals.update(dict(zip(PATTERNS, row)))
        print(f"{module:<16}" + "".join(f"{n:>8}" for n in row) + f"{sum(row):>8}")
    print(f"{'ALL':<16}" + "".join(f"{totals[p]:>8}" for p in PATTERNS) + f"{sum(totals.values()):>8}")


def main():
    flags = ("--sides", "--content", "--summary")  # --content: kept for old callers, a no-op
    args = [a for a in sys.argv[1:] if a not in flags]
    show_sides = "--sides" in sys.argv[1:]
    summary = "--summary" in sys.argv[1:]
    list_file = find_list()
    if not os.path.isfile(list_file):
        guardlib.die(NAME, f"{LIST_PATH} not found")
    reporter = guardlib.Reporter(NAME)
    entries = parse_entries(guardlib.read_text(list_file), reporter, LIST_PATH)
    # Fixtures are classified (a public copy needs them) but never content-scanned: they hold
    # bad patterns on purpose.
    scanned = guardlib.files_from_args(args, include_fixtures=True)
    paths = [shown for _, shown in scanned]
    used = set()
    for path in paths:
        winner = classify(path, entries)
        if winner is None:
            reporter.fail(path, 0, f"unclassified: add it to {LIST_PATH}")
        else:
            # An entry counts only where it decides: an exception placed above its broad entry
            # never wins, and would otherwise put its files on the wrong side without a word.
            used.add(winner[0])
            if show_sides:
                reporter.info(f"{winner[1]} {path}")
    linker = Linker(entries, os.path.dirname(os.path.dirname(list_file)))
    counts = collections.Counter()
    for full, shown in scanned:
        winner = classify(shown, entries)
        lower = shown.lower()
        is_md = lower.endswith(".md")
        if not winner or winner[1] != "public" or shown.startswith(guardlib.FIXTURES_PREFIX):
            continue
        if not lower.endswith(guardlib.COMMENT_EXTENSIONS + HASH_COMMENT_EXTENSIONS) and not is_md:
            continue
        if shown in REPLACED:
            # The copy publishes the replacement, so that is the text to check.
            full = os.path.join(os.path.dirname(os.path.dirname(list_file)), PUBLIC_TEXT_DIR, REPLACED[shown])
            if not os.path.isfile(full):
                continue
        try:
            text = guardlib.read_text(full)
        except UnicodeDecodeError:
            reporter.fail(shown, 0, "not valid UTF-8; content half cannot read it")
            continue
        if is_md:
            hits = scan_markdown(shown, text, linker)
        elif lower.endswith(HASH_COMMENT_EXTENSIONS):
            hits = scan_hash_comments(text, linker)
        else:
            hits = scan_content(shown, text, linker)
        for line, pattern, reason in hits:
            if summary:
                reporter.failures += 1
                counts[(shown.split("/", 1)[0], pattern)] += 1
            else:
                reporter.fail(shown, line, f"{pattern}: {reason}")
    if summary:
        print_summary(counts)
    if not args or args == ["."]:
        for number, side, glob, _ in entries:
            if number not in used:
                reporter.fail(LIST_PATH, number, f"dead entry: `{side} {glob}` decides no file (matches nothing, or a later entry overrides it)")
    reporter.finish()


if __name__ == "__main__":
    main()
