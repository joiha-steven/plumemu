package com.plumemu.shortcuts

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a pinned shortcut is listed, by its app's package, from the shipped platform data. */
class ShortcutFilingTest {
    private val filing = ShortcutFiling(shippedCatalog)

    @Test
    fun steamAppsFileUnderSteam() {
        for (app in listOf("app.gamenative", "gamehub.lite", "emuready.gamehub.lite", "com.droiddeck.launcher")) {
            assertEquals(app, ShortcutFiling.STEAM, filing.systemFor(app))
        }
    }

    @Test
    fun theWinlatorFamilyAndWinNativeFileUnderWindows() {
        for (app in listOf("com.winlator.cmod", "com.winlator", "com.cmodded.winlator", "com.winnative.cmod")) {
            assertEquals(app, ShortcutFiling.WINDOWS, filing.systemFor(app))
        }
    }

    @Test
    fun anyOtherAppFilesUnderShortcuts() {
        for (app in listOf("com.example.notes", "com.valvesoftware.steamlink", "")) {
            assertEquals(app, ShortcutFiling.SHORTCUTS, filing.systemFor(app))
        }
    }

    @Test
    fun theListsComeFromThePlatformDataNotFromASecondList() {
        // Without the data nothing is known to be Steam or Windows.
        assertEquals(ShortcutFiling.SHORTCUTS, ShortcutFiling(null).systemFor("app.gamenative"))
        assertEquals(ShortcutFiling.SHORTCUTS, ShortcutFiling(null).systemFor("com.winlator.cmod"))
    }

    @Test
    fun noPlatformSystemTakesTheShortcutsId() {
        assertEquals(null, shippedCatalog.system(ShortcutFiling.SHORTCUTS))
    }
}
