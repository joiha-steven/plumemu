#!/usr/bin/env python3
"""Dependency licences (RULES R23: no GPL code).

Lists every library in the app's debug and release runtime classpaths (`./gradlew -q
:app:dependencies`), has Gradle resolve the POM of each one and of their parents
(`licence-poms.init.gradle.kts`, which downloads POMs the Gradle cache no longer holds), reads
the `<licenses>` of each POM (following `<parent>` POMs when a POM has none) and fails on:
- any GPL, LGPL or AGPL licence, "with exception" variants included;
- a library whose licence is unknown or missing.
A library may pass anyway when `licence-allowlist.txt` lists it with a reason.
Ends with a count per licence.

Usage: licences.py [--deps-file FILE] [--cache DIR] [--allowlist FILE]
  --deps-file  output of `gradlew :app:dependencies` to read instead of running Gradle; POMs are
               then only looked up in --cache, Gradle is not asked for them
  --cache      Gradle module cache (default $GRADLE_USER_HOME/caches/modules-2/files-2.1)
"""

import argparse
import collections
import glob
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ElementTree

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "licences"

CONFIGURATIONS = ("debugRuntimeClasspath", "releaseRuntimeClasspath")

POMS_INIT_SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "licence-poms.init.gradle.kts")

# `+--- group:artifact:version`, `\--- group:artifact:1.0 -> 1.2 (*)`, `group:artifact -> 1.2`
TREE_LINE = re.compile(r"^[| ]*[+\\]--- ([^\s:]+):([^\s:]+)(?::([^\s]+))?(?: -> ([^\s]+))?(.*)$")

ALLOWED_KINDS = {"Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "BSD", "ISC", "CC0-1.0",
                 "Unlicense"}
COPYLEFT_KINDS = {"GPL", "LGPL", "AGPL"}


def classify(name, url):
    s = f"{name} {url}".lower()
    if re.search(r"affero|\bagpl", s):
        return "AGPL"
    if re.search(r"lesser general public|library general public|\blgpl", s):
        return "LGPL"
    if re.search(r"general public licen[cs]e|\bgpl|gnu\.org/licenses/(old-licenses/)?gpl", s):
        return "GPL"
    if "apache" in s and re.search(r"2\.0|license-2|version 2", s):
        return "Apache-2.0"
    if re.search(r"\bmit\b|opensource\.org/licenses/mit", s):
        return "MIT"
    if "bsd" in s:
        if re.search(r"3-clause|new|revised|bsd-3", s):
            return "BSD-3-Clause"
        if re.search(r"2-clause|simplified|bsd-2", s):
            return "BSD-2-Clause"
        return "BSD"
    if re.search(r"\bisc\b", s):
        return "ISC"
    if "cc0" in s or "publicdomain/zero" in s:
        return "CC0-1.0"
    if "unlicense" in s:
        return "Unlicense"
    return None


def run_gradle():
    out_dir = os.path.join(guardlib.REPO_ROOT, "build", "verify")
    os.makedirs(out_dir, exist_ok=True)
    log = os.path.join(out_dir, "dependencies.txt")
    with open(log, "w") as handle:
        rc = subprocess.run(["./gradlew", "-q", "--console=plain", ":app:dependencies"],
                            cwd=guardlib.REPO_ROOT, stdout=handle, stderr=subprocess.STDOUT).returncode
    if rc != 0:
        guardlib.die(NAME, f"./gradlew :app:dependencies failed (exit {rc}); see {log}")
    return log


def fetch_poms(coordinates):
    """{(group, artifact, version): POM path} from Gradle, parents included, and the log path.

    Gradle downloads the POMs its cache has lost; see licence-poms.init.gradle.kts for why the
    cache alone is not enough."""
    out_dir = os.path.join(guardlib.REPO_ROOT, "build", "verify")
    os.makedirs(out_dir, exist_ok=True)
    wanted = os.path.join(out_dir, "licence-poms-wanted.txt")
    found = os.path.join(out_dir, "licence-poms.txt")
    log = os.path.join(out_dir, "licence-poms-gradle.txt")
    with open(wanted, "w") as handle:
        handle.writelines(":".join(coordinate) + "\n" for coordinate in coordinates)
    if os.path.exists(found):
        os.remove(found)
    with open(log, "w") as handle:
        rc = subprocess.run(["./gradlew", "-q", "--console=plain", "--no-configuration-cache",
                             "--init-script", POMS_INIT_SCRIPT, "licencePoms",
                             f"-PlicencePomsIn={wanted}", f"-PlicencePomsOut={found}"],
                            cwd=guardlib.REPO_ROOT, stdout=handle, stderr=subprocess.STDOUT).returncode
    if rc != 0 or not os.path.exists(found):
        guardlib.die(NAME, f"./gradlew licencePoms failed (exit {rc}); see {log}")
    poms = {}
    with open(found, encoding="utf-8") as handle:
        for line in handle:
            coordinate, _, where = line.rstrip("\n").partition("\t")
            if not where.startswith("missing: "):
                poms[tuple(coordinate.split(":"))] = where
    return poms, found


def parse_dependencies(text):
    """{configuration: set of (group, artifact, version)} for CONFIGURATIONS."""
    found = {}
    current = None
    for line in text.splitlines():
        header = re.match(r"^(\w+) - ", line)
        if header:
            current = header.group(1) if header.group(1) in CONFIGURATIONS else None
            if current:
                found[current] = set()
            continue
        if current is None:
            continue
        m = TREE_LINE.match(line)
        if not m:
            continue
        group, artifact, version, resolved, rest = m.groups()
        if "(c)" in rest or "(n)" in rest:
            continue  # constraints and unresolved declarations; the resolved node appears too
        if group == "project":
            continue
        version = resolved or version
        if version:
            found[current].add((group, artifact, version))
    return found


def find_pom(cache, group, artifact, version):
    matches = glob.glob(os.path.join(cache, group, artifact, version, "*", f"{artifact}-{version}.pom"))
    return matches[0] if matches else None


def _child(element, tag):
    for child in element:
        if child.tag.split("}")[-1] == tag:
            return child
    return None


def _text(element, tag):
    child = _child(element, tag) if element is not None else None
    return (child.text or "").strip() if child is not None else ""


def pom_licences(locate, group, artifact, version, depth=0):
    """([(name, url)], coordinate the licences came from) or ([], reason).

    `locate(group, artifact, version)` gives (path of the POM or None, where it was looked for)."""
    coordinate = f"{group}:{artifact}:{version}"
    pom, looked_in = locate(group, artifact, version)
    if pom is None:
        return [], f"POM of {coordinate} is not in {looked_in}"
    try:
        root = ElementTree.parse(pom).getroot()
    except ElementTree.ParseError as error:
        return [], f"POM of {coordinate} cannot be read: {error}"
    licenses = _child(root, "licenses")
    found = []
    if licenses is not None:
        for licence in licenses:
            if licence.tag.split("}")[-1] == "license":
                found.append((_text(licence, "name"), _text(licence, "url")))
    if found:
        return found, coordinate
    parent = _child(root, "parent")
    if parent is not None and depth < 10:
        return pom_licences(locate, _text(parent, "groupId"), _text(parent, "artifactId"),
                            _text(parent, "version"), depth + 1)
    return [], f"no <licenses> in the POM of {coordinate} or its parents"


def read_allowlist(path, report):
    allowed = {}
    if not os.path.isfile(path):
        return allowed
    with open(path, encoding="utf-8") as handle:
        for number, raw in enumerate(handle, start=1):
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 1)
            if len(parts) < 2 or parts[0].count(":") not in (1, 2):
                report.fail(guardlib.norm(os.path.relpath(path)), number,
                            "expected `group:artifact[:version]  reason`")
                continue
            allowed[parts[0]] = parts[1]
    return allowed


def main(argv):
    parser = argparse.ArgumentParser(description="Dependency licence guard")
    parser.add_argument("--deps-file")
    gradle_home = os.environ.get("GRADLE_USER_HOME", os.path.expanduser("~/.gradle"))
    parser.add_argument("--cache", default=os.path.join(gradle_home, "caches", "modules-2", "files-2.1"))
    parser.add_argument("--allowlist", default=os.path.join(os.path.dirname(__file__), "licence-allowlist.txt"))
    args = parser.parse_args(argv)

    report = guardlib.Reporter(NAME)
    deps_file = args.deps_file or run_gradle()
    with open(deps_file, encoding="utf-8") as handle:
        by_configuration = parse_dependencies(handle.read())
    for configuration in CONFIGURATIONS:
        if configuration not in by_configuration:
            guardlib.die(NAME, f"configuration {configuration} not found in {deps_file}")
        if not by_configuration[configuration]:
            # An empty list means the Gradle output changed shape, not that there is nothing to check.
            guardlib.die(NAME, f"no libraries read for {configuration} from {deps_file}; "
                               f"has the format of `gradlew :app:dependencies` changed?")
    coordinates = sorted(set().union(*by_configuration.values()))
    if not coordinates:
        guardlib.die(NAME, f"no libraries read from {deps_file}")
    allowlist = read_allowlist(args.allowlist, report)

    if args.deps_file:
        def locate(group, artifact, version):
            return find_pom(args.cache, group, artifact, version), "the Gradle cache"
    else:
        poms, poms_file = fetch_poms(coordinates)
        report.info(f"{len(poms)} POMs resolved by Gradle, parents included (list: "
                    f"{os.path.relpath(poms_file, guardlib.REPO_ROOT)})")

        def locate(group, artifact, version):
            return poms.get((group, artifact, version)), f"what Gradle could resolve (see {poms_file})"

    counts = collections.Counter()
    for group, artifact, version in coordinates:
        coordinate = f"{group}:{artifact}:{version}"
        reason = allowlist.get(coordinate) or allowlist.get(f"{group}:{artifact}")
        licences, source = pom_licences(locate, group, artifact, version)
        kinds = sorted({classify(n, u) or f"unknown ({n or u or 'no name'})" for n, u in licences})
        if reason:
            counts["allowlisted"] += 1
            continue
        if not licences:
            report.fail(coordinate, 0, f"licence unknown: {source}; check it by hand and add it "
                                       f"to scripts/guards/licence-allowlist.txt with a reason if it is fine")
            counts["unknown"] += 1
            continue
        bad = [k for k in kinds if k in COPYLEFT_KINDS]
        unknown = [k for k in kinds if k not in COPYLEFT_KINDS and k not in ALLOWED_KINDS]
        if bad:
            report.fail(coordinate, 0, f"{', '.join(bad)} is not allowed (copyleft code cannot ship in the app); "
                                       f"licences: {licences} from {source}")
        elif unknown:
            report.fail(coordinate, 0, f"licence not recognised: {', '.join(unknown)} from {source}; "
                                       f"a licence outside the allow-list needs a decision before the dependency is added; "
                                       f"the allow-list is scripts/guards/licence-allowlist.txt (entry plus reason)")
        for kind in kinds:
            counts[kind if kind in ALLOWED_KINDS or kind in COPYLEFT_KINDS else "unknown"] += 1
    summary = ", ".join(f"{kind} {count}" for kind, count in sorted(counts.items()))
    report.info(f"{len(coordinates)} libraries in {' + '.join(CONFIGURATIONS)}: {summary}")
    report.finish()


if __name__ == "__main__":
    main(sys.argv[1:])
