package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Changing the focused system (D-pad in the system column, L1/R1 in the games list) with about
 * 555 games a system. Building the new system's games list in the frame of the change made that
 * frame miss the Thor's 8.3 ms at 120 Hz (build 129: 16 % janky frames, all slow UI thread), so
 * the games list keeps one page per entry, builds the pages beside the shown one ahead and keeps
 * the page it left (GamesPages.kt). Robolectric gives Compose's prefetcher no idle time, so no page
 * is built ahead here: these check what is asked for and kept, that the pages survive a change
 * (a list rebuilt per system, the old cause, fails them), and that each system's list comes back
 * where it was left. SystemSwitchBenchmark measures the frames on a device.
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSystemSwitchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val content = FakeHomeContent.large()
    private val snes = FakeHomeContent.BOARD_SNES_INDEX
    private val calls = mutableListOf<String>()
    private lateinit var state: StripState

    @Before
    fun traceCompositions() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    calls += info.substringBefore(' ')
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private fun show(actions: List<Action>) {
        compose.setContent { PlumTheme { HomeAt(content, onState = { state = it }) } }
        compose.waitForIdle()
        actions.forEach { act(it) }
    }

    private fun probe(): GamesPagesProbe {
        val list = compose.onNodeWithTag(HomeTags.GAMES, useUnmergedTree = true).fetchSemanticsNode()
        return checkNotNull(list.config.getOrNull(GamesPagesInfo)) { "the games list is not paged" }
    }

    private fun act(action: Action) {
        compose.runOnIdle { calls.clear() }
        compose.runOnIdle { state.dispatch(action) }
        compose.waitForIdle()
    }

    private fun count(function: String): Int = calls.count { it.endsWith(".$function") }

    private fun id(index: Int): String = content.entries[index].id

    private fun assertFirstRowIs(title: String) {
        compose.onNode(hasText(title) and hasAnyAncestor(hasTestTag(HomeTags.game(0)))).assertExists()
    }

    /** The first row whose top is inside the games list's window (only the shown page's rows carry tags). */
    private fun topRow(): Int {
        val list = compose.onNodeWithTag(HomeTags.GAMES, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        return (0 until content.entries[snes].rows.size).first { row ->
            compose.onAllNodesWithTag(HomeTags.game(row)).fetchSemanticsNodes().any { it.boundsInRoot.top >= list.top - 0.5f }
        }
    }

    @Test
    fun theSystemsBesideTheFocusedOneAreAskedForAndThePageLeftIsKept() {
        show(listOf(Action.TapItem(Column.Systems, snes)))
        assertEquals(setOf(id(snes - 1), id(snes + 1)), probe().ahead())
        act(Action.Move(Direction.Down))
        assertEquals("the page left is kept, the next one asked for", setOf(id(snes), id(snes + 2)), probe().ahead())
    }

    @Test
    fun theFirstEntryHasOneNeighbour() {
        show(emptyList())
        assertEquals(setOf(id(1)), probe().ahead())
    }

    /**
     * The old cause: a games list built per system (`key(entry.id)` around the list) threw the
     * list away on every change and built the next one in the change's frame. Here the pages'
     * state lives on, and going back to the system just left builds nothing: its page was kept.
     */
    @Test
    fun aChangeKeepsThePagesAndGoingBackBuildsNoPage() {
        show(listOf(Action.TapItem(Column.Systems, snes)))
        val pages = probe().pages
        act(Action.Move(Direction.Down))
        assertSame("the same pages after a change", pages, probe().pages)
        act(Action.Move(Direction.Up))
        assertSame(pages, probe().pages)
        assertEquals("built going back: ${calls.filter { "EntryGames" in it || "GameRow" in it }}", 0, count("GameRow"))
        assertFirstRowIs(content.entries[snes].rows.first().title)
    }

    @Test
    fun aDpadMoveShowsTheNextSystemsGamesAtOnceAndOnlyThem() {
        show(listOf(Action.TapItem(Column.Systems, snes)))
        act(Action.Move(Direction.Down))
        assertEquals(snes + 1, state.focus.systemIndex)
        // One list carries the row tags: the shown one (the page left is kept, without semantics).
        assertEquals(1, compose.onAllNodesWithTag(HomeTags.game(0)).fetchSemanticsNodes().size)
        assertFirstRowIs(content.entries[snes + 1].rows.first().title)
        // At most the new list is built: the rows that fit on screen (plus the glide's extra
        // rows), never the whole system or every list.
        assertTrue("game rows built: ${count("GameRow")}", count("GameRow") <= ROWS_ON_SCREEN_AT_MOST)
    }

    @Test
    fun r1WithTheGamesFocusedShowsTheNextSystemsGamesAtOnce() {
        show(listOf(Action.TapItem(Column.Systems, snes), Action.Enter))
        assertEquals(Column.Games, state.focus.column)
        act(Action.NextSystem)
        assertEquals(snes + 1, state.focus.systemIndex)
        assertEquals(setOf(id(snes), id(snes + 2)), probe().ahead())
        assertFirstRowIs(content.entries[snes + 1].rows.first().title)
        assertTrue("game rows built: ${count("GameRow")}", count("GameRow") <= ROWS_ON_SCREEN_AT_MOST)
    }

    /**
     * Frames go by after changes (the glide, the fade, any page work): the row tags stay on one
     * list, in the unmerged tree too (where a kept page's rows are still present and only
     * `gameRowTag` keeps their tags off), and the frames do reach the new list.
     */
    @Test
    fun theRowTagsStayOnOneListAcrossFrames() {
        show(listOf(Action.TapItem(Column.Systems, snes)))
        compose.mainClock.autoAdvance = false
        listOf(Action.Move(Direction.Down) to snes + 1, Action.Move(Direction.Down) to snes + 2, Action.Move(Direction.Up) to snes + 1).forEachIndexed { step, (action, shown) ->
            compose.runOnIdle { state.dispatch(action) }
            repeat(FRAMES) { frame ->
                compose.mainClock.advanceTimeByFrame()
                val rows = compose.onAllNodesWithTag(HomeTags.game(0), useUnmergedTree = true).fetchSemanticsNodes().size
                // The test clock's first frame composes before the lazy row lays out the new page.
                if (frame == 0) assertTrue("move $step, frame 0: $rows", rows <= 1) else assertEquals("move $step, frame $frame", 1, rows)
            }
            val first = content.entries[shown].rows.first().title
            compose.onNode(hasText(first) and hasAnyAncestor(hasTestTag(HomeTags.game(0))), useUnmergedTree = true).assertExists("move $step: the tag is on ${content.entries[shown].name}'s list")
        }
    }

    /**
     * A kept page is built but not shown, and hides its rows from the (merged) semantics tree: a
     * game in both Recent and Favorites is found once, in the shown list. (Compose keeps cleared
     * nodes in the unmerged tree; there only the row tags follow the shown page.)
     */
    @Test
    fun aGameInRecentAndFavoritesIsFoundOnceWhileRecentIsKept() {
        val board = FakeHomeContent.board()
        val recent = board.entries[0].rows.take(ROWS_BUILT).map { it.title }
        val favorites = board.entries[1].rows.take(ROWS_BUILT).map { it.title }
        val title = recent.first { it in favorites }
        compose.setContent { PlumTheme { HomeAt(board, onState = { state = it }) } }
        compose.waitForIdle()
        act(Action.Move(Direction.Down))
        assertEquals(1, state.focus.systemIndex)
        assertTrue("Recent's page is kept beside Favorites", board.entries[0].id in probe().ahead())
        assertEquals("two pages built", 2, compose.onAllNodesWithTag(HomeTags.GAMES_PAGE, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("\"$title\" found", 1, compose.onAllNodes(inAList(title)).fetchSemanticsNodes().size)
        compose.onNode(inAList(title)).assertExists()
        act(Action.Move(Direction.Up))
        assertTrue("Favorites' page is kept beside Recent", board.entries[1].id in probe().ahead())
        // Recent lists some games twice: found as often as Recent's rows in the tree have it.
        val inRecent = board.entries[0].rows.indices.count { i -> board.entries[0].rows[i].title == title && compose.onAllNodesWithTag(HomeTags.game(i)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("back on Recent, not in the kept Favorites", inRecent, compose.onAllNodes(inAList(title)).fetchSemanticsNodes().size)
    }

    /** [title] in a games list, not in the text-only details column that names the focused game at 768. */
    private fun inAList(title: String) = hasText(title) and !hasAnyAncestor(hasTestTag(HomeTags.DETAILS))

    /** SNES's games: down to row 30, back up to 21 (the list stays scrolled), then R1 away. */
    private fun scrollSnesThenSwitchAway(): Int {
        act(Action.TapItem(Column.Systems, snes))
        act(Action.Enter)
        repeat(30) { act(Action.Move(Direction.Down)) }
        repeat(9) { act(Action.Move(Direction.Up)) }
        assertEquals(21, state.focus.row)
        val top = topRow()
        act(Action.NextSystem)
        assertEquals(snes + 1, state.focus.systemIndex)
        return top
    }

    @Test
    fun goingBackToASystemShowsItsListWhereItWasLeft() {
        show(emptyList())
        val top = scrollSnesThenSwitchAway()
        act(Action.PreviousSystem)
        assertEquals(snes, state.focus.systemIndex)
        assertEquals(21, state.focus.row)
        assertEquals("same first row", top, topRow())
    }

    /** Further away than a neighbour: the page is dropped and built again where it was left. */
    @Test
    fun aSystemFarAwayComesBackWhereItWasLeft() {
        show(emptyList())
        val top = scrollSnesThenSwitchAway()
        repeat(3) { act(Action.NextSystem) }
        repeat(4) { act(Action.PreviousSystem) }
        assertEquals(snes, state.focus.systemIndex)
        assertEquals(21, state.focus.row)
        assertEquals("same first row", top, topRow())
    }

    /** A game launch or a rotation saves and restores the screen in between: the position is held, not rebuilt. */
    @Test
    fun theListPositionSurvivesASaveAndRestore() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PlumTheme { HomeAt(content, onState = { state = it }) } }
        compose.waitForIdle()
        val top = scrollSnesThenSwitchAway()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        act(Action.PreviousSystem)
        assertEquals(snes, state.focus.systemIndex)
        assertEquals(21, state.focus.row)
        assertEquals("same first row after a restore", top, topRow())
    }

    private companion object {
        /** 432 dp high: about ten rows, plus one extra at each edge for the glide. */
        const val ROWS_ON_SCREEN_AT_MOST = 14
        const val FRAMES = 12

        /** Rows a page builds at 432 dp high, at least: a title among them is in the tree when its page is shown. */
        const val ROWS_BUILT = 8
    }
}
