package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A panel's edge and shadow (decision 0011, point 5): lighter on Paper than on Graphite, never opaque, in every set. */
class PanelLookTest {
    private fun colorsOf(s: PlumThemeSettings) = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor))

    @Test
    fun paperShadowIsLighterThanGraphitesAndNeitherIsHeavy() {
        val graphite = colorsOf(PlumThemeSettings.Graphite)
        val paper = colorsOf(PlumThemeSettings.Paper)
        assertTrue(paper.panelShadow.alpha < graphite.panelShadow.alpha)
        assertTrue(graphite.panelShadow.alpha <= 0.5f)
        assertTrue(paper.panelShadow.alpha <= 0.2f)
    }

    @Test
    fun theEdgeIsTheTextColourFaintInEverySet() {
        for (set in ColorSet.entries) {
            val tokens = PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor)
            val colors = PlumComposeColors.from(tokens)
            assertTrue("${set.name}: edge is faint", colors.panelEdge.alpha in 0.1f..0.2f)
            assertEquals("${set.name}: edge hue is the text's", colors.text.copy(alpha = 1f), colors.panelEdge.copy(alpha = 1f))
        }
    }
}
