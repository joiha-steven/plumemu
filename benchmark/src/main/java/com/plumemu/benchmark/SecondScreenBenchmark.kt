package com.plumemu.benchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the second screen's window costs the top screen: the ScrollBenchmark hold and the SystemSwitchBenchmark D-pad walk, each
 * with the window on and off. Needs a second display: on the emulator,
 * `adb shell settings put global overlay_display_devices 1240x1080/369` first. The overlay is an
 * Unsure screen (off by default), so the "on" runs ask for the window with the benchmark-only extra.
 * Pass: frame time P50 and P90 within 0.5 ms of off, no more janky frames (frameOverrunMs).
 *
 *     ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.plumemu.benchmark.SecondScreenBenchmark
 */
@RunWith(AndroidJUnit4::class)
class SecondScreenBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun holdDpadDownSecondScreenOff() = measure(secondScreen = false, openGames = true) { holdDpadDown(HOLD_MS) }

    @Test
    fun holdDpadDownSecondScreenOn() = measure(secondScreen = true, openGames = true) { holdDpadDown(HOLD_MS) }

    @Test
    fun dpadThroughSystemsSecondScreenOff() = measure(secondScreen = false, openGames = false) { walkSystems() }

    @Test
    fun dpadThroughSystemsSecondScreenOn() = measure(secondScreen = true, openGames = false) { walkSystems() }

    private fun measure(
        secondScreen: Boolean,
        openGames: Boolean,
        moves: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            startHome(largeLibrary = true, secondScreen = secondScreen)
            if (openGames) openFirstSystemGames()
            device.waitForIdle()
        },
    ) {
        moves()
    }

    /** SystemSwitchBenchmark's walk: 16 systems down and back up, one press every 250 ms. */
    private fun MacrobenchmarkScope.walkSystems() {
        repeat(SYSTEM_MOVES) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            SystemClock.sleep(PAUSE_MS)
        }
        repeat(SYSTEM_MOVES) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_UP)
            SystemClock.sleep(PAUSE_MS)
        }
    }

    private companion object {
        /** More than ScrollBenchmark's 5: emulator iterations swing between about 6 and 33 ms at P90 on and off alike. */
        const val ITERATIONS = 10
        const val HOLD_MS = 10_000L
        const val SYSTEM_MOVES = 16
        const val PAUSE_MS = 250L
    }
}
