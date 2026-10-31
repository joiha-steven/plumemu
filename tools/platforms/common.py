"""Shared pieces of the platform data converter: errors, launch shape, name rules."""
import re

PACKAGE_RE = re.compile(r"^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$")
EXTERNAL_STORAGE = "/storage/emulated/0"
INTERNAL_DATA = "/data/data"


class ConvertError(Exception):
    """The sources contain something this converter does not understand."""


class Unsupported(Exception):
    """One emulator command uses a feature Plumemu does not launch; the command is skipped."""


def slug(text):
    out = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    if not out:
        raise ConvertError(f"cannot make an id from {text!r}")
    return out


def split_component(component):
    """'pkg/.Activity' or 'pkg/full.Activity' or 'pkg' -> (package, activity or None)."""
    package, _, activity = component.partition("/")
    if not PACKAGE_RE.match(package):
        raise ConvertError(f"invalid package name {package!r} in {component!r}")
    if not activity:
        return package, None
    if activity.startswith("."):
        activity = package + activity
    return package, activity


def bits_of(package):
    if package == "com.retroarch.aarch64":
        return 64
    if package == "com.retroarch.ra32":
        return 32
    return None


def make_launch(action, category, mime, flags, mode, target, key, extras, prefix=None, id_type=None, template=None):
    """The part of an emulator that says how an Intent is filled; keys omitted when empty.

    [prefix] is only for target "extra_array": the fixed strings before the game in that array.
    [id_type] and [template] are only for mode "steam_app_id": the extra's type ("int" or "string")
    with target "extra", the data with "{id}" in it with target "data".
    """
    launch = {"game": {"mode": mode, "target": target}}
    if key is not None:
        launch["game"]["key"] = key
    if prefix:
        launch["game"]["prefix"] = list(prefix)
    if id_type is not None:
        launch["game"]["type"] = id_type
    if template is not None:
        launch["game"]["template"] = template
    if action:
        launch["action"] = action
    if category:
        launch["category"] = category
    if mime:
        launch["mimeType"] = mime
    if flags:
        launch["flags"] = sorted(flags)
    if extras:
        launch["extras"] = dict(sorted(extras.items()))
    return launch


GAME_MODES = ("path", "uri", "vita_title_id", "steam_app_id")
GAME_TARGETS = ("data", "extra", "extra_array")
ID_TYPES = ("int", "string")
ID_PLACEHOLDER = "{id}"
GAME_FIELDS = {"mode", "target", "key", "prefix", "type", "template", "pathExtensions"}


def check_game(game, where):
    """Raises ConvertError unless [game] is a launch the app reads (platforms CatalogBuilder's rules)."""
    unknown = set(game) - GAME_FIELDS
    if unknown:
        raise ConvertError(f"{where}: unknown game fields {sorted(unknown)}")
    mode, target, key = game.get("mode"), game.get("target"), game.get("key")
    if mode not in GAME_MODES:
        raise ConvertError(f"{where}: unknown game mode {mode!r}")
    if target not in GAME_TARGETS:
        raise ConvertError(f"{where}: unknown game target {target!r}")
    if (target != "data") != bool(key):
        raise ConvertError(f"{where}: target {target} {'needs' if target != 'data' else 'takes no'} key")
    if ("prefix" in game) and target != "extra_array":
        raise ConvertError(f"{where}: prefix only for target extra_array")
    if (mode == "vita_title_id") != (target == "extra_array"):
        raise ConvertError(f"{where}: mode {mode} with target {target}")
    id_type, template = game.get("type"), game.get("template")
    if mode == "steam_app_id" and target == "extra":
        if id_type not in ID_TYPES:
            raise ConvertError(f"{where}: steam_app_id in an extra needs type int or string, not {id_type!r}")
    elif id_type is not None:
        raise ConvertError(f"{where}: type only for mode steam_app_id with target extra")
    if mode == "steam_app_id" and target == "data":
        if not isinstance(template, str) or template.count(ID_PLACEHOLDER) != 1:
            raise ConvertError(f"{where}: steam_app_id in the data needs a template with {ID_PLACEHOLDER} once, not {template!r}")
    elif template is not None:
        raise ConvertError(f"{where}: template only for mode steam_app_id with target data")
    path_extensions = game.get("pathExtensions")
    if path_extensions is not None:
        if mode != "uri":
            raise ConvertError(f"{where}: pathExtensions only for mode uri")
        if not isinstance(path_extensions, list) or not path_extensions or not all(
                isinstance(x, str) and EXTENSION_RE.match(x) for x in path_extensions):
            raise ConvertError(f"{where}: pathExtensions must be a list of lowercase extensions without the dot")
    if mode == "steam_app_id" and target not in ("extra", "data"):
        raise ConvertError(f"{where}: mode {mode} with target {target}")


def hand_launch(entry):
    """The launch part of a hand-kept emulator (tables.HAND_EMULATORS), checked; raises ConvertError."""
    where = f"HAND_EMULATORS {entry.get('id')!r}"
    known = {"id", "name", "systems", "package", "activity", "variants", "action", "category", "mimeType",
             "flags", "game", "extras", "accepts"}
    unknown = set(entry) - known
    if unknown:
        raise ConvertError(f"{where}: unknown fields {sorted(unknown)}")
    for field in ("id", "name", "package"):
        if not isinstance(entry.get(field), str) or not entry[field].strip():
            raise ConvertError(f"{where}: {field} is missing")
    if not entry.get("systems"):
        raise ConvertError(f"{where}: lists no system")
    game = entry.get("game")
    if not isinstance(game, dict):
        raise ConvertError(f"{where}: game is missing")
    check_game(game, where)
    extras = entry.get("extras", {})
    for name, value in extras.items():
        # bool first: in Python True is an int too.
        if not (isinstance(value, bool) or isinstance(value, (int, str))):
            raise ConvertError(f"{where}: extra {name} has a value of type {type(value).__name__}")
    return make_launch(entry.get("action"), entry.get("category"), entry.get("mimeType"), set(entry.get("flags", ())),
                       game["mode"], game["target"], game.get("key"), extras, game.get("prefix"),
                       game.get("type"), game.get("template"))


EXTENSION_RE = re.compile(r"^[a-z0-9][a-z0-9._+-]*$")


def check_accepts(emulator_id, accepts, system_extensions):
    """Raises ConvertError unless [accepts] is a non-empty list of distinct lowercase extensions, each
    one an extension of one of the emulator's systems ([system_extensions])."""
    where = f"accepts of {emulator_id!r}"
    if not isinstance(accepts, list) or not accepts:
        raise ConvertError(f"{where}: must be a non-empty list, not {accepts!r}")
    if len(set(accepts)) != len(accepts):
        raise ConvertError(f"{where}: lists an extension twice: {accepts}")
    for extension in accepts:
        if not isinstance(extension, str) or not EXTENSION_RE.match(extension):
            raise ConvertError(f"{where}: {extension!r} is not a lowercase extension without the dot")
        if extension not in system_extensions:
            raise ConvertError(f"{where}: {extension!r} is no extension of its systems")


def launch_signature(launch):
    import json
    return json.dumps(launch, sort_keys=True)
