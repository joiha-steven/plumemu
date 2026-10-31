package com.plumemu.identify.codec.lzma

/**
 * The LZMA state machine, ported from the public-domain LZMA SDK (Igor Pavlov): it follows
 * `Java/SevenZip/Compression/LZMA/Decoder.java` for the structure (separate probability arrays,
 * length decoders, literal coder) and `DOC/LzmaSpec.cpp` / `C/LzmaDec.c` for the checks a
 * hostile stream needs (no match before the first byte, no distance beyond the bytes produced or
 * the dictionary, end marker only on a finished range coder).
 *
 * It writes into [window] and keeps its probabilities between calls, so LZMA2 can run it over
 * several chunks and reset only what each chunk header asks for.
 */
internal class LzmaDecoder(private val window: OutputWindow, properties: LzmaProperties) {

    private var lc = 0
    private var lpMask = 0
    private var pbMask = 0
    private var dictionarySize = 0L
    private var literalProbs = ShortArray(0)

    private val isMatch = RangeDecoder.probs(STATES shl POS_BITS_MAX)
    private val isRep = RangeDecoder.probs(STATES)
    private val isRepG0 = RangeDecoder.probs(STATES)
    private val isRepG1 = RangeDecoder.probs(STATES)
    private val isRepG2 = RangeDecoder.probs(STATES)
    private val isRep0Long = RangeDecoder.probs(STATES shl POS_BITS_MAX)
    private val posSlotProbs = RangeDecoder.probs(LEN_TO_POS_STATES shl POS_SLOT_BITS)
    private val posProbs = RangeDecoder.probs(1 + FULL_DISTANCES - END_POS_MODEL_INDEX)
    private val alignProbs = RangeDecoder.probs(1 shl ALIGN_BITS)
    private val matchLength = LengthDecoder()
    private val repLength = LengthDecoder()

    private var state = 0
    private var rep0 = 0
    private var rep1 = 0
    private var rep2 = 0
    private var rep3 = 0

    /**
     * Bytes of the last match that did not fit before the stop position of [decode]. Zero
     * unless the output stopped inside a match.
     */
    var pendingLength = 0
        private set

    init {
        setProperties(properties)
        resetState()
    }

    /**
     * Takes new lc / lp / pb (an LZMA2 properties reset). The literal table is reallocated only
     * when its size changes; at most `0x300 << 12` entries (6 MB) for lc = 8, lp = 4, which the
     * properties byte allows and LZMA2 does not (lc + lp <= 4: `0x300 << 4` entries, 24 KB).
     */
    fun setProperties(properties: LzmaProperties) {
        lc = properties.lc
        lpMask = (1 shl properties.lp) - 1
        pbMask = (1 shl properties.pb) - 1
        dictionarySize = maxOf(properties.dictionarySize, LzmaProperties.MIN_DICTIONARY)
        val literalSize = LITERAL_CODER_SIZE shl (properties.lc + properties.lp)
        if (literalProbs.size != literalSize) literalProbs = ShortArray(literalSize)
    }

    /** Puts every probability back at one half and forgets the state and the last distances. */
    fun resetState() {
        for (probs in arrayOf(
            literalProbs, isMatch, isRep, isRepG0, isRepG1, isRepG2, isRep0Long,
            posSlotProbs, posProbs, alignProbs,
        )) {
            probs.fill(RangeDecoder.PROB_INIT)
        }
        matchLength.reset()
        repLength.reset()
        state = 0
        rep0 = 0
        rep1 = 0
        rep2 = 0
        rep3 = 0
        pendingLength = 0
    }

    /**
     * Decodes from [rc] until the window holds [stopAt] bytes or the end marker is read.
     * Every pass of the loop produces at least one byte, so it runs at most [stopAt] times.
     *
     * @return true when the end marker was read.
     * @throws LzmaException on an invalid stream or when the input runs out.
     */
    fun decode(rc: RangeDecoder, stopAt: Int): Boolean {
        pendingLength = 0
        while (window.size < stopAt) {
            val posState = window.sinceReset and pbMask
            val stateIndex = (state shl POS_BITS_MAX) + posState
            if (rc.decodeBit(isMatch, stateIndex) == 0) {
                decodeLiteral(rc)
                continue
            }
            val length: Int
            if (rc.decodeBit(isRep, state) == 1) {
                if (window.sinceReset == 0) throw LzmaException("repeated match before any output")
                if (rc.decodeBit(isRepG0, state) == 0) {
                    if (rc.decodeBit(isRep0Long, stateIndex) == 0) {
                        // A "short rep": one byte at the last distance.
                        state = if (state < LITERAL_STATES) 9 else 11
                        copy(rep0, 1, stopAt)
                        continue
                    }
                } else {
                    pickOlderRep(rc)
                }
                length = repLength.decode(rc, posState)
                state = if (state < LITERAL_STATES) 8 else 11
            } else {
                rep3 = rep2
                rep2 = rep1
                rep1 = rep0
                length = matchLength.decode(rc, posState)
                state = if (state < LITERAL_STATES) 7 else 10
                rep0 = decodeDistance(rc, length)
                if (rep0 == END_MARKER) {
                    if (!rc.isFinishedOk) throw LzmaException("end marker on an unfinished range coder")
                    return true
                }
            }
            copy(rep0, length + MATCH_LEN_MIN, stopAt)
        }
        return false
    }

    /** Moves rep1, rep2 or rep3 to the front, as the isRepG1 / isRepG2 bits say. */
    private fun pickOlderRep(rc: RangeDecoder) {
        val distance: Int
        if (rc.decodeBit(isRepG1, state) == 0) {
            distance = rep1
        } else {
            if (rc.decodeBit(isRepG2, state) == 0) {
                distance = rep2
            } else {
                distance = rep3
                rep3 = rep2
            }
            rep2 = rep1
        }
        rep1 = rep0
        rep0 = distance
    }

    private fun copy(distance: Int, length: Int, stopAt: Int) {
        val reach = minOf(dictionarySize, window.sinceReset.toLong())
        if (Integer.toUnsignedLong(distance) >= reach) {
            throw LzmaException(
                "match distance ${Integer.toUnsignedLong(distance) + 1} reaches beyond " +
                    "the $reach bytes available",
            )
        }
        pendingLength = length - window.copyMatch(distance, length, stopAt)
    }

    private fun decodeLiteral(rc: RangeDecoder) {
        val previous = window.byteBack(0)
        val literalState = ((window.sinceReset and lpMask) shl lc) + (previous ushr (8 - lc))
        val base = LITERAL_CODER_SIZE * literalState
        var symbol = 1
        if (state >= LITERAL_STATES) {
            // After a match the byte at rep0 predicts the literal until the first differing bit.
            var matchByte = window.byteBack(rep0)
            while (symbol < BYTE_SYMBOLS) {
                val matchBit = (matchByte ushr 7) and 1
                matchByte = matchByte shl 1
                val bit = rc.decodeBit(literalProbs, base + ((1 + matchBit) shl 8) + symbol)
                symbol = (symbol shl 1) or bit
                if (matchBit != bit) break
            }
        }
        while (symbol < BYTE_SYMBOLS) symbol = (symbol shl 1) or rc.decodeBit(literalProbs, base + symbol)
        window.put(symbol - BYTE_SYMBOLS)
        state = when {
            state < 4 -> 0
            state < 10 -> state - 3
            else -> state - 6
        }
    }

    /** The zero-based distance of a new match; [length] is the length code before adding 2. */
    private fun decodeDistance(rc: RangeDecoder, length: Int): Int {
        val lenState = minOf(length, LEN_TO_POS_STATES - 1)
        val posSlot = rc.decodeTree(posSlotProbs, lenState shl POS_SLOT_BITS, POS_SLOT_BITS)
        if (posSlot < START_POS_MODEL_INDEX) return posSlot
        val directBits = (posSlot ushr 1) - 1
        // For slot 63 this is 3 << 30, past Int.MAX_VALUE: the sum wraps, as the unsigned
        // 32-bit arithmetic of the SDK does, and 0xFFFFFFFF (-1) is the end marker.
        var distance = (2 or (posSlot and 1)) shl directBits
        if (posSlot < END_POS_MODEL_INDEX) {
            distance += rc.decodeReverseTree(posProbs, distance - posSlot, directBits)
        } else {
            distance += rc.decodeDirectBits(directBits - ALIGN_BITS) shl ALIGN_BITS
            distance += rc.decodeReverseTree(alignProbs, 0, ALIGN_BITS)
        }
        return distance
    }

    /** Match lengths 0..271 (2..273 after adding the minimum), per position state. */
    private class LengthDecoder {
        private val choice = RangeDecoder.probs(2)
        private val low = RangeDecoder.probs(POS_STATES_MAX shl LEN_LOW_BITS)
        private val mid = RangeDecoder.probs(POS_STATES_MAX shl LEN_MID_BITS)
        private val high = RangeDecoder.probs(1 shl LEN_HIGH_BITS)

        fun reset() {
            for (probs in arrayOf(choice, low, mid, high)) probs.fill(RangeDecoder.PROB_INIT)
        }

        fun decode(rc: RangeDecoder, posState: Int): Int = when {
            rc.decodeBit(choice, 0) == 0 ->
                rc.decodeTree(low, posState shl LEN_LOW_BITS, LEN_LOW_BITS)
            rc.decodeBit(choice, 1) == 0 ->
                LEN_LOW_SYMBOLS + rc.decodeTree(mid, posState shl LEN_MID_BITS, LEN_MID_BITS)
            else ->
                LEN_LOW_SYMBOLS * 2 + rc.decodeTree(high, 0, LEN_HIGH_BITS)
        }
    }

    private companion object {
        const val STATES = 12
        const val LITERAL_STATES = 7
        const val POS_BITS_MAX = 4
        const val POS_STATES_MAX = 1 shl POS_BITS_MAX
        const val LITERAL_CODER_SIZE = 0x300
        const val BYTE_SYMBOLS = 0x100
        const val MATCH_LEN_MIN = 2
        const val LEN_LOW_BITS = 3
        const val LEN_MID_BITS = 3
        const val LEN_HIGH_BITS = 8
        const val LEN_LOW_SYMBOLS = 1 shl LEN_LOW_BITS
        const val LEN_TO_POS_STATES = 4
        const val POS_SLOT_BITS = 6
        const val START_POS_MODEL_INDEX = 4
        const val END_POS_MODEL_INDEX = 14
        const val FULL_DISTANCES = 1 shl (END_POS_MODEL_INDEX / 2)
        const val ALIGN_BITS = 4
        const val END_MARKER = -1
    }
}
