package com.plumemu.launcher.play

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.plumemu.launcher.AndroidLauncherLog
import com.plumemu.launcher.LauncherLog
import com.plumemu.launcher.kind

/** How long a package was in front with the screen on; null when not known (option off, no Usage access, failed read). */
fun interface ForegroundTime {
    fun inFrontMs(packageName: String, fromWallMs: Long, toWallMs: Long): Long?

    companion object {
        val None: ForegroundTime = ForegroundTime { _, _, _ -> null }
    }
}

/** One Usage access event, reduced to what the sum needs. */
internal data class FrontEvent(val atMs: Long, val packageName: String?, val kind: Kind, val activity: String? = null) {
    enum class Kind {
        IN_FRONT, NOT_IN_FRONT, SCREEN_OFF, SCREEN_ON;

        val isScreen get() = this == SCREEN_OFF || this == SCREEN_ON
    }
}

internal object FrontIntervals {
    /**
     * Milliseconds [packageName] was in front with the screen on in [fromMs]..[toMs]. It is assumed in
     * front at [fromMs] (it was just started) until an event says otherwise; any of its activities
     * resumed counts it in front; an interval still open at [toMs] runs to it. [events] in time order.
     */
    fun sum(events: List<FrontEvent>, packageName: String, fromMs: Long, toMs: Long): Long {
        if (toMs <= fromMs) return 0
        val resumed = mutableSetOf<String?>(null) // null: the assumed activity of the start
        var screenOn = true
        var total = 0L
        var last = fromMs
        for (e in events) {
            if (e.atMs < fromMs) continue
            if (e.atMs > toMs) break
            val relevant = when (e.kind) {
                FrontEvent.Kind.SCREEN_OFF, FrontEvent.Kind.SCREEN_ON -> true
                else -> e.packageName == packageName
            }
            if (!relevant) continue
            if (resumed.isNotEmpty() && screenOn) total += e.atMs - last
            last = e.atMs
            when (e.kind) {
                FrontEvent.Kind.SCREEN_OFF -> screenOn = false
                FrontEvent.Kind.SCREEN_ON -> screenOn = true
                FrontEvent.Kind.IN_FRONT -> {
                    resumed.remove(null) // a real activity now stands for the assumed one
                    resumed.add(e.activity)
                }
                FrontEvent.Kind.NOT_IN_FRONT -> if (!resumed.remove(e.activity)) resumed.remove(null)
            }
        }
        if (resumed.isNotEmpty() && screenOn) total += toMs - last
        return total
    }
}

/** True when the person allowed Plumemu in Android's Usage access screen. */
fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
    val mode =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
    return mode == AppOpsManager.MODE_ALLOWED
}

/**
 * Reads Android's Usage access; null unless the person granted it to Plumemu. Android hands over the events of the
 * whole window; only the started package's and the screen's are kept, nothing is stored.
 */
class UsageStatsForegroundTime internal constructor(
    private val context: Context,
    private val log: LauncherLog,
    private val query: (Long, Long) -> UsageEvents?,
) : ForegroundTime {
    constructor(context: Context) : this(context.applicationContext, AndroidLauncherLog)

    private constructor(app: Context, log: LauncherLog) : this(app, log, { from, to ->
        (app.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager)?.queryEvents(from, to)
    })

    // The permission is declared by the app (its manifest); here the grant is checked first and a failed read answers null.
    override fun inFrontMs(packageName: String, fromWallMs: Long, toWallMs: Long): Long? {
        if (!hasUsageAccess(context)) return null
        return try {
            val usage = query(fromWallMs, toWallMs) ?: return null
            val events = mutableListOf<FrontEvent>()
            val event = UsageEvents.Event()
            while (usage.hasNextEvent()) {
                usage.getNextEvent(event)
                val kept = toFrontEvent(event) ?: continue
                if (kept.kind.isScreen || kept.packageName == packageName) events.add(kept)
            }
            FrontIntervals.sum(events, packageName, fromWallMs, toWallMs)
        } catch (e: RuntimeException) {
            // Vendors throw more than SecurityException/IllegalStateException here; a failed read must not lose the play.
            log.warn("play time: usage access read failed (${e.kind()})")
            null
        }
    }

    private fun toFrontEvent(e: UsageEvents.Event): FrontEvent? {
        val kind = when (e.eventType) {
            EVENT_RESUMED -> FrontEvent.Kind.IN_FRONT
            EVENT_PAUSED, EVENT_STOPPED -> FrontEvent.Kind.NOT_IN_FRONT
            EVENT_SCREEN_INTERACTIVE -> FrontEvent.Kind.SCREEN_ON
            EVENT_SCREEN_NON_INTERACTIVE -> FrontEvent.Kind.SCREEN_OFF
            else -> return null
        }
        return FrontEvent(e.timeStamp, e.packageName, kind, e.className)
    }

    private companion object {
        // ACTIVITY_RESUMED = MOVE_TO_FOREGROUND = 1; ACTIVITY_PAUSED = MOVE_TO_BACKGROUND = 2; the others are API 28/29+.
        const val EVENT_RESUMED = 1
        const val EVENT_PAUSED = 2
        const val EVENT_STOPPED = 23
        const val EVENT_SCREEN_INTERACTIVE = 15
        const val EVENT_SCREEN_NON_INTERACTIVE = 16
    }
}
