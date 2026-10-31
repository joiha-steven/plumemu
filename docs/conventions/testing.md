# Testing

## Kinds

- **Unit tests** (JUnit, on the JVM): title cleaning, folder-to-system matching, disc grouping,
  intent building per emulator (compared with expected intents), platform data validation,
  telemetry event fields, input action mapping.
- **Identification corpus** (JUnit + Robolectric, on the JVM, part of
  `./gradlew :library:testDebugUnitTest`, about 10 s): the real scan, rules and name packs over
  every `library/src/test/resources/corpus/*.tsv`, scored as a
  person sees the game list: right system, grouping and name. Report in
  `library/build/corpus/report.md`, failing games in `library/build/corpus/<corpus>-failures.tsv`;
  a score below `corpus/scores.txt` fails the build. After an intended change:
  `./gradlew :library:testDebugUnitTest --tests '*CorpusTest' -Pcorpus.update=true` and commit
  scores.txt.
- **Timing budgets in unit tests**: a speed check that holds on any machine is a ratio measured on the same
  machine (`TitleSearchTest`). A test that asserts an absolute time (ms or s) puts `Assume.assumeTrue(...,
  System.getProperty("plumemu.timingBudgets") != "false")` right before that assertion, after the correctness
  assertions. `verify.sh --ci` passes `-Pplumemu.timingBudgets=false` (shared runners are several times slower);
  everywhere else the budget is checked. A laptop-only measurement stays opt-in (`-Pidentify.measure=true`).
- **Screenshot tests** (Roborazzi, on the JVM): every screen at 768×432 and 576×432 dp (the 4:3 640×480 and 640×432 on the screens in design/rules.md › Reference sizes, `sizes = ReferenceShots.Size.entries`), dark and
  light, with Plum Sans; key screens also with Plum Serif and Plum Mono. Reference images are committed; a
  difference fails the gate until a person accepts it.
- **Benchmarks** (Macrobenchmark, on a device): cold start, scrolling 10,000 games in list and
  grid. Results are recorded with the device and build.
- **Device checks** (by a person, on a real handheld): the "Done means" list in the product spec.

## Rules

- A bug fix comes with a test that fails before the fix and passes after.
- A feature has tests for the right path and the wrong ones: empty folder, missing file,
  emulator not installed, no network, no permission, SD card removed.
- Tests never touch the network; ScreenScraper and the statistics server are faked.
- Do not weaken a test to make it pass. If a requirement really changed, say which test changed
  and why in the report.
- Fixture folders of fake ROMs live in `library/src/test/fixtures/`; files are empty or tiny, never real games.
- One run at a time uses a physical device over `adb`; JVM tests can run in parallel. Connected and
  benchmark tasks install on, and uninstall from, every attached device, so set `ANDROID_SERIAL` to the
  device meant before running them.

## CI

CI runs `scripts/verify.sh --ci` on every push to any branch, on release tags and on pull requests,
so a contributor's branch is checked before anyone reviews it. It skips screenshot comparison:
Linux renders text slightly differently from the machines that recorded the reference images.
The checks are listed in [../../scripts/README.md](../../scripts/README.md).
