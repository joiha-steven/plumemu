package com.plumemu.secondscreen

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Looper
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.drainMainLooper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The card as the second display's home: the alias in the manifest (disabled, standard launch mode,
 * the `SECONDARY_HOME` filter), enabled only for the default home with a screen switched on, written
 * only when it changes and always with `DONT_KILL_APP`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenHomeTest {
    private val app = RuntimeEnvironment.getApplication()
    private val packages = app.packageManager
    private val alias = ComponentName(app.packageName, SecondScreenHome.ALIAS)
    private val scope = CoroutineScope(SupervisorJob())
    private val logs = mutableListOf<String>()
    private val writes = mutableListOf<Boolean>()
    private var defaultHome = true
    private var homeAsked = 0
    private var androidHas = false

    @After
    fun tearDown() {
        scope.cancel()
        drainMainLooper()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun home() =
        SecondScreenHome(
            scope,
            isDefaultHome = { homeAsked++; defaultHome },
            readEnabled = { androidHas },
            writeEnabled = { writes += it; androidHas = it },
            io = Dispatchers.Main,
            main = Dispatchers.Main,
            info = { logs += it },
            warn = { logs += "W: $it" },
        )

    private fun secondaryHomes(): List<String> =
        packages.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_SECONDARY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName == app.packageName }
            .map { it.activityInfo.name }

    @Test
    fun theManifestDeclaresTheAliasDisabledWithAStandardTarget() {
        val info = packages.getActivityInfo(alias, PackageManager.MATCH_DISABLED_COMPONENTS)
        assertFalse("disabled until Plumemu is the home", info.enabled)
        assertEquals(SecondScreenCardActivity::class.java.name, info.targetActivity)
        assertEquals("standard: Android refuses a singleTask secondary home", ActivityInfo.LAUNCH_MULTIPLE, info.launchMode)
        val card = packages.getActivityInfo(ComponentName(app, SecondScreenCardActivity::class.java), 0)
        assertEquals(ActivityInfo.LAUNCH_MULTIPLE, card.launchMode)
        assertEquals("com.plumemu.card", card.taskAffinity)
        assertTrue("nothing of Plumemu's is a secondary home by default: ${secondaryHomes()}", secondaryHomes().isEmpty())
    }

    @Test
    fun theRealWriteUsesDontKillAppAndMakesTheAliasASecondaryHome() {
        SecondScreenHome.writeAlias(packages, alias, true)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, packages.getComponentEnabledSetting(alias))
        assertEquals("toggling never kills or restarts Plumemu", PackageManager.DONT_KILL_APP, shadowOf(packages).getComponentEnabledSettingFlags(alias))
        assertEquals(listOf(SecondScreenHome.ALIAS), secondaryHomes())
        SecondScreenHome.writeAlias(packages, alias, false)
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, packages.getComponentEnabledSetting(alias))
        assertEquals(PackageManager.DONT_KILL_APP, shadowOf(packages).getComponentEnabledSettingFlags(alias))
        assertTrue(secondaryHomes().isEmpty())
    }

    @Test
    fun enabledOnlyForTheDefaultHomeWithTheScreenOn() {
        val home = home()
        home.update(true)
        idle()
        assertEquals(listOf(true), writes)
        assertTrue(home.isOn)
        home.update(false)
        idle()
        assertEquals(listOf(true, false), writes)
        assertFalse(home.isOn)
    }

    @Test
    fun notTheDefaultHomeWritesNothing() {
        defaultHome = false
        val home = home()
        home.update(true)
        idle()
        assertTrue("the manifest's disabled state stands", writes.isEmpty())
        assertFalse(home.isOn)
    }

    @Test
    fun notWrittenWhenUnchanged() {
        val home = home()
        repeat(3) {
            home.update(true)
            idle()
        }
        assertEquals(listOf(true), writes)
        assertEquals("asked again each time (the person may have changed the home app)", 3, homeAsked)
    }

    @Test
    fun aScreenOffAsksNothingOfTheHomeRole() {
        val home = home()
        home.update(false)
        idle()
        assertEquals(0, homeAsked)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun noLongerTheDefaultHomeDisablesIt() {
        val home = home()
        home.update(true)
        idle()
        defaultHome = false
        home.update(true)
        idle()
        assertEquals(listOf(true, false), writes)
    }

    @Test
    fun aRefusedWriteIsLoggedAndNeverCrashes() {
        val home =
            SecondScreenHome(
                scope,
                isDefaultHome = { true },
                readEnabled = { false },
                writeEnabled = { throw SecurityException("test") },
                io = Dispatchers.Main,
                main = Dispatchers.Main,
                info = { logs += it },
                warn = { logs += "W: $it" },
            )
        home.update(true)
        idle()
        assertFalse(home.isOn)
        assertEquals(listOf("W: Second screen card: could not enable the bottom screen's home (SecurityException)"), logs)
    }
}
