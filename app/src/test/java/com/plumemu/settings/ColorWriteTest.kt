package com.plumemu.settings

import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.screen.textColorName
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The writes of Background, Text color and Accent color. */
class ColorWriteTest {
    private val black = AppearanceSettings()
    private val paper = ColorSetting.Preset("Paper")
    private val pale = TextColorSetting.Custom(0xC8E6C9)

    @Test
    fun customTextIsStoredAsHexAndMapsToTheThemeExactly() {
        val a = black.copy(textColor = pale)
        assertEquals(Rgb(0xC8E6C9), a.toThemeSettings(animationsOn = true).text)
        assertEquals(Rgb(0xC8E6C9), a.toThemeSettings(animationsOn = true).textColor)
        assertEquals(TextColorSetting.Light, Rgb(0xEDEDED).toTextSetting())
        assertEquals(TextColorSetting.Dark, Rgb(0x17181A).toTextSetting())
        assertEquals(TextColorSetting.Custom(0x123456), Rgb(0x123456).toTextSetting())
        assertEquals(Rgb(0x17181A), TextColorSetting.Dark.toRgb())
    }

    @Test
    fun aBackgroundKeepsATextThatStillReadsAndReportsNothing() {
        val write = black.copy(textColor = pale).writeBackground(ColorSetting.Preset("Midnight"))
        assertEquals(pale, write.appearance.textColor)
        assertEquals(ColorSetting.Preset("Midnight"), write.appearance.background)
        assertFalse(write.adjusted)
    }

    @Test
    fun aBackgroundMovesAFailingCustomTextAndReportsIt() {
        val write = black.copy(textColor = pale).writeBackground(paper)
        assertTrue(write.textAdjusted)
        val moved = write.appearance.textColor.toRgb()
        assertNotEquals(Rgb(0xC8E6C9), moved)
        assertTrue(Readability.passes(moved, Rgb(0xF4F3EF), ColorRole.Text))
        assertEquals("the accent preset is kept", black.accent, write.appearance.accent)
    }

    @Test
    fun aBackgroundTurnsAFailingPresetTextIntoTheOtherPreset() {
        val write = black.writeBackground(paper)
        assertEquals(TextColorSetting.Dark, write.appearance.textColor)
        assertTrue(write.textAdjusted)
        assertEquals(TextColorSetting.Light, write.appearance.writeBackground(ColorSetting.Preset("Black")).appearance.textColor)
    }

    @Test
    fun aBackgroundMovesAnAccentThatNoLongerReadsAndKeepsOneThatDoes() {
        val darkAccent = black.copy(accent = ColorSetting.Custom(0x1F3A5F)) // reads on Paper, not on Black
        val onBlack = darkAccent.writeBackground(ColorSetting.Preset("Black"))
        assertTrue(onBlack.accentAdjusted)
        assertTrue(Readability.passes(onBlack.appearance.toThemeSettings(true).accentColor, Rgb(0x000000), ColorRole.Accent))
        val kept = black.copy(accent = ColorSetting.Custom(0xDA4655)).writeBackground(ColorSetting.Preset("Midnight"))
        assertFalse(kept.accentAdjusted)
        assertEquals(ColorSetting.Custom(0xDA4655), kept.appearance.accent)
    }

    @Test
    fun aTextPickThatDoesNotReadIsRefusedWithTheNearestOneThatDoes() {
        val refused = black.writeText(TextColorSetting.Dark)
        assertEquals(black, refused.appearance)
        assertEquals(ColorRole.Text, refused.refusal?.role)
        val darkOnBlack = black.writeText(TextColorSetting.Custom(0x305030))
        assertNotEquals(null, darkOnBlack.refusal)
        assertTrue(Readability.passes(darkOnBlack.refusal!!.nearest, Rgb(0x000000), ColorRole.Text))
        val accepted = black.writeText(pale)
        assertNull(accepted.refusal)
        assertEquals(pale, accepted.appearance.textColor)
    }

    @Test
    fun anAccentBelowThreeToOneIsRefusedAndOneAboveIsStored() {
        val refused = black.writeAccent(ColorSetting.Custom(0x1A2B3C))
        assertEquals(black, refused.appearance)
        assertEquals(ColorRole.Accent, refused.refusal?.role)
        assertTrue(Readability.passes(refused.refusal!!.nearest, Rgb(0x000000), ColorRole.Accent))
        assertEquals(ColorSetting.Custom(0x5A8BCF), black.writeAccent(ColorSetting.Custom(0x5A8BCF)).appearance.accent)
    }

    @Test
    fun everySetsColoursPassEveryWriteUnchanged() {
        for (set in ColorSet.entries) {
            val a = black.withColors(set)
            assertEquals("$set", set, a.colorSet())
            val again = a.writeBackground(a.background)
            assertFalse("$set", again.adjusted)
            assertNull("$set text", a.writeText(a.textColor).refusal)
            assertNull("$set accent", a.writeAccent(a.accent).refusal)
        }
    }

    @Test
    fun theTextRowReadsCustomForAnOwnColour() {
        val settings = PlumSettings(appearance = black.copy(textColor = pale))
        assertEquals(R.string.settings_color_custom, textColorName(settings.appearance.textColor))
        assertEquals(R.string.settings_text_light, textColorName(TextColorSetting.Light))
    }

    @Test
    fun aStoredFailingComboIsDrawnAdjustedAndTheFileIsNotRewritten() {
        val stored = black.copy(background = ColorSetting.Custom(0xCDCCC4)) // Pocket's background with Light text
        val theme = stored.toThemeSettings(animationsOn = true)
        assertEquals(TextMode.Dark, theme.textMode)
        assertEquals(TextColorSetting.Light, stored.textColor)
    }
}
