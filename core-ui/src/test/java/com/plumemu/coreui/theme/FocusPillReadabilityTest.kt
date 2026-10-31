package com.plumemu.coreui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The focused row's pill: accent tint over the background plus a 2 dp accent ring. The text on it keeps
 * the contrast the rules require, and the tint and ring are visible, on every ready-made set (with its own accent and with
 * every accent preset) and on every preset background with both text modes.
 */
class FocusPillReadabilityTest {
    private data class Case(
        val name: String,
        val colors: PlumColors,
    )

    private fun cases(): List<Case> {
        val out = mutableListOf<Case>()
        for (set in ColorSet.entries) {
            out += Case("$set (own accent)", PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor))
            for (preset in AccentPreset.entries) {
                out += Case("$set + $preset", PlumColors.derive(set.backgroundColor, set.textMode, preset.colorFor(set.textMode)))
            }
        }
        // The app's two defaults: Graphite with light text, Paper with dark text, default accent.
        for ((settings, mode) in listOf(PlumThemeSettings.Graphite to TextMode.Light, PlumThemeSettings.Paper to TextMode.Dark)) {
            out += Case("default accent on ${settings.backgroundColor}, $mode", PlumColors.derive(settings.backgroundColor, mode))
        }
        return out
    }

    @Test
    fun textOnThePillKeepsItsContrast() {
        val failures = mutableListOf<String>()
        for ((name, c) in cases()) {
            fun check(
                what: String,
                text: Rgb,
                target: Double,
            ) {
                val ratio = ColorMath.contrast(text, c.pill)
                if (ratio < target) failures += "$name: $what on pill %.2f < %.1f".format(ratio, target)
            }
            check("strong", c.strong, 4.5)
            check("text", c.text, 4.5)
            check("meta", c.meta, 6.0)
            check("muted", c.muted, 4.6)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theRingShowsAgainstTheBackgroundAndThePillIsSeen() {
        val failures = mutableListOf<String>()
        for ((name, c) in cases()) {
            val ring = ColorMath.contrast(c.accent, c.bg)
            if (ring < Readability.ACCENT_MIN_CONTRAST) failures += "$name: ring on bg %.2f < 3.0".format(ring)
            // The ring is inside the pill, so it must also stand off the pill itself.
            val onPill = ColorMath.contrast(c.accent, c.pill)
            if (onPill < ON_PILL_MIN) failures += "$name: ring on pill %.2f < %.1f".format(onPill, ON_PILL_MIN)
            val tint = ColorMath.contrast(c.pill, c.bg)
            if (tint < TINT_MIN) failures += "$name: pill against bg %.3f < %.2f (no visible tint)".format(tint, TINT_MIN)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val ON_PILL_MIN = 2.5
        const val TINT_MIN = 1.05
    }
}
