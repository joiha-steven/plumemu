package com.plumemu.home

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.layout.boundsInRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
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
 * The focused tile's lift in the Apps grid (docs/design/rules.md › Apps grid), the cover grid's
 * ([HomeCoverLiftTest]) driven by the compose test clock and read from the pixels: the tile focused as the grid
 * opens has its ring at full scale in the first frame with no grow and no sweep; a focus move grows and sweeps;
 * with animations off nothing grows (it snaps) and nothing sweeps; and a grid at rest writes no state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppsGridLiftTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var accent = Color.Unspecified
    private var background = Color.Unspecified

    private fun open(
        focus: Int,
        animationsOn: Boolean = true,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(MediumText.Default.copy(animationsOn = animationsOn)) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                AppsGridBoard(focused = focus, onState = { state = it })
            }
        }
    }

    private fun move(direction: Direction) = compose.runOnUiThread { checkNotNull(state).dispatch(Action.Move(direction)) }

    private fun frame() = compose.mainClock.advanceTimeByFrame()

    /** The tile [index] at rest (its layout place), in px. */
    private fun resting(index: Int): Rect = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** [rest] grown about its centre by [scale] and raised in step with it (the full lift at the full scale), the way the Apps grid draws the focused tile. */
    private fun grown(
        rest: Rect,
        scale: Float,
    ): Rect {
        val lift = PlumDimens.focusedLift.value * compose.density.density * (scale - 1f) / (PlumDimens.FOCUSED_ICON_SCALE - 1f)
        return Rect(rest.center.x - rest.width * scale / 2f, rest.center.y - rest.height * scale / 2f - lift, rest.center.x + rest.width * scale / 2f, rest.center.y + rest.height * scale / 2f - lift)
    }

    private fun ringAt(
        rest: Rect,
        scale: Float,
    ): Boolean {
        val density = compose.density.density
        val top = grown(rest, scale).let { Offset(it.center.x, it.top - (PlumDimens.focusRingGap.value + PlumDimens.focusRingWidth.value / 2f) * density) }
        val p = compose.onRoot().captureToImage().toPixelMap()[top.x.roundToInt(), top.y.roundToInt()]
        return abs(p.red - accent.red) < 0.12f && abs(p.green - accent.green) < 0.12f && abs(p.blue - accent.blue) < 0.12f
    }

    /** The pixels of the focused tile's whole neighbourhood (ring, lifted copy, its shadow), to compare frame with frame. */
    private fun around(rest: Rect): List<Int> {
        val density = compose.density.density
        val area = grown(rest, PlumDimens.FOCUSED_ICON_SCALE)
        val pad = 24f * density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val out = ArrayList<Int>()
        for (y in (area.top - pad).roundToInt()..(area.bottom + pad).roundToInt()) {
            for (x in (area.left - pad).roundToInt()..(area.right + pad).roundToInt()) out += pixels[x.coerceIn(0, pixels.width - 1), y.coerceIn(0, pixels.height - 1)].hashCode()
        }
        return out
    }

    /**
     * [index] is lifted from the first frame: no grow, no sweep. [strict] (the grid just opened): the ring is at full scale in
     * the first frame and every pixel around the tile stays put until the sweep's time has passed. Otherwise (a new grid
     * size or system: the grid fades in and its pictures may be re-picked for the new size) only that no light band crosses it.
     */
    private fun assertLiftedInTheFirstFrame(
        label: String,
        index: Int,
        strict: Boolean,
    ) {
        val rest = resting(index)
        if (strict) {
            assertTrue("$label: the ring is at full scale in the first frame", ringAt(rest, PlumDimens.FOCUSED_ICON_SCALE))
            val first = around(rest)
            compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS - 40L)
            assertEquals("$label: nothing moved after the first frame (no grow, no sweep yet)", first, around(rest))
            compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS.toLong())
            assertEquals("$label: and no sweep played once the rest delay was over", first, around(rest))
        } else {
            compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L + 20L)
            val mid = around(resting(index))
            compose.mainClock.advanceTimeBy(3L * PlumMotion.FOCUS_SWEEP_MS)
            assertEquals("$label: no light band crosses the tile", mid, around(resting(index)))
            assertTrue("$label: the ring is at full scale", ringAt(resting(index), PlumDimens.FOCUSED_ICON_SCALE))
        }
    }

    @Test
    fun theTileFocusedAsTheGridOpensIsLiftedInTheFirstFrame() {
        open(focus = 8)
        frame()
        assertLiftedInTheFirstFrame("opening", 8, strict = true)
    }

    @Test
    fun aFocusMoveGrowsAndThenSweeps() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        val next = resting(9)
        assertTrue("two frames in, the tile is still growing", !ringAt(next, PlumDimens.FOCUSED_ICON_SCALE))
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 40L)
        assertTrue("grown after 150 ms", ringAt(next, PlumDimens.FOCUSED_ICON_SCALE))
        val before = around(next)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L)
        assertNotEquals("the light band is crossing the tile", before, around(next))
    }

    @Test
    fun withAnimationsOffThereIsNoGrowAndNoSweep() {
        open(focus = 8, animationsOn = false)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        val next = resting(9)
        assertTrue("it snaps to full scale", ringAt(next, PlumDimens.FOCUSED_ICON_SCALE))
        val snapped = around(next)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L)
        assertEquals("no sweep halfway through where it would play", snapped, around(next))
        compose.mainClock.advanceTimeBy(5 * PlumMotion.FOCUS_SWEEP_MS.toLong())
        assertEquals("and none after", snapped, around(next))
    }

    @Test
    fun aGridAtRestWritesNoState() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS + PlumMotion.FOCUS_GROW_MS + 500L)
        // The move's own writes are announced lazily: flush them so only what happens while idle is counted.
        repeat(2) {
            compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(500)
        }
        val writes = mutableListOf<Set<Any>>()
        val observer = Snapshot.registerApplyObserver { changed, _ -> writes += changed }
        try {
            compose.mainClock.advanceTimeBy(IDLE_MS)
            frame()
            assertTrue("no state changes while the focus rests (a sweep, a grow or a timer would): ${writes.flatten().map { it.javaClass.name }.distinct()} ${writes.flatten().take(5)}", writes.isEmpty())
        } finally {
            observer.dispose()
        }
    }

    /** How much brighter (0..255, the largest channel) the pixel at ([x], [y]) is than the background. */
    private fun brighterThanBackground(
        x: Float,
        y: Float,
    ): Int {
        val p = compose.onRoot().captureToImage().toPixelMap()[x.roundToInt(), y.roundToInt()]
        return (maxOf(p.red - background.red, p.green - background.green, p.blue - background.blue) * 255f).roundToInt()
    }

    @Test
    fun theFocusedTileGlowsAndTheOthersDoNot() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        val focused = grown(resting(8), PlumDimens.FOCUSED_ICON_SCALE)
        val probe = 6f * compose.density.density
        val glow = brighterThanBackground(focused.center.x, focused.bottom + probe)
        assertTrue("6 dp under the focused tile is at least 20 levels brighter than the background (was $glow)", glow >= 20)
        val other = resting(9)
        val level = brighterThanBackground(other.center.x, other.bottom + probe)
        assertTrue("no glow under another tile (was $level)", level <= 1)
    }

    @Test
    fun theGlowIsWholeAtOnceWithAnimationsOff() {
        open(focus = 8, animationsOn = false)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        val next = grown(resting(9), PlumDimens.FOCUSED_ICON_SCALE)
        assertTrue("the glow is there at once", brighterThanBackground(next.center.x, next.bottom + 6f * compose.density.density) >= 20)
    }

    @Test
    fun noTileIsFramedAtRest() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        // A tile far from the focused one, so its neighbourhood holds no glow: its edge and the gap around it are the background.
        val tile = resting(0)
        val density = compose.density.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        fun level(x: Float, y: Float): Int {
            val p = pixels[x.roundToInt(), y.roundToInt()]
            return (maxOf(abs(p.red - background.red), abs(p.green - background.green), abs(p.blue - background.blue)) * 255f).roundToInt()
        }
        for (dy in listOf(-4f, -1f, 0.5f)) assertTrue("no rim or shadow at the tile's top edge ($dy dp)", level(tile.left + 2 * density, tile.top + dy * density) <= 1)
        for (dx in listOf(-4f, -1f, 0.5f)) assertTrue("no rim or shadow at the tile's left edge ($dx dp)", level(tile.left + dx * density, tile.center.y) <= 1)
        assertTrue("no shadow under the tile", level(tile.left + 2 * density, tile.bottom + 3 * density) <= 1)
    }

    private companion object {
        const val IDLE_MS = 5_000L
    }
}
