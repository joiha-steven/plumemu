package com.plumemu.home

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Clock and battery: system broadcasts only while started, formats from the system. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SystemStatusTest {
    private val app = RuntimeEnvironment.getApplication()

    private fun receiversFor(action: String) = shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(action) }

    /**
     * The main screen in a started activity. Not MainActivity: it shows the screen only once its
     * settings and library arrive from background threads, and in any test after the first one of
     * a JVM that content never reached the composition (most likely Compose's main-thread
     * dispatcher stays bound to the first Robolectric test's looper).
     */
    private fun startedWithHomeScreen(): ActivityController<ComponentActivity> {
        val host = ComponentName(app, ComponentActivity::class.java)
        shadowOf(app.packageManager).addActivityIfNotPresent(host)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        activity.setContent { PlumTheme { HomeScreen(FakeHomeContent.board(), InputRouter(activity.lifecycleScope), HomeCallbacks()) } }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        return controller
    }

    @Test
    fun receiversLiveOnlyWhileTheScreenIsStarted() {
        val controller = startedWithHomeScreen()
        assertEquals(Lifecycle.State.RESUMED, controller.get().lifecycle.currentState)
        assertEquals("minute tick", 1, receiversFor(Intent.ACTION_TIME_TICK))
        assertEquals("12/24 h and time changes", 1, receiversFor(Intent.ACTION_TIME_CHANGED))
        assertEquals("battery", 1, receiversFor(Intent.ACTION_BATTERY_CHANGED))
        controller.pause().stop()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("nothing listens while stopped", 0, receiversFor(Intent.ACTION_TIME_TICK) + receiversFor(Intent.ACTION_BATTERY_CHANGED))
        controller.start()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("back when started again", 1, receiversFor(Intent.ACTION_TIME_TICK))
        controller.pause().stop().destroy()
    }

    /**
     * On Android 6–12 a "not exported" receiver is guarded by the app's own signature permission,
     * and the system's battery broadcast (sent with no caller) is refused: seen on the API 23
     * emulator as "Permission Denial: broadcasting Intent BATTERY_CHANGED from null". Both
     * broadcasts are protected (only the system may send them), so the receivers are exported
     * and unguarded.
     */
    @Test
    fun systemBroadcastReceiversAreNotGuardedByAPermission() {
        val controller = startedWithHomeScreen()
        for (action in listOf(Intent.ACTION_TIME_TICK, Intent.ACTION_BATTERY_CHANGED)) {
            val receiver = shadowOf(app).registeredReceivers.single { it.intentFilter.hasAction(action) }
            assertEquals("$action permission", null, receiver.broadcastPermission)
            assertTrue("$action exported (flags ${receiver.flags})", receiver.flags and Context.RECEIVER_EXPORTED != 0)
        }
        controller.pause().stop().destroy()
    }

    @Test
    fun clockFollowsThe12Or24HourSetting() {
        val time =
            Calendar.getInstance(TimeZone.getDefault()).apply {
                set(2026, Calendar.OCTOBER, 21, 21, 40, 0)
            }.timeInMillis
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
        assertEquals("21:40", formatClock(app, time))
        Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "12")
        val twelve = formatClock(app, time)
        assertTrue("12 h: $twelve", twelve.startsWith("9:40"))
    }

    @Test
    fun batteryIsAPercentForTheLocale() {
        val intent =
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 41)
                .putExtra(BatteryManager.EXTRA_SCALE, 50)
        assertEquals(82, batteryPercent(intent))
        assertEquals("82%", formatBattery(82, Locale.US))
        val french = formatBattery(82, Locale.FRANCE)
        assertEquals("French keeps the number and the sign", "82%", french.filter { it.isDigit() || it == '%' })
        assertEquals("with a (no-break) space between them", 4, french.length)
        assertEquals("no level: unknown", -1, batteryPercent(Intent(Intent.ACTION_BATTERY_CHANGED)))
    }

    /** The second screen's in-game card reads charging from the same broadcast as the level. */
    @Test
    fun chargingComesFromTheSameBroadcast() {
        fun reading(
            status: Int,
            plugged: Int,
        ) = batteryReadingOf(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 82)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, status)
                .putExtra(BatteryManager.EXTRA_PLUGGED, plugged),
        )
        // Plugged now counts too (the top bar's bolt); charging itself is unchanged for the in-game card.
        assertEquals(BatteryReading(82, isCharging = true, isPlugged = true), reading(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals(BatteryReading(82, isCharging = false), reading(BatteryManager.BATTERY_STATUS_DISCHARGING, 0))
        assertEquals("plugged in but not charging", BatteryReading(82, isCharging = false, isPlugged = true), reading(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals("full on the charger", BatteryReading(82, isCharging = true, isPlugged = true), reading(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals("full, unplugged", BatteryReading(82, isCharging = false), reading(BatteryManager.BATTERY_STATUS_FULL, 0))
        assertEquals("no level: no reading", null, batteryReadingOf(Intent(Intent.ACTION_BATTERY_CHANGED)))
    }

    /** The top bar's bolt: on the charger (charging, full or held back by a charge limit), never on battery or unknown. */
    @Test
    fun boltShowsOnTheCharger() {
        fun onCharger(
            status: Int,
            plugged: Int,
        ) = batteryReadingOf(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 36)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, status)
                .putExtra(BatteryManager.EXTRA_PLUGGED, plugged),
        )?.isOnCharger
        assertEquals("charging on USB", true, onCharger(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_PLUGGED_USB))
        assertEquals("full, still plugged", true, onCharger(BatteryManager.BATTERY_STATUS_FULL, BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals("plugged, not charging", true, onCharger(BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC))
        assertEquals("unplugged", false, onCharger(BatteryManager.BATTERY_STATUS_DISCHARGING, 0))
        assertEquals("full, unplugged", false, onCharger(BatteryManager.BATTERY_STATUS_FULL, 0))
        assertEquals("status unknown, nothing plugged", false, onCharger(BatteryManager.BATTERY_STATUS_UNKNOWN, 0))
        assertEquals("no level: no reading, no bolt", null, batteryReadingOf(Intent(Intent.ACTION_BATTERY_CHANGED))?.isOnCharger)
    }
}
