package com.plumemu.coreui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The focused row's slab (`FocusSlab`): the text on it keeps the contrast the rules require (a focused row: 4.5 for the text colours, 6 for meta, 4.6 for muted), its accent bar is seen, and it stands off
 * a resting row by a measured amount on every ready-made set (with its own accent and with every accent preset), every preset
 * background with its best text, and the app's two defaults. The fill and the drop shadow carry it alone.
 */
class FocusSlabReadabilityTest {
    private data class Case(
        val name: String,
        val colors: PlumColors,
    ) {
        val dark get() = TextMode.of(colors.text, colors.bg) == TextMode.Light
        val fill get() = FocusSlab.fill(colors.bg, colors.text, dark)
    }

    private fun cases(): List<Case> {
        val out = mutableListOf<Case>()
        for (set in ColorSet.entries) {
            out += Case("$set (own accent)", PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor))
            for (preset in AccentPreset.entries) {
                out += Case("$set + $preset", PlumColors.derive(set.backgroundColor, set.textMode, preset.colorFor(set.textMode)))
            }
        }
        for (bg in BackgroundPreset.entries) {
            val mode = TextMode.bestOn(bg.color)
            for (preset in AccentPreset.entries) out += Case("$bg + $preset", PlumColors.derive(bg.color, mode, preset.colorFor(mode)))
        }
        for ((settings, mode) in listOf(PlumThemeSettings.Graphite to TextMode.Light, PlumThemeSettings.Paper to TextMode.Dark)) {
            out += Case("default accent on ${settings.backgroundColor}, $mode", PlumColors.derive(settings.backgroundColor, mode))
        }
        return out
    }

    @Test
    fun textOnTheSlabKeepsItsContrast() {
        val failures = mutableListOf<String>()
        for (case in cases()) {
            val c = case.colors
            fun check(
                what: String,
                text: Rgb,
                target: Double,
            ) {
                val ratio = ColorMath.contrast(text, case.fill)
                if (ratio < target) failures += "${case.name}: $what on slab %.2f < %.1f".format(ratio, target)
            }
            check("strong", c.strong, 4.5)
            check("text", c.text, 4.5)
            check("meta", c.meta, 6.0)
            check("muted", c.muted, 4.6)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theAccentBarShowsOnTheSlabAndTheBackground() {
        val failures = mutableListOf<String>()
        for (case in cases()) {
            val onSlab = ColorMath.contrast(case.colors.accent, case.fill)
            if (onSlab < BAR_ON_SLAB_MIN) failures += "${case.name}: bar on slab %.2f < %.1f".format(onSlab, BAR_ON_SLAB_MIN)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The slab is told from a resting row by its fill alone on every theme (no outline). */
    @Test
    fun theSlabStandsOffARestingRowByAMeasuredAmount() {
        val failures = mutableListOf<String>()
        for (case in cases()) {
            val c = case.colors
            val fill = ColorMath.contrast(case.fill, c.bg)
            if (case.dark && fill < FILL_MIN_DARK) failures += "${case.name}: slab fill against bg %.3f < %.2f".format(fill, FILL_MIN_DARK)
            if (!case.dark && fill < FILL_MIN_LIGHT) failures += "${case.name}: light slab fill %.3f < %.2f against bg".format(fill, FILL_MIN_LIGHT)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        /** 2.5 to 2.0 ("lifted card"): the face is lighter, so a preset accent such as Red on a dark set has less contrast on it; the plate itself tells focus. */
        const val BAR_ON_SLAB_MIN = 2.0
        const val FILL_MIN_DARK = 1.10
        const val FILL_MIN_LIGHT = 1.10 // back from 1.08: 70 % white lifts even the lightest sets (L* 93) past it
    }
}
