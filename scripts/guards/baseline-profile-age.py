#!/usr/bin/env python3
"""Baseline Profile age (benchmark/README.md: regenerate the profile after UI changes).

The Baseline Profile names the methods Android compiles ahead of time. Code added or renamed after
it was generated runs in the interpreter until the JIT warms up: on the Thor, closing Settings spent
22-33 % of its main-thread time in the interpreter with a profile 74 UI commits old. This guard fails
when more than MAX_COMMITS commits touched the Kotlin main sources of the UI modules since the last
commit that changed the profile. Run by `scripts/dist.sh --release`, so no release ships a stale
profile; regenerate with `./gradlew :app:generateBaselineProfile` (benchmark/README.md).

Usage: baseline-profile-age.py [--max N]
  --max N  the most UI commits allowed since the profile (default MAX_COMMITS)
Exit 0 pass, 1 too old, 2 could not run (no git, no profile in the history).
"""

import argparse
import subprocess
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
import guardlib  # noqa: E402

NAME = "baseline-profile-age"
PROFILE = "app/src/main/generated/baselineProfiles/baseline-prof.txt"
# The modules whose code runs on the screens the profile's journeys walk.
UI_SOURCES = ["app/src/main/java", "core-ui/src/main/java", "core-input/src/main/java"]
# About two weeks of UI work at this project's pace; the stale profile measured above was 74.
MAX_COMMITS = 25


def git(*args):
    return subprocess.run(["git", *args], capture_output=True, text=True, check=True).stdout.strip()


def main():
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--max", type=int, default=MAX_COMMITS)
    args = parser.parse_args()
    # Both git calls in one try: a failure must exit 2 ("could not run"), never 1 ("too old").
    try:
        last = git("log", "-1", "--format=%H", "--", PROFILE)
        if not last:
            guardlib.die(NAME, f"{PROFILE} is not in the history")
        count = int(git("rev-list", "--count", f"{last}..HEAD", "--", *UI_SOURCES) or 0)
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        guardlib.die(NAME, f"git failed: {error}")
    if count > args.max:
        print(f"{PROFILE}:1: generated {count} UI commits ago (at most {args.max}): "
              "regenerate it with ./gradlew :app:generateBaselineProfile on an Android 9+ emulator")
        print(f"{NAME}: FAIL (1 violation)")
        return 1
    print(f"{NAME}: PASS ({count} UI commits since the profile, at most {args.max})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
