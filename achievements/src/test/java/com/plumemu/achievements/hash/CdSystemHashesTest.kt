package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.MissingFile
import com.plumemu.achievements.hash.RaCannotHashReason.NotARom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * Vectors from rcheevos `test_rc_hash_disc.c` for 3DO, FM Towns, Neo Geo CD, PC Engine CD, PC-FX and
 * PS3 (each through every container a CD image can come in), plus wrong paths.
 */
class CdSystemHashesTest {
    private class Vector(val name: String, val console: Int, val image: ByteArray, val md5: String, val isoLayout: Boolean = true)

    private fun md5Of(vararg parts: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        parts.forEach { md.update(it) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hash(console: Int, source: RaRomSource) = RaHasher.hashBlocking(console, source)

    private fun reason(result: RaHashResult): RaCannotHashReason? =
        (result as? RaHashResult.CannotHash)?.reason ?: fail("not a CannotHash: $result").let { null }

    private fun fmTownsIso(): ByteArray {
        val image = isoBin(256, "TEST")
        isoAddFile(image, "AUTOEXEC.BAT", genericFile(177))
        isoAddFile(image, "MENU.EXP", genericFile(240602))
        isoAddFile(image, "GAME\\GAME.EXP", genericFile(203075))
        return image
    }

    private fun neoGeoIso(ipl: String, vararg programs: Pair<String, Int>): ByteArray {
        val image = isoBin(160, "TEST")
        isoAddFile(image, "IPL.TXT", ipl.toByteArray(Charsets.ISO_8859_1))
        for ((name, size) in programs) isoAddFile(image, name, genericFile(size))
        return image
    }

    private fun ps3Iso(): ByteArray {
        val image = isoBin(160, "TEST")
        isoAddFile(image, "PS3_GAME\\PARAM.SFO", genericFile(690))
        isoAddFile(image, "PS3_GAME\\USRDIR\\EBOOT.BIN", genericFile(273470))
        return image
    }

    private val vectors: List<Vector> by lazy {
        val ipl = "FIXA.FIX,0,0\r\nPROG.PRG,0,0\r\nSOUND.PCM,0,0\r\n\u001a"
        listOf(
            Vector("3DO, 123456-byte program", RaConsoles.THREE_DO, threeDoBin(1, 123456), "9b2266b8f5abed9c12cce780750e88d6", false),
            Vector("3DO, small program", RaConsoles.THREE_DO, threeDoBin(1, 9347), "257d1d19365a864266b236214dbea29c", false),
            Vector(
                "3DO, launchme in lower case", RaConsoles.THREE_DO,
                threeDoBin(1, 6543).withText(2048 + 0x14 + 0x48 + 0x20, "launchme"), "59622882e3261237e8a1e396825ae4f5", false,
            ),
            Vector("3DO, root directory over three sectors", RaConsoles.THREE_DO, threeDoBin(3, 6543), "8979e876ae502e0f79218f7ff7bd8c2a", false),
            Vector("Neo Geo CD", RaConsoles.NEO_GEO_CD, neoGeoIso(ipl, "PROG.PRG" to 273470), "96f35b20c6cf902286da45e81a50b2a3"),
            Vector(
                "Neo Geo CD, two PRG files", RaConsoles.NEO_GEO_CD,
                neoGeoIso(
                    "FIXA.FIX,0,0\r\nPROG1.PRG,0,0\r\nSOUND.PCM,0,0\r\nPROG2.PRG,0,44000\r\n\u001a",
                    "PROG1.PRG" to 273470, "PROG2.PRG" to 13768,
                ),
                "d62df483c4786d3c63f27b6c5f17eeca",
            ),
            Vector(
                "Neo Geo CD, IPL.TXT in lower case", RaConsoles.NEO_GEO_CD,
                neoGeoIso(ipl.lowercase(), "PROG.PRG" to 273470), "96f35b20c6cf902286da45e81a50b2a3",
            ),
            Vector("PC Engine CD", RaConsoles.PC_ENGINE_CD, pceCdBin(72), "6565819195a49323e080e7539b54f251", false),
            Vector("PC-FX", RaConsoles.PCFX, pcFxBin(72), "0a03af66559b8529c50c4e7788379598", false),
            Vector("PS3", RaConsoles.PLAYSTATION_3, ps3Iso(), "27ec2f9b7238b2ef29af31ddd254f201"),
        )
    }

    @Test
    fun everyVectorGivesItsHashThroughEveryContainer() {
        for (vector in vectors) {
            for (case in discCases(vector.image, isoLayout = vector.isoLayout)) {
                // PS3 reads a disc only from an .iso; any other name is a flat executable.
                if (vector.console == RaConsoles.PLAYSTATION_3 && !case.source.fileName.endsWith(".iso")) continue
                assertEquals("${vector.name} as ${case.name}", RaHashResult.Hashed(vector.md5), hash(vector.console, case.source))
            }
        }
    }

    @Test
    fun fmTownsHashesItsProgramFilesInPathOrderThroughEveryContainer() {
        val md5 = "9f1cfd0565d4c5278b34f9fc3be60d5d"
        // A bare .bin under 2 MiB is a floppy image for this system, so it is not one of the cases.
        for (case in discCases(fmTownsIso()).filter { it.name != "bin, cooked" }) {
            assertEquals(case.name, RaHashResult.Hashed(md5), hash(RaConsoles.FM_TOWNS, case.source))
        }
    }

    @Test
    fun fmTownsBootSectorDiscIsHashedWhenThereAreNoProgramFiles() {
        val image = isoBin(1024, "TEST").withText(0, "IPL4")
        for (case in discCases(image).filter { it.name != "bin, cooked" }) {
            assertEquals(case.name, RaHashResult.Hashed(md5Of(image.copyOf(1024 * 1024))), hash(RaConsoles.FM_TOWNS, case.source))
        }
    }

    @Test
    fun fmTownsFloppyImagesAreHashedWhole() {
        val floppy = genericFile(1_261_568)
        for (name in listOf("game.hdm", "game.d88", "game.bin", "GAME.HDM")) {
            assertEquals(name, RaHashResult.Hashed(md5Of(floppy)), hash(RaConsoles.FM_TOWNS, MemorySource(name, floppy)))
        }
        val disc = fmTownsIso()
        assertEquals(RaHashResult.Hashed(md5Of(disc)), hash(RaConsoles.FM_TOWNS, MemorySource("game.hdm", disc)))
    }

    @Test
    fun fmTownsDiscWithoutProgramsOrBootSectorIsRefused() {
        assertEquals(NotARom, reason(hash(RaConsoles.FM_TOWNS, MemorySource("game.iso", isoBin(64, "TEST")))))
        val onlyData = isoBin(64, "TEST").also { isoAddFile(it, "README.TXT", genericFile(100)) }
        assertEquals(NotARom, reason(hash(RaConsoles.FM_TOWNS, MemorySource("game.iso", onlyData))))
    }

    @Test
    fun fmTownsOnlyTakesTheNamesRcHashTakes() {
        val image = isoBin(64, "TEST")
        isoAddFile(image, "SHORT.EX", genericFile(100)) // no three-letter extension
        isoAddFile(image, "A.EXE", genericFile(100)) // name too short
        isoAddFile(image, "TOOL.EXE", genericFile(300))
        isoAddFile(image, "SUB\\RUN.exp", genericFile(200))
        val expected = md5Of(genericFile(200), genericFile(300)) // sorted by path: SUB/RUN.EXP before TOOL.EXE
        assertEquals(RaHashResult.Hashed(expected), hash(RaConsoles.FM_TOWNS, MemorySource("game.iso", image)))
    }

    // --- wrong paths

    @Test
    fun a3doDiscWithAnotherVolumeHeaderIsRefused() {
        val image = threeDoBin(1, 12).withPatch(3, 0x34)
        assertEquals(NotARom, reason(hash(RaConsoles.THREE_DO, MemorySource("game.bin", image))))
    }

    @Test
    fun a3doDiscWithoutLaunchMeIsRefused() {
        val image = threeDoBin(1, 6543).withText(2048 + 0x14 + 0x48 + 0x20, "filename")
        assertEquals(NotARom, reason(hash(RaConsoles.THREE_DO, MemorySource("game.bin", image))))
    }

    @Test
    fun a3doDirectoryThatPointsBackToItselfEndsInsteadOfLooping() {
        // Sector 1 names itself as the next directory block and holds no LaunchMe.
        val image = threeDoBin(1, 6543).withText(2048 + 0x14 + 0x48 + 0x20, "filename").withPatch(2048 + 2, 0, 0)
        assertEquals(NotARom, reason(hash(RaConsoles.THREE_DO, MemorySource("game.bin", image))))
    }

    @Test
    fun pcEngineAndPcFxDiscsWithADamagedHeaderAreRefused() {
        assertEquals(NotARom, reason(hash(RaConsoles.PC_ENGINE_CD, MemorySource("game.iso", pceCdBin(72).withPatch(2048 + 0x24, 0x34).withPatch(2048 + 32, 0x34)))))
        assertEquals(NotARom, reason(hash(RaConsoles.PCFX, MemorySource("game.iso", pcFxBin(72).withPatch(12, 0x34)))))
    }

    @Test
    fun aPcEngineDiscCutInsideItsProgramIsRefused() {
        val image = pceCdBin(72)
        assertEquals(NotARom, reason(hash(RaConsoles.PC_ENGINE_CD, MemorySource("game.iso", image.copyOf(40 * 2048)))))
        assertEquals(NotARom, reason(hash(RaConsoles.PCFX, MemorySource("game.iso", pcFxBin(72).copyOf(40 * 2048)))))
    }

    @Test
    fun gameExpressDiscHashesItsBootBin() {
        val boot = genericFile(5000)
        val image = isoBin(64, "TEST").also { isoAddFile(it, "BOOT.BIN", boot) }
        assertEquals(RaHashResult.Hashed(md5Of(boot)), hash(RaConsoles.PC_ENGINE_CD, MemorySource("game.iso", image)))
        assertEquals(NotARom, reason(hash(RaConsoles.PC_ENGINE_CD, MemorySource("game.iso", isoBin(64, "TEST")))))
    }

    @Test
    fun pcFxDiscLaidOutAsAPcEngineCdIsHashedAsThatInTrackTwo() {
        val image = pceCdBin(72)
        val cue = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE1/2048\n    INDEX 01 00:00:00\n" +
            "FILE \"game2.bin\" BINARY\n  TRACK 02 MODE1/2048\n    INDEX 01 00:00:00\n"
        val source = Folder("game.cue" to cue.toByteArray(), "game.bin" to image, "game2.bin" to image).source("game.cue")
        assertEquals(RaHashResult.Hashed("6565819195a49323e080e7539b54f251"), hash(RaConsoles.PCFX, source))
    }

    @Test
    fun neoGeoDiscWithoutIplTxtOrWithAMissingProgramIsRefused() {
        assertEquals(NotARom, reason(hash(RaConsoles.NEO_GEO_CD, MemorySource("game.iso", isoBin(64, "TEST")))))
        val noProgram = neoGeoIso("FIXA.FIX,0,0\r\nPROG.PRG,0,0\r\n")
        assertEquals(NotARom, reason(hash(RaConsoles.NEO_GEO_CD, MemorySource("game.iso", noProgram))))
    }

    @Test
    fun anImageOfThePlayStationKindIsNotA3doOrNeoGeoDisc() {
        val image = psxBin("SLUS_007.45", 0x07D800)
        for (console in listOf(RaConsoles.THREE_DO, RaConsoles.NEO_GEO_CD, RaConsoles.PC_ENGINE_CD, RaConsoles.PCFX, RaConsoles.DREAMCAST, RaConsoles.ATARI_JAGUAR_CD)) {
            assertEquals("console $console", NotARom, reason(hash(console, MemorySource("game.iso", image))))
        }
    }

    // --- PS3 beside the disc: the extracted game

    @Test
    fun anExtractedPs3GameIsHashedFromEbootAndParamSfoBesideIt() {
        val sfo = genericFile(690)
        val eboot = genericFile(273470)
        val expected = RaHashResult.Hashed("27ec2f9b7238b2ef29af31ddd254f201")
        assertEquals(expected, hash(RaConsoles.PLAYSTATION_3, Folder("EBOOT.BIN" to eboot, "PARAM.SFO" to sfo).source("EBOOT.BIN")))
        assertEquals(RaHashResult.Hashed(md5Of(sfo, eboot)), hash(RaConsoles.PLAYSTATION_3, Folder("EBOOT.BIN" to eboot, "PARAM.SFO" to sfo).source("EBOOT.BIN")))
        // The retail layout keeps PARAM.SFO a folder up, where a lookup beside the file cannot go.
        assertEquals(MissingFile, reason(hash(RaConsoles.PLAYSTATION_3, Folder("USRDIR/EBOOT.BIN" to eboot).source("USRDIR/EBOOT.BIN"))))
    }

    @Test
    fun aPs3PlaylistHashesItsFirstDisc() {
        val folder = Folder("game.iso" to ps3Iso(), "game.m3u" to "game.iso".toByteArray())
        assertEquals(RaHashResult.Hashed("27ec2f9b7238b2ef29af31ddd254f201"), hash(RaConsoles.PLAYSTATION_3, folder.source("game.m3u")))
    }

    // --- Atari Jaguar CD

    private fun jaguarCue(trackBins: Boolean = true): ByteArray = (
        "REM SESSION 01\nFILE \"track01.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n" +
            "REM SESSION 02\nFILE \"track02.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n" +
            "FILE \"track03.bin\" BINARY\n  TRACK 03 AUDIO\n    INDEX 01 00:00:00\n"
        ).toByteArray().takeIf { trackBins } ?: ByteArray(0)

    private fun jaguarHash(cue: ByteArray, files: Map<String, ByteArray>, homebrewBootHash: String = JAGUAR_HOMEBREW_BOOT_HASH): RaHashResult =
        try {
            hashJaguarCd(Folder(files + ("game.cue" to cue)).source("game.cue"), {}, homebrewBootHash)
        } catch (e: DiscFormatException) {
            RaHashResult.CannotHash(e.reason, e.message)
        }

    @Test
    fun jaguarCdBootCodeIsFoundInTheFirstTrackOfTheSecondSession() {
        for (swapped in listOf(false, true)) {
            val result = jaguarHash(jaguarCue(), mapOf("track02.bin" to jaguarCdBin(2, 60024, swapped)))
            assertEquals("swapped=$swapped", RaHashResult.Hashed("c324d95dc5831c2d5c470eefb18c346b"), result)
        }
    }

    @Test
    fun jaguarCdSecondSessionMayStartAtTrackThree() {
        val cue = "REM SESSION 01\nFILE \"track01.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n" +
            "FILE \"track02.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n" +
            "REM SESSION 02\nFILE \"track03.bin\" BINARY\n  TRACK 03 AUDIO\n    INDEX 01 00:00:00\n"
        val result = jaguarHash(cue.toByteArray(), mapOf("track03.bin" to jaguarCdBin(1470, 99200, true)))
        assertEquals(RaHashResult.Hashed("060e9d223c584b581cf7d7ce17c0e5dc"), result)
    }

    @Test
    fun jaguarCdWithoutTheApprovedDataHeaderOrWithoutASecondSessionIsRefused() {
        val damaged = jaguarCdBin(2, 32768, true).also { it[2 + 64 + 12] = 'B'.code.toByte() }
        assertEquals(NotARom, reason(jaguarHash(jaguarCue(), mapOf("track02.bin" to damaged))))
        val noSessions = "FILE \"track01.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n" +
            "FILE \"track02.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n" +
            "FILE \"track03.bin\" BINARY\n  TRACK 03 AUDIO\n    INDEX 01 00:00:00\n"
        assertEquals(NotARom, reason(jaguarHash(noSessions.toByteArray(), mapOf("track03.bin" to jaguarCdBin(2, 99200, true)))))
    }

    @Test
    fun jaguarCdBootCodeLongerThanTheTrackIsRefused() {
        val image = jaguarCdBin(2, 60024, false)
        assertEquals(NotARom, reason(jaguarHash(jaguarCue(), mapOf("track02.bin" to image.copyOf(5 * 2352)))))
    }

    @Test
    fun aJaguarCdHomebrewIsHashedByTheProgramInTrackTwo() {
        val cue = "REM SESSION 01\nFILE \"track01.bin\" BINARY\n  TRACK 01 AUDIO\n    INDEX 01 00:00:00\n" +
            "FILE \"track02.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 01 00:00:00\n" +
            "REM SESSION 02\nFILE \"track03.bin\" BINARY\n  TRACK 03 AUDIO\n    INDEX 01 00:00:00\n"
        val loader = jaguarCdBin(2, 45760, true)
        val program = jaguarCdBin(2, 986742, true)
        program[0x60] = 0x21 // "ATARI APPROVED DATA HEADER ATRI!"
        program.copyInto(program, 0xA2, 0x62, 0x6A) // load address and size
        for (at in listOf(0x62, 0x72, 0x82, 0x92)) "RTKARTKARTKARTKA".forEachIndexed { i, c -> program[at + i] = c.code.toByte() }
        val files = mapOf("track03.bin" to loader, "track02.bin" to program)
        // The test names the loader's hash as the homebrew one (rc_hash does the same).
        val result = jaguarHash(cue.toByteArray(), files, homebrewBootHash = "4e4114b2675eff21bb77dd41e141ddd6")
        assertEquals(RaHashResult.Hashed("3fdf70e362c845524c9e447aacaed0a9"), result)
        // Any other loader is hashed as it is.
        val plain = jaguarHash(cue.toByteArray(), files)
        assertEquals("4e4114b2675eff21bb77dd41e141ddd6", (plain as RaHashResult.Hashed).md5)
        // A homebrew whose track 2 has no program is refused.
        val noProgram = jaguarHash(cue.toByteArray(), mapOf("track03.bin" to loader, "track02.bin" to genericFile(2352 * 4)), "4e4114b2675eff21bb77dd41e141ddd6")
        assertEquals(NotARom, reason(noProgram))
    }

    @Test
    fun jaguarCdThroughTheHasherEntryPoint() {
        val files = mapOf("game.cue" to jaguarCue(), "track02.bin" to jaguarCdBin(2, 60024, false))
        assertEquals(RaHashResult.Hashed("c324d95dc5831c2d5c470eefb18c346b"), hash(RaConsoles.ATARI_JAGUAR_CD, Folder(files).source("game.cue")))
    }

    // --- Dreamcast

    private val gdiThree = "3\n1 0 4 2352 track01.bin 0\n2 600 0 2352 track02.raw 0\n3 45000 4 2352 track03.bin 0"

    @Test
    fun dreamcastGdiWithTheBootFileInTrackThree() {
        val image = dreamcastBin(45000, 1458208)
        val expected = RaHashResult.Hashed("2a550500caee9f06e5d061fe10a46f6e")
        val raw = Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to convertTo2352(image, 45000))
        assertEquals("raw sectors", expected, hash(RaConsoles.DREAMCAST, raw.source("game.gdi")))
        val cooked = Folder("game.gdi" to gdiThree.replace("4 2352 track03", "4 2048 track03").toByteArray(), "track03.bin" to image)
        assertEquals("cooked sectors", expected, hash(RaConsoles.DREAMCAST, cooked.source("game.gdi")))
    }

    @Test
    fun dreamcastGdiWithTheBootFileInTheLastTrack() {
        val image = dreamcastBin(548106, 1830912)
        val gdi = "26\n1 0 4 2352 track01.bin 0\n3 45000 4 2352 track03.bin 0\n26 548106 4 2352 track26.bin 0\n"
        val expected = RaHashResult.Hashed("771e56aff169230ede4505013a4bcf9f")
        // The first sector of each file is the LBA the line gives, so each file starts with its own stamp.
        val raw = Folder("game.gdi" to gdi.toByteArray(), "track03.bin" to convertTo2352(image, 45000), "track26.bin" to convertTo2352(image, 548106))
        assertEquals("raw sectors", expected, hash(RaConsoles.DREAMCAST, raw.source("game.gdi")))
        val cooked = Folder("game.gdi" to gdi.replace("2352", "2048").toByteArray(), "track03.bin" to image, "track26.bin" to image)
        assertEquals("cooked sectors", expected, hash(RaConsoles.DREAMCAST, cooked.source("game.gdi")))
    }

    @Test
    fun dreamcastCueSheetFindsTrackThree() {
        val cue = "FILE \"track01.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n" +
            "FILE \"track02.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 00 00:00:00\n    INDEX 01 00:02:00\n" +
            "FILE \"track03.bin\" BINARY\n  TRACK 03 MODE1/2352\n    INDEX 01 00:00:00\n" +
            "FILE \"track04.bin\" BINARY\n  TRACK 04 AUDIO\n    INDEX 00 00:00:00\n    INDEX 01 00:02:00\n" +
            "FILE \"track05.bin\" BINARY\n  TRACK 05 MODE1/2352\n    INDEX 00 00:00:00\n    INDEX 01 00:03:00\n"
        val image = convertTo2352(dreamcastBin(45000, 1697028), 45000)
        val folder = Folder(
            "game.cue" to cue.toByteArray(), "track01.bin" to image.copyOf(1425312), "track02.bin" to image.copyOf(1589952),
            "track03.bin" to image, "track04.bin" to image.copyOf(1237152), "track05.bin" to image,
        )
        assertEquals(RaHashResult.Hashed("c952864c3364591d2a8793ce2cfbf3a0"), hash(RaConsoles.DREAMCAST, folder.source("game.cue")))
    }

    @Test
    fun aMilCdWithNoTrackThreeIsHashedFromItsFirstDataTrack() {
        val image = convertTo2352(dreamcastBin(45000, 1458208), 45000)
        val cue = "FILE \"game.bin\" BINARY\n  TRACK 01 MODE1/2352\n    INDEX 01 00:00:00\n"
        val folder = Folder("game.cue" to cue.toByteArray(), "game.bin" to image)
        assertEquals(RaHashResult.Hashed("2a550500caee9f06e5d061fe10a46f6e"), hash(RaConsoles.DREAMCAST, folder.source("game.cue")))
    }

    @Test
    fun aDreamcastPlaylistHashesItsFirstGdi() {
        val folder = Folder(
            "list.m3u" to "game.gdi\n".toByteArray(), "game.gdi" to gdiThree.toByteArray(), "track03.bin" to convertTo2352(dreamcastBin(45000, 1458208), 45000),
        )
        assertEquals(RaHashResult.Hashed("2a550500caee9f06e5d061fe10a46f6e"), hash(RaConsoles.DREAMCAST, folder.source("list.m3u")))
    }

    @Test
    fun dreamcastWrongPaths() {
        val image = convertTo2352(dreamcastBin(45000, 100000), 45000)
        val gdi = Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to image)
        // No IP.BIN marker.
        val unmarked = image.copyOf().also { it.fill(0, 16, 16 + 16) }
        assertEquals(NotARom, reason(hash(RaConsoles.DREAMCAST, Folder("game.gdi" to gdiThree.toByteArray(), "track01.bin" to unmarked, "track03.bin" to unmarked).source("game.gdi"))))
        // Without track 1 to fall back on, the disc cannot even be opened.
        assertEquals(MissingFile, reason(hash(RaConsoles.DREAMCAST, Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to unmarked).source("game.gdi"))))
        // Boot file name blank.
        val blank = convertTo2352(dreamcastBin(45000, 100000, bootName = ""), 45000)
        assertEquals(NotARom, reason(hash(RaConsoles.DREAMCAST, Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to blank).source("game.gdi"))))
        // Boot file not on the disc.
        val missing = convertTo2352(dreamcastBin(45000, 100000, bootName = "OTHER.BIN"), 45000)
        assertEquals(NotARom, reason(hash(RaConsoles.DREAMCAST, Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to missing).source("game.gdi"))))
        // Track files that are not there.
        assertEquals(MissingFile, reason(hash(RaConsoles.DREAMCAST, Folder("game.gdi" to gdiThree.toByteArray()).source("game.gdi"))))
        // A data track cut before its volume descriptor has no boot file to find.
        assertEquals(NotARom, reason(hash(RaConsoles.DREAMCAST, Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to image.copyOf(10 * 2352)).source("game.gdi"))))
        // Like rc_hash, a boot file cut short in the middle is hashed as far as it goes.
        val cut = hash(RaConsoles.DREAMCAST, Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to image.copyOf(30 * 2352)).source("game.gdi"))
        assertTrue(cut is RaHashResult.Hashed)
        // A disc image of another system.
        assertEquals(NotARom, reason(hash(RaConsoles.DREAMCAST, gdi.source("game.gdi").let { MemorySource("game.iso", psxBin("SLUS_007.45", 0x07D800)) })))
    }

    @Test
    fun aDreamcastGdiWhoseTrackThreeIsMissingAndFirstTrackIsNotDreamcastIsMissingFile() {
        val plain = convertTo2352(isoBin(64, "TEST"), 0)
        val folder = Folder("game.gdi" to gdiThree.toByteArray(), "track01.bin" to plain)
        assertEquals(MissingFile, reason(hash(RaConsoles.DREAMCAST, folder.source("game.gdi"))))
    }

    @Test
    fun theListedPathLimitMatchesRcHashInSubfolders() {
        // 18 filler files in folders A, B and C use 18 * 103 bytes of the 2048-byte budget; a file of
        // bare length n in folder D then needs 1854 + 1 + n + 2 < 2048, so n = 190 fits and 191 does not.
        fun image(bare: Int): ByteArray {
            val image = isoBin(256, "TEST")
            for ((folder, count) in listOf("A" to 8, "B" to 8, "C" to 2)) {
                for (n in 0 until count) isoAddFile(image, "$folder\\${"F".repeat(95)}$n.EXP", genericFile(10))
            }
            isoAddFile(image, "D\\${"T".repeat(bare - 4)}.EXP", genericFile(10))
            return image
        }
        fun listed(bare: Int): Int =
            DiscReader.openTrack(MemorySource("game.iso", image(bare)), 1, {}).use { Iso9660.list(it) { true }.size }
        assertEquals(19, listed(190))
        assertEquals(18, listed(191))
    }

    // --- the .gdi reader on its own

    private fun gdiTrackBytes(gdi: String, request: Int, files: Map<String, ByteArray>): Pair<Int, Byte> {
        val source = Folder(files + ("game.gdi" to gdi.toByteArray())).source("game.gdi")
        DiscReader.openTrack(source, request, {}).use { track ->
            val data = ByteArray(1)
            assertEquals(1, track.readSector(track.firstSector.toLong(), data, 1))
            return track.firstSector to data[0]
        }
    }

    @Test
    fun gdiTracksAreFoundByNumberAndBySpecialRequest() {
        val gdi = "5\n1 0 4 2352 a.bin 0\n2 600 0 2352 b.raw 0\n3 45000 4 2352 c.bin 0\n4 46000 0 2352 d.raw 0\n5 50000 4 2352 e.bin 0\n"
        fun bin(marker: Int, sectors: Int) = ByteArray(sectors * 2352).also { it[16] = marker.toByte() }
        val files = mapOf("a.bin" to bin(1, 20), "b.raw" to bin(2, 5), "c.bin" to bin(3, 40), "d.raw" to bin(4, 5), "e.bin" to bin(5, 10))
        assertEquals(45000 to 3.toByte(), gdiTrackBytes(gdi, 3, files))
        assertEquals(0 to 1.toByte(), gdiTrackBytes(gdi, DiscTracks.FIRST_DATA, files))
        assertEquals(50000 to 5.toByte(), gdiTrackBytes(gdi, DiscTracks.LAST, files))
        assertEquals(45000 to 3.toByte(), gdiTrackBytes(gdi, DiscTracks.LARGEST, files))
        assertEquals(1, 1) // a request for a track that is not listed is refused below
        try {
            gdiTrackBytes(gdi, 9, files)
            fail("track 9 is not listed")
        } catch (expected: DiscFormatException) {
            assertEquals(NotARom, expected.reason)
        }
    }

    @Test
    fun gdiLinesMayBeQuotedAndSpacedOut() {
        val gdi = "3\n\n  1       0   4   2352   \"track 01.bin\"   0\n\n  3   45000   4   2352   \"track 03.bin\"   0\n\n"
        val files = mapOf("track 01.bin" to ByteArray(2352 * 3), "track 03.bin" to ByteArray(2352 * 3).also { it[16] = 9 })
        assertEquals(45000 to 9.toByte(), gdiTrackBytes(gdi, 3, files))
    }

    @Test
    fun gdiNamesThatLeaveTheFolderAreRefused() {
        val gdi = "1\n1 0 4 2352 ../track01.bin 0\n"
        val folder = Folder("game.gdi" to gdi.toByteArray(), "../track01.bin" to ByteArray(2352 * 3))
        try {
            DiscReader.openTrack(folder.source("game.gdi"), 1, {}).close()
            fail("expected a refusal")
        } catch (expected: DiscFormatException) {
            assertEquals(MissingFile, expected.reason)
        }
    }

    @Test
    fun anEmptyOrBrokenGdiHasNoTracks() {
        for (text in listOf("", "0\n", "3\nnot a track line\n1 0 four 2352 a.bin 0\n")) {
            try {
                DiscReader.openTrack(Folder("game.gdi" to text.toByteArray()).source("game.gdi"), 3, {}).close()
                fail("expected a refusal for \"$text\"")
            } catch (expected: DiscFormatException) {
                assertEquals(NotARom, expected.reason)
            }
        }
    }

    // --- cancellation and read errors

    @Test
    fun cancellationPassesThroughEveryCdSystem() {
        val cases = listOf(
            RaConsoles.THREE_DO to MemorySource("game.iso", threeDoBin(3, 123456)),
            RaConsoles.NEO_GEO_CD to MemorySource("game.iso", vectors.first { it.console == RaConsoles.NEO_GEO_CD }.image),
            RaConsoles.PC_ENGINE_CD to MemorySource("game.iso", pceCdBin(72)),
            RaConsoles.PCFX to MemorySource("game.iso", pcFxBin(72)),
            RaConsoles.FM_TOWNS to MemorySource("game.iso", fmTownsIso()),
            RaConsoles.PLAYSTATION_3 to MemorySource("game.iso", ps3Iso()),
        )
        for ((console, source) in cases) {
            try {
                RaHasher.hashBlocking(console, source) { throw CancellationException("stop") }
                fail("console $console: expected the cancellation to pass through")
            } catch (expected: CancellationException) {
                assertEquals("stop", expected.message)
            }
        }
    }

    @Test
    fun cancellationStopsAJaguarOrDreamcastRead() {
        val jaguar = Folder("game.cue" to jaguarCue(), "track02.bin" to jaguarCdBin(2, 60024, false)).source("game.cue")
        val dreamcast = Folder("game.gdi" to gdiThree.toByteArray(), "track03.bin" to convertTo2352(dreamcastBin(45000, 1458208), 45000)).source("game.gdi")
        for ((console, source) in listOf(RaConsoles.ATARI_JAGUAR_CD to jaguar, RaConsoles.DREAMCAST to dreamcast)) {
            try {
                RaHasher.hashBlocking(console, source) { throw CancellationException("stop") }
                fail("console $console: expected the cancellation to pass through")
            } catch (expected: CancellationException) {
                assertTrue(expected.message == "stop")
            }
        }
    }

    @Test
    fun aReadFailureOnACdSystemIsReportedNotSwallowed() {
        for (console in listOf(RaConsoles.THREE_DO, RaConsoles.PCFX, RaConsoles.DREAMCAST, RaConsoles.FM_TOWNS, RaConsoles.GAMECUBE, RaConsoles.WII)) {
            assertTrue("console $console", hash(console, FailingSource("game.iso")) is RaHashResult.Unreadable)
        }
    }
}
