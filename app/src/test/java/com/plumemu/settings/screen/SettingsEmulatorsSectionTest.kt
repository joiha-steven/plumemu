package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Library's Emulators row is listed only while a system has games, and leaving while open never loses the focus. */
internal class SettingsEmulatorsSectionTest : SettingsModelHarness() {
    private val psx = SystemEmulators("psx", "PlayStation", automatic = null, chosen = null, installed = listOf(EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false)))
    private val withGames = SettingsContent(systems = listOf(psx))
    private val noGames = SettingsContent()

    private fun assertFocusIsOnALibraryRow() {
        assertEquals(SettingsSection.Library, model.focus.section)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(null, model.focus.page)
        assertTrue(model.focusedOption in SettingsSection.Library.rowsIn(model.content))
    }

    @Test
    fun listedInLibraryWithGamesAndNotWithout() {
        assertFalse(SettingsOption.Emulators in SettingsSection.Library.rowsIn(noGames))
        val rows = SettingsSection.Library.rowsIn(withGames)
        assertTrue(SettingsOption.Emulators in rows)
        assertTrue("after Files not added", rows.indexOf(SettingsOption.Emulators) > rows.indexOf(SettingsOption.FilesNotAdded))
        assertEquals("no section of its own", 6, SettingsSection.entries.size)
    }

    @Test
    fun sectionSteppingIsTheSameWithAndWithoutGames() {
        for (content in listOf(noGames, withGames)) {
            model.content = content
            openSection(SettingsSection.HomeScreen)
            press(Action.NextSystem)
            assertEquals(SettingsSection.Controls, model.focus.section)
            press(Action.PreviousSystem)
            assertEquals(SettingsSection.HomeScreen, model.focus.section)
        }
    }

    @Test
    fun theLastGameLeavingWithItsRowFocusedKeepsTheFocusInLibrary() {
        model.content = withGames
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Emulators)
        model.content = noGames
        assertFocusIsOnALibraryRow()
        // Every key still works from there.
        move(Direction.Down)
        assertEquals(SettingsSection.Library, model.focus.section)
        press(Action.NextSystem)
        assertEquals(SettingsSection.Appearance, model.focus.section)
    }

    @Test
    fun theLastGameLeavingWithItsPanelOpenClosesThePanel() {
        model.content = withGames
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Emulators)
        press(Action.Enter)
        assertEquals(PanelPage.EmulatorSystems, model.focus.page)
        press(Action.Enter)
        assertEquals(PanelPage.Emulators, model.focus.page)
        model.content = noGames
        assertEquals(0, model.focus.panelIndex)
        assertFocusIsOnALibraryRow()
    }

    @Test
    fun itComesBackWithGamesAndTheFocusStaysOnARowThatExists() {
        model.content = SettingsContent(systems = listOf(psx, psx.copy(systemId = "snes", name = "Super Nintendo")))
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Emulators)
        model.content = noGames
        model.content = withGames
        assertFocusIsOnALibraryRow()
        move(Direction.Down)
        assertFocusIsOnALibraryRow()
        move(Direction.Up)
        move(Direction.Up)
        assertFocusIsOnALibraryRow()
    }
}
