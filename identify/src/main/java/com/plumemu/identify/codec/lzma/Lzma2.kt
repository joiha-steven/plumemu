package com.plumemu.identify.codec.lzma

import java.io.IOException

/**
 * Decodes a raw LZMA2 stream: a 7z LZMA2 packed stream (7-Zip's default for headers and data)
 * or the data of an xz block. A Kotlin port of `C/Lzma2Dec.c` from Igor Pavlov's public-domain
 * LZMA SDK, running the same [LzmaDecoder] as [lzmaDecode] over the chunks.
 *
 * Chunks start with a control byte:
 * - `0x00` ends the stream;
 * - `0x01` / `0x02`: an uncompressed chunk with / without a dictionary reset, then its size - 1
 *   as 2 big-endian bytes, then the bytes;
 * - `0x80..0xFF`: an LZMA chunk. Bits 0..4 are bits 16..20 of the uncompressed size - 1, then
 *   come its low 16 bits and the compressed size - 1 (2 big-endian bytes each). Bits 5..6 say
 *   what is reset: 0 nothing, 1 the state, 2 the state and the properties (a properties byte
 *   follows, lc + lp <= 4), 3 all that and the dictionary. Each LZMA chunk restarts the range
 *   coder and must use exactly its compressed size.
 * - `0x03..0x7F` are invalid. The first chunk must reset the dictionary, and after any
 *   dictionary reset in an uncompressed chunk the next LZMA chunk must bring new properties
 *   (the `needInitLevel` rule of `Lzma2Dec.c`).
 *
 * Bounded like [lzmaDecode]: at most [outputSize] bytes are produced and allocated; decoding
 * stops there, mid-chunk if need be. Every chunk produces at least one byte, so the chunk loop
 * runs at most [outputSize] times.
 *
 * @param dictionaryProp the LZMA2 dictionary byte from the 7z coder properties (0..40).
 * @return the bytes up to the end chunk or [outputSize], whichever comes first.
 * @throws LzmaException when the stream is invalid or the input ends before the end chunk and
 *   before [outputSize] bytes.
 * @throws IOException when [input] reads from storage that fails.
 */
@Throws(LzmaException::class, IOException::class)
internal fun lzma2Decode(input: CompressedInput, dictionaryProp: Int, outputSize: Int): ByteArray {
    require(outputSize >= 0) { "negative output size" }
    val dictionarySize = LzmaProperties.lzma2DictionarySize(dictionaryProp)
    val window = OutputWindow(outputSize)
    var decoder: LzmaDecoder? = null
    var needInitLevel = NEED_DICTIONARY_RESET
    while (!window.isFull) {
        val control = input.readByte()
        when {
            control == CONTROL_END -> break
            control == CONTROL_COPY_RESET_DICTIONARY || control == CONTROL_COPY -> {
                if (control == CONTROL_COPY && needInitLevel == NEED_DICTIONARY_RESET) {
                    throw LzmaException("LZMA2 stream does not start with a dictionary reset")
                }
                if (control == CONTROL_COPY_RESET_DICTIONARY) {
                    window.resetDictionary()
                    needInitLevel = NEED_PROPERTIES
                }
                window.copyFrom(input, readBigEndian16(input) + 1)
            }
            control < CONTROL_LZMA -> throw LzmaException("LZMA2 control byte $control is invalid")
            else -> {
                if (control < needInitLevel) {
                    throw LzmaException("LZMA2 chunk $control lacks a reset the stream needs")
                }
                needInitLevel = 0
                decoder = decodeLzmaChunk(input, control, window, decoder, dictionarySize)
            }
        }
    }
    return window.toByteArray()
}

/** Decodes one LZMA chunk whose control byte is [control]; returns the decoder to keep. */
private fun decodeLzmaChunk(
    input: CompressedInput,
    control: Int,
    window: OutputWindow,
    current: LzmaDecoder?,
    dictionarySize: Long,
): LzmaDecoder {
    val unpacked = ((control and SIZE_HIGH_BITS) shl 16) + readBigEndian16(input) + 1
    val packed = readBigEndian16(input) + 1
    val reset = (control ushr 5) and 3
    if (reset == RESET_ALL) window.resetDictionary()
    var decoder = current
    if (reset >= RESET_PROPERTIES) {
        val props = LzmaProperties.fromByte(input.readByte(), dictionarySize)
        if (props.lc + props.lp > LZMA2_MAX_LC_PLUS_LP) {
            throw LzmaException("LZMA2 properties with lc + lp above $LZMA2_MAX_LC_PLUS_LP")
        }
        decoder?.setProperties(props)
        decoder = decoder ?: LzmaDecoder(window, props)
    }
    // needInitLevel guarantees properties came before the first LZMA chunk.
    checkNotNull(decoder) { "LZMA chunk without properties" }
    if (reset >= RESET_STATE) decoder.resetState()

    val chunk = ChunkInput(input, packed)
    val rc = RangeDecoder(chunk)
    val chunkEnd = window.size.toLong() + unpacked
    val stopAt = minOf(chunkEnd, window.limit.toLong()).toInt()
    if (decoder.decode(rc, stopAt)) throw LzmaException("end marker inside an LZMA2 chunk")
    if (window.size.toLong() == chunkEnd) {
        if (decoder.pendingLength > 0) throw LzmaException("match runs past the end of an LZMA2 chunk")
        if (chunk.consumed != packed.toLong() || !rc.isFinishedOk) {
            throw LzmaException("LZMA2 chunk used ${chunk.consumed} of its $packed compressed bytes")
        }
    }
    return decoder
}

private fun readBigEndian16(input: CompressedInput): Int = (input.readByte() shl 8) or input.readByte()

private const val CONTROL_END = 0x00
private const val CONTROL_COPY_RESET_DICTIONARY = 0x01
private const val CONTROL_COPY = 0x02
private const val CONTROL_LZMA = 0x80
private const val SIZE_HIGH_BITS = 0x1F
private const val RESET_STATE = 1
private const val RESET_PROPERTIES = 2
private const val RESET_ALL = 3
private const val NEED_DICTIONARY_RESET = 0xE0
private const val NEED_PROPERTIES = 0xC0
private const val LZMA2_MAX_LC_PLUS_LP = 4
