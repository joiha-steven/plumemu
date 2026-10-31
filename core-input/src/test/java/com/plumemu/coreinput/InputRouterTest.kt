package com.plumemu.coreinput

import android.view.KeyEvent
import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keys, axes and hold-to-repeat through the router, on virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class InputRouterTest {
    private class Harness(
        val test: TestScope,
        settings: InputSettings,
        frameClock: MonotonicFrameClock? = null,
    ) {
        /** Parent of everything the router launches: no children means nothing is running. */
        val routerJob = Job(test.coroutineContext[Job])
        val dropped = mutableListOf<Action>()
        val debug = mutableListOf<String>()
        val router =
            InputRouter(
                CoroutineScope(test.coroutineContext + routerJob + (frameClock ?: EmptyCoroutineContext)),
                settings,
                clock = { test.testScheduler.currentTime },
                onDebug = { debug += it },
            ) { dropped += it }
        val received = mutableListOf<Pair<Long, Action>>()

        init {
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) {
                router.actions.collect { received += test.testScheduler.currentTime to it }
            }
        }

        val actions: List<Action> get() = received.map { it.second }
        val isIdle: Boolean get() = routerJob.children.none() && !router.isRepeating

        fun down(key: Int, repeatCount: Int = 0) = router.onKey(key, isDown = true, repeatCount = repeatCount)

        fun up(key: Int) = router.onKey(key, isDown = false, repeatCount = 0)

        fun finish() = routerJob.cancel()
    }

    private fun routerTest(
        settings: InputSettings = InputSettings(),
        frameClock: MonotonicFrameClock? = null,
        block: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        val harness = Harness(this, settings, frameClock)
        try {
            block(harness)
        } finally {
            harness.finish()
        }
    }

    private val up = Action.Move(Direction.Up)
    private val down = Action.Move(Direction.Down)

    @Test
    fun nothingRunsWhileNothingIsHeld() =
        routerTest { h ->
            assertTrue(h.isIdle)
            h.down(KeyEvent.KEYCODE_BUTTON_A)
            h.up(KeyEvent.KEYCODE_BUTTON_A)
            runCurrent()
            assertEquals(listOf(Action.Enter), h.actions)
            assertTrue("a button press starts no coroutine", h.isIdle)
        }

    @Test
    fun aTapMovesOnceAndLeavesNothingRunning() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_DPAD_UP)
            advanceTimeBy(HoldRepeat.FIRST_REPEAT_DELAY_MS - 1)
            h.up(KeyEvent.KEYCODE_DPAD_UP)
            runCurrent()
            assertTrue(h.isIdle)
            advanceTimeBy(10_000)
            assertEquals(listOf(up), h.actions)
        }

    /** The display frame each action came on, at [periodNanos] (the timer clock's frames are exact). */
    private fun Harness.frames(periodNanos: Long): List<Long> =
        received.map { (ms, _) -> Math.round(ms * 1_000_000.0 / periodNanos) }

    @Test
    fun holdingRepeatsAfter300MsThenSpeedsUpToTheFloorOnWholeFramesAt60Hz() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_DPAD_DOWN)
            advanceTimeBy(3_000)
            runCurrent()
            val frames = h.frames(SIXTY_HZ)
            // 300 ms is 18 frames; then 120, 96, 77, 61, 49, 39 ms as 7, 6, 5, 4, 3, 2 frames, then
            // the 2-frame floor (32 ms): every repeat on a frame, every move drawn.
            assertEquals(listOf(0L, 18L, 25L, 31L, 36L, 40L, 43L, 45L, 47L, 49L), frames.take(10))
            assertEquals("the floor holds to the end", listOf(2L), frames.drop(7).zipWithNext { a, b -> b - a }.distinct())
            assertEquals(listOf(120L, 96L, 77L, 61L, 49L, 39L, 32L, 32L), HoldRepeat.intervals(true).take(8).toList())
            assertEquals(down, h.actions.first())
            assertTrue(h.actions.drop(1).all { it == Action.Move(Direction.Down, isRepeat = true) })
            h.up(KeyEvent.KEYCODE_DPAD_DOWN)
            runCurrent()
            assertTrue(h.isIdle)
        }

    @Test
    fun at120HzTheSameFeelTakesTwiceTheFramesWithAFourFrameFloor() =
        routerTest(frameClock = TimerFrameClock(ONE_TWENTY_HZ)) { h ->
            h.down(KeyEvent.KEYCODE_DPAD_DOWN)
            advanceTimeBy(3_000)
            runCurrent()
            val frames = h.frames(ONE_TWENTY_HZ)
            // 300 ms is 36 frames; then 14, 12, 9, 7, 6, 5 frames and the floor of 4 (33 ms).
            assertEquals(listOf(0L, 36L, 50L, 62L, 71L, 78L, 84L, 89L, 93L, 97L), frames.take(10))
            assertEquals(listOf(4L), frames.drop(7).zipWithNext { a, b -> b - a }.distinct())
        }

    @Test
    fun aHeldKeyAsksOnlyForTheFramesAroundEachRepeat() {
        val frames = TimerFrameClock()
        routerTest(InputSettings(holdToScrollFaster = false), frames) { h ->
            h.down(KeyEvent.KEYCODE_DPAD_DOWN)
            advanceTimeBy(2_000)
            runCurrent()
            val repeats = h.actions.size - 1
            assertTrue("about 2 s of repeats: $repeats", repeats > 20)
            // It sleeps until two frames before each repeat: 3 frames per repeat, not 5.
            assertTrue("${frames.framesServed} frames for $repeats repeats", frames.framesServed <= 3 * (repeats + 1))
            h.up(KeyEvent.KEYCODE_DPAD_DOWN)
        }
    }

    @Test
    fun withAccelerationOffTheIntervalStaysConstant() =
        routerTest(InputSettings(holdToScrollFaster = false)) { h ->
            h.down(KeyEvent.KEYCODE_DPAD_DOWN)
            advanceTimeBy(1_000)
            runCurrent()
            val gaps = h.frames(SIXTY_HZ).zipWithNext { a, b -> b - a }
            assertEquals("the first repeat after 300 ms", 18L, gaps.first())
            assertTrue(gaps.drop(1).isNotEmpty())
            assertEquals("80 ms is 5 frames at 60 Hz", listOf(5L), gaps.drop(1).distinct())
        }

    @Test
    fun releasingStopsTheRepeatAndNoCoroutineIsLeft() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_DPAD_UP)
            advanceTimeBy(700)
            h.up(KeyEvent.KEYCODE_DPAD_UP)
            runCurrent()
            val count = h.actions.size
            assertTrue(h.isIdle)
            advanceTimeBy(60_000)
            assertEquals(count, h.actions.size)
        }

    @Test
    fun androidsOwnKeyRepeatsAreIgnored() =
        routerTest { h ->
            assertTrue(h.down(KeyEvent.KEYCODE_DPAD_UP))
            for (repeat in 1..5) assertTrue("still taken", h.down(KeyEvent.KEYCODE_DPAD_UP, repeatCount = repeat))
            advanceTimeBy(HoldRepeat.FIRST_REPEAT_DELAY_MS - 1)
            assertEquals(listOf(up), h.actions)
        }

    @Test
    fun windowFocusLossCancelsTheRepeat() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_DPAD_UP)
            advanceTimeBy(500)
            h.router.releaseAll()
            runCurrent()
            assertTrue(h.isIdle)
            val count = h.actions.size
            advanceTimeBy(5_000)
            // The key-up that arrives after focus comes back changes nothing.
            h.up(KeyEvent.KEYCODE_DPAD_UP)
            assertEquals(count, h.actions.size)
        }

    @Test
    fun theNewestDirectionTakesOverTheRepeat() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_DPAD_UP)
            advanceTimeBy(100)
            h.down(KeyEvent.KEYCODE_DPAD_DOWN)
            h.up(KeyEvent.KEYCODE_DPAD_UP)
            advanceTimeBy(HoldRepeat.FIRST_REPEAT_DELAY_MS + 1)
            assertEquals(listOf(up, down, Action.Move(Direction.Down, isRepeat = true)), h.actions)
            h.up(KeyEvent.KEYCODE_DPAD_DOWN)
            runCurrent()
            assertTrue(h.isIdle)
        }

    @Test
    fun aDirectionSentByKeyAndHatAtOnceActsOnce() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_DPAD_UP)
            h.router.onAxes(JoystickAxes(hatY = -1f))
            h.up(KeyEvent.KEYCODE_DPAD_UP)
            assertTrue("the hat still holds it", h.router.isRepeating)
            h.router.onAxes(JoystickAxes())
            runCurrent()
            assertEquals(listOf(up), h.actions)
            assertTrue(h.isIdle)
        }

    @Test
    fun aHeldStickActsLikeAHeldDpad() =
        routerTest { h ->
            h.router.onAxes(JoystickAxes(stickY = -AxisEdges.PRESS + 0.05f))
            assertTrue("inside the deadzone nothing happens", h.actions.isEmpty())
            h.router.onAxes(JoystickAxes(stickY = -0.9f))
            h.router.onAxes(JoystickAxes(stickY = -0.85f))
            advanceTimeBy(HoldRepeat.FIRST_REPEAT_DELAY_MS + 1)
            assertEquals(listOf(up, Action.Move(Direction.Up, isRepeat = true)), h.actions)
            // Easing off to 40 % (between release and press) keeps holding: no chatter.
            h.router.onAxes(JoystickAxes(stickY = -0.4f))
            assertTrue(h.router.isRepeating)
            h.router.onAxes(JoystickAxes(stickY = -0.1f, stickX = 0.05f))
            runCurrent()
            assertTrue(h.isIdle)
        }

    @Test
    fun theStickFollowsItsStrongestAxis() =
        routerTest { h ->
            h.router.onAxes(JoystickAxes(stickX = 0.8f, stickY = -0.6f))
            h.router.onAxes(JoystickAxes())
            h.router.onAxes(JoystickAxes(stickX = -0.3f, stickY = 0.7f))
            h.router.onAxes(JoystickAxes())
            assertEquals(listOf(Action.Move(Direction.Right), down), h.actions)
        }

    @Test
    fun triggersWorkAsAxesAndKeysWithoutDoubling() =
        routerTest { h ->
            h.router.onAxes(JoystickAxes(rightTrigger = 1f))
            h.down(KeyEvent.KEYCODE_BUTTON_R2)
            h.up(KeyEvent.KEYCODE_BUTTON_R2)
            h.router.onAxes(JoystickAxes(rightTrigger = 0.4f))
            h.router.onAxes(JoystickAxes(rightTrigger = 0f))
            h.router.onAxes(JoystickAxes(leftTrigger = 0.6f))
            h.router.onAxes(JoystickAxes())
            runCurrent()
            assertEquals(listOf(Action.JumpLetterNext, Action.JumpLetterPrevious), h.actions)
            assertTrue("triggers do not repeat", h.isIdle)
        }

    @Test
    fun theLayoutIsReadAtEachPress() =
        routerTest { h ->
            h.down(KeyEvent.KEYCODE_BUTTON_A)
            h.router.settings = InputSettings(ButtonLayout.Nintendo)
            h.up(KeyEvent.KEYCODE_BUTTON_A)
            h.down(KeyEvent.KEYCODE_BUTTON_A)
            h.up(KeyEvent.KEYCODE_BUTTON_A)
            assertEquals(listOf(Action.Enter, Action.Back), h.actions)
        }

    @Test
    fun backPressesWaitForASlowCollectorAndAreNotLost() =
        runTest {
            val routerJob = Job(coroutineContext[Job])
            val router = InputRouter(CoroutineScope(coroutineContext + routerJob)) { error("dropped $it") }
            val received = mutableListOf<Action>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                router.actions.collect {
                    delay(100)
                    received += it
                }
            }
            repeat(20) {
                router.onKey(KeyEvent.KEYCODE_BUTTON_B, isDown = true, repeatCount = 0)
                router.onKey(KeyEvent.KEYCODE_BUTTON_B, isDown = false, repeatCount = 0)
            }
            advanceTimeBy(5_000)
            assertEquals(List(20) { Action.Back }, received)
            routerJob.cancel()
        }

    @Test
    fun aStalledCollectorIsReportedNotSilent() =
        runTest {
            val routerJob = Job(coroutineContext[Job])
            val dropped = mutableListOf<Action>()
            val router = InputRouter(CoroutineScope(coroutineContext + routerJob)) { dropped += it }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { router.actions.collect { awaitCancellation() } }
            repeat(InputRouter.BUFFER_CAPACITY + 2) { router.send(Action.Back) }
            assertEquals(listOf(Action.Back), dropped)
            routerJob.cancel()
        }

    /** A router nobody collects yet, on virtual time; collect it later with [collectLater]. */
    private class Uncollected(
        val test: TestScope,
        parent: Job,
    ) {
        val debug = mutableListOf<String>()
        val router =
            InputRouter(
                CoroutineScope(test.coroutineContext + parent),
                clock = { test.testScheduler.currentTime },
                onDebug = { debug += it },
            ) { error("not a stall: $it") }
        val received = mutableListOf<Action>()

        fun collectLater() {
            test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { router.actions.collect { received += it } }
        }
    }

    @Test
    fun withoutACollectorActionsAreDroppedWithALogAndNotReplayedLater() =
        routerTest { h ->
            val late = Uncollected(this, h.routerJob)
            late.router.send(Action.Enter)
            late.router.onKey(KeyEvent.KEYCODE_DPAD_DOWN, isDown = true, repeatCount = 0)
            late.router.onKey(KeyEvent.KEYCODE_DPAD_DOWN, isDown = false, repeatCount = 0)
            assertEquals(listOf("No collector; dropped Enter", "No collector; dropped ${Action.Move(Direction.Down)}"), late.debug)
            late.collectLater()
            late.router.send(Action.Play)
            assertEquals(listOf(Action.Play), late.received)
            assertFalse(late.router.isRepeating)
        }

    @Test
    fun aBackPressedJustBeforeACollectorArrivesIsDeliveredOnce() =
        routerTest { h ->
            val late = Uncollected(this, h.routerJob)
            late.router.send(Action.Back)
            late.router.send(Action.Enter)
            advanceTimeBy(300)
            late.router.send(Action.Back)
            advanceTimeBy(InputRouter.PENDING_BACK_MAX_AGE_MS)
            late.collectLater()
            assertEquals("only the most recent Back, at exactly 1 s old", listOf(Action.Back), late.received)
            // A second collector does not get it again.
            val second = mutableListOf<Action>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { late.router.actions.collect { second += it } }
            assertTrue(second.isEmpty())
        }

    @Test
    fun aBackOlderThanOneSecondIsDroppedWithALog() =
        routerTest { h ->
            val late = Uncollected(this, h.routerJob)
            late.router.send(Action.Back)
            advanceTimeBy(InputRouter.PENDING_BACK_MAX_AGE_MS + 1)
            late.collectLater()
            assertTrue(late.received.isEmpty())
            assertEquals("No collector for 1001ms; dropped a pending Back", late.debug.last())
        }

    @Test
    fun aStickJitteringAround45DegreesKeepsOneDirectionAndItsRepeat() =
        routerTest { h ->
            h.router.onAxes(JoystickAxes(stickX = 0.70f, stickY = 0.71f))
            repeat(20) { i ->
                advanceTimeBy(25)
                val jitter = if (i % 2 == 0) JoystickAxes(stickX = 0.71f, stickY = 0.70f) else JoystickAxes(stickX = 0.70f, stickY = 0.71f)
                h.router.onAxes(jitter)
            }
            runCurrent()
            assertEquals(down, h.actions.first())
            assertTrue("no Right, and the repeat was never restarted: ${h.actions}", h.actions.drop(1).all { it == Action.Move(Direction.Down, isRepeat = true) })
            assertEquals("repeats at 300 and about 417 ms (7 frames later), as for a steady hold", 3, h.actions.size)
            assertTrue(h.router.isRepeating)
            // A clear turn does change direction.
            h.router.onAxes(JoystickAxes(stickX = 0.9f, stickY = 0.3f))
            assertEquals(Action.Move(Direction.Right), h.actions.last())
        }

    @Test
    fun aRestingSecondControllerDoesNotReleaseTheFirst() =
        routerTest { h ->
            h.router.onAxes(JoystickAxes(stickY = 1f), deviceId = 1)
            h.router.onAxes(JoystickAxes(), deviceId = 2)
            assertTrue("device 2 at rest leaves device 1 holding", h.router.isRepeating)
            advanceTimeBy(HoldRepeat.FIRST_REPEAT_DELAY_MS + 1)
            assertEquals(listOf(down, Action.Move(Direction.Down, isRepeat = true)), h.actions)
            h.router.onAxes(JoystickAxes(stickY = 1f), deviceId = 2)
            assertEquals("the same direction from a second device acts once", 2, h.actions.size)
            h.router.onAxes(JoystickAxes(), deviceId = 1)
            assertTrue("device 2 still holds it", h.router.isRepeating)
            h.router.onAxes(JoystickAxes(), deviceId = 2)
            runCurrent()
            assertTrue(h.isIdle)
        }

    private companion object {
        const val SIXTY_HZ = 16_666_666L
        const val ONE_TWENTY_HZ = 8_333_333L
    }
}
