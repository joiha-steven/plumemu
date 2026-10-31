package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.TitleKind
import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u16be
import com.plumemu.identify.fingerprint.u32be
import com.plumemu.identify.fingerprint.u32le
import com.plumemu.identify.fingerprint.u64be
import com.plumemu.identify.fingerprint.u64le
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.Locale

/**
 * Nintendo 3DS headers (3dbrew "NCSD", "NCCH", "CIA", "Title metadata"). Only plain header
 * fields are read; nothing is decrypted and no key is used.
 *
 * - **NCCH** (`.cxi`, and every partition of the formats below): `"NCCH"` at 0x100, program id
 *   (u64 little-endian) at 0x118, product code (16 bytes ASCII, "CTR-P-ZZZE") at 0x150. The
 *   NCCH header is never encrypted, even when the ExeFS and RomFS behind it are. The game code is
 *   the product code's last 4 characters ("ZZZE", the id GameTDB's 3dstdb uses); makerom's
 *   default "CTAP" is a homebrew placeholder, not a code. Reads 0x160 bytes.
 * - **NCSD** (`.3ds`, `.cci`, a card image): `"NCSD"` at 0x100, partition table at 0x120 (eight
 *   u32 little-endian offset and length pairs in media units), media unit 0x200 shifted by the
 *   flag byte at 0x18E; partition 0 is the game's NCCH. Reads 0x190 bytes, then its NCCH header.
 * - **CIA** (installable archive): u32 little-endian header size (0x2020) at 0, type and version
 *   0, then the sizes of the certificate chain (0x08), ticket (0x0C), TMD (0x10), each section
 *   starting at the next multiple of 64 bytes. The TMD (big-endian) starts with its u32
 *   signature type, then the signature and its padding (0x140 bytes in all for RSA-2048), then
 *   the header; the header holds the title id at
 *   0x4C, the content count at 0x9E, and after 0xC4 + 64 * 0x24 bytes of info records the first
 *   content chunk (type u16 at 6, bit 0 set when the content is encrypted with the title key).
 *   The title id is always read. The game code only when content 0 is not encrypted and its
 *   NCCH header is there; an encrypted one is left alone with a note. Reads about 3 KB.
 *
 * The title id's high word gives its kind: 0x00040000 a game, 0x00040002 a demo, 0x0004000E an
 * update, 0x0004008C DLC.
 */
internal object N3dsReader : CartridgeReader {
    override val name = "n3ds"
    override val codeKind: CodeKind? = CodeKind.N3DS
    override val extensions = setOf("3ds", "cci", "cxi", "cia")

    private val NCCH = ascii("NCCH")
    private val NCSD = ascii("NCSD")
    private const val MAGIC_AT = 0x100
    private const val NCCH_HEADER = 0x160
    private const val NCSD_HEADER = 0x190
    private const val MEDIA_UNIT = 0x200L
    private const val CIA_HEADER_SIZE = 0x2020L
    private const val CIA_ALIGN = 64L
    private const val MAX_CIA_SECTION = 1L shl 20
    private const val TMD_CHUNKS_AT = 0xC4 + 64 * 0x24
    private const val HOMEBREW_CODE = "CTAP"
    private val PRODUCT_CODE = Regex("[A-Z]{3}-[A-Z]-([A-Z0-9]{4})")

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val head = source.readFully(0, NCSD_HEADER)
        return when {
            head.startsWith(NCCH, MAGIC_AT) -> ncch(source, 0L, "ncch")
            head.startsWith(NCSD, MAGIC_AT) -> ncsd(source, head)
            else -> cia(source, head)
        }
    }

    @Throws(IOException::class)
    private fun ncsd(source: RandomAccessSource, head: ByteArray): CartridgeHeader? {
        if (head.size < NCSD_HEADER) return null
        val unitShift = head[0x18E].toInt() and 0xFF
        if (unitShift > 16) return null
        val offset = head.u32le(0x120) * (MEDIA_UNIT shl unitShift)
        return ncch(source, offset, "ncsd") ?: CartridgeHeader(notes = listOf("ncsd: no NCCH header in partition 0"))
    }

    /** The NCCH header at [offset], or null when it is not there. */
    @Throws(IOException::class)
    private fun ncch(source: RandomAccessSource, offset: Long, label: String): CartridgeHeader? {
        if (offset < 0 || offset + NCCH_HEADER > source.size) return null
        val header = source.readFully(offset, NCCH_HEADER)
        if (header.size < NCCH_HEADER || !header.startsWith(NCCH, MAGIC_AT)) return null
        val titleId = titleIdText(header.u64le(0x118))
        val code = gameCode(header.asciiText(0x150, 16))
        return CartridgeHeader(
            codeKind = if (code != null) CodeKind.N3DS else null,
            productCode = code,
            titleId = titleId,
            titleKind = kindOf(titleId),
            notes = if (code == null) listOf("$label: no game code in the product code field") else emptyList(),
        )
    }

    @Throws(IOException::class)
    private fun cia(source: RandomAccessSource, head: ByteArray): CartridgeHeader? {
        if (head.size < 0x20 || head.u32le(0) != CIA_HEADER_SIZE || head.u32le(4) != 0L) return null
        val certSize = head.u32le(0x08)
        val ticketSize = head.u32le(0x0C)
        val tmdSize = head.u32le(0x10)
        if (certSize > MAX_CIA_SECTION || ticketSize > MAX_CIA_SECTION || tmdSize > MAX_CIA_SECTION || tmdSize < 0x200) return null
        val tmdAt = align(CIA_HEADER_SIZE) + align(certSize) + align(ticketSize)
        val contentAt = tmdAt + align(tmdSize)
        val signature = signatureLength(source.readFully(tmdAt, 4).takeIf { it.size == 4 }?.u32be(0)) ?: return null
        val tmdLength = minOf(tmdSize, signature + TMD_CHUNKS_AT + 0x30L).toInt()
        val tmd = source.readFully(tmdAt, tmdLength)
        if (tmd.size < signature + 0xC4) return null
        val titleId = titleIdText(tmd.u64be(signature + 0x4C))
        val notes = ArrayList<String>(1)
        var code: String? = null
        val chunkAt = signature + TMD_CHUNKS_AT
        val contents = tmd.u16be(signature + 0x9E)
        if (contents == 0 || tmd.size < chunkAt + 0x30) {
            notes += "cia: the TMD lists no content"
        } else if (tmd.u16be(chunkAt + 6) and 1 != 0) {
            notes += "cia: content 0 is encrypted with its title key; game code not read (no keys)"
        } else {
            val inner = ncch(source, contentAt, "cia")
            if (inner == null) notes += "cia: no NCCH header in content 0" else code = inner.productCode
            if (inner != null) notes += inner.notes
        }
        return CartridgeHeader(
            codeKind = if (code != null) CodeKind.N3DS else null,
            productCode = code,
            titleId = titleId,
            titleKind = kindOf(titleId),
            notes = notes,
        )
    }

    private fun align(value: Long): Long = (value + CIA_ALIGN - 1) / CIA_ALIGN * CIA_ALIGN

    /** Bytes in front of a TMD header: the u32 signature type, the signature and its padding; null when unknown. */
    private fun signatureLength(type: Long?): Int? = when (type) {
        0x10000L, 0x10003L -> 4 + 0x200 + 0x3C
        0x10001L, 0x10004L -> 4 + 0x100 + 0x3C
        0x10002L, 0x10005L -> 4 + 0x3C + 0x40
        else -> null
    }

    /** "ZZZE" from "CTR-P-ZZZE"; null for no code or makerom's placeholder. */
    private fun gameCode(productCode: String?): String? {
        val code = productCode?.let { PRODUCT_CODE.matchEntire(it.uppercase(Locale.ROOT)) }?.groupValues?.get(1)
        return code?.takeIf { it != HOMEBREW_CODE }
    }

    private fun titleIdText(id: Long): String? = if (id == 0L) null else String.format(Locale.ROOT, "%016X", id)

    private fun kindOf(titleId: String?): TitleKind? = when (titleId?.substring(0, 8)) {
        null -> null
        "00040000", "00040002" -> TitleKind.BASE
        "0004000E" -> TitleKind.UPDATE
        "0004008C" -> TitleKind.DLC
        else -> TitleKind.OTHER
    }
}
