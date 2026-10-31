package com.plumemu.identify.codec.lzma

import java.io.IOException

/**
 * Decodes a raw LZMA stream (no header): a CHD `lzma` / `cdlz` hunk, a 7z LZMA packed stream,
 * or the stream after the 13-byte header of a `.lzma` file.
 *
 * A Kotlin port of the decoder in Igor Pavlov's LZMA SDK, which is in the public domain:
 * `Java/SevenZip/Compression/LZMA/Decoder.java` for the structure and `C/LzmaDec.c` /
 * `DOC/LzmaSpec.cpp` for the checks (see [LzmaDecoder] and [RangeDecoder]).
 *
 * Bounded by design: it produces at most [outputSize] bytes and allocates at most
 * that much for them, whatever dictionary size [props] declares; it reads only the compressed
 * bytes those output bytes need; every loop is bounded by the output or the input.
 *
 * @param outputSize the most bytes to produce. Decoding stops as soon as they are produced, so
 *   asking for the first 4 KB of a 200 KB stream reads only the start of it.
 * @param knownSize true when the stream holds exactly [outputSize] bytes (an end marker or a
 *   match running past [outputSize] is then an error). False when the stream may end with an
 *   end marker before [outputSize] (`.lzma` files written with an unknown size), or when the
 *   caller only wants the first [outputSize] bytes.
 * @param exactInput true when the stream also fills [input] exactly, as a CHD `lzma` / `cdlz`
 *   hunk does: after [outputSize] bytes every compressed byte must be used, else
 *   [LzmaException]. MAME (`chd_lzma_decompressor::decompress`) checks those two lengths, the
 *   bytes consumed and the bytes produced; this also checks that the range coder ends at 0,
 *   which a whole stream written without an end marker always does. Such a stream has no end
 *   marker; one written there is rejected too. Only meaningful with [knownSize]; false by
 *   default, so only a caller that knows its input is exactly one stream asks for it.
 * @return the decoded bytes: exactly [outputSize] when [knownSize], otherwise up to the end
 *   marker or [outputSize], whichever comes first.
 * @throws LzmaException when the stream is invalid or the input ends before the output does.
 * @throws IOException when [input] reads from storage that fails.
 */
@Throws(LzmaException::class, IOException::class)
internal fun lzmaDecode(
    input: CompressedInput,
    props: LzmaProperties,
    outputSize: Int,
    knownSize: Boolean,
    exactInput: Boolean = false,
): ByteArray {
    require(outputSize >= 0) { "negative output size" }
    val window = OutputWindow(outputSize)
    val decoder = LzmaDecoder(window, props)
    val rc = RangeDecoder(input)
    val endMarker = decoder.decode(rc, outputSize)
    if (knownSize) {
        if (endMarker) throw LzmaException("end marker after ${window.size} of $outputSize bytes")
        if (decoder.pendingLength > 0) throw LzmaException("last match runs past $outputSize bytes")
        if (exactInput && (input.consumed != input.length || !rc.isFinishedOk)) {
            throw LzmaException(
                "stream of $outputSize bytes used ${input.consumed} of ${input.length} compressed bytes" +
                    if (rc.isFinishedOk) "" else " and did not end cleanly",
            )
        }
    }
    return window.toByteArray()
}

/**
 * Size of the header of a `.lzma` file (LZMA_Alone): 5 property bytes and the uncompressed
 * size as 8 little-endian bytes, all ones when unknown (the stream then ends with a marker).
 */
internal const val LZMA_ALONE_HEADER_SIZE = 13

/**
 * Decodes a `.lzma` (LZMA_Alone) file held in [input]: reads the 13-byte header, then at most
 * [maxOutput] bytes of the stream. A declared size above [maxOutput] is not trusted for
 * allocation; the output just stops at [maxOutput]. A declared size is a promise: a stream
 * that ends (end marker) before it, or before [maxOutput] when it is larger, is an error, not
 * short data. Bytes after the stream (an end marker the spec allows after a known size) are
 * not read.
 *
 * @throws LzmaException when the header or the stream is invalid.
 * @throws IOException when [input] reads from storage that fails.
 */
@Throws(LzmaException::class, IOException::class)
internal fun lzmaAloneDecode(input: CompressedInput, maxOutput: Int): ByteArray {
    val header = ByteArray(LZMA_ALONE_HEADER_SIZE) { input.readByte().toByte() }
    val props = LzmaProperties.fromBlob(header)
    var declared = 0L
    for (i in LZMA_ALONE_HEADER_SIZE - 1 downTo LzmaProperties.BLOB_SIZE) {
        declared = (declared shl 8) or (header[i].toLong() and 0xFF)
    }
    // All ones (-1) means "unknown, the stream ends with a marker".
    if (declared == -1L) return lzmaDecode(input, props, maxOutput, knownSize = false)
    if (declared in 0..maxOutput.toLong()) {
        return lzmaDecode(input, props, declared.toInt(), knownSize = true, exactInput = false)
    }
    val out = lzmaDecode(input, props, maxOutput, knownSize = false)
    if (out.size < maxOutput) throw LzmaException("stream ends after ${out.size} of the $declared bytes it declares")
    return out
}
