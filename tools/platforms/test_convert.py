"""Run with: python3 -I -m unittest discover -s tools/platforms -p 'test_*.py'"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import convert  # noqa: E402
import daijisho  # noqa: E402
import esde  # noqa: E402
import manifest  # noqa: E402
import tables  # noqa: E402
from common import ConvertError, Unsupported  # noqa: E402


class ParserTests(unittest.TestCase):
    def test_unknown_es_de_token_fails_loudly(self):
        with self.assertRaises(ConvertError):
            esde.parse_command("%EMULATOR_FOO% %WHATEVER%=1 %DATA%=%ROMSAF%")

    def test_garbage_between_tokens_fails_loudly(self):
        with self.assertRaises(ConvertError):
            esde.parse_command("%EMULATOR_FOO% hello %DATA%=%ROMSAF%")

    def test_unsupported_commands_are_skipped_not_guessed(self):
        with self.assertRaises(Unsupported):
            esde.parse_command("%EMULATOR_FOO% %EXTRA_x%=%INJECT%=%BASENAME%")

    def test_es_de_data_is_read(self):
        emulator, launch, core = esde.parse_command("%EMULATOR_FOO% %ACTION%=android.intent.action.VIEW %DATA%=%ROMSAF%")
        self.assertEqual("foo", emulator)
        self.assertEqual({"mode": "uri", "target": "data"}, launch["game"])
        self.assertNotIn("mimeType", launch)
        self.assertIsNone(core)

    def test_unknown_daijisho_option_fails_loudly(self):
        with self.assertRaises(ConvertError):
            daijisho.parse_player("-n a.b/.C\n --nonsense x -d {file.uri}")

    def test_daijisho_player_is_read(self):
        package, activity, launch, core = daijisho.parse_player("-n a.b/.C\n -a android.intent.action.VIEW\n -d {file.path}")
        self.assertEqual(("a.b", "a.b.C"), (package, activity))
        self.assertEqual({"mode": "path", "target": "data"}, launch["game"])


class VitaTitleIdTests(unittest.TestCase):
    """PS Vita: the .psvita file's content (the title ID) goes last in a string-array extra."""

    def test_vita3k_command_is_read(self):
        emulator, launch, core = esde.parse_command(
            "%EMULATOR_VITA3K% %EXTRAARRAY_AppStartParameters%=-r,%INJECT%=%BASENAME%.psvita")
        self.assertEqual("vita3k", emulator)
        self.assertEqual({"game": {"mode": "vita_title_id", "target": "extra_array",
                                   "key": "AppStartParameters", "prefix": ["-r"]}}, launch)
        self.assertIsNone(core)

    def test_other_inject_uses_stay_skipped(self):
        for text in ("%EMULATOR_ARMSX3% %ACTION%=android.intent.action.VIEW %EXTRA_title_id%=%INJECT%=%BASENAME%.ps3",
                     "%EMULATOR_X% %EXTRAARRAY_AppStartParameters%=-r,%INJECT%=%BASENAME%.ps3",
                     "%EMULATOR_X% %EXTRAARRAY_AppStartParameters%=-r,%ROM%"):
            with self.assertRaises(Unsupported, msg=text):
                esde.parse_command(text)

    def test_psvita_is_shipped_with_vita3k_first(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        data = convert.convert(sources)[0]
        psvita = next(s for s in data["systems"] if s["id"] == "psvita")
        self.assertEqual(["vita3k", "emucorev"], psvita["emulators"])
        self.assertEqual(["psvita"], psvita["extensions"], "Daijisho's .dpt placeholder is left out")
        for folder in ("psvita", "vita", "ps vita", "playstation vita"):
            self.assertIn(folder, psvita["folders"])
        vita3k = next(e for e in data["emulators"] if e["id"] == "vita3k")
        self.assertEqual(("org.vita3k.emulator", "org.vita3k.emulator.Emulator"), (vita3k["package"], vita3k["activity"]))
        self.assertEqual({"mode": "vita_title_id", "target": "extra_array", "key": "AppStartParameters",
                          "prefix": ["-r"]}, vita3k["game"])
        self.assertIn('<package android:name="org.vita3k.emulator" />', manifest.render(data))


class SteamAppIdTests(unittest.TestCase):
    """Steam: the .steam file's content (the app id) as an Int or String extra, or in a URI template."""

    @staticmethod
    def entry(**game):
        return {"id": "x", "name": "X", "systems": ["steam"], "package": "a.b", "game": game}

    def test_the_three_shapes_are_accepted(self):
        import common
        self.assertEqual({"game": {"mode": "steam_app_id", "target": "extra", "key": "app_id", "type": "int"}},
                         common.hand_launch(self.entry(mode="steam_app_id", target="extra", key="app_id", type="int")))
        self.assertEqual("string", common.hand_launch(
            self.entry(mode="steam_app_id", target="extra", key="steamAppId", type="string"))["game"]["type"])
        self.assertEqual({"game": {"mode": "steam_app_id", "target": "data", "template": "dd://game/{id}"}},
                         common.hand_launch(self.entry(mode="steam_app_id", target="data", template="dd://game/{id}")))

    def test_bad_shapes_fail_loudly(self):
        import common
        bad = [
            dict(mode="steam_app_id", target="extra", key="app_id", type="long"),     # unknown type
            dict(mode="steam_app_id", target="extra", key="app_id"),                  # no type
            dict(mode="steam_app_id", target="extra", type="int"),                    # no key
            dict(mode="steam_app_id", target="extra", key="", type="int"),            # blank key
            dict(mode="steam_app_id", target="data", template="dd://game/"),          # no {id}
            dict(mode="steam_app_id", target="data", template="dd://{id}/{id}"),      # {id} twice
            dict(mode="steam_app_id", target="data"),                                 # no template
            dict(mode="steam_app_id", target="data", key="k", template="dd://{id}"),  # data takes no key
            dict(mode="steam_app_id", target="extra_array", key="k", prefix=["-r"]),  # no array target
            dict(mode="path", target="extra", key="k", type="int"),                   # type on another mode
            dict(mode="uri", target="data", template="x://{id}"),                     # template on another mode
            dict(mode="steam_id", target="extra", key="k", type="int"),               # unknown mode
            dict(mode="steam_app_id", target="extra", key="k", type="int", extra=1),  # unknown field
        ]
        for game in bad:
            with self.subTest(game=game), self.assertRaises(ConvertError):
                common.hand_launch(self.entry(**game))
        with self.assertRaises(ConvertError):
            entry = self.entry(mode="steam_app_id", target="extra", key="k", type="int")
            entry["extras"] = {"bad": 1.5}
            common.hand_launch(entry)
        with self.assertRaises(ConvertError):
            common.hand_launch({"id": "x", "name": "X", "systems": ["steam"],
                                "game": {"mode": "steam_app_id", "target": "extra", "key": "k", "type": "int"}})

    def test_a_hand_emulator_on_a_missing_system_or_a_taken_id_fails_loudly(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        for broken in ({"systems": ["nosuchsystem"]}, {"id": "winnative"}):
            original = dict(tables.HAND_EMULATORS[0])
            tables.HAND_EMULATORS[0].update(broken)
            try:
                with self.subTest(broken=broken), self.assertRaises(ConvertError):
                    convert.convert(sources)
            finally:
                tables.HAND_EMULATORS[0] = original

    def test_accepts_must_name_extensions_of_the_emulators_systems(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        for broken in (["iso"], [], ["Steam"], [".steam"], ["steam", "steam"]):
            original = dict(tables.HAND_EMULATORS[0])
            tables.HAND_EMULATORS[0]["accepts"] = broken
            try:
                with self.subTest(accepts=broken), self.assertRaises(ConvertError):
                    convert.convert(sources)
            finally:
                tables.HAND_EMULATORS[0] = original
        for broken in (["gba"], [], ["steam", "steam"]):
            tables.NOT_ACCEPTED["winnative"] = broken
            try:
                with self.subTest(not_accepted=broken), self.assertRaises(ConvertError):
                    convert.convert(sources)
            finally:
                tables.NOT_ACCEPTED["winnative"] = ["steam"]

    def test_disc_emulators_take_only_the_files_daijisho_lists_for_them(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        data = convert.convert(sources)[0]
        emulators = {e["id"]: e for e in data["emulators"]}
        duckstation = set(emulators["duckstation"]["accepts"])
        self.assertTrue({"m3u", "chd", "pbp", "cue", "bin", "iso"} <= duckstation, duckstation)
        self.assertNotIn("zip", duckstation)
        # Beetle PSX needs a cue sheet: a bare .bin or .iso is not offered to it, an .m3u is.
        beetle = set(emulators["retroarch-mednafen-psx"]["accepts"])
        self.assertTrue({"cue", "m3u", "chd", "pbp"} <= beetle and not {"bin", "iso", "img"} & beetle, beetle)
        # The Citra line has no Azahar compressed files and no .cia; Azahar itself stays undeclared.
        self.assertFalse({"zcci", "zcxi", "z3dsx", "cia"} & set(emulators["citra"]["accepts"]))
        self.assertNotIn("accepts", emulators["azahar"])
        # Undeclared where Daijisho's list is copied or misses a known format.
        for undeclared in ("epsxe", "nethersx2", "ppsspp", "dolphin", "flycast"):
            self.assertNotIn("accepts", emulators[undeclared], undeclared)
        # A Saturn .m3u is offered to Saturn.emu, not to YabaSanshiro's core.
        self.assertIn("m3u", emulators["saturn-emu"]["accepts"])
        self.assertNotIn("m3u", emulators["retroarch-yabasanshiro"]["accepts"])

    def test_accepts_from_daijisho_must_name_the_emulators_own_players(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        broken_cases = (
            ["psx - ePSXe"],  # starts another emulator
            ["psx - No such player"],
            [],  # its system has no source
            ["psx - Duckstation", "ps2 - RetroArch - LRPS2"],  # another emulator, another system
        )
        for broken in broken_cases:
            tables.ACCEPTS_FROM_DAIJISHO["duckstation"] = broken
            try:
                with self.subTest(players=broken), self.assertRaises(ConvertError):
                    convert.convert(sources)
            finally:
                tables.ACCEPTS_FROM_DAIJISHO["duckstation"] = ["psx - Duckstation"]
        tables.ACCEPTS_FROM_DAIJISHO["winnative"] = ["psx - Duckstation"]
        try:
            with self.assertRaises(ConvertError):  # already has its own accepts (NOT_ACCEPTED)
                convert.convert(sources)
        finally:
            del tables.ACCEPTS_FROM_DAIJISHO["winnative"]

    def test_daijisho_accepted_regex_of_another_shape_fails_loudly(self):
        self.assertEqual(["cue", "toc"], daijisho.accepted_extensions("^(.*).(?:cue|toc)$", "x"))
        self.assertEqual(["cue", "toc"], daijisho.accepted_extensions(r"^(.*)\.(?:cue|toc)$", "x"))
        for odd in ("^(?!(?:\\._|\\.).*).*(?<!bin)$", "^(.*).(?:CUE)$", "", None):
            with self.subTest(regex=odd), self.assertRaises(ConvertError):
                daijisho.accepted_extensions(odd, "x")

    def test_a_file_type_no_emulator_of_its_system_accepts_fails(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        data = convert.convert(sources)[0]
        alone = next(s for s in data["systems"] if len(s["emulators"]) == 1 and len(s["extensions"]) >= 2
                     and s["emulators"][0] not in tables.NOT_ACCEPTED and s["emulators"][0] not in tables.ACCEPTS_FROM_DAIJISHO
                     and not any(s["emulators"][0] in o["emulators"] for o in data["systems"] if o is not s))
        emulator_id = alone["emulators"][0]
        # Not zip or 7z: a RetroArch core gets those back (RETROARCH_UNPACKS).
        refused = next(x for x in alone["extensions"] if x not in convert.RETROARCH_UNPACKS)
        tables.NOT_ACCEPTED[emulator_id] = [refused]
        try:
            with self.assertRaises(ConvertError) as caught:
                convert.convert(sources)
            self.assertIn("no emulator of", str(caught.exception))
        finally:
            del tables.NOT_ACCEPTED[emulator_id]

    def test_a_retroarch_core_with_an_accepts_list_also_takes_zip_and_7z(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        data = convert.convert(sources)[0]
        emulators = {e["id"]: e for e in data["emulators"]}
        saturn = next(s for s in data["systems"] if s["id"] == "saturn")
        psx = next(s for s in data["systems"] if s["id"] == "psx")
        self.assertTrue({"zip", "7z"} <= set(emulators["retroarch-mednafen-saturn"]["accepts"]))
        self.assertTrue({"zip", "7z"} <= set(emulators["retroarch-duckstation"]["accepts"]))
        # Standalone emulators keep Daijisho's list: DuckStation does not open a .zip.
        self.assertNotIn("zip", emulators["duckstation"]["accepts"])
        for system in (saturn, psx):
            cores = [emulators[e] for e in system["emulators"] if emulators[e].get("core")]
            for ext in ("zip", "7z"):
                self.assertTrue(any(ext in c.get("accepts", system["extensions"]) for c in cores), (system["id"], ext))

    def test_a_file_type_no_retroarch_core_accepts_any_more_fails(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        original = convert.RETROARCH_UNPACKS
        convert.RETROARCH_UNPACKS = set()  # crafted: without the zip/7z rule every core drops them
        try:
            with self.assertRaises(ConvertError) as caught:
                convert.convert(sources)
            self.assertIn("no RetroArch core of", str(caught.exception))
        finally:
            convert.RETROARCH_UNPACKS = original

    def test_a_system_order_must_name_exactly_the_systems_emulators(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        original = list(tables.SYSTEM_EMULATOR_ORDER["steam"])
        for broken in (original[:-1], original + ["dolphin"]):
            tables.SYSTEM_EMULATOR_ORDER["steam"] = broken
            try:
                with self.subTest(order=broken), self.assertRaises(ConvertError):
                    convert.convert(sources)
            finally:
                tables.SYSTEM_EMULATOR_ORDER["steam"] = original

    def test_steam_is_shipped_with_the_three_apps_before_winnative(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        data = convert.convert(sources)[0]
        steam = next(s for s in data["systems"] if s["id"] == "steam")
        self.assertEqual(["gamenative", "gamehub-lite", "droiddeck", "winnative"], steam["emulators"])
        self.assertIn("steam", steam["extensions"])
        windows = next(s for s in data["systems"] if s["id"] == "windows")
        self.assertNotIn("steam", windows["extensions"], "a .steam file is a Steam game, wherever it is")
        emulators = {e["id"]: e for e in data["emulators"]}
        self.assertEqual({"id": "gamenative", "name": "GameNative", "package": "app.gamenative",
                          "activity": "app.gamenative.MainActivity",
                          "game": {"mode": "steam_app_id", "target": "extra", "key": "app_id", "type": "int"},
                          "action": "app.gamenative.LAUNCH_GAME", "extras": {"game_source": "STEAM"},
                          "accepts": ["steam"]},
                         emulators["gamenative"])
        for app in ("gamehub-lite", "droiddeck"):
            self.assertEqual(["steam"], emulators[app]["accepts"])
        # WinNative opens everything of its systems but an app id file (.epic, .gog, .amazon stay).
        self.assertEqual(["amazon", "app", "desktop", "epic", "gog", "pcgame"], emulators["winnative"]["accepts"])
        # Everything else takes every extension of its systems, but those whose list is read from Daijisho.
        self.assertEqual({"gamenative", "gamehub-lite", "droiddeck", "winnative"} | set(tables.ACCEPTS_FROM_DAIJISHO),
                         {e["id"] for e in data["emulators"] if "accepts" in e})
        hub = emulators["gamehub-lite"]
        self.assertEqual(("gamehub.lite", "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"),
                         (hub["package"], hub["activity"]))
        self.assertEqual([{"package": "emuready.gamehub.lite",
                           "activity": "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"}], hub["variants"])
        self.assertEqual({"mode": "steam_app_id", "target": "extra", "key": "steamAppId", "type": "string"}, hub["game"])
        self.assertEqual(("gamehub.lite.LAUNCH_GAME", {"autoStartGame": True}), (hub["action"], hub["extras"]))
        self.assertEqual({"id": "droiddeck", "name": "DroidDeck", "package": "com.droiddeck.launcher",
                          "game": {"mode": "steam_app_id", "target": "data", "template": "droiddeck://game/{id}"},
                          "action": "android.intent.action.VIEW", "accepts": ["steam"]}, emulators["droiddeck"])
        # Only the Steam apps take an app id, and only from the steam system.
        self.assertEqual({"gamenative", "gamehub-lite", "droiddeck"},
                         {e["id"] for e in data["emulators"] if e["game"]["mode"] == "steam_app_id"})
        rendered = manifest.render(data)
        for package in ("app.gamenative", "gamehub.lite", "emuready.gamehub.lite", "com.droiddeck.launcher"):
            self.assertIn(f'<package android:name="{package}" />', rendered)
        self.assertNotIn("uses-permission", rendered)


class ExtraVariantTests(unittest.TestCase):
    def test_dual_screen_builds_are_variants_of_their_emulator(self):
        # Issue #1: Eden Duo and PPSSPP Duo were listed as Android games, not emulators.
        data = convert.convert(os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources"))[0]
        emulators = {e["id"]: e for e in data["emulators"]}
        self.assertIn({"package": "dev.igawa6.edenduo", "activity": "org.yuzu.yuzu_emu.activities.EmulationActivity"},
                      emulators["eden"]["variants"])
        self.assertIn({"package": "org.ppsspp.ppssppduo", "activity": "org.ppsspp.ppsspp.PpssppActivity"},
                      emulators["ppsspp"]["variants"])
        self.assertTrue(emulators["eden"]["legalRisk"])
        rendered = manifest.render(data)
        for package in ("dev.igawa6.edenduo", "org.ppsspp.ppssppduo"):
            self.assertIn(f'<package android:name="{package}" />', rendered)

    def test_an_extra_variant_of_an_unknown_emulator_or_a_known_package_fails_loudly(self):
        rules = {"ppsspp": [("org.ppsspp.ppsspp", ".PpssppActivity")]}
        with self.assertRaises(ConvertError):
            convert.add_extra_variants(rules, {"nope": [("a.b", None)]})
        with self.assertRaises(ConvertError):
            convert.add_extra_variants(rules, {"ppsspp": [("org.ppsspp.ppsspp", None)]})


class OutputTests(unittest.TestCase):
    def test_extra_extensions_are_added_and_claimed_by_no_other_system(self):
        data = convert.convert(os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources"))[0]
        by_id = {s["id"]: s for s in data["systems"]}
        self.assertTrue({"nsz", "xcz"} <= set(by_id["switch"]["extensions"]))
        self.assertIn("cia", by_id["n3ds"]["extensions"])
        for ext, owner in (("nsz", "switch"), ("xcz", "switch"), ("cia", "n3ds")):
            self.assertEqual([owner], [s["id"] for s in data["systems"] if ext in s["extensions"]], ext)

    def test_two_runs_give_the_same_data(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        first = convert.convert(sources)[0]
        second = convert.convert(sources)[0]
        self.assertEqual(first, second)


class DisplayNameTests(unittest.TestCase):
    def test_maker_is_dropped_only_when_a_name_is_left(self):
        self.assertEqual("Dreamcast", convert.display_name("dreamcast", "Sega Dreamcast"))
        self.assertEqual("Nintendo DS", convert.display_name("nds", "Nintendo DS"))
        self.assertEqual("Atari 2600", convert.display_name("atari2600", "Atari 2600"))
        self.assertEqual("Super Nintendo", convert.display_name("snes", "Nintendo SNES (Super Nintendo)"))

    def test_too_long_name_fails_loudly(self):
        with self.assertRaises(ConvertError):
            convert.display_name("x", "A very long system name indeed")

    def test_order_and_names_in_the_output(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        systems = convert.convert(sources)[0]["systems"]
        self.assertEqual(len(systems), len({s["displayName"] for s in systems}))
        self.assertEqual(list(range(len(systems))), sorted(s["order"] for s in systems))
        first = [s["id"] for s in sorted(systems, key=lambda s: s["order"])][:13]
        self.assertEqual(["snes", "gba", "psx", "psp", "nds", "gc", "megadrive", "nes", "gb", "gbc", "n64",
                          "ps2", "arcade"], first)

    def test_system_colours_are_known_systems_in_hex_and_engines_have_none(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        systems = {s["id"]: s for s in convert.convert(sources)[0]["systems"]}
        self.assertTrue(set(tables.SYSTEM_COLORS) <= set(systems))
        for system_id, color in tables.SYSTEM_COLORS.items():
            self.assertRegex(color, r"^[0-9A-F]{6}$", system_id)
            self.assertEqual("#" + color, systems[system_id]["color"])
        for system_id in ("doom", "quake", "scummvm", "ports", "videos", "arcade", "mame"):
            self.assertNotIn("color", systems[system_id], system_id)

    def test_maker_and_year_for_hardware_and_none_for_engines(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        systems = {s["id"]: s for s in convert.convert(sources)[0]["systems"]}
        for system_id in tables.TESTED - {"arcade"}:
            self.assertIn("maker", systems[system_id], system_id)
            self.assertIn("year", systems[system_id], system_id)
        self.assertEqual(("Nintendo", 1990), (systems["snes"]["maker"], systems["snes"]["year"]))
        self.assertEqual(("Sony", 1994), (systems["psx"]["maker"], systems["psx"]["year"]))
        for system_id in ("arcade", "scummvm", "steam", "pico8"):
            self.assertNotIn("maker", systems[system_id], system_id)
            self.assertNotIn("year", systems[system_id], system_id)
        for maker, year in tables.MAKER_YEAR.values():
            self.assertTrue(maker.strip())
            self.assertTrue(1970 <= year <= 2030, year)

    def test_every_system_with_a_maker_has_a_kind_and_snes_an_end_year(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        systems = {s["id"]: s for s in convert.convert(sources)[0]["systems"]}
        for system_id, system in systems.items():
            has_maker = "maker" in system
            # The download stores are the only ones with a maker and no kind.
            self.assertEqual(has_maker and system_id not in tables.NO_KIND, "kind" in system, system_id)
            self.assertFalse("inProduction" in system and "endYear" in system, system_id)
            if "inProduction" in system:
                self.assertTrue(system["inProduction"] is True and has_maker, system_id)
            if "endYear" in system:
                self.assertTrue(has_maker, system_id)
                self.assertLessEqual(system["year"], system["endYear"], system_id)
        self.assertEqual(("console", 1990, 2003), (systems["snes"]["kind"], systems["snes"]["year"], systems["snes"]["endYear"]))
        self.assertEqual("console", systems["switch"]["kind"])
        self.assertNotIn("endYear", systems["switch"])
        self.assertTrue(systems["switch"]["inProduction"])
        self.assertEqual({"arduboy", "macintosh", "switch", "windows"}, {i for i, s in systems.items() if s.get("inProduction")})
        for system_id in ("nds", "wiiware", "pspminis"):
            self.assertNotIn("inProduction", systems[system_id], system_id)
            self.assertNotIn("endYear", systems[system_id], system_id)
        for system_id in tables.NO_KIND:
            self.assertIn("maker", systems[system_id], system_id)
            self.assertNotIn("kind", systems[system_id], system_id)
        for system_id in ("arcade", "scummvm", "steam", "pico8"):
            self.assertNotIn("kind", systems[system_id], system_id)

    def test_kind_table_mistakes_fail_loudly(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        original = tables.KIND_END_YEAR["snes"]
        try:
            for wrong in (("spaceship", 2003), ("console", 1989), ("console", 2027)):
                tables.KIND_END_YEAR["snes"] = wrong
                with self.assertRaises(ConvertError, msg=str(wrong)):
                    convert.convert(sources)
            tables.KIND_END_YEAR["snes"] = original
            tables.IN_PRODUCTION.add("snes")
            with self.assertRaises(ConvertError):
                convert.convert(sources)
            tables.IN_PRODUCTION.discard("snes")
            del tables.KIND_END_YEAR["snes"]
            with self.assertRaises(ConvertError):
                convert.convert(sources)
        finally:
            tables.KIND_END_YEAR["snes"] = original
            tables.IN_PRODUCTION.discard("snes")

    def test_maker_table_naming_no_system_fails_loudly(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        tables.MAKER_YEAR["nosuchsystem"] = ("Nobody", 2000)
        try:
            with self.assertRaises(ConvertError):
                convert.convert(sources)
        finally:
            del tables.MAKER_YEAR["nosuchsystem"]

    def test_every_system_has_a_short_unique_abbreviation(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        systems = convert.convert(sources)[0]["systems"]
        abbrs = [s["abbr"] for s in systems]
        self.assertEqual(len(systems), len({a.lower() for a in abbrs}))
        self.assertTrue(all(0 < len(a) <= 5 for a in abbrs))
        by_id = {s["id"]: s["abbr"] for s in systems}
        self.assertEqual(["NES", "SNES", "GBA", "PS1", "MD"], [by_id[i] for i in ("nes", "snes", "gba", "psx", "megadrive")])

    def test_every_system_has_a_short_unique_code(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        systems = convert.convert(sources)[0]["systems"]
        codes = [s["code"] for s in systems]
        self.assertEqual(len(systems), len({c.lower() for c in codes}))
        self.assertTrue(all(0 < len(c) <= 3 for c in codes))
        by_id = {s["id"]: s["code"] for s in systems}
        self.assertEqual(["NES", "SNS", "GBA", "PS1", "MD"], [by_id[i] for i in ("nes", "snes", "gba", "psx", "megadrive")])

    def _convert_with_code(self, system_id, value):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        before = tables.CODE.get(system_id)
        tables.CODE[system_id] = value
        try:
            return convert.convert(sources)
        finally:
            if before is None:
                del tables.CODE[system_id]
            else:
                tables.CODE[system_id] = before

    def test_code_naming_no_system_fails_loudly(self):
        with self.assertRaises(ConvertError):
            self._convert_with_code("nosuchsystem", "NOP")

    def test_code_longer_than_three_fails_loudly(self):
        with self.assertRaises(ConvertError):
            self._convert_with_code("nes", "NES1")

    def test_duplicate_code_fails_loudly(self):
        with self.assertRaises(ConvertError):
            self._convert_with_code("nes", "sns")

    def test_system_without_code_fails_loudly(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        before = tables.CODE.pop("nes")
        try:
            with self.assertRaises(ConvertError):
                convert.convert(sources)
        finally:
            tables.CODE["nes"] = before

    def _convert_with_abbreviation(self, system_id, value):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        before = tables.ABBREVIATION.get(system_id)
        tables.ABBREVIATION[system_id] = value
        try:
            return convert.convert(sources)
        finally:
            if before is None:
                del tables.ABBREVIATION[system_id]
            else:
                tables.ABBREVIATION[system_id] = before

    def test_abbreviation_naming_no_system_fails_loudly(self):
        with self.assertRaises(ConvertError):
            self._convert_with_abbreviation("nosuchsystem", "NOPE")

    def test_abbreviation_longer_than_five_fails_loudly(self):
        with self.assertRaises(ConvertError):
            self._convert_with_abbreviation("nes", "NES123")

    def test_duplicate_abbreviation_fails_loudly(self):
        with self.assertRaises(ConvertError):
            self._convert_with_abbreviation("nes", "snes")

    def test_system_without_abbreviation_fails_loudly(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        before = tables.ABBREVIATION.pop("nes")
        try:
            with self.assertRaises(ConvertError):
                convert.convert(sources)
        finally:
            tables.ABBREVIATION["nes"] = before

    def test_measured_systems_carry_their_ratio_and_a_class_that_follows_it(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        systems = {s["id"]: s for s in convert.convert(sources)[0]["systems"]}
        self.assertEqual((1.37, "landscape"), (systems["snes"]["coverRatio"], systems["snes"]["cover"]))
        # Daijisho calls NES and Mega Drive boxes landscape; the measured US boxes are tall.
        self.assertEqual((0.73, "portrait"), (systems["nes"]["coverRatio"], systems["nes"]["cover"]))
        self.assertEqual((0.71, "portrait"), (systems["megadrive"]["coverRatio"], systems["megadrive"]["cover"]))
        self.assertEqual((1.12, "portrait"), (systems["nds"]["coverRatio"], systems["nds"]["cover"]))
        self.assertNotIn("coverRatio", systems["3do"])
        self.assertEqual(set(tables.COVER_RATIOS), {i for i, s in systems.items() if "coverRatio" in s})

    def test_cover_ratio_table_naming_no_system_fails_loudly(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        tables.COVER_RATIOS["nosuchsystem"] = 1.0
        try:
            with self.assertRaises(ConvertError):
                convert.convert(sources)
        finally:
            del tables.COVER_RATIOS["nosuchsystem"]


class EmulatorOrderTests(unittest.TestCase):
    def setUp(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        data = convert.convert(sources)[0]
        self.systems = {s["id"]: s["emulators"] for s in data["systems"]}
        self.cores = {e["id"] for e in data["emulators"] if e.get("core")}

    def test_standalone_emulators_come_before_retroarch_cores(self):
        for system_id, emulators in self.systems.items():
            rest = emulators[1:] if system_id in tables.FIRST_EMULATOR else emulators
            kinds = [e in self.cores for e in rest]
            self.assertEqual(sorted(kinds), kinds, f"{system_id}: {rest}")

    def test_first_emulator_table_still_wins(self):
        for system_id, first in tables.FIRST_EMULATOR.items():
            self.assertEqual(first, self.systems[system_id][0])

    def test_standalones_keep_source_order(self):
        registry = convert.Registry({"a": [("x.a", None)]})
        registry.by_id = {"c1": {"core": "c1.so"}, "s1": {}, "c2": {"core": "c2.so"}, "s2": {}}
        self.assertEqual(["s1", "s2", "c1", "c2"], convert.standalone_first(["c1", "s1", "c2", "s2"], registry))

    def test_three_ds_wii_and_dreamcast_start_with_the_standalone(self):
        self.assertEqual("azahar", self.systems["n3ds"][0])
        self.assertEqual("dolphin", self.systems["wii"][0])
        self.assertEqual("flycast", self.systems["dreamcast"][0])


class ManifestTests(unittest.TestCase):
    def setUp(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        self.data = convert.convert(sources)[0]

    def test_every_emulator_package_and_variant_is_queried(self):
        text = manifest.render(self.data)
        for emulator in self.data["emulators"]:
            for package in [emulator["package"]] + [v["package"] for v in emulator.get("variants", [])]:
                self.assertIn(f'<package android:name="{package}" />', text)

    def test_apps_entry_intent_is_queried_and_no_query_all_packages(self):
        text = manifest.render(self.data)
        self.assertIn('<action android:name="android.intent.action.MAIN" />', text)
        self.assertIn('<category android:name="android.intent.category.LAUNCHER" />', text)
        self.assertNotIn("uses-permission", text)

    def test_packages_are_sorted_and_unique(self):
        packages = manifest.emulator_packages(self.data)
        self.assertEqual(sorted(set(packages)), packages)


class AppsSystemTests(unittest.TestCase):
    def setUp(self):
        sources = os.path.join(os.path.dirname(os.path.abspath(__file__)), "sources")
        self.data = convert.convert(sources)[0]

    def test_android_is_an_apps_system_with_no_files_or_emulators(self):
        android = next(s for s in self.data["systems"] if s["id"] == "android")
        self.assertEqual("apps", android["source"])
        self.assertEqual("Android games", android["displayName"])
        self.assertEqual(([], [], []), (android["folders"], android["extensions"], android["emulators"]))

    def test_no_other_system_is_an_apps_system(self):
        others = [s["id"] for s in self.data["systems"] if s.get("source") and s["id"] not in tables.APPS_SYSTEMS]
        self.assertEqual([], others)

    def test_known_apps_name_existing_systems(self):
        ids = {s["id"] for s in self.data["systems"]}
        for package, system_id in self.data["knownApps"].items():
            self.assertIn(system_id, ids, package)
            self.assertIn(system_id, tables.KNOWN_APP_SYSTEMS, package)

    def test_the_known_apps_table_has_no_package_twice(self):
        # A dict literal silently keeps the last of two equal keys, so read the source text instead.
        import ast
        with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "tables.py"), encoding="utf-8") as handle:
            tree = ast.parse(handle.read())
        for node in tree.body:
            if isinstance(node, ast.Assign) and any(getattr(x, "id", "") == "KNOWN_APPS" for x in node.targets):
                keys = [k.value for k in node.value.keys]
                self.assertEqual(len(keys), len(set(keys)))
                return
        self.fail("KNOWN_APPS not found in tables.py")


if __name__ == "__main__":
    unittest.main()
