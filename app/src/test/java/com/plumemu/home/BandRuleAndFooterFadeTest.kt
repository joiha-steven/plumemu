package com.plumemu.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.BAR_FADE_BOTTOM
import com.plumemu.coreui.components.HINT_KEY_CAP_TAG
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
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
 * Two edges of the games list: the system info band's hairline never draws over the focused row's slab (at rest on the first row
 * and while the slab glides off it), and the bottom fade ends as far above the hint keys as the keys stand above the screen's bottom.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class BandRuleAndFooterFadeBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var state: StripState

    private fun show(
        settings: PlumThemeSettings,
        grid: Boolean = false,
    ) {
        val content = FakeHomeContent.board().withSystemInfo()
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(settings.copy(animationsOn = true)) { HomeAt(content, isMotionOn = true, onState = { state = it }, gridIds = if (grid) setOf(SNES) else emptySet()) }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            state.dispatch(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX))
            state.dispatch(Action.Enter)
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun capture(): Bitmap = compose.onRoot().captureToImage().asAndroidBitmap()

    private fun Int.differs(other: Int) = maxOf(abs((this shr 16 and 255) - (other shr 16 and 255)), abs((this shr 8 and 255) - (other shr 8 and 255)), abs((this and 255) - (other and 255)))

    /**
     * This frame drawn with and without the hairline: in each column across the middle of the list where the slab's top edge (the sharpest step
     * in the frame without the line, from 4 dp above the line to 6 dp below it) stands above the line, how much the line changes the frame. The
     * most over those columns; null when the slab's edge is above the line in none.
     */
    /** The most the line changed a frame anywhere across the middle of the list: proof the frames without it are without it. */
    private var ruleSeen = 0

    private fun ruleThroughSlab(): Int? {
        val with = capture()
        // A process-wide switch: always turned back on, or later tests in this sandbox would run without the line.
        BandRuleShown.isOn = false
        val without =
            try {
                capture()
            } finally {
                BandRuleShown.isOn = true
            }
        val rule = compose.onNodeWithTag(HomeTags.SYSTEM_BAND_RULE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val d = compose.density.density
        val top = (rule.top.value * d).toInt()
        val bottom = (rule.bottom.value * d).toInt().coerceAtLeast(top + 1)
        val from = ((rule.left.value + (rule.right - rule.left).value * 0.3f) * d).toInt()
        val to = ((rule.left.value + (rule.right - rule.left).value * 0.6f) * d).toInt()
        var most: Int? = null
        for (x in from until to) {
            var edge = -1
            var jump = EDGE_STEP
            for (y in top - (4 * d).toInt() until bottom + (6 * d).toInt()) {
                val step = without.getPixel(x, y + 1).differs(without.getPixel(x, y))
                if (step > jump) {
                    jump = step
                    edge = y + 1
                }
            }
            for (y in top until bottom) ruleSeen = maxOf(ruleSeen, with.getPixel(x, y).differs(without.getPixel(x, y)))
            if (edge < 0 || edge >= top) continue
            for (y in top until bottom) most = maxOf(most ?: 0, with.getPixel(x, y).differs(without.getPixel(x, y)))
        }
        return most
    }

    /** Every frame of a glide, [frames] of them, as [move] starts it: the worst the hairline shows through the slab, and how many frames the slab lay over it. */
    private fun glide(
        frames: Int,
        move: () -> Unit,
    ): Pair<Int, Int> {
        compose.mainClock.autoAdvance = false
        compose.runOnIdle(move)
        capture()
        var worst = 0
        var over = 0
        repeat(frames) {
            compose.mainClock.advanceTimeBy(16)
            ruleThroughSlab()?.let {
                worst = maxOf(worst, it)
                over++
            }
        }
        compose.mainClock.autoAdvance = true
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        return worst to over
    }

    private fun theRuleStaysUnderTheSlab(
        settings: PlumThemeSettings,
        label: String,
    ) {
        show(settings)
        assertTrue("$label: the band is shown", compose.onAllNodesWithTag(HomeTags.SYSTEM_BAND_RULE, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val atRest = ruleThroughSlab()
        val (off, offFrames) = glide(GLIDE_FRAMES) { state.dispatch(Action.Move(Direction.Down)) }
        val (back, backFrames) = glide(GLIDE_FRAMES) { state.dispatch(Action.Move(Direction.Up)) }
        assertTrue("$label: the hairline is seen beside the slab in some frame ($ruleSeen)", ruleSeen > SMOOTH)
        assertTrue("$label: the slab lies over the hairline at rest on the first row", atRest != null)
        assertTrue("$label: the slab lies over the hairline in some frames of the glides ($offFrames, $backFrames)", offFrames + backFrames > 0)
        assertTrue("$label: the hairline shows through the slab at rest on the first row ($atRest)", atRest!! <= SMOOTH)
        assertTrue("$label: the hairline shows through the slab as it glides off the first row ($off)", off <= SMOOTH)
        assertTrue("$label: the hairline shows through the slab as it glides back onto the first row ($back)", back <= SMOOTH)
    }

    @Test fun ruleUnderSlabGraphite() = theRuleStaysUnderTheSlab(PlumThemeSettings.Graphite, "Graphite")

    @Test fun ruleUnderSlabPaper() = theRuleStaysUnderTheSlab(PlumThemeSettings.Paper, "Paper")

    @Test fun ruleUnderSlabYellow() = theRuleStaysUnderTheSlab(yellow, "yellow")

    /** The space between the faded rows and the keys' caps is the space under the caps, down to the screen's edge, and nothing of a row shows in it. */
    private fun theFadeEndsAsFarAboveTheKeysAsTheyStandAboveTheEdge(
        settings: PlumThemeSettings,
        label: String,
        grid: Boolean = false,
    ) {
        show(settings, grid)
        val fade = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cap = compose.onAllNodesWithTag(HINT_KEY_CAP_TAG, useUnmergedTree = true)[0].getBoundsInRoot()
        val root = compose.onRoot().getBoundsInRoot()
        val above = (cap.top - fade.bottom).value
        val below = (root.bottom - cap.bottom).value
        assertEquals("$label: gap above the keys $above dp, under them $below dp", below, above, 1f)
        val image = capture()
        val d = compose.density.density
        val background = image.getPixel(2, image.height / 2)
        val list = compose.onNodeWithTag(if (grid) HomeTags.GRID else HomeTags.GAMES, useUnmergedTree = true).getBoundsInRoot()
        var most = 0
        // The upper half of the gap: the keys' collars reach above their caps into the lower half.
        for (y in (fade.bottom.value * d).toInt() + 1 until ((fade.bottom.value + above / 2) * d).toInt()) {
            for (x in (list.left.value * d).toInt() until (list.right.value * d).toInt().coerceAtMost(image.width)) {
                val p = image.getPixel(x, y)
                most = maxOf(most, abs((p shr 16 and 255) - (background shr 16 and 255)), abs((p shr 8 and 255) - (background shr 8 and 255)), abs((p and 255) - (background and 255)))
            }
        }
        assertTrue("$label: a row shows between the fade and the keys (ink $most)", most <= SMOOTH)
    }

    @Test fun fadeGapGraphite() = theFadeEndsAsFarAboveTheKeysAsTheyStandAboveTheEdge(PlumThemeSettings.Graphite, "Graphite")

    @Test fun fadeGapPaper() = theFadeEndsAsFarAboveTheKeysAsTheyStandAboveTheEdge(PlumThemeSettings.Paper, "Paper")

    @Test fun fadeGapYellow() = theFadeEndsAsFarAboveTheKeysAsTheyStandAboveTheEdge(yellow, "yellow")

    @Test fun fadeGapGridGraphite() = theFadeEndsAsFarAboveTheKeysAsTheyStandAboveTheEdge(PlumThemeSettings.Graphite, "Graphite grid", grid = true)

    @Test fun fadeGapGridPaper() = theFadeEndsAsFarAboveTheKeysAsTheyStandAboveTheEdge(PlumThemeSettings.Paper, "Paper grid", grid = true)

    @Test fun fadeGapGridYellow() = theFadeEndsAsFarAboveTheKeysAsTheyStandAboveTheEdge(yellow, "yellow grid", grid = true)

    private companion object {
        /** A step a person does not see as a line: the slab's face changes this little over 1.5 dp. */
        const val SMOOTH = 6

        /** The least step between two px that is the slab's edge, not its glow's soft ramp. */
        const val EDGE_STEP = 10

        const val SNES = "snes"

        /** Frames of 16 ms that cover a glide. */
        const val GLIDE_FRAMES = 16

        /** A custom yellow background, where the hairline showed most. */
        val yellow: PlumThemeSettings =
            Rgb(0xF2CF43).let { PlumThemeSettings.Graphite.copy(background = BackgroundChoice.Custom(it), text = TextMode.bestOn(it).color) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class BandRuleAndFooterFade576Test : BandRuleAndFooterFadeBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class BandRuleAndFooterFade768Test : BandRuleAndFooterFadeBase()
