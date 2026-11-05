package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadImage
import com.plumemu.achievements.hash.RaCannotHashReason.Empty
import com.plumemu.achievements.hash.RaCannotHashReason.MissingFile
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import com.plumemu.achievements.hash.RaCannotHashReason.NotYetSupported
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException

/**
 * Disc vectors from rcheevos `test_rc_hash_disc.c` (they build synthetic images and name the MD5
 * each must give), run through every container this module reads, plus wrong paths.
 */
class DiscHashesTest {
    private class Vector(val name: String, val console: Int, val image: ByteArray, val md5: String)

    private val vectors: List<Vector> by lazy {
        listOf(
            Vector("PlayStation SLUS_007.45", RaConsoles.PLAYSTATION, psxBin("SLUS_007.45", 0x07D800), "db433fb038cde4fb15c144e8c7dea6e3"),
            Vector("PlayStation exe in a sub-folder", RaConsoles.PLAYSTATION, psxBin("bin\\SCES_012.37", 0x07D800), "674018e23a4052113665dfb264e9c2fc"),
            Vector("PlayStation BOOT path with an extra slash", RaConsoles.PLAYSTATION, psxBin("\\SLUS_007.45", 0x07D800), "db433fb038cde4fb15c144e8c7dea6e3"),
            Vector("PlayStation without SYSTEM.CNF (PSX.EXE)", RaConsoles.PLAYSTATION, psxHomebrew(), "e494c79a7315be0dc3e8571c45df162c"),
            Vector("PlayStation 2", RaConsoles.PLAYSTATION_2, ps2Bin("SLUS_200.64", 0x07D800), "01a517e4ad72c6c2654d1b839be7579d"),
            Vector("PSP", RaConsoles.PSP, pspBin(), "27ec2f9b7238b2ef29af31ddd254f201"),
            Vector("Sega CD", RaConsoles.SEGA_CD, segaBin("SEGADISCSYSTEM  "), "574498e1453cb8934df60c4ab906e783"),
            Vector("Saturn", RaConsoles.SATURN, segaBin("SEGA SEGASATURN "), "4cd9c8e41cd8d137be15bbe6a93ae1d8"),
        )
    }

    private fun psxHomebrew(): ByteArray {
        val size = 0x12000
        val image = isoBin((size + 2047) / 2048 + 20, "HOMEBREW")
        markPsxExe(image, isoAddFile(image, "PSX.EXE", null, size), size)
        return image
    }

    /** Turns the stored bytes of block [n] of a `.cso` (index shift 0) into noise. */
    private fun damageBlock(cso: ByteArray, n: Int) {
        val at = 24 + 4 * n
        val start = ((cso[at].toInt() and 0xFF) or ((cso[at + 1].toInt() and 0xFF) shl 8) or ((cso[at + 2].toInt() and 0xFF) shl 16))
        for (i in 0 until 20) cso[start + i] = (0xF7 - i).toByte()
    }

    private fun hash(console: Int, source: RaRomSource) = RaHasher.hashBlocking(console, source)
    private fun reason(result: RaHashResult) = (result as? RaHashResult.CannotHash)?.reason ?: fail("not a CannotHash: $result").let { null }

    // --- every vector through every container

    @Test
    fun everyVectorGivesItsHashThroughEveryContainer() {
        for (vector in vectors) {
            for (case in discCases(vector.image)) {
                assertEquals("${vector.name} as ${case.name}", RaHashResult.Hashed(vector.md5), hash(vector.console, case.source))
            }
        }
    }

    @Test
    fun aPlaylistHashesTheFirstDiscItLists() {
        for (vector in vectors) {
            val raw = rawSectors(vector.image, 2)
            val files = mapOf(
                "game.iso" to vector.image,
                "game.cue" to "FILE \"game.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n".toByteArray(),
                "game.bin" to raw,
                "game.cso" to csoOf(vector.image),
                "second-disc.iso" to genericFile(4096),
            )
            for (first in listOf("game.iso", "game.cue", "game.cso", "game.iso")) {
                val playlist = "#EXTM3U\r\n\r\n   \r\n$first\r\nsecond-disc.iso\r\n"
                val source = Folder(files + ("list.m3u" to playlist.toByteArray())).source("list.m3u")
                assertEquals("${vector.name} via $first", RaHashResult.Hashed(vector.md5), hash(vector.console, source))
            }
        }
    }

    @Test
    fun namesThatLeaveTheFolderAreRefusedBeforeAnythingIsOpened() {
        val image = vectors[0].image
        // The folder holds a file under each name, so only the refusal can make these fail.
        for (name in listOf("/storage/emulated/0/Games/game.iso", "../game.iso", "Discs/../game.iso", "C:/Games/game.iso", "..\\game.iso")) {
            val files = mapOf("list.m3u" to "$name\n".toByteArray(), name.replace('\\', '/') to image)
            assertEquals("m3u $name", MissingFile, reason(hash(RaConsoles.PLAYSTATION, Folder(files).source("list.m3u"))))
            val cue = "FILE \"$name\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n".toByteArray()
            val cueFiles = mapOf("g.cue" to cue, name.replace('\\', '/') to rawSectors(image, 1))
            assertEquals("cue $name", MissingFile, reason(hash(RaConsoles.PLAYSTATION, Folder(cueFiles).source("g.cue"))))
        }
    }

    @Test
    fun aCueMayNameItsBinWithBackslashes() {
        val vector = vectors[0]
        val cue = "FILE \"Tracks\\game.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n".toByteArray()
        val source = Folder("g.cue" to cue, "Tracks/game.bin" to rawSectors(vector.image, 1)).source("g.cue")
        assertEquals(RaHashResult.Hashed(vector.md5), hash(vector.console, source))
    }

    // --- the C vectors that must not hash

    @Test
    fun aPlayStation2DiscIsNotAPlayStationDiscAndTheOtherWayRound() {
        val ps1 = vectors[0].image
        val ps2 = vectors[4].image
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION_2, MemorySource("game.iso", ps1))))
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.iso", ps2))))
    }

    @Test
    fun aPspVideoDiscHasNoGameToHash() {
        val image = isoBin(160, "TEST")
        isoAddFile(image, "PSP_GAME\\SYSDIR\\UPDATE\\EBOOT.BIN", genericFile(273470))
        isoAddFile(image, "UMD_VIDEO\\PARAM.SFO", genericFile(690))
        assertEquals(NotARom, reason(hash(RaConsoles.PSP, MemorySource("game.iso", image))))
    }

    @Test
    fun aPspHomebrewPbpIsHashedWhole() {
        val pbp = genericFile(3532124)
        assertEquals(RaHashResult.Hashed("fcde8760893b09e508e5f4fe642eb132"), hash(RaConsoles.PSP, MemorySource("eboot.pbp", pbp)))
    }

    @Test
    fun segaDiscWithoutItsMarkerIsRefused() {
        val image = ByteArray(2048).also { fillImage(it, 0, 512) }
        for (console in listOf(RaConsoles.SEGA_CD, RaConsoles.SATURN)) {
            assertEquals(NotARom, reason(hash(console, MemorySource("game.iso", image))))
        }
    }

    // --- wrong paths

    @Test
    fun aCueNamingAMissingBinSaysSo() {
        val cue = "FILE \"gone.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n".toByteArray()
        val result = hash(RaConsoles.PLAYSTATION, Folder("game.cue" to cue).source("game.cue")) as RaHashResult.CannotHash
        assertEquals(MissingFile, result.reason)
        assertTrue(result.detail!!, result.detail!!.contains("gone.bin"))
    }

    @Test
    fun aCueWhoseFirstTrackIsThereHashesEvenIfALaterBinIsMissing() {
        val vector = vectors[0]
        val cue = "FILE \"a.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\nFILE \"gone.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n"
        val source = Folder("game.cue" to cue.toByteArray(), "a.bin" to rawSectors(vector.image, 2)).source("game.cue")
        assertEquals(RaHashResult.Hashed(vector.md5), hash(vector.console, source))
    }

    @Test
    fun aCueWithoutTheTrackAndAnEmptyCueAreNotDiscs() {
        val noTrack = Folder("game.cue" to "REM nothing here\n".toByteArray()).source("game.cue")
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION, noTrack)))
        val empty = Folder("game.cue" to ByteArray(0)).source("game.cue")
        assertEquals(Empty, reason(hash(RaConsoles.PLAYSTATION, empty)))
    }

    @Test
    fun aPlaylistNamingAMissingDiscOrNothingSaysSo() {
        assertEquals(MissingFile, reason(hash(RaConsoles.PLAYSTATION, Folder("list.m3u" to "gone.cue\n".toByteArray()).source("list.m3u"))))
        assertEquals(MissingFile, reason(hash(RaConsoles.PLAYSTATION, Folder("list.m3u" to "#only a comment\n\n".toByteArray()).source("list.m3u"))))
    }

    @Test
    fun anImageCutBeforeTheDirectoryOrTheExecutableCannotBeHashed() {
        val image = vectors[0].image
        // Cut inside the system area: no volume descriptor.
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.iso", image.copyOf(10 * 2048)))))
        // Volume and directory present, executable not: SYSTEM.CNF is found, the boot file's first sector is not.
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.iso", image.copyOf(19 * 2048)))))
        // The same cut on a PSP disc: PARAM.SFO is there, EBOOT.BIN is not.
        assertEquals(NotARom, reason(hash(RaConsoles.PSP, MemorySource("game.iso", pspBin().copyOf(21 * 2048)))))
    }

    @Test
    fun anImageCutInsideTheExecutableHashesWhatIsThereLikeRcHash() {
        val image = vectors[0].image
        val cut = hash(RaConsoles.PLAYSTATION, MemorySource("game.iso", image.copyOf(image.size - 20 * 2048)))
        assertTrue(cut.toString(), cut is RaHashResult.Hashed)
        assertNotEquals(RaHashResult.Hashed(vectors[0].md5), cut)
    }

    @Test
    fun aPlayStation2DiscWithoutSystemCnfCannotBeHashed() {
        val image = isoBin(40, "TEST")
        isoAddFile(image, "SLUS_200.64", null, 4096)
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION_2, MemorySource("game.iso", image))))
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.iso", image))))
    }

    @Test
    fun aFileThatIsNotADiscImageIsRefused() {
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION, MemorySource("junk.iso", genericFile(5000)))))
        assertEquals(NotARom, reason(hash(RaConsoles.PLAYSTATION, MemorySource("junk.bin", genericFile(2048 * 40)))))
        assertEquals(Empty, reason(hash(RaConsoles.PLAYSTATION, MemorySource("empty.iso", ByteArray(0)))))
    }

    @Test
    fun aReadFailureIsReportedNotSwallowed() {
        assertTrue(hash(RaConsoles.PLAYSTATION, FailingSource("game.iso")) is RaHashResult.Unreadable)
        assertTrue(hash(RaConsoles.PLAYSTATION, BreaksHalfwaySource("game.iso")) !is RaHashResult.Hashed)
    }

    @Test
    fun cancellationStopsAtOnce() {
        try {
            RaHasher.hashBlocking(RaConsoles.PLAYSTATION, MemorySource("game.iso", vectors[0].image)) { throw CancellationException("stop") }
            fail("expected the cancellation to pass through")
        } catch (expected: CancellationException) {
            assertEquals("stop", expected.message)
        }
    }

    // --- containers that need a decoder

    @Test
    fun packedDiscFormatsNeedADecoder() {
        for (name in listOf("game.rvz", "game.wia", "game.wbfs", "game.gcz", "game.pbp", "game.ecm")) {
            assertEquals(name, NeedsDecoder, reason(hash(RaConsoles.PLAYSTATION, MemorySource(name, genericFile(5000)))))
        }
        assertEquals(NeedsDecoder, reason(hash(RaConsoles.PSP, MemorySource("game.rvz", genericFile(5000)))))
        val rvzInPlaylist = Folder("list.m3u" to "game.rvz\n".toByteArray(), "game.rvz" to genericFile(5000)).source("list.m3u")
        assertEquals(NeedsDecoder, reason(hash(RaConsoles.PLAYSTATION, rvzInPlaylist)))
    }

    @Test
    fun aZsoAndACsoV2AreHashedLikeTheIso() {
        val psp = vectors.first { it.console == RaConsoles.PSP }
        val iso = hash(RaConsoles.PSP, MemorySource("game.iso", psp.image))
        assertEquals(RaHashResult.Hashed(psp.md5), iso)
        assertEquals(iso, hash(RaConsoles.PSP, MemorySource("game.zso", lz4ImageOf(psp.image, zso = true))))
        assertEquals(iso, hash(RaConsoles.PSP, MemorySource("game.cso", lz4ImageOf(psp.image, zso = false))))
        // The format is told by the header, not by the extension.
        assertEquals(iso, hash(RaConsoles.PSP, MemorySource("game.cso", lz4ImageOf(psp.image, zso = true))))
    }

    @Test
    fun aCsoNewerThanV2NeedsADecoder() {
        val cso = csoOf(vectors[0].image, version = 3)
        assertEquals(NeedsDecoder, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso))))
    }

    @Test
    fun aZsoOrCsoV2WithADamagedOrCutBlockIsBadImage() {
        val psp = vectors.first { it.console == RaConsoles.PSP }.image
        for (zso in listOf(true, false)) {
            val name = if (zso) "game.zso" else "game.cso"
            val good = lz4ImageOf(psp, zso)
            // Noise over every LZ4 block (bit 31 clear in a ZSO, set in v2): a bad sequence, never a wrong hash.
            val damaged = good.copyOf()
            val blocks = (psp.size + 2047) / 2048
            fun word(n: Int) = (0 until 4).sumOf { (good[24 + 4 * n + it].toLong() and 0xFF) shl (8 * it) }
            for (n in 0 until blocks) {
                val start = (word(n) and 0x7FFFFFFFL).toInt()
                val span = ((word(n + 1) and 0x7FFFFFFFL) - start).toInt()
                // v2: a full-size block is raw even with bit 31 set.
                val lz4 = if (zso) word(n) and 0x80000000L == 0L else word(n) and 0x80000000L != 0L && span < 2048
                if (lz4) for (i in 0 until minOf(40, span)) damaged[start + i] = (0xF7 - i).toByte()
            }
            assertEquals(name, BadImage, reason(hash(RaConsoles.PSP, MemorySource(name, damaged))))
            assertEquals(name, BadImage, reason(hash(RaConsoles.PSP, MemorySource(name, good.copyOf(good.size / 2)))))
        }
    }

    @Test
    fun anArchiveIsNeedsDecoderNotNotARom() {
        for (name in listOf("game.7z", "game.rar", "game.gz")) {
            assertEquals(name, NeedsDecoder, reason(hash(RaConsoles.PLAYSTATION, MemorySource(name, genericFile(5000)))))
        }
    }

    @Test
    fun cancellationInTheMiddleOfAScanStops() {
        var calls = 0
        try {
            RaHasher.hashBlocking(RaConsoles.PLAYSTATION, MemorySource("game.iso", vectors[0].image)) { if (++calls > 3) throw CancellationException("stop") }
            fail("expected the cancellation to pass through")
        } catch (expected: CancellationException) {
            assertTrue(calls > 3)
        }
    }

    // --- damaged .cso

    @Test
    fun aCsoWithADamagedBlockIsBadImageNotAWrongHash() {
        val cso = csoOf(vectors[0].image)
        damageBlock(cso, 16)
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso))))
    }

    @Test
    fun aCsoCutShortIsBadImage() {
        val cso = csoOf(vectors[0].image)
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso.copyOf(cso.size / 2)))))
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso.copyOf(12)))))
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso.copyOf(200)))))
    }

    @Test
    fun aCsoWithoutTheMagicOrWithABadHeaderIsBadImage() {
        val cso = csoOf(vectors[0].image)
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", genericFile(4096)))))
        val zeroBlock = cso.copyOf().also { for (i in 16 until 20) it[i] = 0 }
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", zeroBlock))))
    }

    @Test
    fun aCsoIndexPointingPastTheFileIsBadImage() {
        val cso = csoOf(vectors[0].image)
        // Block 16 (the volume descriptor) says its data is far beyond the end of the file.
        cso[24 + 4 * 16 + 3] = 0x3F
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso))))
    }

    @Test
    fun aCsoWithAnUnusableBlockSizeIsBadImage() {
        for (blockSize in listOf(0, 1, 512, 3000, 1 shl 21)) {
            val cso = csoOf(vectors[0].image)
            for (i in 0 until 4) cso[16 + i] = ((blockSize shr (8 * i)) and 0xFF).toByte()
            assertEquals("block size $blockSize", BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso))))
        }
    }

    @Test
    fun aCsoIndexWithAnImpossibleBlockSizeIsBadImageNotACrash() {
        val at = 24 + 4 * 16
        // Block 16 starts at 32, so its stored size would be almost the whole file: far more than a block.
        val tooBig = csoOf(vectors[0].image).also { it[at] = 32; it[at + 1] = 0; it[at + 2] = 0; it[at + 3] = 0 }
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", tooBig))))
        // Block 17 starts before block 16: a negative size.
        val negative = csoOf(vectors[0].image).also { for (i in 0 until 4) it[at + 4 + i] = 0 }
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", negative))))
    }

    @Test
    fun aCsoWhoseIndexWouldBeHugeIsBadImage() {
        // 2048-byte blocks, a size that needs a 64 MiB index, in a file too small to hold it.
        val cso = csoOf(vectors[0].image)
        val total = 2048L * 16_000_000
        for (i in 0 until 8) cso[8 + i] = ((total shr (8 * i)) and 0xFF).toByte()
        assertEquals(BadImage, reason(hash(RaConsoles.PLAYSTATION, MemorySource("game.cso", cso))))
    }

    // --- tracks of a cue, for the disc systems that come next

    @Test
    fun cueTracksAreFoundByNumberAndBySpecialRequest() {
        val data = rawSectors(vectors[0].image, 2)
        val audio = audioSectors(40)
        val cue = "FILE \"a.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n" +
            "FILE \"b.bin\" BINARY\n  TRACK 02 MODE2/2352\n    INDEX 01 00:00:00\n"
        val folder = Folder("g.cue" to cue.toByteArray(), "a.bin" to audio, "b.bin" to data)
        val source = folder.source("g.cue")
        val buffer = ByteArray(16)
        DiscReader.openTrack(source, 1, {}).use { track ->
            assertEquals(16, track.readSector(0, buffer, 16))
            assertEquals(audio.take(16), buffer.toList())
        }
        for (request in listOf(2, DiscTracks.FIRST_DATA, DiscTracks.LAST, DiscTracks.LARGEST)) {
            DiscReader.openTrack(source, request, {}).use { track ->
                assertEquals("request $request", 2048, track.readSector(16, ByteArray(2048), 2048))
                assertEquals("request $request", RaHashResult.Hashed(vectors[0].md5), hashPlayStation(track) {})
            }
        }
        try {
            DiscReader.openTrack(source, 3, {}).close()
            fail("track 3 does not exist")
        } catch (expected: DiscFormatException) {
            assertEquals(NotARom, expected.reason)
        }
    }

    @Test
    fun theSecondSessionStartsAtItsRemMarker() {
        val data = rawSectors(vectors[0].image, 2)
        val cue = "REM SESSION 01\nFILE \"a.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n" +
            "REM SESSION 02\nFILE \"b.bin\" BINARY\n  TRACK 02 MODE2/2352\n    INDEX 01 00:00:00\n"
        val source = Folder("g.cue" to cue.toByteArray(), "a.bin" to audioSectors(10), "b.bin" to data).source("g.cue")
        DiscReader.openTrack(source, DiscTracks.FIRST_OF_SECOND_SESSION, {}).use { track ->
            assertEquals(RaHashResult.Hashed(vectors[0].md5), hashPlayStation(track) {})
        }
    }

    @Test
    fun consolesOnlyNeedingImplementedRulesNowRefuseRandomBytesAsNotARom() {
        for (console in listOf(RaConsoles.GAMECUBE, RaConsoles.THREE_DO, RaConsoles.DREAMCAST, RaConsoles.PC_ENGINE_CD, RaConsoles.WII)) {
            assertEquals("console $console", NotARom, reason(hash(console, MemorySource("a.iso", genericFile(100)))))
        }
        for (console in listOf(RaConsoles.NINTENDO_3DS, RaConsoles.MS_DOS)) {
            assertEquals("console $console", NotYetSupported, reason(hash(console, MemorySource("a.iso", genericFile(100)))))
        }
    }
}
