package com.plumemu.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile on an emulator or device with Android 9 or newer (or rooted):
 * startup and list scrolling (also the startup profile), then Settings, the game menu and search
 *, then L1/R1 list swaps, the game panel's emulator choice and search opening and
 * closing. Setup cannot be walked here: benchmark builds always show the stand-in content.
 *
 *     ./gradlew :app:generateBaselineProfile
 *
 * The profile is written to app/src/main/generated/baselineProfiles/ and committed.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndListScrolling() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
            startHome(largeLibrary = true)
            openFirstSystemGames()
            holdDpadDown(HOLD_MS)
            flingList(FLINGS)
        }

    @Test
    fun settingsGameMenuAndSearch() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = false) {
            startHome(largeLibrary = true)
            walkSettings()
            openFirstSystemGames()
            openAndCloseGameMenu()
            typeASearch()
        }

    /** The motion of the main screen: list swaps, the game panel's sub-screens, search. */
    @Test
    fun systemSwitchPanelsAndSearch() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = false) {
            startHome(largeLibrary = true)
            openFirstSystemGames()
            switchSystems()
            openGameMenuAndEmulatorChoice()
            openAndCloseSearch()
        }

    private companion object {
        const val HOLD_MS = 3_000L
        const val FLINGS = 3
    }
}
