package com.plumemu.library.catalog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkFilterTest {
    @Test
    fun biosFilesAreJunk() {
        listOf(
            "scph1001.bin", "SCPH5501.BIN", "scph-7001.bin", "scph39001a.bin", "gba_bios.bin", "syscard3.pce",
            "neogeo.zip", "bios7.bin", "bios9.bin", "firmware.bin", "dc_boot.bin", "bios_CD_U.bin",
            "mpr-17933.bin", "panafz10.bin", "ps2-0230a-20080220.bin", "disksys.rom", "lynxboot.img",
            "openbios.bin", "SCPH-5501 (NTSC-U).bin", "scph1001.bin", "SCPH5501.BIN", "scph7502 (PAL) [!].bin", "kick13.rom",
        ).forEach { assertTrue(it, JunkFilter.isJunkFile(it)) }
    }

    @Test
    fun hiddenNotesImagesAndSavesAreJunk() {
        listOf(
            ".hidden.sfc", "._Game.gba", "readme.txt", "info.nfo", "cover.png", "box.JPG", "Game.srm",
            "Game.sav", "Game.state", "Game.state3", "Game.st0", "gamelist.xml", "Thumbs.db", "desktop.ini",
        ).forEach { assertTrue(it, JunkFilter.isJunkFile(it)) }
    }

    @Test
    fun documentsAreJunkWhateverTheirExtension() {
        listOf("README.md", "readme", "LICENSE", "Licence.MD", "CHANGELOG.md", "CONTRIBUTING.md", "notes.md", "README.first.txt")
            .forEach { assertTrue(it, JunkFilter.isJunkFile(it)) }
        // A real Mega Drive game with a .md extension, or a title holding those words, is not a document.
        listOf("Sonic.md", "Readme Quest.md", "Release Notes (Japan).md", "License to Kill (USA).nes")
            .forEach { assertFalse(it, JunkFilter.isJunkFile(it)) }
    }

    @Test
    fun gamesAreNotJunk() {
        listOf(
            "Super Mario World (USA).sfc", "Sonic the Hedgehog.md", "Crash Bandicoot (USA).bin", "sf2.zip",
            "Final Fantasy VII (USA).m3u", "Bios Fear.sfc", "Game.iso", "Game.chd",
        ).forEach { assertFalse(it, JunkFilter.isJunkFile(it)) }
    }

    @Test
    fun biosHiddenAndMediaFoldersAreSkipped() {
        listOf("bios", "BIOS", ".thumbnails", "media", "images", "downloaded_media").forEach {
            assertTrue(it, JunkFilter.isSkippedDirectory(it))
        }
        listOf("snes", "psx", "hacks", "Biosphere").forEach { assertFalse(it, JunkFilter.isSkippedDirectory(it)) }
    }

    @Test
    fun gamesWithBiosLikeWordsAreNotJunk() {
        listOf("Bios Fear.bin", "BioShock.iso", "Openbios Quest.bin", "scph.bin", "Kickboxer.rom", "SCPH Adventure.bin")
            .forEach { assertFalse(it, JunkFilter.isJunkFile(it)) }
    }
}
