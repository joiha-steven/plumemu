"""Reads Daijishō platform files (platforms/*.json)."""
import glob
import json
import os
import re
import shlex

from common import ConvertError, Unsupported, make_launch, split_component

FLAGS = {"--activity-clear-task": "clear_task", "--activity-clear-top": "clear_top",
         "--activity-no-history": "no_history"}
GAME = {"{file.path}": "{path}", "{file.uri}": "{uri}"}
RETROARCH_PACKAGES = ("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
EXTENSIONS_RE = re.compile(r"Supported extensions:\s*([^.]*)\.")
# A player's acceptedFilenameRegex in its usual shape: "^(.*).(?:cue|chd|m3u)$" (the dot escaped or not).
ACCEPTED_RE = re.compile(r"^\^\(\.\*\)\\?\.\(\?:([a-z0-9]+(?:\|[a-z0-9]+)*)\)\$$")


def read_platforms(folder):
    """List of dicts: id, shortname, name, boxArt, extensions, players [(name, text)], accepted {name: regex}."""
    platforms = []
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        data = json.load(open(path, encoding="utf-8"))
        try:
            platform = data["platform"]
            extensions = set()
            players = []
            accepted = {}
            for player in data["playerList"]:
                match = EXTENSIONS_RE.search(player.get("description") or "")
                if match:
                    extensions.update(_extensions(match.group(1)))
                players.append((player["name"], player["amStartArguments"]))
                if player.get("acceptedFilenameRegex"):
                    accepted[player["name"]] = player["acceptedFilenameRegex"]
            platforms.append({"id": platform["uniqueId"], "shortname": platform["shortname"],
                              "name": platform["name"], "boxArt": platform["boxArtAspectRatioId"],
                              "extensions": extensions, "players": players,
                              "accepted": accepted})
        except KeyError as missing:
            raise ConvertError(f"{path}: missing field {missing}")
    return platforms


def accepted_extensions(regex, where):
    """The extensions of a player's acceptedFilenameRegex; ConvertError for any other shape, never a guess."""
    match = ACCEPTED_RE.match(regex or "")
    if not match:
        raise ConvertError(f"{where}: acceptedFilenameRegex {regex!r} is not ^(.*).(?:ext|ext)$")
    return match.group(1).split("|")


def _extensions(text):
    """'a, b, and c' -> {a, b, c}. Words such as 'folder path' describe how a game is found, not a file type."""
    found = set()
    for part in text.split(","):
        part = part.strip().lower()
        part = part[4:] if part.startswith("and ") else part
        if part and re.fullmatch(r"[a-z0-9_+-]+", part):
            found.add(part)
    return found


def parse_player(text):
    """-> (package, activity, launch dict, core or None). Raises Unsupported when skipped."""
    tokens = shlex.split(text)
    component = action = category = mime = None
    flags, extras, games = set(), {}, []
    i = 0
    while i < len(tokens):
        t = tokens[i]
        if t in FLAGS:
            flags.add(FLAGS[t])
            i += 1
            continue
        if i + 1 >= len(tokens) and t.startswith("-"):
            raise ConvertError(f"option without value in: {text}")
        v = tokens[i + 1] if i + 1 < len(tokens) else None
        if t == "-n":
            component = v
        elif t == "-a":
            action = v
        elif t == "-c":
            category = v
        elif t == "-t":
            mime = v
        elif t == "-d":
            games.append(("data", None, v))
        elif t in ("-e", "--es", "--ez", "--ei"):
            if i + 2 >= len(tokens):
                raise ConvertError(f"extra without value in: {text}")
            value = tokens[i + 2]
            i += 1
            if "{tags." in value or "{file.mime" in value:
                raise Unsupported("tags")
            if value in GAME:
                games.append(("extra", v, value))
            elif t == "--ez":
                extras[v] = {"true": True, "false": False, "1": True, "0": False}[value]
            elif t == "--ei":
                extras[v] = int(value)
            else:
                extras[v] = value
        elif t in ("--esa", "-f") or t.startswith("-r,"):
            raise Unsupported(t)
        else:
            raise ConvertError(f"unknown option {t} in: {text}")
        i += 2
    if component is None:
        raise ConvertError(f"no component in: {text}")
    package, activity = split_component(component)
    if any("{tags." in str(v) or "{file.mime" in str(v) for v in list(extras.values()) + [g[2] for g in games]):
        raise Unsupported("tags")
    games = [(t, k, GAME.get(v)) for t, k, v in games]
    if len(games) != 1 or games[0][2] is None:
        raise Unsupported("no single game reference")
    target, key, value = games[0]
    mode = "path" if value == "{path}" else "uri"
    core = None
    if package in RETROARCH_PACKAGES:
        if "LIBRETRO" not in extras:
            raise ConvertError(f"RetroArch without LIBRETRO in: {text}")
        core = os.path.basename(extras["LIBRETRO"])
        if "_libretro" not in core:
            core += "_libretro_android.so"
        extras["LIBRETRO"] = "/data/data/{package}/cores/{core}"
        if "CONFIGFILE" in extras:
            extras["CONFIGFILE"] = "/storage/emulated/0/Android/data/{package}/files/retroarch.cfg"
    return package, activity, make_launch(action, category, mime, flags, mode, target, key, extras), core
