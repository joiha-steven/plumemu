# Name packs, format v1

A name pack holds the name data of one system (names are data, never code): titles, releases, CRCs and product
codes for a console, or short names for arcade sets. Packs ship LZMA-compressed (`<id>.pack.lzma`) in the APK, are
unpacked once to app storage and then read memory-mapped (`pack/NamePack.kt`). The Kotlin writer
is `pack/PackWriter.kt`; the Python pack tool (step I2) must write **the same bytes for the same
input**. This page is the contract between the two; "Canonical order" below fixes every choice
the format leaves open, and `src/test/resources/packs/` holds golden packs to compare against.

## General rules

- All integers are **little-endian**. `u8`, `u16`, `u32` are unsigned; `i32` is signed.
- **NONE** is `u32` all bits set (`FF FF FF FF`), used for a missing string reference.
- A **string reference** (`ref`) is a byte offset into the `STRS` section. At that offset: `u16`
  byte length, then that many bytes of UTF-8. No terminator. The writer stores each distinct
  string once. A reader accepts any offset that points at a length; a writer appends strings in
the canonical order below.
- Every section starts at a multiple of 4 from the start of the file; the gap before it is
  zero bytes. The end of the last section is padded with zeros to a multiple of 4.
- The **trailer** is the SHA-256 (32 bytes) of every byte before it.
- Records inside a section are packed back to back with no gaps; a record section's length is
  a whole number of records.

## Header (36 bytes)

| Offset | Size | Field | Value |
|---|---|---|---|
| 0 | 8 | magic | ASCII `PLMPACK` followed by one zero byte |
| 8 | u16 | formatVersion | 1. A reader refuses any other value |
| 10 | u8 | kind | 1 = console names, 2 = arcade names |
| 11 | u8 | reserved | 0 |
| 12 | u32 | dataVersion | date of the data as a number, e.g. 20261021; higher replaces lower |
| 16 | 16 | systemId | Plumemu system id (`platforms.json`), printable ASCII, zero-padded; the arcade pack is `arcade` |
| 32 | u16 | sectionCount | number of entries in the section table |
| 34 | u16 | reserved | 0 |

Then the **section table** at offset 36: `sectionCount` entries of 12 bytes each.

| Offset in entry | Size | Field |
|---|---|---|
| 0 | 4 | tag, 4 ASCII characters (`STRS`, `SRCS`, `TITL`, `RELS`, `CRCS`, `CODE`, `ARCD`) |
| 4 | u32 | offset of the section from the start of the file |
| 8 | u32 | length of the section in bytes |

A reader checks on open: the file is at least 68 bytes; magic, version and kind; the table ends
before the trailer; every section lies after the table and before the trailer; no tag appears
twice; record sections are whole records; the sections the kind needs are present. Unknown tags
are allowed and ignored (room for later sections without a new format version).

Required sections: console `STRS SRCS TITL RELS CRCS CODE`; arcade `STRS SRCS ARCD`. The Kotlin
writer puts them in that order; readers find them by tag, not by position.

## Sections

### `STRS` string pool

Strings as described above. Used for titles, publishers, variants, codes, short names, makers
and source names.

### `SRCS` sources (every pack)

`u32 count`, then `count` records of 8 bytes: `ref name`, `ref version`. Name is the source
("No-Intro", "Redump", "MAME software lists"), version its DAT date or program version. A
release or arcade set refers to a source by its index here (`u8`; `FF` = not recorded), so the
game's Info can say where a name came from. At most 255 sources. The build tool can leave any
source out (a source whose terms do not allow it is not bundled); the pack then simply does not list it.

### `TITL` titles (console, 20 bytes each)

| Offset | Size | Field |
|---|---|---|
| 0 | ref | name, the canonical title without region or revision tags ("Zork Quest II") |
| 4 | ref | publisher, or NONE |
| 8 | u16 | year, 0 when unknown |
| 10 | u16 | reserved, 0 |
| 12 | u32 | index of the title's first release in `RELS` |
| 16 | u32 | number of releases (0 allowed; then the first-release index is 0) |

A title's releases are consecutive in `RELS`, so listing titles with their releases (the
fix-name search) needs no search. Readers accept titles in any order; writers use the canonical
order (alphabetical by lower-cased name).

### `RELS` releases (console, 20 bytes each)

| Offset | Size | Field |
|---|---|---|
| 0 | u32 | title index in `TITL` |
| 4 | u32 | region mask (table below) |
| 8 | u32 | language mask (table below) |
| 12 | ref | variant ("Rev 1", "Beta", "Proto"), or NONE for the plain release |
| 16 | u8 | source index in `SRCS`, `FF` when not recorded |
| 17 | 3 | zero |

Releases are ordered by title index, so each title's releases form one run.

### `CRCS` CRC index (console, 12 bytes each)

| Offset | Size | Field |
|---|---|---|
| 0 | u32 | CRC-32 of the ROM content, as name databases list it (no copier header, N64 big-endian) |
| 4 | u32 | content size in bytes; `FF FF FF FF` when unknown or 4 GiB or more (matches any size) |
| 8 | u32 | release index in `RELS` |

Sorted by CRC, then size, then release index, all as unsigned numbers. One release may have
several entries (several dumps); one CRC may have several entries (the same dump released in
several regions, or two dumps of different sizes). A lookup with a size returns the entries of
that size and of unknown size; without a size, all entries with the CRC.

### `CODE` product codes (console, 12 bytes each)

| Offset | Size | Field |
|---|---|---|
| 0 | ref | the code exactly as `Fingerprints.productCode` gives it: upper case, spaces collapsed |
| 4 | u8 | code kind (table below; 0 when unspecified). Informative: lookups match the code only |
| 5 | 3 | zero |
| 8 | u32 | release index in `RELS` |

Sorted by the code's UTF-8 bytes compared as unsigned bytes (a shorter prefix first), then kind,
then release index. A lookup compares the code string only, never the kind byte.

Code kinds. The numbers are fixed and the table is append-only: a number is never reused or
renumbered, a new kind takes the next free number. `CodeKind.packByte` in the identify module
holds the same table and `PackGoldenTest.codeKindBytesArePinned` pins it. 0 means unspecified.

| Byte | `CodeKind` | Used for | Code exactly as identify produces it |
|---|---|---|---|
| 1 | `GBA` | Game Boy Advance | 4 characters, A-Z and 0-9 only, from the header at 0xAC ("AXVE"-style) |
| 2 | `NDS` | Nintendo DS, DSi | 4 characters, A-Z and 0-9 only, game code at 0x0C |
| 3 | `GB` | Game Boy Color | 4 characters, A-Z and 0-9 only, manufacturer code at 0x13F; only late cartridges have one |
| 4 | `N64` | Nintendo 64 | 4 characters, A-Z and 0-9 only: media type, 2-character id, region ("NZZE"), read after byte order is normalised |
| 5 | `MEGADRIVE` | Mega Drive, Genesis, 32X | the product code in the 14-byte serial at 0x180 ("TT PPPPPPPP-VV"): printable ASCII, upper case; a leading 2-letter type and its space dropped ("GM "), a trailing version dropped ("-00", " -00") when 3 or more letters or digits stay, every space removed: "GM MK-1401 -00" gives "MK-1401", "GM T-50016 -00" gives "T-50016", "GM 00001009-00" gives "00001009" (MAME's software lists write the same part) |
| 6 | `SNES` | Super Nintendo | 2 or 4 characters, A-Z and 0-9 only, from the extended header (only present when the maker byte is 0x33) |
| 7 | `PS1` | PlayStation | `PREFIX-NNNNN`, see below |
| 8 | `PS2` | PlayStation 2 | `PREFIX-NNNNN`, see below |
| 9 | `PSP` | PlayStation Portable | `PREFIX-NNNNN`, see below |
| 10 | `GAMECUBE` | GameCube | 6 characters, A-Z and 0-9 only, disc id at 0 ("GZZE01") |
| 11 | `WII` | Wii | 6 characters, A-Z and 0-9 only, disc id at 0 ("RZZE01") |
| 12 | `SATURN` | Saturn | the product number field at 0x20 of the system area (10 bytes): printable ASCII, upper case, spaces collapsed and trimmed ("T-99999H"); no other change |
| 13 | `SEGACD` | Sega CD, Mega-CD | the serial at 0x180 of the system area, cut to its product code as for `MEGADRIVE` ("GM T-99999 -00" gives "T-99999") |
| 14 | `DREAMCAST` | Dreamcast | the product number field at 0x40 of `IP.BIN` (10 bytes), normalised as for `SATURN` ("T-99999N") |
| 15 | `SWITCH` | Nintendo Switch | the base application id, 16 upper-case hex digits ("0100ABCD00010000"): the title id an NSP's ticket or content meta name gives, else the one in the file name, with its low 13 bits cleared, so an update (+0x800) or DLC (+0x1000 + n) gives its game's id. Pack rows list base ids only (titledb); the pack also holds name-only titles from GameTDB's `switchtdb` (no code row), reached by the name match |
| 16 | `N3DS` | Nintendo 3DS | 4 characters, A-Z and 0-9 only: the last part of the NCCH product code at 0x150 ("CTR-P-ZZZE" gives "ZZZE"), the id GameTDB's 3dstdb uses; makerom's placeholder "CTAP" is not a code |

`PREFIX-NNNNN` (PS1, PS2, PSP): 3 to 5 upper-case letters, a hyphen, then 3 to 7 digits. identify
accepts the boot file name or `DISC_ID` as `LETTERS`, an optional `_`, space or `-`, `DIGITS`, and
an optional `.` and more `DIGITS`; it upper-cases, drops the separator and the dot, and joins:
"slus_999.99", "SLUS99999" and "SLUS-99999" all give "SLUS-99999". A name with fewer than 3 or
more than 7 digits in total is not a serial. A pack writer must store codes in this form.

A code that is blank, has no letter or digit, repeats one character ("0000"), or has a run of
three or more digits that are all 0 ("00000000", "T-00000") is a dummy: identify does not look it up,
and a pack should not list it.

### `ARCD` arcade sets (arcade, 24 bytes each)

| Offset | Size | Field |
|---|---|---|
| 0 | ref | short name, lower case ASCII ("zorkfght") |
| 4 | ref | title ("Zork Fighters (Japan)") |
| 8 | ref | maker, or NONE |
| 12 | u16 | year, 0 when unknown |
| 14 | u16 | flags: bit 0 BIOS, bit 1 device, bit 2 mechanical, bit 3 clone, bit 4 not a game for another reason |
| 16 | i32 | index of the parent set in `ARCD`, -1 for none |
| 20 | u8 | source index in `SRCS`, `FF` when not recorded |
| 21 | 3 | zero |

Sorted by short name, UTF-8 bytes compared as unsigned bytes. A clone has bit 3 set and a parent
index. The identifier hides sets with bit 0, 1 or 4 (`hide = true`); a mechanical set is shown.

The title is the full description; identify shows its short form (`ArcadeTitles.shortTitle`).
The bundled pack also holds a record per older short name (MAME 2003-Plus names) under
its own source in `SRCS`: an ordinary set record, never a short name that is a set of the pack,
so readers need nothing new (the build tool's "Arcade aliases").

## Canonical order (identical bytes)

Readers depend only on the sort orders stated per section. Writers follow this order too, so
the Kotlin writer and the Python tool produce the same bytes for the same input (the same
titles, releases, CRCs, codes and sources, given in the same order):

1. **Titles** are sorted by the UTF-8 bytes of the lower-cased name (Java
   `lowercase(Locale.ROOT)`, Python `str.lower()`; they agree on every letter the No-Intro and
   Redump DATs use), compared as unsigned bytes; equal keys keep input order. A title's index
   is its position after sorting.
2. **Releases** are sorted by their title's new index; releases of one title keep input order.
3. **The string pool** is filled by appending each string the first time it is referenced, in
   this order: for each title in sorted order its name, then its publisher (if any); for each
   release in sorted order its variant (if any); for each `CODE` row in sorted order its code;
   for each source in input order its name, then its version. A string seen before reuses its
   offset. Arcade: for each set in sorted order its short name, title, maker (if any); then the
   sources.
4. **`CRCS`** rows sorted by (CRC, stored size, release index); **`CODE`** rows by (code bytes,
   kind, release index); **`ARCD`** by short-name bytes (short names are lower-cased and unique;
   a parent that is not in the pack is written as -1).
5. **Sections** in the order `STRS SRCS TITL RELS CRCS CODE` (console) or `STRS SRCS ARCD`
   (arcade). Each starts at the next multiple of 4 after the previous one; the table records
   its unpadded length. After the last section, zeros up to a multiple of 4, then the trailer.
6. All reserved and padding bytes are zero. A year of 0 or none is written as 0. A title with
   no releases has first-release index 0.

Golden packs: `src/test/resources/packs/console.pack` and `arcade.pack`, with their input
written out in `PackGoldenTest`. Both were produced by an independent writer made from this page
and are byte for byte what `PackWriter` writes; I2's tests should rebuild them from the same input
and compare.

## Region mask

Bit n set means the release is for region n. Names are spelled as No-Intro and Redump write
them, which is also what `library`'s `Region.parseGroup` reads (bits 0 to 20 are exactly the
regions `library` knows). Append-only: a bit never changes meaning. A region outside this list
is **dropped** when the pack is built (the Kotlin writer adds a warning); 32 bits are all used,
so a new region needs format version 2.

| Bit | Region | Bit | Region | Bit | Region | Bit | Region |
|---|---|---|---|---|---|---|---|
| 0 | USA | 8 | Asia | 16 | Italy | 24 | Greece |
| 1 | Europe | 9 | Brazil | 17 | Netherlands | 25 | India |
| 2 | Japan | 10 | Australia | 18 | Sweden | 26 | Latin America |
| 3 | World | 11 | Canada | 19 | Scandinavia | 27 | Mexico |
| 4 | Korea | 12 | UK | 20 | Russia | 28 | New Zealand |
| 5 | China | 13 | France | 21 | Belgium | 29 | Norway |
| 6 | Taiwan | 14 | Germany | 22 | Denmark | 30 | Poland |
| 7 | Hong Kong | 15 | Spain | 23 | Finland | 31 | Portugal |

## Language mask

No-Intro's two-letter codes. Append-only, unknown languages dropped, as for regions.

| Bit | Code | Bit | Code | Bit | Code | Bit | Code |
|---|---|---|---|---|---|---|---|
| 0 | En | 8 | Sv | 16 | El | 24 | Ro |
| 1 | Ja | 9 | No | 17 | Ca | 25 | Sk |
| 2 | Fr | 10 | Da | 18 | Hu | 26 | Sl |
| 3 | De | 11 | Fi | 19 | Cs | 27 | Uk |
| 4 | Es | 12 | Zh | 20 | Tr | 28 | Bg |
| 5 | It | 13 | Ko | 21 | Ar | 29 | Id |
| 6 | Nl | 14 | Pl | 22 | He | 30 | Th |
| 7 | Pt | 15 | Ru | 23 | Hr | 31 | Vi |

## Bundled index

The bundled index `packs.txt` (in the identify module's assets), plain text, one entry per line; blank lines and
lines starting with `#` are ignored; fields are separated by spaces or tabs.

```
# <systemId> <fileName> <dataVersion> <sha256 of the pack, lower-case hex>
megadrive megadrive.pack.lzma 20261021 5f0c...64 hex digits
arcade arcade.pack.lzma 20261021 9a1b...
alias genesis megadrive
```

- `fileName` is the compressed pack in the same assets folder: `<systemId>.pack.lzma`, an
  LZMA_Alone file (`.lzma`: 5 property bytes, 8 size bytes, here "unknown", then the stream with an
  end marker) that the pack build tool writes with Python's `lzma` at preset 9 extreme.
  `PackStore` picks the decoder by the suffix: `.lzma` goes through identify's own decoder
  (`codec/lzma`), anything else is gzip (the form of a data update, and of older
  indexes). `app/build.gradle.kts` lists `lzma` in `androidResources.noCompress`, so the APK
  stores the file as it is instead of deflating it again. LZMA took the bundled packs from 4.50 MB
  (deflated in the APK) to 3.25 MB. A corrupt or truncated file is logged as `pack <id>: corrupt LZMA
  data: ...` and the pack counts as missing.
- `sha256` is the pack's trailer: the SHA-256 of the unpacked pack minus its last 32 bytes.
  Installing checks it against the index and recomputes it over the content.
- `alias <system> <packSystem>` sends a system to another system's pack. Arcade systems need no
  alias lines: they all use the `arcade` pack (`arcade/ArcadeNames.kt`, `ArcadeSystems`).

## Installed packs

`PackStore` keeps `<directory>/<systemId>.pack` (unpacked). A bundled pack is installed when no
pack is installed or the installed `dataVersion` is lower; a data update (E4) goes through
`PackStore.install` with the same checks and is refused when older than the installed pack.
