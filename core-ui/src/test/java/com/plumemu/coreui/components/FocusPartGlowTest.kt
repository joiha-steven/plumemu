package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import com.plumemu.coreui.theme.FlatPartDimens
import com.plumemu.coreui.theme.FocusLift
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
 * A focused button's rim (docs/design/rules.md > Flat parts), measured in pixels on every colour set and every preset with every accent: a
 * resting button has nothing outside its cap, a focused one has a 2 dp rim glued to the cap's edge (no gap) in 96 % white on a dark page and the
 * text ink on a light one, never the accent, and a tight glow that is gone by 12 dp ([FlatPartDimens.focusRimRoom]).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FocusPartGlowTest {
    @get:Rule
    val compose = createComposeRule()

    private data class Case(
        val name: String,
        val settings: PlumThemeSettings,
    ) {
        val colors: PlumColors get() = settings.let { PlumColors.derive(it.backgroundColor, TextMode.of(it.textColor, it.backgroundColor), it.accentColor) }
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

    // One composition for the whole test: the theme is state, so a case changes it and the same frames redraw.
    private val current = mutableStateOf(PlumThemeSettings.Graphite)
    private var shown = false

    @androidx.compose.runtime.Composable
    private fun Frames() {
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

    @Test
    fun aFocusedButtonHasAGluedRimOnEverySetAndNoAccentGlow() {
        val failures = mutableListOf<String>()
        for (case in cases()) {
            current.value = case.settings
            if (!shown) {
                shown = true
                compose.setContent { Frames() }
            }
            compose.waitForIdle()
            val colors = PlumComposeColors.from(case.colors)
            val frames = (0..1).map { compose.onNodeWithTag("frame$it").captureToImage().toPixelMap() }
            val d = compose.density.density
            val b = compose.onNodeWithTag("button0").fetchSemanticsNode().boundsInRoot
            val f = compose.onNodeWithTag("frame0").fetchSemanticsNode().boundsInRoot
            // The button's box holds its cap inside the collar's room; the cap is the pill the rim is glued to.
            val capLeft = (b.left - f.left) + CollarSize.Control.sidePadding.value * d
            val capMidY = (b.top - f.top) + CollarSize.Control.topPadding.value * d + (b.height - (CollarSize.Control.topPadding + CollarSize.Control.bottomPadding).value * d) / 2
            val y = capMidY.toInt()
            val rimWant = colors.collar.flat.rim.compositeOver(colors.bg)
            val rimAt = frames[1][(capLeft - FocusLift.rimWidth.value * d / 2).toInt(), y]
            val gluedAt = frames[1][(capLeft - 0.5f * d).toInt(), y]
            val farAt = (capLeft - (FlatPartDimens.focusRimRoom.value + 1) * d).toInt()
            val rest = frames[0][(capLeft - FocusLift.rimWidth.value * d / 2).toInt(), y]
            if (rimAt.distance(rimWant) > 14f / 255) failures += "${case.name}: rim ${rimAt} wanted $rimWant"
            if (gluedAt.distance(rimWant) > 14f / 255) failures += "${case.name}: a gap between cap and rim: $gluedAt"
            if (rest.distance(colors.bg) > 3f / 255) failures += "${case.name}: the resting button has ink outside its cap: $rest"
            if (rimAt.distance(colors.accent) < 0.2f && colors.accent.distance(rimWant) > 0.4f) failures += "${case.name}: the rim is the accent"
            if (frames[1][farAt, y].distance(colors.bg) > 3f / 255) failures += "${case.name}: the glow still shows ${FlatPartDimens.focusRimRoom} out: ${frames[1][farAt, y]}"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aDarkThemeKeepsItsHaloAsItWas() {
        for (case in cases().filter { TextMode.of(it.settings.textColor, it.settings.backgroundColor) != TextMode.Dark }) {
            val colors = PlumComposeColors.from(case.colors)
            assertEquals("${case.name}: a collar part glows with the halo itself", colors.focusGlow, colors.partGlow)
        }
    }
}
