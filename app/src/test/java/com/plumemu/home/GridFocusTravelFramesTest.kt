package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
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
 * The focus ring of the games grid and the Apps grid travels from the cell it leaves to the cell it lands on (the PS Vita's
 * way; the home lists' pill does the same, [GlidingPillFramesBase]): over [PlumMotion.GLIDE_MS],
 * decelerating, in X within a row and in Y between rows (in step with the rows' glide), drawn by the grid, nothing recomposed.
 * The ring is a draw, not a node, so it is found in the pixels the screen really draws: the box of the accent-coloured pixels
 * inside the grid, its centre X and its bottom Y (the cover grows and lifts under it, which moves its top). Frame by frame on
 * the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class GridFocusTravelFramesBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var accent = Color.Unspecified
    private var columns = 0

    private enum class Kind { Covers, Apps }

    private fun open(
        kind: Kind,
        focus: Int,
        animationsOn: Boolean = true,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(MediumText.Default.copy(animationsOn = animationsOn)) {
                    accent = PlumTheme.colors.accent
                    if (kind == Kind.Covers) {
                        columns = PlumTheme.layout.gridColumns(CoverShape.Landscape)
                        HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, focus), gridIds = setOf("snes"), onState = { state = it })
                    } else {
                        columns = appsGridColumns(PlumTheme.layout)
                        AppsGridBoard(focused = focus, onState = { state = it })
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    /** Puts the focus on cover [index] and lets everything settle. */
    private fun focusOn(index: Int) {
        compose.runOnIdle { checkNotNull(state).dispatch(Action.TapItem(Column.Games, index)) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    private fun move(direction: Direction) = compose.runOnIdle { checkNotNull(state).dispatch(Action.Move(direction)) }

    /** Where cell [index] is drawn now (its layout place and the glide's translation). */
    private fun cell(index: Int): Rect = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** The ring's centre X and bottom Y in the pixels now; null when none is drawn. */
    private fun ring(): Pair<Float, Float>? {
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var minX = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        for (y in grid.top.toInt() until grid.bottom.toInt() step 2) {
            for (x in grid.left.toInt() until pixels.width step 2) {
                val p = pixels[x, y]
                if (abs(p.red - accent.red) + abs(p.green - accent.green) + abs(p.blue - accent.blue) < 0.12f) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else Pair((minX + maxX) / 2f, maxY.toFloat())
    }

    /** One frame at a time after a move, [before] run ahead of frame i; the ring each frame drew. */
    private fun record(
        frames: Int,
        before: (Int) -> Unit = {},
    ): List<Pair<Float, Float>?> {
        compose.mainClock.autoAdvance = false
        return (0 until frames).map { i ->
            before(i)
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            ring()
        }
    }

    private fun framesFor(ms: Int) = ms / FRAME_MS

    private fun List<Pair<Float, Float>?>.xs() = filterNotNull().map { it.first }

    private fun List<Pair<Float, Float>?>.ys() = filterNotNull().map { it.second }

    private fun assertTravel(
        what: String,
        values: List<Float>,
        from: Float,
        to: Float,
        tol: Float = TOL,
    ) {
        println("$what ring (from $from, to $to): $values")
        val sign = if (to > from) 1f else -1f
        val span = abs(to - from)
        assertTrue("$what: travels more than a few px: $span", span > 20f)
        assertTrue("$what: never past either end (tolerance ${tol}px): $values", values.all { (it - from) * sign >= -tol && (to - it) * sign >= -tol })
        assertTrue("$what: only one way: $values", values.zipWithNext().all { (a, b) -> (b - a) * sign >= -1f })
        assertTrue("$what: the first frame travels at most $FIRST_FRAME_SHARE of the way: $values", (values.first() - from) * sign <= span * FIRST_FRAME_SHARE + tol)
        assertTrue("$what: still travelling after a third of the glide: $values", (to - values[framesFor(PlumMotion.GLIDE_MS) / 3]) * sign > tol)
        assertEquals("$what: arrived within the glide and two frames", to, values[framesFor(PlumMotion.GLIDE_MS) + 2], tol)
        assertEquals("$what: ends on the cell", to, values.last(), tol)
        assertTrue("$what: several frames of travel: $values", values.map { it.toInt() }.distinct().size > 5)
    }

    private fun rightWithinARow(kind: Kind) {
        open(kind, focus = 1)
        val from = checkNotNull(ring())
        val delta = cell(2).center.x - cell(1).center.x
        compose.mainClock.autoAdvance = false
        move(Direction.Right)
        val frames = record(framesFor(PlumMotion.GLIDE_MS) + 6)
        assertTravel("${kind.name} right", frames.xs(), from.first, from.first + delta)
        assertTrue("${kind.name} right: Y stays (same row): ${frames.ys()}", frames.ys().all { abs(it - from.second) <= TOL_Y })
    }

    @Test
    fun aRightMoveInsideARowTravelsInXInTheCoverGrid() = rightWithinARow(Kind.Covers)

    @Test
    fun aRightMoveInsideARowTravelsInXInTheAppsGrid() = rightWithinARow(Kind.Apps)

    /**
     * A move between rows that needs no scroll (up from the second row: the grid keeps the row past the focus whole only on the
     * side it moves to, and the first row is already shown) travels the ring in Y.
     */
    private fun upWithoutScroll(kind: Kind) {
        // A cover of the second row: 4 covers a row at 768, 3 at 576; 7 or 5 apps.
        val index = if (kind == Kind.Covers) 4 else 7
        open(kind, focus = index)
        assertTrue("${kind.name}: the focus is on the second row (columns $columns)", index in columns until 2 * columns)
        val from = checkNotNull(ring())
        val delta = cell(index - columns).center.y - cell(index).center.y
        compose.mainClock.autoAdvance = false
        move(Direction.Up)
        val frames = record(framesFor(PlumMotion.GLIDE_MS) + 6)
        assertTravel("${kind.name} up", frames.ys(), from.second, from.second + delta, TOL_Y)
        assertTrue("${kind.name} up: X stays (same column): ${frames.xs()}", frames.xs().all { abs(it - from.first) <= TOL })
    }

    @Test
    fun anUpMoveBetweenRowsTravelsInYInTheCoverGrid() = upWithoutScroll(Kind.Covers)

    @Test
    fun anUpMoveBetweenRowsTravelsInYInTheAppsGrid() = upWithoutScroll(Kind.Apps)

    /**
     * A move down at the last whole row scrolls the rows and they glide (the grid scrolls smoothly too): the
     * new row's cover comes up from where the row below it stood, over the glide, on the decelerating curve, no frame
     * jumps; the ring, in step with the rows, stays where it is (the cover it is on changes under it) and ends on its cell.
     */
    private fun downWithScroll(kind: Kind) {
        open(kind, focus = 0)
        // Walk down until the rows scroll: from then on every move down scrolls one row (the row past the focus is kept whole).
        val top = cell(0).top
        var guard = 0
        while (cell(0).top >= top && guard++ < MAX_WALK) {
            move(Direction.Down)
            compose.mainClock.advanceTimeBy(400)
            compose.waitForIdle()
        }
        assertTrue("${kind.name}: the grid scrolled while walking down", cell(0).top < top)
        val index = checkNotNull(state).focus.row
        val before = checkNotNull(ring())
        val oldCell = cell(index)
        val next = index + columns
        val nextBefore = cell(next).top
        compose.mainClock.autoAdvance = false
        move(Direction.Down)
        val rows = mutableListOf<Float>()
        val frames = record(framesFor(PlumMotion.GLIDE_MS) + 6) { rows += cell(next).top }
        val settled = cell(next).top
        // The rows scrolled one row and ended where the focused row stood.
        assertEquals("${kind.name}: the new row ends where the old one stood", oldCell.top, settled, TOL)
        val stride = nextBefore - oldCell.top
        // Drawn after each frame: starts a row below its place, comes up decelerating, never goes back, and is on its place within the glide.
        val drawn = rows.drop(1) + settled
        println("${kind.name} down with scroll: new row top $drawn, ring Y ${frames.ys()} (rest ${before.second})")
        assertTrue("${kind.name}: the rows glide: ${drawn.map { it.toInt() }.distinct().size} distinct places in $drawn", drawn.map { it.toInt() }.distinct().size > 6)
        assertTrue("${kind.name}: only up, between the row below and the place: $drawn", drawn.zipWithNext().all { (a, b) -> b <= a + 1f } && drawn.all { it >= settled - 1f && it <= settled + stride + 1f })
        assertTrue("${kind.name}: no frame moves the rows more than ${FIRST_FRAME_SHARE} of a row but the first: $drawn", drawn.zipWithNext().all { (a, b) -> a - b <= stride * 0.45f })
        assertEquals("${kind.name}: the rows are on their place within the glide and two frames", settled, drawn[framesFor(PlumMotion.GLIDE_MS) + 1], TOL)
        val ys = frames.ys()
        assertTrue("${kind.name}: the ring stays within a row of its place, in step with the rows: $ys", ys.all { abs(it - before.second) <= stride + TOL_Y })
        assertTrue("${kind.name}: the ring never jumps between frames: $ys", ys.zipWithNext().all { (a, b) -> abs(b - a) <= stride * FIRST_FRAME_SHARE })
        assertEquals("${kind.name}: ends on the new cell", before.second, ys.last(), TOL_Y)
    }

    @Test
    fun aDownMoveThatScrollsTheRowsGlidesThemAndKeepsTheRingWithThemInTheCoverGrid() = downWithScroll(Kind.Covers)

    @Test
    fun aDownMoveThatScrollsTheRowsGlidesThemAndKeepsTheRingWithThemInTheAppsGrid() = downWithScroll(Kind.Apps)

    /** Held Down through the grid: the rows keep moving at the repeat speed (linear) and the ring never goes back or jumps. */
    private fun heldDownScrolls(kind: Kind) {
        open(kind, focus = 0)
        val repeats = 5
        val probe = 1
        val stride = cell(columns).top - cell(0).top
        compose.mainClock.autoAdvance = false
        val tops = mutableListOf<Float>()
        val frames = record(HOLD_REPEAT_FRAMES * repeats + framesFor(PlumMotion.GLIDE_MS) + 4) { i ->
            if (i % HOLD_REPEAT_FRAMES == 0 && i / HOLD_REPEAT_FRAMES < repeats) move(Direction.Down)
            tops += runCatching { cell(probe).top }.getOrDefault(Float.NaN)
        }
        val focus = checkNotNull(state).focus.row
        println("${kind.name} held down: focus row $focus, cover $probe top $tops, ring Y ${frames.ys()}")
        assertTrue("${kind.name}: the focus moved down several rows: $focus", focus / columns >= 3)
        val ys = frames.ys()
        assertTrue("${kind.name}: the ring never jumps (more than a row's share between frames): $ys", ys.zipWithNext().all { (a, b) -> abs(b - a) <= abs(stride) * 0.6f })
        val moving = tops.filter { !it.isNaN() }
        assertTrue("${kind.name}: the rows never go back while held: $moving", moving.zipWithNext().all { (a, b) -> b <= a + 1f })
    }

    @Test
    fun aHeldDownGlidesTheRowsAndTheRingWithoutJumpsInTheCoverGrid() = heldDownScrolls(Kind.Covers)

    @Test
    fun aHeldDownGlidesTheRowsAndTheRingWithoutJumpsInTheAppsGrid() = heldDownScrolls(Kind.Apps)

    /** Down off a column that the last, short row does not have lands on its last cover: the ring travels left as well as down. */
    @Test
    fun aDownMoveIntoAShortLastRowTravelsDiagonally() {
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(MediumText.Default) {
                    accent = PlumTheme.colors.accent
                    columns = appsGridColumns(PlumTheme.layout)
                    val board = remember { appsGridLibrary() }
                    val short = remember(board) { board.withEntries(board.entries.map { if (it.kind == EntryKind.Apps) it.copy(rows = AppsGridApps.take(SHORT_APPS), shownCount = SHORT_APPS) else it }) }
                    CompositionLocalProvider(LocalHomeAppIcons provides remember { FakeAppIcons() }) {
                        HomeAt(short, gridActions(APPS_ENTRY, 0), gridIds = setOf(HomeContent.APPS_ID), onState = { state = it })
                    }
                }
            }
        }
        compose.waitForIdle()
        val lastRow = (SHORT_APPS - 1) / columns
        assertTrue("the last row is short at $columns columns", SHORT_APPS % columns != 0)
        // The row above the last, in the last column: below it the short row has no cover.
        focusOn((lastRow - 1) * columns + columns - 1)
        val from = checkNotNull(ring())
        val start = cell(checkNotNull(state).focus.row)
        compose.mainClock.autoAdvance = false
        move(Direction.Down)
        val frames = record(framesFor(PlumMotion.GLIDE_MS) + 6)
        assertEquals("the focus landed on the last cover", SHORT_APPS - 1, checkNotNull(state).focus.row)
        val end = cell(SHORT_APPS - 1)
        val xs = frames.xs()
        println("short last row: ring X $xs, Y ${frames.ys()} (from $from)")
        assertTravel("short row X", xs, from.first, from.first + (end.center.x - start.center.x))
        val ys = frames.ys()
        assertTrue("short row: Y ends on the new row: $ys", abs(ys.last() - (from.second + (end.center.y - start.center.y))) <= TOL_Y)
        assertTrue("short row: X and Y arrive together, no frame jumps: $ys", ys.zipWithNext().all { (a, b) -> abs(b - a) <= abs(end.center.y - start.center.y).coerceAtLeast(1f) * FIRST_FRAME_SHARE + TOL_Y })
    }

    /**
     * The row's end: Right does not wrap to the next row. Where the details are a column of their own (576) it hands the focus
     * on to them and the ring leaves the covers; where they are not (768) the focus stays and so does the ring.
     */
    @Test
    fun aRightMoveAtTheEndOfARowDoesNotWrapAndLeavesNoRingTravelling() {
        open(Kind.Covers, focus = 0)
        focusOn(columns - 1)
        val covers = cell(0).let { Rect(it.left, it.top, cell(columns - 1).right, it.bottom) }.inflate(10f)
        val from = checkNotNull(ring())
        val row = checkNotNull(state).focus.row
        compose.mainClock.autoAdvance = false
        move(Direction.Right)
        val frames = record(framesFor(PlumMotion.GLIDE_MS) + 4)
        val focus = checkNotNull(state).focus
        assertEquals("the focus did not wrap to the next row", row, focus.row)
        if (focus.column == Column.Games) {
            assertTrue("the focus stayed, so the ring stays: ${frames.map { it?.first }}", frames.all { it != null && abs(it.first - from.first) <= 1f && abs(it.second - from.second) <= 1f })
        } else {
            assertEquals(Column.Details, focus.column)
            assertTrue("no ring on the covers once the focus left them", !ringIn(covers))
        }
    }

    /** Left at the first column hands the focus to the system column: no ring is left on the covers and nothing travels. */
    @Test
    fun aLeftMoveAtTheStartOfARowTakesTheRingAway() {
        open(Kind.Covers, focus = 0)
        val covers = cell(0).let { Rect(it.left, it.top, cell(columns - 1).right, it.bottom) }.inflate(10f)
        assertTrue("the ring is on the first cover at rest", ringIn(covers))
        compose.mainClock.autoAdvance = false
        move(Direction.Left)
        record(framesFor(PlumMotion.GLIDE_MS) + 4)
        assertEquals(Column.Systems, checkNotNull(state).focus.column)
        assertTrue("no ring on the covers once the focus left them", !ringIn(covers))
    }

    private fun ringIn(area: Rect): Boolean {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return (area.top.toInt() until area.bottom.toInt() step 2).any { y ->
            (area.left.toInt() until area.right.toInt() step 2).any { x ->
                val p = pixels[x.coerceIn(0, pixels.width - 1), y.coerceIn(0, pixels.height - 1)]
                abs(p.red - accent.red) + abs(p.green - accent.green) + abs(p.blue - accent.blue) < 0.12f
            }
        }
    }

    private fun heldRight(kind: Kind) {
        open(kind, focus = 0)
        val repeats = minOf(3, columns - 1)
        // How far the ring's centre stands from its cell's centre at rest (measured on a cover the grid's edge does not cut).
        focusOn(1)
        val ringToCell = checkNotNull(ring()).first - cell(1).center.x
        focusOn(0)
        compose.mainClock.autoAdvance = false
        val frames = record(HOLD_REPEAT_FRAMES * repeats + framesFor(PlumMotion.GLIDE_MS) + 4) { i -> if (i % HOLD_REPEAT_FRAMES == 0 && i / HOLD_REPEAT_FRAMES < repeats) move(Direction.Right) }
        val xs = frames.xs()
        println("${kind.name} held right ring X: $xs")
        assertTrue("${kind.name}: the ring never goes back: $xs", xs.zipWithNext().all { (a, b) -> b >= a - 1f })
        assertEquals("${kind.name}: it moved on $repeats covers", repeats, checkNotNull(state).focus.row)
        assertEquals("${kind.name}: ends on the focused cover", cell(repeats).center.x + ringToCell, xs.last(), TOL)
        // At the repeat speed: while the key is held the ring keeps moving (a frame that starts a step may repeat the last position, as the list's glide does), never two in a row.
        val held = xs.take(HOLD_REPEAT_FRAMES * repeats).zipWithNext().map { (a, b) -> b - a }.drop(HOLD_REPEAT_FRAMES)
        assertTrue("${kind.name}: keeps moving while held: $held", held.zipWithNext().none { (a, b) -> a < 0.5f && b < 0.5f })
    }

    @Test
    fun aHeldRightKeepsTheRingMovingEvenlyInTheCoverGrid() = heldRight(Kind.Covers)

    @Test
    fun aHeldRightKeepsTheRingMovingEvenlyInTheAppsGrid() = heldRight(Kind.Apps)

    private fun jumpsWithAnimationsOff(kind: Kind) {
        open(kind, focus = 1, animationsOn = false)
        val from = checkNotNull(ring())
        val delta = cell(2).center.x - cell(1).center.x
        compose.mainClock.autoAdvance = false
        move(Direction.Right)
        val frames = record(framesFor(PlumMotion.GLIDE_MS))
        val xs = frames.xs()
        println("${kind.name} animations off ring X: $xs")
        assertTrue("${kind.name}: the ring is on the new cell at once, from the first frame it is drawn: $xs", xs.all { abs(it - (from.first + delta)) <= TOL })
    }

    @Test
    fun withAnimationsOffTheRingJumpsInTheCoverGrid() = jumpsWithAnimationsOff(Kind.Covers)

    @Test
    fun withAnimationsOffTheRingJumpsInTheAppsGrid() = jumpsWithAnimationsOff(Kind.Apps)

    private companion object {
        const val FRAME_MS = 16
        const val FIRST_FRAME_SHARE = 0.30f
        const val HOLD_REPEAT_FRAMES = 6
        const val TOL = 8f

        /** The ring's bottom edge moves a few px more than its centre while the cover grows and lifts under it. */
        const val TOL_Y = 12f
        const val MAX_WALK = 10


        /** Apps in the short-last-row board: a partial last row at 7 columns (768) and at 5 (576). */
        const val SHORT_APPS = 27
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridFocusTravelFramesTest : GridFocusTravelFramesBase()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GridFocusTravelFrames576Test : GridFocusTravelFramesBase()
