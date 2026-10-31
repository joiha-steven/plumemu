package com.plumemu.home

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The narrow (576 x 432) details stop, where the details text shows (design/rules.md › Narrow): going into it for a
 * game with a one-line or a two-line title at Largest Serif runs the column once, in one frame. Before the stop the
 * peek shows the same text, composed before the stop. The 768 case is in `HomeRecompositionTest`
 * (this file is split from it by the 600-line limit and shares its counting helpers).
 */
@OptIn(InternalComposeTracingApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeRecompositionNarrowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val callTimes = mutableListOf<Pair<String, Long>>()

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
                    callTimes += info to compose.mainClock.currentTime
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stopTracing() = Composer.setTracer(null)

    private lateinit var state: StripState

    private fun show() {
        val largestSerif = PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = PlumFont.Serif)
        val content = FakeHomeContent.withArtwork()
        val snes = content.entries[FakeHomeContent.BOARD_SNES_INDEX]
        val titles = listOf(ONE_LINE_TITLE, TWO_LINE_TITLE)
        val renamed = snes.copy(rows = snes.rows.mapIndexed { i, row -> titles.getOrNull(i)?.let { row.copy(title = it) } ?: row })
        val titled = content.withEntries(content.entries.map { if (it.id == snes.id) renamed else it })
        val actions = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter)
        compose.setContent { PlumTheme(largestSerif) { HomeAt(titled, actions, onState = { state = it }) } }
        compose.waitForIdle()
    }

    private fun press(action: Action) {
        compose.runOnIdle { state.dispatch(action) }
        compose.waitForIdle()
    }

    private fun titleNodes() = compose.onAllNodesWithTagCount(HomeTags.DETAILS_TITLE)

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun aMoveAtTheGamesStopShowsTheDetailsTextInThePeekAndTheCoverToo() {
        show()
        press(Action.Move(Direction.Down))
        assertEquals("the title is composed in the peek (cut by the screen's edge)", 1, titleNodes())
        assertEquals("the cover still peeks", 1, compose.onAllNodesWithTagCount(HomeTags.DETAILS_COVER))
    }

    /** The peek has text now: a game move at the games stop re-runs two rows and the details column once, in one frame (as beside the column at 768). */
    @Test
    fun aGameMoveAtTheGamesStopRecomposesTheTwoRowsAndTheColumnOnce() {
        show()
        compose.runOnIdle { callTimes.clear() }
        press(Action.Move(Direction.Down))
        assertEquals("still the games stop", Column.Games, state.focus.column)
        fun ran(prefix: String) = callTimes.count { (info, _) -> info.startsWith(prefix) }
        assertEquals("list items run: only the two whose look changed", 2, ran("com.plumemu.home.GamesListItem "))
        assertEquals("game rows recomposed", 2, ran("com.plumemu.coreui.components.GameRow "))
        val parts = callTimes.filter { (info, _) -> info.isDetailsPart() && "Details" in info.substringBefore(' ') }
        assertEquals("the column ran once", 1, parts.count { it.first.substringBefore(' ') == "com.plumemu.home.DetailsColumn" })
        val tooOften = parts.map { it.first.substringBefore(' ') }.groupingBy { it }.eachCount().filter { (name, times) -> times > (CALLS_PER_PASS[name] ?: 1) }
        assertEquals("nothing runs more often than one pass calls it", emptyMap<String, Int>(), tooOften)
        assertEquals("all in one frame", 1, parts.map { it.second }.distinct().size)
        assertEquals("the title shows in the peek", 1, titleNodes())
    }

    @Test
    fun enteringTheDetailsStopRunsTheColumnOnceInOneFrameForOneAndTwoLineTitles() {
        show()
        press(Action.Move(Direction.Down))
        enterStopAndCheck("two-line title")
        press(Action.Back)
        press(Action.Move(Direction.Up))
        enterStopAndCheck("one-line title")
    }

    private fun enterStopAndCheck(what: String) {
        compose.runOnIdle { callTimes.clear() }
        press(Action.Move(Direction.Right))
        assertEquals("$what: the details stop", Column.Details, state.focus.column)
        // The strip slides into the stop, so the list rows and the count format run too: only the column's own functions count.
        val parts = callTimes.filter { (info, _) -> info.isDetailsPart() && "Details" in info.substringBefore(' ') }
        assertEquals("$what: the column ran once", 1, parts.count { it.first.substringBefore(' ') == "com.plumemu.home.DetailsColumn" })
        val tooOften = parts.map { it.first.substringBefore(' ') }.groupingBy { it }.eachCount().filter { (name, times) -> times > (CALLS_PER_PASS[name] ?: 1) }
        assertEquals("$what: nothing runs more often than one pass calls it", emptyMap<String, Int>(), tooOften)
        assertEquals("$what: all in one frame", 1, parts.map { it.second }.distinct().size)
        assertEquals("$what: a title shows", 1, titleNodes())
    }
}
