package com.plumemu.achievements.hash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Random

class Lz4Test {
    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun decode(block: ByteArray, size: Int, minOut: Int = size): ByteArray {
        val out = ByteArray(size)
        val n = Lz4.decode(block, 0, block.size, out, minOut)
        return out.copyOf(n)
    }
    private fun fails(block: ByteArray, size: Int, minOut: Int = size) {
        try {
            decode(block, size, minOut)
            fail("expected Lz4Exception")
        } catch (_: Lz4Exception) {
        }
    }

    @Test
    fun aBlockMadeByTheLz4Command() {
        // `lz4 -9 -BD --no-frame-crc` over the text below: the block is the bytes after the 7-byte frame header and 4-byte size.
        val text = "hello hello hello hello hello world, hello hello hello hello!\n"
        val block = hex("6f68656c6c6f200600056f776f726c642c1f0002506c6c6f210a")
        assertEquals(text, String(decode(block, text.length)))
    }

    @Test
    fun literalOnlyBlocks() {
        assertArrayEquals(byteArrayOf(), decode(byteArrayOf(0x00), 0))
        assertArrayEquals("abc".toByteArray(), decode(hex("30616263"), 3))
        // 15 + 5 literals: length extension byte.
        val twenty = ByteArray(20) { (it + 1).toByte() }
        assertArrayEquals(twenty, decode(byteArrayOf(0xF0.toByte(), 5) + twenty, 20))
    }

    @Test
    fun anOverlappingMatchRepeatsWhatItJustWrote() {
        // 1 literal "a", then a match at offset 1 of 15 + 6 + 4 = 25 bytes (length extension byte 6): 26 times "a".
        assertEquals("a".repeat(26), String(decode(hex("1f61010006"), 26)))
        // Offset 2 over "ab": "ababab".
        assertEquals("ababab", String(decode(hex("2061620200"), 6)))
    }

    @Test
    fun longLengthsUseExtensionBytes() {
        val data = ByteArray(1000) { (it % 7).toByte() }
        assertArrayEquals(data, decode(lz4Compress(data), 1000))
        val zeros = ByteArray(5000)
        assertArrayEquals(zeros, decode(lz4Compress(zeros), 5000))
    }

    @Test
    fun randomBlocksRoundTrip() {
        val random = Random(7)
        for (round in 0 until 200) {
            val size = random.nextInt(4000)
            val data = ByteArray(size) { if (random.nextInt(3) == 0) random.nextInt(256).toByte() else (it % 5).toByte() }
            assertArrayEquals("round $round size $size", data, decode(lz4Compress(data), size))
        }
    }

    @Test
    fun paddingAfterTheDataIsNotRead() {
        val data = ByteArray(300) { (it % 9).toByte() }
        val packed = lz4Compress(data)
        assertArrayEquals(data, decode(packed + ByteArray(5), 300))
        // A short last block packed as a full one: stop once the wanted bytes exist.
        val full = data.copyOf(2048)
        val out = ByteArray(2048)
        val written = Lz4.decode(lz4Compress(full), 0, lz4Compress(full).size, out, 300)
        assertTrue(written >= 300)
        assertArrayEquals(data, out.copyOf(300))
    }

    @Test
    fun malformedBlocksFailCleanly() {
        fails(hex("306162"), 3) // literals cut short
        fails(hex("50616263"), 5) // literal run longer than the input
        fails(hex("116100"), 6) // match offset cut short
        fails(hex("11610000"), 6) // offset 0
        fails(hex("1161050000"), 6) // offset before the start
        fails(hex("f0"), 20) // length extension missing
        fails(hex("1f610100"), 40) // match extension missing
        fails(hex("1f61010100"), 10) // match runs past the output
        fails(hex("30616263"), 5) // too few bytes
        fails(hex("30616263"), 2) // literals past the output
        fails(byteArrayOf(0xF0.toByte()) + ByteArray(5000) { 255.toByte() }, 100) // endless length extension
    }

    @Test
    fun randomNoiseNeverCrashesOrOverruns() {
        val random = Random(11)
        repeat(2000) {
            val noise = ByteArray(random.nextInt(60)).also { random.nextBytes(it) }
            val out = ByteArray(random.nextInt(200))
            try {
                val n = Lz4.decode(noise, 0, noise.size, out, 0)
                assertTrue(n <= out.size)
            } catch (_: Lz4Exception) {
            }
        }
    }
}
