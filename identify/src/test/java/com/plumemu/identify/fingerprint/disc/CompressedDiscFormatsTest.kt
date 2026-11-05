package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.ContainerFixtures
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.disc.PackedImages.HunkPlan
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHD `zstd` / `cdzs` hunks, CSO v2, ZSO, RVZ and WIA give the serial the plain image of the same
 * disc gives; damaged or unknown data gives a note and no serial.
 */
class CompressedDiscFormatsTest {

    private fun read(bytes: ByteArray, name: String, system: String): Pair<Fingerprints, Long> {
        val counting = CountingSource(ByteArraySource(bytes))
        val f = FingerprintReader().read(counting, name, system, Depth.QUICK)
        println("BYTES $name (${bytes.size} bytes): ${f.productCode} read ${counting.bytesRead} bytes ${f.notes}")
        return f to counting.bytesRead
    }

    private fun sameAsPlain(packed: ByteArray, name: String, system: String, plain: ByteArray, plainName: String): Long {
        val (expected, _) = read(plain, plainName, system)
        val (f, bytes) = read(packed, name, system)
        assertTrue(expected.productCode != null)
        assertEquals("$name ${f.notes}", expected.productCode, f.productCode)
        assertEquals(name, expected.codeKind, f.codeKind)
        assertEquals(name, expected.internalTitle, f.internalTitle)
        assertTrue("$name ${f.notes}", f.notes.isEmpty())
        assertTrue("$name read $bytes", bytes < DiscReader.DEFAULT_CAP)
        return bytes
    }

    private fun cdChd(raw: ByteArray, codec: String, ecc: Set<Int> = emptySet()) = PackedImages.chd(
        PackedImages.toFrames(raw), PackedImages.CD_FRAME * PackedImages.FRAMES_PER_HUNK, PackedImages.CD_FRAME, listOf(codec),
        eccFrames = ecc,
    )

    @Test
    fun chdWithZstdAndCdzsHunksGivesThePlainImagesSerial() {
        val cooked = DiscImages.ps1Iso(padTo = 2 shl 20)
        val raw = DiscImages.toRaw(cooked, mode = 2)
        sameAsPlain(cdChd(raw, "cdzs", ecc = setOf(16, 17, 18, 19)), "game.chd", "psx", raw, "game.bin")
        sameAsPlain(cdChd(raw, "cdzl", ecc = setOf(16, 17)), "game.chd", "psx", raw, "game.bin")
        val ps2 = DiscImages.ps2Iso(padTo = 1 shl 20)
        sameAsPlain(PackedImages.chd(ps2, 8192, 2048, listOf("zstd")), "game.chd", "ps2", ps2, "game.iso")
        // A mixed map: a stored hunk, a zstd one and a zlib one.
        sameAsPlain(PackedImages.chd(ps2, 8192, 2048, listOf("zstd", "zlib"), plan = { HunkPlan(it % 2) }), "game.chd", "ps2", ps2, "game.iso")
    }

    @Test
    fun chdHunksFromTheZstdCommandLineGiveTheSerial() {
        for ((file, codec) in listOf("ps1-cd.cdzs.bin" to "cdzs", "ps1-cd-64k.cdzs.bin" to "cdzs", "ps1-dvd.zstd.bin" to "zstd")) {
            val fixture = ContainerFixtures.named(file)
            val (f, bytes) = read(ChdLzmaTest.chdOf(fixture, codec), "game.chd", "psx")
            assertEquals(file + f.notes, "SLUS-99999", f.productCode)
            assertTrue(f.notes.toString(), f.notes.isEmpty())
            // Header, map, the stored sectors asked for and the whole compressed hunk (2-3 KB), which the CRC-16 needs.
            assertTrue("$file read $bytes", bytes < 8 * 1024)
        }
    }

    @Test
    fun wholeZstdHunksDecodeToTheSameBytesAsTheCdlzTwins() {
        for ((zstdFile, lzmaFile, codec) in listOf(
            Triple("ps1-cd.cdzs.bin", "ps1-cd.cdlz.bin", "cdzs"),
            Triple("ps1-cd-64k.cdzs.bin", "ps1-cd-64k.cdlz.bin", "cdzs"),
            Triple("ps1-dvd.zstd.bin", "ps1-dvd.lzma.bin", "zstd"),
        )) {
            val fixture = ContainerFixtures.named(zstdFile)
            val chd = ChdLzmaTest.chdOf(fixture, codec)
            val source = ChdSource(ByteArraySource(chd), ChdHeader.read(ByteArraySource(chd)))
            val hunk = fixture.int("hunk")
            val at = ChdLzmaTest.hunkIndex(fixture, codec).toLong() * hunk
            val whole = ByteArray(hunk)
            assertEquals(hunk, source.readAt(at, whole, 0, hunk))
            val crc = java.util.zip.CRC32().apply { update(whole) }.value
            assertEquals(zstdFile, fixture.crc(), crc)
            assertEquals(zstdFile, ContainerFixtures.named(lzmaFile).crc(), crc)
        }
    }

    private fun indexOf(bytes: ByteArray, text: String): Int =
        (0..bytes.size - text.length).first { at -> text.indices.all { bytes[at + it] == text[it].code.toByte() } }

    private fun assertCrcNote(what: String, f: Fingerprints) {
        assertNull(what, f.productCode)
        assertTrue(f.notes.toString(), f.notes.any { it.contains("fails its CRC-16") })
    }

    @Test
    fun aHunkThatFailsItsCrcGivesNoSerialEvenWhenItStillDecodes() {
        // A byte of the serial flipped in the stored literal: the zstd data stays valid, the text changes.
        val fixture = ContainerFixtures.named("ps1-cd.cdzs.bin")
        val flipped = fixture.bytes.copyOf().also { it[indexOf(it, "SLUS_999") + 5] = '8'.code.toByte() }
        assertEquals("SLUS-99999", read(ChdLzmaTest.chdOf(fixture, "cdzs"), "game.chd", "psx").first.productCode)
        assertCrcNote("fixture", read(ChdLzmaTest.chdOf(fixture, "cdzs", flipped), "game.chd", "psx").first)

        // Every codec, built here: the serial read with the right CRC, nothing with a wrong one.
        val raw = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 2 shl 20), mode = 2)
        val hunkBytes = PackedImages.CD_FRAME * PackedImages.FRAMES_PER_HUNK
        for (codec in listOf("cdzs", "cdzl")) {
            val chd = cdChd(raw, codec)
            assertEquals(codec, "SLUS-99999", read(chd, "game.chd", "psx").first.productCode)
            if (codec == "cdzs") {
                val bad = chd.copyOf().also { it[indexOf(it, "SLUS_999") + 5] = '8'.code.toByte() }
                assertCrcNote(codec, read(bad, "game.chd", "psx").first)
            }
            val wrong = PackedImages.chd(PackedImages.toFrames(raw), hunkBytes, PackedImages.CD_FRAME, listOf(codec), crcFlip = (0 until 64).toSet())
            assertCrcNote(codec, read(wrong, "game.chd", "psx").first)
        }
        val ps2 = DiscImages.ps2Iso(padTo = 1 shl 20)
        for (codec in listOf("zstd", "zlib")) {
            val wrong = PackedImages.chd(ps2, 8192, 2048, listOf(codec), crcFlip = (0 until 64).toSet())
            assertCrcNote(codec, read(wrong, "game.chd", "ps2").first)
        }
    }

    @Test
    fun damagedOrWrongSizedZstdHunksAreNotesNeverAGuess() {
        val sha1 = (0 until 20).joinToString("") { String.format("%02x", 0x11 + it) }
        val cd = ContainerFixtures.named("ps1-cd.cdzs.bin")
        val dvd = ContainerFixtures.named("ps1-dvd.zstd.bin")
        val cases = listOf(
            // The zstd magic of the sector part (after the 1-byte bitmap and 2-byte length) broken.
            Triple(cd, "cdzs", cd.bytes.copyOf().also { it[3] = 0 }),
            Triple(dvd, "zstd", dvd.bytes.copyOf().also { it[0] = 0 }),
            // A block cut short inside the frame.
            Triple(dvd, "zstd", dvd.bytes.copyOf(dvd.bytes.size / 2)),
            // Not zstd at all.
            Triple(dvd, "zstd", ByteArray(100) { 0x5A }),
            Triple(cd, "cdzs", ByteArray(100) { 0x5A }),
        )
        for ((fixture, codec, bytes) in cases) {
            val (f, _) = read(ChdLzmaTest.chdOf(fixture, codec, bytes), "game.chd", "psx")
            assertNull("$codec: ${f.productCode}", f.productCode)
            assertEquals(sha1, f.chdSha1)
            assertTrue(f.notes.toString(), f.notes.any { it.startsWith("chd $codec:") })
        }
        // A frame that decodes cleanly to the wrong size: a note too.
        val short = PackedCodecs.zstdFrame(ByteArray(1000))
        val (f, _) = read(ChdLzmaTest.chdOf(dvd, "zstd", short), "game.chd", "psx")
        assertNull(f.productCode)
        assertTrue(f.notes.toString(), f.notes.any { it.contains("zstd data is 1000 bytes") })
    }

    @Test
    fun cdfl_flac_and_huff_hunksStayNotes() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 1 shl 20), mode = 2)
        for (codec in listOf("cdfl", "flac", "huff")) {
            val (f, _) = read(cdChd(raw, codec), "game.chd", "psx")
            assertNull(codec, f.productCode)
            assertTrue(f.notes.toString(), f.notes.any { it.contains("codec $codec not read") })
        }
    }

    @Test
    fun csoV2AndZsoGiveThePlainImagesSerial() {
        val psp = DiscImages.pspIso(padTo = 4 shl 20)
        val ps2 = DiscImages.ps2Iso(padTo = 1 shl 20)
        for (shift in listOf(0, 2)) {
            sameAsPlain(PackedImages.csoV2(psp, shift = shift), "game.cso", "psp", psp, "game.iso")
            sameAsPlain(PackedImages.zso(psp, shift = shift), "game.zso", "psp", psp, "game.iso")
        }
        sameAsPlain(PackedImages.csoV2(ps2, blockSize = 4096), "game.cso", "ps2", ps2, "game.iso")
        sameAsPlain(PackedImages.zso(ps2, blockSize = 16384), "game.zso", "ps2", ps2, "game.iso")
        // v1 is unchanged.
        sameAsPlain(PackedImages.cso(psp, stored = setOf(16)), "game.cso", "psp", psp, "game.iso")
    }

    @Test
    fun aDamagedLz4BlockIsANoteAndNoSerial() {
        val psp = DiscImages.pspIso(padTo = 1 shl 20)
        for (make in listOf({ PackedImages.zso(psp) }, { PackedImages.csoV2(psp) })) {
            val image = make()
            // Sector 16 is the volume descriptor: ruin the bytes of its block (index entry 16) with an offset that no match fits.
            val at = ((image[0x18 + 16 * 4].toInt() and 0xFF) or ((image[0x19 + 16 * 4].toInt() and 0xFF) shl 8) or
                ((image[0x1A + 16 * 4].toInt() and 0xFF) shl 16)) and 0x7FFFFF
            val damaged = image.copyOf()
            // Literals of 15+ with no input behind them: a token that claims more than the block holds.
            for (i in 0 until 6) damaged[at + i] = 0xFF.toByte()
            val (f, _) = read(damaged, "game.cso", "psp")
            if (f.productCode != null) {
                // The block was stored raw (so the bytes read as they are) and the serial still has to be the real one.
                assertEquals("ULUS-99999", f.productCode)
            } else {
                assertTrue(f.notes.toString(), f.notes.isNotEmpty())
            }
        }
        // A block of pure LZ4 garbage in a ZSO.
        val zso = PackedImages.zso(psp)
        val garbage = zso.copyOf()
        val first = (garbage[0x18 + 16 * 4].toInt() and 0xFF) or ((garbage[0x19 + 16 * 4].toInt() and 0xFF) shl 8)
        assertTrue(first > 0)
        val (g, _) = read(garbage.also { for (i in 0 until 12) it[first + i] = 0xF0.toByte() }, "game.zso", "psp")
        assertNull(g.productCode)
        assertTrue(g.notes.toString(), g.notes.any { it.startsWith("cso: block") || it.contains("not ") || it.contains("ISO 9660") })
    }

    @Test
    fun rvzAndWiaGiveTheIdFromTheHeaderCopyAlone() {
        val gc = DiscImages.nintendoHeader("GZZE01", "Test Game: Cube Edition")
        val wii = DiscImages.nintendoHeader("RZZE01", "Test Game Wii", wii = true)
        for ((header, id) in listOf(gc to "GZZE01", wii to "RZZE01")) {
            val plain = header.copyOf(1 shl 20)
            for ((magic, name) in listOf("RVZ" to "game.rvz", "WIA" to "game.wia")) {
                val bytes = sameAsPlain(PackedImages.rvz(header, magic), name, "gc", plain, "game.iso")
                println("BYTES $name read $bytes for $id")
                // 0x48 + 0xDC bytes of the two file headers, plus the 3 magic bytes the format test reads first.
                assertTrue("$name read $bytes", bytes <= 0x48 + 0xDC + 16)
            }
        }
    }

    @Test
    fun anRvzWhoseChecksumsDoNotMatchGivesNoId() {
        val header = DiscImages.nintendoHeader("GZZE01", "Test Game")
        val cases = listOf(
            PackedImages.rvz(header, fixSums = false),
            PackedImages.rvz(header).also { it[0x60] = (it[0x60] + 1).toByte() },
            PackedImages.rvz(header).also { it[0x08] = (it[0x08] + 1).toByte() },
            PackedImages.rvz(header).also { it[0x0F] = 0x10 },
        )
        for ((i, bytes) in cases.withIndex()) {
            val (f, _) = read(bytes, "game.rvz", "gc")
            assertNull("case $i", f.productCode)
            assertTrue("case $i ${f.notes}", f.notes.any { it.startsWith("rvz:") })
        }
        assertNull(read(PackedImages.rvz(header).copyOf(100), "game.wia", "gc").first.productCode)
    }
}
