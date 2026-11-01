#!/usr/bin/env python3
"""Release notes format: every version section of CHANGELOG.md reads the same way.

A checked section (`## Unreleased`, or `## <version>` newer than 0.1.0) holds an optional short
summary, then groups in this order, each optional: `### New`, `### Improved`, `### Fixed`,
`### Known issues`, `### Not in this version`. At least one of New, Improved or Fixed has
bullets. Each bullet is `- ` and one line: one plain sentence of at most 140 characters, no
markdown (bold, italics, code, links, HTML) and no issue numbers. At most 8 bullets per group
and 20 in New, Improved, Fixed and Known issues together (the app shows at most 20 notes).
Not in this version is for the GitHub release and the public CHANGELOG; the app never gets it.

Not checked: 0.1.0 and older, and everything below the first of them (history, written before
this format; an `## Unreleased` down there is pre-alpha history too). An `## Unreleased` with
nothing in it yet passes.

Scanned: the repository's root CHANGELOG.md; a folder argument means `<folder>/CHANGELOG.md`, a
file argument is checked whatever its name. scripts/publish-update.py imports `read_section` so
the in-app notes follow the same rules (one parser, no second copy). The format is described in
docs/conventions/releases.md.
"""

import os
import re
import sys
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

sys.dont_write_bytecode = True  # no __pycache__ in the working tree

NAME = "changelog-format"

# (heading, kind) in the only allowed order; kind is the manifest's `sections[].kind`.
GROUPS = (
    ("New", "new"),
    ("Improved", "improved"),
    ("Fixed", "fixed"),
    ("Known issues", "known"),
    ("Not in this version", "not-in-version"),
)
APP_KINDS = ("new", "improved", "fixed", "known")  # the groups the app shows
REQUIRED_KINDS = ("new", "improved", "fixed")  # a section needs bullets in one of these
MAX_BULLET_CHARS = 140
MAX_PER_GROUP = 8
MAX_APP_BULLETS = 20
MAX_SUMMARY_SENTENCES = 2
# Sections up to and including this version are history in an older style.
LAST_UNCHECKED = (0, 1, 0)

VERSION_HEADING = re.compile(r"^## (\d+)\.(\d+)\.(\d+)([-+][0-9A-Za-z.+-]*)?(\s|$)")
MARKDOWN = (
    (re.compile(r"\*\*|__"), "bold"),
    (re.compile(r"`"), "code"),
    (re.compile(r"\]\(|\[[^\]]*\]\[|<https?:"), "a link"),
    (re.compile(r"(?<![\w*])\*(?=\S)[^*]*\S\*(?![\w*])"), "italics"),
    (re.compile(r"(?<![\w_])_(?=\S)[^_]*\S_(?![\w_])"), "italics"),
    (re.compile(r"</?[A-Za-z!][^>]*>"), "HTML"),
)
ISSUE_NUMBER = re.compile(r"(?<![\w&])#\d+\b")
# A sentence end followed by the start of another sentence.
SENTENCE_BREAK = re.compile(r"[.!?][\"”’)]?\s+[\"“(]?[A-Z0-9]")


@dataclass
class Group:
    heading: str
    kind: str
    line: int
    items: List[Tuple[int, str]] = field(default_factory=list)  # (line number, text)


@dataclass
class Section:
    title: str  # the heading without "## "
    line: int
    summary: List[Tuple[int, str]] = field(default_factory=list)
    groups: List[Group] = field(default_factory=list)


def version_key(heading: str) -> Optional[Tuple[int, int, int]]:
    match = VERSION_HEADING.match(heading)
    return tuple(int(match.group(i)) for i in (1, 2, 3)) if match else None  # type: ignore[return-value]


def markdown_problem(text: str) -> Optional[str]:
    for pattern, what in MARKDOWN:
        if pattern.search(text):
            return f"markdown ({what}); release notes are plain text"
    return None


def bullet_problems(text: str) -> List[str]:
    problems = []
    if not text.strip():
        return ["an empty bullet"]
    if len(text) > MAX_BULLET_CHARS:
        problems.append(f"bullet of {len(text)} characters; at most {MAX_BULLET_CHARS}")
    problem = markdown_problem(text)
    if problem:
        problems.append(problem)
    if ISSUE_NUMBER.search(text):
        problems.append("an issue number; the app shows these words, write what changed instead")
    if SENTENCE_BREAK.search(text):
        problems.append("more than one sentence in a bullet; split it or shorten it")
    return problems


def read_section(lines: List[str], start: int) -> Tuple[Section, List[Tuple[int, str]]]:
    """Reads the section whose `## ` heading is lines[start] (0-based); line numbers in the
    result are 1-based. Returns the section and its problems as (line number, message)."""
    section = Section(lines[start][3:].strip(), start + 1)
    problems: List[Tuple[int, str]] = []
    order = [heading for heading, _ in GROUPS]
    group: Optional[Group] = None
    summary_paragraphs = 0
    previous_blank = True
    for number, line in enumerate(lines[start + 1:], start + 2):
        if line.startswith("## ") or line.startswith("# "):
            break
        blank = not line.strip()
        if line.startswith("#"):
            heading = line.lstrip("#").strip()
            if not line.startswith("### ") or heading not in order:
                problems.append((number, f"unknown heading '{line.strip()}'; use ### "
                                         + ", ### ".join(order)))
                group = Group(heading, "", number)  # its bullets are not reported again
            elif section.groups and order.index(heading) <= order.index(section.groups[-1].heading):
                problems.append((number, f"'### {heading}' out of order or twice; the order is "
                                         + ", ".join(order)))
                group = Group(heading, "", number)
            else:
                group = Group(heading, dict(GROUPS)[heading], number)
                section.groups.append(group)
        elif blank:
            pass
        elif line.startswith("- "):
            if group is None:
                problems.append((number, "a bullet outside a group; put it under ### New, "
                                         "### Improved, ### Fixed or ### Known issues"))
            else:
                group.items.append((number, line[2:].strip()))
        elif line[:1].isspace():
            problems.append((number, "a bullet is one line; this continues the line above"))
        elif re.match(r"^[*+] |^\d+[.)] ", line):
            problems.append((number, "bullets start with '- '"))
        elif group is not None or section.groups:
            problems.append((number, "only '- ' bullets under a group heading"))
        else:
            if previous_blank:
                summary_paragraphs += 1
            section.summary.append((number, line.strip()))
        previous_blank = blank

    if section.summary:
        first = section.summary[0][0]
        text = " ".join(t for _, t in section.summary)
        if summary_paragraphs > 1:
            problems.append((first, "the summary is one paragraph"))
        sentences = len(SENTENCE_BREAK.findall(text)) + 1
        if sentences > MAX_SUMMARY_SENTENCES:
            problems.append((first, f"a summary of {sentences} sentences; at most "
                                    f"{MAX_SUMMARY_SENTENCES}"))
        for number, line in section.summary:
            problem = markdown_problem(line)
            if problem:
                problems.append((number, problem))

    app_bullets = 0
    for g in section.groups:
        if not g.items:
            problems.append((g.line, f"'### {g.heading}' has no bullets; remove the heading"))
        for index, (number, text) in enumerate(g.items, start=1):
            for problem in bullet_problems(text):
                problems.append((number, problem))
            if index == MAX_PER_GROUP + 1:
                problems.append((number, f"more than {MAX_PER_GROUP} bullets under "
                                         f"'### {g.heading}'"))
            if g.kind in APP_KINDS:
                app_bullets += 1
                if app_bullets == MAX_APP_BULLETS + 1:
                    problems.append((number, f"more than {MAX_APP_BULLETS} bullets in New, "
                                             "Improved, Fixed and Known issues together"))
    empty_unreleased = section.title == "Unreleased" and not section.groups and not section.summary
    if not empty_unreleased and not any(g.items for g in section.groups if g.kind in REQUIRED_KINDS):
        problems.append((section.line, "no bullets under ### New, ### Improved or ### Fixed"))
    problems.sort(key=lambda p: p[0])
    return section, problems


def check_text(text: str) -> List[Tuple[int, str]]:
    """Problems of every checked section of a CHANGELOG, as (line number, message)."""
    lines = text.splitlines()
    problems: List[Tuple[int, str]] = []
    for index, line in enumerate(lines):
        if not line.startswith("## "):
            continue
        key = version_key(line)
        if key is not None and key <= LAST_UNCHECKED:
            break  # history from here down
        if key is None and line.strip() != "## Unreleased":
            problems.append((index + 1, "a '## ' heading is a version ('## 0.1.1 (date, tag ...)') "
                                        "or '## Unreleased'"))
            continue
        problems.extend(read_section(lines, index)[1])
    return problems


def targets(argv):
    """(full path, shown path) of the CHANGELOGs to check."""
    import guardlib  # here, so publish-update.py can import this file without guardlib

    if not argv:
        full = os.path.join(guardlib.REPO_ROOT, "CHANGELOG.md")
        return [(full, "CHANGELOG.md")] if os.path.isfile(full) else []
    found = []
    for arg in argv:
        if os.path.isdir(arg):
            full = os.path.join(arg, "CHANGELOG.md")
            if os.path.isfile(full):
                found.append((full, guardlib.norm(os.path.normpath(full))))
        elif os.path.isfile(arg):
            found.append((arg, guardlib.norm(os.path.normpath(arg))))
        else:
            guardlib.die(NAME, f"no such file or folder: {arg}")
    return found


def main(argv):
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import guardlib

    report = guardlib.Reporter(NAME)
    for full, shown in targets(argv):
        try:
            text = guardlib.read_text(full)
        except (OSError, UnicodeDecodeError) as e:
            guardlib.die(NAME, f"cannot read {shown}: {e}")
        for number, message in check_text(text):
            report.fail(shown, number, message)
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
