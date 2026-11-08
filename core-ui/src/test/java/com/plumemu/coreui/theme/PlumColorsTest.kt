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
                "bg" to "#111214", "text" to "#EBE7DD", "strong" to "#FFFFFF", "sel" to "#272728",
                "sel2" to ToneLadder.level(Rgb(0x111214), ToneLadder.SURFACE).toHex(), "text2" to "#CCC9C1", "meta" to "#AAA7A1", "muted" to "#94928D",
                "chip" to "#646360", "accent" to "#DA4655", "onAccent" to "#000000",
            )
        assertEquals(expected, hexes(graphite))
    }

    @Test
    fun paperMatchesTheDesignTable() {
        val expected =
            mapOf(
                "bg" to "#ECE8DF", "text" to "#1C1C1E", "strong" to "#0F0F10", "sel" to "#D9D6CE",
                "sel2" to ToneLadder.level(Rgb(0xECE8DF), ToneLadder.SURFACE).toHex(), "text2" to "#2D2C2D", "meta" to "#4A4948", "muted" to "#5A5958",
                "chip" to "#84827E", "accent" to "#9E2A3A", "onAccent" to "#ECE8DF",
            )
        assertEquals(expected, hexes(paper))
    }

    /** The info line's subtitle is the boards' `text2`: #C9C5BB on Graphite, #4A4740 on Paper (within 5 per channel), and readable on every preset. */
    @Test
    fun infoMetaIsTheBoardsText2() {
        fun near(what: String, want: Int, got: Rgb) {
            val w = Rgb(want)
            for ((a, b) in listOf(w.red to got.red, w.green to got.green, w.blue to got.blue)) assertTrue("$what ${got.toHex()} vs ${w.toHex()}", kotlin.math.abs(a - b) <= 5)
        }
        near("graphite", 0xC9C5BB, graphite.infoMeta)
        near("paper", 0x4A4740, paper.infoMeta)
        for (preset in BackgroundPreset.entries) {
            val c = PlumColors.derive(preset.color, TextMode.bestOn(preset.color))
            assertTrue("$preset infoMeta on the page", ColorMath.contrast(c.infoMeta, c.bg) >= 4.5)
        }
    }

    /**
     * The keyboard tray (design/rules.md › Derived tokens): the tone ladder's surface step, its keys the top step, on dark and light pages;
     * only the hairline is still mixed toward the text (`mix(bg, text, 0.14)`).
     */
    @Test
    fun keyboardTrayTokensFollowTheToneLadder() {
        val pages =
            listOf(
                graphite to "#313438",
                paper to "#CFCBC4",
                PlumColors.derive(BackgroundPreset.Black.color, TextMode.Light) to "#232528",
                PlumColors.derive(BackgroundPreset.Midnight.color, TextMode.Light) to "#2C3443",
            )
        for ((c, edge) in pages) {
            assertEquals("tray on ${c.bg}", ToneLadder.level(c.bg, ToneLadder.SURFACE).toHex(), c.tray.toHex())
            assertEquals("trayKey on ${c.bg}", ToneLadder.level(c.bg, ToneLadder.TOP).toHex(), c.trayKey.toHex())
            assertEquals("trayEdge on ${c.bg}", edge, c.trayEdge.toHex())
        }
    }

    /** The tray and its keys are the page moved up the ladder (lighter on dark and light pages alike), the key lighter than the tray; the hairline still goes toward the text. */
    @Test
    fun trayIsLighterThanThePageAndTheKeysLighterStill() {
        for (c in listOf(graphite, paper, PlumColors.derive(BackgroundPreset.Black.color, TextMode.Light))) {
            assertTrue("${c.tray} lighter than ${c.bg}", ColorMath.luminance(c.tray) > ColorMath.luminance(c.bg))
            assertTrue("${c.trayKey} lighter than ${c.tray}", ColorMath.luminance(c.trayKey) > ColorMath.luminance(c.tray))
        }
        for (c in listOf(graphite, PlumColors.derive(BackgroundPreset.Black.color, TextMode.Light))) {
            assertTrue(ColorMath.luminance(c.trayEdge) > ColorMath.luminance(c.trayKey))
        }
        assertTrue("${paper.trayEdge} darker than ${paper.tray}", ColorMath.luminance(paper.trayEdge) < ColorMath.luminance(paper.tray))
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
        assertEquals(0.45 * Depth.SHADOW, graphite.trayShadowAlpha, 0.0)
        assertEquals(0.07 * Depth.SHADOW, paper.trayShadowAlpha, 0.0)
        // Dark text on a dark custom background still casts the faint shadow: the text decides.
        assertEquals(0.07 * Depth.SHADOW, PlumColors.derive(Rgb(0x202020), TextMode.Dark).trayShadowAlpha, 0.0)
        assertEquals(0.45 * Depth.SHADOW, PlumColors.derive(BackgroundPreset.Sand.color, TextMode.Light).trayShadowAlpha, 0.0)
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
    fun defaultRedIsAbout445To1OnGraphite() {
        assertEquals(4.45, ColorMath.contrast(Rgb(0xDA4655), BackgroundPreset.Graphite.color), 0.01)
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
        assertEquals("#2F2633", plum.sel.toHex())
        assertEquals("#CEC9C3", plum.text2.toHex())
        assertEquals("#979191", plum.muted.toHex())
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
        // Graphite (#111214, the board's) reaches 4.45 with its own background, so it falls back to black like Midnight.
        val graphite = PlumColors.derive(BackgroundPreset.Graphite.color, TextMode.Light, red)
        assertEquals(Rgb(0x000000), graphite.onAccent)
    }

    @Test
    fun accentPresetsMatchTheDesign() {
        assertEquals(
            listOf("#DA4655", "#E8A13A", "#B583E8", "#6FA8FF", "#5FD38D", "#EBE7DD"),
            AccentPreset.entries.map { it.colorFor(TextMode.Light).toHex() },
        )
        assertEquals(
            listOf("#9E2A3A", "#8F5500", "#7A3FC0", "#1F5FBF", "#1A6B3B", "#1C1C1E"),
            AccentPreset.entries.map { it.colorFor(TextMode.Dark).toHex() },
        )
        assertEquals(AccentPreset.Red, AccentPreset.Default)
    }

    @Test
    fun backgroundPresetsMatchTheDesign() {
        assertEquals(
            listOf("#111214", "#000000", "#0B1220", "#1A1020", "#0E1A14", "#ECE8DF", "#EEE6D6", "#E8ECF0"),
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
