package com.plumemu

import android.app.ActivityOptions
import android.content.Intent
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.drainMainLooper
import com.plumemu.secondscreen.CardInstances
import com.plumemu.secondscreen.SecondScreenCardActivity
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.secondscreen.Presence
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.secondscreen.SecondScreenFeed
import com.plumemu.secondscreen.SecondScreenHost
import com.plumemu.secondscreen.SecondScreenHostTestBase
import com.plumemu.secondscreen.TopScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowLog

/**
 * `MainActivity` as the card's top screen: a D-pad press the card receives before the hand-back
 * lands reaches the main screen exactly once; the card's window stays focusable through a game
 * (a non-focusable top ANRs), and taking the controller then hands nothing back over the game.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityCardTest {
    private val main = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = main.get()
    private val received = mutableListOf<Action>()
    private val displayId = ShadowDisplayManager.addDisplay(SecondScreenHostTestBase.THOR_BOTTOM)
    private val cards = mutableListOf<ActivityController<SecondScreenCardActivity>>()

    init {
        activity.lifecycleScope.launch { activity.input.actions.collect { received += it } }
        idle()
    }

    @After
    fun tearDown() {
        host?.stop()
        SecondScreenHost.cardHolder.value = null
        cards.forEach { card ->
            if (card.get().isDestroyed) return@forEach
            val state = card.get().lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) card.pause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) card.stop()
            card.destroy()
        }
        CardInstances.finishLive()
        drainMainLooper()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private var host: SecondScreenHost? = null

    /** The card as the host starts it on the second display (so it is the registered one, not a blank stray). */
    private fun card(): SecondScreenCardActivity {
        val app = RuntimeEnvironment.getApplication()
        val made =
            SecondScreenHost(
                activity,
                activity.lifecycleScope,
                MutableStateFlow(SecondScreen(displayId, "Screen-2", ScreenKind.BuiltIn)),
                MutableStateFlow(PlumSettings()),
                SecondScreenFeed(activity.lifecycleScope),
                MutableStateFlow(Presence.InPlumemu),
                0,
                startCard = { id ->
                    val options = ActivityOptions.makeBasic().setLaunchDisplayId(id).toBundle()
                    cards += Robolectric.buildActivity(SecondScreenCardActivity::class.java, Intent(app, SecondScreenCardActivity::class.java), options).setup()
                },
            )
        host = made
        made.start()
        idle()
        return checkNotNull(CardInstances.live) { "no card registered on display $displayId" }
    }

    private fun key(
        action: Int,
        code: Int,
    ) = KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)

    @Test
    fun theMainScreenIsTheTopScreenItHandsBackTo() {
        assertTrue(TopScreen.side?.isResumed == true)
        main.pause()
        assertFalse("paused: no hand-back", TopScreen.side?.isResumed == true)
    }

    @Test
    fun aVolumeKeyOnTheCardIsLeftToAndroidAndNotForwarded() {
        val card = card()
        card.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP))
        card.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP))
        idle()
        assertTrue("not forwarded: $received", received.isEmpty())
    }

    @Test
    fun aDpadPressTheCardReceivesReachesTheMainScreenOnce() {
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        idle()
        val direct = received.toList()
        assertEquals("a press on the top screen is one action", 1, direct.size)
        received.clear()
        val card = card()
        assertTrue(card.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)))
        assertTrue(card.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)))
        idle()
        assertEquals("the same, once, never twice or lost", direct, received)
    }

    @Test
    fun aPressWithTheMainScreenStoppedGoesNowhere() {
        val card = card()
        main.pause().stop()
        card.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        card.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        idle()
        assertTrue(received.isEmpty())
        main.start().resume()
    }

    @Test
    fun theCardStaysFocusableThroughAGameAndHandsNothingBackMeanwhile() {
        val card = card()
        idle()
        assertTrue("focusable at its start", card.isFocusableNow)
        main.pause().stop()
        idle()
        assertTrue("still focusable with a game in front (a non-focusable top there ANRs)", card.isFocusableNow)
        ShadowLog.clear()
        // A tap on the card during the game: it takes the controller.
        card.onTopResumedActivityChanged(true)
        idle()
        val lines = handBackLines()
        assertTrue("not handed back over the game: $lines", "Controller on the second screen: not taken back (Plumemu is not in front)" in lines)
        assertFalse("Plumemu's task never moved: $lines", "Controller on the second screen: taken back to the top screen" in lines)
        card.onTopResumedActivityChanged(false)
        main.restart().resume()
        idle()
        assertTrue("focusable with Plumemu back", card.isFocusableNow)
    }

    @Test
    fun aCardMadeWhileTheMainScreenIsStoppedIsFocusable() {
        main.pause().stop()
        idle()
        val card = card()
        idle()
        assertTrue(card.isFocusableNow)
        assertSame(card, CardInstances.live)
        main.restart().resume()
    }

    private fun handBackLines(): List<String> = ShadowLog.getLogsForTag(SecondScreenHost.TAG).map { it.msg }
}
