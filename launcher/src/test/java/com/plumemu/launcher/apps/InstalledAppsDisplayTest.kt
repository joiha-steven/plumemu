package com.plumemu.launcher.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.launcher.FakePackages
import com.plumemu.launcher.RecordingLog
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An app opened on another display (the second screen): where the start goes, and what a refusal there does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstalledAppsDisplayTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val here = mutableListOf<Intent>()
    private val there = mutableListOf<Pair<Intent, Int>>()
    private var hereFailure: RuntimeException? = null
    private var thereFailure: RuntimeException? = null
    private val log = RecordingLog()
    private val apps = InstalledApps(
        context.packageManager,
        "com.plumemu",
        { intent -> hereFailure?.let { throw it } ?: here.add(intent) },
        log,
        UnconfinedTestDispatcher(),
        displayStarter = { intent, display -> thereFailure?.let { throw it } ?: there.add(intent to display) },
    )
    private val alpha = LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")

    @Test
    fun aDisplayReachesTheDisplayStarterAndNotThePlainOne() {
        assertEquals(AppLaunchOutcome.Started, apps.launch(alpha, onDisplay = 7))
        assertTrue("not started here", here.isEmpty())
        val (intent, display) = there.single()
        assertEquals(7, display)
        assertEquals("com.alpha/com.alpha.Main", intent.component?.flattenToString())
        assertTrue("same flags as on this screen", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun noDisplayIsThePlainStart() {
        assertEquals(AppLaunchOutcome.Started, apps.launch(alpha))
        assertEquals(1, here.size)
        assertTrue(there.isEmpty())
    }

    @Test
    fun aGameAppIsNeverGivenADisplay() {
        FakePackages.install("com.alpha", "com.alpha.Main", label = "Alpha", launcher = true)
        assertEquals(AppLaunchOutcome.Started, apps.startGameApp("com.alpha"))
        assertEquals(1, here.size)
        assertTrue(there.isEmpty())
    }

    @Test
    fun aRefusalOnTheDisplayOpensTheAppOnThisScreen() {
        thereFailure = SecurityException("no")
        assertEquals(AppLaunchOutcome.StartedOnTop, apps.launch(alpha, onDisplay = 7))
        assertEquals(1, here.size)
        assertEquals("com.alpha/com.alpha.Main", here.single().component?.flattenToString())
    }

    @Test
    fun anyOtherFailureOnTheDisplayFallsBackToo() {
        thereFailure = IllegalStateException("display gone")
        assertEquals(AppLaunchOutcome.StartedOnTop, apps.launch(alpha, onDisplay = 7))
    }

    @Test
    fun anAppThatIsGoneIsMissingAndNotTriedAgain() {
        thereFailure = ActivityNotFoundException("gone")
        assertEquals(AppLaunchOutcome.Missing, apps.launch(alpha, onDisplay = 7))
        assertTrue("no retry on this screen", here.isEmpty())
    }

    @Test
    fun failingOnBothScreensIsAFailure() {
        thereFailure = SecurityException("no")
        hereFailure = SecurityException("no either")
        assertTrue(apps.launch(alpha, onDisplay = 7) is AppLaunchOutcome.Failed)
    }

    @Test
    fun theFallbackIsLoggedWithoutTheFailureMessage() {
        thereFailure = SecurityException("secret detail")
        apps.launch(alpha, onDisplay = 7)
        assertTrue("the fallback is logged: ${log.lines}", log.lines.any { it.startsWith("W ") && "second screen" in it })
        assertTrue("class only: ${log.lines}", log.lines.none { "secret detail" in it })
    }
}
