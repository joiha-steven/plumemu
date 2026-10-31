package com.plumemu.secondscreen

import com.plumemu.coredata.settings.HomeScreenSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The home screen's system info band is off by default where a second screen is in use. */
class SystemInfoDefaultTest {
    private val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
    private val unsure = SecondScreen(2, "Overlay", ScreenKind.Unsure)
    private val neverSet = PlumSettings.Defaults

    @Test
    fun singleScreenStaysOn() {
        val after = withSystemInfoDecided(neverSet, null)
        assertSame(neverSet, after)
        assertEquals(true, after.homeScreen.isSystemInfoShown)
    }

    @Test
    fun secondScreenAndNeverSetWritesOffOnce() {
        val after = withSystemInfoDecided(neverSet, builtIn)
        assertEquals(false, after.homeScreen.showSystemInfo)
        assertSame("a second pass changes nothing", after, withSystemInfoDecided(after, builtIn))
    }

    @Test
    fun aPersonsOnOrOffIsNeverOverridden() {
        for (value in listOf(true, false)) {
            val set = neverSet.copy(homeScreen = HomeScreenSettings(showSystemInfo = value))
            assertSame(set, withSystemInfoDecided(set, builtIn))
        }
    }

    @Test
    fun secondScreenTurnedOffInSettingsKeepsItOn() {
        val off = neverSet.copy(secondScreen = SecondScreenSettings(enabled = false))
        assertSame(off, withSystemInfoDecided(off, builtIn))
    }

    @Test
    fun anUnsureScreenNotYetSwitchedOnKeepsItOnUntilItIs() {
        assertSame(neverSet, withSystemInfoDecided(neverSet, unsure))
        val on = neverSet.copy(secondScreen = SecondScreenSettings(enabled = true))
        assertEquals(false, withSystemInfoDecided(on, unsure).homeScreen.showSystemInfo)
    }
}

/** The wiring: waits for settings and the screen, writes once, follows a reset, and a person's choice wins. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemInfoWiringTest {
    private val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)

    private class Rig(scope: CoroutineScope, screen: SecondScreen?, initial: PlumSettings?) {
        val screens = MutableStateFlow(screen)
        val settings = MutableStateFlow(initial)
        var writes = 0
        val pending = mutableListOf<(PlumSettings) -> PlumSettings>()

        init {
            decideSystemInfoOnce(scope, screens, settings) { transform -> writes++; pending += transform }
        }

        /** Lands the queued writes on the stored settings, as the settings writer does. */
        fun land() {
            pending.forEach { t -> settings.value = settings.value?.let(t) }
            pending.clear()
        }
    }

    @Test
    fun waitsForSettingsAndScreenThenWritesExactlyOnce() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(backgroundScope, null, null)
        assertEquals(0, rig.writes)
        rig.settings.value = PlumSettings.Defaults
        assertEquals("settings loaded, no screen yet", 0, rig.writes)
        rig.screens.value = builtIn
        assertEquals(1, rig.writes)
        rig.land()
        assertEquals(false, rig.settings.value?.homeScreen?.showSystemInfo)
        rig.screens.value = null
        rig.screens.value = builtIn
        assertEquals("decided: nothing more", 1, rig.writes)
    }

    @Test
    fun aValueThePersonSetsWhileTheWriteIsPendingWins() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(backgroundScope, builtIn, PlumSettings.Defaults)
        assertEquals(1, rig.writes)
        rig.settings.value = PlumSettings.Defaults.copy(homeScreen = HomeScreenSettings(showSystemInfo = true))
        rig.land()
        assertEquals(true, rig.settings.value?.homeScreen?.showSystemInfo)
    }

    @Test
    fun decidesAgainAfterAResetWithoutARestart() = runTest(UnconfinedTestDispatcher()) {
        val rig = Rig(backgroundScope, builtIn, PlumSettings.Defaults)
        rig.land()
        assertEquals(false, rig.settings.value?.homeScreen?.showSystemInfo)
        rig.settings.value = PlumSettings.Defaults.copy(secondScreen = SecondScreenSettings(enabled = true))
        assertEquals(2, rig.writes)
        rig.land()
        assertEquals(false, rig.settings.value?.homeScreen?.showSystemInfo)
    }
}
