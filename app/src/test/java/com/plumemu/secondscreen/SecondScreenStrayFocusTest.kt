package com.plumemu.secondscreen

import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * The controller stays on the top screen: the bottom window never takes the focus or reacts to a tap, except that while Plumemu has
 * lost the controller to the bottom display it catches the focus left there and hands it back.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenStrayFocusTest : SecondScreenHostTestBase() {
    private var handedBack = 0
    private var resumed = true
    private var moves = 0
    private var moveResult: () -> Boolean = { true }

    private fun flagsOf(host: SecondScreenHost): Int = host.shownWindow!!.window!!.attributes.flags

    private fun isFocusable(host: SecondScreenHost): Boolean = flagsOf(host) and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0

    private fun catcher(): StrayFocusCatcher =
        StrayFocusCatcher(
            activity,
            isResumed = { resumed },
            moveToFront = {
                moves++
                moveResult()
            },
            info = { logs += it },
            warn = { logs += "W: $it" },
        )

    private fun waitMs(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun theWindowTakesNoFocusAndEveryTapIsTakenAndIgnored() {
        val host = startHost(onStrayFocus = { handedBack++ })
        assertFalse("not focusable", isFocusable(host))
        val content = composeViewOf(host.shownWindow!!)
        assertFalse("the content takes no focus", content.isFocusable || content.isFocusableInTouchMode)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
            assertTrue("taken: nothing under the window gets it", content.dispatchTouchEvent(MotionEvent.obtain(0, 0, action, 300f, 300f, 0)))
        }
        assertFalse("a tap does not make it focusable", isFocusable(host))
        assertEquals(0, handedBack)
    }

    @Test
    fun catchingMakesTheWindowFocusableUntilItStops() {
        val host = startHost()
        host.catchesStrayFocus = true
        assertTrue(isFocusable(host))
        host.catchesStrayFocus = false
        assertFalse(isFocusable(host))
    }

    @Test
    fun aFocusLandingWhileCatchingGoesToTheOwnerWhichStopsCatching() {
        lateinit var catcher: StrayFocusCatcher
        val host = startHost(onStrayFocus = { catcher.takeControllerBack() })
        catcher = catcher().also { it.host = host }
        controller.windowFocusChanged(true)
        catcher.onTopResumedChanged(false)
        waitMs(StrayFocusCatcher.DELAY_MS + 50)
        host.shownWindow!!.onWindowFocusChanged(true)
        assertEquals(1, moves)
        assertFalse("both stop catching together", catcher.isCatching || host.catchesStrayFocus)
        assertFalse("not focusable again", isFocusable(host))
        assertEquals(
            listOf(
                "Second screen window: the controller was left on display $displayId; giving it back to the top screen",
                "Controller left on the second screen: taken back to the top screen",
            ),
            logs.takeLast(2),
        )
        host.shownWindow!!.onWindowFocusChanged(true)
        assertEquals("a landing after the catch is ignored", 1, moves)
    }

    @Test
    fun aFocusLandingWhileNotCatchingIsIgnored() {
        val host = startHost(onStrayFocus = { handedBack++ })
        host.shownWindow!!.onWindowFocusChanged(true)
        assertEquals(0, handedBack)
    }

    @Test
    fun aWindowMadeWhileCatchingMayTakeTheFocus() {
        settings.value = on(false)
        val host = startHost()
        host.catchesStrayFocus = true
        settings.value = on(true)
        idle()
        assertTrue(isFocusable(host))
    }

    @Test
    fun losingTheControllerWhileStillResumedCatchesAfterTheDelay() {
        val host = startHost()
        val catcher = catcher().also { it.host = host }
        catcher.onTopResumedChanged(false)
        waitMs(StrayFocusCatcher.DELAY_MS - 50)
        assertFalse("not at once", catcher.isCatching)
        assertFalse(isFocusable(host))
        waitMs(100)
        assertTrue(catcher.isCatching)
        assertTrue(isFocusable(host))
        catcher.onTopResumedChanged(true)
        assertFalse("back on top: never focusable", isFocusable(host))
    }

    /** Measured on the Thor: Settings brought to the front made the window focusable for 23 ms before onPause. */
    @Test
    fun anActivityComingInFrontHereNeverMakesTheWindowFocusable() {
        val host = startHost()
        val catcher = catcher().also { it.host = host }
        catcher.onTopResumedChanged(false)
        resumed = false
        catcher.onPause()
        waitMs(StrayFocusCatcher.DELAY_MS * 2)
        assertFalse(catcher.isCatching)
        assertFalse(isFocusable(host))
    }

    @Test
    fun aHostMadeLaterGetsTheCatchingState() {
        val catcher = catcher()
        catcher.onTopResumedChanged(false)
        waitMs(StrayFocusCatcher.DELAY_MS + 50)
        val host = startHost()
        catcher.host = host
        assertTrue(host.catchesStrayFocus)
    }

    @Test
    fun theTaskMovesToTheFrontOnlyWhileResumedWithThisScreensFocus() {
        val catcher = catcher()
        controller.windowFocusChanged(false)
        catcher.takeControllerBack()
        assertEquals("another window has this screen's focus", 0, moves)
        assertEquals("Controller left on the second screen: not taken back (this screen's window has no focus), trying again", logs.last())
        controller.windowFocusChanged(true)
        resumed = false
        catcher.takeControllerBack()
        assertEquals("paused", 0, moves)
        resumed = true
        catcher.takeControllerBack()
        assertEquals(1, moves)
        assertEquals("Controller left on the second screen: taken back to the top screen", logs.last())
    }

    @Test
    fun aRefusedMoveIsLoggedNeverThrown() {
        val catcher = catcher()
        controller.windowFocusChanged(true)
        moveResult = { throw SecurityException("test") }
        catcher.takeControllerBack()
        assertEquals("Controller left on the second screen: not taken back (SecurityException), trying again", logs.last())
        moveResult = { false }
        catcher.takeControllerBack()
        assertEquals("Controller left on the second screen: not taken back (our task was not found), trying again", logs.last())
    }

    @Test
    fun theControllerBackWithinTheDelayNeverMakesTheWindowFocusable() {
        val host = startHost()
        val catcher = catcher().also { it.host = host }
        catcher.onTopResumedChanged(false)
        waitMs(StrayFocusCatcher.DELAY_MS / 2)
        catcher.onTopResumedChanged(true)
        waitMs(StrayFocusCatcher.DELAY_MS * 2)
        assertFalse(catcher.isCatching)
        assertFalse(isFocusable(host))
    }

    @Test
    fun resumedWithoutTheControllerCatchesAfterTheDelay() {
        val catcher = catcher()
        catcher.onResume()
        waitMs(StrayFocusCatcher.DELAY_MS + 50)
        assertTrue("back from a game while the bottom display holds it", catcher.isCatching)
    }

    @Test
    fun resumedWithTheControllerNeverCatches() {
        val catcher = catcher()
        catcher.onResume()
        catcher.onTopResumedChanged(true)
        waitMs(StrayFocusCatcher.DELAY_MS * 2)
        assertFalse(catcher.isCatching)
    }

    @Test
    fun aFailedHandBackIsTriedAgainThenGivenUpOnce() {
        val catcher = catcher()
        controller.windowFocusChanged(true)
        moveResult = { false }
        catcher.onTopResumedChanged(false)
        repeat(StrayFocusCatcher.TRIES) {
            waitMs(StrayFocusCatcher.RETRY_MS + 50)
            assertTrue("caught again for try ${it + 1}", catcher.isCatching)
            catcher.takeControllerBack()
            assertFalse(catcher.isCatching)
        }
        assertEquals(StrayFocusCatcher.TRIES, moves)
        waitMs(StrayFocusCatcher.RETRY_MS * 4)
        assertFalse("given up", catcher.isCatching)
        assertEquals(
            "W: Controller left on the second screen: gave up after 3 tries (our task was not found); a tap on the top screen brings it back",
            logs.last(),
        )
        assertEquals(1, logs.count { it.contains("gave up") })
    }

    @Test
    fun aHandBackThatChangesNothingIsTriedAgain() {
        val catcher = catcher()
        controller.windowFocusChanged(true)
        catcher.onTopResumedChanged(false)
        waitMs(StrayFocusCatcher.DELAY_MS + 50)
        catcher.takeControllerBack()
        assertEquals(1, moves)
        waitMs(StrayFocusCatcher.RETRY_MS * 2 + 50)
        assertTrue("Android never said it is on top again: caught again", catcher.isCatching)
        catcher.onTopResumedChanged(true)
        assertFalse(catcher.isCatching)
        waitMs(StrayFocusCatcher.RETRY_MS * 4)
        assertFalse("back on top: no more tries", catcher.isCatching)
    }
}
