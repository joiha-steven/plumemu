package com.plumemu.launcher.choice

import com.plumemu.launcher.TestCatalogs
import com.plumemu.platforms.PackageVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Disc emulators of the shipped data that open only some file types (`accepts`, read from Daijisho's
 * player lists by tools/platforms, audit item 20): one that cannot open the game's file is neither
 * offered nor recommended for it, and a choice of it is passed over for that file only.
 */
class DiscFileTypesTest {
    private val catalog = TestCatalogs.shipped
    private val psx = checkNotNull(catalog.system("psx"))
    private val saturn = checkNotNull(catalog.system("saturn"))
    private val gc = checkNotNull(catalog.system("gc"))

    private fun installed(vararg packages: String): (PackageVariant) -> Boolean = { it.packageName in packages }

    private fun offered(pick: EmulatorPick): List<String> = (pick as EmulatorPick.NotChosen).installed.map { it.id }

    @Test
    fun duckStationOpensPlaylistsCompressedAndSheetedDiscs() {
        val duckStation = checkNotNull(catalog.emulator("duckstation"))
        for (file in listOf("Final Fantasy VII.m3u", "Crash.chd", "Tekken 3.pbp", "Spyro.cue", "Spyro.bin")) {
            assertTrue(file, duckStation.acceptsFile(file))
        }
        assertFalse(duckStation.acceptsFile("Spyro.zip"))
        val pick = EmulatorResolver.resolve(
            catalog, psx, null, null, installed("com.github.stenzek.duckstation"), "Final Fantasy VII.m3u",
        )
        assertEquals(listOf("duckstation"), offered(pick))
    }

    @Test
    fun anEmulatorThatCannotOpenAnM3uIsNotOfferedForOne() {
        // RetroArch holds every core; YabaSanshiro's core takes no .m3u, Beetle Saturn does.
        val retroArch = installed("com.retroarch.aarch64", "com.explusalpha.SaturnEmu")
        val forM3u = offered(EmulatorResolver.resolve(catalog, saturn, null, null, retroArch, "Panzer Dragoon Saga.m3u"))
        assertFalse(forM3u.toString(), "retroarch-yabasanshiro" in forM3u)
        assertTrue(forM3u.toString(), "retroarch-mednafen-saturn" in forM3u && "saturn-emu" in forM3u)
        val forCue = offered(EmulatorResolver.resolve(catalog, saturn, null, null, retroArch, "Panzer Dragoon Saga.cue"))
        assertTrue(forCue.toString(), "retroarch-yabasanshiro" in forCue)
    }

    @Test
    fun aGameChoiceThatCannotOpenTheFileIsPassedOver() {
        // The person chose Dolphin MMJR for this game; its file is an .m3u, which MMJR does not open.
        val pick = EmulatorResolver.resolve(
            catalog, gc, "dolphin-mmjr", null, installed("org.mm.jr", "org.dolphinemu.dolphinemu"), "Baten Kaitos.m3u",
        )
        assertEquals(listOf("dolphin"), offered(pick))
        // A .rvz is MMJR's: the choice stands.
        val rvz = EmulatorResolver.resolve(
            catalog, gc, "dolphin-mmjr", null, installed("org.mm.jr", "org.dolphinemu.dolphinemu"), "Baten Kaitos.rvz",
        )
        assertEquals("dolphin-mmjr", (rvz as EmulatorPick.Ready).emulator.id)
    }

    @Test
    fun beetlePsxIsNotOfferedABareBinButAnUndeclaredStandaloneStill() {
        val pick = EmulatorResolver.resolve(catalog, psx, null, null, installed("com.retroarch.aarch64", "com.epsxe.ePSXe"), "Spyro.bin")
        val ids = offered(pick)
        assertFalse(ids.toString(), "retroarch-mednafen-psx" in ids || "retroarch-mednafen-psx-hw" in ids)
        // ePSXe's formats have no source of its own: it stays offered for every file of its system.
        assertTrue(ids.toString(), "epsxe" in ids && "retroarch-duckstation" in ids)
    }

    @Test
    fun retroArchCoresStillGetAZipOrA7zThatItUnpacksItself() {
        // RetroArch unpacks .zip and .7z before the core sees them, so Daijisho's lists do not drop them.
        val retroArch = installed("com.retroarch.aarch64")
        val saturn7z = offered(EmulatorResolver.resolve(catalog, saturn, null, null, retroArch, "Panzer Dragoon Saga.7z"))
        assertTrue(saturn7z.toString(), "retroarch-mednafen-saturn" in saturn7z)
        val psxZip = offered(EmulatorResolver.resolve(catalog, psx, null, null, retroArch, "Spyro.zip"))
        assertTrue(psxZip.toString(), "retroarch-mednafen-psx" in psxZip && "retroarch-duckstation" in psxZip)
        // The standalone DuckStation still opens no .zip.
        assertFalse(checkNotNull(catalog.emulator("duckstation")).acceptsFile("Spyro.zip"))
    }
}
