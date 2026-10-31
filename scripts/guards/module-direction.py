#!/usr/bin/env python3
"""Module direction (docs/ARCHITECTURE.md › Modules): app -> features -> core-*.

Reads the modules from settings.gradle.kts and every `project(":...")` or type-safe
`projects.xxx` reference in each module's build.gradle.kts. Rules:
- nothing depends on `app`;
- `core-*` modules never depend on feature modules;
- feature modules (every module that is neither `app` nor `core-*`: library, platforms,
  launcher, scraper, telemetry, ...) never depend on `core-ui`.

Usage: module-direction.py [PROJECT_ROOT]   (default: this repository)
"""

import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "module-direction"

INCLUDE = re.compile(r"\binclude\s*\(([^)]*)\)")
QUOTED = re.compile(r"\"(:[^\"]+)\"")
PROJECT_CALL = re.compile(r"\bproject\(\s*(?:path\s*=\s*)?\"(:[^\"]+)\"")
TYPE_SAFE = re.compile(r"\bprojects\.([A-Za-z0-9_.]+)")


def accessor(module):
    """`:core-ui` -> `coreUi`, `:a:b-c` -> `a.bC`: Gradle's type-safe project accessor."""
    parts = module.strip(":").split(":")
    return ".".join(re.sub(r"[-_](\w)", lambda m: m.group(1).upper(), p) for p in parts)


def kind(module):
    name = module.strip(":")
    if name == "app":
        return "app"
    return "core" if name.startswith("core-") else "feature"


def broken_rule(source, target):
    if kind(target) == "app":
        return "nothing may depend on :app"
    if kind(source) == "core" and kind(target) == "feature":
        return "core-* modules may not depend on feature modules"
    if kind(source) == "feature" and target == ":core-ui":
        return "feature modules may not depend on :core-ui"
    return None


def main(argv):
    report = guardlib.Reporter(NAME)
    root = argv[0] if argv else guardlib.REPO_ROOT
    settings = os.path.join(root, "settings.gradle.kts")
    if not os.path.isfile(settings):
        guardlib.die(NAME, f"no settings.gradle.kts in {root}")
    settings_code, _ = guardlib.strip_kotlin(guardlib.read_text(settings))
    modules = [m for call in INCLUDE.finditer(settings_code) for m in QUOTED.findall(call.group(1))]
    if not modules:
        guardlib.die(NAME, "no include(...) found in settings.gradle.kts")
    by_accessor = {accessor(m): m for m in modules}

    for source in modules:
        rel = source.strip(":").replace(":", "/") + "/build.gradle.kts"
        path = os.path.join(root, rel)
        if not os.path.isfile(path):
            continue
        code, _ = guardlib.strip_kotlin(guardlib.read_text(path))
        references = [(m.start(), m.group(1)) for m in PROJECT_CALL.finditer(code)]
        for m in TYPE_SAFE.finditer(code):
            # `projects.coreUi.something` -> try the longest known accessor prefix.
            parts = m.group(1).split(".")
            for end in range(len(parts), 0, -1):
                target = by_accessor.get(".".join(parts[:end]))
                if target:
                    references.append((m.start(), target))
                    break
        for offset, target in references:
            if target == source:
                continue
            rule = broken_rule(source, target)
            if rule:
                shown = guardlib.norm(os.path.relpath(path, guardlib.REPO_ROOT)) if not argv else rel
                report.fail(shown, guardlib.line_of(code, offset), f"{source} -> {target}: {rule}")
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
