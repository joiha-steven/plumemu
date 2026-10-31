package com.plumemu.identify.codec.lzma

/**
 * The parameters of an LZMA stream: [lc] literal context bits (0..8), [lp] literal position
 * bits (0..4), [pb] position bits (0..4) and the encoder's [dictionarySize] in bytes (up to
 * 4 GB, so a Long).
 *
 * The dictionary size is only used to reject matches that reach further back than the encoder
 * could have looked; it is never used to size a buffer (the decoder holds at most the output
 * the caller asked for, see [lzmaDecode]).
 */
internal data class LzmaProperties(
    val lc: Int,
    val lp: Int,
    val pb: Int,
    val dictionarySize: Long,
) {
    init {
        require(lc in 0..MAX_LC && lp in 0..MAX_LP && pb in 0..MAX_PB) { "lc/lp/pb out of range" }
        require(dictionarySize >= 0) { "negative dictionary size" }
    }

    companion object {
        const val MAX_LC = 8
        const val MAX_LP = 4
        const val MAX_PB = 4

        /** Size of the property blob in a `.lzma` header and in 7z coder properties. */
        const val BLOB_SIZE = 5

        /** The smallest dictionary the LZMA SDK decoder uses, whatever the stream says. */
        const val MIN_DICTIONARY = 4096L

        private const val PROPS_BYTE_LIMIT = 9 * 5 * 5

        /**
         * Decodes the properties byte `(pb * 5 + lp) * 9 + lc`.
         *
         * @throws LzmaException when [propsByte] is 225 or more.
         */
        @Throws(LzmaException::class)
        fun fromByte(propsByte: Int, dictionarySize: Long): LzmaProperties {
            if (propsByte !in 0 until PROPS_BYTE_LIMIT) {
                throw LzmaException("LZMA properties byte $propsByte is not below $PROPS_BYTE_LIMIT")
            }
            if (dictionarySize < 0) throw LzmaException("negative LZMA dictionary size")
            val lc = propsByte % 9
            val rest = propsByte / 9
            return LzmaProperties(lc = lc, lp = rest % 5, pb = rest / 5, dictionarySize)
        }

        /**
         * Decodes the 5-byte property blob of a `.lzma` header or a 7z LZMA coder: the
         * properties byte, then the dictionary size as a 32-bit little-endian unsigned number.
         *
         * @throws LzmaException when the blob is shorter than 5 bytes or the byte is invalid.
         */
        @Throws(LzmaException::class)
        fun fromBlob(blob: ByteArray, offset: Int = 0): LzmaProperties {
            if (offset < 0 || blob.size - offset < BLOB_SIZE) {
                throw LzmaException("LZMA property blob shorter than $BLOB_SIZE bytes")
            }
            var dictionary = 0L
            for (i in 4 downTo 1) dictionary = (dictionary shl 8) or (blob[offset + i].toLong() and 0xFF)
            return fromByte(blob[offset].toInt() and 0xFF, dictionary)
        }

        /**
         * The dictionary size an LZMA2 dictionary byte (7z coder properties, xz filter flags)
         * stands for: `(2 | (prop & 1)) << (prop / 2 + 11)`, and 40 for 4 GB - 1.
         *
         * @throws LzmaException when [prop] is above 40.
         */
        @Throws(LzmaException::class)
        fun lzma2DictionarySize(prop: Int): Long {
            if (prop !in 0..LZMA2_DICTIONARY_PROP_MAX) {
                throw LzmaException("LZMA2 dictionary byte $prop is above $LZMA2_DICTIONARY_PROP_MAX")
            }
            if (prop == LZMA2_DICTIONARY_PROP_MAX) return (1L shl 32) - 1
            return (2L or (prop.toLong() and 1)) shl (prop / 2 + 11)
        }

        private const val LZMA2_DICTIONARY_PROP_MAX = 40
    }
}
