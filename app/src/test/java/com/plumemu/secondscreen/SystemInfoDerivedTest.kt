package com.plumemu.secondscreen

import com.plumemu.coredata.settings.HomeScreenSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home system info band is decided by the device, not stored: shown on a single screen, off while a second
 * screen is in use (found and switched on). A stored choice from an older version is not read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemInfoDerivedTest {
    private val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
    private val unsure = SecondScreen(2, "Overlay", ScreenKind.Unsure)

    private fun inUse(
        screen: SecondScreen?,
        settings: PlumSettings,
    ): Boolean = isSecondScreenOn(settings.secondScreen, screen)

    @Test
    fun aSingleScreenIsNotInUse() {
        assertEquals(false, inUse(null, PlumSettings.Defaults))
        assertEquals("even switched on: nothing to show it on", false, inUse(null, PlumSettings.Defaults.copy(secondScreen = SecondScreenSettings(enabled = true))))
    }

    @Test
    fun aBuiltInScreenIsInUseUntilSwitchedOff() {
        assertEquals(true, inUse(builtIn, PlumSettings.Defaults))
        assertEquals(false, inUse(builtIn, PlumSettings.Defaults.copy(secondScreen = SecondScreenSettings(enabled = false))))
    }

    @Test
    fun anUnsureScreenIsInUseOnlyOnceSwitchedOn() {
        assertEquals(false, inUse(unsure, PlumSettings.Defaults))
        assertEquals(true, inUse(unsure, PlumSettings.Defaults.copy(secondScreen = SecondScreenSettings(enabled = true))))
    }

    @Test
    fun theFlowFollowsTheScreenAndTheSwitchAndNeverWritesAnything() = runTest(UnconfinedTestDispatcher()) {
        val screens = MutableStateFlow<SecondScreen?>(null)
        val settings = MutableStateFlow<PlumSettings?>(null)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { secondScreenInUse(screens, settings).toList(seen) }
        assertEquals("settings not read yet: nothing said", emptyList<Boolean>(), seen)
        settings.value = PlumSettings.Defaults
        screens.value = builtIn
        settings.value = PlumSettings.Defaults.copy(secondScreen = SecondScreenSettings(enabled = false))
        settings.value = PlumSettings.Defaults.copy(secondScreen = SecondScreenSettings(enabled = false), homeScreen = HomeScreenSettings(showApps = false))
        settings.value = PlumSettings.Defaults.copy(secondScreen = SecondScreenSettings(enabled = true))
        screens.value = null
        assertEquals("one answer per change, the same answer not repeated", listOf(false, true, false, true, false), seen)
    }

    @Test
    fun aStoredSystemInfoChoiceChangesNothing() = runTest(UnconfinedTestDispatcher()) {
        val screens = MutableStateFlow<SecondScreen?>(builtIn)
        for (stored in listOf(null, true, false)) {
            val settings = MutableStateFlow<PlumSettings?>(PlumSettings.Defaults.copy(homeScreen = HomeScreenSettings(showSystemInfo = stored)))
            val seen = mutableListOf<Boolean>()
            val job = backgroundScope.launch { secondScreenInUse(screens, settings).toList(seen) }
            assertEquals("stored $stored", listOf(true), seen)
            job.cancel()
        }
    }
}
