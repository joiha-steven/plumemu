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
            val dark = TextMode.of(c.text, c.bg) == TextMode.Light
            val slab = FocusSlab.fill(c.bg, c.text, dark)
            check(set, "accent on slab (bar)", ColorMath.contrast(c.accent, slab), 3.5)
            check(set, "strong on slab", ColorMath.contrast(c.strong, slab), 4.5)
            check(set, "text on slab", ColorMath.contrast(c.text, slab), 4.5)
            check(set, "meta on slab", ColorMath.contrast(c.meta, slab), 6.0)
            check(set, "muted on slab", ColorMath.contrast(c.muted, slab), 4.6)
            // The slab must stand off the page: on dark pages its lit pane does; on light ones the pane stays near the page and its white
            // rim does (FocusSlab), so a light page must stay below white.
            if (dark) {
                check(set, "slab against bg", ColorMath.contrast(slab, c.bg), 1.12)
            } else {
                check(set, "slab rim against bg", ColorMath.contrast(FocusSlab.edgeOver(slab, c.bg, dark), c.bg), 1.12)
            }
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
    fun theSeventeenSetsInCycleOrderWithTheirColours() {
        val light = TextMode.Light
        val dark = TextMode.Dark
        val expected =
            listOf(
                ColorSet.Plumemu to Triple(0x161A24, light, 0x3A7CEB),
                ColorSet.Daylight to Triple(0xECEBE5, dark, 0x2A64D8),
                ColorSet.Ultramarine to Triple(0x0F2142, light, 0xF4B740),
                ColorSet.Magenta to Triple(0x240F1C, light, 0xF0649D),
                ColorSet.Peach to Triple(0xFDE3D3, dark, 0x00706B),
                ColorSet.Matcha to Triple(0xE4ECD1, dark, 0x3D6E1C),
                ColorSet.Mocha to Triple(0xF1E6DA, dark, 0xA4492A),
                ColorSet.FrontLoader to Triple(0x26272B, light, 0xF2545D),
                ColorSet.SpiceCube to Triple(0x231C50, light, 0xFF9F43),
                ColorSet.PeaGreen to Triple(0xC6D88F, dark, 0x2F5F2F),
                ColorSet.Pocket to Triple(0xD2D1CA, dark, 0x8C1D4E),
                ColorSet.Lavender to Triple(0xDEDCE6, dark, 0x5B3EA1),
                ColorSet.RedAndGold to Triple(0xF3EBD8, dark, 0x9A1B2F),
                ColorSet.Ivory to Triple(0xF3EAD0, dark, 0x127087),
                ColorSet.ClearGrape to Triple(0x221838, light, 0xBE98FF),
                ColorSet.ColdGrey to Triple(0x272B32, light, 0x8EC3FF),
                ColorSet.Banana to Triple(0xF2CF43, dark, 0x1F45A8),
            )
        assertEquals("enum order is the cycle order", expected.map { it.first }, ColorSet.entries.toList())
        for ((set, colours) in expected) {
            assertEquals("$set background", Rgb(colours.first), set.backgroundColor)
            assertEquals("$set text", colours.second, set.textMode)
            assertEquals("$set accent", Rgb(colours.third), set.accentColor)
        }
    }

    /** The five removed sets' stored looks (background, text, accent) read as the set that replaced them, and `matching` finds it. */
    @Test
    fun everyRemovedSetsLookIsSettledToItsReplacement() {
        fun look(
            background: Int,
            text: TextMode,
            accent: Int,
        ) = PlumThemeSettings(background = BackgroundChoice.Custom(Rgb(background)), text = text.color, accent = AccentChoice.Custom(Rgb(accent)))
        val removed =
            listOf(
                Triple("DotMatrix", look(0xB8E011, TextMode.Dark, 0x2E5E2E), ColorSet.PeaGreen),
                Triple("Super", look(0xD9D8D3, TextMode.Dark, 0xAA2924), ColorSet.Ivory),
                Triple("Indigo", look(0x262158, TextMode.Light, 0xA79CF5), ColorSet.SpiceCube),
                Triple("Charcoal", look(0x2A2B2E, TextMode.Light, 0x5FD38D), ColorSet.ColdGrey),
                Triple("Cream", look(0xE9DFC8, TextMode.Dark, 0x9A1F2B), ColorSet.RedAndGold),
            )
        for ((name, stored, replacement) in removed) {
            assertNull("$name unsettled is Custom", ColorSet.matching(stored))
            val settled = ColorSet.settled(stored)
            assertEquals("$name background", replacement.background, settled.background)
            assertEquals("$name accent", replacement.accent, settled.accent)
            assertEquals("$name reads as ${replacement.name}", replacement, ColorSet.matching(settled))
        }
        // Every value the Dot matrix set had had (two backgrounds, two accents) reads as Pea green.
        for (bg in listOf(0xB8E011, 0xB4DB11)) {
            for (accent in listOf(0x2E5E2E, 0x306130)) {
                assertEquals("DotMatrix $bg $accent", ColorSet.PeaGreen, ColorSet.matching(ColorSet.settled(look(bg, TextMode.Dark, accent))))
            }
        }
        // The Paper page with the Red accent (the taken-out Paper set) is two presets anyone can pick: it stays as chosen.
        val paperPreset = PlumThemeSettings.Paper.copy(accent = AccentChoice.Preset(AccentPreset.Red))
        assertEquals(paperPreset, ColorSet.settled(paperPreset))
        assertNull(ColorSet.matching(paperPreset))
    }

    @Test
    fun aRemovedSetsNameResolvesToItsReplacement() {
        assertNull(ColorSet.named("Paper"))
        assertEquals(ColorSet.PeaGreen, ColorSet.named("DotMatrix"))
        assertEquals(ColorSet.Ivory, ColorSet.named("Super"))
        assertEquals(ColorSet.SpiceCube, ColorSet.named("Indigo"))
        assertEquals(ColorSet.ColdGrey, ColorSet.named("Charcoal"))
        assertEquals(ColorSet.RedAndGold, ColorSet.named("Cream"))
        for (set in ColorSet.entries) assertEquals(set, ColorSet.named(set.name))
        assertNull(ColorSet.named("Nope"))
    }

    /** Plumemu's old looks on Black: the blue and an own-colour red move to it; a Red preset (a deliberate pick) stays. */
    @Test
    fun onlyAnOwnColourRedOnBlackIsMovedToPlumemu() {
        val black = BackgroundChoice.Preset(BackgroundPreset.Black)
        val preset = PlumThemeSettings(background = black, text = TextMode.Light.color, accent = AccentChoice.Preset(AccentPreset.Red))
        assertEquals(preset, ColorSet.settled(preset))
        val own = preset.copy(accent = AccentChoice.Custom(Rgb(0xDA4655)))
        assertEquals(ColorSet.Plumemu, ColorSet.matching(ColorSet.settled(own)))
        assertEquals(ColorSet.Plumemu.background, ColorSet.settled(own).background)
    }

    @Test
    fun theOldDefaultBlackWithBlueSettlesToPlumemu() {
        val old = PlumThemeSettings(background = BackgroundChoice.Preset(BackgroundPreset.Black), text = TextMode.Light.color, accent = AccentChoice.Custom(Rgb(0x3E7BE0)))
        val settled = ColorSet.settled(old)
        assertEquals(ColorSet.Plumemu.background, settled.background)
        assertEquals(ColorSet.Plumemu.accent, settled.accent)
        assertEquals(ColorSet.Plumemu, ColorSet.matching(settled))
    }

    /** Plumemu's old own-colour red on Black still reads as the set and draws the new accent (the default was Black with red before the blue, then the ink). */
    @Test
    fun theOldRedAccentOfThePlumemuSetIsSettledToItsNewOne() {
        val stored = PlumThemeSettings(background = BackgroundChoice.Preset(BackgroundPreset.Black), accent = AccentChoice.Custom(Rgb(0xDA4655)))
        assertEquals(ColorSet.Plumemu.accent, ColorSet.settled(stored).accent)
        assertEquals(ColorSet.Plumemu, ColorSet.matching(ColorSet.settled(stored)))
        // Another background keeps it as stored.
        val onPaper = PlumThemeSettings.Paper.copy(accent = AccentChoice.Custom(Rgb(0xDA4655)))
        assertEquals(onPaper, ColorSet.settled(onPaper))
    }

    /** A background a set had before the dark text became #1C1C1E still reads as the set and draws the current one. */
    @Test
    fun anOldBackgroundIsSettledToTheSetsCurrentOne() {
        for ((set, old) in listOf(ColorSet.Pocket to 0xCDCCC4, ColorSet.Banana to 0xF2CD3C)) {
            val stored = themeOf(set).copy(background = BackgroundChoice.Custom(Rgb(old)))
            assertEquals("$set background", set.background, ColorSet.settled(stored).background)
            assertEquals("$set reads", set, ColorSet.matching(ColorSet.settled(stored)))
            assertNull("$set unsettled is Custom", ColorSet.matching(stored))
            assertNull("$set near", ColorSet.matching(ColorSet.settled(stored.copy(background = BackgroundChoice.Custom(Rgb(old + 1))))))
            // The set's other colours must be the set's too.
            assertEquals(stored.copy(accent = AccentChoice.Custom(Rgb(0x123456))), ColorSet.settled(stored.copy(accent = AccentChoice.Custom(Rgb(0x123456)))))
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
        val typed = PlumThemeSettings(background = BackgroundChoice.Custom(Rgb(0x161A24)), accent = AccentChoice.Custom(Rgb(0x3A7CEB)))
        assertEquals(ColorSet.Plumemu, ColorSet.matching(typed))
        // One of the three changed is no set.
        assertNull("other text", ColorSet.matching(themeOf(ColorSet.Pocket).copy(text = Rgb(0x000000))))
        assertNull("other accent", ColorSet.matching(themeOf(ColorSet.Pocket).copy(accent = AccentChoice.Preset(AccentPreset.Blue))))
        assertNull("other background", ColorSet.matching(PlumThemeSettings.Graphite))
        // Size, animations and the grid are not colours.
        assertEquals(ColorSet.Ivory, ColorSet.matching(themeOf(ColorSet.Ivory).copy(textSize = TextSize.Huge, animationsOn = false)))
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
                ColorSet.FrontLoader to listOf(0xD22A35 to white, 0xD22A35 to white, 0x3A3B40 to white, 0x3A3B40 to white),
                ColorSet.SpiceCube to listOf(0x2EA557 to dark, 0xD93A3E to white, 0xBDBCC8 to dark, 0xBDBCC8 to dark),
                ColorSet.Pocket to listOf(0x8C1D4E to white, 0x8C1D4E to white, 0x8C1D4E to white, 0x8C1D4E to white),
                ColorSet.Lavender to listOf(0x4E3591 to white, 0x4E3591 to white, 0xB2A5DB to dark, 0xB2A5DB to dark),
                ColorSet.RedAndGold to listOf(0x9A1B2F to white, 0x9A1B2F to white, 0xC69A1E to dark, 0xC69A1E to dark),
                ColorSet.Ivory to listOf(0xCF3A3A to white, 0xD9A400 to dark, 0x2F6FDB to white, 0x2E9A47 to dark),
                ColorSet.ColdGrey to listOf(0x3E6FD8 to white, 0x2E9A47 to dark, 0xD9A400 to dark, 0xD9A400 to dark),
                ColorSet.Banana to listOf(0xC8302B to white, 0x0E7C7C to white, 0x926B00 to white, 0x926B00 to white),
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
                // The lowest letter on its fill measures 4.54 (Spice cube's B, rules.md); every letter keeps 4.5.
                assertTrue("$set $letter letter at 4.5:1", ColorMath.contrast(button.letter, button.fill) >= 4.5)
            }
        }
    }

    /** Setup's look on an OLED handheld (true Black under Plumemu's accent) is the Plumemu set, drawn as stored. */
    @Test
    fun plumemuOnTrueBlackIsStillPlumemu() {
        val oled = PlumThemeSettings(background = BackgroundChoice.Preset(BackgroundPreset.Black), text = TextMode.Light.color, accent = ColorSet.Plumemu.accent)
        assertEquals(ColorSet.Plumemu, ColorSet.matching(oled))
        assertEquals("drawn as stored, never moved to the ink", oled, ColorSet.settled(oled))
        val other = oled.copy(accent = AccentChoice.Preset(AccentPreset.Green))
        assertNull(ColorSet.matching(other))
    }
}
