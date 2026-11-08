package com.plumemu.coreui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
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
 * The flat button measured in pixels on every colour set (docs/design/rules.md > Flat parts): a label-less cap of one solid fill (every row of
 * a column the same: no gradient, no highlight, no thickness band), a hairline on its edge, a 1 dp drop shadow under it on a light page and
 * none on a dark one, and a press that changes the fill and moves nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PlumButtonFlatTest {
    @get:Rule
    val compose = createComposeRule()

    private val current = mutableStateOf(PlumThemeSettings.Graphite)
    private var shown = false

    @Composable
    private fun Frames() {
        PlumTheme(current.value) {
            Row(Modifier.background(PlumTheme.colors.bg).padding(24.dp)) {
                // rest and pressed: secondary then primary
                for ((i, kind) in listOf(ButtonKind.Secondary, ButtonKind.Primary).withIndex()) {
                    Box(Modifier.padding(end = 24.dp).testTag("rest$i")) { PlumButton("", kind, focused = false, modifier = Modifier.width(100.dp)) }
                    Box(Modifier.padding(end = 24.dp).testTag("down$i")) {
                        val held = rememberKeyState()
                        LaunchedEffect(held) { held.dip.progress.snapTo(1f) }
                        PlumButton("", kind, focused = false, modifier = Modifier.width(100.dp), state = held)
                    }
                }
            }
        }
    }

    private fun Color.dist(o: Color) = abs(red - o.red) + abs(green - o.green) + abs(blue - o.blue)

    private fun colorsOf(s: PlumThemeSettings) = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, TextMode.of(s.textColor, s.backgroundColor), s.accentColor))

    @Test
    fun theFlatCapIsOneSolidFillWithAHairlineOnEverySet() {
        val failures = mutableListOf<String>()
        for (set in ColorSet.entries) {
            val settings = PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent)
            current.value = settings
            if (!shown) {
                shown = true
                compose.setContent { Frames() }
            }
            compose.waitForIdle()
            val colors = colorsOf(settings)
            val flat = colors.collar.flat
            val d = compose.density.density
            for (kind in 0..1) {
                val rest = compose.onNodeWithTag("rest$kind").captureToImage().toPixelMap()
                val down = compose.onNodeWithTag("down$kind").captureToImage().toPixelMap()
                val w = rest.width
                val x = w / 2
                val a = flat.primary(colors.accent)
                val fillRest = if (kind == 1) a.fill else flat.secondary
                val fillDown = if (kind == 1) a.pressed else flat.secondaryPressed
                val edge = if (kind == 1) a.edge else flat.secondaryEdge
                // The cap inside the collar's room: the centre column's solid run.
                val rows = (0 until rest.height).filter { rest[x, it].dist(fillRest) < 3f / 255 }
                val name = "${set.name} ${if (kind == 1) "primary" else "secondary"}"
                if (rows.size < 20 * d) {
                    failures += "$name: no solid cap in the centre column (${rows.size} rows)"
                    continue
                }
                if (rows.last() - rows.first() + 1 != rows.size) failures += "$name: the cap's fill is not one run (a gradient or a band): rows ${rows.first()}..${rows.last()} of ${rows.size}"
                // The hairline: the pixel row just above the solid run is the edge colour.
                val hair = rest[x, rows.first() - 1]
                if (hair.dist(edge) > 14f / 255) failures += "$name: hairline above the cap is $hair, wanted $edge"
                // The shadow: a light page has one under the cap, a dark page does not (the page's own colour two rows under the edge).
                val under = rest[x, rows.last() + 3]
                val bg = colors.bg
                if (flat.dark && under.dist(bg) > 3f / 255) failures += "$name: a dark page has a shadow under the cap: $under"
                if (!flat.dark && kind == 0 && under.dist(bg) < 2f / 255 && rest[x, rows.last() + 2].dist(bg) < 2f / 255) failures += "$name: a light page has no shadow under the cap"
                // Pressed: the fill changes to the pressed tone and nothing moves (the same rows).
                val downRows = (0 until down.height).filter { down[x, it].dist(fillDown) < 3f / 255 }
                if (downRows.isEmpty()) failures += "$name: no pressed fill found"
                else if (downRows.first() != rows.first() || downRows.last() != rows.last()) failures += "$name: the cap moved when pressed: ${rows.first()}..${rows.last()} to ${downRows.first()}..${downRows.last()}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theFlatLookIsKeptByTheSetsOwnTokens() {
        val colors = colorsOf(PlumThemeSettings.Graphite)
        assertEquals(true, colors.collar.flat.dark)
        assertEquals(false, colorsOf(PlumThemeSettings.Paper).collar.flat.dark)
    }
}
