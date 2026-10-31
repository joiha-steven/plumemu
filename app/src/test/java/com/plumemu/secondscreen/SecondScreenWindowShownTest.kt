package com.plumemu.secondscreen

import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplayManager

/**
 * `SecondScreenFeed.isWindowShown`, the state the top screen's layout follows: true
 * only while the window is on a display, from a `show()` that worked until it goes; false for a
 * refused show, the switch off, no second screen, a removed display and a stopped host.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenWindowShownTest : SecondScreenHostTestBase() {
    @Test
    fun falseBeforeAnyWindow() {
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun trueWhileShown() {
        val host = startHost()
        assertEquals(displayId, host.shownDisplayId)
        assertTrue(feed.isWindowShown.value)
    }

    @Test
    fun falseWithTheSwitchOffAndTrueOnceSwitchedOn() {
        val host = startHost(kind = ScreenKind.Unsure)
        assertNull(host.shownDisplayId)
        assertFalse("an Unsure screen is off until switched on", feed.isWindowShown.value)
        settings.value = on(true)
        idle()
        assertTrue(feed.isWindowShown.value)
        settings.value = on(false)
        idle()
        assertNull(host.shownDisplayId)
        assertFalse("switched off", feed.isWindowShown.value)
    }

    @Test
    fun falseWithNoSecondScreen() {
        startHost()
        screens.value = null
        idle()
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun falseBeforeTheSettingsArrive() {
        settings.value = null
        startHost()
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun falseForARefusedShowAndTrueWhenALaterTryWorks() {
        var refusals = 2
        val host =
            startHost(showWindow = {
                if (refusals-- > 0) throw WindowManager.InvalidDisplayException("test")
                it.show()
            })
        assertNull(host.shownDisplayId)
        assertFalse("requested is not shown", feed.isWindowShown.value)
        idle(SecondScreenHost.SHOW_RETRY_MS * 3)
        assertEquals(displayId, host.shownDisplayId)
        assertTrue(feed.isWindowShown.value)
    }

    @Test
    fun falseWhenEveryTryIsRefused() {
        startHost(showWindow = { throw WindowManager.InvalidDisplayException("test") })
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun falseWhenAndroidDismissesItWithItsDisplay() {
        startHost()
        ShadowDisplayManager.removeDisplay(displayId)
        idle()
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun falseWhenStopped() {
        val host = startHost()
        host.stop()
        idle()
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun falseWhenPlumemuIsLeftAnotherWayAndTrueOnTheWayBack() {
        startHost()
        presence.value = Presence.Away
        idle()
        assertFalse("no window on another launcher's screen", feed.isWindowShown.value)
        presence.value = Presence.InPlumemu
        idle()
        assertTrue(feed.isWindowShown.value)
    }

    /** In game the window stays (clock or black): the top screen keeps its layout for the way back. */
    @Test
    fun staysTrueThroughAGame() {
        val host = startHost()
        presence.value = Presence.Playing
        idle()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        assertFalse("the feed stops following in game", feed.isFollowing.value)
        assertTrue("the window is still up", feed.isWindowShown.value)
        presence.value = Presence.InPlumemu
        idle()
        assertTrue(feed.isWindowShown.value)
        assertTrue(feed.isFollowing.value)
    }

    @Test
    fun trueAgainOnTheNewWindowOfAChangedDisplay() {
        startHost()
        ShadowDisplayManager.changeDisplay(displayId, "w600dp-h468dp-xhdpi")
        idle()
        assertTrue(feed.isWindowShown.value)
    }
}

/**
 * The guess `MainActivity` makes before the first frame (`isWindowLikelyShown`, seeded into the
 * feed) and how the host corrects it: kept while the answer is not known, false once no window
 * will come or the show is refused.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenWindowGuessTest : SecondScreenHostTestBase() {
    @Test
    fun theGuessFollowsTheCachedSwitchAndTheDisplays() {
        val builtIn = DisplayFacts(4, "Screen-2", isPresentation = true, type = TYPE_INTERNAL)
        val overlay = DisplayFacts(2, "Overlay #1", isPresentation = true, type = TYPE_OVERLAY)
        assertTrue("built in, not decided", isWindowLikelyShown(null, listOf(builtIn)))
        assertTrue("built in, on", isWindowLikelyShown(true, listOf(builtIn)))
        assertFalse("switched off", isWindowLikelyShown(false, listOf(builtIn)))
        assertFalse("no display", isWindowLikelyShown(true, emptyList()))
        assertFalse("an Unsure screen is off until decided", isWindowLikelyShown(null, listOf(overlay)))
        assertTrue("an Unsure screen switched on", isWindowLikelyShown(true, listOf(overlay)))
        assertTrue("benchmark builds", isWindowLikelyShown(null, listOf(overlay), forcedOn = true))
        assertFalse("an HDMI screen is never used", isWindowLikelyShown(true, listOf(builtIn.copy(type = TYPE_EXTERNAL))))
    }

    @Test
    fun theCacheKeepsOnOffAndNotDecided() {
        val cache = SecondScreenSwitchCache(activity)
        assertNull("nothing cached on a first launch", cache.read())
        cache.write(true)
        assertEquals(true, SecondScreenSwitchCache(activity).read())
        cache.write(false)
        assertEquals(false, SecondScreenSwitchCache(activity).read())
        cache.write(null)
        assertNull("not decided again (Reset all settings)", SecondScreenSwitchCache(activity).read())
    }

    @Test
    fun aRightGuessStaysTrueWhileTheSettingsAreReadAndTheWindowComes() {
        feed.setWindowShown(true)
        settings.value = null
        startHost()
        assertTrue("not known yet: the guess holds", feed.isWindowShown.value)
        settings.value = on(true)
        idle()
        assertTrue(feed.isWindowShown.value)
    }

    @Test
    fun aGuessThatTheStoredSwitchContradictsEndsFalse() {
        feed.setWindowShown(true)
        settings.value = on(false)
        startHost()
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun aGuessWithoutAScreenEndsFalse() {
        feed.setWindowShown(true)
        startHost()
        screens.value = null
        idle()
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun aRefusedShowAfterARightGuessEndsFalse() {
        feed.setWindowShown(true)
        startHost(showWindow = { throw WindowManager.InvalidDisplayException("test") })
        assertFalse("refused: the column comes back", feed.isWindowShown.value)
        idle(SecondScreenHost.SHOW_RETRY_MS * SecondScreenHost.SHOW_ATTEMPTS * 2)
        assertFalse(feed.isWindowShown.value)
    }

    @Test
    fun aNewActivitysGuessHoldsWhileTheOldWindowIsStillUp() {
        val old = startHost()
        val newFeed = SecondScreenFeed(activity.lifecycleScope).also { it.setWindowShown(true) }
        val newer = SecondScreenHost(activity, activity.lifecycleScope, screens, settings, newFeed, presence, 0, info = { logs += "new: $it" })
        others += newer
        newer.start()
        idle()
        assertTrue("the old window is up: no column", newFeed.isWindowShown.value)
        old.stop()
        idle()
        assertTrue("its own window now", newFeed.isWindowShown.value)
        assertEquals(displayId, newer.shownDisplayId)
        newer.stop()
    }
}
