package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.cart.CartridgeReaders
import java.util.Locale

/** What a file is wrapped in, or for a disc image, how its sectors are stored. */
enum class Container {
    /** A loose file: the ROM itself. */
    NONE,

    /** A zip archive; fingerprints come from its central directory, nothing is unpacked. */
    ZIP,

    /** A 7z archive; fingerprints come from its header (decoded when compressed), no file data is unpacked. */
    SEVEN_ZIP,

    /** A disc image of 2048-byte sectors (ISO 9660, PS2 DVD), or a GameCube / Wii disc image. */
    ISO,

    /** A raw CD image of 2352-byte sectors (`.bin`, `.img`) read without a cue sheet. */
    BIN_RAW,

    /** A cue sheet; the fingerprints come from its first data track, opened as a sibling file. */
    CUE,

    /** A Dreamcast GD-ROM track list; the fingerprints come from track 3 (`IP.BIN`). */
    GDI,

    /** A playlist of discs; the fingerprints come from its first entry. */
    M3U,

    /** MAME Compressed Hunks of Data, version 5. */
    CHD,

    /** Compressed ISO (CISO), deflate blocks; version 1 is read, version 2 is not. */
    CSO,

    /** ZISO: CSO layout with LZ4 blocks; recognised, not read (no LZ4 decoder). */
    ZSO,

    /** PlayStation Portable `EBOOT.PBP`, also used for PlayStation classics. */
    PBP,

    /** Wii Backup File System image. */
    WBFS,

    /** Dolphin RVZ image (GameCube / Wii). */
    RVZ,

    /** Wii ISO Archive (GameCube / Wii). */
    WIA,

    /** Dolphin GCZ compressed image (GameCube / Wii). */
    GCZ,

    /**
     * Switch NSP or NSZ: a PFS0 partition; its file table (names only) is read for the title id
     * of a ticket or a content meta file (`nx/SwitchReader`). Nothing is decrypted.
     */
    PFS0,

    /** Switch game card image (XCI, XCZ): its plain header carries no title id; the file name may. */
    XCI,
}

/** What a [Fingerprints.titleId] is: the game itself, an update to it or extra content for it. */
enum class TitleKind {
    /** The game (Switch: low 13 bits of the id zero; 3DS: application or demo). */
    BASE,

    /** An update (Switch: base + 0x800; 3DS: id high word 0x0004000E). */
    UPDATE,

    /** Downloadable content (Switch: base + 0x1000 + n; 3DS: id high word 0x0004008C). */
    DLC,

    /** Any other id (system or unknown content type). */
    OTHER,
}

/** Where [Fingerprints.crc32] came from. */
enum class CrcSource {
    /** The CRC the zip's central directory lists for the chosen entry: free, but of the entry as stored. */
    ZIP_DIRECTORY,

    /** Computed over the loose file's content (header skipped, byte order normalised). */
    WHOLE_FILE,

    /**
     * The CRC a 7z archive header lists for the chosen file (`kSubStreamsInfo` digests): the
     * header is decoded, the file data is not; like [ZIP_DIRECTORY], of the file as stored.
     */
    SEVEN_ZIP_HEADER,

    /**
     * Computed at `Depth.FULL` over a zip entry unpacked because its format may carry a header
     * or a byte order (iNES, SNES copier, SMD, N64 v64 / n64, ...): header left out, byte order
     * normalised, as for [WHOLE_FILE]. [Fingerprints.archiveCrc32] keeps the directory's CRC.
     */
    ZIP_ENTRY_CONTENT,
}

/** Which numbering a [Fingerprints.productCode] belongs to: a cartridge game code or a disc serial. */
enum class CodeKind(
    /** The code kind byte in a pack's `CODE` section (PACKS.md). Fixed, append-only: never renumber. */
    val packByte: Int,
    /**
     * The Plumemu system (a `platforms.json` id) whose format this code is read from, whatever folder
     * the file sits in: a `.gbc` in `gba/` or an `.sfc` in `snesmsu1/` still carries a Game Boy or SNES
     * code. The scraper accepts a serial lookup's answer on this system too (`RomHints.serialSystemId`).
     */
    val nativeSystemId: String,
) {
    /** Game Boy Advance game code, 4 characters, e.g. "ZZZE". */
    GBA(1, "gba"),

    /** Nintendo DS (and DSi) game code, 4 characters. */
    NDS(2, "nds"),

    /** Game Boy Color manufacturer code, 4 characters; only some late cartridges have one. */
    GB(3, "gb"),

    /** Nintendo 64 cartridge id: media type, 2-character id and region, e.g. "NZZE". */
    N64(4, "n64"),

    /**
     * Mega Drive, Genesis and 32X product code: the middle of the serial at 0x180
     * ("GM T-99999 -00" gives "T-99999", "GM 00001009-00" gives "00001009"), as MAME's software
     * lists write it.
     */
    MEGADRIVE(5, "megadrive"),

    /** Super Nintendo game code from the extended header, 2 or 4 characters. */
    SNES(6, "snes"),

    /** PlayStation serial from `SYSTEM.CNF` (`BOOT`) or a classic's `PARAM.SFO`, e.g. "SLUS-99999". */
    PS1(7, "psx"),

    /** PlayStation 2 serial from `SYSTEM.CNF` (`BOOT2`), e.g. "SLUS-99999". */
    PS2(8, "ps2"),

    /** PlayStation Portable serial from `UMD_DATA.BIN` or `PARAM.SFO`, e.g. "ULUS-99999". */
    PSP(9, "psp"),

    /** GameCube game id from the disc header, 6 characters, e.g. "GZZE01". */
    GAMECUBE(10, "gc"),

    /** Wii game id from the disc header, 6 characters, e.g. "RZZE01". */
    WII(11, "wii"),

    /** Saturn product number from the system area, e.g. "T-99999". */
    SATURN(12, "saturn"),

    /** Sega CD / Mega-CD product code from the serial in the system area, cut as for [MEGADRIVE]. */
    SEGACD(13, "megacd"),

    /** Dreamcast product number from `IP.BIN`, e.g. "T-99999N". */
    DREAMCAST(14, "dreamcast"),

    /**
     * Switch base application id, 16 upper-case hex digits ("0100ABCD00010000"): the id of the
     * file's content with its low 13 bits cleared, so an update or DLC gives its game's id.
     */
    SWITCH(15, "switch"),

    /** Nintendo 3DS game code: the last 4 characters of the NCCH product code ("CTR-P-ZZZE" gives "ZZZE"). */
    N3DS(16, "n3ds"),
    ;

    companion object {
        /**
         * The code numbering a cartridge file of [systemId] (a folder's system) with [extension] is
         * read with (`CartridgeReaders`), null when its reader reads none: a `.gbc` in `gba/` gives [GB].
         */
        fun ofCartridge(systemId: String, extension: String): CodeKind? =
            CartridgeReaders.codeKindFor(systemId, extension)

        /**
         * [ofCartridge] only when a reader of [systemId] claims [extension] itself: null for an
         * archive or an extension the system takes without a reader of its own (its format unknown).
         */
        fun ofLooseCartridge(systemId: String, extension: String): CodeKind? =
            CartridgeReaders.codeKindClaiming(systemId, extension)

        /** The kind named [name] (a `code:<kind>:` key's middle part), any case; null when unknown. */
        fun named(name: String): CodeKind? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * The cheap fingerprints read from one file by [FingerprintReader]. Every field may be missing;
 * [notes] says why something was not read.
 *
 * @property size length of the file as found (the archive, for a zip).
 * @property container what the file is wrapped in.
 * @property innerName the archive entry the fingerprints describe (the ROM chosen inside a zip).
 * @property entryCount files in the archive, directories not counted; many entries means an
 *   arcade set rather than one console ROM.
 * @property contentSize length of the bytes [crc32] covers: the archive entry's uncompressed size
 *   or the loose file, minus [headerBytesSkipped].
 * @property crc32 CRC-32 of the ROM content as name databases list it, as an unsigned value: header
 *   left out, byte order normalised. For a zip or 7z entry at `Depth.QUICK` (and for a 7z at
 *   any depth: its file data is never unpacked) it is the archive's own CRC of the entry as
 *   stored, the same as [archiveCrc32], so for an entry that carries a header it includes the
 *   header. At `Depth.FULL` a zip entry whose format may carry a header is unpacked and this is
 *   the header-less CRC ([CrcSource.ZIP_ENTRY_CONTENT]).
 * @property crcSource where [crc32] came from.
 * @property headerBytesSkipped copier or emulator header left out of [crc32] (iNES 16, SNES
 *   copier 512, SMD 512, Atari 7800 128, Lynx 64). 0 for an archive entry that was not unpacked.
 * @property productCode cartridge game code or disc serial, upper case, in the form of its
 *   [codeKind] that `identify/PACKS.md` ("Code kinds") lists: for Mega Drive and Sega CD the
 *   product-code part of the serial with no spaces ("T-99999"), for PlayStation discs
 *   "PREFIX-NNNNN".
 * @property codeKind the numbering [productCode] belongs to.
 * @property internalTitle the title the file carries itself, trimmed: a header title (printable
 *   ASCII only) or a full title from a metadata block (any letters; see [titleIsCanonical]).
 * @property notes plain-English reasons for anything not read, for the log and for people debugging.
 * @property titleIsCanonical true when [internalTitle] is the full official title in its own
 *   case: PSP and PlayStation classic `PARAM.SFO` `TITLE`, GameCube / Wii disc header, Nintendo
 *   DS banner (English line). False for short or upper-case header titles (GBA, Saturn, ...),
 *   which only help a fuzzy match.
 * @property chdSha1 the SHA-1 of a CHD's raw data as its header lists it, 40 lower-case hex
 *   digits; a stable key for a CHD whose serial could not be read.
 * @property archiveCrc32 for a zip or 7z entry, the CRC the archive's directory or header lists,
 *   of the entry as stored (header included), covering `contentSize + headerBytesSkipped` bytes;
 *   null for anything else.
 * @property titleId the content's own title id, 16 upper-case hex digits: for Switch the id of
 *   the game, update or DLC itself (read from a ticket or content meta name inside an NSP, else
 *   from the file name), for 3DS the program id of the NCCH header or the CIA's TMD. Unlike
 *   [productCode] (Switch: the base id), an update keeps its own id here, so files can be
 *   grouped by [productCode] and the base file told from its update and DLC by [titleKind].
 * @property titleKind what [titleId] is.
 */
data class Fingerprints(
    val size: Long,
    val container: Container,
    val innerName: String? = null,
    val entryCount: Int? = null,
    val contentSize: Long? = null,
    val crc32: Long? = null,
    val crcSource: CrcSource? = null,
    val headerBytesSkipped: Int = 0,
    val productCode: String? = null,
    val codeKind: CodeKind? = null,
    val internalTitle: String? = null,
    val notes: List<String> = emptyList(),
    val titleIsCanonical: Boolean = false,
    val chdSha1: String? = null,
    val archiveCrc32: Long? = null,
    val titleId: String? = null,
    val titleKind: TitleKind? = null,
) {
    /**
     * Every key a person's name fix can be stored under, strongest first, so the fix survives
     * renames, moves and rescans:
     * 1. `crc:<8 lower-case hex digits>:<contentSize>` when [crc32] is known and the content is
     *    not empty (every empty file would share one key).
     * 2. `crc:<archive CRC>:<entry size>` when [archiveCrc32] differs from [crc32] (a zipped ROM
     *    with a header, read at `Depth.FULL`): the key a QUICK read of the same zip gives, so a
     *    fix stored after a QUICK read is still found after a FULL one, and the other way round.
     * 3. `code:<codeKind>:<productCode>` when a header code was read and is not a dummy.
     *    Revisions of one game share a code, so they share a fix. A disc gives its serial here,
     *    e.g. `code:PS1:SLUS-99999`; a Switch file its base id, `code:SWITCH:0100ABCD00010000`,
     *    the same for the game, its updates and its DLC (library stores no fix under `code:`).
     * 4. `chd:<40 hex digits>` when [chdSha1] is known and not all zeros: the CHD's own hash of
     *    its raw data, for a CHD whose serial is in a hunk this module cannot decompress.
     *
     * A code is a dummy, and gives no key, when it has no letter or digit (blank, "####"), when
     * all its letters and digits are the same character ("0000", "XXXX", "00000000"), or when it
     * has a run of three or more digits and every digit in it is 0 ("T-00000"). Unfinished and
     * homebrew headers carry such codes, and one key would join unrelated games.
     *
     * The keys depend on how deep the file was read: a QUICK read of a loose file has no CRC, a
     * FULL read has one. So a fix is stored under all keys known when it is made, and a game is
     * looked up by any of its keys; a later FULL read still finds a fix made after a QUICK one
     *. The keys also depend on how the file is stored: zipping or unzipping a ROM that
     * carries a header (an iNES file, a SNES file with a copier header) changes the QUICK key;
     * at FULL the content key is the same zipped or loose. Arcade
     * keys (`arcade:<shortname>`) are added with the arcade lookup.
     */
    fun fixKeys(): List<String> {
        val keys = ArrayList<String>(4)
        val crc = crc32
        val length = contentSize
        if (crc != null && length != null && length > 0) keys += crcKey(crc, length)
        val archiveCrc = archiveCrc32
        val archiveLength = length?.plus(headerBytesSkipped)
        if (archiveCrc != null && archiveCrc != crc && archiveLength != null && archiveLength > 0) {
            keys += crcKey(archiveCrc, archiveLength)
        }
        val code = productCode
        val kind = codeKind
        if (code != null && kind != null && !isDummyCode(code)) keys += "code:" + kind.name + ":" + code
        val sha1 = chdSha1
        if (sha1 != null && sha1.any { it != '0' }) keys += "chd:$sha1"
        return keys
    }

    /** The strongest of [fixKeys], or null when there is none. */
    fun fixKey(): String? = fixKeys().firstOrNull()

    private companion object {
        val ZERO_DIGIT_RUN = Regex("[0-9]{3,}")

        fun crcKey(crc: Long, length: Long): String = "crc:" + String.format(Locale.ROOT, "%08x", crc) + ":" + length

        fun isDummyCode(code: String): Boolean {
            val significant = code.filter { it.isLetterOrDigit() }
            if (significant.isEmpty()) return true
            if (significant.all { it == significant[0] }) return true
            return ZERO_DIGIT_RUN.containsMatchIn(code) && code.filter { it.isDigit() }.all { it == '0' }
        }
    }
}
