package com.plumemu.settings.screen

import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home screen › Grid size: touch's way to the size, listed only while the tappable Size hint is gone. */
internal class SettingsGridSizeRowTest : SettingsModelHarness() {
    private val hintsOff = SettingsContent(hasNoButtonHints = true)

    @Test
    fun theRowIsListedOnlyWithHintsOff() {
        assertFalse(SettingsOption.GridSize in SettingsSection.HomeScreen.rowsIn(SettingsContent()))
        assertEquals(SettingsOption.GridSize, SettingsSection.HomeScreen.rowsIn(hintsOff).last())
    }

    @Test
    fun itStepsTheStoredSize() {
        model.content = hintsOff
        openSection(SettingsSection.HomeScreen)
        focusOption(SettingsOption.GridSize)
        assertEquals(GridSizeSetting.Medium, appearance.gridSize)
        move(Direction.Right)
        assertEquals(GridSizeSetting.Large, appearance.gridSize)
        move(Direction.Left)
        move(Direction.Left)
        assertEquals(GridSizeSetting.Small, appearance.gridSize)
        model.onTapValue(SettingsSection.HomeScreen.rowsIn(hintsOff).indexOf(SettingsOption.GridSize), actions.settings, wide)
        assertEquals("a tap on the value steps too", GridSizeSetting.Medium, appearance.gridSize)
    }

    @Test
    fun whenItLeavesWhileFocusedTheCursorLandsOnTheRowAbove() {
        model.content = hintsOff
        openSection(SettingsSection.HomeScreen)
        focusOption(SettingsOption.GridSize)
        model.content = SettingsContent()
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.LeftEdge, model.focusedOption)
        move(Direction.Up)
        assertEquals("the cursor still walks", SettingsOption.SystemInfo, model.focusedOption)
    }

    @Test
    fun whenItLeavesUnderItsOpenPanelThePanelClosesWithIt() {
        model.content = hintsOff
        openSection(SettingsSection.HomeScreen)
        focusOption(SettingsOption.GridSize)
        press(Action.Move(Direction.Right), narrow) // steps it: a choice has no panel stop
        assertNull(model.focus.page)
        val open = model.focus.copy(column = SettingsColumn.Panel, page = PanelPage.ReadOnly)
        val kept = SettingsListPages.homeScreenFocusKept(open, hintsOff, SettingsContent())
        assertEquals(SettingsColumn.Options, kept?.column)
        assertNull(kept?.page)
    }

    @Test
    fun anotherRowOfTheSectionStaysWhereItIs() {
        model.content = hintsOff
        openSection(SettingsSection.HomeScreen)
        focusOption(SettingsOption.ShowApps)
        model.content = SettingsContent()
        assertEquals(SettingsOption.ShowApps, model.focusedOption)
        assertTrue(SettingsSection.HomeScreen.rowsIn(model.content).isNotEmpty())
    }
}
