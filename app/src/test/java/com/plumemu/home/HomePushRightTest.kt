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
 * Settings › Controls › Push right to play on the real main screen: with it on, a list row needs two
 * fresh Rights to start (A still starts at once); with it off, Right does what it always did.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomePushRightTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private val right = Action.Move(Direction.Right)
    private val heldRight = Action.Move(Direction.Right, isRepeat = true)

    private fun show(pushRight: Boolean, content: HomeContent = FakeHomeContent.board()) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme {
                HomeScreen(content, router, HomeCallbacks(games = games), bars = HomeBars(pushRightToPlay = pushRight))
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

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Super Nintendo's list, in the games column. */
    private fun intoSnesList() = send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)

    private fun playedByA(): String {
        games.launched.clear()
        send(Action.Enter)
        return games.launched.single()
    }

    @Test
    fun offRightOnAGameDoesNotPlayAndNoHintIsShown() {
        show(pushRight = false)
        intoSnesList()
        send(right, right)
        assertTrue(games.launched.isEmpty())
        assertFalse(hasText("Twice to play"))
    }

    @Test
    fun onRightRightPlaysTheSameGameAsA() {
        show(pushRight = true)
        intoSnesList()
        send(Action.Move(Direction.Down))
        assertTrue("the hint bar says it", hasText("Twice to play"))
        send(right)
        assertTrue("one Right only arms", games.launched.isEmpty())
        assertTrue(hasText("Again to play"))
        send(right)
        val byRight = games.launched.single()
        assertEquals(playedByA(), byRight)
    }

    @Test
    fun onRightDownRightDoesNotPlayAndAHeldRightNeverDoes() {
        show(pushRight = true)
        intoSnesList()
        send(right, Action.Move(Direction.Down), right)
        assertTrue(games.launched.isEmpty())
        send(heldRight, heldRight, heldRight)
        assertTrue(games.launched.isEmpty())
    }

    @Test
    fun onTheRightThatEntersTheListArmsNothing() {
        show(pushRight = true)
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray())
        send(right)
        assertTrue("it entered the list", hasText("Twice to play"))
        assertFalse("and armed nothing", hasText("Again to play"))
        assertTrue(games.launched.isEmpty())
        send(right)
        assertTrue("now armed", hasText("Again to play"))
        assertTrue(games.launched.isEmpty())
    }

    @Test
    fun aGridKeepsRightForMovingAndShowsNoHint() {
        show(pushRight = true, content = FakeHomeContent.withArtwork())
        intoSnesList()
        send(Action.ToggleView, right, right)
        assertTrue(games.launched.isEmpty())
        assertFalse(hasText("Twice to play"))
    }

    @Test
    fun theArmingIsForgottenWhenTheScreenStops() {
        val owner = object : androidx.lifecycle.LifecycleOwner {
            val registry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
            override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.lifecycle.compose.LocalLifecycleOwner provides owner) {
                PlumTheme { HomeScreen(FakeHomeContent.board(), router, HomeCallbacks(games = games), bars = HomeBars(pushRightToPlay = true)) }
            }
        }
        compose.waitForIdle()
        intoSnesList()
        send(right)
        assertTrue(hasText("Again to play"))
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.CREATED }
        compose.waitForIdle()
        assertFalse("stopped: forgotten", hasText("Again to play"))
        compose.runOnUiThread { owner.registry.currentState = androidx.lifecycle.Lifecycle.State.RESUMED }
        compose.waitForIdle()
        send(right)
        assertTrue("a Right after waking only arms", games.launched.isEmpty())
    }
}
