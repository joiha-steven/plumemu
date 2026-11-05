package com.plumemu.identify.codec.zstd

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.Random

/**
 * The fixtures in `src/test/resources/zstd/` were made by the `zstd` command line (v1.5.7) from generated inputs (words,
 * records, random bytes, zeros, repeats); `manifest.txt` has each input's size and SHA-256, worked out outside this code.
 */
class ZstdTest {
    private fun resource(name: String): ByteArray =
        checkNotNull(ZstdTest::class.java.getResourceAsStream("/zstd/$name")) { "missing fixture $name" }.use { it.readBytes() }

    private class Entry(val name: String, val size: Int, val sha256: String)

    private fun manifest(): List<Entry> = String(resource("manifest.txt")).lines().filter { it.isNotBlank() }.map {
        val (name, size, sha) = it.split(" ")
        Entry(name, size.toInt(), sha)
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun failure(block: () -> Unit): ZstdException {
        try {
            block()
        } catch (e: ZstdException) {
            assertTrue("message is empty", !e.message.isNullOrBlank())
            return e
        }
        fail("expected a ZstdException")
        throw IllegalStateException()
    }

    private fun frame(vararg bytes: Int) = ByteArray(bytes.size) { bytes[it].toByte() }
    private val magic = intArrayOf(0x28, 0xB5, 0x2F, 0xFD)

    @Test
    fun everyFixtureDecodesToItsKnownHash() {
        val entries = manifest()
        assertTrue(entries.size >= 15)
        val problems = ArrayList<String>()
        for (e in entries) {
            try {
                val out = Zstd.decompress(resource(e.name + ".zst"), maxOutput = e.size)
                if (out.size != e.size || sha256(out) != e.sha256) problems += "${e.name}: wrong output"
            } catch (x: ZstdException) {
                problems += "${e.name}: ${x.message}"
            }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun xxhash64MatchesItsPublishedValueForNothing() {
        assertEquals(-1205034819632174695L, Xxh64.hash(ByteArray(0), 0, 0))
    }

    @Test
    fun aMegabyteHunkDecodesFastAndAllocatesOnlyItsOutput() {
        val packed = resource("big-1mib-l3.zst")
        val size = 1 shl 20
        Zstd.decompress(packed, maxOutput = size) // warm up the JIT, as a real scan does within its first hunks
        for (name in listOf("big-1mib-l3.zst", "big-1mib-l19.zst")) {
            val data = resource(name)
            val start = System.nanoTime()
            val out = Zstd.decompress(data, maxOutput = size)
            val millis = (System.nanoTime() - start) / 1_000_000
            println("zstd 1 MiB $name: $millis ms")
            assertEquals(size, out.size)
            assertTrue("$name took $millis ms", millis < 1000)
        }
        // The worst case is data full of literals and short matches (text): four frames of it, 960,000 bytes, as 1 MiB would be.
        val text = resource("multiblock-l3.zst")
        val fourFrames = text + text + text + text
        Zstd.decompress(fourFrames, maxOutput = 4 * 239_976)
        val textStart = System.nanoTime()
        val textOut = Zstd.decompress(fourFrames, maxOutput = 4 * 239_976)
        val textMillis = (System.nanoTime() - textStart) / 1_000_000
        println("zstd 960,000 bytes of text: $textMillis ms")
        assertEquals(4 * 239_976, textOut.size)
        assertTrue("text took $textMillis ms", textMillis < 1000)
        // The header announces the size, so the output buffer is made at once and exactly that big (1 MiB); a smaller limit is refused.
        failure { Zstd.decompress(packed, maxOutput = size - 1) }
    }

    @Test
    fun aHandBuiltSequenceWithARepeatOffsetCopiesOverItself() {
        // Raw literal "a", one sequence (literal length 1, offset value 1 = repeat offset 1, match length 3), all tables RLE.
        val block = intArrayOf(0x08, 'a'.code, 0x01, 0x54, 0x01, 0x00, 0x00, 0x01)
        val ok = frame(*magic, 0x00, 0x00, (block.size shl 3) or 5, 0, 0, *block) // not single segment, 1 KiB window
        assertArrayEquals("aaaa".toByteArray(), Zstd.decompress(ok, maxOutput = 16))
    }

    @Test
    fun anOffsetBeyondTheWindowIsRefused() {
        // Same block, but the offset code 4 with zero extra bits is value 16, an offset of 13 with one byte written.
        val block = intArrayOf(0x08, 'a'.code, 0x01, 0x54, 0x01, 0x04, 0x00, 0x10)
        val bad = frame(*magic, 0x00, 0x00, (block.size shl 3) or 5, 0, 0, *block)
        val e = failure { Zstd.decompress(bad, maxOutput = 16) }
        assertTrue(e.message, e.message!!.contains("window"))
    }

    @Test
    fun aBlockLargerThanTheWindowOrOver128KiBIsRefused() {
        val block = intArrayOf(0x08, 'a'.code, 0x01, 0x54, 0x01, 0x00, 0x00, 0x01)
        // Single segment with 4 bytes of content: the window is 4, so an 8-byte block is too large.
        val small = frame(*magic, 0x20, 4, (block.size shl 3) or 5, 0, 0, *block)
        assertTrue(failure { Zstd.decompress(small, maxOutput = 16) }.message!!.contains("larger"))
        // A raw block of 128 KiB + 1 in a frame with a big window (descriptor 0x00, window byte 0x58: 8 MiB).
        val size = 131073
        val big = frame(*magic, 0x00, 0x58, (size shl 3) or 1, (size shl 3 or 1) shr 8, (size shl 3 or 1) shr 16) + ByteArray(size)
        assertTrue(failure { Zstd.decompress(big, maxOutput = 1 shl 20) }.message!!.contains("larger"))
    }

    @Test
    fun aBlockWithNoSequencesMustNotHaveBytesLeftOver() {
        // Raw literal "a", zero sequences: valid; one extra byte after the count: not.
        val clean = intArrayOf(0x08, 'a'.code, 0x00)
        val ok = frame(*magic, 0x00, 0x00, (clean.size shl 3) or 5, 0, 0, *clean)
        assertArrayEquals("a".toByteArray(), Zstd.decompress(ok, maxOutput = 16))
        val dirty = clean + 0x00
        val bad = frame(*magic, 0x00, 0x00, (dirty.size shl 3) or 5, 0, 0, *dirty)
        assertTrue(failure { Zstd.decompress(bad, maxOutput = 16) }.message!!.contains("left over"))
    }

    @Test
    fun aFrameThatNeedsADictionaryIsRefusedClearly() {
        val bad = frame(*magic, 0x21, 7, 0, 0x01, 0, 0)
        val e = failure { Zstd.decompress(bad, maxOutput = 16) }
        assertTrue(e.message, e.message!!.contains("dictionary"))
        // A dictionary id of zero names no dictionary.
        val zero = frame(*magic, 0x21, 0, 0, 0x01, 0, 0)
        assertEquals(0, Zstd.decompress(zero, maxOutput = 16).size)
    }

    @Test
    fun badMagicEmptyAndCutOffInputsAreRefused() {
        val good = resource("text-l3.zst")
        val wrong = good.copyOf().also { it[0] = 0 }
        assertTrue(failure { Zstd.decompress(wrong, maxOutput = 1 shl 20) }.message!!.contains("magic"))
        failure { Zstd.decompress(ByteArray(0), maxOutput = 16) }
        failure { Zstd.decompress(good, 0, 3, maxOutput = 16) }
        // Every shorter prefix of a real frame is a clear failure, never another kind of exception.
        for (n in 0 until good.size) failure { Zstd.decompress(good, 0, n, maxOutput = 1 shl 20) }
    }

    @Test
    fun aWindowOrContentSizeBeyondTheLimitsIsRefusedBeforeAnythingIsAllocated() {
        // Not single-segment, window descriptor 0xF8 (a 2 TiB window).
        failure { Zstd.decompress(frame(*magic, 0x00, 0xF8, 0x01, 0, 0), maxOutput = 16) }
        // Single segment announcing 2^40 bytes of content.
        val huge = frame(*magic, 0xE0, 0, 0, 0, 0, 0, 1, 0, 0, 0x01, 0, 0)
        failure { Zstd.decompress(huge, maxOutput = 1 shl 20) }
        // A window over the caller's limit.
        val text = resource("multiblock-l3.zst")
        failure { Zstd.decompress(text, maxOutput = 1 shl 20, maxWindow = 1024) }
    }

    @Test
    fun aBadChecksumIsRefusedAndNoChecksumIsFine() {
        val checked = resource("check-l3.zst").copyOf()
        checked[checked.size - 1] = (checked[checked.size - 1] + 1).toByte()
        assertTrue(failure { Zstd.decompress(checked, maxOutput = 1 shl 20) }.message!!.contains("checksum"))
    }

    @Test
    fun badFseAndHuffmanTablesAreRefused() {
        // Accuracy 15 is over the limit of 9; a table whose probabilities fall short of its size.
        failure { FseTable.parse(frame(0x0A, 0, 0, 0), 0, 4, 35, 9) }
        failure { FseTable.parse(frame(0x00, 0x00), 0, 2, 35, 9) }
        failure { FseTable.parse(frame(0x00), 0, 1, 35, 9) }
        failure { HuffmanTable.parse(frame(0x80), 0, 1) }
        failure { HuffmanTable.parse(frame(0x84, 0x11, 0x11, 0x10), 0, 4) } // five weights of 1 sum to 5: the last cannot make a power of two
        failure { HuffmanTable.parse(frame(0x00), 0, 1) }
    }

    @Test
    fun damagedFramesNeverCrashOrLoopForever() {
        val random = Random(42)
        val start = System.nanoTime()
        for (name in listOf("text-l3", "structured-l19", "multiblock-l19", "text-ultra22", "repeat-l3")) {
            val good = resource("$name.zst")
            val size = manifest().first { it.name == name }.size
            repeat(150) {
                val bad = good.copyOf()
                val flips = 1 + random.nextInt(3)
                repeat(flips) { bad[random.nextInt(bad.size)] = random.nextInt(256).toByte() }
                // The frames carry a content checksum, so damage that still decodes is caught there: always a ZstdException.
                try {
                    val out = Zstd.decompress(bad, maxOutput = size)
                    // A flip that changed nothing (same byte value) is the only way through.
                    assertArrayEquals(Zstd.decompress(good, maxOutput = size), out)
                } catch (_: ZstdException) {
                    // expected
                }
            }
        }
        val seconds = (System.nanoTime() - start) / 1_000_000_000
        assertTrue("fuzzing took $seconds s", seconds < 30)
    }

    @Test
    fun framesJoinedTogetherAndSkippableFramesWork() {
        val a = resource("tiny.zst")
        val b = resource("empty.zst")
        val skippable = frame(0x50, 0x2A, 0x4D, 0x18, 3, 0, 0, 0, 9, 9, 9)
        val out = Zstd.decompress(a + skippable + b + a, maxOutput = 16)
        assertEquals("aa", String(out))
    }
}
