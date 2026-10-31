package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.secondscreen.SecondScreenFeed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout C on the top screen: while the second screen's window is up, a list has no details
 * column and no narrow details stop, and takes the no-details width (`width − 96`); the
 * grid is unchanged; when the window goes the column is back in the very next frame, on the same
 * game, and the list's rows are not recomposed by either change.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeDetailsOnSecondScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var windowUp by mutableStateOf(false)
    private lateinit var state: StripState

    /** The layout the screen drew with (the Thor starts at Large text, which widens the peek). */
    private lateinit var layout: PlumLayout

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun hasDetails(): Boolean = compose.onAllNodesWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, TOLERANCE)

    private fun show(
        actions: List<Action>,
        gridIds: Set<String> = emptySet(),
        content: HomeContent = FakeHomeContent.withArtwork(),
    ) {
        compose.setContent {
            PlumTheme(MediumText.Default) {
                layout = PlumTheme.layout
                HomeAt(content, actions, gridIds = gridIds, onState = { state = it }, detailsOnSecondScreen = { windowUp })
            }
        }
        compose.waitForIdle()
    }

    /** Flips the window state and runs exactly one frame: what the person sees right after the change. */
    private fun flipForOneFrame(up: Boolean) {
        compose.mainClock.autoAdvance = false
        windowUp = up
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeByFrame()
    }

    // The widths at the reference sizes (docs/design/rules.md › Reference sizes).

    @Test
    fun listWidthsAtTheReferenceSizes() {
        val sizes =
            listOf(
                Triple(PlumLayout(768.dp, 432.dp), 400.dp, 672.dp),
                Triple(PlumLayout(837.dp, 471.dp), 469.dp, 741.dp),
                // From 640 dp the list beside the details ends inside the right margin (395); without details beside it the width is as before.
                Triple(PlumLayout(667.dp, 375.dp), 395.dp, 571.dp),
                Triple(PlumLayout(576.dp, 432.dp), 400.dp, 480.dp),
            )
        for ((layout, beside, alone) in sizes) {
            val at = "at ${layout.width}"
            assertDp("$at: beside the details", beside, homeListWidth(layout, hasBoxArt = true))
            assertDp("$at: window up, the no-details width", alone, homeListWidth(layout, hasBoxArt = true, detailsOnSecondScreen = true))
            assertDp("$at: no box art is unchanged", alone, homeListWidth(layout, hasBoxArt = false, detailsOnSecondScreen = true))
            assertDp("$at: the strip's list", alone, homeStripGeometry(layout, hasBoxArt = true, detailsOnSecondScreen = true).gamesListWidth)
            assertDp("$at: a grid is unchanged", layout.gridAreaWidth, homeGamesWidth(layout, true, showsGrid = true, detailsOnSecondScreen = true))
        }
    }

    @Test
    fun onlyListsLoseTheirDetailsStop() {
        val content = FakeHomeContent.withArtwork()
        val grids = setOf("snes")
        val strip = content.stripWith(grids, PlumLayout(576.dp, 432.dp)).withDetailsOnSecondScreen(content, grids)
        val byId = strip.systems.associateBy { it.id }
        assertTrue("the grid keeps its stop", byId.getValue("snes").hasDetails)
        assertFalse("a list has none", byId.getValue("psp").hasDetails)
        assertFalse("Recent's list has none", byId.getValue(HomeContent.RECENT_ID).hasDetails)
        assertFalse("Apps never had one", byId.getValue(HomeContent.APPS_ID).hasDetails)
    }

    // The Thor's top screen (Wide).

    @Test
    @Config(qualifiers = THOR)
    fun thorListTakesTheColumnsWidthWhileTheWindowIsUp() {
        windowUp = true
        show(BoardGamesFocused)
        assertFalse("no details column", hasDetails())
        val row = bounds(HomeTags.GAMES)
        assertDp("list after the peek and its gap", layout.peek + 24.dp, row.left)
        assertDp("to the right margin (837 − 96 = 741 at the default peek)", 837.dp - 24.dp, row.right)
        assertDp("the no-details width", layout.listWidthWithoutArt, row.right - row.left)
    }

    @Test
    @Config(qualifiers = THOR)
    fun thorColumnComesBackInTheNextFrameOnTheSameGame() {
        windowUp = true
        show(BoardGamesFocused)
        val focusedTop = bounds(HomeTags.game(FakeHomeContent.BOARD_REMEMBERED_ROW)).top
        flipForOneFrame(up = false)
        assertTrue("the column is drawn in the first frame", hasDetails())
        val row = bounds(HomeTags.GAMES)
        assertDp("and the list is already beside it (837 − 368 = 469 at the default peek)", layout.gamesListWidth, row.right - row.left)
        assertDp("the list did not move", layout.peek + 24.dp, row.left)
        assertEquals(Column.Games to FakeHomeContent.BOARD_REMEMBERED_ROW, state.focus.column to state.focus.row)
        assertDp("the focused row did not move", focusedTop, bounds(HomeTags.game(FakeHomeContent.BOARD_REMEMBERED_ROW)).top)
        flipForOneFrame(up = true)
        assertFalse("gone again in one frame", hasDetails())
        assertDp("the no-details width again", layout.listWidthWithoutArt, bounds(HomeTags.GAMES).let { it.right - it.left })
        assertEquals(FakeHomeContent.BOARD_REMEMBERED_ROW, state.focus.row)
    }

    /** One read at the layout level: the rows are re-measured at the new width, not recomposed. */
    @Test
    @Config(qualifiers = THOR)
    fun theRowsAreNotRecomposedWhenTheWindowComesOrGoes() {
        show(BoardGamesFocused)
        val calls = mutableListOf<String>()
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
        val perFlip = mutableListOf<Int>()
        for (up in listOf(true, false, true, false)) {
            calls.clear()
            windowUp = up
            compose.waitForIdle()
            perFlip += calls.count { it.startsWith("com.plumemu.home.GamesListItem ") }
        }
        // The first change of the list's width after the pages were built re-runs the shown page's
        // visible items once (cause not found: a first recomposition that keeps the width runs none,
        // theFirstRecompositionForAnotherReasonRerunsNoRow). Since the guess before the first frame
        // (MainActivity) a launch has no such change; switching the second screen on or off later
        // does it once. Every later change of the window runs no row.
        assertTrue("at most the shown page's rows the first time: $perFlip", perFlip[0] <= ROWS_ON_THOR_MAX)
        assertEquals("no row after that: $perFlip", listOf(0, 0, 0), perFlip.drop(1))
    }

    /**
     * The first recomposition of the strip for a reason other than the
     * width (a new content object with the same entries) re-runs no row. So the one-time re-run on
     * the window's first change comes with the list's new width, not with any first recomposition.
     */
    @Test
    @Config(qualifiers = THOR)
    fun theFirstRecompositionForAnotherReasonRerunsNoRow() {
        val first = FakeHomeContent.withArtwork()
        var content by mutableStateOf(first)
        compose.setContent {
            PlumTheme(MediumText.Default) { HomeAt(content, BoardGamesFocused, onState = { state = it }) }
        }
        compose.waitForIdle()
        val calls = traceCompositions()
        content = first.withEntries(first.entries)
        compose.waitForIdle()
        val strip = calls.count { it.startsWith("com.plumemu.home.HomeStrip ") }
        val rows = calls.count { it.startsWith("com.plumemu.home.GamesListItem ") }
        println("S4b note 3: same entries, new content: HomeStrip ran $strip, GamesListItem ran $rows")
        assertTrue("the strip recomposed", strip > 0)
        assertEquals("rows re-run by a first recomposition that keeps the width", 0, rows)
    }

    /** Layout C's guess before the first frame (MainActivity): a feed already true draws the list alone from frame one, with no extra row composition. */
    @Test
    @Config(qualifiers = THOR)
    fun aFeedGuessedShownDrawsTheListAloneFromTheFirstFrame() {
        lateinit var feed: SecondScreenFeed
        val calls = traceCompositions()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val router = remember { InputRouter(scope) }
            feed = remember { SecondScreenFeed(scope).also { it.setWindowShown(true) } }
            PlumTheme(MediumText.Default) {
                layout = PlumTheme.layout
                HomeScreen(FakeHomeContent.withArtwork(), router, HomeCallbacks(), secondScreen = feed)
            }
        }
        compose.mainClock.advanceTimeByFrame()
        assertFalse("no column in the first frame", hasDetails())
        val firstFrameRows = calls.count { it.startsWith("com.plumemu.home.GamesListItem ") }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertFalse(hasDetails())
        assertEquals("no row composed twice at startup", firstFrameRows, calls.count { it.startsWith("com.plumemu.home.GamesListItem ") })
    }

    private fun traceCompositions(): MutableList<String> {
        val calls = mutableListOf<String>()
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info
                }

                override fun traceEventEnd() = Unit
            },
        )
        return calls
    }

    @Test
    @Config(qualifiers = THOR)
    fun thorGridIsUnchanged() {
        val actions = gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X)
        show(actions, gridIds = setOf("snes"))
        val without = bounds(HomeTags.GRID)
        flipForOneFrame(up = true)
        assertEquals("the grid's area", without, bounds(HomeTags.GRID))
        assertEquals(MEGA_MAN_X, state.focus.row)
    }

    @Test
    @Config(qualifiers = THOR)
    fun noBoxArtLosesItsTextColumnLikeBoxArt() {
        // A wide no-art list has the text-only details column,
        // so the window takes it away as it does the box-art column.
        show(BoardGamesFocused, content = FakeHomeContent.board())
        assertTrue("the text-only column", hasDetails())
        assertDp("the list beside it, width − 368", layout.gamesListWidth, bounds(HomeTags.GAMES).let { it.right - it.left })
        flipForOneFrame(up = true)
        assertFalse("no details column", hasDetails())
        assertDp("the full no-details width", layout.listWidthWithoutArt, bounds(HomeTags.GAMES).let { it.right - it.left })
        flipForOneFrame(up = false)
        assertTrue("back in one frame", hasDetails())
    }

    // A narrow screen (the Nova): the details stop.

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFocusOnTheDetailsStopGoesBackToTheListOnTheSameGame() {
        show(BoardGamesFocused + Action.Move(Direction.Right))
        assertEquals(Column.Details, state.focus.column)
        flipForOneFrame(up = true)
        assertEquals(Column.Games to FakeHomeContent.BOARD_REMEMBERED_ROW, state.focus.column to state.focus.row)
        assertFalse("no details stop drawn", hasDetails())
        val row = bounds(HomeTags.GAMES)
        assertDp("the list at the games stop in that same frame", 72.dp, row.left)
        assertDp("576 − 96 = 480", 480.dp, row.right - row.left)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowRightAndASwipeFindNoDetailsStop() {
        windowUp = true
        show(BoardGamesFocused)
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        assertEquals("Right does nothing", Column.Games to FakeHomeContent.BOARD_REMEMBERED_ROW, state.focus.column to state.focus.row)
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals("a swipe left settles on the list", Column.Games, state.focus.column)
        assertDp("resting at the games stop", 72.dp, bounds(HomeTags.GAMES).left)
        assertFalse(hasDetails())
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowStopComesBackWhenTheWindowGoesWithTheCursorOnTheList() {
        show(BoardGamesFocused + Action.Move(Direction.Right))
        flipForOneFrame(up = true)
        flipForOneFrame(up = false)
        assertTrue("the cover peeks in again", hasDetails())
        assertEquals("the cursor stays on the list", Column.Games to FakeHomeContent.BOARD_REMEMBERED_ROW, state.focus.column to state.focus.row)
        assertDp("list 400 beside the peek", 400.dp, bounds(HomeTags.GAMES).let { it.right - it.left })
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        assertEquals("the stop is there again", Column.Details, state.focus.column)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowGridKeepsItsDetailsStop() {
        windowUp = true
        show(gridActions(FakeHomeContent.BOARD_SNES_INDEX, FINAL_FANTASY_VI), gridIds = setOf("snes"))
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        // Right moves inside the grid first; from its last column it reaches the stop.
        compose.runOnIdle { repeat(4) { if (state.focus.column == Column.Games) state.dispatch(Action.Move(Direction.Right)) } }
        compose.waitForIdle()
        assertEquals(Column.Details, state.focus.column)
        assertTrue(hasDetails())
    }

    // The real wiring: HomeScreen reads the feed's window state.

    @Test
    @Config(qualifiers = THOR)
    fun homeScreenFollowsTheFeedsWindowState() {
        lateinit var feed: SecondScreenFeed
        lateinit var router: InputRouter
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            feed = remember { SecondScreenFeed(scope) }
            PlumTheme(MediumText.Default) {
                layout = PlumTheme.layout
                HomeScreen(FakeHomeContent.withArtwork(), router, HomeCallbacks(), secondScreen = feed)
            }
        }
        compose.waitForIdle()
        BoardGamesFocused.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
        assertTrue("no window: the column", hasDetails())
        compose.runOnIdle { feed.setWindowShown(true) }
        compose.waitForIdle()
        assertFalse("window up: list only", hasDetails())
        assertDp("the no-details width", layout.listWidthWithoutArt, bounds(HomeTags.GAMES).let { it.right - it.left })
        compose.runOnIdle { feed.setWindowShown(false) }
        compose.waitForIdle()
        assertTrue("window gone: the column again", hasDetails())
    }

    private companion object {
        const val THOR = "w837dp-h471dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.5f

        /** More than the rows that fit on the Thor's top screen at any text size (about 9 at Medium). */
        const val ROWS_ON_THOR_MAX = 12
    }
}
