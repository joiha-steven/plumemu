package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Test

/** Left from the leftmost preset leaves the page on a press, never on a held key's repeat. */
internal class SettingsColourPresetLeftHeldTest : SettingsModelHarness() {
    private fun onFirstPreset() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        val page = colourPage
        repeat(10) { if (onPart != ColorPart.Preset) move(Direction.Up) }
        repeat(30) { if (model.focus.panelIndex > 0) move(Direction.Up) }
        assertEquals(ColorPart.Preset, onPart)
        assertEquals(0, model.focus.panelIndex)
        assertEquals(page, colourPage)
    }

    @Test
    fun aHeldLeftOnTheLeftmostPresetStaysOnThePage() {
        onFirstPreset()
        val page = colourPage
        press(Action.Move(Direction.Left, isRepeat = true))
        assertEquals(page, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun aPressOfLeftOnTheLeftmostPresetLeavesThePage() {
        onFirstPreset()
        press(Action.Move(Direction.Left))
        assertOnColorsRow(colorsRowItemOf(ColorKind.Background))
    }
}
