"""Reads ES-DE's Android es_systems.xml and es_find_rules.xml."""
import re
import xml.etree.ElementTree as ET

from common import (ConvertError, Unsupported, EXTERNAL_STORAGE,
                    INTERNAL_DATA, bits_of, make_launch, split_component)

# Commands using any of these are skipped, with the token as the reason in the summary.
UNSUPPORTED_TOKENS = ("INJECT", "FILEINJECT", "ANDROIDAPP", "GAMEDIRRAW", "ROMPATHRAW",
                      "ROMRAW", "BASENAME", "EXTRAARRAY_")
GAME_VALUES = {"%ROM%": "{path}", "%ROMSAF%": "{uri}", "%ROMPROVIDER%": "{uri}"}
GAME_MODES = {"{path}": "path", "{uri}": "uri", "{vita_title_id}": "vita_title_id"}
FLAG_TOKENS = {"%ACTIVITY_CLEAR_TASK%": "clear_task", "%ACTIVITY_CLEAR_TOP%": "clear_top"}
TOKEN_RE = re.compile(r'(%[A-Za-z0-9_.!-]+%)(?:=("(?:[^"\\]|\\.)*"|\S+))?')
# PS Vita only (ES-DE psvita): the game file is a small text file holding the title ID, and ES-DE
# injects its content as the last item of a string-array extra, e.g. Vita3K's
# %EXTRAARRAY_AppStartParameters%=-r,%INJECT%=%BASENAME%.psvita gives ["-r", "PCSE00000"].
# Other %INJECT% uses (PS3 serials, store ids, command files) stay skipped: each would need its own
# checked format in the app, and a value guessed wrong starts the wrong game or nothing.
VITA_TITLE_ID_RE = re.compile(r"%EXTRAARRAY_([A-Za-z0-9_]+)%=((?:[^,%\s]+,)*)%INJECT%=%BASENAME%\.psvita(?=\s|$)")
LIBRETRO_RE = re.compile(r"^" + re.escape(INTERNAL_DATA) + r"/\{package\}/cores/([^/]+_libretro(?:_android)?\.so)$")


def read_find_rules(path):
    """emulator key (lowercase) -> list of (package, activity or None), in ES-DE's order."""
    rules = {}
    for emulator in ET.parse(path).getroot():
        if emulator.tag != "emulator":
            raise ConvertError(f"es_find_rules.xml: unexpected element <{emulator.tag}>")
        entries = []
        for rule in emulator:
            if rule.tag != "rule" or rule.get("type") != "androidpackage":
                raise ConvertError(f"es_find_rules.xml: unknown rule in {emulator.get('name')}")
            for entry in rule:
                if entry.tag != "entry" or not (entry.text or "").strip():
                    raise ConvertError(f"es_find_rules.xml: bad entry in {emulator.get('name')}")
                entries.append(split_component(entry.text.strip()))
        if not entries:
            raise ConvertError(f"es_find_rules.xml: {emulator.get('name')} has no entries")
        rules[emulator.get("name").lower()] = entries
    return rules


def read_systems(path):
    """List of dicts: name, fullname, extensions, commands [(label, text)]."""
    systems = []
    for node in ET.parse(path).getroot():
        if node.tag != "system":
            raise ConvertError(f"es_systems.xml: unexpected element <{node.tag}>")
        name = node.findtext("name")
        if not name or not node.findtext("fullname"):
            raise ConvertError("es_systems.xml: a system has no name or fullname")
        extensions = {e.lstrip(".").lower() for e in (node.findtext("extension") or "").split()} - {""}
        commands = [(c.get("label") or "", (c.text or "").strip()) for c in node.findall("command")]
        systems.append({"name": name, "fullname": node.findtext("fullname").strip(),
                        "extensions": extensions, "commands": commands})
    return systems


def _substitute(value):
    value = value.strip('"')
    for token, text in GAME_VALUES.items():
        value = value.replace(token, text)
    return (value.replace("%ANDROIDPACKAGE%", "{package}")
                 .replace("%EXTERNALDATA%", EXTERNAL_STORAGE)
                 .replace("%INTERNALDATA%", INTERNAL_DATA))


def _typed(kind, value):
    if kind == "EXTRABOOL":
        if value not in ("true", "false"):
            raise ConvertError(f"boolean extra with value {value!r}")
        return value == "true"
    if kind == "EXTRAINTEGER":
        return int(value)
    return value


def parse_command(text):
    """-> (emulator key, launch dict, core or None). Raises Unsupported for skipped commands."""
    action = category = mime = None
    flags, extras, games = set(), {}, []
    vita = VITA_TITLE_ID_RE.search(text)
    if vita:
        # The prefix items are fixed strings ("-r"); the title ID goes after them.
        prefix = [item for item in vita.group(2).split(",") if item]
        games.append(("extra_array", vita.group(1), "{vita_title_id}", prefix))
        text = (text[:vita.start()] + " " + text[vita.end():]).strip()
    for token in UNSUPPORTED_TOKENS:
        if token in text:
            raise Unsupported(token)
    if text.startswith("PLACEHOLDER"):
        raise Unsupported("PLACEHOLDER")
    emulator = None
    consumed = 0
    for match in TOKEN_RE.finditer(text):
        token, value = match.group(1), match.group(2)
        consumed += len(re.sub(r'\s+', '', match.group(0)))
        name = token.strip("%")
        if name.startswith("EMULATOR_") and value is None:
            emulator = name[len("EMULATOR_"):].lower()
        elif token in FLAG_TOKENS and value is None:
            flags.add(FLAG_TOKENS[token])
        elif name == "ACTION" and value:
            action = _substitute(value)
        elif name == "CATEGORY" and value:
            category = _substitute(value)
        elif name == "MIMETYPE" and value:
            mime = _substitute(value)
        elif name == "DATA" and value:
            games.append(("data", None, _substitute(value), None))
        elif value and re.match(r"EXTRA(BOOL|INTEGER)?_", name):
            kind, _, key = name.partition("_")
            text_value = _substitute(value)
            if kind == "EXTRA" and text_value in ("{path}", "{uri}"):
                games.append(("extra", key, text_value, None))
            else:
                extras[key] = _typed(kind, text_value)
        else:
            raise ConvertError(f"unknown token {token} in command: {text}")
    if emulator is None or consumed != len(re.sub(r"\s+", "", text)):
        raise ConvertError(f"cannot read command: {text}")
    if len(games) != 1 or games[0][2] not in GAME_MODES:
        raise Unsupported("no single game reference")
    target, key, value, prefix = games[0]
    mode = GAME_MODES[value]
    core = None
    libretro = extras.get("LIBRETRO")
    if isinstance(libretro, str):
        match = LIBRETRO_RE.match(libretro)
        if not match:
            raise ConvertError(f"unknown LIBRETRO value {libretro!r}")
        core = match.group(1)
        extras["LIBRETRO"] = INTERNAL_DATA + "/{package}/cores/{core}"
    return emulator, make_launch(action, category, mime, flags, mode, target, key, extras, prefix), core
