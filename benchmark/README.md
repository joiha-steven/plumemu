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
