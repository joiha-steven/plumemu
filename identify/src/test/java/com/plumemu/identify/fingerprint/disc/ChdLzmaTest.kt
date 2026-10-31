package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.ContainerFixtures
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.disc.PackedImages.HunkPlan
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.readFully
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.CRC32

/** CHD hunks in the `cdlz` and `lzma` codecs, from the fixtures of `containers/make_fixtures.py`. */
class ChdLzmaTest {

    private val cd = ContainerFixtures.named("ps1-cd.cdlz.bin")
    private val big = ContainerFixtures.named("ps1-cd-64k.cdlz.bin")
    private val dvd = ContainerFixtures.named("ps1-dvd.lzma.bin")
    private val all = listOf(cd to "cdlz", big to "cdlz", dvd to "lzma")
    private val sha1Hex = (0 until 20).joinToString("") { String.format("%02x", 0x11 + it) }

    private fun read(chd: ByteArray): Pair<Fingerprints, Long> {
        val counting = CountingSource(ByteArraySource(chd))
        val f = FingerprintReader().read(counting, "game.chd", "psx", Depth.QUICK)
        println("chd (${chd.size} bytes): read ${counting.bytesRead} bytes; ${f.notes}")
        return f to counting.bytesRead
    }

    private fun crc(bytes: ByteArray) = CRC32().apply { update(bytes) }.value

    @Test
    fun cdlzHunksGiveTheSerial() {
        val (f, bytes) = read(chdOf(cd, "cdlz"))
        assertEquals(Container.CHD, f.container)
        assertEquals(f.notes.toString(), "SLUS-99999", f.productCode)
        assertEquals(CodeKind.PS1, f.codeKind)
        assertEquals(sha1Hex, f.chdSha1)
        assertTrue(f.notes.toString(), f.notes.isEmpty())
        // Header, map, the two stored sectors asked for and the compressed hunk (2,097 bytes).
        assertTrue("read $bytes", bytes < 6 * 1024)
    }

    @Test
    fun lzmaHunksAndHunksOf64KbGiveTheSerial() {
        for ((fixture, codec) in listOf(big to "cdlz", dvd to "lzma")) {
            val (f, bytes) = read(chdOf(fixture, codec))
            assertEquals(fixture.file + f.notes, "SLUS-99999", f.productCode)
            assertTrue(f.notes.toString(), f.notes.isEmpty())
            assertTrue("read $bytes", bytes < 6 * 1024)
        }
    }

    @Test
    fun wholeHunksDecodeExactlyWithTheSyncPutBack() {
        for ((fixture, codec) in all) {
            val chd = chdOf(fixture, codec)
            val source = ChdSource(ByteArraySource(chd), ChdHeader.read(ByteArraySource(chd)))
            val hunk = fixture.int("hunk")
            val at = hunkIndex(fixture, codec).toLong() * hunk
            // A prefix first (the reader's way), then the whole hunk from the kept compressed bytes.
            assertEquals(100, source.readFully(at, 100).size)
            assertEquals(fixture.file, fixture.crc(), crc(source.readFully(at, hunk)))
        }
    }

    @Test
    fun damagedLzmaHunksAreNotesAndKeepTheSha1() {
        // The first range-coder byte must be 0: cdlz's LZMA part starts after the bitmap and length.
        // In a hunk of 64 KB or more the length takes 3 bytes and the bitmap of 27 frames 4.
        for ((fixture, codec, at) in listOf(Triple(cd, "cdlz", 3), Triple(big, "cdlz", 7), Triple(dvd, "lzma", 0))) {
            val damaged = fixture.bytes.copyOf().also { it[at] = 1 }
            val (f, _) = read(chdOf(fixture, codec, damaged))
            assertNull(f.productCode)
            assertEquals(sha1Hex, f.chdSha1)
            val note = "chd: hunk ${hunkIndex(fixture, codec)}: LZMA data damaged"
            assertTrue(f.notes.toString(), f.notes.any { it.startsWith(note) })
        }
    }

    @Test
    fun aWholeHunkMustUseExactlyItsCompressedBytes() {
        // A prefix does not look at the end of the stream; the whole hunk does, as MAME does.
        val padded = dvd.bytes + ByteArray(7) { 0x55 }
        val chd = chdOf(dvd, "lzma", padded)
        val source = ChdSource(ByteArraySource(chd), ChdHeader.read(ByteArraySource(chd)))
        assertEquals(2048, source.readFully(2L * dvd.int("hunk"), 2048).size)
        val thrown = assertThrows(NotRead::class.java) { source.readFully(2L * dvd.int("hunk"), dvd.int("hunk")) }
        assertTrue(thrown.message, thrown.message!!.contains("LZMA data damaged"))
    }

    @Test
    fun codecsWithoutADecoderStayNotes() {
        for (codec in listOf("cdfl", "flac", "zstd", "cdzs", "huff")) {
            val (f, _) = read(chdOf(cd, codec))
            assertNull(codec, f.productCode)
            assertTrue(f.notes.toString(), f.notes.any { it.contains("codec $codec not read") })
        }
    }

    companion object {
        /**
         * A CHD of four hunks around [fixture]: the hunk holding the fixture's sectors (from
         * sector `first`) compressed with [codec] as [bytes], the others stored zeros.
         */
        internal fun chdOf(fixture: ContainerFixtures.Fixture, codec: String, bytes: ByteArray = fixture.bytes): ByteArray {
            val hunk = fixture.int("hunk")
            val index = hunkIndex(fixture, codec)
            return PackedImages.chd(
                ByteArray(4 * hunk), hunk, unit(codec), codecs = listOf(codec),
                plan = { if (it == index) HunkPlan(0) else HunkPlan(4) },
                encoded = mapOf(index to bytes),
            )
        }

        private fun unit(codec: String) = if (codec == "lzma") 2048 else PackedImages.CD_FRAME

        internal fun hunkIndex(fixture: ContainerFixtures.Fixture, codec: String) =
            fixture.int("first") / (fixture.int("hunk") / unit(codec))
    }
}
