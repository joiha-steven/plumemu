package com.plumemu.launcher.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.plumemu.launcher.FakePackages
import com.plumemu.launcher.RecordingLog
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class InstalledAppsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val started = mutableListOf<Intent>()
    private var failure: RuntimeException? = null
    private val log = RecordingLog()
    private val apps = InstalledApps(
        context.packageManager,
        "com.plumemu",
        { intent -> failure?.let { throw it } ?: started.add(intent) },
        log,
        UnconfinedTestDispatcher(),
    )

    @Test
    fun launchableAppsAreSortedByLabelWithoutPlumemuItself() = runTest {
        FakePackages.install("org.zeta", "org.zeta.Main", label = "zeta", launcher = true)
        FakePackages.install("com.alpha", "com.alpha.Main", label = "Alpha", launcher = true)
        FakePackages.install("com.emu", "com.emu.Main", label = "\u00c9mu", launcher = true)
        FakePackages.install("com.plumemu", "com.plumemu.MainActivity", label = "Plumemu", launcher = true)
        FakePackages.install("com.hidden.service", "com.hidden.Service", label = "Aaa no launcher")
        assertEquals(
            listOf(
                LaunchableApp("Alpha", "com.alpha", "com.alpha.Main"),
                LaunchableApp("\u00c9mu", "com.emu", "com.emu.Main"),
                LaunchableApp("zeta", "org.zeta", "org.zeta.Main"),
            ),
            apps.list(),
        )
    }

    @Test
    fun androidSettingsComesFirstThenTheRestByLabel() = runTest {
        FakePackages.install("com.alpha", "com.alpha.Main", label = "Alpha", launcher = true)
        FakePackages.install("com.android.settings", "com.android.settings.Settings", label = "Settings", launcher = true)
        FakePackages.install("org.zeta", "org.zeta.Main", label = "zeta", launcher = true)
        assertEquals(
            listOf(
                LaunchableApp("Settings", "com.android.settings", "com.android.settings.Settings"),
                LaunchableApp("Alpha", "com.alpha", "com.alpha.Main"),
                LaunchableApp("zeta", "org.zeta", "org.zeta.Main"),
            ),
            apps.list(),
        )
    }

    @Test
    fun noAppsGivesAnEmptyList() = runTest {
        assertEquals(emptyList<LaunchableApp>(), apps.list())
    }

    @Test
    fun launchingStartsTheActivityAsANewTask() {
        val app = LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")
        assertEquals(AppLaunchOutcome.Started, apps.launch(app))
        val intent = started.single()
        assertEquals("com.alpha/com.alpha.Main", intent.component?.flattenToString())
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun anAppUninstalledSinceTheListIsReported() {
        failure = ActivityNotFoundException("gone")
        assertEquals(AppLaunchOutcome.Missing, apps.launch(LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")))
    }

    @Test
    fun aRefusedStartIsAFailureLoggedByPackage() {
        val refusal = SecurityException("Permission Denial: starting Intent { cmp=com.alpha/.Main } from secret/path")
        failure = refusal
        assertEquals(AppLaunchOutcome.Failed(refusal), apps.launch(LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")))
        assertEquals(listOf("W apps: com.alpha refused to start (java.lang.SecurityException)"), log.lines)
    }

    /** As for games: startActivity can throw anything (a bad Intent, a dead system service); none of it may crash the home screen. */
    @Test
    fun anyOtherStartErrorIsAFailureNotACrash() {
        val odd = IllegalStateException("system service died: secret/path")
        failure = odd
        assertEquals(AppLaunchOutcome.Failed(odd), apps.launch(LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")))
        assertEquals(
            "one line, the package and the class only (never the message)",
            listOf("W apps: com.alpha could not start (java.lang.IllegalStateException)"),
            log.lines,
        )
    }
}
