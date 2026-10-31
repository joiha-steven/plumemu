# tools/platforms

Builds `platforms/src/main/assets/platforms.json` (the format is the one `platforms/README.md` describes) from
snapshots of two open-source projects. Python 3, standard library only. Run by hand when the
snapshots change; the output is committed.

```
python3 -I tools/platforms/convert.py            # rewrites the asset and the <queries> manifest, prints a summary
python3 -I -m unittest discover -s tools/platforms -p 'test_*.py'   # converter unit tests
```

The summary lists systems (tested / untested), emulators, dropped systems and every skipped
command with its reason. Output is deterministic: the same snapshots give the same bytes. The
JVM test `ConverterTest` runs the converter and compares with the committed asset.

## Files

| File | What it holds |
|---|---|
| `convert.py` | Merge and write; the registry that decides which emulator entries are the same |
| `manifest.py` | Writes `platforms/src/main/AndroidManifest.xml`: `<queries>` for every emulator package and the MAIN/LAUNCHER intent |
| `esde.py` | Reads ES-DE `es_systems.xml` and `es_find_rules.xml`; parses one command |
| `daijisho.py` | Reads Daijishō `platforms/*.json`; parses one `amStartArguments` |
| `gen_folder_names.py` | Writes `library/.../matching/DatFolderNames.kt` (folder names like `Sony - PlayStation 2`) from the name tool's system list; `--check` fails when it is stale (run by `scripts/verify.sh`) |
| `tables.py` | Hand-kept decisions: tested systems, `SYSTEM_COLORS` (each system's own colour, `#RRGGBB`, written as `color`; adding a system means adding its colour, or leaving it out when it has none: engines, frontends, stores and generic folders), aliases, extra folders, excluded and extra extensions, short names, hand-kept emulators (the Steam apps), extra package names of a source's emulator (`EXTRA_VARIANTS`) |
| `common.py` | Errors, package-name check, shape of the launch part, the check of a hand-kept emulator's launch |
| `test_convert.py` | Converter unit tests |
| `sources/daijisho/`, `sources/esde/` | The snapshots, each with `LICENSE` and `SOURCE.md` (URL, commit, date) |

## Merge rules

1. **Systems.** One system per ES-DE `name` (that is also the folder name ES-DE expects). ES-DE
   names that are only a region or variant (`sfc`, `famicom`, `megadrivejp`, `genesis`, `segacd`,
   `tg16`, ...; list `ES_MERGE`) fold into the main system and add their name as a folder.
   Daijishō platforms join the ES-DE system of the same id, or the one named in `DJ_TO_ES`
   (`genesis` to `megadrive`, `3ds` to `n3ds`, ...); a Daijishō platform with no ES-DE
   counterpart becomes a system of its own.
2. **Folders.** The system id, ES-DE names folded in, Daijishō `uniqueId` and `shortname`,
   then `EXTRA_FOLDERS` (the tested systems; `psvita`: `ps vita`, `playstationvita`, `playstation vita`; the
   MinUI/Onion tags and stock firmware names `sms`, `gg`, `pce`, `ps`). Daijishō has no folder field, so its two ids are
   the folder names it implies. The converter fails if a folder belongs to two systems.
3. **Extensions.** Union of ES-DE `<extension>` and the "Supported extensions" of Daijishō
   players, lowercase, no dot. Words that are not file types ("folder path") are ignored, and so are
   the extensions of `EXCLUDED_EXTENSIONS` (`psvita`: Daijishō's `.dpt` placeholder, whose title ID
   lives in Daijishō's own database; `n64`: `.ndd`, a 64DD disk, which belongs to `n64dd`;
   `windows`: `.steam`, which ES-DE lists for GameNative commands Plumemu skips, so a `.steam` file
   is a Steam game in any folder). Then
   `EXTRA_EXTENSIONS` are added, game files the sources do not list (`switch`: `.nsz`, `.xcz`;
   `n3ds`: `.cia`; identify step S5a); the converter fails if another system lists one of them.
4. **Emulators.** ES-DE commands are the base: package and activity come from
   `es_find_rules.xml` (first entry preferred, the rest are `variants`), the Intent from the
   command. `EXTRA_VARIANTS` in `tables.py` adds builds ES-DE's find rules miss (Eden Duo, PPSSPP Duo)
   after its entries, as variants with the same launch; the converter fails if the find-rule key is
   unknown or the package is already listed. A Daijishō player is added only when its package is not defined by ES-DE at all
   (RetroArch players are matched by core instead); ES-DE wins on conflicts. Daijishō's own
   Intent (`amStartArguments`) is used for the players it adds.
5. **Order.** ES-DE's own command order per system, then the Daijishō additions. Then, for every
   system, every standalone emulator moves before every RetroArch core, each group keeping that
   order (`standalone_first`): a core needs RetroArch opened once to fetch it, a standalone is ready
   after install. ES-DE lists the core
   first for 3DS, Wii, Dreamcast, N64, DS and about fifty others. Last, for the tested systems one
   emulator is moved to the front (`FIRST_EMULATOR` in `tables.py`): nes
   `mesen`, snes `snes9x`, gb and gbc `gambatte`, gba `mgba`, megadrive `genesis-plus-gx`, arcade
   `mamearcade` (all RetroArch cores on purpose), psx DuckStation, psp PPSSPP, ps2 NetherSX2, gc
   Dolphin. n64 and nds get their first standalone (Mupen64Plus FZ, melonDS). The plan's "ready to
   play wins over a first-run step" among standalones has no data yet, so source order decides.
6. **Same id, different launch.** The second definition gets `-2`, `-3`, ... (APS3E and the
   Dolphin forks have per-system differences).
7. **Cover.** `landscape` when Daijishō's `boxArtAspectRatioId` is 1, 3 or 4 (N64, NES, SNES
   style boxes), else `portrait`. A system in `COVER_RATIOS` (box fronts measured on 2026-10-23,
   one US box each) gets `coverRatio` and its class from it (landscape from 1.2), so NES and Mega
   Drive are portrait.
8. **Tier.** `tested` for the ids in `TESTED`, `untested` for everything else.
9. **Short name.** Hand table for the tested systems, `SHORT_NAME_OVERRIDES` for long names, else
   the full name without brackets and maker prefix ("Nintendo Wii" gives "Wii"). The converter fails
   if a short name is longer than 16 characters or than the name, or is just the upper-cased id.
10. **Display name and order.** `displayName` is the hand table `DISPLAY_NAMES` (tested systems and
    long or odd names), else the name without brackets and maker prefix, keeping the maker when `KEEP_MAKER`
    lists the id or what is left is under 3 characters or starts with a digit. The converter fails if one is
    empty, over 22 characters, or shared by two systems. `order` counts from 0: `FIRST_ORDER` (the tested
    systems), then every other system alphabetically by `displayName`.
    `maker` and `year` (optional, both or neither) come from the hand table `MAKER_YEAR`: who made the
    system and the year it first went on sale anywhere, from well-known facts, for hardware and operating
    systems only; engines, fantasy consoles, stores, players and multi-system emulators have none and sort
    last under Maker & year. The converter fails if the table names a system that is not in the data.
    `kind` and `endYear` (optional, only for systems with a maker and year) come from the hand table
    `KIND_END_YEAR` (same ids as `MAKER_YEAR`, a Wikipedia page named in each entry's comment): `kind` is one
    of `console`, `handheld`, `arcade`, `computer`, `add-on`, `os`; `endYear` is the year its maker stopped
    making it, left out where no reliable year exists (the comment says why); `IN_PRODUCTION` lists the systems
    still made, which get `inProduction: true` instead. `NO_KIND` (the download stores) keep maker and year and get
    no kind. The
    converter fails if the two tables list different systems, a kind is unknown, or an end year is before
    the year or after `LAST_END_YEAR`.
    `abbr` (every system) comes from the hand table `ABBREVIATION`: the short name players say (NES, SNES,
    GBA, PS1, MD), at most `MAX_ABBREVIATION` (5) characters, unique ignoring case; shown in Recent, Favorites and
    Search. The converter fails for a system without one, an id that is not a system, a longer one or a duplicate.
    `code` (every system) comes from the hand table `CODE`: three characters at most (`CODE_LENGTH`), unique ignoring
    case; the home screen's left edge shows it when Settings › Appearance › Left edge is System codes. Same failures as `abbr`.
11. **Legal risk.** Every emulator used by a system in `LEGAL_RISK_SYSTEMS` (Switch) gets
    `legalRisk: true` (Switch emulators carry a legal-risk marker the app shows).
12. **MIME type.** None unless a source sets one. ES-DE's launch code (`es-app/src/FileData.cpp` around
    line 2299) only forwards an explicit `%MIMETYPE%`; its docs (INSTALL-DEV.md line 1883) say Android
    supplies a default.
13. **Game value.** `%ROM%`, `{file.path}` mean `path`; `%ROMSAF%`, `%ROMPROVIDER%`,
   `{file.uri}` mean `uri` (a document URI with a read grant).
14. **PS Vita title ID.** ES-DE's `psvita` games are small `.psvita` text files holding the title
   ID (PCSE00000); its Vita3K command `%EXTRAARRAY_AppStartParameters%=-r,%INJECT%=%BASENAME%.psvita`
   injects that content as the last item of a `String[]` extra. It becomes `game` `{"mode":
   "vita_title_id", "target": "extra_array", "key": "AppStartParameters", "prefix": ["-r"]}`; the
   launcher reads the file (at most 65 bytes: `VitaTitleId.MAX_FILE_BYTES` + 1, to tell a longer file) and checks the format before anything starts.
   Only this exact form is read (`VITA_TITLE_ID_RE` in `esde.py`); every other `%INJECT%` stays skipped.
15. **Hand-kept emulators.** `HAND_EMULATORS` in `tables.py` holds emulators no source gives in a
   form the converter reads: the Steam apps GameNative, GameHub Lite (variant `emuready.gamehub.lite`)
   and DroidDeck (added 2026-10-23; the launch Intents come from a comparison of the other launchers). ES-DE's GameNative and GameHub
   Lite commands are `%INJECT%=%ROM%` (skipped like every other `%INJECT%`); DroidDeck is in neither
   source. Each entry is checked by `common.hand_launch` (the app's own rules: known mode and
   target, a key for an extra, a `type` of `int` or `string`, a `template` holding `{id}` once,
   extras of string, boolean or integer), gets its own id (the converter fails if a source already
   gives it, or a listed system does not exist) and is added to its systems after the sources'
   emulators, in table order. Their `game` is `{"mode": "steam_app_id", ...}`: the Steam app id
   written in the game's `.steam` file, in an `int` or `string` extra or in the data through a
   template:
   `{"mode": "steam_app_id", "target": "extra", "key": "app_id", "type": "int"}` (GameNative),
   `{"mode": "steam_app_id", "target": "extra", "key": "steamAppId", "type": "string"}` (GameHub Lite),
   `{"mode": "steam_app_id", "target": "data", "template": "droiddeck://game/{id}"}` (DroidDeck).
   The launcher reads the file (at most 1025 bytes: `SteamAppId.MAX_FILE_BYTES` + 1).
16. **File types.** An emulator's optional `accepts` names the extensions it can open: a hand-kept
   emulator's own field; for a source's, `NOT_ACCEPTED` in `tables.py` names what it cannot open and
   `accepts` is every other extension of its systems (WinNative: all but `steam`, so `amazon`, `app`,
   `desktop`, `epic`, `gog`, `pcgame`). `ACCEPTS_FROM_DAIJISHO` names, for an emulator, the Daijisho
   players (one or more for each of its systems) whose `acceptedFilenameRegex` is its list: `accepts` is
   their extensions within its systems' (`daijisho.accepted_extensions` reads only the `^(.*).(?:a|b)$`
   shape; a player that starts another emulator, or a system left without a player, fails). Only where
   Daijisho's list is the emulator's own; the comment in `tables.py` lists what was left out and why.
   A RetroArch core with an `accepts` list always also takes `zip` and `7z` (RetroArch unpacks them itself;
   `convert.RETROARCH_UNPACKS`).
   After all lists are set, every extension of a system must be accepted by one of its emulators, and
   every extension of a system with RetroArch cores by one of those cores..
   The converter fails unless it is a non-empty list of distinct lowercase extensions, each of one
   of the emulator's systems (`common.check_accepts`). Absent: every extension of its systems.
17. **Whole order.** `SYSTEM_EMULATOR_ORDER` replaces a system's emulator order and must name exactly
   its emulators (steam: GameNative, GameHub Lite, DroidDeck, WinNative).

## What is skipped (never guessed)

A command that needs something Plumemu does not provide is skipped and counted: values
built from the game name or a store id (`%INJECT%` other than the PS Vita title ID, `%BASENAME%`, Daijishō `{tags.*}`), MAME4droid
command lines with per-system arguments (`%GAMEDIRRAW%`, `%ROMPATHRAW%`), launching an Android app
(`%ANDROIDAPP%`), array extras other than the PS Vita one, and commands with no single game reference. A system left with no
emulator or no file extension is dropped. Any token or option the converter has never seen stops
the run with an error: add support for it deliberately.

## Update the snapshots

Fetch the two repositories again (sparse checkout of `platforms/` and
`resources/systems/android/`), copy the files over, update the commit and date in each
`SOURCE.md` (keep the non-ASCII to XML character references note in `esde/SOURCE.md`), run the
converter, read the summary, run `./gradlew :platforms:testDebugUnitTest`, and fix the hand-written
expectations in `ExpectedLaunches.kt` for anything that changed on purpose.
