package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flat look of buttons and switches (docs/design/rules.md > Flat parts) on every colour set, and on every preset background with every
 * accent: pressed steps, the off track's contrast with the page, and the rim's colour. Pure values, no drawing.
 */
class FlatPartLookTest {
    private class Case(
        val name: String,
        val settings: PlumThemeSettings,
    ) {
        val tokens: PlumColors = settings.let { PlumColors.derive(it.backgroundColor, TextMode.of(it.textColor, it.backgroundColor), it.accentColor) }
        val look: FlatPartLook = PlumComposeColors.from(tokens).collar.flat
        val dark: Boolean = TextMode.of(settings.textColor, settings.backgroundColor) != TextMode.Dark
    }

    private fun cases(): List<Case> {
        val out = mutableListOf<Case>()
        for (set in ColorSet.entries) out += Case("$set", PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent))
        for (bg in BackgroundPreset.entries) {
            val mode = TextMode.bestOn(bg.color)
            for (accent in AccentPreset.entries) out += Case("$bg + $accent", PlumThemeSettings(background = BackgroundChoice.Preset(bg), text = mode.color, accent = AccentChoice.Preset(accent)))
        }
        return out
    }

    private fun rgb(c: Color) = Rgb(c.toArgb() and 0xFFFFFF)

    private fun l(c: Color) = ColorMath.lightness(rgb(c))

    private fun contrast(
        a: Color,
        b: Color,
    ) = ColorMath.contrast(rgb(a), rgb(b))

    // WCAG 1.4.11: the parts of a switch that tell its state reach 3:1 against the page. (Replaces the old 1.3:1 floor on the off track's hairline.)
    @Test
    fun theSwitchStatesReachThreeToOneAgainstThePageOnEverySetAndPreset() {
        val failures = mutableListOf<String>()
        for (c in cases()) {
            val bg = Color(c.tokens.bg.toArgb())
            val outline = contrast(c.look.trackEdge, bg)
            val thumb = contrast(c.look.thumbOff, bg)
            val onTrack = contrast(c.look.primary(Color(c.tokens.accent.toArgb())).fill, bg)
            if (outline < FLOOR) failures += "${c.name}: off outline ${"%.2f".format(outline)}:1"
            if (thumb < FLOOR) failures += "${c.name}: off thumb ${"%.2f".format(thumb)}:1"
            if (onTrack < FLOOR) failures += "${c.name}: on track ${"%.2f".format(onTrack)}:1"
        }
        assertTrue("Off outline, off thumb and on track must reach $FLOOR:1 against the page:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** The numbers behind the test above, printed for the work log: the lowest of each per colour set and per preset background (over its accents). */
    @Test
    fun theSwitchNumbersArePrinted() {
        val groups = cases().groupBy { it.name.substringBefore(" + ") }
        for ((name, group) in groups) {
            val outline = group.minOf { contrast(it.look.trackEdge, Color(it.tokens.bg.toArgb())) }
            val onTrack = group.minOf { contrast(it.look.primary(Color(it.tokens.accent.toArgb())).fill, Color(it.tokens.bg.toArgb())) }
            println("SWITCH3 $name: off outline and thumb min %.2f:1, on track min %.2f:1".format(outline, onTrack))
        }
    }

    @Test
    fun aPressedCapIsAStepTowardThePagesContrastSide() {
        val failures = mutableListOf<String>()
        for (c in cases()) {
            val page = Color(c.tokens.bg.toArgb())
            val rest = l(c.look.secondary)
            val down = l(c.look.secondaryPressed)
            val step = if (c.dark) down - rest else rest - down
            if (step < FlatPartLook.PRESS_DELTA - 0.3) failures += "${c.name}: pressed is ${"%.1f".format(step)} L* from the rest fill, toward the wrong side or too near"
            if (!c.dark && l(page) - down < FlatPartLook.PRESS_BELOW_PAGE - 0.3) failures += "${c.name}: pressed is only ${"%.1f".format(l(page) - down)} L* below the page"
            // The primary: the accent, then darker by at least the same step (the white label keeps its contrast).
            val a = c.look.primary(Color(c.tokens.accent.toArgb()))
            val aStep = l(a.fill) - l(a.pressed)
            // An accent already near black cannot go darker by a whole step: it must at least have moved or be within 1 L* of black.
            if (aStep < FlatPartLook.PRESS_DELTA - 0.3 && l(a.pressed) > 1.0) failures += "${c.name}: the accent's pressed fill is only ${"%.1f".format(aStep)} L* darker"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun thePartsRimIsTheCoversRim() {
        // One rim for covers and parts: its colour is FocusLift's, near-white on dark pages and the text ink on light ones.
        for (c in cases()) {
            assertEquals("${c.name}: rim colour", FocusLift.rimColor(c.dark, Color(c.tokens.text.toArgb())), c.look.rim)
        }
        assertEquals(0.96f, FocusLift.RIM_ALPHA, 0f)
        assertEquals(2f, FocusLift.rimWidth.value, 0f)
        assertEquals(2.25f, FocusLift.glowSigma.value, 0f)
    }

    @Test
    fun theRimsReachStaysInsideThePanelRoom() {
        // A Gaussian's tail is 3 sigma: glow (sigma blur / 2) from the rim's outer edge, shadow (dropped) likewise.
        val glow = FocusLift.rimWidth.value + 3 * FocusLift.glowSigma.value
        val shadow = FocusLift.rimWidth.value + FlatPartDimens.shadowDrop.value + 3 * FlatPartDimens.shadowBlur.value / 2
        assertTrue("room ${FlatPartDimens.focusRimRoom} must hold glow $glow and shadow $shadow dp", FlatPartDimens.focusRimRoom.value >= maxOf(glow, shadow))
    }

    private companion object {
        const val FLOOR = 3.0
    }
}
