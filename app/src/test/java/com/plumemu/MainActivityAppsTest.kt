package com.plumemu

import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.lifecycle.Lifecycle
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.game.AppOpenOutcome
import com.plumemu.home.drainMainLooper
import com.plumemu.secondscreen.Presence
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A on a row of the Apps entry, through the wiring `MainActivity` gives the home screen
 * (`HomeCallbacks.apps`, which `HomeScreen` hands to the game panel's controller) and the real
 * graph (its launcher, its app list, its game-start relay): the app's own activity is started and
 * the second screen hears of it. Needs no content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityAppsTest {
    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()
    private val app = activity.application as PlumApplication

    @After
    fun tearDown() {
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) controller.pause().stop()
        controller.destroy()
        drainMainLooper()
    }

    private val alphaRow = "com.example.alpha/com.example.alpha.Main"

    /** Once the Activity's callback only logged "not built yet", so on a Thor A on an app did nothing. */
    @Test
    fun aOnAnAppsRowStartsThatApp() {
        listAlpha()

        val outcome = onMainLoop { activity.homeCallbacks.apps.open(alphaRow) }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(AppOpenOutcome.Started, outcome)
        val started = shadowOf(app).nextStartedActivity
        assertEquals(
            "the app's launcher activity was started",
            ComponentName("com.example.alpha", "com.example.alpha.Main"),
            started?.component,
        )
        assertTrue("as a new task, like a home screen icon", started!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /** Mirrors MainActivityGameStartsTest: an app opened from Plumemu counts as a game for the second screen. */
    @Test
    fun anOpenedAppMakesTheNextStopPlaying() {
        listAlpha()
        val presence = checkNotNull(app.graph.gameStarts.target)
        assertEquals(Presence.InPlumemu, presence.current.value)

        assertEquals(AppOpenOutcome.Started, onMainLoop { activity.homeCallbacks.apps.open(alphaRow) })
        controller.pause().stop()

        assertEquals(Presence.Playing, presence.current.value)
    }

    /** Uninstalled after the list was read: nothing starts, the stop after it is leaving Plumemu, and the row leaves the list. */
    @Test
    fun anAppGoneSinceTheListIsMissingLeavesTheListAndTheStopIsAway() {
        listAlpha()
        val presence = checkNotNull(app.graph.gameStarts.target)
        shadowOf(app.packageManager).removeActivity(ComponentName("com.example.alpha", "com.example.alpha.Main"))
        shadowOf(app.packageManager).removePackage("com.example.alpha")
        // Robolectric then throws ActivityNotFoundException, as Android does for an uninstalled app.
        shadowOf(app).checkActivities(true)

        val outcome = onMainLoop { activity.homeCallbacks.apps.open(alphaRow) }

        assertEquals(AppOpenOutcome.Missing, outcome)
        assertNull(shadowOf(app).nextStartedActivity)
        onMainLoop { app.graph.listedApps.first { list -> list.none { it.packageName == "com.example.alpha" } } }
        controller.pause().stop()
        assertEquals("the failed start cleared the flag", Presence.Away, presence.current.value)
    }

    @Test
    fun aRowNotInTheListStartsNothing() {
        listAlpha()

        val outcome = onMainLoop { activity.homeCallbacks.apps.open("com.example.other/com.example.other.Main") }

        assertEquals(AppOpenOutcome.NotListed, outcome)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun listAlpha() {
        installLauncherApp(app, "com.example.alpha", "com.example.alpha.Main", "Alpha")
        onMainLoop { app.graph.refreshApps() }
        assertEquals(listOf("Alpha"), app.graph.listedApps.value.map { it.label })
        shadowOf(app).clearNextStartedActivities()
    }
}
