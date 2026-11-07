package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.FocusHalo
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A focused primary button against a resting one, measured in the ring of pixels just outside the part (where the glow is drawn). On a light theme
 * the groove and the cap are both the accent, so the glow is what tells them apart; the light glow is the accent at [FocusHalo.LIGHT_PART_PEAK] there (the board's 0.30 read back as 0.12 to 0.13 at the edge), which over the
 * background moves a pixel by about that x its distance to the accent at the edge. The board's blur (a Gaussian of sigma 7.3 dp) leaves about a third
 * of that 3 dp out ([GLOW_SHARE_AT_3_DP]); the test asks for it at the brightest ring pixel, on Paper and every light set and preset.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FocusPartGlowTest {
    private companion object {
        const val GLOW_SHARE_AT_3_DP = 0.3f
    }

    @get:Rule
    val compose = createComposeRule()

    private data class Case(
        val name: String,
        val settings: PlumThemeSettings,
    ) {
        val colors: PlumColors get() = settings.let { PlumColors.derive(it.backgroundColor, TextMode.of(it.textColor, it.backgroundColor), it.accentColor) }
        val light get() = TextMode.of(settings.textColor, settings.backgroundColor) == TextMode.Dark
    }

    private fun cases(): List<Case> {
        val out = mutableListOf<Case>()
        for (set in ColorSet.entries) out += Case("$set", PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent))
        for (bg in BackgroundPreset.entries) {
            val mode = TextMode.bestOn(bg.color)
            for (accent in AccentPreset.entries) {
                out += Case("$bg + $accent", PlumThemeSettings(background = BackgroundChoice.Preset(bg), text = mode.color, accent = AccentChoice.Preset(accent)))
            }
        }
        return out
    }

    private fun Color.distance(other: Color) = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

    /** The biggest per-pixel change between a resting and a focused button over the band [inner]..[outer] px outside the button's bounds. */
    private fun ringChange(settings: PlumThemeSettings): Float {
        current.value = settings
        if (!shown) {
            shown = true
            compose.setContent { Frames() }
        }
        compose.waitForIdle()
        return measure()
    }

    // One composition for the whole test: the theme is state, so a case changes it and the same frames redraw.
    private val current = mutableStateOf(PlumThemeSettings.Graphite)
    private var shown = false

    @androidx.compose.runtime.Composable
    private fun Frames() {
        run {
            PlumTheme(current.value) {
                Row(Modifier.background(PlumTheme.colors.bg).padding(40.dp)) {
                    for ((i, focused) in listOf(false, true).withIndex()) {
                        Box(Modifier.padding(end = 80.dp).testTag("frame$i")) {
                            Box(Modifier.padding(30.dp)) { PlumButton("Yes", ButtonKind.Primary, focused, Modifier.testTag("button$i")) }
                        }
                    }
                }
            }
        }
    }

    private fun measure(): Float {
        val frames = (0..1).map { compose.onNodeWithTag("frame$it").captureToImage().toPixelMap() }
        val d = compose.density.density
        val b = compose.onNodeWithTag("button0").fetchSemanticsNode().boundsInRoot
        val f = compose.onNodeWithTag("frame0").fetchSemanticsNode().boundsInRoot
        val part = Rect(b.left - f.left, b.top - f.top, b.right - f.left, b.bottom - f.top)
        // The shadow below the collar is in both; the band is the 3..10 dp ring, whole around.
        var worst = 0f
        for (y in 0 until minOf(frames[0].height, frames[1].height)) {
            for (x in 0 until minOf(frames[0].width, frames[1].width)) {
                val dx = maxOf(part.left - x, x - part.right, 0f)
                val dy = maxOf(part.top - y, y - part.bottom, 0f)
                val out = maxOf(dx, dy)
                if (out < 3 * d || out > 10 * d) continue
                worst = maxOf(worst, frames[0][x, y].distance(frames[1][x, y]))
            }
        }
        return worst
    }

    @Test
    fun aFocusedPrimaryButtonGlowsOnEveryLightTheme() {
        val failures = mutableListOf<String>()
        for (case in cases().filter { it.light }) {
            val c = case.colors
            val toAccent = abs(c.accent.red - c.bg.red) + abs(c.accent.green - c.bg.green) + abs(c.accent.blue - c.bg.blue)
            // The peak is at its Depth; the glow's reach is GLOW_REACH of the board's, so the band 3 dp out, near its edge, keeps that share of it.
            val need = FocusHalo.LIGHT_PART_PEAK.toFloat() * GLOW_SHARE_AT_3_DP * Depth.GLOW_REACH * toAccent / 255f
            val got = ringChange(case.settings)
            println("GLOW ${case.name}: ring change %.3f, wanted %.3f".format(got, need))
            if (got < need) failures += "${case.name}: ring change %.3f < %.3f".format(got, need)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aDarkThemeKeepsItsHaloAsItWas() {
        for (case in cases().filter { !it.light }) {
            val colors = PlumComposeColors.from(case.colors)
            assertEquals("${case.name}: a collar part glows with the halo itself", colors.focusGlow, colors.partGlow)
        }
    }
}
