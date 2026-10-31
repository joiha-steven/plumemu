package com.plumemu.launcher.play.battery

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `BatteryManager`'s "not supported" answers are no read: `Long.MIN_VALUE` (documented), 0 (some
 * devices) and `Int.MIN_VALUE` (an int property passed through as a long), never a draw of 2,147 A.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidBatteryReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = shadowOf(context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
    private val reader = AndroidBatteryReader(context)

    private fun reads(value: Long): Pair<Long, Long> {
        manager.setLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, value)
        manager.setLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, value)
        return reader.currentNow() to reader.chargeCounter()
    }

    @Test
    fun notSupportedAnswersAreUnknown() {
        val unknown = BatteryReader.UNKNOWN to BatteryReader.UNKNOWN
        assertEquals(unknown, reads(Long.MIN_VALUE))
        assertEquals(unknown, reads(0))
        assertEquals(unknown, reads(Int.MIN_VALUE.toLong()))
    }

    @Test
    fun aRealReadPassesThroughAsTheDeviceGivesIt() {
        assertEquals(-412_000L to -412_000L, reads(-412_000))
        assertEquals(2_060_000L to 2_060_000L, reads(2_060_000))
    }
}
