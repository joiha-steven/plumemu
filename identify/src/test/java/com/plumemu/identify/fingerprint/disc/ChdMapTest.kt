package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.disc.DiscImages.putU32be
import com.plumemu.identify.fingerprint.disc.PackedImages.BitWriter
import com.plumemu.identify.source.ByteArraySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The compressed CHD v5 map, against a map written by hand: an unbalanced Huffman tree, a long
 * run, and every pseudo type. Expected offsets are worked out by hand from MAME's rules, not by
 * the reader.
 */
class ChdMapTest {

    /**
     * Code lengths 0:1, 4:3, 5:4, 6:4, 8:4, 9:4, 10..13:5. MAME's canonical assignment (longest
     * codes first, from 0) gives these codes.
     */
    private val code = mapOf(
        0 to "1", 4 to "011", 5 to "0010", 6 to "0011", 8 to "0100", 9 to "0101",
        10 to "00000", 11 to "00001", 12 to "00010", 13 to "00011",
    )

    private fun BitWriter.symbol(s: Int) = code.getValue(s).forEach { write(if (it == '1') 1 else 0, 1) }

    private fun map(bits: ByteArray, firstOffset: Long = 1000, lengthBits: Int = 16, selfBits: Int = 8, parentBits: Int = 8): ByteArray {
        val header = ByteArray(16)
        header.putU32be(0, bits.size.toLong())
        header.putU32be(4, firstOffset ushr 16)
        header[8] = (firstOffset ushr 8).toByte()
        header[9] = firstOffset.toByte()
        header[12] = lengthBits.toByte()
        header[13] = selfBits.toByte()
        header[14] = parentBits.toByte()
        return header + bits
    }

    private fun handMap(): ByteArray {
        val w = BitWriter()
        // Tree, 4-bit fields: "1 1" is a literal 1; "1 5 1" is 5 repeated 1 + 3 times.
        for (nibble in listOf(1, 1, 0, 0, 0, 3, 4, 4, 0, 4, 4, 1, 5, 1, 0, 0)) w.write(nibble.toLong(), 4)
        // Types: 0, NONE, SELF, SELF_1, SELF_0, PARENT_SELF, PARENT_1, PARENT_0, PARENT, 0, then
        // RLE_LARGE with counts 0 and 4 (2 + 16 + 0 * 16 + 4 = 22 more of type 0), then NONE.
        for (s in listOf(0, 4, 5, 10, 9, 11, 13, 12, 6, 0, 8, 0, 4, 4)) w.symbol(s)
        // Per hunk: lengths (16 bits) and CRCs (16), self and parent references (8).
        w.write(100, 16); w.write(0, 16) // hunk 0
        w.write(0, 16) // hunk 1: NONE, CRC only
        w.write(0, 8) // hunk 2: SELF of hunk 0
        w.write(40, 8) // hunk 8: PARENT unit 40
        w.write(200, 16); w.write(0, 16) // hunk 9
        repeat(23) { w.write(50, 16); w.write(0, 16) } // hunks 10-32
        w.write(0, 16) // hunk 33: NONE
        return map(w.bytes())
    }

    @Test
    fun handWrittenMapGivesHandComputedOffsets() {
        val map = CompressedChdMap.open(ByteArraySource(handMap()), 0, hunkCount = 34, hunkBytes = 4096, unitBytes = 2048)
        val expected = listOf(
            ChdHunk(0, 100, 1000),
            ChdHunk(4, 4096, 1100),
            ChdHunk(5, 0, 0),
            ChdHunk(5, 0, 1), // SELF_1: last self + 1
            ChdHunk(5, 0, 1), // SELF_0: last self
            ChdHunk(6, 0, 10), // PARENT_SELF: hunk 5 * 4096 / 2048
            ChdHunk(6, 0, 12), // PARENT_1: last parent + 4096 / 2048
            ChdHunk(6, 0, 12), // PARENT_0
            ChdHunk(6, 0, 40),
            ChdHunk(0, 200, 5196),
            ChdHunk(0, 50, 5396),
            ChdHunk(0, 50, 5446),
        )
        for ((i, hunk) in expected.withIndex()) assertEquals("hunk $i", hunk, map.hunk(i.toLong()))
        assertEquals(ChdHunk(0, 50, 6496), map.hunk(32))
        assertEquals(ChdHunk(4, 4096, 6546), map.hunk(33))
        val past = runCatching { map.hunk(34) }.exceptionOrNull()
        assertTrue("$past", past is NotRead)
    }

    @Test
    fun overSubscribedTreeIsANoteNotAnOverflow() {
        val w = BitWriter()
        // Three literal 1-bit codes: more than the 8-bit table holds.
        for (nibble in listOf(1, 1, 1, 1, 1, 1) + List(13) { 0 }) w.write(nibble.toLong(), 4)
        repeat(8) { w.write(0, 8) }
        val error = runCatching { CompressedChdMap.open(ByteArraySource(map(w.bytes())), 0, 4, 4096, 2048) }.exceptionOrNull()
        assertTrue("$error", error is NotRead && error.message!!.contains("Huffman"))
    }

    @Test
    fun aMapShorterThanItsHunkCountStopsEarly() {
        val w = BitWriter()
        repeat(16) { w.write(4, 4) } // every symbol 4 bits, code = symbol
        repeat(3) { w.write(0, 4) } // three hunks of type 0
        val bits = w.bytes()
        val ends = runCatching { CompressedChdMap.open(ByteArraySource(map(bits)), 0, 1000, 4096, 2048) }.exceptionOrNull()
        assertTrue("$ends", ends is NotRead && ends.message!!.contains("map ends"))
        val tooMany = runCatching { CompressedChdMap.open(ByteArraySource(map(bits)), 0, 10_000_000, 4096, 2048) }.exceptionOrNull()
        assertTrue("$tooMany", tooMany is NotRead && tooMany.message!!.contains("cannot describe"))
    }
}
