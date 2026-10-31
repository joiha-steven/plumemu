package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.disc.PackedImages.HunkPlan
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.SiblingOpener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** CSO, CHD and the track lists that open sibling files. */
class PackedDiscTest {

    private fun read(bytes: ByteArray, name: String, system: String? = null, siblings: SiblingOpener? = null): Pair<Fingerprints, Long> {
        val counting = CountingSource(ByteArraySource(bytes))
        val result = FingerprintReader().read(counting, name, system, Depth.QUICK, siblings)
        val extra = (siblings as? DiscImages.Siblings)?.bytesRead() ?: 0
        println("$name (${bytes.size} bytes): read ${counting.bytesRead + extra} bytes; ${result.notes}")
        return result to counting.bytesRead + extra
    }

    private val sha1Hex = (0 until 20).joinToString("") { String.format("%02x", 0x11 + it) }

    @Test
    fun csoReadsOnlyTheBlocksItNeeds() {
        val iso = DiscImages.pspIso(padTo = 4 shl 20)
        val (f, bytes) = read(PackedImages.cso(iso, stored = setOf(16)), "game.cso", "psp")
        assertEquals(Container.CSO, f.container)
        assertEquals("ULUS-99999", f.productCode)
        assertTrue(f.titleIsCanonical)
        assertTrue("read $bytes", bytes < 16 * 1024)
        val (ps2, _) = read(PackedImages.cso(DiscImages.ps2Iso()), "game.cso", "ps2")
        assertEquals("SLES-99998", ps2.productCode)
    }

    @Test
    fun zsoAndCsoV2AreNotRead() {
        val zso = PackedImages.cso(DiscImages.pspIso()).also { it[0] = 'Z'.code.toByte() }
        val (z, _) = read(zso, "game.zso", "psp")
        assertEquals(Container.ZSO, z.container)
        assertTrue(z.notes.toString(), z.notes.any { it.contains("LZ4") })
        val (v2, _) = read(PackedImages.cso(DiscImages.pspIso(), version = 2), "game.cso", "psp")
        assertEquals(Container.CSO, v2.container)
        assertNull(v2.productCode)
        assertTrue(v2.notes.toString(), v2.notes.any { it.contains("version 2") })
    }

    private fun cdChd(raw: ByteArray, codecs: List<String> = listOf("cdzl"), plan: (Int) -> HunkPlan = { HunkPlan(0) }, ecc: Set<Int> = emptySet()) =
        PackedImages.chd(PackedImages.toFrames(raw), PackedImages.CD_FRAME * PackedImages.FRAMES_PER_HUNK, PackedImages.CD_FRAME, codecs, plan, eccFrames = ecc)

    @Test
    fun chdWithCdzlHunksAndACompressedMapGivesTheSerial() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 2 shl 20), mode = 2)
        // Hunk 0 (sector 0) stored, the rest cdzl; frames 16-19 (the volume descriptor's hunk) had their sync removed.
        val plan = { h: Int -> if (h == 0) HunkPlan(4) else HunkPlan(0) }
        val (f, bytes) = read(cdChd(raw, plan = plan, ecc = setOf(16, 17, 18, 19)), "game.chd", "psx")
        assertEquals(Container.CHD, f.container)
        assertEquals("SLUS-99999", f.productCode)
        assertEquals(sha1Hex, f.chdSha1)
        assertEquals(listOf("code:PS1:SLUS-99999", "chd:$sha1Hex"), f.fixKeys())
        assertTrue("read $bytes", bytes < 16 * 1024)
    }

    @Test
    fun chdSelfHunksAndParentHunks() {
        val logical = ByteArray(8 * 4096).also { for (i in 4096 until 8192) it[i] = 7 }
        // Hunk 1 holds 7s, hunk 3 is a copy of it; hunk 5 says "in the parent".
        val plan = { h: Int -> if (h == 3) HunkPlan(5, selfOf = 1) else if (h == 5) HunkPlan(6) else HunkPlan(0) }
        val chd = PackedImages.chd(logical, hunkBytes = 4096, unitBytes = 2048, codecs = listOf("zlib"), plan = plan)
        val source = ChdSource(ByteArraySource(chd), ChdHeader.read(ByteArraySource(chd)))
        val out = ByteArray(16)
        source.readAt(3 * 4096L + 100, out, 0, 16)
        assertTrue(out.all { it.toInt() == 7 })
        source.readAt(4 * 4096L, out, 0, 16)
        assertTrue(out.all { it.toInt() == 0 })
        val parent = runCatching { source.readAt(5 * 4096L, out, 0, 16) }.exceptionOrNull()
        assertTrue("$parent", parent is NotRead && parent.message!!.contains("parent"))
    }

    @Test
    fun chdHunkCodecsWithoutADecoderKeepTheSha1() {
        // cdlz is read (ChdLzmaTest); cdfl (FLAC) still has no decoder.
        val raw = DiscImages.toRaw(DiscImages.ps1Iso())
        val (f, _) = read(cdChd(raw, codecs = listOf("cdfl", "cdzl", "cdlz")), "game.chd", "psx")
        assertNull(f.productCode)
        assertEquals(sha1Hex, f.chdSha1)
        assertEquals(listOf("chd:$sha1Hex"), f.fixKeys())
        assertTrue(f.notes.toString(), f.notes.any { it.contains("codec cdfl not read") })
        // Bytes that are not LZMA in a cdlz hunk: a note, the SHA-1 kept.
        val (g, _) = read(cdChd(raw, codecs = listOf("cdlz")), "game.chd", "psx")
        assertEquals(listOf("chd:$sha1Hex"), g.fixKeys())
        assertTrue(g.notes.toString(), g.notes.any { it.startsWith("chd cdlz:") || it.contains("LZMA data damaged") })
    }

    @Test
    fun dvdChdWithZlibHunks() {
        val iso = DiscImages.ps2Iso(padTo = 1 shl 20)
        val chd = PackedImages.chd(iso, hunkBytes = 8192, unitBytes = 2048, codecs = listOf("zlib", "lzma"))
        val (f, bytes) = read(chd, "game.chd", "ps2")
        assertEquals("SLES-99998", f.productCode)
        assertTrue("read $bytes", bytes < 16 * 1024)
    }

    @Test
    fun uncompressedChdUsesThePlainMap() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso())
        val chd = PackedImages.chd(PackedImages.toFrames(raw), PackedImages.CD_FRAME * 8, PackedImages.CD_FRAME, compressedMap = false)
        val (f, bytes) = read(chd, "game.chd", "psx")
        assertEquals("SLUS-99999", f.productCode)
        assertTrue("read $bytes", bytes < 64 * 1024)
    }

    @Test
    fun chdWithAllZeroSha1GivesNoChdKey() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso())
        val chd = PackedImages.chd(PackedImages.toFrames(raw), PackedImages.CD_FRAME * 8, PackedImages.CD_FRAME, codecs = listOf("cdlz"), rawSha1 = ByteArray(20))
        val (f, _) = read(chd, "game.chd", "psx")
        assertEquals("0".repeat(40), f.chdSha1)
        assertEquals(emptyList<String>(), f.fixKeys())
    }

    @Test
    fun chdParentHunksAndOldVersionsAreNotes() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso())
        val v3 = cdChd(raw).also { it[15] = 3 }
        val (old, _) = read(v3, "old.chd", "psx")
        assertNull(old.chdSha1)
        assertTrue(old.notes.toString(), old.notes.any { it.contains("version 3") })
    }

    @Test
    fun cueOpensItsDataTrack() {
        val bin = DiscImages.toRaw(DiscImages.ps1Iso(), mode = 2)
        val cue = """
            REM made by a test
            FILE "Test Game (Track 1).bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
            FILE "Test Game (Track 2).bin" BINARY
              TRACK 02 AUDIO
                INDEX 00 00:00:00
                INDEX 01 00:02:00
        """.trimIndent()
        val siblings = DiscImages.Siblings(mapOf("Test Game (Track 1).bin" to bin))
        val (f, bytes) = read(cue.toByteArray(), "Test Game.cue", "psx", siblings)
        assertEquals(Container.CUE, f.container)
        assertEquals("Test Game (Track 1).bin", f.innerName)
        assertEquals("SLUS-99999", f.productCode)
        assertTrue("read $bytes", bytes < 12 * 1024)
    }

    @Test
    fun cueWhoseDataTrackIsNotFirstOrIsMissing() {
        val cooked = DiscImages.ps2Iso()
        val cue = "FILE \"audio.bin\" BINARY\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nFILE game.iso BINARY\nTRACK 02 MODE1/2048\nINDEX 01 00:00:00\n"
        val (f, _) = read(cue.toByteArray(), "x.cue", null, DiscImages.Siblings(mapOf("game.iso" to cooked)))
        assertEquals("SLES-99998", f.productCode)
        val (missing, _) = read(cue.toByteArray(), "x.cue", null, DiscImages.Siblings(emptyMap()))
        assertTrue(missing.notes.toString(), missing.notes.any { it.contains("game.iso not found") })
        val (noOpener, _) = read(cue.toByteArray(), "x.cue", null, null)
        assertTrue(noOpener.notes.toString(), noOpener.notes.any { it.contains("no sibling opener") })
        val (audioOnly, _) = read("FILE a.bin BINARY\nTRACK 01 AUDIO\n".toByteArray(), "x.cue", null, null)
        assertTrue(audioOnly.notes.toString(), audioOnly.notes.any { it.contains("no data track") })
    }

    @Test
    fun cueTrackStartingInsideItsFile() {
        val bin = ByteArray(150 * 2352) + DiscImages.toRaw(DiscImages.ps1Iso())
        val cue = "FILE \"all.bin\" BINARY\nTRACK 01 AUDIO\nINDEX 01 00:00:00\nTRACK 02 MODE2/2352\nINDEX 01 00:02:00\n"
        val (f, _) = read(cue.toByteArray(), "x.cue", "psx", DiscImages.Siblings(mapOf("all.bin" to bin)))
        assertEquals("SLUS-99999", f.productCode)
    }

    @Test
    fun m3uFollowsItsFirstEntryOnceWithItsFolder() {
        val bin = DiscImages.toRaw(DiscImages.ps1Iso())
        val files = mapOf(
            "disc1/game.cue" to "FILE \"game.bin\" BINARY\nTRACK 01 MODE2/2352\nINDEX 01 00:00:00\n".toByteArray(),
            "disc1/game.bin" to bin,
            "loop.m3u" to "loop.m3u\n".toByteArray(),
        )
        val siblings = DiscImages.Siblings(files)
        val (f, _) = read("#EXTM3U\n\ndisc1/game.cue\ndisc2/game.cue\n".toByteArray(), "game.m3u", "psx", siblings)
        assertEquals(Container.M3U, f.container)
        assertEquals("disc1/game.cue", f.innerName)
        assertEquals("SLUS-99999", f.productCode)
        assertEquals(listOf("disc1/game.cue", "disc1/game.bin"), siblings.opened)
        val (loop, _) = read("loop.m3u\n".toByteArray(), "loop.m3u", "psx", DiscImages.Siblings(files))
        assertNull(loop.productCode)
        assertTrue(loop.notes.toString(), loop.notes.any { it.contains("playlist inside a playlist") })
    }

    @Test
    fun gdiReadsIpBinFromTrack3() {
        val track3 = DiscImages.toRaw(DiscImages.iso(systemArea = DiscImages.dreamcastArea(), padTo = 64 * 1024))
        val gdi = "3\n1 0 4 2352 track01.bin 0\n2 450 0 2352 track02.raw 0\n3 45000 4 2352 \"track 03.bin\" 0\n"
        val siblings = DiscImages.Siblings(mapOf("track 03.bin" to track3))
        val (f, bytes) = read(gdi.toByteArray(), "game.gdi", "dreamcast", siblings)
        assertEquals(Container.GDI, f.container)
        assertEquals(CodeKind.DREAMCAST, f.codeKind)
        assertEquals("T-99999N", f.productCode)
        assertEquals("TEST GAME DC", f.internalTitle)
        assertEquals(listOf("track 03.bin"), siblings.opened)
        assertTrue("read $bytes", bytes < 2048)
    }

    @Test
    fun dreamcastChdFindsTrack3InItsMetadata() {
        val track3 = DiscImages.toRaw(DiscImages.iso(systemArea = DiscImages.dreamcastArea(), padTo = 100 * 2048))
        val (f, bytes) = read(PackedImages.dreamcastChd(track3), "game.chd", "dreamcast")
        assertEquals("T-99999N", f.productCode)
        assertTrue("read $bytes", bytes < 8 * 1024)
    }

    @Test
    fun negativeTrackCountsAreANoteAndKeepTheSha1() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso())
        val meta = listOf("CHT2" to "TRACK:1 TYPE:MODE2_RAW SUBTYPE:NONE FRAMES:-5 PREGAP:0 PGTYPE:MODE1 PGSUB:RW POSTGAP:0")
        val chd = PackedImages.chd(PackedImages.toFrames(raw), 2448 * 8, 2448, listOf("cdzl"), metadata = meta)
        val (f, _) = read(chd, "game.chd", "psx")
        assertEquals(sha1Hex, f.chdSha1)
        assertTrue(f.notes.toString(), f.notes.any { it.contains("FRAMES") } && f.notes.none { it.startsWith("read failed") })
        val pregap = listOf("CHGD" to "TRACK:3 TYPE:MODE1_RAW SUBTYPE:NONE FRAMES:10 PAD:0 PREGAP:-1 PGTYPE:VMODE1 PGSUB:RW POSTGAP:0")
        val (g, _) = read(PackedImages.chd(PackedImages.toFrames(raw), 2448 * 8, 2448, listOf("cdzl"), metadata = pregap), "game.chd", "dreamcast")
        assertEquals(sha1Hex, g.chdSha1)
        assertTrue(g.notes.toString(), g.notes.any { it.contains("PREGAP") })
    }

    @Test
    fun anUnexpectedFailureKeepsWhatWasFound() {
        val cue = "FILE game.bin BINARY\nTRACK 01 MODE2/2352\nINDEX 01 00:00:00\n".toByteArray()
        val (f, _) = read(cue, "game.cue", "psx", SiblingOpener { throw IllegalStateException("opener bug") })
        assertEquals(Container.CUE, f.container)
        assertEquals("game.bin", f.innerName)
        assertTrue(f.notes.toString(), f.notes.any { it.startsWith("disc: read failed: IllegalStateException") })
    }

    @Test
    fun chdOfASystemWithoutSerialsStillGivesItsSha1() {
        val chd = cdChd(DiscImages.toRaw(DiscImages.ps1Iso()))
        for (system in listOf("pcenginecd", "tg-cd", "tgcd", "3do", "neogeocd")) {
            val (f, bytes) = read(chd, "game.chd", system)
            assertEquals(system, Container.CHD, f.container)
            assertEquals(system, sha1Hex, f.chdSha1)
            assertNull(system, f.productCode)
            assertEquals(system, listOf("chd:$sha1Hex"), f.fixKeys())
            assertTrue(f.notes.toString(), f.notes.single().startsWith("$system:"))
            assertTrue("$system read $bytes", bytes <= 8 + 124)
        }
    }

    @Test
    fun redumpDreamcastCuePicksTheHighDensityTrack() {
        val cue = """
            REM SINGLE-DENSITY AREA
            FILE "Test Game (Track 1).bin" BINARY
              TRACK 01 MODE1/2352
                INDEX 01 00:00:00
            FILE "Test Game (Track 2).bin" BINARY
              TRACK 02 AUDIO
                INDEX 00 00:00:00
                INDEX 01 00:02:00
            REM HIGH-DENSITY AREA
            FILE "Test Game (Track 3).bin" BINARY
              TRACK 03 MODE1/2352
                INDEX 01 00:00:00
        """.trimIndent()
        val track3 = DiscImages.toRaw(DiscImages.iso(systemArea = DiscImages.dreamcastArea(), padTo = 64 * 1024))
        val track1 = DiscImages.toRaw(DiscImages.iso(padTo = 64 * 1024))
        val files = mapOf("Test Game (Track 1).bin" to track1, "Test Game (Track 3).bin" to track3)
        val (f, _) = read(cue.toByteArray(), "game.cue", "dreamcast", DiscImages.Siblings(files))
        assertEquals("Test Game (Track 3).bin", f.innerName)
        assertEquals("T-99999N", f.productCode)
        // Without the marker, the system id picks track 3 or later.
        val unmarked = cue.lines().filter { !it.startsWith("REM") }.joinToString("\n")
        val (g, _) = read(unmarked.toByteArray(), "game.cue", "dreamcast", DiscImages.Siblings(files))
        assertEquals("T-99999N", g.productCode)
        assertEquals("Test Game (Track 1).bin", TrackLists.cueDataTrack(unmarked)?.fileName)
    }

    @Test
    fun cdzlHunkOf64KbOrMoreHasAThreeByteLength() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 256 * 1024))
        val hunk = 2448 * 27 // 66,096 bytes
        val (f, _) = read(PackedImages.chd(PackedImages.toFrames(raw), hunk, 2448, listOf("cdzl")), "big.chd", "psx")
        assertEquals("SLUS-99999", f.productCode)
    }

    @Test
    fun chdMode2CookedTrackReadsAfterTheSubheader() {
        val frames = PackedImages.mode2CookedFrames(DiscImages.ps1Iso(padTo = 256 * 1024))
        val meta = listOf("CHT2" to "TRACK:1 TYPE:MODE2 SUBTYPE:NONE FRAMES:${frames.size / 2448} PREGAP:0 PGTYPE:MODE1 PGSUB:RW POSTGAP:0")
        val (f, _) = read(PackedImages.chd(frames, 2448 * 8, 2448, listOf("cdzl"), metadata = meta), "game.chd", "psx")
        assertEquals("SLUS-99999", f.productCode)
    }

    @Test
    fun csoWithAnIndexShiftAndPaddedBlocks() {
        val iso = DiscImages.pspIso(padTo = 1 shl 20)
        for (shift in listOf(11, 12)) {
            val (f, _) = read(PackedImages.cso(iso, stored = setOf(16, 18, 19), shift = shift), "game.cso", "psp")
            assertEquals("shift $shift: ${f.notes}", "ULUS-99999", f.productCode)
        }
    }
}
