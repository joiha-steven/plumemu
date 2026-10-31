package com.plumemu.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.plumemu.launcher.FakePackages
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.play.PlayBattery
import com.plumemu.coredata.db.SessionBattery
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Installed apps as games: the list carries Android's own game mark, read without any
 * new permission (the category from API 26, the old flag on Android 6 and 7), and an app game starts
 * by its package alone, like a tap on its home screen icon.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class InstalledAppsGameTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val started = mutableListOf<Intent>()
    private var failure: RuntimeException? = null
    private var batteryStarts = 0
    private val battery = object : PlayBattery {
        override fun onPlayStarting(usualDrawMicroamps: Long?) {
            batteryStarts++
        }

        override fun takeMeasured(): SessionBattery? = null
    }
    private val apps = InstalledApps(
        context.packageManager,
        "com.plumemu",
        { intent -> failure?.let { throw it } ?: started.add(intent) },
        RecordingLog(),
        UnconfinedTestDispatcher(),
        battery,
    )

    @Test
    fun plumemuAndAndroidsSettingsAreNotOfferedAsGames() {
        assertFalse(apps.canShowAsGame("com.plumemu"))
        assertFalse(apps.canShowAsGame(InstalledApps.ANDROID_SETTINGS))
        assertTrue(apps.canShowAsGame("com.game"))
    }

    @Test
    fun theListCarriesAndroidsGameMark() = runTest {
        FakePackages.install("com.game", "com.game.Main", label = "Game", launcher = true, isGame = true)
        FakePackages.install("com.tool", "com.tool.Main", label = "Tool", launcher = true)

        val listed = apps.list().associate { it.packageName to it.isGame }

        assertEquals(mapOf("com.game" to true, "com.tool" to false), listed)
    }

    @Test
    fun theGameMarkIsTheCategoryFromApi26AndTheFlagBelow() {
        @Suppress("DEPRECATION") // FLAG_IS_GAME is the only game mark below API 26.
        val flag = ApplicationInfo.FLAG_IS_GAME
        assertTrue(InstalledApps.isGameMarked(23, -1, flag))
        assertFalse(InstalledApps.isGameMarked(23, -1, 0))
        assertTrue(InstalledApps.isGameMarked(26, ApplicationInfo.CATEGORY_GAME, 0))
        assertFalse("the old flag alone no longer counts from 26", InstalledApps.isGameMarked(34, ApplicationInfo.CATEGORY_UNDEFINED, flag))
        assertFalse(InstalledApps.isGameMarked(34, ApplicationInfo.CATEGORY_PRODUCTIVITY, 0))
    }

    @Test
    fun anAppGameStartsItsPackagesLauncherActivityAsANewTask() {
        FakePackages.install("com.game", "com.game.Main", label = "Game", launcher = true, isGame = true)

        assertEquals(AppLaunchOutcome.Started, apps.startGameApp("com.game"))

        val intent = started.single()
        assertEquals("com.game/com.game.Main", intent.component?.flattenToString())
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals("the play's own note tells the battery, with the game's pace", 0, batteryStarts)
    }

    @Test
    fun aPackageWithNoLauncherActivityIsMissingAndNothingStarts() {
        FakePackages.install("com.service", "com.service.Worker", label = "Service")

        assertEquals(AppLaunchOutcome.Missing, apps.startGameApp("com.service"))
        assertEquals(AppLaunchOutcome.Missing, apps.startGameApp("com.never.installed"))
        assertTrue(started.isEmpty())
    }

    @Test
    fun anUninstalledAppGameIsMissing() {
        FakePackages.install("com.game", "com.game.Main", label = "Game", launcher = true)
        FakePackages.uninstall("com.game")

        assertEquals(AppLaunchOutcome.Missing, apps.startGameApp("com.game"))
    }

    @Test
    fun aDisabledAppIsStillInstalledButHasNothingToStart() {
        FakePackages.install("com.game", "com.game.Main", label = "Game", launcher = true, isGame = true)
        context.packageManager.setApplicationEnabledSetting("com.game", PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0)

        assertEquals(PackagePresence.Installed, apps.presenceOf("com.game"))
        assertEquals("Play says it is no longer installed or turned off", AppLaunchOutcome.Missing, apps.startGameApp("com.game"))
    }

    @Test
    fun anAppAndroidNoLongerKnowsIsNotInstalled() {
        FakePackages.install("com.game", "com.game.Main", label = "Game", launcher = true)
        assertEquals(PackagePresence.Installed, apps.presenceOf("com.game"))

        FakePackages.uninstall("com.game")

        assertEquals(PackagePresence.NotInstalled, apps.presenceOf("com.game"))
        assertEquals(PackagePresence.NotInstalled, apps.presenceOf("com.never.installed"))
    }

    @Test
    fun aPackageAndroidKeepsOnlyAsUninstalledWithDataIsNotInstalled() {
        // Found only through the uninstalled-packages flag (as an app on unmounted storage is), but without
        // FLAG_INSTALLED: removed with `pm uninstall -k`, so it counts as gone.
        FakePackages.install("com.game", "com.game.Main", label = "Game", launcher = true, isGame = true, installed = false)

        assertEquals(PackagePresence.NotInstalled, apps.presenceOf("com.game"))
        assertEquals(PackagePresence.NotInstalled, InstalledApps.presenceFromFlags(0))
        assertEquals(PackagePresence.Installed, InstalledApps.presenceFromFlags(ApplicationInfo.FLAG_INSTALLED))
    }

    @Test
    fun aStartThatThrowsIsAFailureNotACrash() {
        FakePackages.install("com.game", "com.game.Main", label = "Game", launcher = true)
        failure = SecurityException("refused")

        val outcome = apps.startGameApp("com.game")

        assertTrue("$outcome", outcome is AppLaunchOutcome.Failed)
    }
}
