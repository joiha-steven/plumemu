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
        assertEquals(PanelPage.AccentPresets, model.focus.page)
    }

    private fun shown(): Rgb = actions.settings.toThemeSettings().accentColor

    /** With a store that writes, the accent in effect (what the Color code row shows) is the shade the cursor or strip stands on after every kind of move on the Accent page. */
    @Test
    fun theAccentShownIsAlwaysTheShadeTheCursorStandsOn() {
        openAccent()
        toShade()
        val shades = model.colorShades(colourPage)
        assertEquals("arriving on a shade", shades[model.shadePlaceOf(colourPage)], shown())
        move(Direction.Right)
        assertEquals("a step among the shades", shades[model.focus.panelIndex - model.colorIndex(colourPage) - 1], shown())
        move(Direction.Up)
        move(Direction.Right)
        val onColor = model.colorShades(colourPage)
        assertEquals("a step on Color", onColor[model.shadePlaceOf(colourPage)], shown())
        toHexRow()
        assertEquals("arriving on the Color code row keeps it", onColor[model.shadePlaceOf(colourPage)], shown())
    }
}
