package com.plumemu.identify.codec.lzma

import com.plumemu.identify.codec.lzma.LzmaFixtures.ANY_OUTPUT
import com.plumemu.identify.codec.lzma.LzmaFixtures.crc
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.RandomAccessSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

/** Wrong paths: broken, truncated and hostile streams fail with [LzmaException], bounded. */
class LzmaRobustnessTest {

    @Test(timeout = 60_000)
    fun everyTruncationIsAnError() {
        for (fixture in LzmaFixtures.all) {
            val bytes = fixture.bytes
            // Every length for small fixtures; a spread of lengths for the big ones.
            val step = maxOf(1, bytes.size / 400)
            for (length in (0 until bytes.size step step) + (bytes.size - 1)) {
                if (length < 0) continue
                assertThrows("${fixture.file} cut to $length", LzmaException::class.java) {
                    LzmaFixtures.decode(fixture, bytes.copyOf(length))
                }
            }
        }
    }

    @Test(timeout = 60_000)
    fun flippedBitsFailOrStayBounded() {
        val random = Random(20261021)
        val fixtures = LzmaFixtures.all.filter { it.bytes.size in 20..10_000 }
        var failures = 0
        repeat(200) { round ->
            val fixture = fixtures[round % fixtures.size]
            val bytes = fixture.bytes.copyOf()
            repeat(1 + random.nextInt(3)) {
                // The .lzma header's size field (bytes 5..12) is left alone: flipping it only
                // changes how much output is asked for.
                val alone = fixture.kind == "alone"
                var at = random.nextInt(bytes.size - if (alone) 8 else 0)
                if (alone && at >= LzmaProperties.BLOB_SIZE) at += 8
                bytes[at] = (bytes[at].toInt() xor (1 shl random.nextInt(8))).toByte()
            }
            try {
                val out = LzmaFixtures.decode(fixture, bytes)
                assertTrue("${fixture.file} round $round: ${out.size} bytes", out.size <= ANY_OUTPUT)
            } catch (expected: LzmaException) {
                failures++
            } catch (other: RuntimeException) {
                fail("${fixture.file} round $round threw ${other.javaClass.name}: ${other.message}")
            }
        }
        println("flipped bits: $failures of 200 rounds rejected, the rest decoded within the cap")
        assertTrue("most corruptions are noticed: $failures", failures > 100)
    }

    @Test(timeout = 10_000)
    fun aHugeDeclaredDictionaryAllocatesOnlyTheOutput() {
        val fixture = LzmaFixtures.named("big.lzma")
        val header = fixture.bytes.copyOf()
        for (i in 1..4) header[i] = -1 // dictionary 4 GB - 1
        val props = LzmaProperties.fromBlob(header)
        assertEquals((1L shl 32) - 1, props.dictionarySize)

        val window = OutputWindow(4096)
        val decoder = LzmaDecoder(window, props)
        val input = ByteArrayInput(header, LZMA_ALONE_HEADER_SIZE, header.size - LZMA_ALONE_HEADER_SIZE)
        decoder.decode(RangeDecoder(input), window.limit)
        assertEquals(4096, window.capacity)
        assertEquals(fixture.prefix4096, crc(window.toByteArray()))

        val lzma2 = LzmaFixtures.named("big.lzma2.bin")
        val out = lzma2Decode(ByteArrayInput(lzma2.bytes), dictionaryProp = 40, outputSize = 4096)
        assertEquals(lzma2.prefix4096, crc(out))
    }

    @Test
    fun theOutputBufferGrowsOnlyWithTheOutput() {
        val window = OutputWindow(ANY_OUTPUT)
        assertEquals(OutputWindow.INITIAL_CAPACITY, window.capacity)
        val fixture = LzmaFixtures.named("big.lzma")
        val decoder = LzmaDecoder(window, LzmaProperties.fromBlob(fixture.bytes))
        val input = ByteArrayInput(fixture.bytes, LZMA_ALONE_HEADER_SIZE)
        assertTrue(decoder.decode(RangeDecoder(input), window.limit))
        assertEquals(fixture.size, window.size)
        assertEquals(256 * 1024, window.capacity)
    }

    @Test
    fun aDeclaredSizeAboveTheCapIsNotTrusted() {
        val fixture = LzmaFixtures.named("big.lzma")
        val bytes = fixture.bytes.copyOf()
        for (i in 5..12) bytes[i] = 0x7F // declares about 9 EB
        assertEquals(fixture.prefix4096, crc(lzmaAloneDecode(ByteArrayInput(bytes), maxOutput = 4096)))
    }

    @Test
    fun invalidPropertiesAreRejected() {
        assertThrows(LzmaException::class.java) { LzmaProperties.fromByte(225, 4096) }
        assertThrows(LzmaException::class.java) { LzmaProperties.fromByte(255, 4096) }
        assertThrows(LzmaException::class.java) { LzmaProperties.fromBlob(byteArrayOf(0x5D, 0, 0, 1)) }
        assertThrows(LzmaException::class.java) { LzmaProperties.lzma2DictionarySize(41) }
        val alone = LzmaFixtures.named("text.lzma").bytes.copyOf().also { it[0] = 225.toByte() }
        assertThrows(LzmaException::class.java) { lzmaAloneDecode(ByteArrayInput(alone), ANY_OUTPUT) }
    }

    @Test
    fun aFirstRangeCoderByteOtherThanZeroIsRejected() {
        val fixture = LzmaFixtures.named("text.raw.bin")
        val bytes = fixture.bytes.copyOf().also { it[0] = 1 }
        val thrown = assertThrows(LzmaException::class.java) { LzmaFixtures.decode(fixture, bytes) }
        assertTrue(thrown.message, thrown.message!!.contains("first range-coder byte"))
    }

    @Test
    fun aMatchBeforeAnyOutputIsRejected() {
        // code = 0x80000000: the first isMatch bit is 1, isRep 0, then length 2 at distance 1
        // when nothing has been produced yet.
        val stream = byteArrayOf(0, -128, 0, 0, 0) + ByteArray(32)
        val thrown = assertThrows(LzmaException::class.java) {
            lzmaDecode(ByteArrayInput(stream), LzmaProperties(3, 0, 2, 1L shl 16), 100, knownSize = true)
        }
        assertTrue(thrown.message, thrown.message!!.contains("reaches beyond the 0 bytes"))
    }

    @Test
    fun aMatchBeyondTheDeclaredDictionaryIsRejected() {
        // The text was compressed with a 12 KB dictionary and reaches further back than 4 KB.
        val fixture = LzmaFixtures.named("text.raw.bin")
        val small = fixture.props.copy(dictionarySize = 4096)
        val thrown = assertThrows(LzmaException::class.java) {
            lzmaDecode(ByteArrayInput(fixture.bytes), small, fixture.size, knownSize = true)
        }
        assertTrue(thrown.message, thrown.message!!.contains("reaches beyond the 4096 bytes"))
    }

    @Test
    fun anEndMarkerBeforeTheKnownSizeIsAnError() {
        val fixture = LzmaFixtures.named("text.raw-eopm.bin")
        assertThrows(LzmaException::class.java) {
            lzmaDecode(ByteArrayInput(fixture.bytes), fixture.props, fixture.size + 10, knownSize = true)
        }
    }

    @Test
    fun aStreamWithoutAMarkerRunsOutOfInputWhenAskedForMore() {
        val fixture = LzmaFixtures.named("cdhunk.raw.bin")
        assertThrows(LzmaException::class.java) {
            lzmaDecode(ByteArrayInput(fixture.bytes), fixture.props, fixture.size + 1, knownSize = false)
        }
    }

    @Test
    fun badLzma2ChunksAreRejected() {
        val cases = mapOf(
            "control 3" to byteArrayOf(3),
            "control 0x7F" to byteArrayOf(0x7F),
            "copy without a dictionary reset first" to byteArrayOf(2, 0, 0, 0x41, 0),
            "LZMA without a dictionary reset first" to byteArrayOf(0xC0.toByte(), 0, 0, 0, 4, 0x5D, 0, 0, 0, 0, 0, 0),
            "no properties after a reset in a copy" to byteArrayOf(1, 0, 0, 0x41, 0xA0.toByte(), 0, 0, 0, 4, 0, 0, 0, 0, 0, 0),
            "lc + lp above 4" to byteArrayOf(0xE0.toByte(), 0, 0, 0, 4, 103, 0, 0, 0, 0, 0, 0),
            "no end chunk" to byteArrayOf(1, 0, 0, 0x41),
            "empty" to ByteArray(0),
        )
        for ((name, stream) in cases) {
            assertThrows(name, LzmaException::class.java) { lzma2Decode(ByteArrayInput(stream), 16, ANY_OUTPUT) }
        }
    }

    @Test
    fun anLzma2ChunkThatLeavesCompressedBytesUnusedIsRejected() {
        val bytes = LzmaFixtures.named("text.lzma2.bin").bytes
        assertEquals(0xE0, bytes[0].toInt() and 0xE0)
        val packed = (((bytes[3].toInt() and 0xFF) shl 8) or (bytes[4].toInt() and 0xFF)) + 1
        // Declare one more compressed byte and put a spare zero after the chunk.
        val grown = bytes.copyOf()
        grown[3] = (packed shr 8).toByte()
        grown[4] = packed.toByte()
        val stretched = grown.copyOfRange(0, 6 + packed) + byteArrayOf(0) + grown.copyOfRange(6 + packed, grown.size)
        val thrown = assertThrows(LzmaException::class.java) { lzma2Decode(ByteArrayInput(stretched), 16, ANY_OUTPUT) }
        assertTrue(thrown.message, thrown.message!!.contains("compressed bytes"))
    }

    @Test
    fun aFileShorterThanTheRangeEndsTheInput() {
        val fixture = LzmaFixtures.named("text.raw.bin")
        val source = ByteArraySource(fixture.bytes.copyOf(fixture.bytes.size / 2))
        val input = SourceInput(source, start = 0, length = fixture.bytes.size.toLong())
        assertThrows(LzmaException::class.java) { lzmaDecode(input, fixture.props, fixture.size, knownSize = true) }
    }

    @Test
    fun aStorageFailureStaysAnIoException() {
        val failing = object : RandomAccessSource {
            override val size = 1000L
            override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int =
                throw IOException("card removed")
            override fun close() = Unit
        }
        val thrown = assertThrows(IOException::class.java) {
            lzmaDecode(SourceInput(failing, 0, 1000), LzmaProperties(3, 0, 2, 4096), 100, knownSize = true)
        }
        assertEquals("card removed", thrown.message)
    }
}
