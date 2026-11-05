package com.plumemu.launcher.play

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowUsageStatsManager

/** Reads Android's Usage access: the events in front of the game are summed when the person allowed it, nothing otherwise. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageStatsForegroundTimeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager

    private fun grant(mode: Int) {
        shadowOf(context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
            .setMode(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, mode)
    }

    private fun event(
        pkg: String,
        at: Long,
        type: Int,
    ) = shadowOf(usage).addEvent(ShadowUsageStatsManager.EventBuilder.buildEvent().setPackage(pkg).setClass("$pkg.Main").setTimeStamp(at).setEventType(type).build())

    @Test
    fun grantedSumsTheTimeTheGameWasInFront() {
        grant(AppOpsManager.MODE_ALLOWED)
        event("game.pkg", 2_000, UsageEvents.Event.ACTIVITY_RESUMED)
        event("game.pkg", 7_000, UsageEvents.Event.ACTIVITY_PAUSED)
        event("other.pkg", 7_000, UsageEvents.Event.ACTIVITY_RESUMED)
        event("other.pkg", 20_000, UsageEvents.Event.ACTIVITY_PAUSED)
        event("game.pkg", 20_000, UsageEvents.Event.ACTIVITY_RESUMED)
        event("game.pkg", 24_000, UsageEvents.Event.ACTIVITY_PAUSED)

        // 0..2 s assumed in front (just started), 2..7 s in front, 20..24 s in front: 2 + 5 + 4 s.
        assertEquals(11_000L, UsageStatsForegroundTime(context).inFrontMs("game.pkg", 0, 30_000))
        assertTrue(hasUsageAccess(context))
    }

    @Test
    fun notGrantedAnswersNullAndReadsNothing() {
        grant(AppOpsManager.MODE_IGNORED)
        event("game.pkg", 2_000, UsageEvents.Event.ACTIVITY_RESUMED)

        assertNull(UsageStatsForegroundTime(context).inFrontMs("game.pkg", 0, 30_000))
        assertFalse(hasUsageAccess(context))
    }

    @Test
    fun anyRuntimeFailureOfTheReadAnswersNullAndLogsOnlyTheKind() {
        grant(AppOpsManager.MODE_ALLOWED)
        val log = com.plumemu.launcher.RecordingLog()
        val source = UsageStatsForegroundTime(context, log) { _, _ -> throw NullPointerException("vendor") }

        assertNull(source.inFrontMs("game.pkg", 0, 30_000))
        assertEquals(1, log.lines.size)
        assertTrue(log.lines.single().contains("NullPointerException"))
        assertFalse(log.lines.single().contains("vendor"))
    }
}
