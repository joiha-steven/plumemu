#!/usr/bin/env python3
"""Writes library/.../matching/DatFolderNames.kt: the folder names of the RetroArch / No-Intro / Redump
style ("Sony - PlayStation 2") for each system, from the DAT header names in tools/names/systems.json
(`datNames`) plus the few libretro playlist names no DAT carries (EXTRA below).

    python3 -I tools/platforms/gen_folder_names.py          # rewrite the file
    python3 -I tools/platforms/gen_folder_names.py --check  # exit 1 when the file is out of date

Each DAT name is written twice: as is, and without trailing brackets ("(Headerless)", "(FDS)").
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SYSTEMS = ROOT / "tools/names/systems.json"
OUT = ROOT / "library/src/main/java/com/plumemu/library/matching/DatFolderNames.kt"

# Playlist / folder names seen in the wild that are not DAT header names.
EXTRA = {
    "Commodore - CD32": "amigacd32",
    "Atari - 8-bit": "atari800",
    "Nintendo - Game & Watch": "gameandwatch",
    "Sinclair - ZX Spectrum +3": "zxspectrum",
    "FBNeo - Arcade Games": "arcade",
}


def build():
    names = {}
    pairs = list(json.loads(SYSTEMS.read_text())["datNames"].items()) + list(EXTRA.items())
    for name, system_id in pairs:
        bucket = names.setdefault(system_id, [])
        for variant in (name, re.sub(r"\s*\([^)]*\)\s*$", "", name)):
            if variant not in bucket:
                bucket.append(variant)
    lines = [
        "package com.plumemu.library.matching",
        "",
        "// Generated from the platform and DAT tables of the build tools; do not edit by hand.",
        "",
        "/** System id to the DAT / playlist names its folder may carry, e.g. `Sony - PlayStation 2`. */",
        "internal val DAT_FOLDER_NAMES: Map<String, List<String>> = mapOf(",
    ]
    for system_id in sorted(names):
        items = ", ".join('"%s"' % n.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") for n in names[system_id])
        lines.append('    "%s" to listOf(%s),' % (system_id, items))
    lines.append(")")
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    text = build()
    if "--check" in sys.argv:
        sys.exit(0 if OUT.exists() and OUT.read_text() == text else 1)
    OUT.write_text(text)
