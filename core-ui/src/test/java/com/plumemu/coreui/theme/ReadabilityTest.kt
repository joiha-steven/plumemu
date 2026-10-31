package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The one readability check and the never-unreadable rules. */
class ReadabilityTest {
    private val light = TextMode.Light.color
    private val dark = TextMode.Dark.color

    private val backgrounds =
        listOf(0x000000, 0x0E0F11, 0x1A1020, 0x262158, 0x2A2B2E, 0x404040, 0x606060, 0x808080, 0xA0A0A0, 0xCDCCC4, 0xE9DFC8, 0xF4F3EF, 0xFFFFFF, 0xB4DB11, 0xF2CD3C, 0x8C1D4E)
            .map(::Rgb)
    private val colours =
        listOf(0xFF0000, 0x00FF00, 0x3366FF, 0xDA4655, 0x5FD38D, 0xA79CF5, 0x808080, 0xC8A060, 0x123456, 0xEDEDED, 0x17181A, 0xFF00FF, 0x2E8B57).map(::Rgb)

    @Test
    fun theTwoPresetsPassOnTheirOwnBackgroundsAndFailOnTheOther() {
        assertTrue(Readability.passes(light, Rgb(0x000000), ColorRole.Text))
        assertTrue(Readability.passes(dark, BackgroundPreset.Paper.color, ColorRole.Text))
        // Today's real bug: Light text on Pocket's #CDCCC4.
        assertFalse(Readability.passes(light, Rgb(0xCDCCC4), ColorRole.Text))
        assertFalse(Readability.passes(dark, BackgroundPreset.Black.color, ColorRole.Text))
    }

    @Test
    fun ownTextNeedsSevenToOneAndSecondaryTextIsTheTextWhereItCannotReachEleven() {
        // #767676 on white is 4.54:1 (the classic AA grey), #5E5E5E 6.4:1: both too weak for a person's own text colour.
        assertFalse(Readability.passes(Rgb(0x767676), Rgb(0xFFFFFF), ColorRole.Text))
        assertFalse(Readability.passes(Rgb(0x5E5E5E), Rgb(0xFFFFFF), ColorRole.Text))
        // #4D4D4D is 8.4:1: bolder than the sets' 11:1, and allowed; its secondary text is the text itself, not a failure.
        assertTrue(Readability.passes(Rgb(0x4D4D4D), Rgb(0xFFFFFF), ColorRole.Text))
        assertEquals(emptyList<String>(), Readability.textFailures(Rgb(0x4D4D4D), Rgb(0xFFFFFF)))
        assertEquals(Rgb(0x4D4D4D), PlumColors.derive(Rgb(0xFFFFFF), Rgb(0x4D4D4D), accent = Rgb(0x4D4D4D)).text2)
        assertTrue(Readability.passes(Rgb(0x333333), Rgb(0xFFFFFF), ColorRole.Text))
        assertTrue(Readability.textFailures(Rgb(0x767676), Rgb(0xFFFFFF)).any { it.startsWith("text on bg") })
    }

    @Test
    fun accentNeedsThreeToOne() {
        assertTrue(Readability.passes(Rgb(0xDA4655), Rgb(0x000000), ColorRole.Accent))
        assertFalse(Readability.passes(Rgb(0x0E0F11), Rgb(0x000000), ColorRole.Accent))
        assertFalse(Readability.passes(Rgb(0xEDEDED), Rgb(0xF4F3EF), ColorRole.Accent))
    }

    @Test
    fun nearestPassingKeepsAPassingColour() {
        assertEquals(Rgb(0x5FD38D), Readability.nearestPassing(Rgb(0x5FD38D), Rgb(0x000000), ColorRole.Accent))
    }

    /** Property-style loop: for every background and colour the result passes (or is the documented fallback) and keeps the hue. */
    @Test
    fun nearestPassingKeepsTheHueAndPassesOrFallsBackToTheBetterPreset() {
        val failures = mutableListOf<String>()
        for (role in ColorRole.entries) {
            for (bg in backgrounds) {
                for (c in colours) {
                    val moved = Readability.nearestPassing(c, bg, role)
                    val fallback = TextMode.bestOn(bg).color
                    if (Readability.passes(moved, bg, role)) {
                        val (h0, s0, l0) = ColorMath.toHsl(c)
                        val (h1, _, l1) = ColorMath.toHsl(moved)
                        // Only colours with some saturation have a hue to keep; ends of the lightness range lose it to rounding.
                        val hueKept = s0 < 0.15 || l1 < 0.08 || l1 > 0.92 || hueDistance(h0, h1) < 0.03
                        if (!hueKept && moved != c) failures += "$role ${c.toHex()} on ${bg.toHex()} -> ${moved.toHex()} lost the hue ($h0 -> $h1)"
                        if (moved != c && abs(l1 - l0) > 1.0) failures += "lightness out of range"
                    } else if (moved != fallback) {
                        failures += "$role ${c.toHex()} on ${bg.toHex()} -> ${moved.toHex()} neither passes nor is the fallback ${fallback.toHex()}"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun nearestPassingMovesTheShortestWay() {
        // A red that is too dark on black moves lighter, and only as far as it must.
        val bg = Rgb(0x000000)
        val moved = Readability.nearestPassing(Rgb(0x300000), bg, ColorRole.Accent)
        assertTrue(Readability.passes(moved, bg, ColorRole.Accent))
        val (_, _, before) = ColorMath.toHsl(Rgb(0x300000))
        val (_, _, after) = ColorMath.toHsl(moved)
        assertTrue(after > before)
        val oneStepLess = ColorMath.fromHsl(ColorMath.toHsl(moved).first, ColorMath.toHsl(Rgb(0x300000)).second, after - 0.01)
        assertFalse("a slightly less light colour would not pass", Readability.passes(oneStepLess, bg, ColorRole.Accent))
    }

    /**
     * Mid-greys: pure black needs a background luminance of 0.5 for the 11:1 secondary text, pure white
     * at most 0.0455, so on #808080 no text colour passes at all. The text falls back to the better
     * of Light and Dark (Dark here), exactly what a background change did before text was free; the
     * accent still has colours that pass (black or white reach 3:1 on any background).
     */
    @Test
    fun onAMidGreyNoTextPassesSoTheBetterPresetIsUsed() {
        val grey = Rgb(0x808080)
        for (c in listOf(light, dark, Rgb(0x000000), Rgb(0xFFFFFF), Rgb(0xFF0000))) {
            assertFalse(Readability.passes(c, grey, ColorRole.Text))
            assertEquals(dark, Readability.nearestPassing(c, grey, ColorRole.Text))
        }
        assertEquals(dark, Readability.settleText(light, grey))
        assertEquals(dark, Readability.settleText(dark, grey))
        val accent = Readability.nearestPassing(Rgb(0x808080), grey, ColorRole.Accent)
        assertTrue(Readability.passes(accent, grey, ColorRole.Accent))
    }

    @Test
    fun aBackgroundChangeKeepsATextThatStillPasses() {
        val custom = Rgb(0xC8E6C9) // pale green on black: well over 11:1
        val settled = Readability.settle(Rgb(0x0E0F11), custom, AccentChoice.Custom(Rgb(0xDA4655)))
        assertEquals(custom, settled.text)
        assertFalse(settled.adjusted)
    }

    @Test
    fun aBackgroundChangeMovesAFailingCustomTextInLightnessOnlyAndSaysSo() {
        val pale = Rgb(0xC8E6C9)
        val settled = Readability.settle(Rgb(0xF4F3EF), pale, AccentChoice.Custom(Rgb(0x9E2A3A)))
        assertTrue(settled.textAdjusted)
        assertTrue(Readability.passes(settled.text, Rgb(0xF4F3EF), ColorRole.Text))
        val (h0, _, l0) = ColorMath.toHsl(pale)
        val (h1, _, l1) = ColorMath.toHsl(settled.text)
        assertTrue("same green hue", hueDistance(h0, h1) < 0.03)
        assertTrue("moved darker", l1 < l0)
        assertFalse(settled.accentAdjusted)
    }

    @Test
    fun aPresetTextThatFailsBecomesTheOtherPreset() {
        assertEquals(dark, Readability.settle(Rgb(0xF4F3EF), light, AccentChoice.Preset(AccentPreset.Red)).text)
        assertEquals(light, Readability.settle(Rgb(0x000000), dark, AccentChoice.Preset(AccentPreset.Red)).text)
        assertTrue(Readability.settle(Rgb(0xF4F3EF), light, AccentChoice.Preset(AccentPreset.Red)).textAdjusted)
    }

    @Test
    fun aFailingAccentMovesAndIsReported() {
        val settled = Readability.settle(Rgb(0x000000), light, AccentChoice.Custom(Rgb(0x100000)))
        assertTrue(settled.accentAdjusted)
        val moved = (settled.accent as AccentChoice.Custom).color
        assertTrue(Readability.passes(moved, Rgb(0x000000), ColorRole.Accent))
    }

    @Test
    fun aPresetAccentFollowsTheTextModeWithoutBeingCalledAdjusted() {
        val settled = Readability.settle(Rgb(0xF4F3EF), light, AccentChoice.Preset(AccentPreset.Red))
        assertFalse(settled.accentAdjusted)
        assertEquals(AccentChoice.Preset(AccentPreset.Red), settled.accent)
    }

    @Test
    fun everyColourSetPassesAndNothingIsAdjusted() {
        for (set in ColorSet.entries) {
            val settled = Readability.settle(set.backgroundColor, set.textMode.color, set.accent)
            assertFalse("$set", settled.adjusted)
            assertEquals("$set", set.textMode, TextMode.of(set.textMode.color, set.backgroundColor))
        }
    }

    @Test
    fun theModeFollowsWhichIsLighter() {
        assertEquals(TextMode.Light, TextMode.of(Rgb(0xC8E6C9), Rgb(0x000000)))
        assertEquals(TextMode.Dark, TextMode.of(Rgb(0x203020), Rgb(0xF4F3EF)))
        assertNotEquals(TextMode.of(Rgb(0x808080), Rgb(0x000000)), TextMode.of(Rgb(0x808080), Rgb(0xFFFFFF)))
    }

    // Draw time: the same adjustment, without rewriting what is stored.

    @Test
    fun lightTextOnPocketsBackgroundIsDrawnDark() {
        val stored = PlumThemeSettings(background = BackgroundChoice.Custom(Rgb(0xCDCCC4)), text = light)
        assertEquals("what is stored stays", light, stored.text)
        assertEquals(dark, stored.textColor)
        assertEquals(TextMode.Dark, stored.textMode)
        // The preset accent follows the drawn mode (Red with dark text).
        assertEquals(Rgb(0x9E2A3A), stored.accentColor)
    }

    @Test
    fun aCustomStoredTextThatFailsIsDrawnMovedAndAPassingOneIsDrawnAsStored() {
        val bg = BackgroundChoice.Custom(Rgb(0xF4F3EF))
        val pale = PlumThemeSettings(background = bg, text = Rgb(0xC8E6C9))
        assertNotEquals(Rgb(0xC8E6C9), pale.textColor)
        assertTrue(Readability.passes(pale.textColor, Rgb(0xF4F3EF), ColorRole.Text))
        val fine = PlumThemeSettings(background = bg, text = Rgb(0x203020))
        assertEquals(Rgb(0x203020), fine.textColor)
    }

    @Test
    fun anAccentBelowThreeToOneIsDrawnMovedButStoredAsIs() {
        val stored = PlumThemeSettings(accent = AccentChoice.Custom(Rgb(0x0E0F11)))
        assertEquals(AccentChoice.Custom(Rgb(0x0E0F11)), stored.accent)
        assertTrue(Readability.passes(stored.accentColor, Rgb(0x000000), ColorRole.Accent))
        assertNotEquals(Rgb(0x0E0F11), stored.accentColor)
    }

    @Test
    fun aSetMatchesWhateverIsDrawn() {
        val pocketWithStoredLight = PlumThemeSettings(background = ColorSet.Pocket.background, text = light, accent = ColorSet.Pocket.accent)
        assertEquals("drawn exactly as Pocket", ColorSet.Pocket, ColorSet.matching(pocketWithStoredLight))
    }

    @Test
    fun aMiddleBackgroundAllowsNoTextAndTheNearestUsableOneKeepsItsHueAndIsLighterOrDarkerOnly() {
        assertFalse(Readability.backgroundAllowsText(Rgb(0x808080)))
        assertFalse(Readability.backgroundAllowsText(Rgb(0x6A7F3A)))
        for (preset in BackgroundPreset.entries) assertTrue("${preset.name} allows text", Readability.backgroundAllowsText(preset.color))
        assertEquals(Rgb(0x101820), Readability.nearestUsableBackground(Rgb(0x101820)))
        for (mid in listOf(0x808080, 0x6A7F3A, 0x3A6F8F, 0xA0A0A0).map(::Rgb)) {
            val near = Readability.nearestUsableBackground(mid)
            assertTrue("${near.toHex()} for ${mid.toHex()}", Readability.backgroundAllowsText(near))
            val (h0, s0, _) = ColorMath.toHsl(mid)
            val (h1, _, _) = ColorMath.toHsl(near)
            assertTrue("hue kept for ${mid.toHex()}", s0 < 0.15 || hueDistance(h0, h1) < 0.03)
        }
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b)
        return minOf(d, 1 - d)
    }
}
