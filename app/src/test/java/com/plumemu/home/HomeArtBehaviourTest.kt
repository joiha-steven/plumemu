package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.FakeAppActions
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SELECT, grid moves and the details stop through a real router: the grid is
 * remembered per system, moves go by columns, Recent / Favorites / Apps have no grid, and
 * without box art SELECT does nothing (the alpha's screen).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeArtBehaviourTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val apps = FakeAppActions()
    private lateinit var router: InputRouter

    /** [content] on the main screen; the grid at [gridSize] (the board's landscape grid, 3 columns on the RP5, is Large). */
    private fun show(
        content: HomeContent,
        gridSize: GridSize = GridSize.Default,
    ) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Default.copy(gridSize = gridSize)) {
                HomeScreen(
                    content,
                    router,
                    HomeCallbacks(apps = apps, games = games),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Super Nintendo's games focused from a fresh screen. */
    private fun openSnes() =
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    @Test
    fun selectSwitchesToTheGridAndBack() {
        show(FakeHomeContent.withArtwork())
        openSnes()
        assertEquals("a list first", 0, count(HomeTags.GRID))
        assertEquals(true, hasText("Grid view"))
        send(Action.ToggleView)
        assertEquals("SELECT shows the grid", 1, count(HomeTags.GRID))
        assertEquals("its hint offers the list", true, hasText("List view"))
        compose.onNodeWithTag(HomeTags.hint(Action.ToggleView), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("tapping the hint goes back to the list", 0, count(HomeTags.GRID))
    }

    @Test
    fun theGridIsRememberedPerSystem() {
        show(FakeHomeContent.withArtwork())
        openSnes()
        // In the grid L1 R1 change its size: the system changes from the systems column.
        send(Action.ToggleView, Action.Back, Action.JumpLetterNext)
        assertEquals("Game Boy Advance keeps its list", 0, count(HomeTags.GRID))
        send(Action.JumpLetterPrevious)
        assertEquals("Super Nintendo comes back as a grid", 1, count(HomeTags.GRID))
    }

    @Test
    fun gridMovesGoByColumns() {
        show(FakeHomeContent.withArtwork(), GridSize.Large)
        openSnes()
        send(Action.ToggleView, Action.Move(Direction.Down), Action.Move(Direction.Right), Action.Play)
        assertEquals("down one row of 3, right one: cover 4 (Mega Man X)", listOf("snes-$MEGA_MAN_X"), games.launched)
    }

    @Test
    fun gridMovesGoByColumnsAtTheDefaultSize() {
        show(FakeHomeContent.withArtwork())
        openSnes()
        // Medium on the RP5: 4 landscape columns.
        send(Action.ToggleView, Action.Move(Direction.Down), Action.Play, Action.Move(Direction.Right), Action.Play, Action.Move(Direction.Right), Action.Move(Direction.Right), Action.Move(Direction.Left), Action.Play)
        assertEquals("down one row of 4 (4), right (5), right twice and left once (6)", listOf("snes-4", "snes-5", "snes-6"), games.launched)
    }

    @Test
    fun aGridMoveFollowsASizeChangedWhileTheGridIsShown() {
        var gridSize by mutableStateOf(GridSize.Medium)
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Default.copy(gridSize = gridSize)) {
                HomeScreen(FakeHomeContent.withArtwork(), router, HomeCallbacks(games = games))
            }
        }
        compose.waitForIdle()
        openSnes()
        send(Action.ToggleView)
        gridSize = GridSize.Large
        compose.waitForIdle()
        send(Action.Move(Direction.Down), Action.Play)
        gridSize = GridSize.Small
        compose.waitForIdle()
        send(Action.Move(Direction.Down), Action.Play)
        assertEquals("Large: down 3 (3); then Small: down 5 more (8)", listOf("snes-3", "snes-8"), games.launched)
    }

    /** Recent, Favorites and Apps switch like a system. */
    @Test
    fun recentAndAppsHaveAGrid() {
        show(FakeHomeContent.withArtwork())
        send(Action.Enter) // Recent's games
        assertEquals("the SELECT hint on Recent", true, hasText("Grid view"))
        send(Action.ToggleView)
        assertEquals("Recent's grid", 1, count(HomeTags.GRID))
        send(Action.Back, Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter) // Apps
        assertEquals("the SELECT hint on Apps", true, hasText("Grid view"))
        send(Action.ToggleView)
        assertEquals("Apps' grid", 1, count(HomeTags.GRID))
    }

    @Test
    fun withoutBoxArtSelectDoesNothing() {
        show(FakeHomeContent.board())
        openSnes()
        assertEquals("no SELECT hint", false, hasText("Grid view"))
        send(Action.ToggleView)
        assertEquals("no grid", 0, count(HomeTags.GRID))
        // At 768 the text-only details column stays beside the list: no cover in it.
        assertEquals("the text-only details column", 1, count(HomeTags.DETAILS))
        assertEquals("no cover", 0, count(HomeTags.DETAILS_COVER))
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun narrowDetailsStopShowsTheFactsAndOpensTheMenu() {
        show(FakeHomeContent.withArtwork())
        openSnes()
        send(*List(FakeHomeContent.BOARD_REMEMBERED_ROW) { Action.Move(Direction.Down) }.toTypedArray())
        assertEquals("the games stop keeps the edge fade over the cover peek", 1, count(HomeTags.EDGE_FADE))
        send(Action.Move(Direction.Right))
        assertEquals("no edge fade on the details stop (Nova-3-details)", 0, count(HomeTags.EDGE_FADE))
        assertEquals("Super Metroid's facts", true, hasText("Yesterday") && hasText("4 h 12 min") && hasText("Action, Adventure · 8.5/10"))
        assertEquals("START offers Menu", true, hasText("Menu"))
        send(Action.Menu)
        assertEquals("START opens the game menu", true, count(GamePanelTags.PANEL) == 1)
        send(Action.Back)
        assertEquals("B closes it", 0, count(GamePanelTags.PANEL))
        send(Action.Back, Action.Play)
        assertEquals("B goes back to the list, A plays", listOf("snes-${FakeHomeContent.BOARD_REMEMBERED_ROW}"), games.launched)
    }

    /** Apps' list from a fresh screen (Recent, Favorites, Apps). */
    private fun openApps() = send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)

    @Test
    fun appsHasNoDetailsColumnOnAWideScreen() {
        show(FakeHomeContent.withArtwork())
        openApps()
        assertEquals("no details beside the apps list", 0, count(HomeTags.DETAILS))
        openSnesFromApps()
        assertEquals("a system has them", 1, count(HomeTags.DETAILS))
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun appsHasNoDetailsStopOnANarrowScreen() {
        show(FakeHomeContent.withArtwork())
        openApps()
        assertEquals("no details column after the apps list", 0, count(HomeTags.DETAILS))
        send(Action.Move(Direction.Right), Action.Play)
        assertEquals("Right does nothing: A still opens the focused app (the Apps entry's first row) from the list", listOf("app-0"), apps.opened)
        assertEquals("no details appeared", 0, count(HomeTags.DETAILS))
    }

    private fun openSnesFromApps() = send(Action.JumpLetterNext)
}
