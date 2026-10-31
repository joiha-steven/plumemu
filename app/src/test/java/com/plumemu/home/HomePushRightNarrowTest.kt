package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.onAllNodesWithText
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Push right to play on a narrow screen with box art: Right in the list moves to the
 * details stop (it used to arm, which left the stop out of reach); there the first Right arms and the second plays.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomePushRightNarrowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private val right = Action.Move(Direction.Right)
    private val heldRight = Action.Move(Direction.Right, isRepeat = true)

    private fun show(pushRight: Boolean) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme {
                HomeScreen(FakeHomeContent.withArtwork(), router, HomeCallbacks(games = games), bars = HomeBars(pushRightToPlay = pushRight))
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

    /** The hint bar shows "Twice to play" at the details stop only (in the list Right goes there first). */
    private fun atDetailsStop() = compose.onAllNodesWithText("Twice to play", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun isArmed() = compose.onAllNodesWithText("Again to play", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun intoSnesList() = send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    @Test
    fun rightInTheListReachesTheDetailsStopAndArmsNothing() {
        show(pushRight = true)
        intoSnesList()
        assertFalse("in the list the hint waits for the details stop", atDetailsStop())
        send(right)
        assertTrue("the focus is at the details stop", atDetailsStop())
        assertFalse("Right went to the details stop, not an arming", isArmed())
        assertTrue(games.launched.isEmpty())
        // Left comes back to the list, where Right goes to the stop again.
        send(Action.Move(Direction.Left))
        assertFalse("Left is back in the list", atDetailsStop())
        send(right)
        assertTrue(atDetailsStop())
        assertFalse(isArmed())
        assertTrue(games.launched.isEmpty())
    }

    @Test
    fun atTheDetailsStopRightRightPlays() {
        show(pushRight = true)
        intoSnesList()
        send(right, right)
        assertTrue("the first Right at the stop arms", isArmed())
        assertTrue(games.launched.isEmpty())
        send(right)
        assertTrue("the second plays", games.launched.size == 1)
    }

    @Test
    fun theSecondRightPlaysTheGameTheStopShowsNotAnEarlierOne() {
        show(pushRight = true)
        intoSnesList()
        send(right, right, right)
        val first = games.launched.single()
        games.launched.clear()
        send(Action.Back, Action.Move(Direction.Down), right, right, right)
        val second = games.launched.single()
        assertTrue("another game than the first ($first)", second != first)
        games.launched.clear()
        send(Action.Enter)
        assertEquals("the same game A plays at this row", games.launched.single(), second)
    }

    @Test
    fun atTheDetailsStopRightThenAnotherKeyDoesNotPlayAndAHeldRightNeverDoes() {
        show(pushRight = true)
        intoSnesList()
        send(right, right, Action.Move(Direction.Down), right)
        assertTrue(games.launched.isEmpty())
        send(Action.Back, right, heldRight, heldRight, heldRight)
        assertTrue(games.launched.isEmpty())
    }

    @Test
    fun withTheSettingOffRightGoesToTheDetailsStopAndNothingArms() {
        show(pushRight = false)
        intoSnesList()
        send(right, right, right)
        assertFalse(isArmed())
        assertTrue(games.launched.isEmpty())
    }

    @Test
    fun aStillPlaysAtTheDetailsStop() {
        show(pushRight = true)
        intoSnesList()
        send(right, Action.Enter)
        assertTrue(games.launched.size == 1)
    }
}
