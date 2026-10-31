package com.plumemu.game

import com.plumemu.platforms.PlatformCatalog
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Play with" offers only the emulators that can open the game's file (the data's `accepts`): in
 * Steam, a `.steam` file goes to the Steam apps and a `.desktop` shortcut to WinNative.
 */
class PlayWithFileTypeTest {
    private val catalog: PlatformCatalog =
        (PlatformLoader.load(File("../platforms/src/main/assets/platforms.json").readText(), "shipped") as PlatformLoadResult.Loaded).catalog
    private val steam = catalog.system("steam")!!
    private val both = setOf("gamenative", "winnative")

    @Test
    fun aSteamFileListsNoWinNative() {
        val listing = emulatorListingOf(catalog, steam, "Broforce.steam", both, systemChoiceId = null, gameChoiceId = null)
        assertEquals(listOf("gamenative"), listing.installed.map { it.id })
        assertEquals(listOf("gamehub-lite", "droiddeck"), listing.notInstalled.map { it.id })
        assertEquals("gamenative", listing.automatic?.id)
        assertFalse(listing.isSystemChosen)
    }

    @Test
    fun aDesktopFileListsOnlyWinNative() {
        val listing = emulatorListingOf(catalog, steam, "Broforce.desktop", both, systemChoiceId = null, gameChoiceId = null)
        assertEquals(listOf("winnative"), listing.installed.map { it.id })
        assertTrue(listing.notInstalled.isEmpty())
        assertEquals("winnative", listing.automatic?.id)
    }

    @Test
    fun aSystemChoiceThatCannotOpenTheFileGivesWayToTheRecommendation() {
        // WinNative chosen for Steam: Automatic on a .steam game starts GameNative, without asking.
        val listing = emulatorListingOf(catalog, steam, "Broforce.steam", both, systemChoiceId = "winnative", gameChoiceId = null)
        assertEquals("gamenative", listing.automatic?.id)
        assertTrue(listing.isAutomaticInstalled)
        assertTrue("a system choice exists: Play does not ask", listing.isSystemChosen)
        val chosen = emulatorListingOf(catalog, steam, "Broforce.steam", both, systemChoiceId = "gamenative", gameChoiceId = null)
        assertEquals("gamenative", chosen.automatic?.id)
    }

    @Test
    fun aGameChoiceTheSystemNoLongerListsOrThatCannotOpenTheFileIsNotCurrent() {
        // Half-Life.steam moved from windows with Winlator chosen for it.
        val moved = emulatorListingOf(catalog, steam, "Half-Life.steam", both, systemChoiceId = null, gameChoiceId = "winlator-cmod")
        assertEquals(null, moved.currentEmulatorId)
        val wrongType = emulatorListingOf(catalog, steam, "Half-Life.steam", both, systemChoiceId = null, gameChoiceId = "winnative")
        assertEquals(null, wrongType.currentEmulatorId)
        val kept = emulatorListingOf(catalog, steam, "Half-Life.steam", both, systemChoiceId = null, gameChoiceId = "gamenative")
        assertEquals("gamenative", kept.currentEmulatorId)
    }

    @Test
    fun anEpicFileListsWinNative() {
        val listing = emulatorListingOf(catalog, catalog.system("epic")!!, "Fortnite.epic", setOf("winnative"), systemChoiceId = null, gameChoiceId = null)
        assertEquals(listOf("winnative"), listing.installed.map { it.id })
    }

    @Test
    fun otherSystemsListEveryEmulatorAsBefore() {
        val snes = catalog.system("snes")!!
        val listing = emulatorListingOf(catalog, snes, "Zork.sfc", emptySet(), systemChoiceId = null, gameChoiceId = null)
        assertEquals(catalog.emulatorsOf(snes).map { it.id }, listing.notInstalled.sortedBy { it.isLegalRisk }.map { it.id }.let { ids -> catalog.emulatorsOf(snes).map { it.id }.filter { it in ids } })
        assertEquals(catalog.emulatorsOf(snes).size, listing.installed.size + listing.notInstalled.size)
    }
}
