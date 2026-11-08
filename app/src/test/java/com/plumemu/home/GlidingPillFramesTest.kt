package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.After
import org.junit.Assume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The focus pill of the home lists glides from the row it leaves to the row it lands on (the PS Vita's
 * way): over [PlumMotion.GLIDE_MS], decelerating, drawn by the list, nothing recomposed on the way. The pill is a draw, not a
 * node, so it is found in the pixels the screen really draws: the first row of the list's width that is wholly the ring's
 * colour is the pill's top edge. Frame by frame on the test clock.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class GlidingPillFramesBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var state: StripState
    private val composed = mutableListOf<Pair<String, Long>>()

    /** The pages' prefetcher, run by the test: Compose's own spends a wall-clock budget, so what it built ahead varied with load. */
    private val prefetch = ManualPagesPrefetch()

    @Before
    fun trace() {
        gamesPagesStateMade = prefetch::install
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    composed += info to compose.mainClock.currentTime
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stop() {
        Composer.setTracer(null)
        gamesPagesStateMade = null
    }

    private fun show(
        content: HomeContent,
        animationsOn: Boolean,
        listTag: String,
        focusColumn: Column,
        entry: Int,
        panel: OpenPanel? = null,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(PlumThemeSettings.Default.copy(animationsOn = animationsOn)) {
                    HomeAt(content, isMotionOn = animationsOn, onState = { state = it }, panel = panel)
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            // The screen opens on the system column; a tap there opens the games.
            if (focusColumn == Column.Games) state.dispatch(Action.TapItem(Column.Systems, entry))
            if (focusColumn == Column.Games) state.dispatch(Action.Enter)
        }
        compose.waitForIdle()
        this.listTag = listTag
    }

    private var listTag = ""

    protected open val panelLeavesTheListInView = true

    private fun listBounds(): Rect = compose.onNodeWithTag(listTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** How far the slab is drawn above its row's box: [pillTop] reports the box, so the glide is checked in row positions. */
    private var overhang = 0f

    /** The height of the row [overhang] was taken from. */
    private var rowPx = 0f

    /** Where [pillTop] starts looking: the list's top, or the first game row's when something else (the system info band) stands above it. */
    private var scanTop = 0f

    /** The slab's hairline edge (1 dp), above its fill. */
    private val edgePx get() = compose.density.density.toInt().coerceAtLeast(1)

    /** The ring's colour: the pill's top edge on the row [rowTag] that wears it, at rest. Also takes that row's overhang. */
    private fun ringColour(rowTag: String): Color {
        val list = listBounds()
        // A game row's box holds its tap gap under the row; the slab is as tall as the row.
        val gap = if (rowTag.startsWith("home_game")) PlumDimens.listRowGap else 0.dp
        overhang = compose.slabOverhangPx(rowTag, gap)
        rowPx = compose.rowHeightPx(rowTag, gap)
        // A system info band above the first game row ends in a hairline of its own: the search for the pill starts at the first row, under it.
        scanTop = if (rowTag.startsWith("home_game")) runCatching { rowTop(HomeTags.game(0)) }.getOrDefault(list.top) else list.top
        return compose.onRoot().captureToImage().toPixelMap()[((list.left + list.right) / 2).toInt(), rowTop(rowTag).toInt() + 1]
    }

    /** The top of the pill's row box now (the slab's drawn top border plus its overhang); -1 when none. [ring] only marks that a row was measured. */
    @Suppress("UNUSED_PARAMETER")
    private fun pillTop(ring: Color): Int {
        val list = listBounds()
        // The slab of a first row is drawn above the list's top, inside the list's clip bleed.
        val y = compose.onRoot().captureToImage().slabTopRow(compose.barColumns(list.left), scanTop.toInt() - overhang.toInt() - 1, list.bottom.toInt(), rowPx, overhang)
        return if (y < 0) -1 else Math.round(y + overhang)
    }

    private fun Color.isNear(other: Color) = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < 0.03f

    private fun rowTop(tag: String): Float = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top

    /** One drawn frame: the pill's top, and how many composable bodies ran in it. */
    private data class Frame(
        val top: Int,
        val composed: Int,
        val rows: Int,
    )

    /** Frames after a move: what each drew and composed. The frame the focus change is composed in is the first with [Frame.rows] > 0. */
    private fun record(
        frames: Int,
        ring: Color,
        before: (Int) -> Unit = {},
    ): List<Frame> {
        compose.mainClock.autoAdvance = false
        composed.clear()
        return (0 until frames).map { i ->
            before(i)
            composed.clear()
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            Frame(pillTop(ring), composed.size, composed.count { it.first.startsWith("com.plumemu.home.GamesListItem ") || it.first.startsWith("com.plumemu.coreui.components.SystemRow") })
        }
    }

    /** The pill's tops from the frame the move was composed in on. */
    private fun fromMove(frames: List<Frame>): List<Int> = frames.dropWhile { it.rows == 0 }.map { it.top }

    private fun framesFor(ms: Int) = ms / FRAME_MS

    private fun oneDown(
        animationsOn: Boolean,
        column: Column,
        listTag: String,
        rowTag: (Int) -> String,
        entry: Int,
        first: Int,
        content: HomeContent = FakeHomeContent.board(),
        preMoves: Int = 0,
    ): Triple<List<Frame>, Float, Float> {
        show(content, animationsOn, listTag, column, entry)
        repeat(preMoves) {
            compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
            compose.waitForIdle()
        }
        val oldTop = rowTop(rowTag(first))
        val ring = ringColour(rowTag(first))
        val newTop = rowTop(rowTag(first + 1))
        listBounds().let { list ->
            val gap = if (rowTag(first).startsWith("home_game")) PlumDimens.listRowGap else 0.dp
            compose.assertSlabRestsOn(rowTag(first), compose.barColumns(list.left), scanTop.toInt() - overhang.toInt() - 1, list.bottom.toInt(), gap)
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        compose.mainClock.autoAdvance = true
        val tops = record(framesFor(PlumMotion.GLIDE_MS) + 4, ring)
        return Triple(tops, oldTop, newTop)
    }

    private fun assertGlide(
        what: String,
        result: Triple<List<Frame>, Float, Float>,
    ) {
        val (frames, oldTop, newTop) = result
        val tops = fromMove(frames)
        val old = oldTop.toInt()
        val new = newTop.toInt()
        println("$what pill tops (old $old, new $new): $tops")
        assertTrue("$what: never above the old row or below the new one: $tops", tops.all { it in old..new })
        assertTrue("$what: only moves down: $tops", tops.zipWithNext().all { (a, b) -> b >= a })
        // The glide starts in the frame of the move: its first drawn frame has covered about a quarter of the way, the same for every step (a step that starts a gap off jumps it).
        assertTrue("$what: starts at the old row: $tops", tops.first() >= old && tops.first() - old <= (new - old) * FIRST_FRAME_SHARE)
        assertTrue("$what: still travelling after a third of the glide: $tops", tops[framesFor(PlumMotion.GLIDE_MS) / 3] < new)
        assertEquals("$what: on the new row within the glide and a frame (${PlumMotion.GLIDE_MS} ms): $tops", new, tops[framesFor(PlumMotion.GLIDE_MS) + 1])
        assertEquals("$what: ends exactly on the row: $tops", new, tops.last())
        assertTrue("$what: the first frame is not on the new row: $tops", tops.first() < new)
    }

    @Test
    fun theGamesPillGlidesFromTheOldRowToTheNewOneInTheGlide() {
        assertGlide("games", oneDown(true, Column.Games, HomeTags.GAMES, HomeTags::game, FakeHomeContent.BOARD_SNES_INDEX, 0))
    }

    @Test
    fun theSystemsPillGlidesFromTheOldRowToTheNewOneInTheGlide() {
        assertGlide("systems", oneDown(true, Column.Systems, HomeTags.SYSTEMS, HomeTags::system, 0, 0))
    }

    /** The step over the gap above the first system: the real distance is a row and the gap, and the first frame must not jump the gap. */
    @Test
    fun theSystemsPillGlidesFromTheLastEntryOfTheTopGroupToTheFirstSystem() {
        val content = FakeHomeContent.board()
        val last = content.firstSystemIndex - 1
        assertGlide("apps to first system", oneDown(true, Column.Systems, HomeTags.SYSTEMS, HomeTags::system, 0, last, content, preMoves = last))
    }

    @Test
    fun theGamesPillGlidesWithTheSystemInfoBandShown() {
        assertGlide("games with band", oneDown(true, Column.Games, HomeTags.GAMES, HomeTags::game, FakeHomeContent.BOARD_SNES_INDEX, 0, FakeHomeContent.board().withSystemInfo()))
    }

    @Test
    fun withAnimationsOffTheGamesPillIsOnTheNewRowInTheFirstFrame() {
        val (frames, _, newTop) = oneDown(false, Column.Games, HomeTags.GAMES, HomeTags::game, FakeHomeContent.BOARD_SNES_INDEX, 0)
        val tops = fromMove(frames)
        assertEquals("games, animations off: on the new row at once: $tops", List(tops.size) { newTop.toInt() }, tops)
    }

    @Test
    fun withAnimationsOffTheSystemsPillIsOnTheNewRowInTheFirstFrame() {
        val (frames, _, newTop) = oneDown(false, Column.Systems, HomeTags.SYSTEMS, HomeTags::system, 0, 0)
        val tops = fromMove(frames)
        assertEquals("systems, animations off: on the new row at once: $tops", List(tops.size) { newTop.toInt() }, tops)
    }

    @Test
    fun aMoveDuringAMoveStartsFromWherePillIsDrawnAndNeverSnapsBack() {
        show(FakeHomeContent.board(), true, HomeTags.GAMES, Column.Games, FakeHomeContent.BOARD_SNES_INDEX)
        val ring = ringColour(HomeTags.game(0))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        // Midway to row 1 the focus moves on to row 2.
        val tops =
            fromMove(record(framesFor(PlumMotion.GLIDE_MS) + 8, ring) { i -> if (i == 6) compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) } })
        println("retarget pill tops: $tops")
        assertTrue("never goes back: $tops", tops.zipWithNext().all { (a, b) -> b >= a })
        assertEquals("ends on row 2: $tops", rowTop(HomeTags.game(2)).toInt(), tops.last())
    }

    @Test
    fun aHeldKeyKeepsThePillWithTheFocusWithinARowAndNeverGoesBack() {
        val content = FakeHomeContent.large(LARGE_GAMES)
        val system = FakeHomeContent.BOARD_SNES_INDEX
        show(content, true, HomeTags.GAMES, Column.Games, system)
        val ring = ringColour(HomeTags.game(0))
        val stride = rowTop(HomeTags.game(1)) - rowTop(HomeTags.game(0))
        compose.mainClock.autoAdvance = false
        val worst = mutableListOf<String>()
        var last = -1
        val tops =
            fromMove(record(40, ring) { i -> if (i % HOLD_REPEAT_FRAMES == 0) compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) } })
        // The focused row's drawn place now (its tag is on the row, translation included).
        val focused = state.focus.rowOf(content.entries[system].id)
        val rowNow = rowTop(HomeTags.game(focused))
        tops.forEachIndexed { i, top ->
            if (last >= 0 && top < last - 1) worst += "frame $i went back from $last to $top"
            last = top
        }
        println("held pill tops: $tops")
        assertTrue("the focus moved down several rows: $focused", focused >= 5)
        assertEquals("never goes back: $worst", emptyList<String>(), worst)
        assertTrue("within a row of the focused row at the end: pill ${tops.last()} row $rowNow stride $stride", abs(rowNow - tops.last()) <= stride + 1)
    }

    @Test
    fun theGlideRecomposesNothingAfterTheFrameOfTheMove() {
        show(FakeHomeContent.board(), true, HomeTags.GAMES, Column.Games, FakeHomeContent.BOARD_SNES_INDEX)
        val ring = ringColour(HomeTags.game(0))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        val frames = record(framesFor(PlumMotion.GLIDE_MS) + 4, ring)
        val move = frames.indexOfFirst { it.rows > 0 }
        assertTrue("the move was composed: $frames", move >= 0)
        val travelling = fromMove(frames)
        assertTrue("the pill travelled for several frames: $travelling", travelling.distinct().size > 5)
        assertEquals("compositions per frame after the move's own: $frames", List(frames.size - move - 1) { 0 }, frames.drop(move + 1).map { it.composed })
    }

    /** The mean y of the halo just left of the list (pixels that differ from the empty background beside it), or -1 when there is none. */
    private fun haloCentreLeftOfList(): Float {
        val list = listBounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = list.left.toInt() - HALO_PROBE_PX
        // The page colour from the screen's side margin: the systems column beside the list may scroll a number under any probe nearer.
        val bg = pixels[2, list.top.toInt() + 1]
        val ys = (list.top.toInt() until list.bottom.toInt()).filter { y -> pixels[x, y] != bg }
        return if (ys.isEmpty()) -1f else ys.average().toFloat()
    }

    /** The halo reaches past the pill's sides (the pill is as wide as the column) and stays with the pill in every frame of a glide. */
    @Test
    fun theGamesHaloIsDrawnOutsideTheListAndMovesWithThePill() {
        show(FakeHomeContent.board(), true, HomeTags.GAMES, Column.Games, FakeHomeContent.BOARD_SNES_INDEX)
        val ring = ringColour(HomeTags.game(0))
        assertTrue("at rest some halo is left of the list", haloCentreLeftOfList() >= 0f)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
        compose.mainClock.autoAdvance = true
        val gaps = mutableListOf<Float>()
        val tops = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        repeat(framesFor(PlumMotion.GLIDE_MS) + 2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val top = pillTop(ring)
            val centre = haloCentreLeftOfList()
            // The halo reaches 2.5 sigmas above the pill: while the pill is nearer the list's top than that, the list's edge cuts the part this
            // measurement can see, which moves the centre down without the halo leaving the pill.
            val reach = (2.5f * SlabDimens.glowSigma.value * compose.density.density)
            if (top >= 0 && centre >= 0f && top - listBounds().top >= reach) {
                gaps += centre - top
                tops += top.toFloat()
            }
        }
        println("halo centre minus pill top per frame: $gaps")
        assertTrue("measured in several frames: $gaps", gaps.size > 5)
        // A halo one frame behind or ahead is off by that frame's step, at mid-glide the largest one;
        // anything under half of it is edge anti-aliasing, which differs between renderers (Linux CI
        // spreads 1.5 px where macOS spreads 1).
        val fastestStep = tops.zipWithNext { a, b -> kotlin.math.abs(b - a) }.max()
        assertTrue("the pill glides several pixels a frame: $tops", fastestStep >= 6f)
        assertTrue("the halo never lags or leads the pill by a frame: $gaps (fastest step $fastestStep)", gaps.max() - gaps.min() < fastestStep / 2)
    }

    /** An L2/R2 system switch onto a page built ahead in idle time, as at rest on a device: no halo is left on the old page. */
    @Test
    fun noHaloIsLeftOnTheOldPageWhileTheSystemSwitches() = assertNoHaloLeftBehind(neighboursBuilt = true)

    /** The same onto a page the switch builds in its own frame (a fast hold, no idle time between). */
    @Test
    fun noHaloIsLeftOnTheOldPageWhenTheSwitchBuildsTheNewPage() = assertNoHaloLeftBehind(neighboursBuilt = false)

    /** An L2/R2 system switch: in every frame, a halo left of the list belongs to a pill drawn in the list (none is left on the old page). */
    private fun assertNoHaloLeftBehind(neighboursBuilt: Boolean) {
        show(FakeHomeContent.board(), true, HomeTags.GAMES, Column.Games, FakeHomeContent.BOARD_SNES_INDEX)
        val ring = ringColour(HomeTags.game(0))
        compose.mainClock.autoAdvance = false
        if (neighboursBuilt) idle()
        compose.runOnIdle { state.dispatch(Action.NextSystem) }
        var measured = 0
        repeat(framesFor(PlumMotion.GLIDE_MS) + 2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val halo = haloCentreLeftOfList()
            if (halo >= 0f) {
                // While the new page fades in its pill is too faint for the pixels to place: then the focused row, laid out in the list, places it.
                val top = pillTop(ring).takeIf { it >= 0 } ?: focusedRowTopInList()
                assertTrue("a halo with no pill in the list (halo at $halo)", top >= 0)
                assertTrue("the halo ($halo) is beside the pill ($top), not left on the old page", abs(halo - top) <= listBounds().height / 4)
            }
            measured++
            if (neighboursBuilt) idle()
        }
        assertTrue("frames were measured", measured > 5)
        assertTrue("pages were asked of the prefetcher: ${prefetch.scheduled}", prefetch.scheduled > 0)
    }


    /** The top of the focused row (the page's first, on a page not visited yet) when it is laid out inside the list's bounds, else -1. */
    private fun focusedRowTopInList(): Int {
        val list = listBounds()
        val row = compose.onAllNodesWithTag(HomeTags.game(0), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        return row.firstOrNull { abs(it.left - list.left) < list.width / 4 && it.top >= list.top && it.top < list.bottom }?.top?.let(Math::round) ?: -1
    }

    /** Idle time between frames: the prefetcher builds the pages beside the shown one to the end. */
    private fun idle() = compose.runOnUiThread { prefetch.runIdle() }

    /** With the game panel open the row is not focused in the list: no pill and no halo. */
    @Test
    fun noListHaloIsDrawnWhileTheGamePanelIsOpen() {
        // At 576 dp the panel covers the whole screen and its own row's halo is all there is to see.
        Assume.assumeTrue("the panel leaves the list in view", panelLeavesTheListInView)
        val panel = OpenPanel(GameRef("1", "Super Metroid"), PanelScreen.AppMenu(canUninstall = false))
        show(FakeHomeContent.board(), true, HomeTags.GAMES, Column.Games, FakeHomeContent.BOARD_SNES_INDEX, panel)
        assertEquals("with the panel open there is no halo", -1f, haloCentreLeftOfList(), 0f)
    }

    /** How many pixels of the column [dx] px outside the list's left or right edge differ from the empty background beside it. */
    private fun haloPixelsAt(
        dx: Int,
        right: Boolean,
    ): Int {
        val list = listBounds()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = if (right) list.right.toInt() + dx else list.left.toInt() - dx
        val gap = if (right) HALO_BG_GAP_PX else -HALO_BG_GAP_PX
        val bg = pixels[x + gap, list.top.toInt() + 1]
        return (list.top.toInt() until list.bottom.toInt()).count { y -> pixels[x, y] != bg }
    }

    /** Halo pixels left and right of the list with the focus [downs] steps below the first row. */
    private fun haloAtEnds(
        animationsOn: Boolean,
        downs: Int,
    ): List<Int> {
        show(FakeHomeContent.board(), animationsOn, HomeTags.GAMES, Column.Games, FakeHomeContent.BOARD_SNES_INDEX)
        repeat(downs) {
            compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
            compose.waitForIdle()
        }
        return listOf(haloPixelsAt(HALO_PROBE_PX, false), haloPixelsAt(HALO_PROBE_PX, true))
    }

    /** With animations off the halo still reaches past both sides of the list: the row's own clipped halo is not what shows. */
    @Test
    fun withAnimationsOffTheHaloReachesPastBothEndsOfThePillOnTheFirstRow() = assertHaloAtBothEnds(0)

    @Test
    fun withAnimationsOffTheHaloReachesPastBothEndsOfThePillOnTheLastRow() = assertHaloAtBothEnds(MANY_DOWNS)

    private fun assertHaloAtBothEnds(downs: Int) {
        val ends = haloAtEnds(false, downs)
        assertTrue("animations off, $downs downs: halo pixels left and right of the list: $ends", ends.all { it > 0 })
    }

    private companion object {
        const val MANY_DOWNS = 60
        const val HALO_PROBE_PX = 3
        const val HALO_BG_GAP_PX = 60
        const val FRAME_MS = 16
        const val FIRST_FRAME_SHARE = 0.27f
        const val HOLD_REPEAT_FRAMES = 6
        const val LARGE_GAMES = 3600
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GlidingPillFramesTest : GlidingPillFramesBase()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GlidingPillFrames576Test : GlidingPillFramesBase() {
    override val panelLeavesTheListInView = false
}
