package com.plumemu.launcher.play.battery

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock

/**
 * The battery as `BatteryManager` reports it (public API, no permission).
 * Raw values: the device's own unit and sign, [UNKNOWN] when it reports nothing.
 * Every call is one quick call into the system; nothing here keeps the device awake or listens.
 * Tests pass a fake with a test clock.
 */
interface BatteryReader {
    /**
     * Elapsed time since boot in milliseconds, the clock the reads are timed by. It keeps running in
     * deep sleep (`SystemClock.elapsedRealtime`, not `uptimeMillis`, which stops): a sleep must show
     * as a gap between two reads, so it is never measured as play.
     */
    fun nowMs(): Long

    /** `BATTERY_PROPERTY_CURRENT_NOW`: documented in microamperes, negative while discharging; [UNKNOWN] when not reported. */
    fun currentNow(): Long

    /** `BATTERY_PROPERTY_CHARGE_COUNTER`: documented in microampere-hours; [UNKNOWN] when not reported. */
    fun chargeCounter(): Long

    /** The level, 0 to 100; [UNKNOWN_LEVEL] when not reported. */
    fun level(): Int

    fun isCharging(): Boolean

    /** `computeChargeTimeRemaining()` in milliseconds (Android 9+), [UNKNOWN] when it does not answer. */
    fun chargeTimeRemainingMs(): Long

    companion object {
        const val UNKNOWN = Long.MIN_VALUE
        const val UNKNOWN_LEVEL = -1
    }
}

/** [BatteryReader] over the system's `BatteryManager`. */
class AndroidBatteryReader(context: Context) : BatteryReader {
    private val manager = context.applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager?

    override fun nowMs(): Long = SystemClock.elapsedRealtime()

    // Long.MIN_VALUE (documented), 0 or Int.MIN_VALUE (an int property passed through) on some devices
    // means "not supported"; 0 is never a real reading while the screen is on (current) or the battery
    // holds charge (counter).
    override fun currentNow(): Long = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW).orUnknown()

    override fun chargeCounter(): Long = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).orUnknown()

    override fun level(): Int {
        val level = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return BatteryReader.UNKNOWN_LEVEL
        return if (level in 0..PERCENT) level else BatteryReader.UNKNOWN_LEVEL
    }

    override fun isCharging(): Boolean = manager?.isCharging ?: false

    override fun chargeTimeRemainingMs(): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return BatteryReader.UNKNOWN
        val ms = manager?.computeChargeTimeRemaining() ?: return BatteryReader.UNKNOWN
        return if (ms >= 0) ms else BatteryReader.UNKNOWN
    }

    private fun Long?.orUnknown(): Long = if (this == null || this == 0L || this == Int.MIN_VALUE.toLong()) BatteryReader.UNKNOWN else this

    private companion object {
        const val PERCENT = 100
    }
}
