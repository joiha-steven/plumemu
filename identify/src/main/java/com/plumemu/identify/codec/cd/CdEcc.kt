// CD-ROM sector sync and ECC parity (ECMA-130, clause 15), written from the standard. Not in rcheevos.
package com.plumemu.identify.codec.cd

/**
 * A CHD of a CD cuts the sync pattern and the error-correction bytes out of Mode 1 sectors that carry valid
 * ones, and sets a bit in the hunk; reading the hunk puts them back with [rebuild].
 */
object CdEcc {
    private const val P_AT = 0x81C
    private const val Q_AT = 0x8C8
    private val SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)

    /** Multiplication by 2 in GF(256) with the field polynomial 0x11D, and its inverse mapping. */
    private val forward = IntArray(256) { (it shl 1) xor (if (it and 0x80 != 0) 0x11D else 0) }
    private val backward = IntArray(256).also { table -> for (i in 0 until 256) table[i xor forward[i]] = i }

    /**
     * Puts back what `chdman` cuts out of a sector that passed its check: the sync pattern and the P and Q parity. The EDC
     * (and a Mode 1 sector's 8 zero bytes) stay in the stored data, so they are not touched. The mode byte (offset 15)
     * is still there. Mode 2 is taken as Form 1, whose parity counts the 4 address bytes as zero.
     */
    fun rebuild(buffer: ByteArray, at: Int) {
        SYNC.copyInto(buffer, at)
        when (buffer[at + 15].toInt()) {
            1 -> parities(buffer, at)
            2 -> {
                val address = buffer.copyOfRange(at + 12, at + 16)
                buffer.fill(0, at + 12, at + 16)
                parities(buffer, at)
                address.copyInto(buffer, at + 12)
            }
        }
    }

    private fun parities(buffer: ByteArray, at: Int) {
        parity(buffer, at + 12, 86, 24, 2, 86, at + P_AT)
        parity(buffer, at + 12, 52, 43, 86, 88, at + Q_AT)
    }

    private fun parity(buffer: ByteArray, src: Int, majorCount: Int, minorCount: Int, majorMult: Int, minorInc: Int, dest: Int) {
        val size = majorCount * minorCount
        for (major in 0 until majorCount) {
            var index = (major shr 1) * majorMult + (major and 1)
            var a = 0
            var b = 0
            for (minor in 0 until minorCount) {
                val value = buffer[src + index].toInt() and 0xFF
                index += minorInc
                if (index >= size) index -= size
                a = a xor value
                b = forward[b xor value]
            }
            a = backward[forward[a] xor b]
            buffer[dest + major] = a.toByte()
            buffer[dest + major + majorCount] = (a xor b).toByte()
        }
    }
}
