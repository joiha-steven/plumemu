package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.BackgroundPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A colour page opens with its cursor on the shade nearest an own colour, a shade that was never
 * previewed: A before any move keeps the current colour, a tap on that shade keeps it, and once the
 * cursor moved A keeps what it shows. Also the names of the tinted text presets.
 */
internal class SettingsColorPickTest : SettingsModelHarness() {
    private val typed = ColorSetting.Custom(0x123456)

    private fun openBackgroundOnTheTypedColour() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = typed))
        model.forgetColorWrites()
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        // An own colour opens on its nearest shade.
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        assertEquals(ColorPart.Shade, model.colourPart)
    }

    private fun shadeUnderTheCursor() = model.colorShades(colourPage)[model.focus.panelIndex - model.colorIndex(colourPage) - 1]

    @Test
    fun aAtOnceKeepsTheTypedColourInsteadOfTheShadeUnderTheCursor() {
        openBackgroundOnTheTypedColour()
        assertNotEquals("the cursor sits on a different colour than the typed one", typed.rgb, shadeUnderTheCursor().value)
        press(Action.Enter)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM, "the page closed")
        assertEquals(typed, appearance.background)
    }

    @Test
    fun aTapOnTheShadeUnderTheCursorKeepsThatShade() {
        openBackgroundOnTheTypedColour()
        val shade = shadeUnderTheCursor()
        model.onTapPanelItem(model.focus.panelIndex, actions.shown, wide)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM, "the page closed")
        assertEquals(ColorSetting.Custom(shade.value), appearance.background)
    }

    @Test
    fun afterTheCursorMovedAKeepsTheShadeItShows() {
        openBackgroundOnTheTypedColour()
        val from = model.focus.panelIndex
        move(if (from == model.shadeIndex(colourPage, 0)) Direction.Right else Direction.Left)
        assertNotEquals(from, model.focus.panelIndex)
        val shade = shadeUnderTheCursor()
        press(Action.Enter)
        assertEquals(ColorSetting.Custom(shade.value), appearance.background)
    }

    @Test
    fun reopeningAfterAMoveAndBackKeepsTheCurrentColourOnAAtOnce() {
        openBackgroundOnTheTypedColour()
        move(Direction.Right)
        press(Action.Back)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM)
        press(Action.Enter)
        assertEquals(ColorPart.Shade, model.colourPart)
        // The cursor moved on the first visit; the reopened page must not remember it.
        press(Action.Enter)
        assertOnColorsRow(COLORS_BACKGROUND_ITEM, "the page closed")
        assertEquals(typed, appearance.background)
    }

    @Test
    fun movingThenBackBringsTheColourFromBeforeThePageBack() {
        openBackgroundOnTheTypedColour()
        move(Direction.Right)
        assertNotEquals("the move previewed a shade", typed, appearance.background)
        press(Action.Back)
        assertEquals(typed, appearance.background)
    }

    @Test
    fun aTintedTextPresetIsNamedAndAnyOtherColourIsCustom() {
        val black = BackgroundPreset.Black.color
        val tinted = ColorPages.textPresetsOn(black).filterIsInstance<TextColorSetting.Custom>()
        assertTrue("some tinted presets are offered on Black", tinted.size >= 4)
        val names = tinted.map { textColorName(it, black) }
        assertTrue("every one has its own name", names.none { it == R.string.settings_color_custom })
        assertEquals("names are distinct", names.size, names.toSet().size)
        assertEquals(R.string.settings_color_custom, textColorName(TextColorSetting.Custom(0xC8E6C9), black))
        // A preset of the dark side is not offered on Black, so it reads as Custom there.
        val onPaper = ColorPages.textPresetsOn(BackgroundPreset.Paper.color).filterIsInstance<TextColorSetting.Custom>()
        assertTrue(onPaper.isNotEmpty())
        assertEquals(R.string.settings_color_custom, textColorName(onPaper.first(), black))
        assertNotEquals(R.string.settings_color_custom, textColorName(onPaper.first(), BackgroundPreset.Paper.color))
        assertEquals(R.string.settings_text_light, textColorName(TextColorSetting.Light, black))
    }
}
