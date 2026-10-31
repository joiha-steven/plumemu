package com.plumemu.launcher.apps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.plumemu.launcher.RecordingLog
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** App info and Uninstall of the app menu: the intents, who may be uninstalled, and a refusal that is told. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class InstalledAppsToolsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val tools = mutableListOf<Intent>()
    private val observed = mutableListOf<Intent>()
    private var failure: RuntimeException? = null
    private val log = RecordingLog()
    private val apps = InstalledApps(
        context.packageManager,
        "com.plumemu",
        { observed += it },
        log,
        UnconfinedTestDispatcher(),
        plainStarter = { intent -> failure?.let { throw it } ?: tools.add(intent) },
    )
    private val alpha = LaunchableApp("Alpha", "com.alpha", "com.alpha.Main")

    private fun install(pkg: String, flags: Int) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = pkg
            applicationInfo = ApplicationInfo().apply {
                packageName = pkg
                this.flags = flags
            }
        })
    }

    @Test
    fun appInfoOpensTheDetailsPageOfThePackage() {
        assertEquals(AppToolOutcome.Started, apps.showInfo(alpha))
        val intent = tools.single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:com.alpha", intent.data.toString())
        assertTrue("not an app opened from Plumemu: the second screen is not told", observed.isEmpty())
    }

    @Test
    fun uninstallAsksAndroidToConfirmForThePackage() {
        assertEquals(AppToolOutcome.Started, apps.uninstall(alpha))
        val intent = tools.single()
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("package:com.alpha", intent.data.toString())
    }

    @Test
    fun uninstallIsNeverStartedForPlumemuItself() {
        assertEquals(AppToolOutcome.Failed, apps.uninstall(LaunchableApp("Plumemu", "com.plumemu", "com.plumemu.Main")))
        assertTrue(tools.isEmpty())
        assertTrue(log.lines.any { it.contains("uninstall refused") })
    }

    @Test
    fun anOrdinaryAppAndAnUpdatedSystemAppCanBeUninstalledAPlainSystemAppAndPlumemuCannot() {
        install("com.alpha", 0)
        install("com.sys.plain", ApplicationInfo.FLAG_SYSTEM)
        install("com.sys.updated", ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)
        install("com.plumemu", 0)
        assertTrue(apps.canUninstall("com.alpha"))
        assertFalse(apps.canUninstall("com.sys.plain"))
        assertTrue(apps.canUninstall("com.sys.updated"))
        assertFalse(apps.canUninstall("com.plumemu"))
        assertFalse("not installed any more", apps.canUninstall("com.gone"))
    }

    @Test
    fun aScreenThatCannotBeOpenedIsFailedAndLoggedWithoutTheMessage() {
        failure = ActivityNotFoundException("secret detail")
        assertEquals(AppToolOutcome.Failed, apps.showInfo(alpha))
        failure = IllegalStateException("secret detail")
        assertEquals(AppToolOutcome.Failed, apps.uninstall(alpha))
        val lines = log.lines.joinToString("\n")
        assertTrue(lines.contains("com.alpha"))
        assertFalse(lines.contains("secret detail"))
    }
}
