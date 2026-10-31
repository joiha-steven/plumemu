package com.plumemu.secondscreen

import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.BatteryManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.Lifecycle
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.formatClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/**
 * The window while a game runs : the clock card or black for a game
 * Plumemu started, no window when Plumemu was left another way, picking again on the way back,
 * and the clock still drawing new frames while the Activity is stopped.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenInGameTest : SecondScreenHostTestBase() {
    private fun inGame(setting: InGameSetting) = PlumSettings(secondScreen = SecondScreenSettings(enabled = null, inGame = setting))

    private fun receiversFor(action: String) = shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.count { it.intentFilter.hasAction(action) }

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    private fun batteryAt(percent: Int) {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, percent).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
    }

    private fun connectivity() = RuntimeEnvironment.getApplication().getSystemService(ConnectivityManager::class.java)

    private fun wifiCallbacks() = shadowOf(connectivity()).networkCallbacks.size

    /** One ordinary Wi-Fi network up: Robolectric's own default lacks the capabilities the watch asks for. */
    private fun wifiUp() {
        val shadow = shadowOf(connectivity())
        val network = ShadowNetwork.newInstance(1)
        shadow.clearAllNetworks()
        shadow.addNetwork(network, ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, NetworkInfo.State.CONNECTED))
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadow.setNetworkCapabilities(network, caps)
    }

    private fun glyphsIn(window: android.app.Dialog) = nodesOf(window).count { it.config.getOrNull(SemanticsProperties.TestTag) == InGameTags.WIFI }

    /** A game started from Plumemu: the Activity stops with the flag set. */
    private fun startAGame() {
        presence.value = Presence.Playing
        controller.pause().stop()
        idle()
    }

    @Test
    fun aGameStartedFromPlumemuShowsTheClockCardOnBlack() {
        batteryAt(82)
        val host = startHost()
        val window = host.shownWindow!!
        startAGame()
        assertEquals("the same window stays", window, host.shownWindow)
        assertEquals(WindowMode.InGameClock, host.shownMode)
        val texts = textsOf(window)
        assertTrue("the time: $texts", formatClock(activity, now) in texts)
        assertTrue("the battery: $texts", "82%" in texts)
        assertFalse("the feed stops following in game", feed.isFollowing.value)
        assertEquals("Second screen window: in game, clock and battery on display $displayId", logs.last())
        assertEquals("one minute tick listener", 1, receiversFor(Intent.ACTION_TIME_TICK))
        assertEquals("one battery listener", 1, receiversFor(Intent.ACTION_BATTERY_CHANGED))
        assertTrue("dim: nothing brighter than #646464, ${brightest(window)}", brightest(window) <= 0x64 / 255f + 0.001f)
        assertTrue("but something is drawn", brightest(window) > 0f)
    }

    @Test
    fun theClockCardShowsTheWifiGlyphWhileConnectedAndListensOnlyWhileShown() {
        batteryAt(82)
        wifiUp()
        val host = startHost()
        assertEquals("picking listens to no network", 0, wifiCallbacks())
        startAGame()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        val window = host.shownWindow!!
        assertEquals("one Wi-Fi listener while the card shows", 1, wifiCallbacks())
        assertEquals("connected: the glyph on the battery line", 1, glyphsIn(window))
        assertTrue("dim: nothing brighter than #646464, ${brightest(window)}", brightest(window) <= 0x64 / 255f + 0.001f)
        controller.restart().resume()
        presence.value = Presence.InPlumemu
        idle()
        assertEquals("back to picking: no Wi-Fi listener left", 0, wifiCallbacks())
    }

    private fun showBattery(on: Boolean) {
        settings.value = settings.value!!.let { it.copy(homeScreen = it.homeScreen.copy(showBattery = on)) }
    }

    /** Show battery off: the time alone, no battery line, no Wi-Fi glyph, no battery or Wi-Fi listener. */
    @Test
    fun withShowBatteryOffTheClockCardIsTheTimeAloneAndListensToNeitherBatteryNorWifi() {
        batteryAt(82)
        wifiUp()
        showBattery(false)
        val host = startHost()
        startAGame()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        val window = host.shownWindow!!
        pixelsOf(window)
        val texts = textsOf(window)
        assertTrue("the time stays: $texts", formatClock(activity, now) in texts)
        assertTrue("no battery line: $texts", texts.none { "%" in it })
        assertEquals("no glyph", 0, glyphsIn(window))
        assertEquals("no Wi-Fi listener", 0, wifiCallbacks())
        assertEquals("no battery listener", 0, receiversFor(Intent.ACTION_BATTERY_CHANGED))
        assertEquals("the minute tick still runs", 1, receiversFor(Intent.ACTION_TIME_TICK))
    }

    /** The setting changes while the card shows: it follows at once, without reopening the window. */
    @Test
    fun showBatteryChangedWhileTheCardShowsUpdatesTheSameWindow() {
        batteryAt(82)
        wifiUp()
        val host = startHost()
        startAGame()
        val window = host.shownWindow!!
        pixelsOf(window)
        assertTrue("battery shown: ${textsOf(window)}", "82%" in textsOf(window))
        assertEquals(1, glyphsIn(window))
        assertEquals(1, wifiCallbacks())
        showBattery(false)
        idle()
        pixelsOf(window)
        assertTrue("battery gone: ${textsOf(window)}", textsOf(window).none { "%" in it })
        assertEquals(0, glyphsIn(window))
        assertEquals("Wi-Fi listener dropped", 0, wifiCallbacks())
        assertEquals("battery listener dropped", 0, receiversFor(Intent.ACTION_BATTERY_CHANGED))
        assertSame("the same window", window, host.shownWindow)
        showBattery(true)
        idle()
        pixelsOf(window)
        assertTrue("battery back: ${textsOf(window)}", "82%" in textsOf(window))
        assertEquals(1, glyphsIn(window))
        assertEquals(1, wifiCallbacks())
        assertEquals(1, receiversFor(Intent.ACTION_BATTERY_CHANGED))
    }

    @Test
    fun theClockCardHasNoWifiGlyphWhileNotConnected() {
        batteryAt(82)
        shadowOf(connectivity()).clearAllNetworks()
        val host = startHost()
        startAGame()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        assertEquals("one Wi-Fi listener while the card shows", 1, wifiCallbacks())
        assertEquals("not connected: no glyph", 0, glyphsIn(host.shownWindow!!))
    }

    @Test
    fun blackModeDrawsNothingAndListensToNothing() {
        settings.value = inGame(InGameSetting.Black)
        val host = startHost()
        val window = host.shownWindow!!
        startAGame()
        assertEquals(WindowMode.InGameBlack, host.shownMode)
        assertEquals("the window stays up", displayId, host.shownDisplayId)
        assertEquals("all black", 0f, brightest(window))
        assertEquals("no listener at all", 0, receiversFor(Intent.ACTION_TIME_TICK) + receiversFor(Intent.ACTION_BATTERY_CHANGED))
        assertEquals("no Wi-Fi listener in Black", 0, wifiCallbacks())
        assertEquals("Second screen window: in game, black on display $displayId", logs.last())
    }

    @Test
    fun leavingPlumemuAnotherWayDismissesTheWindowAndComingBackShowsPicking() {
        val host = startHost()
        val window = host.shownWindow!!
        presence.value = Presence.Away
        controller.pause().stop()
        idle()
        assertNull("not on someone else's screen", host.shownDisplayId)
        assertFalse(window.isShowing)
        assertEquals("Second screen window: dismissed from display $displayId (Plumemu left)", logs.last())
        controller.restart().resume()
        presence.value = Presence.InPlumemu
        idle()
        assertEquals(displayId, host.shownDisplayId)
        assertEquals(WindowMode.Picking, host.shownMode)
        assertTrue("the feed follows again", feed.isFollowing.value)
        assertEquals("Second screen window: shown on display $displayId (switched on)", logs.last())
    }

    @Test
    fun backFromAGameShowsPickingAgainAndTheFeedFollowsAfterTheSettle() {
        val host = startHost()
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        startAGame()
        assertEquals(SecondScreenContent.Calm, feed.content.value)
        controller.restart().resume()
        presence.value = Presence.InPlumemu
        idle()
        assertEquals(WindowMode.Picking, host.shownMode)
        assertTrue(feed.isFollowing.value)
        assertEquals("Second screen window: picking on display $displayId", logs.last())
        assertEquals("no listener left from the clock card", 0, receiversFor(Intent.ACTION_TIME_TICK))
        // The home screen offers its focus again once following; the card comes after the settle.
        feed.offer(SecondScreenContent.Game("Super Metroid", "Super Nintendo"))
        idle(SETTLED_MS)
        // Robolectric draws no frame by itself (no surface), and Compose measures the title's
        // BoxWithConstraints in the draw pass: the frame a device would draw, drawn here.
        pixelsOf(host.shownWindow!!)
        assertTrue(textsOf(host.shownWindow!!).toString(), "Super Metroid" in textsOf(host.shownWindow!!).joinToString())
    }

    @Test
    fun theStatisticsCardGivesWayToTheClockCardInGame() {
        val host = startHost()
        feed.offer(SecondScreenContent.Statistics(SecondScreenStatisticsSamples.withCovers))
        idle(SETTLED_MS)
        pixelsOf(host.shownWindow!!)
        assertTrue("the statistics card in the window: ${textsOf(host.shownWindow!!)}", "23 h 10 min" in textsOf(host.shownWindow!!))
        startAGame()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        val texts = textsOf(host.shownWindow!!)
        assertTrue("the clock card: $texts", formatClock(activity, now) in texts)
        assertTrue("no statistics in game: $texts", texts.none { "23 h 10 min" in it || "Statistics" in it })
    }

    @Test
    fun aGameWithTheSwitchOffShowsNothing() {
        settings.value = on(false)
        val host = startHost()
        startAGame()
        assertNull(host.shownDisplayId)
        assertEquals(0, receiversFor(Intent.ACTION_TIME_TICK))
        assertEquals(0, wifiCallbacks())
    }

    @Test
    fun theWifiWatchEndsWhenTheClockCardsWindowCloses() {
        batteryAt(82)
        val host = startHost()
        startAGame()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        assertEquals("watching while the card shows", 1, wifiCallbacks())
        // Plumemu left another way while on the clock card: the window goes.
        presence.value = Presence.Away
        idle()
        assertNull(host.shownDisplayId)
        assertEquals("no Wi-Fi watch after Away", 0, wifiCallbacks())
        presence.value = Presence.Playing
        idle()
        assertEquals("watching again when the card shows again", 1, wifiCallbacks())
        // The switch turned off while the clock card shows.
        settings.value = on(false)
        idle()
        assertNull(host.shownDisplayId)
        assertEquals("no Wi-Fi watch after the switch went off", 0, wifiCallbacks())
    }

    /**
     * The review's frame test: with the Activity stopped (a game runs), a minute tick still gets
     * the clock card a new frame, drawn with the new time and position. If the window followed the
     * Activity's lifecycle, Compose would pause its frame clock and the clock would freeze.
     */
    @Test
    fun theClockCardDrawsANewFrameOnATickWhileTheActivityIsStopped() {
        batteryAt(82)
        val host = startHost()
        val window = host.shownWindow!!
        startAGame()
        assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
        val before = pixelsOf(window)
        val timeBefore = formatClock(activity, now)
        assertTrue(timeBefore in textsOf(window))
        now += 60_000L
        RuntimeEnvironment.getApplication().sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        idle()
        val texts = textsOf(window)
        assertTrue("the new minute: $texts", formatClock(activity, now) in texts)
        assertFalse(texts.toString(), timeBefore in texts)
        assertFalse("a new frame was drawn", before.contentEquals(pixelsOf(window)))
        val block = nodesOf(window).first { it.config.getOrNull(SemanticsProperties.TestTag) == InGameTags.BLOCK }
        assertNotNull(block)
    }

}
