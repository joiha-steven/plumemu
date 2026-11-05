package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.components.CollarSize
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
 * the cover and its collar shrink together about the cell's centre (the groove stays lit around the dipped box).
 * Read from the pixels around the dipped cover.
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

    private fun Color.near(other: Color): Boolean = abs(red - other.red) < 0.12f && abs(green - other.green) < 0.12f && abs(blue - other.blue) < 0.12f

    /** The picture of cover 0 as laid out, in px. */
    private fun drawnPicture(): Rect {
        val node =
            compose.onNodeWithTag(HomeTags.cover(0), useUnmergedTree = true).fetchSemanticsNode().children
                .first { child -> child.config.any { it.value == HomeTags.COVER_PICTURE } }
        return Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    @Test
    fun theFocusedCoverAndItsCollarShrinkTogether() {
        compose.setContent {
            // The 0.12 colour tolerance below was tuned on a red accent; the test is about where the groove stands, not its colour.
            PlumTheme(PlumThemeSettings.Default.copy(accent = AccentChoice.Preset(AccentPreset.Red))) {
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

        // The focused cover is lifted (drawn larger about its collar's centre) and dips about that same centre, collar and box together:
        // the lit groove stands just outside the box, scaled by both, on every side.
        val s = PlumMotion.PRESS_SCALE * PlumDimens.FOCUSED_COVER_SCALE
        val centre = Offset(drawn.center.x, (drawn.top - CollarSize.Cover.topPadding.value * density + drawn.bottom + CollarSize.Cover.bottomPadding.value * density) / 2f)
        fun dip(v: Float, pivot: Float) = pivot + (v - pivot) * s
        val dipped = Rect(dip(drawn.left, centre.x), dip(drawn.top, centre.y), dip(drawn.right, centre.x), dip(drawn.bottom, centre.y))
        val groove = CollarDimens.coverGroove.value * density * s / 2f
        val sides =
            mapOf(
                "top" to (Offset(dipped.center.x, dipped.top) to Offset(0f, -1f)),
                "bottom" to (Offset(dipped.center.x, dipped.bottom) to Offset(0f, 1f)),
                "left" to (Offset(dipped.left, dipped.center.y) to Offset(-1f, 0f)),
                "right" to (Offset(dipped.right, dipped.center.y) to Offset(1f, 0f)),
            )
        assertNotEquals("the cover dipped", resting.at(Offset(dipped.center.x, dipped.top - 1f)), pressed.at(Offset(dipped.center.x, dipped.top - 1f)))
        for ((side, edgeAndOut) in sides) {
            val (edge, out) = edgeAndOut
            // Not the bottom: the box's drop shadow falls on its groove there.
            if (side == "bottom") continue
            // The groove is about 2.7 px thick and the dipped edge falls on a fraction of a px: the lit groove is within a px of the edge's own place.
            val around = listOf(-1f, 0f, 1f).map { pressed.at(edge + out * (groove + it)) }
            assertTrue("$side: the lit groove dipped with the cover ($around)", around.any { it.near(litGroove(accent)) })
        }
    }
}
