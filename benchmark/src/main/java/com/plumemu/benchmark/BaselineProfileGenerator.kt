package com.plumemu.benchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile on an emulator or device with Android 9 or newer (or rooted):
 * startup and list scrolling (also the startup profile), then Settings, the game menu and search
 *, then L1/R1 list swaps, the game panel's emulator choice and search opening and
 * closing, then the cover grid (walk, size steps, system switches). Setup cannot be walked here: benchmark builds always show the stand-in content.
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

    /** The cover grid: a walk down and up, size steps (L1/R1) and system switches (R2/L2), with covers landing. */
    @Test
    fun coverGridSizesAndSwitches() =
        rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = false) {
            startHome(covers = true)
            openGridOnDs()
            repeat(GRID_STEPS) { device.pressDPadDown() }
            repeat(GRID_STEPS) { device.pressDPadUp() }
            listOf(
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_R2,
                KeyEvent.KEYCODE_BUTTON_R2,
                KeyEvent.KEYCODE_BUTTON_L2,
                KeyEvent.KEYCODE_BUTTON_L2,
            ).forEach {
                device.pressKeyCode(it)
                SystemClock.sleep(GRID_PAUSE_MS)
            }
        }

    private companion object {
        const val HOLD_MS = 3_000L
        const val FLINGS = 3
        const val GRID_STEPS = 6
        const val GRID_PAUSE_MS = 600L
    }
}
