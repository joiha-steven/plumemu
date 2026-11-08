package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.Rgb
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

internal class SettingsAccentCodeTest : SettingsModelHarness() {
    init {
        startOnRed()
    }

    private fun openAccent() {
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Accent)
        press(Action.Enter)
        openPlus()
        assertEquals(PanelPage.AccentCustom, model.focus.page)
    }

    private val cells: Int get() = ColorPages.palette(model.focus.page!!, actions.settings.toThemeSettings().backgroundColor).size

    private fun onSlider(slider: ColorSlider) {
        var guard = 0
        while (model.focus.panelIndex != cells + slider.ordinal && guard++ < MAX_MOVES) move(Direction.Down)
        assertEquals(cells + slider.ordinal, model.focus.panelIndex)
    }

    /** With a store that writes, what the sliders stand on and what the Color code row shows are one colour after every kind of move on the Accent page. */
    @Test
    fun accentSlidersAndTheColorCodeNeverDisagree() {
        openAccent()
        fun assertTheyAgree(what: String) = assertEquals(what, actions.settings.toThemeSettings().accentColor.value, Rgb(model.sliderHsl.rgb().value).value)
        repeat(2) {
            move(Direction.Right)
            assertTheyAgree("a step on the grid")
        }
        for (slider in listOf(ColorSlider.Hue, ColorSlider.Saturation, ColorSlider.Lightness)) {
            onSlider(slider)
            assertTheyAgree("arriving on $slider")
            move(Direction.Right)
            assertTheyAgree("a step of $slider")
            move(Direction.Left)
            assertTheyAgree("back a step of $slider")
        }
        repeat(ColorSlider.entries.size + 1) { move(Direction.Up) }
        assertTheyAgree("up on the grid")
    }

    private companion object {
        const val MAX_MOVES = 20
    }
}
