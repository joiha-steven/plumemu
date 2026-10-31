package com.plumemu.graph

import com.plumemu.game.AppToolResult
import com.plumemu.launcher.apps.AppToolOutcome
import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** App info and Uninstall from the Apps menu to the launcher: the row looked up in the graph's list, every failure logged with the row id. */
@OptIn(ExperimentalCoroutinesApi::class)
class GraphAppToolsTest {
    private val alpha = LaunchableApp("Alpha", "com.example.alpha", "com.example.alpha.Main")
    private val alphaRow = "com.example.alpha/com.example.alpha.Main"
    private val logs = mutableListOf<String>()
    private val infoOf = mutableListOf<LaunchableApp>()
    private val uninstallOf = mutableListOf<LaunchableApp>()
    private var refreshes = 0
    private var outcome: AppToolOutcome? = AppToolOutcome.Started

    private fun TestScope.actions(listed: List<LaunchableApp> = listOf(alpha), canUninstall: Boolean = true) =
        GraphAppActions(
            listed = { listed },
            launch = { null },
            refreshApps = { refreshes++ },
            scope = this,
            canUninstallPackage = { canUninstall },
            showInfoOf = { infoOf += it; outcome },
            uninstallOf = { uninstallOf += it; outcome },
            log = { logs += it },
        )

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun infoAndUninstallGoToTheLauncherWithTheListedApp() = run {
        val actions = actions()
        assertEquals(AppToolResult.Started, actions.showInfo(alphaRow))
        assertEquals(AppToolResult.Started, actions.uninstall(alphaRow))
        assertEquals(listOf(alpha), infoOf)
        assertEquals(listOf(alpha), uninstallOf)
    }

    @Test
    fun uninstallIsOfferedOnlyWhenTheLauncherSaysSoAndNeverForARowThatIsGone() = run {
        assertTrue(actions().canUninstall(alphaRow))
        assertFalse(actions(canUninstall = false).canUninstall(alphaRow))
        assertFalse(actions(listed = emptyList()).canUninstall(alphaRow))
    }

    @Test
    fun aRowThatLeftTheListReadsTheListAgainAndStartsNothing() = run {
        assertEquals(AppToolResult.NotListed, actions(listed = emptyList()).uninstall(alphaRow))
        assertTrue(uninstallOf.isEmpty())
        assertEquals(1, refreshes)
        assertTrue(logs.single().contains(alphaRow))
    }

    @Test
    fun aRefusalAndALauncherThatIsNotSetUpAreFailedAndLogged() = run {
        outcome = AppToolOutcome.Failed
        assertEquals(AppToolResult.Failed, actions().showInfo(alphaRow))
        outcome = null
        assertEquals(AppToolResult.Failed, actions().uninstall(alphaRow))
        assertTrue("the launcher not being set up is logged with the row", logs.single().contains(alphaRow))
    }
}
