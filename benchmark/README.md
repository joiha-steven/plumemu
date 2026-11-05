# benchmark

Test-only module, **never shipped**: Macrobenchmark and Baseline Profile generation against
`:app` (allowed list). A
`com.android.test` module that targets `:app` and runs on a connected emulator or device.

Emulator numbers are marked as such, and are not Thor numbers.

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | `com.android.test` module, target `:app`, self-instrumenting, the Baseline Profile plugin; `suppressErrors=EMULATOR` so it runs on emulators |
| `src/main/AndroidManifest.xml` | `<queries>` for `com.plumemu` (package visibility on Android 11+) |
| `src/main/java/com/plumemu/benchmark/HomeJourneys.kt` | Shared steps: cold start to the main screen (optionally with 10,000 games), open the first system's list, hold D-pad down, fling; walk Settings, open and close a game's menu, type a search |
| `src/main/java/com/plumemu/benchmark/StartupBenchmark.kt` | Cold start to the first frame, no compilation and with the Baseline Profile (`StartupTimingMetric`) |
| `src/main/java/com/plumemu/benchmark/ScrollBenchmark.kt` | 10,000 games: D-pad down held 10 s, and 8 flings (`FrameTimingMetric`) |
| `src/main/java/com/plumemu/benchmark/SystemSwitchBenchmark.kt` | 10,000 games: 16 systems down and back up by D-pad, and by R1/L1 with the games focused, one press every 250 ms (`FrameTimingMetric`, recomposition and layout trace sections) |
| `src/main/java/com/plumemu/benchmark/GridSizeBenchmark.kt` | Nintendo DS grid (88 games, every system a grid, generated covers via the `COVERS` extra): eight L1/R1 size steps, and six R2 then six L2 system switches, one press per 1.2 s (`FrameTimingMetric`). Run `scripts/bench-device.sh <label> GridSizeBenchmark#sizeSteps`; the `plumemu.skip` argument `sync` measures the way before background builds and kept covers |
| `src/main/java/com/plumemu/benchmark/SecondScreenBenchmark.kt` | The second screen's cost on the top screen: D-pad down held 10 s and the 16-system D-pad walk, each with the window off and on (`FrameTimingMetric`). Needs `adb shell settings put global overlay_display_devices 1240x1080/369` on the emulator first; the "on" runs pass the launch extra `com.plumemu.benchmark.SECOND_SCREEN` (`startHome(secondScreen = true)`), honoured only in benchmark builds |
| `src/main/java/com/plumemu/benchmark/BaselineProfileGenerator.kt` | Two journeys for the Baseline Profile: startup and list scrolling (also the startup profile); Settings, the game menu and search. Setup cannot be walked: benchmark builds always show the stand-in content |

## Builds it uses

The Baseline Profile plugin creates two build types in `:app` from release; `app/build.gradle.kts`
makes both test-only: not debuggable, signed with the debug key (never the release key), and
the only builds where `BuildConfig.BENCHMARK_CONTENT` is true, so the 10,000-game intent extra
works there and nowhere else.

- `benchmarkRelease`: R8 on (minify and resource shrinking); what the benchmarks measure.
- `nonMinifiedRelease`: what the profile is generated on.

## Run

One emulator or device at a time (`ANDROID_SERIAL` if several are attached).

```
# Baseline Profile (Android 9+ or rooted): writes app/src/main/generated/baselineProfiles/
./gradlew :app:generateBaselineProfile

# Benchmarks (all, or one class with -Pandroid.testInstrumentationRunnerArguments.class=...)
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark
```

Results: `benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/<device>/*-benchmarkData.json`.

## Second screen on the emulator, and running by hand

- Overlay display for the second screen: `adb shell settings put global overlay_display_devices
  "1240x1080/369,own_content_only"` (a Presentation needs `own_content_only`; a plain overlay makes
  the window fail with `InvalidDisplayException`). Check `adb logcat -d | grep PlumSecondScreen`
  for "shown on display N (benchmark)". Remove it with `... overlay_display_devices none`. An
  overlay slows the emulator whatever the app does: compare on and off with the overlay present.
- Benchmark-only launch extras (honoured only by the `benchmarkRelease` build): `SECOND_SCREEN`
  (show the window on an Unsure screen) and `DETAILS` (box-art facts and a long description on
  every game, as a signed-in library has); `startHome(secondScreen = , details = )`.
- Run on a private adb server, never through Gradle's connected tasks (they ask the default server):
  build with `./gradlew :app:assembleBenchmarkRelease :benchmark:assembleBenchmarkRelease`, then
  `ADB_SERVER_SOCKET=tcp:localhost:5038 ADB_MDNS_AUTO_CONNECT=0 scripts/bench-device.sh <label>
  FocusMoveBenchmark#moveEvery250msWithDetailsSecondScreen` (the emulator started with
  `ANDROID_ADB_SERVER_PORT=5038`). `FocusMoveBenchmark` presses D-pad down every 250 ms.
- The traces in `build/bench/<label>/` are Perfetto files (open them in ui.perfetto.dev);
  the second screen's card names its work in sections `SS:header`, `SS:tiles`, `SS:description`,
  `SS:probes`, `SS:picking`, `SS:card`.
