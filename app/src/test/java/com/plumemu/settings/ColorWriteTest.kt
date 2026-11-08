package com.plumemu.settings

import com.plumemu.R
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.screen.textColorName
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.BackgroundPreset
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
        assertEquals(TextColorSetting.Light, Rgb(0xEBE7DD).toTextSetting())
        assertEquals(TextColorSetting.Dark, Rgb(0x1C1C1E).toTextSetting())
        assertEquals(TextColorSetting.Custom(0x123456), Rgb(0x123456).toTextSetting())
        assertEquals(Rgb(0x1C1C1E), TextColorSetting.Dark.toRgb())
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
        assertTrue(Readability.passes(moved, BackgroundPreset.Paper.color, ColorRole.Text))
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

    /** WCAG contrast ratio, written out here so the test does not lean on the code it checks. */
    private fun contrast(
        a: Rgb,
        b: Rgb,
    ): Double {
        fun lum(c: Rgb): Double {
            fun ch(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else Math.pow((it + 0.055) / 1.055, 2.4) }
            return 0.2126 * ch((c.value shr 16) and 0xFF) + 0.7152 * ch((c.value shr 8) and 0xFF) + 0.0722 * ch(c.value and 0xFF)
        }
        val (hi, lo) = lum(a).coerceAtLeast(lum(b)) to lum(a).coerceAtMost(lum(b))
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun aRefusedAccentIsSuggestedByTheReadabilityRuleAndReadsOnThePage() {
        val daylight = black.withColors(ColorSet.Daylight)
        val page = ColorSet.Daylight.backgroundColor
        val refused = daylight.writeAccent(ColorSetting.Custom(0xFFD400)) // yellow on a light page
        assertEquals(daylight, refused.appearance)
        assertEquals(ColorRole.Accent, refused.refusal?.role)
        val suggested = refused.refusal!!.nearest
        assertEquals(Readability.suggest(Rgb(0xFFD400), page, ColorRole.Accent), suggested)
        assertTrue("suggestion reaches 3.5:1 (${contrast(suggested, page)})", contrast(suggested, page) >= 3.5)
        assertTrue(Readability.passes(suggested, page, ColorRole.Accent))
    }

    @Test
    fun aRefusedTextIsSuggestedByTheReadabilityRuleAndReads() {
        val daylight = black.withColors(ColorSet.Daylight)
        val page = ColorSet.Daylight.backgroundColor
        val faint = TextColorSetting.Custom(0xD8D6CC) // barely off the page
        val refused = daylight.writeText(faint)
        assertEquals(ColorRole.Text, refused.refusal?.role)
        val suggested = refused.refusal!!.nearest
        assertEquals(Readability.suggest(Rgb(0xD8D6CC), page, ColorRole.Text), suggested)
        assertTrue(Readability.passes(suggested, page, ColorRole.Text))
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
        val stored = black.copy(background = ColorSetting.Custom(0xD2D1CA)) // Pocket's background with Light text
        val theme = stored.toThemeSettings(animationsOn = true)
        assertEquals(TextMode.Dark, theme.textMode)
        assertEquals(TextColorSetting.Light, stored.textColor)
    }
}
