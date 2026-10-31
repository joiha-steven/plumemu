package com.plumemu.coreinput

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The router while a screen takes text (Search with Plumemu's keyboard):
 * the B button erases and repeats only while [TextEntry.Keyboard] is asked for; Back and Escape stay
 * Back; every other button and every other screen ([TextEntry.None]) is exactly as before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TextEntryRouterTest {
    private class Harness(
        test: TestScope,
        settings: InputSettings,
    ) {
        val routerJob = Job(test.coroutineContext[Job])
        val router = InputRouter(CoroutineScope(test.coroutineContext + routerJob), settings, clock = { test.testScheduler.currentTime }, onDebug = {}) { error("dropped $it") }
        val received = mutableListOf<Pair<Long, Action>>()

        init {
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) {
                router.actions.collect { received += test.testScheduler.currentTime to it }
            }
        }

        val actions: List<Action> get() = received.map { it.second }
        val isIdle: Boolean get() = routerJob.children.none() && !router.isRepeating

        fun press(key: Int) {
            router.onKey(key, isDown = true, repeatCount = 0)
            router.onKey(key, isDown = false, repeatCount = 0)
        }

        fun down(key: Int) = router.onKey(key, isDown = true, repeatCount = 0)

        fun up(key: Int) = router.onKey(key, isDown = false, repeatCount = 0)
    }

    private fun routerTest(
        settings: InputSettings = InputSettings(),
        block: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        val harness = Harness(this, settings)
        try {
            block(harness)
        } finally {
            harness.routerJob.cancel()
        }
    }

    private val faceButtons = listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y)

    @Test
    fun withNoTextEntryEveryButtonIsAsBefore() =
        routerTest { h ->
            faceButtons.forEach(h::press)
            h.press(KeyEvent.KEYCODE_BACK)
            assertEquals(listOf(Action.Enter, Action.Back, Action.Search, Action.ToggleFavorite, Action.Back), h.actions)
        }

    @Test
    fun whileTheKeyboardIsUpBErasesAndOnlyB() =
        routerTest { h ->
            h.router.textEntry = TextEntry.Keyboard
            faceButtons.forEach(h::press)
            h.press(KeyEvent.KEYCODE_BACK)
            h.press(KeyEvent.KEYCODE_ESCAPE)
            h.press(KeyEvent.KEYCODE_BUTTON_START)
            h.press(KeyEvent.KEYCODE_BUTTON_SELECT)
            h.press(KeyEvent.KEYCODE_BUTTON_L1)
            h.press(KeyEvent.KEYCODE_BUTTON_R1)
            h.press(KeyEvent.KEYCODE_BUTTON_L2)
            h.press(KeyEvent.KEYCODE_BUTTON_R2)
            assertEquals(
                "A types, B erases, X and Y as printed; the system's Back and Escape still go back",
                listOf(
                    Action.Enter, Action.Erase(), Action.Search, Action.ToggleFavorite, Action.Back, Action.Back,
                    Action.Menu, Action.ToggleView, Action.PreviousSystem, Action.NextSystem, Action.JumpLetterPrevious, Action.JumpLetterNext,
                ),
                h.actions,
            )
        }

    @Test
    fun withConfirmOnBTheButtonPrintedAErasesAndXYStay() =
        routerTest(InputSettings(swapAB = true)) { h ->
            h.router.textEntry = TextEntry.Keyboard
            faceButtons.forEach(h::press)
            assertEquals(listOf(Action.Erase(), Action.Enter, Action.Search, Action.ToggleFavorite), h.actions)
        }

    @Test
    fun onAFieldWithTheKeyboardDownBStillGoesBack() =
        routerTest { h ->
            h.router.textEntry = TextEntry.Field
            h.press(KeyEvent.KEYCODE_BUTTON_B)
            h.router.textEntry = TextEntry.None
            h.press(KeyEvent.KEYCODE_BUTTON_B)
            assertEquals(listOf(Action.Back, Action.Back), h.actions)
        }

    @Test
    fun aHeldBErasesOnTheSameFramesAsAHeldDirectionMovesAndStopsOnRelease() =
        routerTest { h ->
            h.router.textEntry = TextEntry.Keyboard
            h.down(KeyEvent.KEYCODE_BUTTON_B)
            advanceTimeBy(1_000)
            runCurrent()
            h.up(KeyEvent.KEYCODE_BUTTON_B)
            runCurrent()
            assertTrue("nothing runs after the release", h.isIdle)
            val erases = h.received.toList()
            h.received.clear()
            h.down(KeyEvent.KEYCODE_DPAD_LEFT)
            advanceTimeBy(1_000)
            runCurrent()
            h.up(KeyEvent.KEYCODE_DPAD_LEFT)
            runCurrent()
            val moves = h.received.toList()
            assertEquals(Action.Erase(), erases.first().second)
            assertTrue("then only repeats", erases.drop(1).all { it.second == Action.Erase(isRepeat = true) })
            assertTrue("it repeats", erases.size > 5)
            fun gaps(list: List<Pair<Long, Action>>) = list.map { it.first - list.first().first }
            assertEquals("the same timing as a held direction", gaps(moves), gaps(erases))
            assertTrue(h.isIdle)
        }

    @Test
    fun leavingTheKeyboardStopsAHeldBAtOnce() =
        routerTest { h ->
            h.router.textEntry = TextEntry.Keyboard
            h.down(KeyEvent.KEYCODE_BUTTON_B)
            advanceTimeBy(500)
            runCurrent()
            val before = h.actions.size
            // Search closed on the fresh press, or the tray went down: no erase may reach the next screen.
            h.router.textEntry = TextEntry.None
            runCurrent()
            assertTrue("the repeat stopped", h.isIdle)
            advanceTimeBy(2_000)
            runCurrent()
            assertEquals(before, h.actions.size)
            h.up(KeyEvent.KEYCODE_BUTTON_B)
            h.press(KeyEvent.KEYCODE_BUTTON_B)
            assertEquals(Action.Back, h.actions.last())
        }

    @Test
    fun leavingTheKeyboardDoesNotStopAHeldDirection() =
        routerTest { h ->
            h.router.textEntry = TextEntry.Keyboard
            h.down(KeyEvent.KEYCODE_DPAD_DOWN)
            advanceTimeBy(100)
            h.router.textEntry = TextEntry.None
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue(h.actions.count { it == Action.Move(Direction.Down, isRepeat = true) } > 3)
            h.up(KeyEvent.KEYCODE_DPAD_DOWN)
            runCurrent()
            assertTrue(h.isIdle)
        }

    @Test
    fun aBHeldBeforeTheKeyboardCameUpGoesBackOnceAndDoesNotRepeat() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_BUTTON_B)
            h.router.textEntry = TextEntry.Keyboard
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(listOf(Action.Back), h.actions)
            h.up(KeyEvent.KEYCODE_BUTTON_B)
            assertTrue(h.isIdle)
        }
}
