package com.plumemu.secondscreen

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.launcher.play.battery.BatteryReads
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Counts reads and stops; asks for the next one in 5 s like the meter after its start. */
private class CountingReads : BatteryReads {
    var reads = 0
    var stops = 0

    override fun read(): Long {
        reads++
        return 5_000
    }

    override fun stopReading() {
        stops++
    }
}

/**
 * The battery is read only while the in-game clock card is up with Show battery on and the screen
 * on: never while picking, in Black, with Show battery
 * off, with no window, with the device's screen or the second display off, and the reads stop when
 * the card goes.
 */
@RunWith(org.robolectric.RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryReadsTest : SecondScreenHostTestBase() {
    private val counting = CountingReads()

    // Passed typed as the interface: a test class has no Compose stability field ($stable).
    private val reads: BatteryReads get() = counting

    private fun inGame(setting: InGameSetting) = PlumSettings(secondScreen = SecondScreenSettings(enabled = null, inGame = setting))

    private fun showBattery(on: Boolean) {
        settings.value = settings.value!!.let { it.copy(homeScreen = it.homeScreen.copy(showBattery = on)) }
    }

    private fun startAGame() {
        presence.value = Presence.Playing
        controller.pause().stop()
        idle()
    }

    private fun backToPlumemu() {
        controller.restart().resume()
        presence.value = Presence.InPlumemu
        idle()
    }

    @Test
    fun theClockCardReadsEveryFewSecondsWhileUpAndStopsWhenItGoes() {
        val host = startHost(battery = reads)
        idle(20_000)
        assertEquals("picking reads nothing", 0, counting.reads)
        startAGame()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        idle(16_000)
        assertTrue("about one read every 5 s: ${counting.reads}", counting.reads in 3..5)
        backToPlumemu()
        val atReturn = counting.reads
        idle(30_000)
        assertEquals("back to picking: no read", atReturn, counting.reads)
    }

    @Test
    fun blackReadsNothing() {
        settings.value = inGame(InGameSetting.Black)
        val host = startHost(battery = reads)
        startAGame()
        assertEquals(WindowMode.InGameBlack, host.shownMode)
        idle(30_000)
        assertEquals(0, counting.reads)
    }

    @Test
    fun showBatteryOffReadsNothingAndTurningItOnStartsTheReads() {
        showBattery(false)
        val host = startHost(battery = reads)
        startAGame()
        assertEquals(WindowMode.InGameClock, host.shownMode)
        idle(30_000)
        assertEquals("the card is the time alone: nothing read", 0, counting.reads)
        showBattery(true)
        idle(11_000)
        assertTrue("on again: reads ${counting.reads}", counting.reads >= 2)
        showBattery(false)
        idle()
        val atOff = counting.reads
        idle(30_000)
        assertEquals("off again: the reads stop", atOff, counting.reads)
    }

    @Test
    fun noWindowReadsNothing() {
        startHost(battery = reads)
        presence.value = Presence.Away
        controller.pause().stop()
        idle(30_000)
        assertEquals("Plumemu left another way: no window, no read", 0, counting.reads)
    }

    private fun screen(on: Boolean) {
        shadowOf(activity.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(on)
        activity.applicationContext.sendBroadcast(Intent(if (on) Intent.ACTION_SCREEN_ON else Intent.ACTION_SCREEN_OFF))
        idle()
    }

    private fun secondDisplay(state: Int) {
        val display = (activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(displayId)
        shadowOf(display).setState(state)
        idle()
    }

    @Test
    fun theScreenGoingOffStopsTheReadsUntilItComesBack() {
        startHost(battery = reads)
        startAGame()
        idle(11_000)
        assertTrue("reading: ${counting.reads}", counting.reads >= 2)
        // The window stays up with the screen off (the CPU may be awake for the emulator's audio).
        screen(on = false)
        val atOff = counting.reads
        idle(60_000)
        assertEquals("screen off: no read", atOff, counting.reads)
        assertEquals("the meter is told, so the time off is not measured", 1, counting.stops)
        screen(on = true)
        idle(11_000)
        assertTrue("screen on again: reads ${counting.reads - atOff}", counting.reads - atOff >= 2)
    }

    @Test
    fun theSecondDisplayGoingOffStopsTheReadsUntilItComesBack() {
        startHost(battery = reads)
        startAGame()
        idle(11_000)
        secondDisplay(Display.STATE_OFF)
        val atOff = counting.reads
        idle(60_000)
        assertEquals("second display off: no read", atOff, counting.reads)
        assertEquals(1, counting.stops)
        secondDisplay(Display.STATE_ON)
        idle(11_000)
        assertTrue("on again: reads ${counting.reads - atOff}", counting.reads - atOff >= 2)
    }

    @Test
    fun aGameStartedWithTheScreenOffReadsNothingUntilItIsOn() {
        startHost(battery = reads)
        screen(on = false)
        startAGame()
        idle(60_000)
        assertEquals(0, counting.reads)
        screen(on = true)
        idle(11_000)
        assertTrue("reads ${counting.reads}", counting.reads >= 2)
    }

    private fun screenReceivers() =
        shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.count {
            it.intentFilter.hasAction(Intent.ACTION_SCREEN_OFF) || it.intentFilter.hasAction(Intent.ACTION_SCREEN_ON)
        }

    /** Display listeners registered with the process's DisplayManagerGlobal (a hidden field, as in SecondScreenHostTest). */
    private fun displayListenerCount(): Int {
        val type = Class.forName("android.hardware.display.DisplayManagerGlobal")
        val global = type.getMethod("getInstance").invoke(null)
        val field = type.getDeclaredField("mDisplayListeners").apply { isAccessible = true }
        return (field.get(global) as Collection<*>).size
    }

    @Test
    fun backToPickingLeavesNoScreenReceiverOrDisplayListenerAndAScreenOnReadsNothing() {
        startHost(battery = reads)
        val picking = displayListenerCount()
        assertEquals("picking listens to no screen broadcast", 0, screenReceivers())
        startAGame()
        assertEquals("the clock card follows the screen", 1, screenReceivers())
        assertEquals("and its display", picking + 1, displayListenerCount())
        backToPlumemu()
        assertEquals("back to picking: no screen receiver left", 0, screenReceivers())
        assertEquals("no display listener left", picking, displayListenerCount())
        val atReturn = counting.reads
        screen(on = false)
        screen(on = true)
        idle(30_000)
        assertEquals("a screen on while picking reads nothing", atReturn, counting.reads)
    }

    @Test
    fun stoppingTheHostWithTheClockCardUpLeavesNoScreenReceiverOrDisplayListener() {
        val before = displayListenerCount()
        val host = startHost(battery = reads)
        startAGame()
        assertEquals(1, screenReceivers())
        host.stop()
        idle()
        assertEquals("no screen receiver left", 0, screenReceivers())
        assertEquals("no display listener left", before, displayListenerCount())
        val atStop = counting.reads
        screen(on = true)
        idle(30_000)
        assertEquals("nothing reads after the host stopped", atStop, counting.reads)
    }

    @Test
    fun theSwitchOffReadsNothingInGame() {
        settings.value = on(false)
        startHost(battery = reads)
        startAGame()
        idle(30_000)
        assertEquals(0, counting.reads)
    }
}
