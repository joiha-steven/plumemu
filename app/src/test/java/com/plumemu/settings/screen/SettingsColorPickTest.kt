package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "+" page opens with its ring on the swatch nearest the current colour, a swatch that was never
 * previewed: A before any move keeps the current colour, a tap on that swatch picks it, and once the
 * cursor moved A picks what it shows. Also the names of the tinted text presets.
 */
internal class SettingsColorPickTest : SettingsModelHarness() {
    private val typed = ColorSetting.Custom(0x123456)

    private fun openBackgroundPlusPage() {
        actions.settings = actions.settings.copy(appearance = appearance.copy(background = typed))
        model.forgetColorWrites()
        openSection(SettingsSection.Appearance)
        focusOption(SettingsOption.Background)
        press(Action.Enter)
        // A custom colour opens the palette page directly.
        assertEquals(PanelPage.BackgroundCustom, model.focus.page)
    }

    @Test
    fun aAtOnceKeepsTheTypedColourInsteadOfTheSwatchUnderTheRing() {
        openBackgroundPlusPage()
        val ring = backgroundGrid[model.focus.panelIndex]
        assertNotEquals("the ring sits on a different colour than the typed one", typed.rgb, ring.value)
        press(Action.Enter)
        assertNull("the page closed", model.focus.page)
        assertEquals(typed, appearance.background)
    }

    @Test
    fun aTapOnTheSwatchUnderTheRingPicksThatSwatch() {
        openBackgroundPlusPage()
        val at = model.focus.panelIndex
        model.onTapPanelItem(at, actions.shown, wide)
        assertNull("the page closed", model.focus.page)
        assertEquals(ColorSetting.Custom(backgroundGrid[at].value), appearance.background)
    }

    @Test
    fun afterTheCursorMovedAPicksTheSwatchItShows() {
        openBackgroundPlusPage()
        val from = model.focus.panelIndex
        move(if (from % ColorPages.PALETTE_COLUMNS == 0) Direction.Right else Direction.Left)
        val at = model.focus.panelIndex
        assertNotEquals(from, at)
        press(Action.Enter)
        assertEquals(ColorSetting.Custom(backgroundGrid[at].value), appearance.background)
    }

    @Test
    fun reopeningPlusAfterAMoveAndBackKeepsTheCurrentColourOnAAtOnce() {
        openBackgroundPlusPage()
        move(Direction.Right)
        press(Action.Back)
        assertEquals(PanelPage.BackgroundPresets, model.focus.page)
        // B puts the focus back on "+", so A opens it (openPlus would also tap a swatch of the page it opens).
        press(Action.Enter)
        assertEquals(PanelPage.BackgroundCustom, model.focus.page)
        // The cursor moved on the first visit; the reopened page must not remember it.
        press(Action.Enter)
        assertNull("the page closed", model.focus.page)
        assertEquals(typed, appearance.background)
    }

    @Test
    fun movingThenBackBringsTheColourFromBeforeThePageBack() {
        openBackgroundPlusPage()
        move(Direction.Right)
        assertNotEquals("the move previewed a swatch", typed, appearance.background)
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
