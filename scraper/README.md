# scraper

ScreenScraper client: finds a game by ROM hash, then by name, and returns its details and box
art URLs.

Namespace: `com.plumemu.scraper`.

This module holds the engine and its background run, and keeps every text field of a game
(`GameDetails`). The library's `ScrapeSource` over Room, the account in core-data and the
Application as `ScraperHost` (`app/.../graph/GraphScrapes.kt`) use it. Every test here runs
against a local fake server (MockWebServer) except the opt-in `live/ScreenScraperLiveSmokeTest`
(`-Pscreenscraper.live=true`, skipped otherwise and in CI); the app's opt-in
`ScreenScraperLiveTest` is the timing run.

## What it does

1. `Scraper.create(config)` gives the door. Without developer credentials in the build it is an
   `UnavailableScraper`: no network call, every answer `Unavailable(NoDeveloperCredentials)`.
2. `scrape(request)` sends the library's `RomHints` (CRC32, serial, size) when the request has
   them and then never opens the file; without hints it hashes the ROM (CRC32 + MD5 +
   SHA1 up to 64 MiB, CRC32 only above, none for `.chd .rvz .cso .7z`; cached by key + size +
   date). It asks `jeuInfos.php`, and on "not found" asks `jeuRecherche.php` by title when the
   system is known, accepting a result only when exactly one game of that system has a name
   equal to the title after `TitleMatch.normalise` (else `NoMatch`).
3. Answers (found and not found) are cached on disk for 30 days, trimmed to what is kept
   (`CachedAnswer`, format v3: every text field of the game, and the box front, disc or
   cartridge, screenshot, title screen and manual references; 16.8 KB for a rich game measured
   by `GameDetailsTest` (6 synopses of 1,200 characters, 34 button colours, 25 references, 6
   genres in 6 languages) instead of 1-2 MB), without credentials or account. No fan art, no
   video. Every field added for the details reads leniently (`api/Lenient.kt`): an odd shape is
   null or empty, never a lost match.
4. A `Matched` result carries the picked values (`GameInfo`: one title, synopsis, year... in the
   person's order), every media reference (`media`) and `GameDetails`: everything the answer
   gave, per region and per language as given (names, synopses, dates, publisher, developer,
   players, rating, genres, modes, families, themes, styles with their names per language and
   the main flag, age classifications, arcade rotation, resolution, controls and button colours,
   clone of, not a game, staff pick, the media references). `GameDetails.toJson()` /
   `GameDetails.fromJson()` give the stored JSON, `{"format":1,"details":{...}}`, read field by
   field whatever its format: unknown fields skipped, a missing, null or wrong-typed field
   defaulted alone, a list entry that does not read (a media kind this version does not know)
   dropped alone; null only for text without a `details` object. `GameDetails.jsonFormatOf()`
   tells the format written.
5. Requests keep to the account's `maxthreads`, 1.2 s per slot, retry thread limits (2, 4, 8,
   16 s), pause until Paris midnight on the daily quota and an hour when the API is closed.
6. Every outcome is a `ScrapeResult`; credentials never reach a log line, a cache file or a
   stored URL. With an account set, an answer as anonymous (`ssuser.numid` exactly "0"; a missing
   or blank one is not) is a refusal:
   `Unavailable(BadUserCredentials)`, and the scraper asks nothing more.
7. `checkAccount()` asks `ssuserInfos.php` through the same gate (not cached) and gives an
   `AccountCheck`: `Ok` with the account's numbers, `Refused`, `Offline`, `ServerClosed`,
   `DailyQuota`, `ServerError` (a 429 is not retried there), or `NoAccount` without a request.
8. `download(media, file, maxWidth, maxHeight)` fetches a picture through the same gate (the run
   asks for the largest of its sizes, a box front no taller than 720 and as a JPEG; the md5 is checked only at full size). The run fetches the
   box front, then the disc or cartridge (`MediaKind.Support`, "support-2D"), through
   `work/RunPictures` (public, so the person's pick saves the same files); `MediaSaver` saves each as
   WebP at each size the host names (`<mediaDir>/<game id>/box-front-<size>-<w>x<h>.webp`,
   `support-<size>-<w>x<h>.webp`), `RGB_565` on low-RAM devices. A support that fails for any
   reason never holds its game back: the game is delivered with its box front (and a stop met on
   the support, such as a pause, then ends the run). Screenshots (`MediaKind.Screenshot`), title
   screens (`TitleScreen`) and manuals (`Manual`) are references only: the run never downloads
   them.
9. A pause is saved (`SharedPreferencesPauseStore`) and loaded by the next process's scraper.
10. "Fix box art…": `search(title, systemId, regions)` asks `jeuRecherche.php` with
    the person's text through the same gate (threads, spacing, retries, pauses, refusals, offline)
    and returns every game of the answer once as a `SearchHit` (id, name and year in the region
    order, publisher, ScreenScraper's system name, the first box front for a thumbnail), never
    cached; `byGameId(gameId, request)` asks `jeuInfos.php?gameid=` and answers like a scrape match
    with `MatchedBy.PersonChoice` (cached 30 days unless `fresh`; an id that is not a number asks
    nothing). Seen live (developer credentials only): "Super Mario World" on the SNES, 30
    games with game 2144 first; game 2144 by id with 7 box fronts, 2 HTTP requests.
    Box art after a rename: `searchExactTitle(title, systemId, regions)` is the same uncached search
    narrowed to the games a run's name search accepts (the system's games whose name equals the
    title after `TitleMatch.normalise`, each id once); a system without a ScreenScraper id asks nothing.
    `Found.truncated` says the answer held 30 games, the endpoint's cap (set for `search` too).
11. `ScrapeRequest.fresh` (the person asked for the game again): the answer cache is not read (the
    new answer is stored over the old one; a failure leaves it), and the run downloads the
    pictures again; every size is written before any is renamed over the old files (all sizes or
    none). No new box front saved (none listed, every one broken) keeps the old files; a box front
    stop or server error keeps them and the game undelivered; a disc or cartridge that fails is
    delivered as saved before. The hash cache is still used. One more API call than a cached request.

## TLS on Android 6.0-7.0

ScreenScraper's certificate chains up to ISRG Root X1 (`*.screenscraper.fr` <- YE2 <-
Root YE <- ISRG Root X2 <- ISRG Root X1, all sent by the server), which Android's system store
holds only from 7.1.1 (API 25). Below API 25 `ScreenScraperApi.defaultHttpClient()` (also used
for pictures) trusts the system store first, then ISRG Root X1 and X2 bundled as Java resources,
built once per process (a root that cannot be read is logged and the system trust kept); from
API 25 on it keeps OkHttp's platform trust. Hostname verification, TLS versions and cipher
suites are OkHttp's defaults everywhere. Measured on the `plumemu-api23-lowend` emulator: a
sign-in with a made-up account went from "No connection" (`SSLHandshakeException`) to "Wrong
username or password" (HTTP 403 from `ssuserInfos.php`).

## Background run

`ScrapeScheduler.enqueue(context, wifiOnly)` asks for one run (unique work, KEEP; Wi-Fi or any
connection, battery not low; nothing periodic). `ScrapeWorker` reads `applicationContext as?
ScraperHost` and calls its suspending `scraperWork()` once per run, walks `ScrapeSource.pending()` page by page and delivers each `Matched` (with its
saved pictures) or `NoMatch`. A pause re-enqueues the same work after the resume time; after
8 minutes a run hands over to a fresh one at once; no connection, a server unwell or a full disk
retries with exponential backoff; refused credentials fail without retry, after
`ScraperWork.onUnavailable(reason)` told the host why (the app marks a refused account there;
a hook that throws is logged and the run still stops). Every run ends with
`ScraperWork.onEnded(ScrapeRunEnd, workId)` (Done, Paused, Continue, Retry, Stopped); the app
keeps "retrying" from it. Whatever was not delivered comes back next run.

## Password

`KeystorePasswordCipher` encrypts the person's ScreenScraper password with an AES-256-GCM key in
the Android Keystore (alias `plumemu.screenscraper.password`). Storing the text it returns is
core-data's job (DataStore). `decrypt` gives null on any failure: the person signs in again.

## Developer credentials

Read at build time into `BuildConfig.SCREENSCRAPER_DEV_ID` / `SCREENSCRAPER_DEV_PASSWORD`, from
`secrets.properties` at the repository root (keys `screenscraper.devId`,
`screenscraper.devPassword`; git-ignored), else the environment variables `SCREENSCRAPER_DEV_ID`
/ `SCREENSCRAPER_DEV_PASSWORD` (CI secrets), else empty. A worktree reads its own root.

When ScreenScraper grants access, the maintainers set them; nobody else handles them:

- **On a development machine**: create `secrets.properties` at the repository root (never committed; the
  `secrets` guard blocks it) with two lines, `screenscraper.devId=...` and
  `screenscraper.devPassword=...`.
- **For CI**: `gh secret set SCREENSCRAPER_DEV_ID` and `gh secret set SCREENSCRAPER_DEV_PASSWORD`
  (each asks for the value). The CI workflow passes them to every build step as env, and runs
  `./gradlew :scraper:screenScraperCredentialsCheck` before `verify.sh --ci`; its log line says
  `ScreenScraper developer credentials: present` or `absent` and never a value. The same task
  works on a development machine. `BuildConfig.HAS_DEVELOPER_CREDENTIALS` is true only when both are non-blank;
  other modules read it through `DeveloperCredentials.buildHasCredentials`, and the app logs the
  same line once at start.
- A build without them works exactly as before: the scraper reports "unavailable" and never
  calls the network.

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library module; OkHttp, kotlinx.serialization, coroutines, WorkManager; the two BuildConfig fields plus `HAS_DEVELOPER_CREDENTIALS`, the `screenScraperCredentialsCheck` task |
| `src/main/AndroidManifest.xml` | INTERNET permission |
| `src/main/java/com/plumemu/scraper/Scraper.kt` | The door: `Scraper`, `ScraperConfig`, `DeveloperCredentials`, `UserCredentials`, `UnavailableScraper` |
| `…/ScrapeRequest.kt` | `ScrapeRequest` (with `fresh`), `RomHints`, `RomSource` |
| `…/AccountCheck.kt` | What `Scraper.checkAccount` found |
| `…/TitleMatch.kt` | The title cleanup and exact-title rule for name search results |
| `…/ScrapeResult.kt` | `ScrapeResult` and its reasons, `GameMatch`, `MatchedBy` (`Hash`, `Name`, `PersonChoice`, `Fallback`, `Parent`), `GameInfo`, `MediaKind`, `MediaRef` |
| `…/HackParent.kt` | A `[Hack]` file with no box art takes its parent game's (`HackParent.take`, called by `ScreenScraperScraper.scrape`) |
| `…/FallbackArt.kt` | What `Scraper.fallbackArt` found when ScreenScraper has no box front: `FallbackArt` (`NotApplicable`, `NotFound`, `Found`, `Failed`), `OutcomeWords` |
| `…/fallback/ArtFallbackSource.kt` | The seam: one source of box art other than ScreenScraper (`lookup`, `owns`, `download`); listed in `ScreenScraperScraper.fallbacks` |
| `…/fallback/ArtFallbacks.kt` | The list of sources (`forConfig`), asked in turn (`lookup`), and who owns a picture URL (`ownerOf`); held by `ScreenScraperScraper` |
| `…/fallback/LibretroArt.kt`, `…/fallback/LibretroSystems.kt` | libretro's thumbnail server as a source for retro systems: the file-named picture, else the cached folder listing and `pick`; the table of Plumemu system to libretro folder |
| `…/fallback/SteamArt.kt` | Steam's image CDN as a source: the app id from the `.steam` file or the store search, portrait / 2x / header pictures, no credentials |
| `…/work/FallbackBox.kt` | The run's step that asks the fallback sources and saves their picture through `RunPictures` |
| `…/SearchOutcome.kt` | What `Scraper.search` found: `SearchOutcome`, `SearchHit` |
| `…/GameDetails.kt` | `GameDetails` and its parts (`TextByRegion`, `TextByLanguage`, `IdName`, `GameTag`, `Classification`, `ButtonColour`); `toJson` / `fromJson` |
| `…/HtmlEntities.kt` | Decodes the HTML entities in ScreenScraper's texts (`&quot;`, `&#39;`, `&eacute;`), once |
| `…/GameDetailsJson.kt` | The stored JSON: format version, defaults left out, lenient reading |
| `…/HashCache.kt` | `RomHashes` and the `HashCache` interface (the library may back it with Room) |
| `…/ScreenScraperScraper.kt` | The real scraper: hash lookup, name search, cache, retries, pauses |
| `…/SystemIds.kt` | Plumemu system id → ScreenScraper `systemeid` |
| `…/api/ScreenScraperApi.kt` | One OkHttp call with credentials added; `Call.await()`; `defaultHttpClient()` |
| `…/api/ScreenScraperTrust.kt` | TLS trust below API 25: the system store, then the bundled ISRG roots (`SystemThenBundledTrustManager`) |
| `src/main/resources/com/plumemu/scraper/certs/*.pem` | ISRG Root X1 and X2, public certificates from letsencrypt.org (fingerprints in `ScreenScraperTrust`) |
| `…/api/ApiStatus.kt` | HTTP status → `ApiOutcome`, the one mapping (200, 403, 404 seen live) |
| `…/api/ApiModels.kt` | The JSON fields read, and the lenient `Json` |
| `…/api/CachedAnswer.kt` | An answer trimmed to what is kept, each media reference once, for the cache |
| `…/api/GameMapper.kt` | ScreenScraper game → `Matched`: `GameInfo` and the media references by region and language order |
| `…/api/DetailsMapper.kt` | ScreenScraper game → `GameDetails`, every list as given |
| `…/api/Lenient.kt` | Lenient readers for the details fields: a scalar that is not one is null, a list that is not one is empty, an entry that does not read is dropped (`couleurs` is a string in a name search) |
| `…/api/Redact.kt` | Hides credentials in text, strips them from URLs |
| `…/cache/ResponseCache.kt` | 30-day disk cache of answers, one file per request |
| `…/gate/RequestGate.kt` | Thread limit, spacing, pause |
| `…/timing/ScrapeTiming.kt` | Timing log lines (tag `PlumScrapeTiming`): per request, per game, account limits |
| `…/gate/ParisTime.kt` | Next midnight in Paris (Calendar, not java.time) |
| `…/hash/RomHasher.kt` | Streamed CRC32 / MD5 / SHA1 |
| `…/hash/FileHashCache.kt` | `HashCache` in one appended text file |
| `…/MediaDownload.kt` | What `Scraper.download` came back with |
| `…/PauseStore.kt` | `PauseStore`, `SharedPreferencesPauseStore`: a pause that outlives the process |
| `…/media/MediaDownloader.kt` | One picture download: credentials added in memory, md5 checked while streaming |
| `…/media/BoxFrontRequest.kt` | How a box front is asked: JPEG, at most 720 tall (measured sizes in its comment) |
| `…/media/MediaSaver.kt` | Bounds, `inSampleSize`, scale to fit, WebP, atomic rename; the low-RAM check |
| `…/media/SavedMedia.kt` | `MediaSize` (named target size), `SavedMedia` (one saved file) |
| `…/work/ScraperHost.kt` | `ScrapeSource` (the library), `ScraperHost` (the Application, `suspend scraperWork()`), `ScraperWork` (with `onUnavailable`, the host's hook on a stop for an unavailable scraper, and `onEnded`), `ScrapeRunEnd` |
| `…/work/ScrapeScheduler.kt` | Enqueue, cancel, the re-run after a pause; constraints and backoff |
| `…/work/ScrapeWorker.kt` | The CoroutineWorker: host lookup, run, result mapping |
| `…/work/ScrapeRun.kt` | One run: the main pass (`pending()`: lookup, box front, deliver) then the disc pass (`pendingSupport()`: cached answer, box reused, disc), as many workers as `Scraper.requestSlots`, skips, stops, games set aside after a network error |
| `…/work/RunStops.kt` | Which of two stops of a run wins (`worseStop`) |
| `…/work/NetworkMemory.kt` | Per scraper, in memory: games whose main pass met network errors in runs in a row (asked last, dropped after three), `Scraper.forgetNetworkFailures()` |
| `…/work/RunPictures.kt` | The one way pictures are saved (`save(result, kind, fresh)` → `PictureOutcome`): the run uses it, and so does the person's pick ("Fix box art..."); one save at a time in the process (the download is outside the lock, the decode and write inside) |
| `…/account/PasswordCipher.kt` | `PasswordCipher`, the AES-GCM codec, the Keystore key, `KeystorePasswordCipher` |
| `src/test/java/com/plumemu/scraper/ScraperFixture.kt` | MockWebServer, fake credentials, a scraper pointed at it |
| `src/test/java/com/plumemu/scraper/FileRomSource.kt` | A `RomSource` over a plain file, for tests |
| `src/test/…/LibretroArtTest.kt`, `src/test/…/work/LibretroFallbackRunTest.kt` | The libretro fallback: file name hit, listing with region choice, ambiguity, special characters, uncovered systems make no request, listing cache, failures |
| `src/test/…/SteamArtTest.kt`, `src/test/…/work/SteamFallbackRunTest.kt` | The Steam fallback: app id from the file or the store search, ambiguity, 404 to header, network failure, no ScreenScraper credential in any Steam request, other systems never ask Steam |
| `src/test/…/work/RunPicturesTest.kt` | Saving called directly as the person's pick calls it: both sizes, reuse unless fresh, a fresh save replaces, a server error keeps the files, no picture saves nothing |
| `src/test/…/*Test.kt` | Every field kept for a console and an arcade game, cached the same, the size of a rich game (`GameDetailsTest`), odd details fields never cost a match (`OddDetailsFieldsTest`), the stored JSON (`GameDetailsJsonTest`), HTML entities in texts (`HtmlEntitiesTest`), match, hints (`ScrapeHintsTest`), name search and title cleanup (`NameSearchTest`, `TitleMatchTest`), the trimmed cache (`TrimmedCacheTest`), the account (`AccountCheckTest`), scaled pictures (`MediaResizeTest`), limits, errors, gate, stop signals across parallel calls, network threads and cancel, hashing, caches, redaction, Paris time, system ids, the credentials flag |
| `src/test/…/work/*Test.kt` | Worker (resume after a pause, retry, failure, no host, stop), the host told why a run stopped and how each run ended (`ScrapeWorkerHostHooksTest`), media failures (`ScrapeWorkerMediaTest`), the disc or cartridge in the disc pass (`ScrapeWorkerSupportTest`), box fronts first, parallel workers, the gate's limits, a 429, the daily quota, cancellation, timing lines of overlapping games, a no-match's two requests, a timeout set aside (`ScrapeRunParallelTest`), screenshots, title screens and manuals never requested (`ScrapeWorkerReferencesTest`), follow-up runs through WorkManager (`ScrapeChainTest`), a run giving way to a game being played and its follow-up when the person is back (`ScrapeRunHoldOffTest`, `ScrapeWorkerHoldOffTest`), scheduler; `WorkerTestBase.kt` the shared setup; `WorkTestSupport.kt` the test Application, a fake source and a PNG maker |
| `src/test/…/live/ScreenScraperLiveSearchTest.kt` | Opt-in (`-Pscreenscraper.live=true`), the real API: "Super Mario World" on the SNES found by search, then by its id with a box front, at most 3 HTTP requests |
| `src/test/…/SearchTest.kt`, `ByGameIdTest.kt` | The person's search (every game once, region order, never cached, nothing found, refusals, `numid` "0", daily limit, offline, a body that is not JSON), the exact-title search after a rename (only the system's exact names, twins both listed, no system id or blank asks nothing, stops like a search) and the pick by id (a match as `PersonChoice`, cached unless fresh, unknown id, not a number, `numid` "0") |
| `src/test/…/FreshRequestTest.kt`, `work/ScrapeWorkerFreshTest.kt` | `fresh`: the answer cache skipped but written, kept on failure; pictures downloaded again and replaced, the old file kept when the new one is broken or cut off, the earlier disc picture kept when its download fails on the server |
| `src/test/…/live/ScreenScraperLiveSmokeTest.kt` | Opt-in (`-Pscreenscraper.live=true`), the real API: Super Mario Bros. (NES) matched by hash, game 1245, box front at both app sizes, an English synopsis, at most 3 HTTP requests counted on the engine's client |
| `src/test/…/media/MediaSaveBelowApi30Test.kt` | The WebP branch below API 30, at `sdk = [28]` |
| `src/test/…/media/MediaSaveTest.kt` | Sizes, aspect, RGB_565, md5, corrupt body, all sizes or none (a second size failing keeps both older copies), credentials (Robolectric native graphics) |
| `src/test/…/account/PasswordCodecTest.kt` | The codec with a software key |
| `src/androidTest/…/account/KeystorePasswordCipherTest.kt` | The real Keystore round trip (device or emulator only) |
| `src/test/resources/screenscraper/*.json` | Answers shaped like the real API, fake values, ASCII only |
| `src/test/…/api/ScreenScraperTrustTest.kt` | The bundled roots' fingerprints; ScreenScraper's real intermediates accepted below API 25 at a fixed date though the system store refuses them; an unknown root and a fake "ISRG Root X1" refused; the platform trust from API 25 |
| `src/test/resources/screenscraper/tls/*.pem` | ScreenScraper's public intermediates (YE2, Root YE by X2, X2 by X1) and two made-up self-signed roots |
| `src/test/resources/screenscraper/systems.tsv` | ScreenScraper's 250 system ids and names (`systemesListe.php`), for `SystemIdsTest` |
