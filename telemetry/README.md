# telemetry

Usage statistics: the install id, the on-device queue, the statistics switch, the sender and the
common fields, the app's log file and crash reports and problem reports.
The contract is [docs/conventions/telemetry.md](../docs/conventions/telemetry.md).

Namespace: `com.plumemu.telemetry`. Depends on `platforms` (system and emulator ids), OkHttp, kotlinx.serialization (JSON elements only,
no plugin), kotlinx.coroutines, DataStore and WorkManager; never on `core-ui` or `app`.

## How it works

- **First launch**: [Telemetry] makes a random install id and queues `first_open`, asking for a
  send at once. Only when statistics are on: a new install has them off (statistics are opt-in), so a first launch with the switch off queues and sends nothing, not even a decline.
- **Events**: the app calls `track(TelemetryEvent.X(...))`; one type per event of the convention,
  fixed values as enums, private counts turned into ranges here. Queued in
  `noBackupFilesDir/telemetry/queue/queue.jsonl`, at most 2,000 (the oldest 100 go when full,
  counted in one `queue_overflow` event).
- **Sending**: WorkManager, network required. A batch goes within 5 minutes of an event (one
  waiting send at a time), at once on `first_open`, `app_background` and a decline. Batches of at
  most 200 events and 60 KB of events. 2xx: removed. 429, 5xx, no network: kept, WorkManager
  retries with back-off from 1 minute. Any other answer (400, 413, 404 while the server is not
  deployed): the batch is dropped and logged.
- **Start**: the app's stored switch is read first; off on a first run, or nothing chosen yet (`null`), means no `first_open` and no
  decline. An opt-in install sends `first_open` with its first `stats_switched_on`, once. Unreadable: nothing is sent by that process.
- **Switch**: `setEnabled(false, where)` deletes the queue and sends `stats_declined` once per
  install, ever (no install id, with `where` and `common`), then nothing. `setEnabled(true, where)`
  resumes with the same id and sends `stats_switched_on`. The state is in a DataStore in
  `noBackupFilesDir/telemetry/`: kept across updates, never in a backup, gone with the app's data.
- **Endpoint**: HTTPS only. Plain http only to 10.0.2.2 or localhost, and only when the app's
  debug build was pointed at a local `wrangler dev` (`-Pplumemu.statsEndpoint=http://10.0.2.2:8787`).
- **Log file**: `AppLog` (logcat plus `noBackupFilesDir/logs/plumemu.log`, rotated at 1 MB, two
  files at most, written by `LogWriter`: 1,000 lines wait at most); `snapshot` makes the text
  Settings › Advanced › Export logs shares: that file plus this app's own lines of logcat.
- **Crashes**: `CrashCapture.install` (by the Application, first) puts in a handler that writes one
  file to `noBackupFilesDir/telemetry/crashes/` and hands over. At start [Telemetry] adds Android's
  ANR and native-crash records (Android 11+, `ExitReasons`), keeps the newest 10 and sends them
  through `/api/v1/crashes` before the event batches (`CrashSending.kt`); off deletes them unsent.
- **Failures** never reach the person: each is one log line under the tag `Telemetry`, with the
  endpoint and status or the exception's class, never a body or a header.

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library module; SDK levels come from `gradle/libs.versions.toml` |
| `src/main/AndroidManifest.xml` | INTERNET |
| `Telemetry.kt` | The door: `Telemetry.create`, `start`, `track`, `setEnabled`, `sendPending`, `installId`, `isEnabled`; `TelemetrySink` (what the app holds, faked in its tests); the command queue that orders events and switch moves |
| `TelemetryEvent.kt` | One type per event of docs/conventions/telemetry.md and its props |
| `EventValues.kt` | The enums of the events' fixed values, each with its wire text |
| `TelemetryIds.kt` | `SystemRef` / `EmulatorRef`, made only from the platform data's systems and emulators; the version-name shape |
| `Buckets.kt` | The ranges: games, search length, results, memory |
| `TelemetryConfig.kt` | Endpoint, version, build type from the app; which endpoints a build may use |
| `CommonFields.kt` | The `common` object, read once per process |
| `StateStore.kt` | Install id, on / off, the one decline (DataStore in the no-backup folder) |
| `EventQueue.kt` | The capped on-device queue and its overflow count |
| `Sender.kt` | One POST and what its answer means (`Answer`) |
| `SendWork.kt` | `TelemetryHost`, the WorkManager scheduler and `TelemetrySendWorker` |
| `AppLog.kt` | The one logger (logcat and the rotating file `LogFile`) and the export |
| `Logcat.kt` | This app's own lines of logcat for a snapshot: `--pid`, the process-id filter, the 384 KB tail, the 5 s timeout |
| `CrashCapture.kt` | The uncaught-exception handler; the last screen opened |
| `CrashReports.kt` | One crash item of the API: fields, caps, the header line with the version code |
| `CrashStore.kt` | The crash files: write (temporary then renamed), read, at most 10, clear |
| `ExitReasons.kt` | ANR and native crashes from `ApplicationExitInfo`, the marker of the last one seen |
| `CrashSending.kt` | One crash request and what its answer does to the files |

Tests (`src/test/`): `TelemetryTest` (MockWebServer: first launch, batch shape, switch, answers,
offline, cap, install id), `TelemetryEventTest` and `BucketsTest` (every event's fields),
`TelemetryConfigTest` (HTTPS only), `CrashTest` (handler, send once, off, 429, caps), `ExitReasonsTest`
(exit records on fixtures, the 64 KB trace cap), `LogFileTest` (rotation, snapshot, the writer's queue), `LogcatTest` (own lines only, tail, timeout), `TelemetryReviewTest` (switch during a send, bounded send,
stored switch at start, version names, language tags, platform ids), `CommonFieldsTest` and `SendWorkTest` (Robolectric).
`./gradlew :telemetry:testDebugUnitTest`.

## Used by

- `app`: `PlumApplication` creates and starts it and is its `TelemetryHost`;
  `graph/GraphTelemetry.kt` follows the stored switch (Settings); `GraphSetupSource` tells it
  setup's switch. The app's Robolectric tests run with `TestPlumApplication`, which makes none.
