package com.plumemu.identify.codec.lzma

import com.plumemu.identify.codec.lzma.LzmaFixtures.ANY_OUTPUT
import com.plumemu.identify.codec.lzma.LzmaFixtures.crc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Streams whose size is known: a CHD hunk must use exactly its compressed bytes, a `.lzma` header
 * that declares a size must deliver it, and a match may not run past the end.
 */
class LzmaKnownSizeTest {

    private fun assertLzmaError(expected: String, block: () -> Unit) {
        val thrown = assertThrows(LzmaException::class.java) { block() }
        assertTrue(thrown.message, thrown.message!!.contains(expected))
    }

    @Test
    fun aHunkWithBytesAfterItsStreamIsAnError() {
        for (fixture in LzmaFixtures.ofKind("raw")) {
            val padded = fixture.bytes + byteArrayOf(0, 0x11)
            assertLzmaError("compressed bytes") {
                lzmaDecode(ByteArrayInput(padded), fixture.props, fixture.size, knownSize = true, exactInput = true)
            }
            // The same stream when the caller does not ask for the exact length (a .lzma file).
            val out = lzmaDecode(ByteArrayInput(padded), fixture.props, fixture.size, knownSize = true, exactInput = false)
            assertEquals(fixture.file, fixture.crc, crc(out))
        }
    }

    @Test
    fun aHunkWhoseRangeCoderDoesNotEndAtZeroIsAnError() {
        for (fixture in LzmaFixtures.ofKind("raw")) {
            val damaged = fixture.bytes.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            assertThrows(fixture.file, LzmaException::class.java) {
                lzmaDecode(ByteArrayInput(damaged), fixture.props, fixture.size, knownSize = true, exactInput = true)
            }
        }
    }

    @Test
    fun anEndMarkerAfterTheKnownSizeIsRefusedForAHunkOnly() {
        for (fixture in LzmaFixtures.ofKind("raw-eopm")) {
            assertLzmaError("compressed bytes") {
                lzmaDecode(ByteArrayInput(fixture.bytes), fixture.props, fixture.size, knownSize = true, exactInput = true)
            }
            val out = lzmaDecode(ByteArrayInput(fixture.bytes), fixture.props, fixture.size, knownSize = true, exactInput = false)
            assertEquals(fixture.file, fixture.crc, crc(out))
        }
    }

    @Test
    fun aDotLzmaFileWithADeclaredSizeDecodes() {
        for (kind in listOf("raw", "raw-eopm")) {
            val fixture = LzmaFixtures.named("text.$kind.bin")
            val out = lzmaAloneDecode(ByteArrayInput(alone(fixture, fixture.size.toLong())), ANY_OUTPUT)
            assertEquals(kind, fixture.crc, crc(out))
        }
    }

    @Test
    fun aDotLzmaFileDeclaringMoreThanItHoldsIsAnError() {
        val fixture = LzmaFixtures.named("text.raw-eopm.bin")
        // Declared size within the cap: the end marker comes too early.
        assertLzmaError("end marker") { lzmaAloneDecode(ByteArrayInput(alone(fixture, fixture.size + 10L)), ANY_OUTPUT) }
        // Declared size above the cap: the stream must still fill the cap.
        assertLzmaError("of the 50000 bytes it declares") {
            lzmaAloneDecode(ByteArrayInput(alone(fixture, 50_000)), maxOutput = 20_000)
        }
        // A declared size above the cap that the stream does fill: the first bytes, as before.
        val prefix = lzmaAloneDecode(ByteArrayInput(alone(fixture, 50_000)), maxOutput = 4096)
        assertEquals(fixture.prefix4096, crc(prefix))
    }

    @Test
    fun theLastMatchRunningPastTheKnownSizeIsAnError() {
        val fixture = LzmaFixtures.named("zeros.raw.bin")
        assertLzmaError("last match runs past") {
            lzmaDecode(ByteArrayInput(fixture.bytes), fixture.props, fixture.size - 1, knownSize = true)
        }
    }

    @Test
    fun aMatchRunningPastTheEndOfAnLzma2ChunkIsAnError() {
        val fixture = LzmaFixtures.named("zeros.raw.bin")
        assertEquals(fixture.crc, crc(lzma2Decode(ByteArrayInput(lzma2Chunk(fixture, fixture.size)), 16, ANY_OUTPUT)))
        assertLzmaError("match runs past the end of an LZMA2 chunk") {
            lzma2Decode(ByteArrayInput(lzma2Chunk(fixture, fixture.size - 1)), 16, ANY_OUTPUT)
        }
    }

    /** A `.lzma` file: the fixture's properties, [declared] as its size, then its stream. */
    private fun alone(fixture: LzmaFixtures.Fixture, declared: Long): ByteArray {
        val header = ByteArray(LZMA_ALONE_HEADER_SIZE)
        header[0] = fixture.extra.getValue("props").toInt(16).toByte()
        for (i in 0 until 4) header[1 + i] = (fixture.props.dictionarySize ushr (8 * i)).toByte()
        for (i in 0 until 8) header[5 + i] = (declared ushr (8 * i)).toByte()
        return header + fixture.bytes
    }

    /** One LZMA2 chunk (dictionary reset, properties 0x5D) declaring [unpacked] bytes, then the end. */
    private fun lzma2Chunk(fixture: LzmaFixtures.Fixture, unpacked: Int): ByteArray {
        val u = unpacked - 1
        val p = fixture.bytes.size - 1
        val head = byteArrayOf((0xE0 or (u ushr 16)).toByte(), (u ushr 8).toByte(), u.toByte(), (p ushr 8).toByte(), p.toByte(), 0x5D)
        return head + fixture.bytes + byteArrayOf(0)
    }
}
