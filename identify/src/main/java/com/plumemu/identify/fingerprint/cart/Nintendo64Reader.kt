package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.isGameCode
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * Nintendo 64 ROM header (n64brew, "ROM Header"), in any of the three byte orders dumps come in,
 * told apart by the first 4 bytes:
 * - `.z64` big-endian, the cartridge's own order: 80 37 12 40;
 * - `.v64` byte-swapped: 37 80 40 12;
 * - `.n64` little-endian words: 40 12 37 80.
 *
 * After putting the first 0x40 bytes in z64 order: title at 0x20 (20 bytes), game id at 0x3B
 * (4 bytes: media type, 2-character id, region), e.g. "NZZE". Reads 0x40 bytes.
 *
 * Name databases list the CRC of the z64 order, so the whole-file CRC of a v64 or n64 file is
 * computed on bytes put back in that order ([ContentLayout]). A zipped v64 or n64 file cannot be
 * normalised without unpacking, so its directory CRC will not match.
 */
internal object Nintendo64Reader : CartridgeReader {
    override val name = "n64"
    override val codeKind: CodeKind? = CodeKind.N64
    override val extensions = setOf("z64", "v64", "n64")
    override val storedFormMayDiffer = true

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val raw = source.readFully(0, 0x40)
        if (raw.size < 0x40) return null
        val layout = layoutOf(raw) ?: return null
        val header = raw.copyOf()
        normalise(header, header.size, layout)
        val code = header.asciiText(0x3B, 4)
        val notes = if (isGameCode(code, 4)) emptyList() else listOf("n64: game id is not 4 letters or digits")
        return CartridgeHeader(
            codeKind = CodeKind.N64.takeIf { isGameCode(code, 4) },
            productCode = code.takeIf { isGameCode(it, 4) },
            title = header.asciiText(0x20, 20),
            layout = layout,
            notes = notes,
        )
    }

    /** Hints for a zip entry: its byte order is unknown without unpacking. */
    fun zipNote(entryName: String): String? = when (entryName.substringAfterLast('.').lowercase()) {
        "v64", "n64" -> "n64: zipped .v64/.n64 file may be byte-swapped; its directory CRC may not match a z64 database"
        else -> null
    }

    private fun layoutOf(first: ByteArray): ContentLayout? = when {
        first.startsWith(Z64_MAGIC) -> ContentLayout.PLAIN
        first.startsWith(V64_MAGIC) -> ContentLayout.N64_BYTE_SWAPPED
        first.startsWith(N64_MAGIC) -> ContentLayout.N64_WORD_SWAPPED
        else -> null
    }

    private val Z64_MAGIC = byteArrayOf(0x80.toByte(), 0x37, 0x12, 0x40)
    private val V64_MAGIC = byteArrayOf(0x37, 0x80.toByte(), 0x40, 0x12)
    private val N64_MAGIC = byteArrayOf(0x40, 0x12, 0x37, 0x80.toByte())

    /**
     * Puts the first [length] bytes of [bytes] in z64 order in place. Only whole units (2 or
     * 4 bytes) are moved; a ragged end is left as it is.
     */
    fun normalise(bytes: ByteArray, length: Int, layout: ContentLayout) {
        when (layout) {
            ContentLayout.N64_BYTE_SWAPPED -> {
                var i = 0
                while (i + 1 < length) {
                    val a = bytes[i]
                    bytes[i] = bytes[i + 1]
                    bytes[i + 1] = a
                    i += 2
                }
            }
            ContentLayout.N64_WORD_SWAPPED -> {
                var i = 0
                while (i + 3 < length) {
                    val a = bytes[i]
                    val b = bytes[i + 1]
                    bytes[i] = bytes[i + 3]
                    bytes[i + 1] = bytes[i + 2]
                    bytes[i + 2] = b
                    bytes[i + 3] = a
                    i += 4
                }
            }
            else -> Unit
        }
    }
}
