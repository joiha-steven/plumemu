#!/usr/bin/env python3
"""Converts the Daijishō and ES-DE snapshots into platforms/src/main/assets/platforms.json.

Usage: python3 -I tools/platforms/convert.py [--out PATH]
Standard library only. Output is deterministic. See README.md for the merge rules.
"""
import argparse
import collections
import json
import os
import re
import sys

sys.dont_write_bytecode = True  # no __pycache__ in the working tree
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import daijisho  # noqa: E402
import esde  # noqa: E402
import manifest  # noqa: E402
import tables  # noqa: E402
from common import (ConvertError, Unsupported, check_accepts, check_game, hand_launch, launch_signature, slug,  # noqa: E402
                    split_component)

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "..", "platforms", "src", "main", "assets", "platforms.json")
DEFAULT_MANIFEST_OUT = os.path.join(HERE, "..", "..", "platforms", "src", "main", "AndroidManifest.xml")
RETROARCH_UNPACKS = {"zip", "7z"}
CORE_SUFFIX = re.compile(r"_libretro(_android)?\.so$")


class Registry:
    """All emulators, with the rules that decide when two definitions are the same emulator."""

    def __init__(self, find_rules):
        self.rules = find_rules
        self.by_id = {}
        self.signatures = {}

    def _clean_name(self, label, fallback):
        label = re.sub(r"\s*\(Standalone\)|\s*\[[^\]]*\]", "", label).strip()
        return label or fallback

    def _record(self, emulator_id, name, entries, launch, core):
        (package, activity), variants = entries[0], entries[1:]
        record = {"id": emulator_id, "name": name, "package": package}
        if activity:
            record["activity"] = activity
        from common import bits_of
        if bits_of(package):
            record["bits"] = bits_of(package)
        if variants:
            record["variants"] = [
                {k: v for k, v in (("package", p), ("activity", a), ("bits", bits_of(p))) if v}
                for p, a in variants]
        if core:
            record["core"] = core
        record.update(launch)
        return record

    def add_esde(self, key, label, launch, core):
        if key not in self.rules:
            raise ConvertError(f"es_systems.xml uses emulator {key!r} with no find rule")
        if core:
            emulator_id = "retroarch-" + slug(CORE_SUFFIX.sub("", core))
            name = "RetroArch: " + self._clean_name(label, core)
        else:
            emulator_id, name = slug(key), self._clean_name(label, key)
        signature = launch_signature(launch)
        emulator_id = self._free_id(emulator_id, signature)
        if emulator_id not in self.by_id:
            self.by_id[emulator_id] = self._record(emulator_id, name, self.rules[key], launch, core)
            self.signatures[emulator_id] = signature
        return emulator_id

    def _free_id(self, emulator_id, signature):
        """Same id and same launch is the same emulator; same id with another launch gets -2, -3 ..."""
        candidate, number = emulator_id, 1
        while candidate in self.by_id and self.signatures[candidate] != signature:
            number += 1
            candidate = f"{emulator_id}-{number}"
        return candidate

    def add_hand(self, entry):
        """A hand-kept emulator (tables.HAND_EMULATORS): its own id, never merged with a source's."""
        launch = hand_launch(entry)
        emulator_id = entry["id"]
        if emulator_id in self.by_id or slug(emulator_id) != emulator_id:
            raise ConvertError(f"HAND_EMULATORS {emulator_id!r}: the id is taken by a source or not a plain id")
        entries = [split_component(entry["package"] + ("/" + entry["activity"] if entry.get("activity") else ""))]
        entries += [split_component(p + ("/" + a if a else "")) for p, a in entry.get("variants", ())]
        self.by_id[emulator_id] = self._record(emulator_id, entry["name"], entries, launch, None)
        self.signatures[emulator_id] = launch_signature(launch)
        if "accepts" in entry:
            self.by_id[emulator_id]["accepts"] = entry["accepts"]
        return emulator_id

    def esde_packages(self):
        return {p for entries in self.rules.values() for p, _ in entries}

    def add_daijisho(self, player_name, system_id, package, activity, launch, core):
        if core:
            retroarch = self.rules["retroarch"]
            emulator_id = "retroarch-" + slug(CORE_SUFFIX.sub("", core))
            if emulator_id not in self.by_id:
                self.by_id[emulator_id] = self._record(
                    emulator_id, "RetroArch: " + player_name.rsplit(" - ", 1)[-1], retroarch, launch, core)
                self.signatures[emulator_id] = launch_signature(launch)
            return emulator_id
        emulator_id = slug(package)
        name = self._clean_name(player_name.split(" - ", 1)[-1], package)
        signature = launch_signature(launch)
        emulator_id = self._free_id(emulator_id, signature)
        if emulator_id not in self.by_id:
            self.by_id[emulator_id] = self._record(emulator_id, name, [(package, activity)], launch, None)
            self.signatures[emulator_id] = signature
        return emulator_id


def add_extra_variants(rules, extra):
    """Appends tables.EXTRA_VARIANTS to the find rules; an unknown key or a package already listed fails."""
    for key, entries in extra.items():
        if key not in rules:
            raise ConvertError(f"EXTRA_VARIANTS {key!r}: es_find_rules.xml has no such emulator")
        known = {p for found in rules.values() for p, _ in found}
        for package, activity in entries:
            if package in known:
                raise ConvertError(f"EXTRA_VARIANTS {key!r}: {package} is already in es_find_rules.xml")
            rules[key].append((package, activity))


def convert(sources):
    rules = esde.read_find_rules(os.path.join(sources, "esde", "es_find_rules.xml"))
    add_extra_variants(rules, tables.EXTRA_VARIANTS)
    es_systems = esde.read_systems(os.path.join(sources, "esde", "es_systems.xml"))
    dj_platforms = daijisho.read_platforms(os.path.join(sources, "daijisho"))
    registry = Registry(rules)
    skipped = collections.Counter()
    # Each Daijisho player by name: what it starts and which files it takes (tables.ACCEPTS_FROM_DAIJISHO).
    dj_players = collections.defaultdict(list)
    systems = {}

    def system(system_id):
        return systems.setdefault(system_id, {
            "id": system_id, "names": [], "folders": [system_id], "extensions": set(),
            "box_art": None, "emulators": []})

    for es in sorted(es_systems, key=lambda s: s["name"]):
        target = system(tables.ES_MERGE.get(es["name"], es["name"]))
        if es["name"] == target["id"]:
            target["names"].insert(0, es["fullname"])
        if es["name"] not in target["folders"]:
            target["folders"].append(es["name"])
        target["extensions"] |= es["extensions"]
        for label, text in es["commands"]:
            try:
                key, launch, core = esde.parse_command(text)
            except Unsupported as why:
                skipped["ES-DE: " + str(why)] += 1
                continue
            emulator_id = registry.add_esde(key, label, launch, core)
            if emulator_id not in target["emulators"]:
                target["emulators"].append(emulator_id)

    es_packages = registry.esde_packages()
    for dj in sorted(dj_platforms, key=lambda p: p["id"]):
        target = system(tables.DJ_TO_ES.get(dj["id"], dj["id"]))
        tables_added = [dj["id"], dj["shortname"]]
        target["folders"] += [f for f in tables_added if f not in target["folders"]]
        target["names"].append(dj["name"])
        target["extensions"] |= dj["extensions"]
        if dj["boxArt"] in tables.LANDSCAPE_BOX_ART_IDS:
            target["box_art"] = "landscape"
        for player_name, text in dj["players"]:
            try:
                package, activity, launch, core = daijisho.parse_player(text)
            except Unsupported as why:
                skipped["Daijishō: " + str(why)] += 1
                continue
            dj_players[player_name].append({"system": target["id"], "package": package, "activity": activity,
                                            "core": core, "regex": dj["accepted"].get(player_name)})
            if package in es_packages and not core:
                skipped["Daijishō: package already defined by ES-DE"] += 1
                continue
            emulator_id = registry.add_daijisho(player_name, target["id"], package, activity, launch, core)
            if emulator_id not in target["emulators"]:
                target["emulators"].append(emulator_id)

    for entry in tables.HAND_EMULATORS:
        emulator_id = registry.add_hand(entry)
        for system_id in entry["systems"]:
            if system_id not in systems:
                raise ConvertError(f"HAND_EMULATORS {emulator_id!r} lists {system_id}, which is not a system")
            if emulator_id not in systems[system_id]["emulators"]:
                systems[system_id]["emulators"].append(emulator_id)

    for system_id, name in tables.APPS_SYSTEMS.items():
        # ES-DE has an `android` system of .apk files that no emulator launches (it was always dropped);
        # the installed-apps system takes its id. Any other source system under that id would be a clash.
        if system_id in systems and systems[system_id]["emulators"]:
            raise ConvertError(f"APPS_SYSTEMS lists {system_id}, which a source gives with emulators")
        systems[system_id] = {"id": system_id, "names": [name], "folders": [], "extensions": set(),
                              "box_art": None, "emulators": [], "apps": True}
    return finish(systems, registry, skipped, dj_players)


def short_name(system_id, name):
    """Short display name: hand table, else the name without brackets and maker prefix."""
    if system_id in tables.SHORT_NAMES:
        short = tables.SHORT_NAMES[system_id]
    elif system_id in tables.SHORT_NAME_OVERRIDES:
        short = tables.SHORT_NAME_OVERRIDES[system_id]
    else:
        short = re.sub(r"\s*\(.*?\)", "", name).replace(" - ", " ").strip()
        for prefix in tables.MAKER_PREFIXES:
            if short.startswith(prefix) and len(short) - len(prefix) >= 3:
                short = short[len(prefix):]
                break
    if len(short) > tables.MAX_SHORT_NAME or len(short) > len(name):
        raise ConvertError(f"short name {short!r} of {system_id} is too long: add it to SHORT_NAME_OVERRIDES")
    if short == system_id.upper() and len(short) > 4 and system_id not in tables.SHORT_NAME_OVERRIDES:
        raise ConvertError(f"short name of {system_id} is just its id: add it to SHORT_NAME_OVERRIDES")
    return short


def display_name(system_id, name):
    """Name people use: hand table, else the name without brackets and maker (unless the maker is part of it)."""
    if system_id in tables.DISPLAY_NAMES:
        shown = tables.DISPLAY_NAMES[system_id]
    else:
        shown = re.sub(r"\s*\(.*?\)", "", name).replace(" - ", " ").strip()
        if system_id not in tables.KEEP_MAKER:
            for prefix in tables.DISPLAY_MAKER_PREFIXES:
                rest = shown[len(prefix):]
                if shown.startswith(prefix) and len(rest) >= 3 and not rest[0].isdigit():
                    shown = rest
                    break
    if not shown.strip() or len(shown) > tables.MAX_DISPLAY_NAME:
        raise ConvertError(f"display name {shown!r} of {system_id} is empty or longer than "
                           f"{tables.MAX_DISPLAY_NAME}: add it to DISPLAY_NAMES")
    return shown


def cover_of(system_id, box_art):
    """A measured system's class follows its ratio; the others take Daijisho's, portrait when it has none."""
    ratio = tables.COVER_RATIOS.get(system_id)
    if ratio is not None:
        return "landscape" if ratio >= tables.LANDSCAPE_MIN_RATIO else "portrait"
    return box_art or "portrait"


def standalone_first(emulator_ids, registry):
    """Every standalone emulator before every RetroArch core, each group in source order.

    A RetroArch core needs RetroArch opened once to download it (its private storage), a standalone
    is ready after install (owner plan docs/plans/2026-10-21-emulator-setup.md, Risks). ES-DE often
    lists the core first (3DS, Wii, Dreamcast, N64, DS), which made the automatic pick RetroArch
    even with the standalone installed. FIRST_EMULATOR, applied after this, may still put a core first.
    """
    standalone = [e for e in emulator_ids if not registry.by_id[e].get("core")]
    cores = [e for e in emulator_ids if registry.by_id[e].get("core")]
    return standalone + cores


def accepts_from_daijisho(emulator_id, player_names, registry, dj_players, out_systems):
    """The files [emulator_id] opens, read from the acceptedFilenameRegex of the Daijisho players that
    start it, one or more for each of its systems; within its systems' extensions."""
    record = registry.by_id[emulator_id]
    builds = {(record["package"], record.get("activity"))}
    builds |= {(v["package"], v.get("activity")) for v in record.get("variants", ())}
    its_systems = {s["id"]: s for s in out_systems if emulator_id in s["emulators"]}
    accepts, covered = set(), set()
    for name in player_names:
        found = dj_players.get(name, [])
        if len(found) != 1:
            raise ConvertError(f"ACCEPTS_FROM_DAIJISHO {emulator_id!r}: {len(found)} Daijisho players are named {name!r}")
        player = found[0]
        starts_it = (player["core"] == record.get("core")) if record.get("core") else (
            player["core"] is None and (player["package"], player["activity"]) in builds)
        if not starts_it:
            raise ConvertError(f"ACCEPTS_FROM_DAIJISHO {emulator_id!r}: player {name!r} starts another emulator")
        system = its_systems.get(player["system"])
        if system is None:
            raise ConvertError(f"ACCEPTS_FROM_DAIJISHO {emulator_id!r}: player {name!r} is of {player['system']}, "
                               f"not one of its systems")
        listed = daijisho.accepted_extensions(player["regex"], f"Daijisho player {name!r}")
        accepts |= set(listed) & set(system["extensions"])
        covered.add(system["id"])
    if covered != set(its_systems):
        raise ConvertError(f"ACCEPTS_FROM_DAIJISHO {emulator_id!r} names no player for {sorted(set(its_systems) - covered)}: "
                           f"files of a system without a source would be refused by guess")
    return sorted(accepts)


def finish(systems, registry, skipped, dj_players=None):
    out_systems, dropped = [], []
    for system_id, extra in sorted(tables.EXTRA_EXTENSIONS.items()):
        for other_id, other in sorted(systems.items()):
            claimed = extra & other["extensions"]
            if other_id != system_id and claimed:
                raise ConvertError(f"EXTRA_EXTENSIONS gives {sorted(claimed)} to {system_id}, which {other_id} lists too")
    for system_id in sorted(systems):
        s = systems[system_id]
        if not s.get("apps") and (not s["emulators"] or not s["extensions"] - tables.EXCLUDED_EXTENSIONS.get(system_id, set())):
            # No launchable emulator, or games that are not files (store ids): nothing to scan.
            dropped.append(system_id)
            continue
        s["emulators"] = standalone_first(s["emulators"], registry)
        order = tables.SYSTEM_EMULATOR_ORDER.get(system_id)
        if order is not None:
            if sorted(order) != sorted(s["emulators"]):
                raise ConvertError(f"SYSTEM_EMULATOR_ORDER of {system_id} must name exactly {sorted(s['emulators'])}, not {order}")
            s["emulators"] = list(order)
        first = tables.FIRST_EMULATOR.get(system_id)
        if first:
            if first not in s["emulators"]:
                raise ConvertError(f"FIRST_EMULATOR says {first} for {system_id}, which does not list it")
            s["emulators"].remove(first)
            s["emulators"].insert(0, first)
        folders = []
        for f in ([] if s.get("apps") else s["folders"] + tables.EXTRA_FOLDERS.get(system_id, [])):
            if f.lower() not in folders:
                folders.append(f.lower())
        out_systems.append({
            "id": system_id, "name": s["names"][0] if s["names"] else system_id,
            "shortName": short_name(system_id, s["names"][0] if s["names"] else system_id),
            "displayName": display_name(system_id, s["names"][0] if s["names"] else system_id),
            "folders": folders,
            "extensions": sorted((s["extensions"] | tables.EXTRA_EXTENSIONS.get(system_id, set()))
                                 - tables.EXCLUDED_EXTENSIONS.get(system_id, set())),
            "cover": cover_of(system_id, s["box_art"]),
            "tier": "tested" if system_id in tables.TESTED else "untested",
            "emulators": s["emulators"]})
        if s.get("apps"):
            out_systems[-1]["source"] = "apps"
        if system_id in tables.ABBREVIATION:
            out_systems[-1]["abbr"] = tables.ABBREVIATION[system_id]
        if system_id in tables.CODE:
            out_systems[-1]["code"] = tables.CODE[system_id]
        made = tables.MAKER_YEAR.get(system_id)
        if made:
            # Optional keys: left out for systems without a maker (engines, stores, emulators).
            out_systems[-1]["maker"], out_systems[-1]["year"] = made
            if system_id not in tables.NO_KIND:
                if system_id not in tables.KIND_END_YEAR:
                    raise ConvertError(f"{system_id} has a maker and year but no KIND_END_YEAR (tables.py)")
                kind, end_year = tables.KIND_END_YEAR[system_id]
                out_systems[-1]["kind"] = kind
                if end_year is not None:
                    out_systems[-1]["endYear"] = end_year
                if system_id in tables.IN_PRODUCTION:
                    out_systems[-1]["inProduction"] = True
        if system_id in tables.SYSTEM_COLORS:
            # Optional: the accent of Accent color > By system; systems without one use the person's own accent.
            out_systems[-1]["color"] = "#" + tables.SYSTEM_COLORS[system_id]
        if system_id in tables.COVER_RATIOS:
            # Optional: only measured systems carry one; the loader gives the others their class's.
            out_systems[-1]["coverRatio"] = tables.COVER_RATIOS[system_id]
    shown_by = {}
    for system in out_systems:
        other = shown_by.setdefault(system["displayName"], system["id"])
        if other != system["id"]:
            raise ConvertError(f"display name {system['displayName']!r} belongs to both {other} and {system['id']}")
    ids = {system["id"] for system in out_systems}
    for system_id in tables.COVER_RATIOS:
        if system_id not in ids:
            raise ConvertError(f"COVER_RATIOS lists {system_id}, which is not a system")
    for system_id in tables.MAKER_YEAR:
        if system_id not in ids:
            raise ConvertError(f"MAKER_YEAR lists {system_id}, which is not a system")
    if set(tables.KIND_END_YEAR) != set(tables.MAKER_YEAR) - tables.NO_KIND:
        raise ConvertError("KIND_END_YEAR must list the systems of MAKER_YEAR except NO_KIND: "
                           + ", ".join(sorted(set(tables.KIND_END_YEAR) ^ (set(tables.MAKER_YEAR) - tables.NO_KIND))))
    for system_id in tables.IN_PRODUCTION:
        if system_id not in tables.KIND_END_YEAR or tables.KIND_END_YEAR[system_id][1] is not None:
            raise ConvertError(f"IN_PRODUCTION lists {system_id}, which has no KIND_END_YEAR entry or has an end year")
    for system_id, (kind, end_year) in tables.KIND_END_YEAR.items():
        if kind not in tables.KINDS:
            raise ConvertError(f"KIND_END_YEAR gives {system_id} the kind {kind!r}, which is not one of {tables.KINDS}")
        if end_year is not None and not tables.MAKER_YEAR[system_id][1] <= end_year <= tables.LAST_END_YEAR:
            raise ConvertError(f"KIND_END_YEAR gives {system_id} the end year {end_year}, outside "
                               f"{tables.MAKER_YEAR[system_id][1]}..{tables.LAST_END_YEAR}")
    for system_id in tables.ABBREVIATION:
        if system_id not in ids:
            raise ConvertError(f"ABBREVIATION lists {system_id}, which is not a system")
    abbr_of = {}
    for system in out_systems:
        abbr = system.get("abbr")
        if not abbr:
            raise ConvertError(f"{system['id']} has no abbreviation (ABBREVIATION in tables.py)")
        if len(abbr) > tables.MAX_ABBREVIATION:
            raise ConvertError(f"abbreviation {abbr!r} of {system['id']} is longer than {tables.MAX_ABBREVIATION}")
        other = abbr_of.setdefault(abbr.lower(), system["id"])
        if other != system["id"]:
            raise ConvertError(f"abbreviation {abbr!r} belongs to both {other} and {system['id']}")
    for system_id in tables.CODE:
        if system_id not in ids:
            raise ConvertError(f"CODE lists {system_id}, which is not a system")
    code_of = {}
    for system in out_systems:
        code = system.get("code")
        if not code:
            raise ConvertError(f"{system['id']} has no code (CODE in tables.py)")
        if len(code) > tables.CODE_LENGTH:
            raise ConvertError(f"code {code!r} of {system['id']} is longer than {tables.CODE_LENGTH}")
        other = code_of.setdefault(code.lower(), system["id"])
        if other != system["id"]:
            raise ConvertError(f"code {code!r} belongs to both {other} and {system['id']}")
    for first in tables.FIRST_ORDER:
        if first not in ids:
            raise ConvertError(f"FIRST_ORDER lists {first}, which is not a system")
    rank = {system_id: place for place, system_id in enumerate(tables.FIRST_ORDER)}
    ordered = sorted(out_systems, key=lambda x: (x["id"] not in rank, rank.get(x["id"], 0),
                                                 x["displayName"].lower(), x["id"]))
    for place, system in enumerate(ordered):
        system["order"] = place
    owner = {}
    for system in out_systems:
        for folder in system["folders"]:
            if owner.setdefault(folder, system["id"]) != system["id"]:
                raise ConvertError(f"folder {folder!r} belongs to both {owner[folder]} and {system['id']}")
    for system_id in tables.SYSTEM_EMULATOR_ORDER:
        if system_id not in ids:
            raise ConvertError(f"SYSTEM_EMULATOR_ORDER lists {system_id}, which is not a system")
    used = {e for s in out_systems for e in s["emulators"]}
    for emulator_id, refused in tables.NOT_ACCEPTED.items():
        if emulator_id not in used:
            raise ConvertError(f"NOT_ACCEPTED names {emulator_id}, which no system lists")
        extensions = sorted({x for s in out_systems if emulator_id in s["emulators"] for x in s["extensions"]})
        # Checked like an accepts list: what is refused must be extensions of its systems.
        check_accepts(emulator_id + " (NOT_ACCEPTED)", refused, set(extensions))
        accepts = [x for x in extensions if x not in refused]
        if not accepts:
            raise ConvertError(f"NOT_ACCEPTED leaves {emulator_id} nothing to open")
        registry.by_id[emulator_id]["accepts"] = accepts
    for emulator_id, player_names in tables.ACCEPTS_FROM_DAIJISHO.items():
        if emulator_id not in used:
            raise ConvertError(f"ACCEPTS_FROM_DAIJISHO names {emulator_id}, which no system lists")
        if "accepts" in registry.by_id[emulator_id]:
            raise ConvertError(f"ACCEPTS_FROM_DAIJISHO names {emulator_id}, which already says what it accepts")
        registry.by_id[emulator_id]["accepts"] = accepts_from_daijisho(
            emulator_id, player_names, registry, dj_players or {}, out_systems)
    for emulator_id, extensions in tables.GAME_PATH_EXTENSIONS.items():
        if emulator_id not in used:
            raise ConvertError(f"GAME_PATH_EXTENSIONS names {emulator_id}, which no system lists")
        game = registry.by_id[emulator_id]["game"]
        game["pathExtensions"] = list(extensions)
        check_game(game, f"GAME_PATH_EXTENSIONS {emulator_id!r}")
    for emulator_id in sorted(used):
        record = registry.by_id[emulator_id]
        if record.get("core") and record.get("accepts") is not None:
            # RetroArch unpacks .zip and .7z itself before it hands the file to the core, so a core's own
            # list (Daijisho's) is not the whole of what works through RetroArch.
            extensions = {x for s in out_systems if emulator_id in s["emulators"] for x in s["extensions"]}
            record["accepts"] = sorted(set(record["accepts"]) | (RETROARCH_UNPACKS & extensions))
    for emulator_id in sorted(used):
        accepts = registry.by_id[emulator_id].get("accepts")
        if accepts is not None:
            extensions = {x for s in out_systems if emulator_id in s["emulators"] for x in s["extensions"]}
            check_accepts(emulator_id, accepts, extensions)
    for system in out_systems:
        # A file type no emulator of its system opens would be scanned and then have nothing to start it:
        # an accepts list that leaves one is incomplete, not a fact about the file.
        opened = set()
        for emulator_id in system["emulators"]:
            accepts = registry.by_id[emulator_id].get("accepts")
            opened |= set(system["extensions"]) if accepts is None else set(accepts)
        orphans = sorted(set(system["extensions"]) - opened)
        if orphans:
            raise ConvertError(f"no emulator of {system['id']} accepts {orphans}")
        # Before any accepts list every RetroArch core of a system opened all of its file types; one that
        # no core opens now is a file type lost to someone who has only RetroArch.
        cores = [e for e in system["emulators"] if registry.by_id[e].get("core")]
        if cores:
            by_cores = set()
            for emulator_id in cores:
                accepts = registry.by_id[emulator_id].get("accepts")
                by_cores |= set(system["extensions"]) if accepts is None else set(accepts)
            lost = sorted(set(system["extensions"]) - by_cores)
            if lost:
                raise ConvertError(f"no RetroArch core of {system['id']} accepts {lost}")
    risky = {e for s in out_systems if s["id"] in tables.LEGAL_RISK_SYSTEMS for e in s["emulators"]}
    emulators = []
    for emulator_id in sorted(used):
        record = registry.by_id[emulator_id]
        if emulator_id in risky:
            record["legalRisk"] = True
        emulators.append(record)
    package_name = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
    for package, system_id in tables.KNOWN_APPS.items():
        if not package_name.match(package):
            raise ConvertError(f"KNOWN_APPS package {package!r} is not a package name")
        if system_id not in ids:
            raise ConvertError(f"KNOWN_APPS gives {package} to {system_id}, which is not a system")
        if system_id not in tables.KNOWN_APP_SYSTEMS:
            raise ConvertError(f"KNOWN_APPS gives {package} to {system_id}, not one of {sorted(tables.KNOWN_APP_SYSTEMS)}")
    data = {"formatVersion": tables.FORMAT_VERSION, "systems": out_systems, "emulators": emulators}
    if tables.KNOWN_APPS:
        data["knownApps"] = dict(sorted(tables.KNOWN_APPS.items()))
    return data, skipped, dropped


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default=DEFAULT_OUT)
    parser.add_argument("--manifest-out", default=DEFAULT_MANIFEST_OUT)
    args = parser.parse_args()
    try:
        data, skipped, dropped = convert(os.path.join(HERE, "sources"))
    except ConvertError as error:
        print(f"convert.py: {error}", file=sys.stderr)
        return 1
    # ASCII with \u escapes: the repo guard allows English-only files, and JSON readers decode them.
    text = json.dumps(data, ensure_ascii=True, separators=(",", ":"))
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text + "\n")
    queries = manifest.render(data)
    os.makedirs(os.path.dirname(os.path.abspath(args.manifest_out)), exist_ok=True)
    with open(args.manifest_out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(queries)
    tested = sum(1 for s in data["systems"] if s["tier"] == "tested")
    print(f"systems: {len(data['systems'])} ({tested} tested, {len(data['systems']) - tested} untested)")
    print(f"emulators: {len(data['emulators'])} ({len(tables.HAND_EMULATORS)} hand-kept: "
          f"{', '.join(e['id'] for e in tables.HAND_EMULATORS)})")
    print(f"dropped systems with no launchable emulator or no file extensions ({len(dropped)}): {', '.join(dropped)}")
    for reason, count in sorted(skipped.items()):
        print(f"skipped commands: {count} x {reason}")
    print(f"wrote {args.out} ({len(text.encode('utf-8')) + 1} bytes)")
    print(f"wrote {args.manifest_out} ({len(manifest.emulator_packages(data))} emulator packages in <queries>)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
