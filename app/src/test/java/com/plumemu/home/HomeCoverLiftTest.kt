package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
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
 * The focused cover's lift in the cover grid (docs/design/rules.md › Grid), driven by the
 * compose test clock and read from the pixels: the cover focused as the grid opens (also on a system switch
 * and a new grid size) has its ring at full scale in the first frame with no grow and no sweep; a focus move
 * grows and sweeps; with animations off nothing grows (it snaps) and nothing sweeps; and a grid at rest
 * writes no state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeCoverLiftTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var gridSize by mutableStateOf(GridSize.Medium)
    private var accent = Color.Unspecified
    private var background = Color.Unspecified
    private var columns = 0

    private fun open(
        focus: Int,
        animationsOn: Boolean = true,
        gridIds: Set<String> = setOf("snes"),
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(MediumText.Default.copy(gridSize = gridSize, animationsOn = animationsOn)) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                columns = PlumTheme.layout.gridColumns(com.plumemu.coreui.theme.CoverShape.Landscape)
                HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, focus), gridIds = gridIds, onState = { state = it })
            }
        }
    }

    private fun move(direction: Direction) = compose.runOnUiThread { checkNotNull(state).dispatch(Action.Move(direction)) }

    private fun frame() = compose.mainClock.advanceTimeByFrame()

    /** The drawn picture of cover [index] at rest (its layout place), in px. */
    private fun resting(index: Int): Rect {
        val node =
            compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode().children
                .first { child -> child.config.any { it.value == HomeTags.COVER_PICTURE } }
        return Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    /** [rest] grown about its centre by [scale] and raised in step with it (the full lift at the full scale), the way the grid draws the focused cover. */
    private fun grown(
        rest: Rect,
        scale: Float,
    ): Rect {
        val lift = PlumDimens.focusedLift.value * compose.density.density * (scale - 1f) / (PlumDimens.FOCUSED_COVER_SCALE - 1f)
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

    /** The pixels of the focused cover's whole neighbourhood (ring, lifted copy, its shadow), to compare frame with frame. */
    private fun around(rest: Rect): List<Int> {
        val density = compose.density.density
        val area = grown(rest, PlumDimens.FOCUSED_COVER_SCALE)
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
     * the first frame and every pixel around the cover stays put until the sweep's time has passed. Otherwise (a new grid
     * size or system: the grid fades in and its pictures may be re-picked for the new size) only that no light band crosses it.
     */
    private fun assertLiftedInTheFirstFrame(
        label: String,
        index: Int,
        strict: Boolean,
    ) {
        val rest = resting(index)
        if (strict) {
            assertTrue("$label: the ring is at full scale in the first frame", ringAt(rest, PlumDimens.FOCUSED_COVER_SCALE))
            val first = around(rest)
            compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS - 40L)
            assertEquals("$label: nothing moved after the first frame (no grow, no sweep yet)", first, around(rest))
            compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS.toLong())
            assertEquals("$label: and no sweep played once the rest delay was over", first, around(rest))
        } else {
            compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L + 20L)
            val mid = around(resting(index))
            compose.mainClock.advanceTimeBy(3L * PlumMotion.FOCUS_SWEEP_MS)
            assertEquals("$label: no light band crosses the cover", mid, around(resting(index)))
            assertTrue("$label: the ring is at full scale", ringAt(resting(index), PlumDimens.FOCUSED_COVER_SCALE))
        }
    }

    @Test
    fun theCoverFocusedAsTheGridOpensIsLiftedInTheFirstFrame() {
        open(focus = 5)
        frame()
        assertLiftedInTheFirstFrame("opening", 5, strict = true)
    }

    @Test
    fun aNewGridSizeKeepsTheLiftInTheFirstFrame() {
        open(focus = 5)
        compose.mainClock.advanceTimeBy(1000)
        gridSize = GridSize.Large
        frame()
        assertLiftedInTheFirstFrame("after Medium to Large", 5, strict = false)
    }

    @Test
    fun aSystemSwitchKeepsTheLiftInTheFirstFrame() {
        val ids = CoverArtLibrary.entries.map { it.id }.toSet()
        open(focus = 5, gridIds = ids)
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnUiThread { checkNotNull(state).dispatch(Action.NextSystem) }
        frame()
        val opened = checkNotNull(state).focus.let { it.rowOf(CoverArtLibrary.entries[it.systemIndex].id) }
        assertLiftedInTheFirstFrame("after R1", opened, strict = false)
    }

    @Test
    fun aFocusMoveGrowsAndThenSweeps() {
        open(focus = 5)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        val next = resting(6)
        assertTrue("two frames in, the cover is still growing", !ringAt(next, PlumDimens.FOCUSED_COVER_SCALE))
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 40L)
        assertTrue("grown after 150 ms", ringAt(next, PlumDimens.FOCUSED_COVER_SCALE))
        val before = around(next)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L)
        assertNotEquals("the light band is crossing the cover", before, around(next))
    }

    @Test
    fun withAnimationsOffThereIsNoGrowAndNoSweep() {
        open(focus = 5, animationsOn = false)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        val next = resting(6)
        assertTrue("it snaps to full scale", ringAt(next, PlumDimens.FOCUSED_COVER_SCALE))
        val snapped = around(next)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L)
        assertEquals("no sweep halfway through where it would play", snapped, around(next))
        compose.mainClock.advanceTimeBy(5 * PlumMotion.FOCUS_SWEEP_MS.toLong())
        assertEquals("and none after", snapped, around(next))
    }

    @Test
    fun aGridAtRestWritesNoState() {
        open(focus = 5)
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

    /** How much brighter (0..255, the largest channel) the pixel [dpBelow] under cover [rest]'s bottom centre is than the background. */
    private fun brighterThanBackground(
        rest: Rect,
        dpBelow: Float,
    ): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val p = pixels[rest.center.x.roundToInt(), (rest.bottom + dpBelow * compose.density.density).roundToInt()]
        return (maxOf(p.red - background.red, p.green - background.green, p.blue - background.blue) * 255f).roundToInt()
    }

    @Test
    fun theFocusedCoverGlowsAndTheOthersDoNot() {
        open(focus = 5)
        compose.mainClock.advanceTimeBy(1000)
        val glow = brighterThanBackground(resting(5), GLOW_PROBE_DP)
        assertTrue("6 dp under the focused cover is at least 20 levels brighter than the background (was $glow)", glow >= 20)
        for (other in listOf(4, 6)) {
            val level = brighterThanBackground(resting(other), GLOW_PROBE_DP)
            assertTrue("no glow under cover $other: its shadow only darkens (was $level)", level <= 1)
        }
    }

    /** [level] of the pixel at ([x], [y]) over the background, as [brighterThanBackground]. */
    private fun brighterAt(
        x: Float,
        y: Float,
    ): Int {
        val p = compose.onRoot().captureToImage().toPixelMap()[x.roundToInt().coerceIn(0, 100000), y.roundToInt()]
        return (maxOf(p.red - background.red, p.green - background.green, p.blue - background.blue) * 255f).roundToInt()
    }

    @Test
    fun theGlowIsNotCutAtTheFirstRow() {
        open(focus = 0)
        compose.mainClock.advanceTimeBy(1000)
        val first = grown(resting(0), PlumDimens.FOCUSED_COVER_SCALE)
        // Past the ring (3 dp gap + 2.5 dp band); the accent's own soft glow is gone from grids, the cover's own colour's remains.
        val above = brighterAt(first.center.x, first.top - 8f * compose.density.density)
        assertTrue("8 dp above a first-row cover the glow reaches (measured 8, was $above)", above >= 5)
    }

    @Test
    fun theGlowReachesPastTheLastColumn() {
        // Medium grid size on 768: four landscape columns; the last of the first row is focused as the grid opens (lifted in the first frame).
        open(focus = LAST_COLUMN)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("the last column index is the layout's", columns - 1, LAST_COLUMN)
        val last = grown(resting(LAST_COLUMN), PlumDimens.FOCUSED_COVER_SCALE)
        // Well past the ring (5.5 dp) so its antialiased edge cannot count: only the cover's own-colour glow reaches here, clearly above noise (1).
        val beside = brighterAt(last.right + GLOW_BESIDE_DP * compose.density.density, last.center.y)
        assertTrue("$GLOW_BESIDE_DP dp right of a last-column cover the glow reaches (was $beside)", beside >= 4)
    }

    @Test
    fun theGlowFadesInWithTheGrowAndOutWhenFocusLeaves() {
        open(focus = 5)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        val next = resting(6)
        val growing = brighterThanBackground(next, GLOW_PROBE_DP)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 40L)
        val grown = brighterThanBackground(next, GLOW_PROBE_DP)
        assertTrue("the glow is weaker while the cover grows ($growing) than grown ($grown)", growing < grown)
        move(Direction.Left)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 40L)
        assertTrue("the glow is gone once the cover has shrunk back (${brighterThanBackground(next, GLOW_PROBE_DP)})", brighterThanBackground(next, GLOW_PROBE_DP) <= 1)
    }

    @Test
    fun withAnimationsOffTheGlowSnapsOn() {
        open(focus = 5, animationsOn = false)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        assertTrue("the glow is whole at once", brighterThanBackground(resting(6), GLOW_PROBE_DP) >= 20)
    }

    private companion object {
        const val GLOW_PROBE_DP = 6f
        const val GLOW_BESIDE_DP = 9f
        const val LAST_COLUMN = 3
        const val IDLE_MS = 5_000L
    }
}
