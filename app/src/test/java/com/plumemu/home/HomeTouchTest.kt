package com.plumemu.home

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.SystemRowTags
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.strip.rememberStripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.AppOpenOutcome
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
 * The whole screen with a real router: taps, long presses, hints and strip drags go through
 * core-input like buttons do; games play and open their menu, apps open through the same controller.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeTouchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private val apps = FakeAppActions()
    private var settingsOpened = 0
    private lateinit var router: InputRouter

    private fun show(content: HomeContent = FakeHomeContent.board()) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = androidx.compose.runtime.remember { InputRouter(scope) }
            PlumTheme {
                HomeScreen(
                    content,
                    router,
                    HomeCallbacks(
                        apps = apps,
                        games = games,
                        onSettings = { settingsOpened++ },
                    ),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun tap(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }

    @Test
    fun tapFocusesThenSecondTapPlays() {
        show()
        tap(HomeTags.system(3))
        tap(HomeTags.system(3)) // a second tap on the focused system opens its games
        tap(HomeTags.game(2))
        assertEquals("first tap on a game only focuses it", emptyList<String>(), games.launched)
        tap(HomeTags.game(2))
        compose.waitForIdle()
        assertEquals(listOf("snes-2"), games.launched)
    }

    /** The selector runs to the screen edge, so a tap on the margin left of a label (x = 4 dp) selects the row. */
    @Test
    fun tapOnTheMarginLeftOfALabelSelectsTheRow() {
        show()
        compose.onNodeWithTag(HomeTags.system(4), useUnmergedTree = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(4.dp.toPx(), centerY)) }
        compose.waitForIdle()
        compose.onNodeWithTag(SystemRowTags.PILL, useUnmergedTree = true).assertExists()
        compose.onNode(hasTestTag(SystemRowTags.PILL) and hasAnyAncestor(hasTestTag(HomeTags.system(4))), useUnmergedTree = true).assertExists()
    }

    @Test
    fun longPressOpensTheGameMenu() {
        show()
        tap(HomeTags.system(3))
        tap(HomeTags.system(3))
        compose.onNodeWithTag(HomeTags.game(4), useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).assertExists()
        val title = FakeHomeContent.board().entries[FakeHomeContent.BOARD_SNES_INDEX].rows[4].title
        compose.onNodeWithTag(GamePanelTags.TITLE, useUnmergedTree = true).assertTextEquals(title)
        assertEquals(emptyList<String>(), games.launched)
    }

    @Test
    fun hintsDoWhatTheirButtonsDo() {
        show()
        compose.onNodeWithTag(HomeTags.hint(Action.OpenSettings), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, settingsOpened)
        compose.onNodeWithTag(HomeTags.hint(Action.Enter), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        // Now on the games of Recent: A plays its first row.
        compose.onNodeWithTag(HomeTags.hint(Action.Play), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("recent-0"), games.launched)
    }

    @Test
    fun emptyLibraryShowsTheMessageAndSettings() {
        show(FakeHomeContent.noGames())
        compose.onNodeWithTag(HomeTags.EMPTY_MESSAGE, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(HomeTags.hint(Action.OpenSettings), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(1, settingsOpened)
    }

    /** Apps works before any ROM folder is added. */
    @Test
    fun withNoGamesAppsIsReachableAndOpens() {
        show(FakeHomeContent.noGames())
        compose.onNodeWithTag(HomeTags.STRIP).assertExists()
        tap(HomeTags.system(2))
        tap(HomeTags.system(2)) // second tap opens the apps list
        compose.onNodeWithTag(HomeTags.hint(Action.Play), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("the Apps entry's first row", listOf("app-0"), apps.opened)
        assertEquals("apps are not games", emptyList<String>(), games.launched)
    }

    /** A tap on an app's row focuses it; a second tap opens it (as for games). */
    @Test
    fun tapOnAnAppsRowFocusesThenSecondTapOpensIt() {
        show(FakeHomeContent.noGames())
        tap(HomeTags.system(2))
        tap(HomeTags.system(2))
        tap(HomeTags.game(1))
        compose.waitForIdle()
        assertEquals("first tap on an app only focuses it", emptyList<String>(), apps.opened)
        tap(HomeTags.game(1))
        compose.waitForIdle()
        assertEquals(listOf("app-1"), apps.opened)
        assertEquals("nothing to say after a start", false, isPanelOpen())
    }

    /** An app that could not open says so over the list; B, the Back hint and a tap outside close it, and the app stays focused. */
    @Test
    fun anAppThatCouldNotOpenSaysSoAndBOrATapOutsideClosesIt() {
        apps.outcome = { AppOpenOutcome.Missing }
        show(FakeHomeContent.noGames())
        tap(HomeTags.system(2))
        tap(HomeTags.system(2))
        tap(HomeTags.game(1))
        tap(HomeTags.game(1))
        compose.waitForIdle()
        assertEquals(true, isPanelOpen())
        compose.onNodeWithTag(GamePanelTags.TITLE, useUnmergedTree = true).assertTextEquals("Calculator is no longer installed")

        router.send(Action.Back)
        compose.waitForIdle()
        assertEquals("B closes it", false, isPanelOpen())

        tap(HomeTags.game(1))
        compose.waitForIdle()
        assertEquals("the same app is still focused: one tap opens it again", listOf("app-1", "app-1"), apps.opened)
        compose.onNodeWithTag(HomeTags.hint(Action.Back), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals("the Back hint closes it", false, isPanelOpen())

        tap(HomeTags.game(1))
        compose.waitForIdle()
        tap(GamePanelTags.OUTSIDE)
        compose.waitForIdle()
        assertEquals("a tap outside closes it", false, isPanelOpen())
        assertEquals(listOf("app-1", "app-1", "app-1"), apps.opened)
    }

    private fun isPanelOpen() = compose.onAllNodesWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun dragMovesTheStripAndSettlesOnAColumn() {
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                val geometry = homeStripGeometry(PlumTheme.layout, hasBoxArt = false)
                state = rememberStripState(FakeHomeContent.board().strip, geometry, isMotionOn = false, PlumTheme.motion.slideSpec())
                HomeLayout(FakeHomeContent.board(), state, {}, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(Column.Games, state.focus.column)
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(Column.Systems, state.focus.column)
    }
}
