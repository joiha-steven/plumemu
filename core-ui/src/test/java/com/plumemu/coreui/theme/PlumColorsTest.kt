package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tokens against the table in docs/design/rules.md › Colour and its contrast targets. */
class PlumColorsTest {
    private val graphite = PlumColors.derive(BackgroundPreset.Graphite.color, TextMode.Light)
    private val paper = PlumColors.derive(BackgroundPreset.Paper.color, TextMode.Dark)

    private fun hexes(c: PlumColors) =
        mapOf(
            "bg" to c.bg, "text" to c.text, "strong" to c.strong, "sel" to c.sel, "sel2" to c.sel2,
            "text2" to c.text2, "meta" to c.meta, "muted" to c.muted, "chip" to c.chip,
            "accent" to c.accent, "onAccent" to c.onAccent,
        ).mapValues { it.value.toHex() }

    @Test
    fun graphiteMatchesTheDesignTable() {
        val expected =
            mapOf(
                "bg" to "#0E0F11", "text" to "#EDEDED", "strong" to "#FFFFFF", "sel" to "#242527",
                "sel2" to "#191A1C", "text2" to "#C5C5C5", "meta" to "#A6A6A7", "muted" to "#8F9091",
                "chip" to "#636365", "accent" to "#DA4655", "onAccent" to "#0E0F11",
            )
        assertEquals(expected, hexes(graphite))
    }

    @Test
    fun paperMatchesTheDesignTable() {
        val expected =
            mapOf(
                "bg" to "#F4F3EF", "text" to "#17181A", "strong" to "#000000", "sel" to "#E0DFDC",
                "sel2" to "#E9E8E4", "text2" to "#323234", "meta" to "#4C4D4D", "muted" to "#5E5E5E",
                "chip" to "#8A8A89", "accent" to "#9E2A3A", "onAccent" to "#F4F3EF",
            )
        assertEquals(expected, hexes(paper))
    }

    /**
     * The keyboard tray (design/rules.md › Derived tokens), values from the keyboard board's code
     * (`ban-phim-rieng/ve-anh-mau.py`: `mix(bg, text, 0.06 / 0.12 / 0.14)`).
     */
    @Test
    fun keyboardTrayTokensMatchTheBoard() {
        fun tray(c: PlumColors) = listOf(c.tray, c.trayKey, c.trayEdge).map { it.toHex() }
        assertEquals(listOf("#1B1C1E", "#292A2B", "#2D2E30"), tray(graphite))
        assertEquals(listOf("#E7E6E2", "#D9D9D5", "#D5D4D1"), tray(paper))
        assertEquals(listOf("#0E0E0E", "#1C1C1C", "#212121"), tray(PlumColors.derive(BackgroundPreset.Black.color, TextMode.Light)))
        assertEquals(listOf("#191F2C", "#262C39", "#2B313D"), tray(PlumColors.derive(BackgroundPreset.Midnight.color, TextMode.Light)))
    }

    /** Mixed towards the text: lighter than a dark background, darker than a light one. */
    @Test
    fun trayIsLighterOnDarkAndDarkerOnLight() {
        for (c in listOf(graphite, PlumColors.derive(BackgroundPreset.Black.color, TextMode.Light))) {
            assertTrue("${c.tray} lighter than ${c.bg}", ColorMath.luminance(c.tray) > ColorMath.luminance(c.bg))
            assertTrue(ColorMath.luminance(c.trayKey) > ColorMath.luminance(c.tray))
            assertTrue(ColorMath.luminance(c.trayEdge) > ColorMath.luminance(c.trayKey))
        }
        assertTrue("${paper.tray} darker than ${paper.bg}", ColorMath.luminance(paper.tray) < ColorMath.luminance(paper.bg))
        assertTrue(ColorMath.luminance(paper.trayKey) < ColorMath.luminance(paper.tray))
        assertTrue(ColorMath.luminance(paper.trayEdge) < ColorMath.luminance(paper.trayKey))
    }

    /**
     * Text on the tray at 4.5:1 (docs/conventions/ui.md) on all eight background presets with the
     * text a background change picks: letters `text` on `tray`, worded keys `text2` on `trayKey`,
     * the focused key `strong` on `trayKey`.
     */
    @Test
    fun keyTextOnTheTrayReaches4p5To1OnEveryPreset() {
        for (preset in BackgroundPreset.entries) {
            val c = PlumColors.derive(preset.color, TextMode.bestOn(preset.color))
            assertTrue("$preset text on tray", ColorMath.contrast(c.text, c.tray) >= 4.5)
            assertTrue("$preset text2 on trayKey", ColorMath.contrast(c.text2, c.trayKey) >= 4.5)
            assertTrue("$preset strong on trayKey", ColorMath.contrast(c.strong, c.trayKey) >= 4.5)
        }
    }

    /**
     * The action key is an accent button with its word in `onAccent` (like the badges). Every accent
     * preset on every background preset reaches 4.5:1. The default red's light-text crimson #DA4655 on the three tinted dark
     * backgrounds was the exception (4.45, 4.38, 4.24 with the background or the text as the word): there
     * `onAccent` falls back to pure black, so the accent itself still stays as picked.
     */
    @Test
    fun theActionKeysWordReaches4p5To1OnItsAccent() {
        for (preset in BackgroundPreset.entries) {
            val mode = TextMode.bestOn(preset.color)
            for (accent in AccentPreset.entries) {
                val c = PlumColors.derive(preset.color, mode, accent.colorFor(mode))
                val ratio = ColorMath.contrast(c.onAccent, c.accent)
                assertTrue("$accent on $preset: $ratio", ratio >= 4.5)
            }
        }
    }

    /** The upward shadow: 45 % black with light text, 7 % with dark text, whatever the background. */
    @Test
    fun trayShadowFollowsTheTextMode() {
        assertEquals(0.45, graphite.trayShadowAlpha, 0.0)
        assertEquals(0.07, paper.trayShadowAlpha, 0.0)
        // Dark text on a dark custom background still casts the faint shadow: the text decides.
        assertEquals(0.07, PlumColors.derive(Rgb(0x202020), TextMode.Dark).trayShadowAlpha, 0.0)
        assertEquals(0.45, PlumColors.derive(BackgroundPreset.Sand.color, TextMode.Light).trayShadowAlpha, 0.0)
    }

    @Test
    fun contrastTargetsHoldOnBothPresets() {
        for (c in listOf(graphite, paper)) {
            assertTrue("text2 ${c.text2} on ${c.bg}", ColorMath.contrast(c.text2, c.bg) >= 11.0)
            assertTrue("meta ${c.meta} on ${c.sel}", ColorMath.contrast(c.meta, c.sel) >= 6.0)
            assertTrue("muted ${c.muted} on ${c.sel}", ColorMath.contrast(c.muted, c.sel) >= 4.6)
            assertTrue("chip ${c.chip} on ${c.bg}", ColorMath.contrast(c.chip, c.bg) >= 3.0)
        }
    }

    @Test
    fun defaultRedIsAbout456To1OnGraphite() {
        assertEquals(4.56, ColorMath.contrast(Rgb(0xDA4655), BackgroundPreset.Graphite.color), 0.01)
    }

    @Test
    fun reachTakesTheSmallestStep() {
        // One 0.02 step back from text2 must fall short of 11:1, or reach did not stop early.
        val c = graphite
        var t = 0.0
        var previous = c.bg
        while (t < 1) {
            val m = ColorMath.mix(c.bg, c.text, t)
            if (m == c.text2) break
            previous = m
            t += 0.02
        }
        assertTrue(ColorMath.contrast(previous, c.bg) < 11.0)
    }

    @Test
    fun plumBackgroundGivesPlumTintedGreys() {
        val plum = PlumColors.derive(BackgroundPreset.Plum.color, TextMode.Light)
        assertNotEquals(graphite.sel, plum.sel)
        // Values from the board's reference code with bg #1A1020.
        assertEquals("#2F2635", plum.sel.toHex())
        assertEquals("#CBCACC", plum.text2.toHex())
        assertEquals("#949097", plum.muted.toHex())
        // Tinted: red and blue above green, like the plum background.
        assertTrue(plum.sel.red > plum.sel.green && plum.sel.blue > plum.sel.green)
    }

    @Test
    fun customAccentIsReturnedUnchanged() {
        // Hard to read on Graphite, still used as picked.
        val murky = Rgb(0x1B1C1E)
        assertEquals(murky, PlumColors.derive(BackgroundPreset.Graphite.color, TextMode.Light, murky).accent)
        val custom = Rgb(0x12AB34)
        assertEquals(custom, PlumColors.derive(BackgroundPreset.Paper.color, TextMode.Dark, custom).accent)
    }

    @Test
    fun onAccentPicksTheHigherContrastOfBackgroundAndText() {
        // A light accent on Graphite: the dark background contrasts more than light text.
        val light = PlumColors.derive(BackgroundPreset.Graphite.color, TextMode.Light, Rgb(0xF0E68C))
        assertEquals(light.bg, light.onAccent)
        // A dark accent on Graphite: the light text wins.
        val dark = PlumColors.derive(BackgroundPreset.Graphite.color, TextMode.Light, Rgb(0x202080))
        assertEquals(dark.text, dark.onAccent)
        // Same rule with dark text on a light background.
        val onPaper = PlumColors.derive(BackgroundPreset.Paper.color, TextMode.Dark, Rgb(0x202080))
        assertEquals(onPaper.bg, onPaper.onAccent)
    }

    @Test
    fun onAccentFallsBackToBlackOnlyWhenBackgroundAndTextBothFallShort() {
        val red = Rgb(0xDA4655)
        // Midnight: its own background (4.45) and the light text both fall short of 4.5, so pure black (4.99) is the word.
        val midnight = PlumColors.derive(BackgroundPreset.Midnight.color, TextMode.Light, red)
        assertEquals(Rgb(0x000000), midnight.onAccent)
        // Black: the background is already black and reaches it, so nothing falls back (the same colour either way).
        val black = PlumColors.derive(BackgroundPreset.Black.color, TextMode.Light, red)
        assertEquals(black.bg, black.onAccent)
        // Graphite reaches 4.56 with its own background, which stays the word.
        val graphite = PlumColors.derive(BackgroundPreset.Graphite.color, TextMode.Light, red)
        assertEquals(graphite.bg, graphite.onAccent)
    }

    @Test
    fun accentPresetsMatchTheDesign() {
        assertEquals(
            listOf("#DA4655", "#E8A13A", "#B583E8", "#6FA8FF", "#5FD38D", "#EDEDED"),
            AccentPreset.entries.map { it.colorFor(TextMode.Light).toHex() },
        )
        assertEquals(
            listOf("#9E2A3A", "#8F5500", "#7A3FC0", "#1F5FBF", "#1A6B3B", "#17181A"),
            AccentPreset.entries.map { it.colorFor(TextMode.Dark).toHex() },
        )
        assertEquals(AccentPreset.Red, AccentPreset.Default)
    }

    @Test
    fun backgroundPresetsMatchTheDesign() {
        assertEquals(
            listOf("#0E0F11", "#000000", "#0B1220", "#1A1020", "#0E1A14", "#F4F3EF", "#EEE6D6", "#E8ECF0"),
            BackgroundPreset.entries.map { it.color.toHex() },
        )
    }

    @Test
    fun hexParsingRejectsWhatIsNotRrggbb() {
        assertEquals(Rgb(0xDA4655), Rgb.fromHexOrNull("#da4655"))
        for (bad in listOf("", "#DA465", "DA4655", "#DA46555", "#GG0000")) {
            assertEquals("'$bad'", null, Rgb.fromHexOrNull(bad))
        }
    }

    @Test
    fun channelsRoundHalvesUpLikeTheBoard() {
        // JavaScript Math.round(0.5) is 1 and Math.round(2.5) is 3; half-to-even would give 0 and 2.
        assertEquals(Rgb(0x010303), Rgb.fromChannels(0.5, 2.5, 2.5))
        assertEquals(Rgb(0xFF0000), Rgb.fromChannels(300.0, -4.0, 0.0))
    }
}
