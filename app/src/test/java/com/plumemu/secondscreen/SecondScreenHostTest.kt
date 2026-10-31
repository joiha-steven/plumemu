package com.plumemu.secondscreen

import android.app.Dialog
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplayManager
import java.lang.reflect.Proxy
import kotlin.math.abs

/**
 * The second screen's window on a Robolectric display: when it shows and goes, its flags, its own
 * lifecycle (the card keeps drawing while the Activity is stopped), a `show()` that throws, and the
 * size class at the Thor's 538x468 dp.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenHostTest : SecondScreenHostTestBase() {
    @Test
    fun showsOnTheScreenWhileItIsOn() {
        val host = startHost()
        assertEquals(displayId, host.shownDisplayId)
        assertTrue("showing", host.shownWindow?.isShowing == true)
        assertEquals(displayId, (host.shownWindow as android.app.Presentation).display.displayId)
        assertTrue("the feed follows", feed.isFollowing.value)
        assertEquals(listOf("Second screen window: shown on display $displayId (switched on)"), logs)
    }

    @Test
    fun noWindowForAnUnsureScreenUntilSwitchedOn() {
        val host = startHost(kind = ScreenKind.Unsure)
        assertNull(host.shownDisplayId)
        assertFalse(feed.isFollowing.value)
        settings.value = on(true)
        idle()
        assertEquals(displayId, host.shownDisplayId)
    }

    @Test
    fun benchmarkBuildsShowItOnAnUnsureScreen() {
        val host = startHost(kind = ScreenKind.Unsure, forcedOn = true)
        assertEquals(displayId, host.shownDisplayId)
        assertTrue(logs.single(), logs.single().endsWith("(benchmark)"))
    }

    @Test
    fun noWindowBeforeTheSettingsArrive() {
        settings.value = null
        val host = startHost()
        assertNull(host.shownDisplayId)
        settings.value = PlumSettings()
        idle()
        assertEquals(displayId, host.shownDisplayId)
    }

    @Test
    fun switchedOffDismissesIt() {
        val host = startHost()
        val window = host.shownWindow!!
        settings.value = on(false)
        idle()
        assertNull(host.shownDisplayId)
        assertFalse("dismissed", window.isShowing)
        assertFalse(feed.isFollowing.value)
        assertEquals("Second screen window: dismissed from display $displayId (switched off)", logs.last())
        assertEquals("its lifecycle ended", Lifecycle.State.DESTROYED, lifecycleOf(window))
    }

    @Test
    fun noSecondScreenDismissesIt() {
        val host = startHost()
        screens.value = null
        idle()
        assertNull(host.shownDisplayId)
        assertEquals("Second screen window: dismissed from display $displayId (no second screen)", logs.last())
    }

    @Test
    fun aRemovedDisplayTakesTheWindowAndNoneComesBack() {
        val host = startHost()
        val window = host.shownWindow!!
        ShadowDisplayManager.removeDisplay(displayId)
        idle()
        assertFalse("dismissed by Android", window.isShowing)
        assertNull(host.shownDisplayId)
        assertTrue(logs.toString(), "Second screen window: dismissed by Android on display $displayId (display removed)" in logs)
        assertEquals(
            "the new attempt finds the display gone",
            "Second screen window: none, display $displayId is gone (recreated after: display removed)",
            logs.last(),
        )
        // The detection then reports no screen: nothing more happens.
        screens.value = null
        idle()
        assertNull(host.shownDisplayId)
    }

    @Test
    fun aChangedDisplayGetsANewWindow() {
        val host = startHost()
        val first = host.shownWindow!!
        ShadowDisplayManager.changeDisplay(displayId, "w600dp-h468dp-xhdpi")
        idle()
        val second = host.shownWindow
        assertNotNull("a window again: $logs", second)
        assertNotSame("a new one, with the new size", first, second)
        assertFalse(first.isShowing)
        assertTrue(second!!.isShowing)
        assertEquals("Second screen window: shown on display $displayId (recreated after: display changed)", logs.last())
    }

    @Test
    fun stopDismissesIt() {
        val host = startHost()
        val window = host.shownWindow!!
        host.stop()
        idle()
        assertFalse(window.isShowing)
        assertEquals("Second screen window: dismissed from display $displayId (activity destroyed)", logs.last())
        // Stopped: changes no longer bring it back.
        settings.value = on(true)
        idle()
        assertNull(host.shownDisplayId)
    }

    @Test
    fun aNewActivitysHostWaitsForTheOldWindowToGo() {
        val old = startHost()
        val oldLogs = logs.toList()
        val newer =
            SecondScreenHost(activity, activity.lifecycleScope, screens, settings, SecondScreenFeed(activity.lifecycleScope), presence, 0, info = { logs += "new: $it" })
        others += newer
        newer.start()
        idle()
        assertEquals("the old one keeps it", displayId, old.shownDisplayId)
        assertNull("the new one waits", newer.shownDisplayId)
        assertEquals(oldLogs, logs)
        old.stop()
        idle()
        assertEquals("the new one shows once the old window has gone", displayId, newer.shownDisplayId)
        assertEquals("new: Second screen window: shown on display $displayId (switched on)", logs.last())
        newer.stop()
    }

    @Test
    fun theWindowIsNotFocusableAndStillTouchable() {
        val flags = startHost().shownWindow!!.window!!.attributes.flags
        assertTrue("not focusable", flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue("not touch modal", flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertEquals("touchable: a tap must not fall through", 0, flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    @Test
    fun aTapIsTakenAndDoesNothing() {
        val host = startHost()
        val view = composeViewOf(host.shownWindow!!)
        val down = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 200f, 200f, 0)
        assertTrue("taken", view.dispatchTouchEvent(down))
        assertEquals(displayId, host.shownDisplayId)
    }

    @Test
    fun aShowThatAlwaysThrowsIsTriedAFewTimesThenLeavesNoWindow() {
        val host = startHost(showWindow = { throw WindowManager.InvalidDisplayException("test") })
        assertNull(host.shownDisplayId)
        assertFalse(feed.isFollowing.value)
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        val tries = "W: Second screen window: could not show on display $displayId (InvalidDisplayException)"
        assertEquals(List(SecondScreenHost.SHOW_ATTEMPTS - 1) { "$tries, trying again" } + tries, logs)
        assertNull(host.shownDisplayId)
    }

    @Test
    fun aBadTokenIsLoggedByItsClass() {
        val host = startHost(showWindow = { throw WindowManager.BadTokenException("test") })
        assertNull(host.shownDisplayId)
        assertEquals("W: Second screen window: could not show on display $displayId (BadTokenException), trying again", logs.first())
    }

    @Test
    fun aRefusedShowRightAfterAnotherWindowWentSucceedsOnALaterTry() {
        var refusals = 2
        val host =
            startHost(showWindow = {
                if (refusals-- > 0) throw WindowManager.InvalidDisplayException("test")
                it.show()
            })
        assertNull(host.shownDisplayId)
        idle(SecondScreenHost.SHOW_RETRY_MS * 3)
        assertEquals("shown on the third try: $logs", displayId, host.shownDisplayId)
        assertEquals("Second screen window: shown on display $displayId (switched on)", logs.last())
    }

    @Test
    fun noLateTryOnceTheSwitchIsOff() {
        val host = startHost(showWindow = { throw WindowManager.InvalidDisplayException("test") })
        settings.value = on(false)
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        assertEquals("one try only: $logs", 1, logs.size)
        assertNull(host.shownDisplayId)
    }

    /**
     * The leak a refused window left behind: `Dialog.show()` runs `onStart` (the Presentation
     * registers its display listener, which holds the window and the Activity) before
     * `WindowManager.addView`. Here the add itself is refused, after `onStart`, as Android does.
     */
    @Test
    fun aWindowRefusedAfterItStartedLeavesNoDisplayListenerAndEndsItsLifecycle() {
        val before = displayListenerCount()
        val atRefusal = mutableListOf<Int>()
        val refused = mutableListOf<Dialog>()
        val host =
            startHost(showWindow = { dialog ->
                refused += dialog
                refuseTheAdd(dialog) { atRefusal += displayListenerCount() }
                dialog.show()
            })
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        assertNull(host.shownDisplayId)
        assertEquals("every try was refused: $logs", SecondScreenHost.SHOW_ATTEMPTS, refused.size)
        assertEquals("each refusal came after onStart had registered the listener", List(SecondScreenHost.SHOW_ATTEMPTS) { before + 1 }, atRefusal)
        assertEquals("no listener left behind", before, displayListenerCount())
        refused.forEach { assertEquals(Lifecycle.State.DESTROYED, lifecycleOf(it)) }
        assertFalse(feed.isFollowing.value)
    }

    @Test
    fun aWindowShownAgainStartsCalmNotWithTheCardOfBefore() {
        val host = startHost()
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        assertTrue("Super Metroid" in textsOf(host.shownWindow!!).joinToString())
        settings.value = on(false)
        idle()
        assertNull(host.shownDisplayId)
        settings.value = on(true)
        idle()
        val texts = textsOf(host.shownWindow!!).joinToString()
        assertFalse("no old card: $texts", "Super Metroid" in texts)
        assertEquals(SecondScreenContent.Calm, feed.content.value)
    }

    /** Display listeners registered with the process's DisplayManagerGlobal (a hidden field, read for the leak test). */
    private fun displayListenerCount(): Int {
        val type = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val global = type.getMethod("getInstance").invoke(null)
        val field = type.getDeclaredField("mDisplayListeners").apply { isAccessible = true }
        return (field.get(global) as Collection<*>).size
    }

    /** [dialog]'s `WindowManager.addView` throws `InvalidDisplayException` after calling [atAdd]; the rest goes to the real one. */
    private fun refuseTheAdd(
        dialog: Dialog,
        atAdd: () -> Unit,
    ) {
        val field = Dialog::class.java.getDeclaredField("mWindowManager").apply { isAccessible = true }
        val real = field.get(dialog) as WindowManager
        val refusing =
            Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
                if (method.name == "addView") {
                    atAdd()
                    throw WindowManager.InvalidDisplayException("test: refused after onStart")
                }
                method.invoke(real, *(args ?: emptyArray()))
            }
        field.set(dialog, refusing)
    }

    @Test
    fun theWindowStaysResumedAndItsCardRecomposesWhileTheActivityIsStopped() {
        val host = startHost()
        val window = host.shownWindow!!
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        assertTrue(textsOf(window).toString(), "Super Metroid" in textsOf(window).joinToString())
        controller.pause().stop()
        idle()
        assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
        assertEquals("the window's own owner", Lifecycle.State.RESUMED, lifecycleOf(window))
        assertEquals("the window does not follow the Activity's stop by itself", displayId, host.shownDisplayId)
        feed.offer(SecondScreenContent.System("Mega Drive", 12))
        idle(SETTLED_MS)
        val texts = textsOf(window).joinToString()
        assertTrue("recomposed while stopped: $texts", "Mega Drive" in texts)
        assertFalse(texts, "Super Metroid" in texts)
        // Frames for the in-game clock while the Activity is stopped: theClockCardDrawsANewFrameOnATickWhileTheActivityIsStopped.
        assertTrue("the new card is drawn: ${brightest(window)}", brightest(window) > 0.5f)
    }


    /**
     * Robolectric's display context keeps the application's density (seen: 1.0 on a 369 dpi
     * display), so the application is given the Thor's density here; on a device the
     * Presentation's context has its display's own. The emulator check covers that part.
     */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-369dpi")
    fun theThorsBottomScreenIsRoomy() {
        val window = startHost().shownWindow!!
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        val view = composeViewOf(window)
        val density = view.resources.displayMetrics.density
        assertEquals("the second display's density (369 dpi)", 369f / 160f, density, 0.01f)
        assertEquals("538 dp wide", 538f, view.width / density, 1f)
        assertEquals("468 dp high", 468f, view.height / density, 1f)
        val block = nodesOf(window).first { it.config.getOrNull(SemanticsProperties.TestTag) == SecondScreenTags.BLOCK }
        val leftDp = block.boundsInRoot.left / density
        assertTrue("Roomy's 40 dp margin, not Compact's 28: $leftDp", abs(leftDp - 40f) < 1f)
    }

}
