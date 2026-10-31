package com.plumemu.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/*
 * Clock and battery for the top bar and the second screen's in-game card (no tickers,
 * no polling). Both listen to system broadcasts only while their lifecycle owner is started (the
 * Activity for the top bar, the second screen's own window for its card): the clock to the minute tick Android sends on the
 * minute (and to time, zone and 12/24 h changes), the battery to its sticky broadcast.
 */

/**
 * The time as the system formats it (12 or 24 h from the system setting), updated on each minute
 * tick. Returned as a state so only the text that reads it redraws.
 */
@Composable
internal fun rememberClockText(): State<String> {
    val context = LocalContext.current
    val now = rememberClockMillis()
    // Reads the 12 / 24 h setting each minute, so a change shows on the next tick at the latest.
    return remember(context) { derivedStateOf { formatClock(context, now.value) } }
}

/**
 * The time in milliseconds, read again on each minute tick (and on time, zone and 12/24 h
 * changes) while the lifecycle owner is started: the top bar's clock and the second screen's
 * in-game card. [clock] is the system clock; a test passes its own.
 */
@Composable
internal fun rememberClockMillis(clock: () -> Long = System::currentTimeMillis): State<Long> {
    val context = LocalContext.current
    val now = remember { mutableLongStateOf(clock()) }
    LifecycleStartEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    now.longValue = clock()
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                // Also sent when the person switches 12 / 24 h.
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        ContextCompat.registerReceiver(context, receiver, filter, SYSTEM_BROADCAST)
        // Minutes may have passed while stopped.
        now.longValue = clock()
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return now
}

internal fun formatClock(
    context: Context,
    millis: Long,
): String = android.text.format.DateFormat.getTimeFormat(context).format(Date(millis))

/**
 * The battery level as a percentage for the locale ("82%"), or null until the first broadcast;
 * a state, so only the text that reads it redraws. [reading]: the screen's own when it has one
 * ([LocalBatteryReading]), so the screen follows one broadcast receiver, not two.
 */
@Composable
internal fun rememberBatteryText(reading: State<BatteryReading?> = rememberBatteryReading()): State<String?> {
    val locale = currentLocale()
    return remember(locale) { derivedStateOf { reading.value?.let { formatBattery(it.percent, locale) } } }
}

/**
 * Whether the top bar's charging bolt shows: the device is on its charger ([BatteryReading.isOnCharger]), read
 * from [reading] as the percentage is; a state, so only the bolt's scope redraws on a plug or unplug.
 */
@Composable
internal fun rememberBatteryCharging(reading: State<BatteryReading?>): State<Boolean> =
    remember(reading) { derivedStateOf { reading.value?.isOnCharger == true } }

/**
 * The battery's level (0–100), whether it is charging and whether a charger is plugged in, from one
 * battery broadcast. [isPlugged] alone: on the charger but not charging (a charge limit, too warm).
 */
@Immutable
internal data class BatteryReading(
    val percent: Int,
    val isCharging: Boolean,
    val isPlugged: Boolean = false,
) {
    /** On the charger, charging or not: the top bar's bolt. */
    val isOnCharger: Boolean get() = isCharging || isPlugged
}

/**
 * The battery as the screen reads it, once for the whole screen (the top screen: `HomeScreen`, the
 * second screen's picking window: `SecondScreenCard`): the picking line reads its level and
 * charging from here rather than following the broadcast itself, so a move to a measured game registers
 * nothing and the line comes in the same frame as the played facts. Null outside a screen that gives it,
 * and with Show battery off (the screen gives [NoBatteryReading] and registers nothing).
 */
internal val LocalBatteryReading = staticCompositionLocalOf<State<BatteryReading?>> { NoBatteryReading }

/** No battery reading: what a screen gives [LocalBatteryReading] with Show battery off. */
internal val NoBatteryReading: State<BatteryReading?> =
    object : State<BatteryReading?> {
        override val value: BatteryReading? get() = null
    }

/**
 * The battery from its sticky broadcast while the lifecycle owner is started, or null until a
 * broadcast with a level arrives: the top bar's percentage and the second screen's in-game card.
 */
@Composable
internal fun rememberBatteryReading(): State<BatteryReading?> {
    val context = LocalContext.current
    val reading = remember { mutableStateOf<BatteryReading?>(null) }
    LifecycleStartEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    reading.value = batteryReadingOf(intent)
                }
            }
        // Sticky: registering returns the last level at once, so the bar is never empty when started.
        val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), SYSTEM_BROADCAST)
        sticky?.let { reading.value = batteryReadingOf(it) }
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    return reading
}

/** Level and charging from a battery broadcast, or null when it carries no level. */
internal fun batteryReadingOf(intent: Intent): BatteryReading? {
    val percent = batteryPercent(intent)
    if (percent == UNKNOWN) return null
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    // Full on the charger still reads as charging: the person sees the device is plugged in.
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)
    return BatteryReading(percent, isCharging, isPlugged = plugged != 0)
}

/** Level as 0–100 from a battery broadcast, or [UNKNOWN] when it carries none. */
internal fun batteryPercent(intent: Intent): Int {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level < 0 || scale <= 0) UNKNOWN else (level * PERCENT / scale)
}

internal fun formatBattery(
    percent: Int,
    locale: Locale,
): String = NumberFormat.getPercentInstance(locale).format(percent / PERCENT.toDouble())

/**
 * Exported, not "not exported": these broadcasts are protected (only the system may send them),
 * and on Android 6–12 androidx guards a not-exported receiver with the app's own signature
 * permission, which the system's battery broadcast does not hold ("Permission Denial:
 * broadcasting Intent BATTERY_CHANGED from null" on the API 23 emulator): the level would never
 * update after the first, sticky value.
 */
private const val SYSTEM_BROADCAST = ContextCompat.RECEIVER_EXPORTED

private const val UNKNOWN = -1
private const val PERCENT = 100
