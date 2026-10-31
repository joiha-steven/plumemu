package com.plumemu.identify.codec.lzma

/**
 * The LZMA range decoder and its adaptive bit models, ported from the public-domain LZMA SDK
 * (Igor Pavlov): `Java/SevenZip/Compression/RangeCoder/Decoder.java` and `BitTreeDecoder.java`,
 * with the same arithmetic as `C/LzmaDec.c` and the `DOC/LzmaSpec.cpp` reference.
 *
 * [range] and [code] are unsigned 32-bit values held in Ints; comparisons go through
 * [Integer.compareUnsigned]. Normalisation follows each bit, as in the Java SDK, so after the
 * last bit the decoder has read exactly the bytes the encoder wrote.
 */
internal class RangeDecoder(private val input: CompressedInput) {

    private var range = -1
    private var code = 0

    init {
        if (input.readByte() != 0) throw LzmaException("first range-coder byte is not 0")
        repeat(4) { code = (code shl 8) or input.readByte() }
        // code must stay below range; with range = 2^32 - 1 only one value breaks that.
        if (code == -1) throw LzmaException("range-coder start value out of range")
    }

    /** True when the stream ended cleanly: a flushed encoder leaves `code` at zero. */
    val isFinishedOk: Boolean get() = code == 0

    /** Decodes one bit with the probability at [probs][[index]] and updates it. */
    fun decodeBit(probs: ShortArray, index: Int): Int {
        val prob = probs[index].toInt()
        val bound = (range ushr NUM_BIT_MODEL_BITS) * prob
        val bit: Int
        if (Integer.compareUnsigned(code, bound) < 0) {
            range = bound
            probs[index] = (prob + ((BIT_MODEL_TOTAL - prob) ushr MOVE_BITS)).toShort()
            bit = 0
        } else {
            range -= bound
            code -= bound
            probs[index] = (prob - (prob ushr MOVE_BITS)).toShort()
            bit = 1
        }
        normalize()
        return bit
    }

    /** Decodes [count] bits of equal probability (the middle bits of long distances). */
    fun decodeDirectBits(count: Int): Int {
        var result = 0
        repeat(count) {
            range = range ushr 1
            val t = (code - range) ushr 31
            code -= range and (t - 1)
            result = (result shl 1) or (1 - t)
            normalize()
        }
        return result
    }

    /** A [numBits]-bit symbol from the bit tree at [offset] (index `offset + 1` is the root). */
    fun decodeTree(probs: ShortArray, offset: Int, numBits: Int): Int {
        var m = 1
        repeat(numBits) { m = (m shl 1) + decodeBit(probs, offset + m) }
        return m - (1 shl numBits)
    }

    /** Like [decodeTree] but the bits come lowest first. */
    fun decodeReverseTree(probs: ShortArray, offset: Int, numBits: Int): Int {
        var m = 1
        var symbol = 0
        for (i in 0 until numBits) {
            val bit = decodeBit(probs, offset + m)
            m = (m shl 1) + bit
            symbol = symbol or (bit shl i)
        }
        return symbol
    }

    private fun normalize() {
        if (range ushr TOP_BITS == 0) {
            code = (code shl 8) or input.readByte()
            range = range shl 8
        }
    }

    companion object {
        const val NUM_BIT_MODEL_BITS = 11
        const val BIT_MODEL_TOTAL = 1 shl NUM_BIT_MODEL_BITS
        const val PROB_INIT = (BIT_MODEL_TOTAL / 2).toShort()
        private const val MOVE_BITS = 5
        private const val TOP_BITS = 24

        /** A probability array of [size] entries, all at one half. */
        fun probs(size: Int): ShortArray = ShortArray(size).also { it.fill(PROB_INIT) }
    }
}
