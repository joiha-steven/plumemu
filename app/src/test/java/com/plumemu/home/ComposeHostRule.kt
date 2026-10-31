package com.plumemu.home

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

/**
 * `createComposeRule` hosts content in a bare `ComponentActivity`. In a library module the
 * ui-test-manifest artifact declares it; an app module's unit tests use the app's own merged
 * manifest, so Robolectric cannot resolve it ("Unable to resolve activity"). This registers the
 * host with Robolectric's package manager instead of adding it to the debug APK.
 */
private class RegisterComposeHost : ExternalResource() {
    override fun before() {
        val app = RuntimeEnvironment.getApplication()
        val host = ComponentName(app, ComponentActivity::class.java)
        val packageManager = shadowOf(app.packageManager)
        packageManager.addActivityIfNotPresent(host)
        packageManager.addIntentFilterForActivity(
            host,
            IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
        )
    }
}

/**
 * Runs what the main looper still holds once the compose rule has closed its activity.
 *
 * Tearing the content down can write snapshot state (a focused text field loses its focus when its
 * node leaves), and Compose then queues the apply on `AndroidUiDispatcher.Main`, a process-wide
 * dispatcher, with a main-looper message and a Choreographer frame, and marks both as scheduled.
 * Robolectric clears the looper before the next test without running them, so the dispatcher stays
 * marked forever: every later `captureRoboImage` in the same JVM got no frames and drew its panels
 * empty, without its caret (as SettingsAccountAlignTest once did). Running the queue here lets
 * the dispatcher finish and clear its marks.
 */
private class DrainMainLooper : ExternalResource() {
    override fun after() = drainMainLooper()
}

/**
 * Runs the main looper until nothing waits on it ([DrainMainLooper]). A test that hosts compose
 * outside [rulesAround] (a window of its own, as SecondScreenHostTest does) calls it at the end of
 * its teardown, after its windows are gone.
 */
internal fun drainMainLooper() {
    val looper = shadowOf(Looper.getMainLooper())
    // A task run here can post another (a frame after a message): go round until none is left.
    repeat(DRAIN_ROUNDS) {
        if (looper.isLooperEmpty()) return
        looper.runToEndOfTasks()
    }
}

private const val DRAIN_ROUNDS = 20

/** No task waits on this main looper, due now or later. */
internal fun ShadowLooper.isLooperEmpty(): Boolean = isIdle && nextScheduledTaskTime == Duration.ZERO

/**
 * A compose rule whose host activity resolves in the app module; use with [rulesAround].
 *
 * The v2 rule runs effects on a standard test dispatcher, driven from the main thread, as the app's
 * effects run on the main thread. The v1 rule's unconfined dispatcher resumed an effect on whatever
 * thread finished its `withContext(Dispatchers.Default)`, so search wrote its results and count
 * from a worker thread while the main thread composed, and the count's write was sometimes never
 * seen (ComposeHostRuleTest).
 */
internal fun appComposeRule(): ComposeContentTestRule = createComposeRule()

/** The rule chain to annotate with `@get:Rule`: register the host, run [compose], then drain the main looper. */
internal fun rulesAround(compose: ComposeContentTestRule): RuleChain = RuleChain.outerRule(RegisterComposeHost()).around(DrainMainLooper()).around(compose)
