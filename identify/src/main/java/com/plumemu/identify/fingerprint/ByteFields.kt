package com.plumemu.identify.fingerprint

// Small readers for header fields. Callers check bounds first; these throw
// IndexOutOfBoundsException on a short array, which FingerprintReader turns into a note.

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

internal fun ByteArray.u16le(at: Int): Int = u8(at) or (u8(at + 1) shl 8)

internal fun ByteArray.u32le(at: Int): Long = (u16le(at).toLong()) or (u16le(at + 2).toLong() shl 16)

internal fun ByteArray.u64le(at: Int): Long = u32le(at) or (u32le(at + 4) shl 32)

internal fun ByteArray.u16be(at: Int): Int = (u8(at) shl 8) or u8(at + 1)

internal fun ByteArray.u32be(at: Int): Long = (u16be(at).toLong() shl 16) or u16be(at + 2).toLong()

internal fun ByteArray.u64be(at: Int): Long = (u32be(at) shl 32) or u32be(at + 4)

internal fun ByteArray.startsWith(prefix: ByteArray, at: Int = 0): Boolean {
    if (at < 0 || at + prefix.size > size) return false
    for (i in prefix.indices) if (this[at + i] != prefix[i]) return false
    return true
}

internal fun ascii(text: String): ByteArray = text.toByteArray(Charsets.US_ASCII)

/**
 * Text printed in a header: read up to the first NUL, printable ASCII only (anything else, such
 * as Shift-JIS, gives null rather than garbage), runs of spaces collapsed, trimmed; empty is null.
 */
internal fun ByteArray.asciiText(at: Int, length: Int): String? {
    if (at < 0 || at + length > size) return null
    val out = StringBuilder(length)
    for (i in at until at + length) {
        val b = u8(i)
        if (b == 0) break
        if (b < 0x20 || b > 0x7E) return null
        out.append(b.toChar())
    }
    val text = out.toString().trim().replace(MULTIPLE_SPACES, " ")
    return text.ifEmpty { null }
}

/**
 * Text from a metadata block that carries a full title (PARAM.SFO, NDS banner): any letters
 * kept, control characters dropped, spaces collapsed, trimmed; empty is null.
 */
internal fun cleanTitle(text: String): String? {
    val kept = text.substringBefore('\u0000').filter { !it.isISOControl() }
    return kept.trim().replace(SPACE_RUNS, " ").ifEmpty { null }
}

/** A game code: upper-case letters and digits only, of one of the [lengths]. */
internal fun isGameCode(text: String?, vararg lengths: Int): Boolean =
    text != null && text.length in lengths && text.all { it in 'A'..'Z' || it in '0'..'9' }

private val MULTIPLE_SPACES = Regex(" {2,}")
private val SPACE_RUNS = Regex("\\s{2,}")
