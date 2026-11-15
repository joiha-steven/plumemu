package com.plumemu.graph

import com.plumemu.game.AppOpenOutcome
import com.plumemu.launcher.apps.AppLaunchOutcome
import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Apps entry opening on the bottom screen: the display asked for and passed to the launcher, whatever the outcome. */
@OptIn(ExperimentalCoroutinesApi::class)
class GraphAppActionsBottomTest {
    private val alpha = LaunchableApp("Alpha", "com.example.alpha", "com.example.alpha.Main")
    private val alphaRow = "com.example.alpha/com.example.alpha.Main"
    private val events = mutableListOf<String>()
    private var display: Int? = 4
    private val asked = mutableListOf<Boolean>()

    private fun TestScope.actions(launch: suspend (Int?) -> AppLaunchOutcome? = { AppLaunchOutcome.Started }) = GraphAppActions(
        listed = { listOf(alpha) },
        launch = { _, on -> events += "launch $on"; launch(on) },
        refreshApps = {},
        scope = this,
        displayFor = { other -> asked += other; display },
        log = {},
    )

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun theBottomDisplayIsPassedToTheLauncher() = run {
        assertEquals(AppOpenOutcome.Started, actions().open(alphaRow))
        assertEquals(listOf("launch 4"), events)
    }

    @Test
    fun aOneOffIsPassedToTheChoice() = run {
        actions().open(alphaRow, otherScreen = true)
        actions().open(alphaRow)
        assertEquals(listOf(true, false), asked)
    }

    @Test
    fun thisScreenAsksForNoDisplay() = run {
        display = null
        assertEquals(AppOpenOutcome.Started, actions().open(alphaRow))
        assertEquals(listOf("launch null"), events)
    }

    @Test
    fun anAppOpenedOnTopInsteadSaysSo() = run {
        assertEquals(AppOpenOutcome.StartedOnTop, actions { AppLaunchOutcome.StartedOnTop }.open(alphaRow))
        assertEquals(listOf("launch 4"), events)
    }

    @Test
    fun everyOtherFailureOnTheBottomScreenIsOneLaunch() = run {
        for (outcome in listOf(AppLaunchOutcome.Missing, AppLaunchOutcome.Failed(SecurityException("x")), null)) {
            events.clear()
            actions { outcome }.open(alphaRow)
            assertEquals("$outcome", listOf("launch 4"), events)
        }
    }

    @Test
    fun aLauncherThatThrowsIsUnavailable() = run {
        assertEquals(AppOpenOutcome.Unavailable, actions { throw IllegalStateException("x") }.open(alphaRow))
        assertEquals(listOf("launch 4"), events)
    }
}
