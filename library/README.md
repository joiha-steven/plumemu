# library

Folder scan through `DocumentsContract`, system matching, disc grouping, naming games with the
`identify` module, default emulator per system, and the games the ScreenScraper run
works through (`scrape/`).
Writes to the database in `core-data`; never depends on `platforms` (the app turns platform data into
`SystemRule`s) or on `core-ui` / `app`.

Namespace: `com.plumemu.library`.

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library module; depends on `core-data`, `identify`, `scraper` (`LibraryScrapeSource` is its `ScrapeSource`) and kotlinx.coroutines |
| `src/main/.../matching/SystemRule.kt` | `SystemRule(id, folders, extensions, names)`: plain data the app builds from the platform data |
| `src/main/.../matching/DatFolderNames.kt` | Generated from the platform and DAT tables of the build tools: DAT header names per system for `Maker - System` folders |
| `src/main/.../matching/SystemMatcher.kt` | Folder name → system (alias, the system's own names, `Maker - Rest`, a leading maker word, arcade romset families such as `MAME 2003 Plus`; then without a leading number `01 - SNES`; then a MinUI/Onion bracket tag `Super Nintendo (SFC)`); with several known folders on the path each file takes the nearest that claims its extension (`FolderMatch.Path`); outside a system folder, the one system the extension points to; `FOLDER_MATCH_VERSION` makes the app rescan every folder once when the rules grow; the person's system for an unknown folder (`matchPath(..., chosen)`), `isCandidateFile`; `FolderMatch`, `FileMatch` |
| `src/main/.../naming/NameDecision.kt` | Which name a game shows after identification: a person's fix, else `NameTrust.winner`; a ScreenScraper pick (`SCRAPER_PERSON_CHOICE`) is kept over every identifier result |
| `src/main/.../naming/NamingRules.kt` | `NAMING_RULES_VERSION` and the systems each version names differently with the same packs (1: arcade regions; 2: arcade sets grouped); `LibraryScanner.nameAgainForNewRules` marks their rows once |
| `src/main/.../naming/GameCopies.kt` | The game menu's Other versions: `copiesOf` (a row's copies by the grouping's rules: file sort name and variant key for consoles, `ArcadeCopies`' group key for arcade sets, only the parent's clones looked up in the pack), `copyToPlay` (the copy a launch opens: the latest choice, else the row's own), `chooseCopy`. Tests `scan/GameCopiesTest.kt` |
| `src/main/.../naming/ArcadeCopies.kt` | Arcade sets of one game in one directory as one row: who groups (parent, same short title, no bootleg or hack), which copy shows (marks, region, parent); `arcadeGameOf` / `arcadeParentOf` for app's Statistics, `setHiddenWithArcadeCopies` for Hide and show again. Run by `IdentifyPass.groupArcadeCopies` |
| `src/main/.../naming/FingerprintCodec.kt` | `game.fingerprint`: the identifier's fingerprints as JSON, so rescans read nothing |
| `src/main/.../scan/SheetReader.kt` | Reads the sheets of a directory (64 KB cap) and finds the files they name, by name then ignoring case, in the sheet's own folder only; tests `SheetPartsScanTest` |
| `src/main/.../catalog/JunkFilter.kt` | Hidden files, BIOS names, saves, notes, images; skipped folders (`bios`, `media`, …) |
| `src/main/.../catalog/DiscGrouping.kt` | `.cue`/`.bin`, `.gdi` and its tracks (any extension), `.m3u` (by name, or the only playlist beside one game) and `(Disc N)` files grouped into one game, by names only |
| `src/main/.../catalog/SheetClaims.kt` | Files a `.cue`, `.gdi` or `.m3u` names (read by `scan/SheetReader.kt`) are parts of its game whatever their names; the sheet is the row |
| `src/main/.../catalog/SwitchGrouping.kt` | Switch base, update and DLC of one directory as one game by the base title id in their names; the base file is the row |
| `src/main/.../catalog/DiscMarkers.kt` | Disc markers in other forms (`- Disc 1`, `[Disc1]`, `CD1`, `disc1`) rewritten to `(Disc N)`; generic names (`game`, `disc`, `rom`) |
| `src/main/.../catalog/RegionPreference.kt` | Which regional copy a list shows; default order USA, World, Europe, Japan |
| `src/main/.../catalog/DirectoryCataloguer.kt` | One directory's files → games under their file names, or the folder's name for one game's folder with generic file names; `partOf` (what a rescan folds); `CatalogSettings` |
| `src/main/.../scan/DocumentTree.kt` | The seam over a picked folder: `stat`, `listChildren`, `open`, `TreeResult` |
| `src/main/.../scan/ContentResolverDocumentTree.kt` | Real tree: `DocumentsContract` child queries with five columns; files opened with `openFileDescriptor` |
| `src/main/.../scan/FolderPermissions.kt` | Lasting folder grants (`takePersistableUriPermission`) |
| `src/main/.../scan/RomFolders.kt` | `addPickedFolder` for the picker result, `removeFolder` |
| `src/main/.../storage/ExternalStorageIds.kt` | The one reader of Android's storage provider ids (`AUTHORITY`; roots `primary`, `home`, volume ids; tree URI decoding with `+` kept; `.`/`..` refused); used by app's setup, `FolderPresence`, launcher's `DocumentPaths` and `StorageVolumes`. Test `ExternalStorageIdsTest` |
| `src/main/.../storage/StorageRoots.kt` | Where internal storage (per user) and volumes are: `AndroidStorageRoots` (`Environment`, `StorageManager` on API 30+, `/storage/<id>` fallback) |
| `src/main/.../scan/FolderPresence.kt` | Proof that an unavailable picked folder was deleted (Android's storage provider only: missing from its parent's successful listing); the scan then forgets it (`FolderOutcome.Deleted`). Tests `ExternalStorageFolderPresenceTest`, `LibraryScannerTest` |
| `src/main/.../scan/FolderScan.kt` | One folder's first scan or rescan; batches; deletions only at the end, never when the folder is gone; each directory's unknown files and the folders new to the unknown list (`ScanStats.newUnknownFolders`); never reads the sub-folders the person skips (`isSkippedByPerson`) |
| `src/main/.../scan/RenameMatcher.kt` | Matches rows that disappeared with rows inserted by this scan or by an unfinished earlier one, so moved or renamed files keep their rows |
| `src/main/.../scan/LibraryScanner.kt` | `scan()` all folders as a `Flow<ScanEvent>` (walks first, then the naming passes), one scan at a time in the process; `rescanEverything()` after rules or region settings change; `nameWaiting()` runs only the naming passes (Back to automatic); `nameAgainForNewRules()` marks rows named by older naming rules (after an upgrade); `addContentKey()` the whole-file read after a fix |
| `src/main/.../scan/IdentifyPass.kt` | The second pass: names new or changed rows with `identify` (cached fingerprints, QUICK reads, arcade names), then offers a stored ScreenScraper title again; `GameNaming`, `NamingStats` |
| `src/main/.../scan/SiblingFiles.kt` | Opens the files a `.cue`, `.gdi` or `.m3u` names, through the tree |
| `src/main/.../scan/ScanEvent.kt` | `ScanEvent` (`Progress`, `FolderFinished`, `Named`), `FolderOutcome`, `UnavailableReason`, `ScanStats` (with `addedBySystem` / `removedBySystem`, counts per system id) |
| `src/main/.../scrape/LibraryScrapeSource.kt` | The ScreenScraper run's `ScrapeSource` over Room: `pending` (paged, Recent → Favorites → viewed system → rest), `deliver(..., pass)` (row and name in one transaction; the main pass stores the box front, the disc pass the disc or cartridge), `pendingSupport` (the run's disc pass: matched games whose disc or cartridge is still to fetch, `fresh` when one is saved already); `askAgain` (`AskAgain.Missing` / `All`, every system or chosen ones: Settings' Download missing and Re-download); a row marked to ask again goes out `fresh` (no scraper cache, pictures fetched again); "Fix box art...": `personChoiceRequest` (the game as a run asks it, `fresh`) and `storePersonChoice` (the pick or No box art, `is_person_choice`, and the pick's title, in one transaction); box art after a rename: `renameLookup` and `storeRenameMatch` (stored like a pick, only if the game still has no box art and the same name); `forDevice` |
| `src/main/.../scrape/PersonChoice.kt` | `PersonChoice` (`Picked(result, media)`, `NoBoxArt`) and what storing it did (`PersonChoiceStore`) |
| `src/main/.../scrape/RenameLookup.kt` | Box art after a rename: `RenameLookup` (`Ready(title, request)`, `Skip`) from `LibraryScrapeSource.renameLookup`, and `RenameSkip` (why nothing is asked or stored, also from `storeRenameMatch`) |
| `src/main/.../scrape/ScrapeRegions.kt` | Plumemu region → ScreenScraper region codes; the request's region order (file's own, Settings choice, `wor`, `ss`) and languages (device, `en`) |
| `src/main/.../scrape/ScrapeHintsFromFingerprint.kt` | `game.fingerprint` → `RomHints` (CRC, the size it covers, a real serial); never reads the file |
| `src/main/.../scrape/DocumentRomSource.kt` | The scraper's `RomSource` over a document: key = document URI; `open()` through the folder's `DocumentTree` |
| `src/main/.../scrape/ScrapeHackFlag.kt` | Whether a file name has the Hack tag (`ScrapeRequest.hack`: the scraper may then take the parent game's box art) |
| `src/main/.../scrape/ShortcutSteamAppId.kt` | The Steam app id in a pinned shortcut's id (`game:<digits>`, digits; else null), sent as `ScrapeRequest.steamAppId` |
| `src/main/.../scrape/ScraperTrust.kt` | How a match was found (`HASH` only with a CRC or serial sent, `FILE_NAME_SIZE`, `NAME`; the person's pick is `NAME` too) and the name trust of each; a row with `is_person_choice` is `SCRAPER_PERSON_CHOICE` |
| `src/main/.../scrape/ScrapedTitleOffer.kt` | Offers a stored ScreenScraper title to a game's shown name by the trust order (on delivery, and from `IdentifyPass` when a game is named again) |
| `src/main/.../scrape/ScrapedRows.kt` | A `Matched` or `NoMatch` as the `scraped_game` row (details JSON, plain columns, pictures, disc missing) |
| `src/main/.../emulator/DefaultEmulator.kt` | The suggested emulator per system (first installed; the first launch's question shows it first); `EmulatorDefaults` keeps a person's choice |
| `src/test/fixtures/roms/` | Fake ROM folders, empty files only |
| `src/test/.../scan/TestNaming.kt` | Made-up name packs bundled in memory, a GBA header and zips generated by the tests |
| `src/main/.../scan/WholeFileRead.kt` | After a person's fix: one `Depth.FULL` read of a loose cartridge for its whole-file `crc:` key, the fix stored under it (`LibraryScanner.addContentKey`, `ContentKeyOutcome`) |
| `src/main/.../scan/ContentPass.kt` | The content pass: loose cartridges and headered zips read whole (`Depth.FULL`) for their content CRC, named again, box art copied to the new key when the title stays (`LibraryScanner.readContent`, `ContentReadBudget`, `ContentReadResult`) |
| `src/main/.../scan/NameWrite.kt` | The naming write shared by `IdentifyPass` and `ContentPass`: answer stored while the file is unchanged, shown name by the trust order and fixes, stored ScreenScraper title offered again |
| `src/test/.../scan/LibraryIdentifyTest.kt` | The scan names games: headers, zip CRC, name match, arcade, BIOS hidden, cache, renames, unreadable files, access revoked |
| `src/test/.../scan/LibraryNameFixTest.kt` | A person's fixes through the scan: fix keys, Back to automatic, `nameWaiting`, the whole-file read |
| `src/test/.../scan/SiblingFilesTest.kt` | Cue and playlist siblings: case, one folder down, never `..`, listing failures |
| `src/test/.../naming/NameDecisionTest.kt` | Trust order: fix always wins, the identifier's own earlier answer is replaced, scraper names (and `takesScraperName`), regions back to codes |
| `src/test/.../scrape/RenameLookupTest.kt` | Box art after a rename: asked only for a renamed game without box art (none, a run's no-match, a match without a box front), never for a run's box front or a person's choice, Not a game, Back to automatic, gone or being named; a run's match by the file without a box front only takes the same game (a name match may be replaced); stored like a pick only while that still holds and the name is unchanged; the content key landing first; art under the old row key counts |
| `src/test/.../scrape/PersonChoiceStoreTest.kt` | The pick stored with its pictures at both sizes, its name over the identifier's and a run's on every copy, a Fix name kept, picking again replaces, No box art stores no pictures and keeps the name, runs / Download missing / Re-download never touch a choice, a mark cleared, a game gone or being named, the pick's request |
| `src/test/.../scrape/ScrapeAskAgainTest.kt` | `askAgain`: games without a box front (a fresh no-match too), every game, chosen systems only; a person's choice never; rows and pictures kept until replaced; the not-stored set forgotten; only rows asked again are sent `fresh`, once; the disc pass waits for their main pass and then fetches a saved disc again (`fresh`) |
| `src/test/.../scrape/LibraryScrapeSourceTest.kt` | The contract: delivered never returns (also after reopening the file), 30-day no-match, priority order, stable pages, what is not asked, shared keys (both copies renamed), row keys, 10,000 games timed |
| `src/test/.../scrape/MainPassDeliver.kt` | Test-only `deliver(request, result, media)`: a delivery of the run's main pass, as most tests deliver |
| `src/test/.../scrape/ScrapeDeliverTest.kt` | What a request carries; the stored row, details and pictures; a main-pass match goes to the disc pass at once and counts no attempt, at most three disc-pass attempts; name upgrades by trust (exact only with a CRC or serial sent), every copy named, a fix wins, the person's choice kept; a file changed since `pending` gets nothing and is not offered again in the process |
| `src/test/.../scrape/ScrapeAfterScanTest.kt` | Over a real scan: not before naming, hints with nothing opened, `open()` through the tree, a renamed and moved file keeps its result, the stored title comes back after Back to automatic |
| `src/test/.../scrape/ScrapeRegionsTest.kt`, `ScrapeHintsFromFingerprintTest.kt` | The region table (every code in ScreenScraper's list) and order; hints from zip, full-read zip, disc, cartridge, nothing |
| `src/test/.../scrape/ScrapeTestLibrary.kt` | A file database with named games and a source over it, sample results |
| `src/test/.../naming/FingerprintCodecTest.kt` | Round trip of every field; unreadable values mean "read again" |
| `src/test/.../scan/FileSystemDocumentTree.kt` | Fake `DocumentTree` on the JVM filesystem that counts queries; optional move-proof ids |
| `src/test/.../scan/ScanFixture.kt` | Fixture folders, in-memory database and fake tree for scan tests |
| `src/test/.../matching/ShippedFolderNamesTest.kt` | Folder naming styles (MinUI tags, stock firmware short names, plain words, numbered, nested, files in the picked folder) and near names (`GB`/`GBA`/`GBC`, `PS`/`PS2`/`PSP`) against the shipped platforms.json |
| `src/test/.../corpus/` | Corpus harness: `CorpusTest` runs the real scan, rules (`RealData`) and packs over `src/test/resources/corpus/*.tsv` and scores system, grouping and name (`CorpusRun`, `CorpusScore`, `CorpusReport`, ratchet in `corpus/scores.txt`); `CorpusHarnessTest` checks the harness on `corpus-sample/sample.tsv`; `CorpusReaderTest` the format, including paths that clash on a case-blind disk |
| `src/test/.../scan/RescanSafetyTest.kt` | Moves, renames, `.m3u` added (same or another name), an older scan's `Disc 2` entry folded into the game, a rename finished after an interrupted scan, same-size games, ghosts, clock granularity, two scanners |
| `src/test/.../scan/ContentResolverDocumentTreeTest.kt` | The real tree against a misbehaving provider |
| `src/test/.../scan/LogHygieneTest.kt` | A provider message holding a file name reaches no log line, neither as text nor as an attached exception |
| `src/androidTest/.../StaticInitialisersTest.kt` | Loads and initialises every `com.plumemu` class of the test APK on a device and fails on any static initialiser that throws (a regex Android's ICU refuses broke every scan on Android 6). Run on an emulator: `./gradlew :library:assembleDebugAndroidTest`, `adb install -r -t library/build/outputs/apk/androidTest/debug/library-debug-androidTest.apk`, `adb shell am instrument -w -e class com.plumemu.library.StaticInitialisersTest com.plumemu.library.test/androidx.test.runner.AndroidJUnitRunner`; its JVM partner is the guard `scripts/guards/android-regex.py` |
| `src/androidTest/.../scan/ScanTimingTest.kt` | 10,000-file scan timing through the real picker and provider, then with the naming pass and the real packs; with `scanFolder=<folder>`, a real library layout with the shipped rules |
| `src/androidTest/.../scan/TreePickerActivity.kt` | Test-only activity that opens the folder picker |

## Arcade names

Arcade titles come from the `arcade` name pack of the `identify` module (MAME `-listxml`, sets
from BSD-3-Clause, GPL-2.0+ and LGPL-2.1+ drivers), read by the naming pass: `sf2.zip` shows its
full title, BIOS and device sets are kept but not listed. The open question about
`mamenames.xml` is closed; `CatalogSettings.arcadeNames` is gone.
