# identify

Built-in game identification, offline.
This module reads cheap fingerprints from ROM files without unpacking them or reading them whole.
Pure Kotlin and JDK: no Android classes, no project dependencies. `library` and `app` depend on
it; it depends on nothing.

Namespace: `com.plumemu.identify`.

## What is here so far

Layer 2 of the identification, cartridges and zips:

- **Byte sources**: `RandomAccessSource` (positional reads, safe from several threads),
  `FileSource` (a `File` or a `FileChannel`, e.g. from a SAF `ParcelFileDescriptor`),
  `CountingSource` (counts what was read, for the read-volume measurements).
  `SiblingOpener` opens the files a `.cue`/`.gdi`/`.m3u` names (passed to `FingerprintReader.read`).
- **Zip**: the CRC and size of the ROM entry from the central directory, never the local headers,
  never decompressed. Zip64, comments (including one that hides a fake end record), data in
  front (self-extracting), UTF-8 and CP437 names
  (CP437 decoded as ISO-8859-1), a capped directory (4 MB) and broken archives (a note, no
  exception).
- **Cartridge headers**: GBA and NDS game codes, Game Boy titles (and the Color manufacturer code
  when it can be told apart), N64 ids in all three byte orders, Mega Drive / 32X serials
  (including `.smd` interleaved dumps), SNES titles and extended-header codes; iNES, fwNES (FDS),
  A78 and LNX headers left out of the CRC; SNES copier headers left out by length.
- **Whole-file CRC** (`Depth.FULL`) of loose files up to 64 MB, header skipped, N64 and SMD put
  back in the order name databases list.
- **Header-less CRC of zipped ROMs** (`Depth.FULL`, `fingerprint/ZipEntryContent.kt`):
  a zip entry whose format may carry a header or a byte order (iNES, fwNES, A78, LNX, SNES
  copier, SMD, N64 v64 / n64; `CartridgeReader.storedFormMayDiffer`), up to the same 64 MB, is
  unpacked once (stored or deflate, 64 KB at a time) and gets the loose file's CRC. Its first
  128 KB go through the loose-file header detection; with no header found nothing more is read.
  The inflated length and the CRC of the whole stream must match the directory, else a note and
  the directory CRC stays. `Fingerprints.archiveCrc32` always keeps the directory's CRC, and
  `fixKeys()` lists both, so a fix made after a QUICK read is found after a FULL one. A 7z entry
  is never unpacked (its data is LZMA or LZMA2 in nearly every archive): its CRC stays the stored
  form's, with a note at FULL.
- **Mega Drive / 32X / Sega CD product code**: the middle of the serial at 0x180 ("GM T-99999
  -00" gives "T-99999"), the form MAME's software lists use (`MegaDriveReader.productCode`).

Measured in `FingerprintReaderTest.quickReadsOnlyAFewKilobytes` (`Depth.QUICK`, bytes read):
4 MB SNES 168, 16 MB GBA 200, 8 MB N64 72, 4 MB SMD 776 (the plain header attempt plus the
256 interleaved header bytes), 4 MB zip 1,109, the same zip with a 5,000-byte comment 8,277
(the 8 KB end-record window), with a 60,000-byte comment 65,642 (the full window).

## Discs

`fingerprint/disc/`: the serial and title a disc carries, from the first few KB of its image,
through the containers people use, the same at `QUICK` and `FULL` (a disc never gets a
whole-file CRC). Every read of one disc, sibling files included, is capped at 64 KB
(`DiscReader.DEFAULT_CAP`); past the cap, or on anything damaged, the read stops with a note.

- **Sector images**: plain 2048-byte sectors, raw 2352-byte sectors (Mode 1 data at +16, Mode 2
  Form 1 at +24, told by the sync pattern and the mode byte), Mode 2 2336, raw sectors with
  96 bytes of subchannel (2448: CHD frames, `.img` dumps); a CHD track stored cooked as MODE2
  (2336) has its data after the 8-byte subheader.
- **ISO 9660**: PVD at sector 16, root directory, one level below (`PSP_GAME/`), file heads only;
  a directory is read a sector at a time until the name turns up.
- **Serials**, normalised to "PREFIX-DIGITS" (3 to 5 letters, 3 to 7 digits, e.g. "SLUS-99999"): PS1 and PS2 from `SYSTEM.CNF` (`BOOT` / `BOOT2`); PSP
  from `UMD_DATA.BIN`, title and `DISC_ID` from `PSP_GAME/PARAM.SFO`; PBP (`EBOOT.PBP`, PS1
  classics) from its `PARAM.SFO`; GameCube / Wii game id and title from the disc header (plain,
  WBFS, RVZ, WIA, GCZ); Saturn, Sega CD and Dreamcast (`IP.BIN`) from the system area at sector
  0 of the data track.
- **Containers**: CSO v1 (deflate blocks, only those needed, index shift and padding), CHD v5
  (header SHA-1, compressed or plain map, hunks stored / `zlib` / `cdzl` / `lzma` / `cdlz`, the data track from
  the `CHTR` / `CHT2` / `CHGD` metadata: GD-ROM track 3 for a Dreamcast), cue sheets (a Dreamcast
  cue gives the first data track after `REM HIGH-DENSITY AREA`, or track 3 or later for the
  `dreamcast` system), `.gdi` and `.m3u` through `SiblingOpener` (an `.m3u` is followed once,
  never into another playlist).
- **Titles**: `Fingerprints.titleIsCanonical` is true for PARAM.SFO `TITLE`, the GameCube / Wii
  header title and the Nintendo DS banner (English line, read by `NdsReader`, CRC checked).
- **Keys**: a disc adds `code:<kind>:<serial>`; a CHD adds `chd:<raw-data SHA-1>` after it, so a
  CHD whose hunks cannot be decompressed still has a stable key.

Measured in `disc/DiscRobustnessTest.everyFormatIsReadUnderTheCap` (`QUICK`, bytes read, sibling
files included, generated images): PS1 ISO 2,966, PS1 raw BIN 2,914, PS2 ISO 2,951, PSP ISO
5,112, PSP CSO 458, PBP 140, CD CHD (cdzl) 532, DVD CHD (zlib) 987, uncompressed CHD 3,046,
GameCube ISO 148, WBFS 648, RVZ 136, WIA 136, GCZ 140, Saturn BIN 560, Sega CD ISO 660, cue +
BIN 2,951, `.gdi` + track 3 592, `.m3u` + cue + BIN 2,968, Dreamcast CHD 894; Nintendo DS with
banner 2,472; CD CHD (cdlz) 2,794, CD CHD with 64 KB hunks (cdlz) 3,490, DVD CHD (lzma) 2,698
(fixtures with real LZMA, `ChdLzmaTest`). Generated
compressed hunks are mostly zeros, so a real CHD or CSO reads more (each needed hunk or block
compressed as it is); the cap holds either way.

### Not read, and why

| What | Why |
|---|---|
| CHD hunks in `cdfl`, `flac`, `zstd`, `cdzs`, `huff` | No FLAC, Zstandard or CHD-Huffman decoder in the allowed libraries. chdman picks `cdlz` / `lzma` for most data hunks, which are read; a data hunk stored in one of these gives only the `chd:` key. |
| CHD hunks in a parent CHD, CHD v3 / v4 hunks | Rare; v4 still gives its raw-data SHA-1 |
| ZSO, CSO v2 | LZ4 blocks; no LZ4 decoder |
| DiscJuggler `.cdi` | Its track layout is a separate format; Dreamcast games are read from `.gdi` and CHD |
| PC Engine CD / TurboGrafx-CD, 3DO, Neo Geo CD | No serial this module knows on those discs; identified by name (layer 4). A CHD of these systems still gives its `chd:` key |
| The CHD map CRC | It covers every hunk, and finding the first hunks needs only part of the map; a damaged map shows up as a hunk that fails to decompress |

## Switch and 3DS title ids

Nothing is decrypted and no key is used or shipped: only plain names and headers.

- **Switch** (`fingerprint/nx/SwitchReader.kt`, system `switch`, or `.nsp`/`.nsz`/`.xci`/`.xcz`
  without a system). NSP and NSZ are PFS0 partitions: the header and the file names are read
  (under 1 KB for a real NSP, at most 64 KB). A ticket or certificate named by its rights id
  (`<title id><14 zeros><key generation>.tik` / `.cert`) gives the title id of the content it
  unlocks; a `<16-digit title id>.cnmt.xml` name gives it too (the usual 32-digit name is a content
  id, not a title id). XCI and XCZ carry no title id in clear (the card header has none, the
  partitions list NCAs by content id, and NCA headers are encrypted), so they are named by the
  file name only and not read. The file name gives the first `01` + 14 hex digits in any case,
  bracketed or glued to the title. The content's id wins over the file name (a note says when they
  differ). `Fingerprints.titleId` is the content's own id and `titleKind` says base, update
  (+0x800) or DLC (+0x1000 + n); `productCode` is the base id (`CodeKind.SWITCH`), so the pack
  lookup names a game, its updates and its DLC alike, EXACT. Cached fingerprints from before
  this step have no id: `Identifier` adds the file name's, without opening the file.
- **3DS** (`fingerprint/cart/N3dsReader.kt`, system `n3ds` / `3ds`): `.cxi` (NCCH), `.3ds` and
  `.cci` (NCSD, partition 0's NCCH), `.cia` (title id from the TMD, always plain; the game code
  only when content 0 is not encrypted with its title key, which most CIAs are). The NCCH header
  gives the program id (`titleId`) and the product code, whose last 4 characters are the game
  code (`CodeKind.N3DS`, "ZZZE"). The bundled `n3ds` pack lists these codes (the pack tool's
  N3DS rule: GameTDB's 3dstdb ids and "CTR-P-ZZZE" DAT serials), so a readable header names
  its game EXACT.
- Fix keys: a Switch file gives `code:SWITCH:<base id>`, a 3DS file `code:N3DS:<code>`; library
  stores no fix under `code:` (a fix belongs to the file's content; the base id is shared by the
  game, its updates and DLC, and every dump and version of a title shares its own id). Switch
  files are too large for a whole-file CRC, so their fixes stay on the row key.

## LZMA and LZMA2

`codec/lzma/` is a Kotlin port of the decoder in Igor Pavlov's LZMA SDK (public domain):
`LzmaDecoder` and `RangeDecoder` follow `Java/SevenZip/Compression/LZMA/Decoder.java` with the
checks of `C/LzmaDec.c`; `lzma2Decode` follows `C/Lzma2Dec.c`. Entry points:
`lzmaDecode(input, props, outputSize, knownSize, exactInput)` (raw LZMA: CHD `lzma`/`cdlz`
hunks, 7z LZMA streams), `lzma2Decode(input, dictionaryProp, outputSize)` (7z LZMA2),
`lzmaAloneDecode` (`.lzma` files, tests). Compressed bytes come through `CompressedInput`
(`ByteArrayInput`, or `SourceInput` over a `RandomAccessSource` range, whose length is a hard
cap). Bounded: the caller states the most output; the output buffer is the dictionary, grows
from 64 KB up to that cap and never follows the declared dictionary size; decoding stops when the
cap is reached; reading past the input, an invalid properties byte, a match beyond the bytes
produced or a bad LZMA2 chunk throws `LzmaException` (not an `IOException`: callers turn it into
a note). With `exactInput` (a whole CHD hunk) the stream must also use every compressed byte and
end with the range coder at 0, as MAME checks; a `.lzma` header's declared size must be
delivered. The only fixed allocation is the literal table, at most 6 MB for lc=8, lp=4 (24 KB
for lc+lp <= 4, the LZMA2 limit; the 7z path refuses lc+lp > 4). Measured: 105 MB/s on a
laptop; the first 4 KB of a 200 KB stream used 1,045 compressed bytes. CHD properties: lc=3,
lp=0, pb=2, dictionary = max(4096, input size) rounded up to 2^n or 3*2^n (MAME
`chd_lzma_compressor::configure_properties`, level 6), no end marker; for `cdlz` the input size
is frames * 2352 (see `src/test/resources/lzma/make_fixtures.py`). The decoder is given the
input size itself as the dictionary: a match can never reach further back than that.

**CHD** (`fingerprint/disc/ChdLzma.kt`): an `lzma` hunk is one raw LZMA stream; a `cdlz` hunk is
the ECC bitmap, the 2- or 3-byte length of the LZMA part, the sectors as LZMA and the subcode
deflated (not read). A hunk is decoded only as far as the read needs (whole frames for a CD),
again further when a later read needs more, from compressed bytes fetched once while it is the
last hunk read (`HunkBytes` keeps only the last LZMA hunk); a whole hunk gets every MAME check. Damaged data is a note
(`chd: hunk n: LZMA data damaged (...)`) and the CHD SHA-1 stays.

## 7z

`fingerprint/SevenZipDirectory.kt` lists a 7z from its header, the way `ZipDirectory` lists a
zip, and `FingerprintReader` picks the ROM entry, its CRC (`CrcSource.SEVEN_ZIP_HEADER`) and
size the same way, at both depths. Read: the 32-byte signature header (CRC checked), the next
header (CRC checked) and, when 7-Zip packed it (`kEncodedHeader`, nearly always), the one packed
stream holding it: LZMA, LZMA2 or Copy, decoded to at most 1 MB, CRC checked. The next header
and the packed stream together are read only up to 64 KB. Then the sizes and CRCs in `kSubStreamsInfo` (solid archives too) and the names, empty
files and directories in `kFilesInfo`; other file properties, whatever their id, are skipped by
their size. An archive of only empty files and directories has no streams info at all. File data
is never decompressed. Every count in the header is checked against the bytes left before
anything is allocated.

Not read (a note each, no exception): a header packed with another coder (BCJ, PPMd, Deflate,
...) or encrypted (AES, `7z -mhe`), LZMA header properties with lc + lp above 4, names or folders
stored outside the header, a header over the read or decoded limit, more than 65,536 files, any
damage (CRC, truncation, bad LZMA). An archive whose file data is encrypted but whose header is
not still gives its CRCs (of the files before encryption), with a note.

Measured in `SevenZipDirectoryTest` (`QUICK`, bytes read, archives of 3.5 to 10 KB): plain
header 137, LZMA header 190, LZMA2 header 181, solid with 3 files 205, an empty file and a
directory 182: the signature header, the next header and the packed header, whatever the size
of the files.

## Files

| File | What it holds |
|---|---|
| `build.gradle.kts` | Android library module; no dependencies besides JUnit for tests |
| `src/main/.../source/RandomAccessSource.kt` | The byte source interface, `readFully`, `SiblingOpener` |
| `src/main/.../source/FileSource.kt` | `FileChannel` positional reads; from a `File` or a channel |
| `src/main/.../source/CountingSource.kt` | Decorator that counts bytes read and calls |
| `src/test/.../source/ByteArraySource.kt` | Bytes in memory, tests only |
| `src/main/.../fingerprint/Fingerprints.kt` | The result: `Fingerprints`, `Container`, `CrcSource`, `CodeKind`, `fixKeys()` / `fixKey()` |
| `src/main/.../fingerprint/FingerprintReader.kt` | Entry point: `read(source, fileName, systemId, depth)`, `Depth` |
| `src/main/.../fingerprint/ZipDirectory.kt` | End record, Zip64, central directory; `pickRomEntry` |
| `src/main/.../fingerprint/SevenZipDirectory.kt` | 7z signature header, encoded header (LZMA / LZMA2 / Copy), streams and files info |
| `src/main/.../codec/lzma/Lzma.kt` | `lzmaDecode` (raw LZMA, `exactInput` for CHD hunks), `lzmaAloneDecode` (`.lzma`) |
| `src/main/.../codec/lzma/Lzma2.kt` | `lzma2Decode`: LZMA2 chunks over the same decoder |
| `src/main/.../codec/lzma/LzmaDecoder.kt` | The LZMA state machine: literals, matches, lengths, distances |
| `src/main/.../codec/lzma/RangeDecoder.kt` | Range decoder and bit-tree decoding |
| `src/main/.../codec/lzma/OutputWindow.kt` | Output buffer that is also the dictionary, capped |
| `src/main/.../codec/lzma/LzmaProperties.kt` | lc / lp / pb / dictionary from a properties byte, a 5-byte blob or an LZMA2 byte |
| `src/main/.../codec/lzma/CompressedInput.kt` | Compressed input with a hard end: `ByteArrayInput`, `SourceInput`, `ChunkInput` |
| `src/main/.../codec/lzma/LzmaException.kt` | Broken LZMA data (not an `IOException`) |
| `src/main/.../fingerprint/WholeFileCrc.kt` | Streamed CRC-32 with header skip and N64/SMD normalising (`ContentCrc`) |
| `src/main/.../fingerprint/ZipEntryContent.kt` | FULL: unpacks one zip entry that may carry a header, for its header-less CRC |
| `src/main/.../fingerprint/ByteFields.kt` | Little-endian fields, header text, game code check |
| `src/main/.../fingerprint/cart/CartridgeReader.kt` | Reader interface, `CartridgeHeader`, `ContentLayout` |
| `src/main/.../fingerprint/cart/CartridgeReaders.kt` | System id and extension to readers (the id table is in its KDoc) |
| `src/main/.../fingerprint/cart/HandheldReaders.kt` | GBA, NDS (with the banner title), Game Boy / Color |
| `src/main/.../fingerprint/cart/Nintendo64Reader.kt` | N64 byte orders, id, title |
| `src/main/.../fingerprint/cart/MegaDriveReader.kt` | Mega Drive / Genesis / 32X, SMD de-interleave |
| `src/main/.../fingerprint/cart/SnesReader.kt` | Copier header, LoROM/HiROM internal header |
| `src/main/.../fingerprint/cart/HeaderedRomReaders.kt` | NES (iNES/NES 2.0), FDS, Atari 7800, Lynx |
| `src/main/.../fingerprint/cart/N3dsReader.kt` | 3DS NCCH, NCSD and CIA: game code, program id, its kind |
| `src/main/.../fingerprint/nx/SwitchReader.kt` | Switch title ids: PFS0 names (NSP, NSZ), XCI, file names; base, update and DLC ids |
| `src/main/.../fingerprint/nx/SwitchTitleIds.kt` | The public file-name door to those rules (id, base id, kind; is a system the Switch), for library's grouping |
| `src/main/.../fingerprint/disc/DiscReader.kt` | Disc entry point: container by signature, extension, system; the read cap; the table of what is read |
| `src/main/.../fingerprint/disc/DiscSupport.kt` | `DiscId`, `NotRead`, `ReadCap` / `CappedSource`, `inflate`, small text files |
| `src/main/.../fingerprint/disc/SectorImage.kt` | Logical 2048-byte sectors over 2048 / 2352 / 2336 / 2448-byte storage |
| `src/main/.../fingerprint/disc/Iso9660.kt` | PVD, directory records, file heads |
| `src/main/.../fingerprint/disc/PlayStationDiscs.kt` | `SYSTEM.CNF`, PSP files, PBP, serial normalising |
| `src/main/.../fingerprint/disc/ParamSfo.kt` | PARAM.SFO parser |
| `src/main/.../fingerprint/disc/SegaDiscs.kt` | Saturn, Sega CD, Dreamcast system areas |
| `src/main/.../fingerprint/disc/NintendoDiscs.kt` | GameCube / Wii header; WBFS, RVZ, WIA, GCZ |
| `src/main/.../fingerprint/disc/CsoSource.kt` | CSO v1 as a source, block by block |
| `src/main/.../fingerprint/disc/ChdSource.kt` | CHD header; CHD v5 as a source, hunk by hunk (`zlib`, `cdzl`, `lzma`, `cdlz`) |
| `src/main/.../fingerprint/disc/ChdLzma.kt` | CHD `lzma` and `cdlz` hunks, decoded as far as needed; `HunkBytes` keeps the compressed bytes |
| `src/main/.../fingerprint/disc/ChdMap.kt` | CHD v5 maps: plain, and compressed (MAME Huffman, bit reader) |
| `src/main/.../fingerprint/disc/ChdTracks.kt` | The data track (frame, type) from CHD track metadata; GD-ROM track 3 |
| `src/main/.../fingerprint/disc/TrackLists.kt` | Cue sheet, `.gdi` and `.m3u` parsing |
| `src/main/.../fingerprint/disc/SheetReferences.kt` | Public: every file name a `.cue`, `.gdi` or `.m3u` lists (the scan hides them as parts of the sheet's game); tests `SheetReferencesTest` |
| `src/test/.../fingerprint/disc/DiscImages.kt` | Generated ISO 9660 images, raw sectors, PARAM.SFO, PBP, Sega areas, Nintendo headers, in-memory siblings |
| `src/test/.../fingerprint/disc/PackedImages.kt` | Generated CSO, CHD v5 (compressed map, cdzl, zlib), GCZ, WBFS, RVZ / WIA |
| `src/test/.../fingerprint/disc/PlayStationDiscTest.kt` | PS1, PS2, PSP, PBP; serial spellings; PARAM.SFO bounds |
| `src/test/.../fingerprint/disc/PackedDiscTest.kt` | CSO, ZSO, CHD (codecs, maps, self and parent hunks, SHA-1 key), cue, `.m3u`, `.gdi`, GD-ROM CHD |
| `src/test/.../fingerprint/disc/NintendoSegaDiscTest.kt` | GameCube / Wii and containers, Saturn, Sega CD, NDS banner, skipped systems, `.bin` fallback |
| `src/test/.../fingerprint/disc/ChdMapTest.kt` | A hand-written compressed map (unbalanced tree, long run, pseudo types), bad trees, short maps |
| `src/test/.../fingerprint/disc/DiscRobustnessTest.kt` | Bytes read per format, truncated and damaged images and siblings, the cap, sibling I/O errors |
| `src/test/.../fingerprint/disc/ChdLzmaTest.kt` | `cdlz` (2- and 3-byte length) and `lzma` CHDs: serial, whole hunks, damage, exact input |
| `src/test/.../fingerprint/ContainerFixtures.kt` | Reads `src/test/resources/containers/manifest.txt` |
| `src/test/.../fingerprint/SevenZipDirectoryTest.kt` | 7z listings, notes for unread headers, limits, truncation, fuzzed headers, huge counts |
| `src/test/.../codec/lzma/LzmaFixtures.kt` | Reads `src/test/resources/lzma/manifest.txt` |
| `src/test/.../codec/lzma/LzmaDecoderTest.kt` | Every fixture, prefixes, LZMA2 chunk kinds, speed |
| `src/test/.../codec/lzma/LzmaRobustnessTest.kt` | Truncated, flipped and hostile streams; allocation bounds; I/O errors |
| `src/test/.../codec/lzma/LzmaKnownSizeTest.kt` | Exact input of a hunk, declared `.lzma` sizes, matches past the end |
| `src/test/resources/lzma/make_fixtures.py` | Writes the LZMA / LZMA2 fixtures (Python standard library) |
| `src/test/resources/containers/make_fixtures.py` | Writes the CHD LZMA hunks and the 7z archives (Python standard library) |
| `src/test/.../fingerprint/FakeRoms.kt` | Generated fake ROMs, headers and zips (no real data) |
| `src/test/.../fingerprint/ZipDirectoryTest.kt` | Zip listing, Zip64, comments, names, broken zips, cap |
| `src/test/.../fingerprint/ZipEntryContentTest.kt` | Zipped NES, SNES copier, SMD, v64 / n64 at QUICK and FULL; stored entries; damage; limits |
| `src/test/.../fingerprint/cart/CartridgeFingerprintTest.kt` | Every header format; same CRC with and without headers and byte orders |
| `src/test/.../fingerprint/cart/N3dsReaderTest.kt` | Generated NCCH, NCSD (media unit), CIA (plain and encrypted content), kinds, cut and damaged files |
| `src/test/.../fingerprint/nx/SwitchReaderTest.kt` | Generated NSP / NSZ tables, XCI, file-name ids, kinds, damaged tables, bytes read |
| `src/test/.../fingerprint/FingerprintReaderTest.kt` | Containers, depths, bytes read, limits, fix keys, I/O errors, threads |
| `src/test/.../source/SourcesTest.kt` | Sources, short reads, counting |

## Name packs, name matching and the Identifier

Layers 1, 3 and 4 of the identification and the facade over all of them. The bundled packs are
built by a Python tool (section at the end).

- **Packs** (`pack/`): one file per system, format v1 in [PACKS.md](PACKS.md). `NamePack` maps
  a file read-only and answers `byCrc`, `byCode`, `title`, `releasesOf`, `allTitles`,
  `byShortName`, `sources` by binary search, creating only the results. `PackStore` unpacks a
  bundled pack on first use (LZMA from the assets, or gzip, to a temp file, structure, system id and
  SHA-256 checked, then an atomic rename), keeps a lock per system, and installs data updates
  with the same checks. A pack that fails a lookup later is reinstalled once, then skipped
  for the session. `PackWriter` (`ConsolePackBuilder`, `ArcadePackBuilder`) writes
  packs for tests and measurements; the Python tool must write the same bytes.
- **Arcade** (`arcade/`): the arcade system ids in one place (`ArcadeSystems`), all reading the
  one `arcade` pack; a file's short name gives title, year, maker, parent, and `hide` for BIOS,
  device and other non-game sets. Fix key `arcade:<shortname>`. The title shown is the short title
  (`ArcadeTitles.shortTitle`: cut at the first " / ", bracket groups removed); the full
  description is `Identification.fullTitle` and `ScrapeHints.canonicalTitle`; the regions are the
  ones the description names (`ArcadeTitles.regions`: every bracket group split at `,` `/` `+`
  "and", a part that starts with a region word). MAME 2003-Plus short names are found through
  alias records in the pack.
- **Name matching** (`name/`): `TitleCleaner` turns a file name into a title (site marks,
  scene names, dots and underscores, release numbers, version, update and Switch codes; the order
  is in its KDoc); `TitleCase.forSystem` title-cases an all-lower or ALL-UPPER fallback (never an
  arcade short name); `NameCandidates` adds the bolder
  cleanings (words split at case changes, a "The" moved to the front, a DS / GBA release number
  dropped) that are accepted only when they name a pack title; `MatchKey` evens out spelling;
  `NameMatcher` tries every candidate for an equal key (and a name written as one word for its
  words run together) before any fuzzy score: Damerau-Levenshtein similarity of at least 0.85
  with a 0.05 margin over the next title, the same numbers on both sides, and no edition word or
  whole short word of difference ("GB" / "GBC", "Vol"). Among titles with the same key it takes
  the one spelled like the file (case-sensitive edit distance), then one released in the file's
  region, then the higher-ranked source.
- **Identifier** (`Identifier.kt`, `Identification.kt`): arcade short name, then fingerprints,
  CRC, product code, canonical header title, name match, file name; returns the name, regions,
  confidence, method, fix keys and `ScrapeHints` (what ScreenScraper needs so the file is not
  read twice). `NameTrust` orders names from different places: file name, ScreenScraper likely, identifier
  likely, ScreenScraper exact, identifier exact, the game the person picked on ScreenScraper
  (`SCRAPER_PERSON_CHOICE`), and the person's own fix, which always wins. Stored by
  name in `game.name_trust`: never rename a value; a build that does not know one reads `FILE_NAME`.
- **Logging**: `IdentifyLog` (stderr by default; the app passes Android's log). Lines name
  system ids and exception classes, never titles or file names.

The 10,000-file measurement (QUICK bytes and time per kind of file, FULL over loose cartridges,
the real packs' unpack time and index memory) is in
the opt-in `IdentifyMeasurementTest`.

Measured in `NameMatcherTimingTest` (generated pack of 5,000 titles, 10,000 lookups, laptop
JVM): index 391 KB of arrays, built in about 24 ms on first use; 0.034 ms per lookup. With the
run-together key table: 430 KB, built in 35 ms, 0.031 ms per lookup (machine under load).

| File | What it holds |
|---|---|
| `PACKS.md` | Pack format v1 byte by byte, region and language bits, the bundled index |
| `src/main/.../IdentifyLog.kt` | Where log lines go |
| `src/main/.../Identification.kt` | `IdentifyRequest`, `Identification`, `Confidence`, `Method`, `ScrapeHints`, `NameTrust` |
| `src/main/.../Identifier.kt` | The layers in order; one name matcher per pack |
| `src/main/.../LaunchFileSystems.kt` | Systems whose files hold only a launch id (`steam`): never read, named from the file name |
| `src/main/.../pack/PackFormat.kt` | Constants, `PackKind`, `PackMasks` (region and language bits) |
| `src/main/.../pack/PackHeader.kt` | Header and section table checks on open |
| `src/main/.../pack/NamePack.kt` | The mapped reader and its result types |
| `src/main/.../pack/PackWriter.kt` | `ConsolePackBuilder`, `ArcadePackBuilder`, `ArcadeFlags`, `PackSource` |
| `src/main/.../pack/BundledPacks.kt` | `BundledPacks` interface, `BundledIndex` (packs.txt parser) |
| `src/main/.../pack/PackStore.kt` | Install on first use, updates, locks, `InstallResult` |
| `src/main/.../android/AssetBundledPacks.kt` | The module's only Android class: opens the assets (the `.gz` name, else the plain name the APK build leaves) |
| `src/main/.../arcade/ArcadeNames.kt` | `ArcadeSystems`, `ArcadeTitles` (short titles, regions and bracket details of a description), `ArcadeName`, `ArcadeLookup` |
| `src/main/.../name/TitleCleaner.kt` | Clean title, sort name, regions, disc tag and variant key from a file name (file-name habits included) |
| `src/main/.../name/SiteMarks.kt` | Download-site marks: domains, the list of known bare site names, where outside brackets they are cut |
| `src/main/.../name/ReleaseTags.kt` | Version, update and Switch codes left out of the title and variant key; scene platform tags and flags |
| `src/main/.../name/TitleCase.kt` | Title case for an all-lower / ALL-UPPER shown fallback (`forSystem`, not arcade): small words, acronyms, roman numerals, codes kept |
| `src/main/.../name/NameCandidates.kt` | The ordered cleanings the matcher tries (split words, "The" to the front, DS / GBA release number) |
| `src/main/.../name/HackNames.kt` | When a serial or cartridge code must not name a file: a hack or translation tag in the file name, or file title and code name that share a word and each have another |
| `src/main/.../name/Region.kt` | Region codes (USA, EUR, JPN, ...), their No-Intro spelling (`noIntroName`, as packs write it), parsing of `(USA, Europe)` / `(JU)` groups, names only (`parseNamed`: `[US]`, scene `EUR`), `fromNoIntroNames` |
| `src/main/.../name/MatchKey.kt` | Spelling normalisation for matching; `compact` for names written as one word |
| `src/main/.../name/NameMatcher.kt` | Exact key table, run-together key table, word index, scoring, edition guard, spelling choice among equal keys |
| `src/main/.../name/TitleSearch.kt` | Fix name…'s ranking: same key, key starts with the query, every word starts a word, then the matcher's similarity; `TitleSearchTest` |
| `src/main/.../name/DistanceScratch.kt` | Bounded Damerau-Levenshtein (optimal string alignment) |
| `src/main/assets/…/packs.txt` | Bundled pack index, written by the Python pack tool |
| `<system>.pack.lzma` | The bundled packs, written by the Python pack tool (do not edit) |
| `src/test/.../pack/TestPacks.kt` | Generated packs, `FakeBundled`, `RecordingLog` |
| `src/test/.../pack/NamePackTest.kt` | Round trips, lookups, rejected and damaged packs |
| `src/test/.../pack/PackStoreTest.kt` | Install, aliases, upgrades, failed updates, stale temps, broken packs, threads |
| `src/test/.../pack/PackGoldenTest.kt` | Exact bytes of two tiny packs; pinned region and language lists |
| `src/test/resources/packs/` | The golden packs (`console.pack`, `arcade.pack`) for the Python tool's tests |
| `src/test/resources/packs/python/` | The Python tool's build of its made-up fixture |
| `src/test/.../pack/PythonPacksTest.kt` | Opens the tool's fixture packs and the real bundled packs with `NamePack`; checks the index checksums |
| `src/test/.../name/TitleCleanerTest.kt` | 99-row cleaning table (rows from the corpus failures: site marks, scene, dots, case, codes); variant keys; every region's pack spelling reads back; the Python port's rows give the same titles and regions |
| `src/test/.../name/NameMatcherTest.kt` | 52-row match table, keys; same-key spelling choice, one-word names, edition guard, candidate order |
| `src/test/.../name/NameCandidatesTest.kt` | The candidate list per name and system |
| `src/test/.../name/TitleCaseTest.kt` | Title-case table, acronyms, arcade left alone |
| `src/test/.../name/NameMatcherTimingTest.kt` | Time and memory on 5,000 titles |
| `src/test/.../arcade/ArcadeLookupTest.kt` | Parent, clone, BIOS, device, system ids |
| `src/test/.../IdentifierTest.kt` | End to end on generated files |
| `src/test/.../measure/MeasureCorpus.kt` | The 10,000 generated files and what their packs list |
| `src/test/.../measure/IdentifyMeasurementTest.kt` | Opt-in 10,000-file measurement and the real packs' unpack and index cost |

## Where the packs come from

A Python tool (standard library only, in the build tools) writes `<system>.pack.lzma` and `packs.txt` from its list of sources: MAME's software lists (CC0) and MAME's `-listxml` (sets from BSD-3,
GPL-2.0+ and LGPL-2.1+ driver files: listing facts only), and, for development and alpha builds, the No-Intro, Redump and GameTDB files
downloaded by hand into a folder that is never committed.
Each source can be switched off there; every pack lists its sources in `SRCS`. The Python writer
gives the golden packs byte for byte, and `PythonPacksTest` opens its output with this module's
reader.

The bundled files are `.pack.lzma` (LZMA_Alone, preset 9 extreme): 3,252,567 bytes for the 85 packs
(the APK held them deflated at 4,519,898 before). The arcade pack holds 27,265 sets from BSD-3-Clause drivers plus 557 from GPL-2.0+ and 665 from
LGPL-2.1+ driver files, which give listing facts only (set name, title, year, maker, parent,
CRCs), never code; mechanical sets and devices without ROMs are left out.
