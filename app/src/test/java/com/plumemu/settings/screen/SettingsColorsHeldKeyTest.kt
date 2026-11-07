package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A held Left or Right stays where its press landed: its repeats never leave Colors' panel for the row, step the sets or write a preset. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsColorsHeldKeyTest : SettingsModelHarness() {
    private fun held(direction: Direction) = press(Action.Move(direction, isRepeat = true))

    private fun openColors() {
        model.content = SettingsContent()
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Colors)
        press(Action.Enter)
        assertEquals(PanelPage.ColorSets, model.focus.page)
    }

    @Test
    fun heldLeftOnTheBackgroundRowStaysInThePanelAndChangesNoColour() {
        openColors()
        focusOption(SettingsOption.Background)
        val colours = appearance
        repeat(4) { held(Direction.Left) }
        assertEquals(PanelPage.ColorSets, model.focus.page)
        assertEquals(colours, appearance)
    }

    @Test
    fun aPressThatLeavesThePanelIsNotCarriedOnByItsRepeats() {
        openColors()
        focusOption(SettingsOption.TextColor)
        val colours = appearance
        move(Direction.Left)
        assertEquals("a press of its own leaves", SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Colors, model.focusedOption)
        repeat(4) { held(Direction.Left) }
        assertEquals(colours, appearance)
        move(Direction.Left)
        assertEquals("a new press is heard again", true, appearance != colours || model.focus.column == SettingsColumn.Options)
    }

    @Test
    fun heldRightDoesNotOpenARowsPage() {
        openColors()
        focusOption(SettingsOption.Background)
        held(Direction.Right)
        assertEquals(PanelPage.ColorSets, model.focus.page)
    }

    @Test
    fun theRepeatsOfThePressThatOpenedAColourPageWriteNoPreset() {
        openColors()
        focusOption(SettingsOption.Background)
        val colours = appearance
        val writes = actions.writes
        move(Direction.Right)
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        val cursor = model.focus.panelIndex
        repeat(4) { held(Direction.Right) }
        assertEquals(cursor, model.focus.panelIndex)
        assertEquals(colours, appearance)
        assertEquals(writes, actions.writes)
    }

    @Test
    fun heldUpAndDownWalkThePanelAsBefore() {
        openColors()
        val start = model.focus.panelIndex
        press(Action.Move(Direction.Down))
        held(Direction.Down)
        assertEquals(true, model.focus.panelIndex != start)
    }
}
