package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * The grids' focus (`FocusOutline`, `FocusLift`) frame by frame, read from the pixels: on a move between covers or Apps tiles, also one that
 * scrolls the grid, the rims cross-fade, each standing on its own item (a rim seen whole is on the item the focus left or the one it reached,
 * never between them, and at most one is whole at a time); once the grow is over exactly one rim stands on the new item while the rows go on
 * scrolling; with animations off it is on the new item in the move's first frame. A switch from one cover grid to the next never shows a rim over
 * nothing, whether the next system's covers were fetched ahead or not (grids are not built ahead as the list's pages are: each is keyed by its system).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class GridFocusRimFramesBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var rim = Color.Unspecified
    private var background = Color.Unspecified

    private fun openCovers(
        focus: Int,
        motionOn: Boolean = true,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(TableText.Default.copy(animationsOn = motionOn)) {
                rim = FocusLift.rimColor(PlumTheme.colors.isDark, PlumTheme.colors.text).compositeOver(PlumTheme.colors.bg)
                background = PlumTheme.colors.bg
                // The grid's glide on the test clock: on the wall clock, presses a few ms apart read as a held key.
                CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                    HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, focus), gridIds = setOf("snes", "gba"), onState = { state = it })
                }
            }
        }
    }

    private fun openApps(
        focus: Int,
        motionOn: Boolean = true,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(TableText.Default.copy(animationsOn = motionOn)) {
                rim = FocusLift.rimColor(PlumTheme.colors.isDark, PlumTheme.colors.text).compositeOver(PlumTheme.colors.bg)
                background = PlumTheme.colors.bg
                CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                    AppsGridBoard(focused = focus, onState = { state = it })
                }
            }
        }
    }

    private fun send(action: Action) = compose.runOnUiThread { checkNotNull(state).dispatch(action) }

    private fun frame() = compose.mainClock.advanceTimeByFrame()

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        shot()
    }

    private fun shot(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private fun focused(): Int {
        val focus = checkNotNull(state).focus
        return focus.rowOf(checkNotNull(focus.systemId))
    }

    /** Item [index] where it is drawn, also the part the grid's edge cuts (its bounds in root would be cut with it), in px. */
    private fun item(index: Int): Rect {
        val node = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode()
        return Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    private fun grid(): Rect = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Color.isWholeLine() = abs(red - rim.red) < LINE_TOLERANCE && abs(green - rim.green) < LINE_TOLERANCE && abs(blue - rim.blue) < LINE_TOLERANCE

    /**
     * Whether this pixel is the rim at any strength from [LINE_MIN_SHARE] up: on the way from the page colour to the rim's whole colour.
     * A rim fading in is a little short of whole on every side, and not by the same amount (the depth shadow darkens the lower side), so a
     * match against the whole colour alone found some of its sides and not others in one frame: the switch checks use this, to see any rim.
     */
    private fun Color.isRimAtAnyStrength(): Boolean {
        val channels = listOf(Triple(red, background.red, rim.red), Triple(green, background.green, rim.green), Triple(blue, background.blue, rim.blue))
        val spans = channels.filter { abs(it.third - it.second) > 0.15f }
        if (spans.isEmpty()) return false
        val share = spans.map { (it.first - it.second) / (it.third - it.second) }.average().toFloat()
        if (share < LINE_MIN_SHARE || share > 1f + LINE_TOLERANCE) return false
        return channels.all { abs(it.first - (it.second + share * (it.third - it.second))) < LINE_TOLERANCE }
    }

    private fun Color.isBackground() = abs(red - background.red) < 0.03f && abs(green - background.green) < 0.03f && abs(blue - background.blue) < 0.03f

    /** The rim's sides seen in [shot] inside [area]: the middles of the runs of rim colour at least [MIN_RUN_DP] long, rows then columns. */
    private class Sides(
        val rows: List<Float>,
        val columns: List<Float>,
    ) {
        val isOneLine get() = rows.size == 2 && columns.size == 2
        val isNone get() = rows.isEmpty() && columns.isEmpty()
        val rect get() = Rect(columns.first(), rows.first(), columns.last(), rows.last())
    }

    private fun sides(
        shot: PixelMap,
        area: Rect,
        /** True: a rim of any strength counts (a lone rim is one however faint); false: only a whole one (the cross-fade checks count whole rims). */
        anyStrength: Boolean = false,
    ): Sides {
        val minRun = (MIN_RUN_DP * compose.density.density).roundToInt()
        val left = area.left.roundToInt().coerceAtLeast(0)
        val right = area.right.roundToInt().coerceAtMost(shot.width)
        val top = area.top.roundToInt().coerceAtLeast(0)
        val bottom = area.bottom.roundToInt().coerceAtMost(shot.height)

        fun longest(
            count: Int,
            at: (Int) -> Color,
        ): Int {
            var best = 0
            var run = 0
            for (i in 0 until count) {
                run = if (at(i).let { if (anyStrength) it.isRimAtAnyStrength() else it.isWholeLine() }) run + 1 else 0
                best = maxOf(best, run)
            }
            return best
        }
        val rows = (top until bottom).filter { y -> longest(right - left) { shot[left + it, y] } >= minRun }
        val columns = (left until right).filter { x -> longest(bottom - top) { shot[x, top + it] } >= minRun }
        return Sides(clusters(rows), clusters(columns))
    }

    /** The middles of runs of neighbouring values (a gap of a px or two joins them: the stroke is anti-aliased). */
    private fun clusters(values: List<Int>): List<Float> {
        val out = mutableListOf<Float>()
        var start = -1
        var last = -1
        for (v in values) {
            if (start >= 0 && v - last > 2) {
                out += (start + last) / 2f
                start = -1
            }
            if (start < 0) start = v
            last = v
        }
        if (start >= 0) out += (start + last) / 2f
        return out
    }

    private fun area(): Rect = grid().inflate(AREA_ROOM_DP * compose.density.density)

    /** One frame of a move: the line's rect (null: the grid's edge cuts a side of it, as it cuts its item) and the target item's bounds. */
    private class Seen(
        val ms: Long,
        val line: Rect?,
        val item: Rect,
    )

    /** One line or part of one cut at the grid's edge: never two sides more than a line has (a trail, or a second mark). */
    private fun assertAtMostOneLine(
        sides: Sides,
        what: String,
    ) = assertTrue("$what: at most one line (rows ${sides.rows}, columns ${sides.columns})", sides.rows.size <= 2 && sides.columns.size <= 2)

    /** Whether [item] reaches past the grid's top or bottom by the line's room, where the grid cuts the line with its item. */
    private fun isAtGridEdge(item: Rect): Boolean {
        val g = grid()
        val room = AREA_ROOM_DP * compose.density.density
        return item.top - room < g.top || item.bottom + room > g.bottom
    }

    /** Two items' rims sit at different places in their cells by their pivots (an edge item grows inward): up to the growth of the item. */
    private fun pivotSlack(rim: Rect): Float = ATTACHED_PX + (FocusLift.SCALE - 1f) * maxOf(rim.width, rim.height)

    private fun itemOrNull(index: Int): Rect? = runCatching { item(index) }.getOrNull()

    /** Presses [direction], then draws frame by frame until the rims rest, and checks every frame. Returns whether the rows scrolled. */
    private fun checkMove(
        direction: Direction,
        what: String,
    ): Boolean {
        val from = focused()
        val startRect = sides(shot(), area()).takeIf { it.isOneLine }?.rect
        val fromItem = itemOrNull(from)
        send(Action.Move(direction))
        val seen = mutableListOf<Seen>()
        var ms = 0L
        val fromRects = mutableListOf<Rect?>()
        repeat(MOVE_FRAMES) {
            frame()
            ms += FRAME_MS
            val shot = shot()
            val target = item(focused())
            val sides = sides(shot, area())
            assertAtMostOneLine(sides, "$what, ${ms}ms")
            seen += Seen(ms, sides.takeIf { it.isOneLine }?.rect, target)
            fromRects += itemOrNull(from)
        }
        if (focused() == from) return false
        val end = seen.last()
        val endLine = end.line ?: return end.item.top != seen.first().item.top
        val grown = FocusLift.SCALE - 1f
        // A rim seen whole is within a tenth of its growth of its settled place.
        val slack = ATTACHED_PX + 0.1f * grown * maxOf(endLine.width, endLine.height)

        fun aim(
            item: Rect,
            line: Rect,
            at: Rect,
        ) = Rect(item.left + (line.left - at.left), item.top + (line.top - at.top), item.right + (line.right - at.right), item.bottom + (line.bottom - at.bottom))

        fun close(
            a: Rect,
            b: Rect,
        ) = abs(a.left - b.left) <= slack && abs(a.top - b.top) <= slack && abs(a.right - b.right) <= slack && abs(a.bottom - b.bottom) <= slack
        val log = seen.joinToString { s -> "${s.ms}:" + (s.line?.let { "${it.left.roundToInt()},${it.top.roundToInt()} ${it.width.roundToInt()}x${it.height.roundToInt()}" } ?: "-") + "/${s.item.top.roundToInt()}" }
        seen.forEachIndexed { i, s ->
            val line = s.line ?: return@forEachIndexed
            val onNew = close(line, aim(s.item, endLine, end.item))
            val old = fromRects[i]
            val onOld = startRect != null && fromItem != null && old != null && close(line, aim(old, startRect, fromItem))
            assertTrue("$what, ${s.ms}ms: a rim seen whole stands on the item the focus left or the one it reached ($log)", onNew || onOld)
            // The grow is over: only the new item has a rim.
            if (s.ms > PlumMotion.FOCUS_GROW_MS + 2 * FRAME_MS) assertTrue("$what, ${s.ms}ms: the grow is over, the rim stays on its item ($log)", onNew)
        }
        return seen.first().item.top != end.item.top
    }

    private fun checkDowns(what: String) {
        var scrolled = false
        repeat(DOWNS) { scrolled = checkMove(Direction.Down, "$what, down ${it + 1}") || scrolled }
        assertTrue("$what: some Down scrolled the rows, so a move during a scroll was seen", scrolled)
        repeat(2) { checkMove(Direction.Right, "$what, right ${it + 1}") }
        repeat(DOWNS) { checkMove(Direction.Up, "$what, up ${it + 1}") }
    }

    @Test
    fun theRimsCrossFadeBetweenCoversAndTheRimStaysOnACoverThatScrolls() {
        openCovers(focus = 0)
        settle()
        checkDowns("covers")
    }

    @Test
    fun theRimsCrossFadeBetweenAppTilesAndTheRimStaysOnATileThatScrolls() {
        openApps(focus = 0)
        settle()
        checkDowns("apps")
    }

    @Test
    fun withAnimationsOffTheRimIsOnTheNewItemInTheMovesFirstFrame() {
        openCovers(focus = 0, motionOn = false)
        settle()
        val start = sides(shot(), area()).rect
        val startItem = item(focused())
        var whole = 0
        repeat(DOWNS) {
            send(Action.Move(Direction.Down))
            frame()
            val sides = sides(shot(), area())
            val target = item(focused())
            assertAtMostOneLine(sides, "motion off, down ${it + 1}")
            assertTrue("motion off, down ${it + 1}: a rim in the move's first frame", sides.columns.size == 2)
            assertEquals("motion off, down ${it + 1}: the rim is on its item at once (left)", target.left + (start.left - startItem.left), sides.columns.first(), pivotSlack(start))
            if (sides.isOneLine) {
                whole++
                assertEquals("motion off, down ${it + 1}: the rim is on its item at once (top)", target.top + (start.top - startItem.top), sides.rect.top, pivotSlack(start))
            } else {
                assertTrue("motion off, down ${it + 1}: a side is missing only where the grid's edge cuts (item $target, grid ${grid()})", isAtGridEdge(target))
            }
            // The rows may land a frame later (the grid scrolls after the focus): the line is on its item then too, with no glide.
            frame()
            val after = sides(shot(), area())
            val landed = item(focused())
            assertEquals("motion off, down ${it + 1}: still on its item a frame later (left)", landed.left + (start.left - startItem.left), after.columns.first(), pivotSlack(start))
            if (after.isOneLine) assertEquals("motion off, down ${it + 1}: still on its item a frame later (top)", landed.top + (start.top - startItem.top), after.rect.top, pivotSlack(start))
        }
        assertTrue("motion off: some line was seen whole", whole > 0)
    }

    /** Every frame of a switch to the next cover grid: a line, when there is one, stands round a drawn cover; the new grid ends with one. */
    private fun checkSwitch(what: String) {
        send(Action.NextSystem)
        var ms = 0L
        repeat(SWITCH_FRAMES) {
            frame()
            ms += FRAME_MS
            val shot = shot()
            val sides = sides(shot, area(), anyStrength = true)
            if (sides.isNone) return@repeat
            assertTrue("$what, ${ms}ms: one line (rows ${sides.rows}, columns ${sides.columns})", sides.isOneLine)
            val inside = sides.rect.deflate(INSIDE_DP * compose.density.density)
            val points = (1..4).flatMap { i -> (1..4).map { j -> shot[(inside.left + inside.width * i / 5f).roundToInt(), (inside.top + inside.height * j / 5f).roundToInt()] } }
            val empty = points.count { it.isBackground() }
            assertTrue("$what, ${ms}ms: the line stands round a drawn cover, not over nothing ($empty of ${points.size} probes are the page colour)", empty <= points.size / 4)
        }
        assertEquals("$what: the next system is shown", "gba", checkNotNull(state).focus.systemId)
        val last = sides(shot(), area(), anyStrength = true)
        assertTrue("$what: the new grid has its line", last.isOneLine)
        assertNotNull(item(focused()))
    }

    @Test
    fun aSwitchToTheNextCoverGridWhoseCoversWereFetchedAheadShowsNoLoneRim() {
        openCovers(focus = 5)
        settle()
        settle()
        checkSwitch("covers fetched ahead")
    }

    @Test
    fun aSwitchToTheNextCoverGridRightAfterOpeningShowsNoLoneRim() {
        openCovers(focus = 5)
        frame()
        shot()
        checkSwitch("right after opening")
    }

    private companion object {
        const val FRAME_MS = 16L
        const val SETTLE_MS = 1_000L
        const val MOVE_FRAMES = 24
        const val SWITCH_FRAMES = 30
        const val DOWNS = 4
        const val LINE_TOLERANCE = 0.06f
        const val LINE_MIN_SHARE = 0.5f
        const val MIN_RUN_DP = 24f
        const val AREA_ROOM_DP = 8f
        const val INSIDE_DP = 6f
                const val ATTACHED_PX = 1.5f
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridFocusRimFramesTest : GridFocusRimFramesBase()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GridFocusRimFrames576Test : GridFocusRimFramesBase()
