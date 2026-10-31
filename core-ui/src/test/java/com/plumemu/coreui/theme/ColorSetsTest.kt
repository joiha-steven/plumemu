package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Appearance › Colors' ready-made sets. */
class ColorSetsTest {
    private fun themeOf(set: ColorSet) = PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent)

    /**
     * Every set passes what the presets pass: the readability check Settings shows a note for
     * (the one check, `Readability`: text and accent, and nothing is drawn adjusted), the derived text targets of
     * `PlumColorsTest.contrastTargetsHoldOnBothPresets` (secondary 11:1, meta 6:1 and muted 4.6:1 on
     * the focused row's fill, key-cap borders 3:1), text on the focused row at 4.5:1, the keyboard
     * tray's text at 4.5:1, and badge text on the accent (`onAccent`) at 4.5:1.
     */
    @Test
    fun everySetPassesTheContrastTargetsOfThePresets() {
        val failures = mutableListOf<String>()
        fun check(
            set: ColorSet,
            what: String,
            ratio: Double,
            target: Double,
        ) {
            if (ratio < target) failures += "$set $what %.2f < %.1f".format(ratio, target)
        }
        for (set in ColorSet.entries) {
            val theme = themeOf(set)
            if (!Readability.passes(set.accentColor, set.backgroundColor, ColorRole.Accent)) failures += "$set accent fails Readability"
            if (!Readability.passes(set.textMode.color, set.backgroundColor, ColorRole.Text)) failures += "$set text fails Readability: ${Readability.textFailures(set.textMode.color, set.backgroundColor)}"
            if (theme.textColor != set.textMode.color || theme.accentColor != set.accentColor) failures += "$set is drawn adjusted"
            val c = PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor)
            check(set, "text on bg", ColorMath.contrast(c.text, c.bg), 4.5)
            check(set, "text2 on bg", ColorMath.contrast(c.text2, c.bg), 11.0)
            check(set, "meta on sel", ColorMath.contrast(c.meta, c.sel), 6.0)
            check(set, "muted on sel", ColorMath.contrast(c.muted, c.sel), 4.6)
            check(set, "chip on bg", ColorMath.contrast(c.chip, c.bg), 3.0)
            check(set, "text on sel", ColorMath.contrast(c.text, c.sel), 4.5)
            check(set, "strong on sel", ColorMath.contrast(c.strong, c.sel), 4.5)
            // Sets keep a margin over the presets' floors (theme balance): the focused row's ring reads on
            // the pill and on the background, and a badge's text has room to spare on the accent.
            check(set, "accent on bg (ring)", ColorMath.contrast(c.accent, c.bg), 3.5)
            check(set, "accent on pill (ring)", ColorMath.contrast(c.accent, c.pill), 3.5)
            check(set, "onAccent on accent", ColorMath.contrast(c.onAccent, c.accent), 4.7)
            check(set, "strong on pill", ColorMath.contrast(c.strong, c.pill), 4.5)
            check(set, "text on pill", ColorMath.contrast(c.text, c.pill), 4.5)
            check(set, "meta on pill", ColorMath.contrast(c.meta, c.pill), 6.0)
            check(set, "muted on pill", ColorMath.contrast(c.muted, c.pill), 4.6)
            check(set, "pill against bg", ColorMath.contrast(c.pill, c.bg), 1.12)
            check(set, "text on tray", ColorMath.contrast(c.text, c.tray), 4.5)
            check(set, "text2 on trayKey", ColorMath.contrast(c.text2, c.trayKey), 4.5)
            check(set, "strong on trayKey", ColorMath.contrast(c.strong, c.trayKey), 4.5)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun plumemuIsTodaysDefaultLookExactly() {
        val default = PlumThemeSettings.Default
        assertEquals(default.background, ColorSet.Plumemu.background)
        assertEquals(default.textMode, ColorSet.Plumemu.textMode)
        assertEquals(default.accent, ColorSet.Plumemu.accent)
        assertEquals("a fresh install shows Plumemu", ColorSet.Plumemu, ColorSet.matching(default))
    }

    @Test
    fun theTenSetsInCycleOrderWithTheirColours() {
        assertEquals(
            listOf("Plumemu", "Paper", "Pocket", "DotMatrix", "Super", "Indigo", "Charcoal", "Ivory", "Banana", "Cream"),
            ColorSet.entries.map { it.name },
        )
        val expected =
            mapOf(
                ColorSet.Plumemu to Triple(0x000000, TextMode.Light, 0xDA4655),
                ColorSet.Paper to Triple(0xF4F3EF, TextMode.Dark, 0x9E2A3A),
                ColorSet.Pocket to Triple(0xCDCCC4, TextMode.Dark, 0x8C1D4E),
                ColorSet.DotMatrix to Triple(0xB4DB11, TextMode.Dark, 0x2E5E2E),
                ColorSet.Super to Triple(0xD9D8D3, TextMode.Dark, 0xAA2924),
                ColorSet.Indigo to Triple(0x262158, TextMode.Light, 0xA79CF5),
                ColorSet.Charcoal to Triple(0x2A2B2E, TextMode.Light, 0x5FD38D),
                ColorSet.Ivory to Triple(0xF3EAD0, TextMode.Dark, 0x127087),
                ColorSet.Banana to Triple(0xF2CD3C, TextMode.Dark, 0x1F45A8),
                ColorSet.Cream to Triple(0xE9DFC8, TextMode.Dark, 0x9A1F2B),
            )
        for ((set, colours) in expected) {
            assertEquals("$set background", Rgb(colours.first), set.backgroundColor)
            assertEquals("$set text", colours.second, set.textMode)
            assertEquals("$set accent", Rgb(colours.third), set.accentColor)
        }
    }

    /** Each set's text mode is the one a background change would pick, so the Background row's rule agrees with the set. */
    @Test
    fun eachSetsTextIsTheOneItsBackgroundPicks() {
        for (set in ColorSet.entries) assertEquals("$set", TextMode.bestOn(set.backgroundColor), set.textMode)
    }

    @Test
    fun theCurrentSetIsTheOneWithAllThreeColoursEqual() {
        for (set in ColorSet.entries) assertEquals(set, ColorSet.matching(themeOf(set)))
        // A preset or a typed hex of the same colour is the same set: the colours count, not how they are stored.
        val typed = PlumThemeSettings(background = BackgroundChoice.Custom(Rgb(0x000000)), accent = AccentChoice.Custom(Rgb(0xDA4655)))
        assertEquals(ColorSet.Plumemu, ColorSet.matching(typed))
        // One of the three changed is no set.
        assertNull("other text", ColorSet.matching(themeOf(ColorSet.Pocket).copy(text = Rgb(0x000000))))
        assertNull("other accent", ColorSet.matching(themeOf(ColorSet.Pocket).copy(accent = AccentChoice.Preset(AccentPreset.Blue))))
        assertNull("other background", ColorSet.matching(PlumThemeSettings.Graphite))
        // Font, size, animations and the grid are not colours.
        assertEquals(ColorSet.Super, ColorSet.matching(themeOf(ColorSet.Super).copy(font = PlumFont.Mono, textSize = TextSize.Huge, animationsOn = false)))
    }

    @Test
    fun theSetsAreDistinct() {
        val triples = ColorSet.entries.map { Triple(it.backgroundColor, it.textMode, it.accentColor) }
        assertEquals(triples.size, triples.toSet().size)
    }

    /** Glyph colours by printed letter, and the letter on each fill at 3:1 or more (white or the dark text colour). */
    @Test
    fun buttonGlyphColoursAndTheirLetters() {
        val white = Rgb(0xFFFFFF)
        val dark = TextMode.Dark.color
        val expected =
            mapOf(
                ColorSet.Super to listOf(0xC8302B to white, 0xD9A400 to dark, 0x2B54B5 to white, 0x2E8B4A to white),
                ColorSet.Charcoal to listOf(0x3E6FD8 to white, 0x2E9A47 to dark, 0xD9A400 to dark, 0xD9A400 to dark),
                ColorSet.Ivory to listOf(0xCF3A3A to white, 0xD9A400 to dark, 0x2F6FDB to white, 0x2E9A47 to dark),
                ColorSet.Banana to listOf(0xC8302B to white, 0x0E7C7C to white, 0x926B00 to white, 0x926B00 to white),
                ColorSet.Pocket to listOf(0x8C1D4E to white, 0x8C1D4E to white, 0x8C1D4E to white, 0x8C1D4E to white),
            )
        for (set in ColorSet.entries) {
            val buttons = set.buttons
            val want = expected[set]
            if (want == null) {
                assertNull("$set keeps the usual glyphs", buttons)
                continue
            }
            val got = listOf(buttons!!.a, buttons.b, buttons.x, buttons.y)
            for ((i, button) in got.withIndex()) {
                val letter = "ABXY"[i]
                assertEquals("$set $letter fill", Rgb(want[i].first), button.fill)
                assertEquals("$set $letter letter", want[i].second, button.letter)
                // 4.275: Super Y, the lowest, measures 4.28 to two places (rules.md).
                assertTrue("$set $letter letter at 4.28:1", ColorMath.contrast(button.letter, button.fill) >= 4.275)
            }
        }
    }
}
