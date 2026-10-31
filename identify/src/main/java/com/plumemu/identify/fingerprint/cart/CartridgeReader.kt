package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.TitleKind
import com.plumemu.identify.source.RandomAccessSource
import java.io.IOException

/** How a loose file's bytes must be rearranged before its CRC matches a name database. */
internal enum class ContentLayout {
    /** As stored. */
    PLAIN,

    /** N64 `.v64`: every pair of bytes swapped. */
    N64_BYTE_SWAPPED,

    /** N64 `.n64`: every 4-byte word reversed (little-endian words). */
    N64_WORD_SWAPPED,

    /** Mega Drive `.smd`: 16 KB blocks, odd bytes in the first half, even bytes in the second. */
    SMD_INTERLEAVED,
}

/** What a cartridge header reader found; [titleIsCanonical] as in `Fingerprints`. */
internal data class CartridgeHeader(
    val codeKind: CodeKind? = null,
    val productCode: String? = null,
    val title: String? = null,
    val titleIsCanonical: Boolean = false,
    val headerBytesSkipped: Int = 0,
    val layout: ContentLayout = ContentLayout.PLAIN,
    val notes: List<String> = emptyList(),
    val titleId: String? = null,
    val titleKind: TitleKind? = null,
)

/** Reads one cartridge format's header, touching only the bytes it names in its KDoc. */
internal interface CartridgeReader {
    /** Short name used in notes, e.g. "gba". */
    val name: String

    /** File extensions of this format, lower case; used to try the likeliest reader first. */
    val extensions: Set<String>

    /** The numbering of the game code this format's header gives ([CartridgeHeader.codeKind]); null when it has none. */
    val codeKind: CodeKind?

    /** The header, or null when the bytes are not this format (wrong magic, bad checksum). */
    @Throws(IOException::class)
    fun read(source: RandomAccessSource): CartridgeHeader?

    /**
     * Length of the header a stored copy of [length] bytes probably starts with, judged from the
     * length alone, or 0. Used for zip entries, which are not opened.
     */
    fun likelyHeaderSize(length: Long): Int = 0

    /**
     * Header bytes to leave out of a loose file's CRC when [read] found no header of this
     * format: for formats whose copier header is told by the file length alone (SNES), so the
     * CRC stays right for a file whose internal header is missing or unreadable. 0 otherwise.
     */
    fun skipWithoutHeader(length: Long): Int = 0

    /**
     * True when a stored copy may differ from the content name databases list: a header to leave
     * out or a byte order to undo. A zipped copy is then unpacked at `Depth.FULL` for its content
     * CRC (`ZipEntryContent`); for other formats the zip's directory CRC already is that CRC.
     */
    val storedFormMayDiffer: Boolean get() = false
}
