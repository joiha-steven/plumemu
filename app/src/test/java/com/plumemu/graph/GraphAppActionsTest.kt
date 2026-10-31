package com.plumemu.graph

import com.plumemu.coredata.db.AppKind
import com.plumemu.game.AppOpenOutcome
import com.plumemu.launcher.apps.AppLaunchOutcome
import com.plumemu.launcher.apps.LaunchableApp
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Apps entry's row to the launcher: looked up in the graph's list (never parsed from the id),
 * one log line with the row id for each failure the launcher does not log itself, and the list
 * read again when the app is gone or the row is stale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GraphAppActionsTest {
    private val alpha = LaunchableApp("Alpha", "com.example.alpha", "com.example.alpha.Main")
    private val alphaRow = "com.example.alpha/com.example.alpha.Main"
    private val logs = mutableListOf<String>()
    private val launched = mutableListOf<LaunchableApp>()
    private var refreshes = 0
    private var refreshFailure: Exception? = null

    private fun TestScope.actions(
        listed: List<LaunchableApp> = listOf(alpha),
        launch: suspend (LaunchableApp) -> AppLaunchOutcome? = { AppLaunchOutcome.Started },
    ) = GraphAppActions(
        listed = { listed },
        launch = { app -> launched += app; launch(app) },
        refreshApps = { refreshes++; refreshFailure?.let { throw it } },
        scope = this,
        log = { logs += it },
    )

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun TestScope.kindActions(
        listed: List<LaunchableApp> = listOf(alpha),
        allowed: Boolean = true,
        store: suspend (String, AppKind) -> Boolean,
    ) = GraphAppActions(
        listed = { listed },
        launch = { null },
        refreshApps = { refreshes++ },
        scope = this,
        canChooseKindOf = { allowed },
        setChoiceOf = store,
        log = { logs += it },
    )

    @Test
    fun theAnswerIsStoredByThePackageOfTheListedApp() = run {
        val stored = mutableListOf<Pair<String, AppKind>>()
        val followed = kindActions { packageName, kind -> stored += packageName to kind; true }.setKind(alphaRow, AppKind.PORT)
        assertEquals(listOf("com.example.alpha" to AppKind.PORT), stored)
        assertTrue(followed)
    }

    @Test
    fun anAnswerTheLibraryCouldNotFollowYetIsSaidAndLogged() = run {
        assertFalse(kindActions { _, _ -> false }.setKind(alphaRow, AppKind.GAME))
        assertEquals(listOf("apps: $alphaRow answer stored, the library follows at the next read"), logs)
    }

    @Test
    fun anAnswerThatCannotBeStoredReachesTheCaller() = run {
        val failure = runCatching { kindActions { _, _ -> throw IOException("disk full") }.setKind(alphaRow, AppKind.GAME) }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
    }

    @Test
    fun aRowTheListNoLongerHasIsNotAnsweredAndTheListReadAgain() = run {
        var asked = false
        val failure = runCatching { kindActions(listed = emptyList()) { _, _ -> asked = true; true }.setKind(alphaRow, AppKind.GAME) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertFalse("nothing was stored", asked)
        assertEquals(1, refreshes)
    }

    @Test
    fun theChoiceIsOfferedOnlyWhereTheLauncherAllowsIt() = run {
        assertTrue(kindActions { _, _ -> true }.canChooseKind(alphaRow))
        assertFalse(kindActions(allowed = false) { _, _ -> true }.canChooseKind(alphaRow))
        assertFalse(kindActions(listed = emptyList()) { _, _ -> true }.canChooseKind(alphaRow))
    }

    @Test
    fun theListedAppOfTheRowIsStarted() = run {
        assertEquals(AppOpenOutcome.Started, actions().open(alphaRow))
        assertEquals(listOf(alpha), launched)
        assertEquals("the launcher logs a start itself", emptyList<String>(), logs)
        assertEquals(0, refreshes)
    }

    @Test
    fun aRowTheListNoLongerHasIsNotStartedLoggedAndTheListReadAgain() = run {
        assertEquals(AppOpenOutcome.NotListed, actions(listed = emptyList()).open(alphaRow))
        assertEquals("nothing is started from the id alone", emptyList<LaunchableApp>(), launched)
        assertEquals(listOf("apps: row $alphaRow is not in the app list; reading it again"), logs)
        assertEquals(1, refreshes)
    }

    @Test
    fun anAppGoneSinceTheListWasReadReadsTheListAgain() = run {
        assertEquals(AppOpenOutcome.Missing, actions(launch = { AppLaunchOutcome.Missing }).open(alphaRow))
        assertEquals(1, refreshes)
        assertEquals("InstalledApps has logged it", emptyList<String>(), logs)
    }

    @Test
    fun aRefusedStartIsRefused() = run {
        assertEquals(AppOpenOutcome.Refused, actions(launch = { AppLaunchOutcome.Failed(SecurityException("x")) }).open(alphaRow))
        assertEquals(0, refreshes)
    }

    @Test
    fun noLauncherIsUnavailableAndLogged() = run {
        assertEquals(AppOpenOutcome.Unavailable, actions(launch = { null }).open(alphaRow))
        assertEquals(listOf("apps: $alphaRow not opened, the launcher is not set up"), logs)
    }

    @Test
    fun aLauncherThatThrowsWhileSettingUpIsUnavailableLoggedByClassOnly() = run {
        assertEquals(AppOpenOutcome.Unavailable, actions(launch = { throw IOException("/storage/secret/path") }).open(alphaRow))
        assertEquals(listOf("apps: $alphaRow not opened, the launcher could not be set up (IOException)"), logs)
    }

    @Test
    fun aFailedReReadIsLoggedNotThrown() = run {
        refreshFailure = IllegalStateException("/storage/secret/path")
        assertEquals(AppOpenOutcome.Missing, actions(launch = { AppLaunchOutcome.Missing }).open(alphaRow))
        assertEquals(listOf("apps: the app list could not be read again (IllegalStateException)"), logs)
        assertFalse(logs.single().contains("secret"))
    }
}
