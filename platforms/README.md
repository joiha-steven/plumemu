# platforms

System and emulator definitions (Plumemu's own JSON converted from Daijishō and ES-DE) and, later, their network updates.

Namespace: `com.plumemu.platforms`. Depends on `kotlinx.serialization` only. It holds the data, the loader and the launch specs; the Android Intent is built in `launcher`.

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library module; serialization plugin; reruns tests when the asset or converter changes |
| `src/main/assets/platforms.json` | The data, written by a Python converter in the build tools; never edit by hand |
| `src/main/AndroidManifest.xml` | `<queries>` for every emulator package and MAIN/LAUNCHER, written by the converter; the manifest merger carries it into the app; never edit by hand |
| `Models.kt` | `GameSystem` (with `displayName`, `order`, the optional `maker`, `year`, `kind` (`SystemKind`), `endYear` and `color` (Int 0xRRGGBB, the system's own colour for Accent color › By system), and `coverRatio`, measured or its `CoverShape` class's default), `Emulator`, `PlatformCatalog` (with `knownApps`, package name to system id) and their small types; `GameSystem.fromInstalledApps` marks the folderless, emulatorless `android` system (file field `source: "apps"`) |
| `RawData.kt` | The file's shape for kotlinx.serialization (strings, not yet checked) |
| `PlatformLoader.kt` | `PlatformLoader.load(text or stream, source)`: parse, check, log, return `PlatformLoadResult` |
| `CatalogBuilder.kt` | The checks: unique ids, dangling emulators, empty lists, valid values, unique folders |
| `PlatformFailure.kt` | The typed failures (file, field, id) and `PlatformLoadResult` |
| `PlatformLog.kt` | The one logging seam (`android.util.Log` in the app, a recorder in tests) |
| `LaunchSpec.kt` | `LaunchSpec`, `GameLocation`, `LaunchResult`, `LaunchSpecs.build` |
| `VitaTitleId.kt` | A PS Vita title ID read from a `.psvita` file (`parse`, `isEmptyFile`, `MAX_FILE_BYTES`); the format check |
| `SteamAppId.kt` | A Steam app id read from a `.steam` file (`parse`, `isEmptyFile`, `MAX_FILE_BYTES`): digits, 1 to Int max |
| `LaunchFileText.kt` | The text of such a small launch file: UTF-8, UTF-16 by its byte order mark, NULs dropped |

## Tests (`src/test/java/com/plumemu/platforms/`)

| File | What it proves |
|---|---|
| `ShippedDataTest.kt` | The shipped file loads, has 100+ systems, tested systems have emulators, measured cover ratios and the class defaults, short names, display names and order of the tested systems (hand-written), unique and short, a unique 3-character code for every system, Switch is legal risk, PS Vita ships with Vita3K and its folder names, hardware has its maker and year and engines none, hardware has its kind and end year (SNES 1990-2003, Switch none) |
| `LaunchSpecTest.kt` | Every emulator of the tested systems builds exactly the expected spec; path/URI refusals; Vita3K's title ID array extra; the title ID format |
| `ExpectedLaunches.kt`, `ExpectedLaunchesA/B/C.kt`, `ExpectedBuilder.kt` | Expected specs, generated once from the raw sources by a throwaway script (field values independent of the converter; entry order is not) |
| `OrderTest.kt` | The first emulator of each tested system, a hand-written list |
| `BrokenDataTest.kt` | Invalid JSON, missing field, duplicates, dangling id, unknown version, bad values (a cover ratio out of range or against its class too), blank or duplicate display name, duplicate order are rejected with one log line |
| `ConverterTest.kt` | The converter's output equals the committed asset and the committed `<queries>` manifest byte for byte |
| `RecordingLog.kt` | Test double for the log seam |
