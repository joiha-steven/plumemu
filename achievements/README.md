# achievements

RetroAchievements: reads each game's achievements and the person's progress from the
RetroAchievements Web API with the person's own user name and Web API key. Plumemu shows
achievements; it never awards them (emulators do).

Namespace: `com.plumemu.achievements`.

- `api/`: the Web API client.
- `account/`: the person's sign-in check and the key's encryption.
- `hash/`: RetroAchievements' file hashes, rewritten in Kotlin from rcheevos' `rc_hash` (MIT).
  `RaHasher.hash(consoleId, rom)` gives the MD5 a game is listed under, for cartridge systems and
  Arcade (one file per call, streamed, first 64 MiB at most); a `.zip` is read for its first ROM
  entry, other archives need a decoder and say so. It also covers the disc systems (CD systems, Dreamcast
  `.gdi`, GameCube and Wii) and the `.gcz`, `.wbfs` and `.chd` containers (`.chd`: version 5, hunks `zlib`, `lzma`, `cdzl`, `cdlz` or
  stored, read lazily; other codecs need a decoder and say so).
- `match/`: which RetroAchievements game a ROM is. `RaSystems` maps Plumemu's system ids to
  RetroAchievements consoles; `RaMatcher.match(systemId, rom)` hashes the file and looks the hash
  up in the console's game list: `Matched`, `NoSet`, `CannotHash`, `Unreadable`, `NotSupported` or
  `Failed` (the Web API failure, to show). `RaMatcher.matchHash(systemId, md5)` looks a stored
  hash up again without reading the file, so a set added later is found. Storing the answer is
  the caller's (core-data's `ra_match`, which caches the hash by file).

Depends on no other Plumemu module; `app` wires it.

## Files

| File | What it holds |
|---|---|
| `api/RetroAchievementsClient.kt` | The four calls: `profile` (sign-in check, ULID, points, picture), `gameList` (a system's games and MD5 hashes, saved 14 days), `gameProgress` (one game's achievements and the user's counts), `completion` (every game with progress, all pages). Building one does nothing until a call |
| `api/RaHttp.kt` | `WebApiKey`; one GET with the key, one wait on 429 / 5xx honouring Retry-After up to a cap, log lines with the key and user blanked |
| `api/RaResult.kt` | `RaResult` (`Ok`, `WrongKey`, `RateLimited`, `Busy`, `NoConnection`, `UnknownUser`, `BadAnswer`) and `RaStatus`, the one place HTTP codes get a meaning |
| `api/RaModels.kt` | `RaProfile`, `RaGame`, `RaGameList`, `RaGameProgress`, `RaAchievement`, `RaCompletion`, `AwardKind`; `RaMedia` (badge, locked badge and picture URLs) |
| `api/RaParsers.kt`, `api/Lenient.kt` | Reading the answers field by field; trigger logic is never read |
| `api/GameListCache.kt` | Each system's game list on disk: only Plumemu's fields, temp file and rename, one lock per system |
| `api/Redact.kt`, `api/Await.kt` | Blanking `y=` / `u=` in logged text; OkHttp's call as a cancellable suspend call |
| `account/SignInCheck.kt` | The sign-in check: one profile call with the typed name and key |
| `account/ApiKeyCipher.kt` | `ApiKeyCipher`, AES-GCM in a versioned envelope, `KeystoreApiKeyCipher` with its own Android Keystore alias |
| `match/RaSystems.kt` | Plumemu system id to RetroAchievements console id, every system both have; several Plumemu systems share a console (Neo Geo Pocket Color, SuperGrafx, the Arcade folders...) |
| `match/RaMatcher.kt` | `RaMatch` and `RaMatcher`: hash first (no request for a file that cannot be hashed), then the saved or fetched game list; a stale list without the hash is a failure, never "no set" |
| `hash/ChdImage.kt`, `hash/ChdMap.kt`, `hash/CdEcc.kt` | `.chd` version 5: header, metadata, hunks decoded on demand into a small cache (LZMA through XZ for Java); the compressed map (Huffman-coded, with its CRC) in `ChdMap.kt`; CD sync/EDC/ECC rebuilt for `cdlz`/`cdzl` sectors |
| `hash/DiscFiles.kt` | `RawDiscFile`, `CsoDiscFile`, and `ChdTrackFile` / `ChdFlatFile` (a CHD's CD track or whole image as one flat byte range) |
| `src/main/assets/licenses/rcheevos-MIT.txt` | rcheevos' MIT licence text, shown in the app's open-source licences |

Tests run against MockWebServer with fixtures in `src/test/resources/ra/`: every endpoint's good
answer, and 401, 429 with and without Retry-After, 5xx, malformed JSON, empty body, a dropped or
timed-out connection and an unknown user on every endpoint; the game-list cache (fresh, expired,
stale fallback, damaged, one fetch for two callers); the key in no log line, result text or saved
file. The Keystore round trip is an instrumented test. `RaSystemsTest` reads `platforms.json` from
the repository and lists the RetroAchievements consoles Plumemu has no system for; `RaMatcherTest`
covers every `RaMatch` outcome and a stale list.
