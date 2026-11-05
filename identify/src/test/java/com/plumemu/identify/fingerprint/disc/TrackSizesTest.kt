package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.disc.DiscImages.putU64le
import com.plumemu.identify.pack.TrackSizeKeys
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.SiblingOpener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Track-size keys read from sizes alone (`identify/PACKS.md`, "Normalising a copy"). */
class TrackSizesTest {
    /** A file of [size] zero bytes that is never allocated: the key needs only a length. */
    private class Sized(override val size: Long) : RandomAccessSource {
        override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int = -1
        override fun close() = Unit
    }

    private fun opener(sizes: Map<String, Long>, opened: MutableList<String>? = null) = SiblingOpener { name ->
        opened?.add(name)
        sizes[name]?.let { Sized(it) }
    }

    private fun keys(sizes: List<Long>) = listOf(TrackSizeKeys.ordered(sizes), TrackSizeKeys.total(sizes.sum()))

    private fun sheet(text: String, container: Container, files: Map<String, Long>, system: String? = "psx") =
        TrackSizes.read(ByteArraySource(text.toByteArray()), container, system, opener(files))

    // A six-track layout as Redump lists it: each track one file, whole 2352-byte sectors.
    private val redump = listOf(23_520L * 100, 2_352L * 4_100, 2_352L * 3_000, 2_352L * 75, 2_352L * 9_999, 2_352L * 15_000)
    private val names = redump.indices.map { "Game (Track ${it + 1}).bin" }

    @Test
    fun cueWithAFilePerTrackGivesTheSizesInTrackOrder() {
        val cue = names.mapIndexed { i, n -> "FILE \"$n\" BINARY\n  TRACK ${"%02d".format(i + 1)} ${if (i == 0) "MODE2/2352" else "AUDIO"}\n    INDEX 01 00:00:00" }.joinToString("\n")
        val result = sheet(cue, Container.CUE, names.zip(redump).toMap())
        assertEquals(keys(redump), result.keys)
        // The files are listed out of name order: the cue's order decides.
        val shuffled = names.reversed().mapIndexed { i, n -> "FILE \"$n\" BINARY\n  TRACK ${"%02d".format(i + 1)} AUDIO\n    INDEX 01 00:00:00" }.joinToString("\n")
        assertEquals(keys(redump.reversed()), sheet(shuffled, Container.CUE, names.zip(redump).toMap()).keys)
    }

    @Test
    fun singleBinCueGivesOneSizeThatAlsoMatchesTheSumOfTheTracks() {
        val total = redump.sum()
        val cue = "FILE \"Game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n  TRACK 02 AUDIO\n    INDEX 01 20:00:00\n"
        val result = sheet(cue, Container.CUE, mapOf("Game.bin" to total))
        assertEquals(keys(listOf(total)), result.keys)
        // The second key is the one a split-bin dump of the same disc stores as TOTAL.
        assertEquals(TrackSizeKeys.total(redump.sum()), result.keys?.get(1))
    }

    @Test
    fun cueTrackOfCookedSectorsCountsFramesOfRawSectors() {
        val cooked = 2_048L * 30_000
        val cue = "FILE \"Game.iso\" BINARY\n  TRACK 01 MODE1/2048\n    INDEX 01 00:00:00\n"
        assertEquals(keys(listOf(2_352L * 30_000)), sheet(cue, Container.CUE, mapOf("Game.iso" to cooked)).keys)
    }

    @Test
    fun gdiGivesItsTrackFilesInOrder() {
        val sizes = listOf(2_352L * 302, 2_352L * 75, 2_352L * 28_000)
        val gdi = "3\n1 0 4 2352 track01.bin 0\n2 450 0 2352 track02.raw 0\n3 45000 4 2352 \"track 03.bin\" 0\n"
        val files = mapOf("track01.bin" to sizes[0], "track02.raw" to sizes[1], "track 03.bin" to sizes[2])
        val result = sheet(gdi, Container.GDI, files, "dreamcast")
        assertEquals(keys(sizes), result.keys)
    }

    @Test
    fun aMissingTrackFileGivesNoKeyAndSaysWhich() {
        val cue = "FILE \"a.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\nFILE \"b.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n"
        val result = sheet(cue, Container.CUE, mapOf("a.bin" to 2_352L * 100))
        // No key yet (null: asked again later, not "looked at, none").
        assertNull(result.keys)
        assertTrue(result.note.toString(), result.note.toString().contains("b.bin"))
        assertNull(sheet(cue, Container.CUE, emptyMap()).keys)
        // No sibling opener at all: also no key yet.
        assertNull(TrackSizes.read(ByteArraySource(cue.toByteArray()), Container.CUE, "psx", null).keys)
    }

    @Test
    fun aTrackBeforeAnyFileLineOrNoTrackAtAllGivesNoKey() {
        assertEquals(emptyList<Long>(), sheet("TRACK 01 MODE2/2352\n", Container.CUE, mapOf("a.bin" to 2_352L)).keys)
        assertEquals(emptyList<Long>(), sheet("REM nothing\n", Container.CUE, emptyMap()).keys)
    }

    @Test
    fun playlistUsesItsFirstDiscAndItsFolder() {
        val cue = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
        val files = mapOf("disc1/game.cue" to cue.toByteArray(), "disc2/game.cue" to cue.toByteArray())
        val sizes = mapOf("disc1/game.bin" to 2_352L * 5_000, "disc2/game.bin" to 2_352L * 7_000)
        val opened = ArrayList<String>()
        val siblings = SiblingOpener { name ->
            opened += name
            files[name]?.let { ByteArraySource(it) } ?: sizes[name]?.let { Sized(it) }
        }
        val result = TrackSizes.read(ByteArraySource("#EXTM3U\ndisc1/game.cue\ndisc2/game.cue\n".toByteArray()), Container.M3U, "psx", siblings)
        assertEquals(keys(listOf(2_352L * 5_000)), result.keys)
        assertTrue(opened.toString(), opened.none { it.startsWith("disc2") })
    }

    private fun cdChd(frames: List<Int>, tag: String = "CHT2", pregap: String = "PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0"): ByteArray {
        val padded = frames.sumOf { (it + 3) / 4 * 4 }
        val meta = frames.mapIndexed { i, f ->
            tag to "TRACK:${i + 1} TYPE:${if (i == 0) "MODE2_RAW" else "AUDIO"} SUBTYPE:NONE FRAMES:$f $pregap"
        }
        return PackedImages.chd(
            ByteArray(padded * PackedImages.CD_FRAME), PackedImages.CD_FRAME * PackedImages.FRAMES_PER_HUNK, PackedImages.CD_FRAME,
            listOf("cdzl"), metadata = meta,
        )
    }

    @Test
    fun cdChdGivesFramesTimes2352NotThePaddedLayoutAndReadsOnlyTheMetadata() {
        val frames = listOf(301, 75, 150)
        for (tag in listOf("CHT2", "CHTR")) {
            val counting = CountingSource(ByteArraySource(cdChd(frames, tag, pregap = "")))
            val result = TrackSizes.read(counting, Container.CHD, "pcenginecd", null)
            assertEquals(tag, keys(frames.map { it * 2_352L }), result.keys)
            // Header and the metadata entries only: no map, no hunk.
            assertTrue("read ${counting.bytesRead}", counting.bytesRead < 1_024)
        }
        // The padded layout (304 + 76 + 152 frames) is a different list: it must not be what is keyed.
        assertFalse(keys(listOf(304, 76, 152).map { it * 2_352L }) == TrackSizes.read(ByteArraySource(cdChd(frames)), Container.CHD, null, null).keys)
    }

    @Test
    fun gdRomChdGivesItsTracksToo() {
        val frames = listOf(302, 75, 3_000)
        val result = TrackSizes.read(ByteArraySource(cdChd(frames, "CHGD", "PAD:0 PREGAP:0 PGTYPE:MODE1 PGSUB:RW POSTGAP:0")), Container.CHD, "dreamcast", null)
        assertEquals(keys(frames.map { it * 2_352L }), result.keys)
    }

    @Test
    fun gdRomChdDropsTheFramesOfPadFromAnAudioTrack() {
        // As on a real Dreamcast CHD: track 2 is stored as 44,550 frames of which 43,874 are padding.
        val meta = listOf(
            "CHGD" to "TRACK:1 TYPE:MODE1_RAW SUBTYPE:NONE FRAMES:450 PAD:0 PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0",
            "CHGD" to "TRACK:2 TYPE:AUDIO SUBTYPE:NONE FRAMES:44550 PAD:43874 PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0",
            "CHGD" to "TRACK:3 TYPE:MODE1_RAW SUBTYPE:NONE FRAMES:1000 PAD:0 PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0",
        )
        val chd = PackedImages.chd(
            ByteArray(46_000 * PackedImages.CD_FRAME), PackedImages.CD_FRAME * PackedImages.FRAMES_PER_HUNK, PackedImages.CD_FRAME,
            listOf("cdzl"), metadata = meta,
        )
        val result = TrackSizes.read(ByteArraySource(chd), Container.CHD, "dreamcast", null)
        assertEquals(keys(listOf(450L, 676L, 1_000L).map { it * 2_352L }), result.keys)
    }

    @Test
    fun cdChdOfACookedDvdAlsoAsksFramesTimes2048() {
        // `chdman createcd` on a PS2 .iso: one cooked MODE1 track, Redump's size is frames x 2048.
        val meta = listOf("CHT2" to "TRACK:1 TYPE:MODE1 SUBTYPE:NONE FRAMES:2264080 PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0")
        val chd = PackedImages.chd(
            ByteArray(PackedImages.CD_FRAME * 4), PackedImages.CD_FRAME * PackedImages.FRAMES_PER_HUNK, PackedImages.CD_FRAME,
            listOf("cdzl"), metadata = meta,
        )
        val result = TrackSizes.read(ByteArraySource(chd), Container.CHD, "ps2", null)
        assertEquals(keys(listOf(2_264_080L * 2_352)) + keys(listOf(2_264_080L * 2_048)), result.keys)
    }

    @Test
    fun cdChdWithoutATrackListGivesNoKeyAndNoNote() {
        val chd = PackedImages.chd(ByteArray(PackedImages.CD_FRAME * 8), PackedImages.CD_FRAME * 8, PackedImages.CD_FRAME)
        val result = TrackSizes.read(ByteArraySource(chd), Container.CHD, "psx", null)
        assertEquals(emptyList<Long>(), result.keys)
        assertNull(result.note)
    }

    @Test
    fun dvdChdGivesItsLogicalSize() {
        val chd = PackedImages.chd(ByteArray(8192 * 3), 8192, 2048, listOf("zlib"))
        val result = TrackSizes.read(ByteArraySource(chd), Container.CHD, "ps2", null)
        assertEquals(keys(listOf(8192L * 3)), result.keys)
    }

    @Test
    fun isoOfACdSystemAsksBothItsSizeAndItsSizeInRawSectors() {
        val size = 2_048L * 250_000
        val result = TrackSizes.read(Sized(size), Container.ISO, "psx", null)
        // A source with no bytes reads as "no sync", i.e. cooked 2048-byte sectors.
        assertEquals(keys(listOf(2_352L * 250_000)) + keys(listOf(size)), result.keys)
        // The same image under "ps2" bigger than a CD, and a GameCube image: its size only.
        val big = Sized(2_048L * 2_000_000)
        assertEquals(keys(listOf(2_048L * 2_000_000)), TrackSizes.read(big, Container.ISO, "ps2", null).keys)
        val gc = TrackSizes.read(Sized(1_459_978_240L), Container.ISO, "gc", null)
        assertEquals(keys(listOf(1_459_978_240L)), gc.keys)
    }

    @Test
    fun aCookedPs2ImageIsADvdWhateverItsSizeAndWiiUAndXbox360AreDvdSystems() {
        // Redump's PS2 DVDs are .iso (116 under 900 MB), its CDs always bin/cue: no CD keys made up.
        val small = 2_048L * 100_000
        assertEquals(keys(listOf(small)), TrackSizes.read(Sized(small), Container.ISO, "ps2", null).keys)
        for (system in listOf("wiiu", "xbox360")) {
            assertEquals(system, keys(listOf(small)), TrackSizes.read(Sized(small), Container.ISO, system, null).keys)
        }
        // A raw PS2 image is still a CD: its size as Redump's bin.
        val bin = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 1 shl 20))
        assertEquals(keys(listOf(bin.size.toLong())), TrackSizes.read(ByteArraySource(bin), Container.BIN_RAW, "ps2", null).keys)
    }

    @Test
    fun rawBinWithoutACueGivesItsSize() {
        val bin = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 1 shl 20))
        val result = TrackSizes.read(ByteArraySource(bin), Container.BIN_RAW, "psx", null)
        assertEquals(keys(listOf(bin.size.toLong())), result.keys)
    }

    @Test
    fun csoGivesItsUncompressedSizeFromTheHeader() {
        val iso = DiscImages.pspIso(padTo = 1 shl 20)
        val counting = CountingSource(ByteArraySource(PackedImages.cso(iso)))
        val result = TrackSizes.read(counting, Container.CSO, "psp", null)
        assertEquals(keys(listOf(iso.size.toLong())), result.keys)
        assertTrue("read ${counting.bytesRead}", counting.bytesRead <= 24)
    }

    @Test
    fun theReaderStoresTheKeysButTheFixKeysStayTheCrcAndTheCode() {
        val cue = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
        val bin = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 256 * 1024), mode = 2)
        val siblings = DiscImages.Siblings(mapOf("game.bin" to bin))
        val f = FingerprintReader().read(ByteArraySource(cue.toByteArray()), "game.cue", "psx", Depth.QUICK, siblings)
        assertEquals("SLUS-99999", f.productCode)
        assertEquals(keys(listOf(bin.size.toLong())), f.trackKeys)
        // Track keys only look a title up: two games can share a layout, and the first fix key
        // is the ScreenScraper key, so the keys stay what they were without them.
        assertEquals(listOf("code:PS1:SLUS-99999"), f.fixKeys())
        assertEquals(f.copy(trackKeys = null).fixKeys(), f.fixKeys())
        val withCrc = f.copy(crc32 = 0x1234L, contentSize = 10)
        assertEquals(listOf("crc:00001234:10", "code:PS1:SLUS-99999"), withCrc.fixKeys())
    }

    @Test
    fun systemsWithoutSerialsStillGetKeysFromTheirCue() {
        val cue = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n"
        val siblings = SiblingOpener { Sized(2_352L * 12_345) }
        for (system in listOf("pcenginecd", "3do", "neogeocd")) {
            val f = FingerprintReader().read(ByteArraySource(cue.toByteArray()), "game.cue", system, Depth.QUICK, siblings)
            assertEquals(system, keys(listOf(2_352L * 12_345)), f.trackKeys)
            assertNull(system, f.productCode)
            assertEquals(system, emptyList<String>(), f.fixKeys())
        }
    }

    @Test
    fun anOldCachedDiscGetsItsKeysOnceAndOtherFilesAreLeftAlone() {
        val old = Fingerprints(size = 80, container = Container.CUE, productCode = "SLUS-99999")
        assertTrue(FingerprintReader.needsTrackKeys(old))
        val cue = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n"
        val fresh = FingerprintReader.withTrackKeys(old, ByteArraySource(cue.toByteArray()), "psx", opener(mapOf("game.bin" to 2_352L * 10)))
        assertEquals(keys(listOf(2_352L * 10)), fresh.trackKeys)
        assertFalse(FingerprintReader.needsTrackKeys(fresh))
        // A disc that gives no key is marked looked-at (empty list), so it is not opened again.
        val garbage = FingerprintReader.withTrackKeys(old, ByteArraySource(ByteArray(64) { 1 }), "psx", opener(emptyMap()))
        assertEquals(emptyList<Long>(), garbage.trackKeys)
        assertFalse(FingerprintReader.needsTrackKeys(garbage))
        // A cartridge or an archive is returned as it is.
        val rom = Fingerprints(size = 10, container = Container.NONE)
        assertFalse(FingerprintReader.needsTrackKeys(rom))
        assertEquals(rom, FingerprintReader.withTrackKeys(rom, ByteArraySource(ByteArray(10)), "snes", null))
    }

    @Test
    fun aDamagedCueOrChdNeverThrows() {
        val garbage = ByteArray(5_000) { (it * 31).toByte() }
        for (container in listOf(Container.CUE, Container.GDI, Container.M3U, Container.CHD, Container.CSO, Container.ISO)) {
            TrackSizes.read(ByteArraySource(garbage), container, "psx", opener(emptyMap()))
        }
        val header = ByteArray(24)
        header.putU64le(8, -5)
        assertEquals(emptyList<Long>(), TrackSizes.read(ByteArraySource(header), Container.CSO, "psp", null).keys)
    }

    @Test
    fun aMissingTrackFileLeavesTheKeysToAskAgainUntilItIsThere() {
        val cue = "FILE \"a.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\nFILE \"b.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n"
        val sizes = listOf(2_352L * 10, 2_352L * 4)
        val opened = ArrayList<String>()
        val missing = TrackSizes.read(ByteArraySource(cue.toByteArray()), Container.CUE, "psx", opener(mapOf("a.bin" to sizes[0]), opened))
        assertNull("incomplete, not 'looked at, none'", missing.keys)
        assertTrue(missing.note.toString(), missing.note.toString().contains("b.bin"))
        assertEquals("one open per track at most", listOf("a.bin", "b.bin"), opened)
        // Cached that way, the next naming asks again and gets the keys once the file is there.
        val old = Fingerprints(size = 80, container = Container.CUE)
        val first = FingerprintReader.withTrackKeys(old, ByteArraySource(cue.toByteArray()), "psx", opener(mapOf("a.bin" to sizes[0])))
        assertTrue(FingerprintReader.needsTrackKeys(first))
        val later = FingerprintReader.withTrackKeys(first, ByteArraySource(cue.toByteArray()), "psx", opener(mapOf("a.bin" to sizes[0], "b.bin" to sizes[1])))
        assertEquals(keys(sizes), later.trackKeys)
        assertFalse(FingerprintReader.needsTrackKeys(later))
        // An empty track file (a copy still running) and a playlist naming a missing disc are incomplete too.
        assertNull(TrackSizes.read(ByteArraySource(cue.toByteArray()), Container.CUE, "psx", opener(mapOf("a.bin" to sizes[0], "b.bin" to 0L))).keys)
        assertNull(TrackSizes.read(ByteArraySource("disc1.cue\n".toByteArray()), Container.M3U, "psx", opener(emptyMap())).keys)
        // A fresh read of such a sheet stores no keys either.
        val fresh = FingerprintReader().read(ByteArraySource(cue.toByteArray()), "game.cue", "psx", Depth.QUICK, opener(mapOf("a.bin" to sizes[0])))
        assertNull(fresh.trackKeys)
    }

    @Test
    fun aCutShortChdSettlesWithNoKeysInsteadOfAskingAgainForever() {
        val whole = cdChd(listOf(301, 75, 150))
        val header = ChdHeader.read(ByteArraySource(whole))
        // Cut right after the 124-byte header and inside the metadata: no keys, and settled (an
        // empty list, never null). Cut inside the map the metadata is whole, so the keys are real.
        for (cut in listOf(124, 124 + 20)) {
            val chd = whole.copyOf(cut)
            val fresh = FingerprintReader().read(ByteArraySource(chd), "game.chd", "psx", Depth.QUICK)
            assertEquals("cut at $cut: ${fresh.notes}", emptyList<Long>(), fresh.trackKeys)
            assertNull(fresh.productCode)
            val again = FingerprintReader.withTrackKeys(Fingerprints(size = chd.size.toLong(), container = Container.CHD), ByteArraySource(chd), "psx", null)
            assertEquals("cut at $cut", emptyList<Long>(), again.trackKeys)
            assertFalse(FingerprintReader.needsTrackKeys(again))
        }
        for (cut in listOf(header.mapOffset.toInt() + 4, header.mapOffset.toInt() + 12)) {
            val chd = whole.copyOf(cut)
            val fresh = FingerprintReader().read(ByteArraySource(chd), "game.chd", "psx", Depth.QUICK)
            assertEquals("cut at $cut: ${fresh.notes}", keys(listOf(301, 75, 150).map { it * 2_352L }), fresh.trackKeys)
            assertNull(fresh.productCode)
            assertFalse(FingerprintReader.needsTrackKeys(fresh))
        }
    }

    @Test
    fun zstdAndCdzsChdsGiveTheSameKeysAsTheirCdzlTwinFromMetadataOnly() {
        val frames = listOf(301, 75, 150)
        val twin = TrackSizes.read(ByteArraySource(cdChd(frames)), Container.CHD, "psx", null).keys
        assertEquals(keys(frames.map { it * 2_352L }), twin)
        val padded = frames.sumOf { (it + 3) / 4 * 4 }
        val meta = frames.mapIndexed { i, f ->
            "CHT2" to "TRACK:${i + 1} TYPE:${if (i == 0) "MODE2_RAW" else "AUDIO"} SUBTYPE:NONE FRAMES:$f PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0"
        }
        for (codec in listOf("cdzs", "zstd")) {
            val chd = PackedImages.chd(
                ByteArray(padded * PackedImages.CD_FRAME), PackedImages.CD_FRAME * PackedImages.FRAMES_PER_HUNK, PackedImages.CD_FRAME,
                listOf(codec), metadata = meta,
            )
            val counting = CountingSource(ByteArraySource(chd))
            val result = TrackSizes.read(counting, Container.CHD, "psx", null)
            assertEquals(codec, twin, result.keys)
            assertTrue("$codec read ${counting.bytesRead}", counting.bytesRead < 1_024)
        }
    }

    @Test
    fun aReadErrorOnASiblingTrackLosesOnlyTheTrackKeysNeverTheSerial() {
        val cue = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\nFILE \"game2.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n"
        val bin = DiscImages.toRaw(DiscImages.ps1Iso(padTo = 256 * 1024), mode = 2)
        val data = DiscImages.Siblings(mapOf("game.bin" to bin))
        val failing = SiblingOpener { name -> if (name == "game2.bin") throw java.io.IOException("card read failed") else data.open(name) }
        val f = FingerprintReader().read(ByteArraySource(cue.toByteArray()), "game.cue", "psx", Depth.QUICK, failing)
        assertEquals("SLUS-99999", f.productCode)
        assertEquals(listOf("code:PS1:SLUS-99999"), f.fixKeys())
        assertNull("asked again at the next naming", f.trackKeys)
        assertTrue(f.notes.toString(), f.notes.any { it.startsWith("track sizes:") })
    }
}
