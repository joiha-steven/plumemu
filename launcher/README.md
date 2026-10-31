# launcher

Starts games in their emulators, finds installed emulators, picks the emulator of each game, counts
play time, lists apps for the Apps entry. No UI: the game menu and the messages live in `app`.

Namespace: `com.plumemu.launcher`. Depends on `platforms` (data, `LaunchSpec`), `core-data` (game,
`emulator_choice`, `play`), `library` (`EmulatorDefaults`) and kotlinx.coroutines.

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library module; Robolectric unit tests; androidTest for the real launch |
| `src/main/.../Launching.kt` | `Launching.create(context, database, catalog, startObserver, playBattery)`: the one shared set of launcher, choices, detector, play time, apps |
| `src/main/.../GameStartObserver.kt` | `GameStartObserver`: told right before an emulator's `startActivity` and when it throws (the app's second screen); `observedBy` wraps the starter |
| `src/main/.../GameLauncher.kt` | `GameLauncher.launch(gameId, fileOf)`: resolve, check the file, build the Intent, note play time, start; `fileOf` is the copy whose file opens (Other versions), everything recorded on `gameId` |
| `src/test/.../CopyLaunchTest.kt` | A row playing another copy's file: the copy's file, the row's emulator choice, play time and outcomes |
| `src/main/.../LaunchOutcome.kt` | `LaunchOutcome` (Started, EmulatorMissing, FileMissing, BadTitleIdFile, NeedsFilePath, EmulatorNotChosen, NoEmulatorForSystem, Failed), `EmulatorOffer`, `TitleIdProblem` |
| `src/main/.../StorageVolumes.kt` | Whether the SD card or USB drive a game is on is out (explicit removed states only); the launch then says "storage not found" with no Rescan |
| `src/main/.../DocumentProbe.kt` | One two-column provider query: is the file still there, and its MIME type; a second for the folder root when it is not |
| `src/main/.../SmallFileReader.kt` | Reads at most N + 1 bytes of a game file whose content is the launch value (PS Vita title ID); never the whole file |
| `src/main/.../LauncherLog.kt` | The logging seam; ids and exception classes only, never messages, titles, file names or paths |
| `src/main/.../intent/DocumentPaths.kt` | Storage-provider document ids (`primary:`, `home:`, a volume id; read by library's `storage/ExternalStorageIds`) → real paths through `StorageRoots`; anything else has none |
| `src/main/.../intent/GameLocations.kt` | A library game → `GameLocation` (document URI, plus path when there is one) |
| `src/main/.../intent/LaunchIntents.kt` | `LaunchSpec` → `Intent`: component, action, data and type, extras, flags, read grant with ClipData |
| `src/main/.../detect/EmulatorDetector.kt` | `detect(catalog)` (suspend, IO) and the `installed` StateFlow; `InstalledEmulators` |
| `src/main/.../choice/EmulatorPick.kt` | `EmulatorPick`, `ChoiceSource`, the pure resolution order (`EmulatorResolver`) |
| `src/main/.../choice/EmulatorChoices.kt` | Choices stored in `emulator_choice`; the suggested emulator per system refreshed (it starts nothing), a person's choice kept |
| `src/main/.../play/HandOffWatch.kt` | Settles a start's give-up against its hand-off: whichever claims first wins (`GameStarts` in the app, `PlayTimeTracker.handOff`) |
| `src/main/.../play/PlayTimeTracker.kt` | `onGameLaunched`, `onResume` (the Activity's hook), the 10 s rule, `PlaySessionOutcome`; records the total and a `play_session` row ending now and starting the measured time before |
| `src/main/.../play/PlayBattery.kt` | The hook for the battery measured during a play (`onPlayStarting(usualDraw)`, `takeMeasured`); `None` |
| `src/main/.../play/battery/` | Battery use per play: `BatteryReader` / `AndroidBatteryReader`, `BatteryEstimator`, `BatteryTuning`, `PlayBatteryMeter` (`BatteryReads`, `snapshot()` for the in-game part: `BatterySnapshot`, `LevelPoint`), `TimeLeft`, `UsualPace` (a usual draw as the time the charge now lasts and a percent of a full battery an hour) |
| `src/main/.../play/PendingSessionStore.kt` | The pending session on disk (SharedPreferences, `commit()`), survives process death |
| `src/main/.../apps/InstalledApps.kt` | Launchable apps by label without Plumemu, each with Android's game mark (`isGame`: category from API 26, `FLAG_IS_GAME` below); launching one (told to the start observer, and to the battery meter as a play with no usual pace; any start error is `Failed`); `startGameApp(package)` for an app listed as a game (its launcher activity, `Missing` when gone, battery left to the play's note) |
| `src/main/.../apps/AppIcons.kt` | An app's launcher icon as a square bitmap for the Apps grid, drawn on an I/O thread and kept in an LRU cache bounded by bytes; `cached` answers what is in memory without loading (a tile scrolled back in); a failure is remembered until the package's next `invalidate`; `generation` goes up at every `invalidate` so a grid asks again; `None` (logged once) when there is no usable icon |
| `src/test/.../TestSupport.kt`, `FakePackages.kt` | Shipped and small test catalogs, in-memory database, fake installed apps |
| `src/test/.../intent/LaunchIntentsTest.kt` | The Intent of the first emulator of each tested system, path and URI-only folders |
| `src/test/.../intent/DocumentPathsTest.kt` | The path mapping table (a user's storage roots) |
| `src/test/.../intent/DocumentPathsUserTest.kt` | Paths from Android's answers on API 23 and 34: a second user's internal storage, a volume Android does not list |
| `src/test/.../detect/EmulatorDetectorCacheTest.kt` | `current` reuses the last detection until `packagesChanged` |
| `src/test/.../detect/EmulatorDetectorTest.kt` | Detection with Robolectric's PackageManager, API 23 and 34 |
| `src/test/.../choice/EmulatorResolverTest.kt`, `EmulatorChoicesTest.kt` | Resolution order, legal risk, uninstalled choice, stored defaults |
| `src/test/.../GameStartObserverTest.kt`, `LaunchingTest.kt` | The observer hears right before the start, a start that throws, nothing for a launch that stops earlier or is cancelled; and through the real `Launching.create` (also an Apps entry app's start and failure) |
| `src/test/.../play/PlayTimeTrackerBatteryTest.kt` | The battery figures stored with their session, taken once |
| `src/test/.../play/battery/` | `UsualPaceTest`, `BatteryEstimatorTest` (made-up plays, `SyntheticPlay`), `ThorTraceReplayTest` (the Thor's real trace in `src/test/resources/battery/`), `PlayBatteryMeterSnapshotTest` (what the in-game part reads: usual pace, full charge, Android's exact time to full, the level points) |
| `src/test/.../play/PlayHandOffTest.kt` | A cancelled start leaves no half-recorded play (`handOff`, `cancelPending` from a cancelled caller), a `HandOffWatch` given up before the hand-off or refused after it, a given-up start that keeps the next start's note |
| `src/test/.../play/PlayTimeTrackerTest.kt` | Under 10 s, process death, double resume, reboot (boot count), time jumps (the stored session spans the measured time), closed database, game gone |
| `src/test/.../GameLauncherTest.kt` | Every `LaunchOutcome`, database and start errors, a chosen RetroArch core, no file name or path in any log line |
| `src/test/.../VitaLaunchTest.kt` | PS Vita with the shipped data: the exact Vita3K Intent from a `.psvita` file, empty and invalid files, a missing or unreadable file, other emulators never read the file |
| `src/test/.../SmallFileReaderTest.kt` | A short file is read whole, a large one is cut after N + 1 bytes, a failing provider is `Unreadable` |
| `src/test/.../LaunchingTest.kt` | A choice made through the facade is the one its launcher uses |
| `src/test/.../DocumentProbeTest.kt` | The probe against a provider that answers, is empty, says gone, refuses, lost its root (card removed) |
| `src/test/.../apps/InstalledAppsTest.kt` | Sorting, Plumemu left out, launch, a vanished app, a refusal and any other start error (logged by package and class) |
| `src/test/.../apps/InstalledAppsGameTest.kt` | The game mark on API 23 and 34, `startGameApp`: the package's launcher activity, missing or uninstalled, a throwing start, no battery call |
| `src/test/.../apps/InstalledAppsBatteryTest.kt` | An app after a game, and after another app, starts its own battery play: no usual pace, no old level points, nothing for its first minute |
| `src/test/.../apps/AppIconsTest.kt` | Off the caller's thread, size, cache hit, `cached` (memory only), a remembered failure, the generation, invalidation, missing icon logged once, byte-bound eviction, the cap formula |
| `src/test/.../QueriesManifestTest.kt` | The generated `<queries>` list every emulator package and MAIN/LAUNCHER, no permission |
| `src/androidTest/.../RealLaunchTest.kt` | A real launch on an emulator into fake DuckStation (URI + grant) and fake RetroArch (path) |
| `src/androidTest/.../FakeDocumentsProvider.kt`, `HostActivity.kt`, `AndroidManifest.xml` | One fake game behind a documents provider; keeps the test app in front |
| `src/androidTest/fake-emulator/` | `build.sh` builds a fake emulator APK under any package/activity of the data; it reports the Intent it got (to the launcher's test APK, or to another package given as a fourth argument) and may stay open for a given time (fifth argument; app's `EndToEndTest` uses both) |
