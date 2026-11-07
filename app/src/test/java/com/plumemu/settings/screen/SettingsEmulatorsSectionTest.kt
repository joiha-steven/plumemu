package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Emulators has no fixed rows: it is listed only while a system has games, and leaving while open never loses the focus. */
internal class SettingsEmulatorsSectionTest : SettingsModelHarness() {
    private val psx = SystemEmulators("psx", "PlayStation", automatic = null, chosen = null, installed = listOf(EmulatorEntry("duckstation", "DuckStation", isLegalRisk = false)))
    private val withGames = SettingsContent(systems = listOf(psx))
    private val noGames = SettingsContent()

    private fun assertFocusIsOnASectionShown() {
        assertTrue(model.focus.section in SettingsSection.shownIn(model.content))
        assertEquals(SettingsColumn.Sections, model.focus.column)
        assertEquals(null, model.focus.page)
    }

    @Test
    fun hiddenWithNoGamesAndListedWithSome() {
        assertFalse(SettingsSection.Emulators in SettingsSection.shownIn(noGames))
        assertTrue(SettingsSection.Emulators in SettingsSection.shownIn(withGames))
        assertEquals(listOf(SettingsOption.SystemEmulator), SettingsSection.Emulators.rowsIn(withGames))
    }

    @Test
    fun sectionSteppingPassesOverItBothWaysWithNoGames() {
        model.content = noGames
        openSection(SettingsSection.HomeScreen)
        press(Action.NextSystem)
        assertEquals(SettingsSection.Controls, model.focus.section)
        press(Action.PreviousSystem)
        assertEquals(SettingsSection.HomeScreen, model.focus.section)
    }

    @Test
    fun sectionSteppingStopsThereWithGames() {
        model.content = withGames
        openSection(SettingsSection.HomeScreen)
        press(Action.NextSystem)
        assertEquals(SettingsSection.Emulators, model.focus.section)
        assertEquals(SettingsOption.SystemEmulator, model.focusedOption)
    }

    @Test
    fun theLastGameLeavingWithItsRowFocusedPutsTheFocusOnTheSectionAbove() {
        model.content = withGames
        openSection(SettingsSection.Emulators)
        model.content = noGames
        assertEquals(SettingsSection.HomeScreen, model.focus.section)
        assertFocusIsOnASectionShown()
    }

    @Test
    fun theLastGameLeavingWithItsPanelOpenClosesThePanel() {
        model.content = withGames
        openSection(SettingsSection.Emulators)
        press(Action.Enter)
        assertEquals(PanelPage.Emulators, model.focus.page)
        model.content = noGames
        assertEquals(SettingsSection.HomeScreen, model.focus.section)
        assertEquals(0, model.focus.panelIndex)
        assertFocusIsOnASectionShown()
        // Every key still works from there.
        move(Direction.Down)
        assertEquals(SettingsSection.Controls, model.focus.section)
        press(Action.NextSystem)
        assertEquals(SettingsSection.Statistics, model.focus.section)
    }

    @Test
    fun itComesBackWithGamesAndOpensOnARowThatExists() {
        model.content = SettingsContent(systems = listOf(psx, psx.copy(systemId = "snes", name = "Super Nintendo")))
        openSection(SettingsSection.Emulators)
        move(Direction.Down)
        model.content = noGames
        model.content = withGames
        assertEquals("the focus stays where it went", SettingsSection.HomeScreen, model.focus.section)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals("the remembered row is past the end now", SettingsOption.SystemEmulator, model.focusedOption)
        assertEquals(0, model.optionIndex)
    }
}
