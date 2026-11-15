package com.plumemu.secondscreen

import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.drainMainLooper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The card gives the controller back to the top screen: at once while `MainActivity` is resumed,
 * never while a game is in front, a key received meanwhile reaching the top screen exactly once,
 * and failed or ineffective hand-backs tried three times, then one warning.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ControllerHandBackTest {
    private val logs = mutableListOf<String>()
    private val keys = mutableListOf<Int>()
    private val motions = mutableListOf<Int>()

    private inner class FakeTop : TopScreenSide {
        var resumed = true
        var moves = 0
        var moveResult: () -> Boolean = { true }

        override val isResumed: Boolean get() = resumed

        override fun moveToFront(): Boolean {
            moves++
            return moveResult()
        }

        override fun takeKey(event: KeyEvent): Boolean {
            keys += event.keyCode
            return true
        }

        override fun takeMotion(event: MotionEvent): Boolean {
            motions += event.action
            return true
        }
    }

    private val top = FakeTop()
    private val handBack = ControllerHandBack(displayId = { 4 }, top = { top }, info = { logs += it }, warn = { logs += "W: $it" })

    @After
    fun tearDown() {
        handBack.stop()
        drainMainLooper()
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun theControllerGoesBackAtOnceWhileTheTopScreenIsResumed() {
        handBack.onCardTop(true)
        assertEquals("no wait", 1, top.moves)
        assertEquals(
            listOf(
                "Second screen card: the controller is on display 4; giving it back to the top screen",
                "Controller on the second screen: taken back to the top screen",
            ),
            logs,
        )
        // Android says the card lost it: nothing more.
        handBack.onCardTop(false)
        idle(ControllerHandBack.RETRY_MS * 4)
        assertEquals(1, top.moves)
    }

    @Test
    fun neverWhileAGameIsInFrontOnTheTopScreen() {
        top.resumed = false
        handBack.onCardTop(true)
        idle(ControllerHandBack.RETRY_MS * 4)
        assertEquals("Plumemu's task is never moved over a game", 0, top.moves)
        assertEquals("Controller on the second screen: not taken back (Plumemu is not in front)", logs.last())
    }

    @Test
    fun noTopScreenNoMove() {
        val alone = ControllerHandBack(displayId = { 4 }, top = { null }, info = { logs += it }, warn = { logs += "W: $it" })
        alone.onCardTop(true)
        assertEquals("Controller on the second screen: not taken back (Plumemu is not in front)", logs.last())
        alone.stop()
    }

    @Test
    fun keysBeforeTheHandBackLandsReachTheTopScreenOnce() {
        handBack.onCardTop(true)
        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("taken by the card", handBack.onKey(down))
        assertTrue(handBack.onKey(up))
        assertEquals("each event once", listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN), keys)
        val hat = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0f, 0f, 0)
        assertTrue(handBack.onMotion(hat))
        assertEquals(listOf(MotionEvent.ACTION_MOVE), motions)
        hat.recycle()
    }

    @Test
    fun systemKeysAreLeftToAndroidAndNeverForwarded() {
        handBack.onCardTop(true)
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK)) {
            assertFalse("not taken: $code", handBack.onKey(KeyEvent(KeyEvent.ACTION_DOWN, code)))
            assertFalse(handBack.onKey(KeyEvent(KeyEvent.ACTION_UP, code)))
        }
        assertTrue("none forwarded: $keys", keys.isEmpty())
    }

    @Test
    fun onlyThePadsKeysAreForwardedOtherKeysAreTakenAndDropped() {
        handBack.onCardTop(true)
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK)) {
            assertTrue(handBack.onKey(KeyEvent(KeyEvent.ACTION_DOWN, code)))
        }
        assertTrue("a letter does nothing", handBack.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Q)))
        assertEquals(
            listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK),
            keys,
        )
    }

    @Test
    fun keysWithAGameInFrontAreDroppedNotSentToTheTopScreen() {
        top.resumed = false
        assertTrue("taken: no key does anything on the card", handBack.onKey(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
        assertTrue(keys.isEmpty())
    }

    @Test
    fun aRefusedHandBackIsTriedThreeTimesThenOneWarning() {
        top.moveResult = { throw SecurityException("test") }
        handBack.onCardTop(true)
        idle(ControllerHandBack.RETRY_MS * (ControllerHandBack.TRIES + 2))
        assertEquals(ControllerHandBack.TRIES, top.moves)
        val warnings = logs.filter { it.startsWith("W: ") }
        assertEquals(
            listOf("W: Controller on the second screen: gave up after 3 tries (SecurityException); a tap on the top screen brings it back"),
            warnings,
        )
        assertEquals(2, logs.count { it.endsWith("trying again") })
    }

    @Test
    fun aHandBackAfterWhichTheCardKeepsTheControllerIsTriedAgain() {
        handBack.onCardTop(true)
        // Android never says the card lost it.
        idle(ControllerHandBack.RETRY_MS * (ControllerHandBack.TRIES * 2 + 2))
        assertEquals(ControllerHandBack.TRIES, top.moves)
        assertTrue(logs.toString(), logs.last().startsWith("W: Controller on the second screen: gave up after 3 tries (still on the second screen)"))
    }

    @Test
    fun aTaskNotFoundIsTriedAgainAndThenWorks() {
        val answers = mutableListOf(false, true)
        top.moveResult = { answers.removeAt(0) }
        handBack.onCardTop(true)
        idle(ControllerHandBack.RETRY_MS + 10)
        assertEquals(2, top.moves)
        handBack.onCardTop(false)
        idle(ControllerHandBack.RETRY_MS * 4)
        assertEquals(2, top.moves)
        assertTrue(logs.toString(), "Controller on the second screen: not taken back (our task was not found), trying again" in logs)
    }

    @Test
    fun theTopScreenPausingStopsTheRetries() {
        top.moveResult = { false }
        handBack.onCardTop(true)
        top.resumed = false
        idle(ControllerHandBack.RETRY_MS * 4)
        assertEquals("one try, then not while paused", 1, top.moves)
    }
}
