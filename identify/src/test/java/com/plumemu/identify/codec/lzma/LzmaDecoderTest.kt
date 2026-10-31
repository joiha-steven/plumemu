package com.plumemu.identify.codec.lzma

import com.plumemu.identify.codec.lzma.LzmaFixtures.ANY_OUTPUT
import com.plumemu.identify.codec.lzma.LzmaFixtures.crc
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Right paths: every fixture decodes to its content, partial decodes read little. */
class LzmaDecoderTest {

    @Test(timeout = 30_000)
    fun everyFixtureDecodesToItsContent() {
        assertEquals(17, LzmaFixtures.all.size)
        for (fixture in LzmaFixtures.all) {
            val input = ByteArrayInput(fixture.bytes)
            val out = LzmaFixtures.decode(fixture, input = input)
            assertEquals(fixture.file, fixture.size, out.size)
            assertEquals(fixture.file, fixture.crc, crc(out))
            // Every stream is used to its last byte: the end marker, the LZMA2 end chunk, or
            // (raw, as CHD checks) the flushed range coder.
            assertEquals(fixture.file, fixture.bytes.size.toLong(), input.consumed)
        }
    }

    @Test
    fun rawStreamsWithAnEndMarkerAlsoDecodeWhenTheSizeIsKnown() {
        // Not as a CHD hunk (exactInput): the marker left unread is refused there (LzmaKnownSizeTest).
        for (fixture in LzmaFixtures.ofKind("raw-eopm")) {
            val out = lzmaDecode(ByteArrayInput(fixture.bytes), fixture.props, fixture.size, knownSize = true, exactInput = false)
            assertEquals(fixture.file, fixture.crc, crc(out))
        }
    }

    @Test
    fun chdStylePropertiesAreTheOnesTheFixturesUse() {
        val hunk = LzmaFixtures.named("cdhunk.raw.bin")
        // lc=3 lp=0 pb=2, dictionary = 8 sectors of 2352 bytes rounded up to 3 * 2^13.
        assertEquals(LzmaProperties(3, 0, 2, 24_576), hunk.props)
        assertEquals(8 * 2352, hunk.size)
    }

    @Test
    fun theFirstFourKilobytesOfA200KbStreamReadOnlyItsStart() {
        val fixture = LzmaFixtures.named("big.lzma")
        val source = CountingSource(ByteArraySource(fixture.bytes))
        val input = SourceInput(source, start = 0, length = source.size)
        val out = lzmaAloneDecode(input, maxOutput = 4096)

        assertEquals(4096, out.size)
        assertEquals(fixture.prefix4096, crc(out))
        println(
            "partial LZMA: 4096 of ${fixture.size} bytes, decoder took ${input.consumed} of " +
                "${fixture.bytes.size} compressed bytes, source read ${source.bytesRead}",
        )
        assertTrue("decoder took ${input.consumed}", input.consumed < 3_000)
        assertTrue("source read ${source.bytesRead}", source.bytesRead <= 4096)
    }

    @Test
    fun theFirstFourKilobytesOfAnLzma2StreamReadOnlyItsStart() {
        val fixture = LzmaFixtures.named("big.lzma2.bin")
        val input = ByteArrayInput(fixture.bytes)
        val out = lzma2Decode(input, fixture.dictionaryProp, outputSize = 4096)
        assertEquals(fixture.prefix4096, crc(out))
        assertTrue("decoder took ${input.consumed}", input.consumed < 3_000)
    }

    @Test
    fun aHunkCanBeReadInPartFromAFileRange() {
        val fixture = LzmaFixtures.named("cdhunk.raw.bin")
        val padded = ByteArray(100) + fixture.bytes + ByteArray(100) { 0x55 }
        val source = CountingSource(ByteArraySource(padded))
        val input = SourceInput(source, start = 100, length = fixture.bytes.size.toLong(), bufferSize = 512)
        val out = lzmaDecode(input, fixture.props, outputSize = 4096, knownSize = false)
        assertEquals(fixture.prefix4096, crc(out))
        assertTrue(source.bytesRead < fixture.bytes.size)
    }

    @Test
    fun lzma2FixturesCoverEveryKindOfChunk() {
        val controls = chunkControls(LzmaFixtures.named("mixed.lzma2.bin").bytes)
        assertTrue(controls.toString(), 0x01 in controls && 0x02 in controls)
        for (reset in listOf(0xA0, 0xC0, 0xE0)) {
            assertTrue("reset $reset in $controls", controls.any { it and 0xE0 == reset })
        }
        val random = chunkControls(LzmaFixtures.named("random.lzma2.bin").bytes)
        assertTrue("random data is stored uncompressed: $random", random.all { it == 0x01 || it == 0x02 })
    }

    @Test
    fun lzma2StopsAtTheLimitInsideAnUncompressedChunk() {
        val fixture = LzmaFixtures.named("random.lzma2.bin")
        val out = lzma2Decode(ByteArrayInput(fixture.bytes), fixture.dictionaryProp, outputSize = 4096)
        assertEquals(fixture.prefix4096, crc(out))
    }

    @Test
    fun anEmptyStreamDecodesToNothing() {
        assertEquals(0, lzmaAloneDecode(ByteArrayInput(LzmaFixtures.named("empty.lzma").bytes), ANY_OUTPUT).size)
        assertEquals(0, lzma2Decode(ByteArrayInput(byteArrayOf(0)), 16, ANY_OUTPUT).size)
    }

    @Test
    fun propertiesDecodeLikeTheSdk() {
        assertEquals(LzmaProperties(3, 0, 2, 1L shl 16), LzmaProperties.fromByte(0x5D, 1L shl 16))
        assertEquals(LzmaProperties(8, 4, 4, 0), LzmaProperties.fromByte(224, 0))
        val blob = byteArrayOf(0x5D, 0, 0, 0, 0x01)
        assertEquals(LzmaProperties(3, 0, 2, 1L shl 24), LzmaProperties.fromBlob(blob))
        val huge = byteArrayOf(0x5D, -1, -1, -1, -1)
        assertEquals((1L shl 32) - 1, LzmaProperties.fromBlob(huge).dictionarySize)
        assertEquals(4096L, LzmaProperties.lzma2DictionarySize(0))
        assertEquals(6144L, LzmaProperties.lzma2DictionarySize(1))
        assertEquals(1L shl 20, LzmaProperties.lzma2DictionarySize(16))
        assertEquals(3L shl 30, LzmaProperties.lzma2DictionarySize(39))
        assertEquals((1L shl 32) - 1, LzmaProperties.lzma2DictionarySize(40))
    }

    @Test(timeout = 60_000)
    fun decodeSpeed() {
        val fixture = LzmaFixtures.named("big.lzma")
        repeat(20) { LzmaFixtures.decode(fixture) }
        val rounds = 50
        val started = System.nanoTime()
        repeat(rounds) { assertEquals(fixture.crc, crc(LzmaFixtures.decode(fixture))) }
        val seconds = (System.nanoTime() - started) / 1e9
        val mbPerSecond = rounds * fixture.size / seconds / (1 shl 20)
        println("LZMA decode speed: %.1f MB/s of output (%d x %d bytes in %.3f s)".format(mbPerSecond, rounds, fixture.size, seconds))
    }

    /** The control byte of every chunk of a raw LZMA2 stream. */
    private fun chunkControls(stream: ByteArray): List<Int> {
        val controls = mutableListOf<Int>()
        var pos = 0
        while (true) {
            val control = stream[pos].toInt() and 0xFF
            if (control == 0) return controls
            controls += control
            val u16 = { at: Int -> ((stream[at].toInt() and 0xFF) shl 8) or (stream[at + 1].toInt() and 0xFF) }
            pos += if (control < 0x80) {
                3 + u16(pos + 1) + 1
            } else {
                (if (control >= 0xC0) 6 else 5) + u16(pos + 3) + 1
            }
        }
    }
}
