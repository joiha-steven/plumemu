package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.plumemu.coreui.components.CoverGlowColor
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The grid's focused cover dips with the confirm button held (design/rules.md > Shapes and motion > Pressed):
 * the larger copy and its ring shrink together about the cover's centre, and the cell's own copy under them
 * never peeks out (a background plate covers it). Read from the pixels around the dipped cover.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PressFeelGridTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val held = mutableStateOf<Set<String>>(emptySet())
    private var accent = Color.Unspecified
    private var background = Color.Unspecified

    private fun PixelMap.at(point: Offset): Color = this[point.x.roundToInt().coerceIn(0, width - 1), point.y.roundToInt().coerceIn(0, height - 1)]

    /**
     * Every channel lies between the background darkened by the shadow (at most its opacity) and the background lit by the
     * [glow] at most [PlumDimens.COVER_GLOW_ALPHA_DARK] of the way (a little slack for rounding).
     */
    private fun Color.isBackgroundWithGlow(
        bg: Color,
        glow: Color,
    ): Boolean {
        fun ok(mine: Float, base: Float, lit: Float) = mine <= maxOf(base, base + PlumDimens.COVER_GLOW_ALPHA_DARK * (lit - base)) + 0.02f && mine >= base * (1f - PlumDimens.COVER_SHADOW_LIFTED_ALPHA_DARK) - 0.02f
        return ok(red, bg.red, glow.red) && ok(green, bg.green, glow.green) && ok(blue, bg.blue, glow.blue)
    }

    private fun Color.near(other: Color): Boolean = abs(red - other.red) < 0.12f && abs(green - other.green) < 0.12f && abs(blue - other.blue) < 0.12f

    /** The focused picture of cover 0 as drawn at rest (grown about its centre and raised), in px. */
    private fun drawnPicture(): Rect {
        val node =
            compose.onNodeWithTag(HomeTags.cover(0), useUnmergedTree = true).fetchSemanticsNode().children
                .first { child -> child.config.any { it.value == HomeTags.COVER_PICTURE } }
        val scale = PlumDimens.FOCUSED_COVER_SCALE
        val rest = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
        val lift = PlumDimens.focusedLift.value * compose.density.density
        return Rect(rest.center.x - rest.width * scale / 2f, rest.center.y - rest.height * scale / 2f - lift, rest.center.x + rest.width * scale / 2f, rest.center.y + rest.height * scale / 2f - lift)
    }

    @Test
    fun theFocusedCoverAndItsRingShrinkTogetherAndNothingPeeksOut() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                CompositionLocalProvider(LocalHeldKeys provides held) {
                    HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"))
                }
            }
        }
        compose.waitForIdle()
        val density = compose.density.density
        val drawn = drawnPicture()
        val resting = compose.onRoot().captureToImage().toPixelMap()

        compose.runOnIdle {
            held.value = setOf("A")
            Snapshot.sendApplyNotifications()
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(300)
        val pressed = compose.onRoot().captureToImage().toPixelMap()

        // The glow colour as the grid finds it: the picture's average, lifted (sampled inside the cover, clear of its ring).
        val inner = Rect(drawn.left + drawn.width * 0.15f, drawn.top + drawn.height * 0.15f, drawn.right - drawn.width * 0.15f, drawn.bottom - drawn.height * 0.15f)
        val samples = ArrayList<Int>()
        var sy = inner.top
        while (sy < inner.bottom) {
            var sx = inner.left
            while (sx < inner.right) {
                samples += resting.at(Offset(sx, sy)).toArgb()
                sx += 4f
            }
            sy += 4f
        }
        val glow = CoverGlowColor.lifted(checkNotNull(CoverGlowColor.average(samples.toIntArray())))

        val s = PlumMotion.PRESS_SCALE
        val dipped = Rect(drawn.center.x - drawn.width * s / 2f, drawn.center.y - drawn.height * s / 2f, drawn.center.x + drawn.width * s / 2f, drawn.center.y + drawn.height * s / 2f)
        val ring = (PlumDimens.focusRingGap.value + PlumDimens.focusRingWidth.value / 2f) * density * s
        val sides =
            mapOf(
                "top" to (Offset(drawn.center.x, dipped.top) to Offset(0f, -1f)),
                "bottom" to (Offset(drawn.center.x, dipped.bottom) to Offset(0f, 1f)),
                "left" to (Offset(dipped.left, drawn.center.y) to Offset(-1f, 0f)),
                "right" to (Offset(dipped.right, drawn.center.y) to Offset(1f, 0f)),
            )
        for ((side, edgeAndOut) in sides) {
            val (edge, out) = edgeAndOut
            // The way the cover used to reach: now the gap between the dipped picture and its ring.
            val shrunkBy = (if (side == "top" || side == "bottom") drawn.height else drawn.width) * (1f - s) / 2f
            val wasPicture = edge + out * (shrunkBy - 1.5f)
            assertNotEquals("$side: the picture left its old edge", resting.at(wasPicture), pressed.at(wasPicture))
            assertTrue("$side: the ring dipped with the cover", pressed.at(edge + out * ring).near(accent))
            // Every pixel between the dipped picture and the ring is background, or background lit by the focused cover's
            // glow: never what the picture showed there at rest, so no copy of the cell peeks out.
            var offset = 1.5f
            while (offset < ring - PlumDimens.focusRingWidth.value * density) {
                val point = edge + out * offset
                assertTrue("$side: ${offset}px outside the dipped cover is the background or its glow (between them, at most the glow's opacity), not a copy of the cover: ${pressed.at(point)}", pressed.at(point).isBackgroundWithGlow(background, glow))
                offset += 1f
            }
        }
    }
}
