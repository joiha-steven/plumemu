package com.plumemu.benchmark

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Changing the focused system on the main screen, and going into a games list and back, with the
 * 10,000-game library (about 555 games a system), one key press at a time as a person does it
 * (target: no dropped frames). On the
 * Thor at 120 Hz these were 16 % janky (D-pad) and 12 % (L1/R1) in build 129, all slow UI thread.
 * Besides frame timing, the trace gives recomposition and layout time and the longest frame; the
 * saved Perfetto traces show what each frame did.
 *
 *     ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.plumemu.benchmark.SystemSwitchBenchmark
 */
@RunWith(AndroidJUnit4::class)
class SystemSwitchBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Up and down the system column; the games preview beside it follows each move. */
    @Test
    fun dpadThroughSystems() =
        measureSwitches(openGames = false) {
            repeat(SYSTEM_MOVES) { press(KeyEvent.KEYCODE_DPAD_DOWN) }
            repeat(SYSTEM_MOVES) { press(KeyEvent.KEYCODE_DPAD_UP) }
        }

    /** R1 and L1 with the games list focused: each system's games fade in. */
    @Test
    fun shouldersThroughSystems() =
        measureSwitches(openGames = true) {
            repeat(SYSTEM_MOVES) { press(KeyEvent.KEYCODE_BUTTON_R1) }
            repeat(SYSTEM_MOVES) { press(KeyEvent.KEYCODE_BUTTON_L1) }
        }

    /**
     * Into a system's games list and back out to the system column (D-pad right, then left), on a
     * system in the middle so the pages on both sides are kept: every row's look changes.
     */
    @Test
    fun enterAndBack() =
        measureSwitches(openGames = false, systemsDown = SYSTEMS_BEFORE_ENTER) {
            repeat(SYSTEM_MOVES) {
                press(KeyEvent.KEYCODE_DPAD_RIGHT, SLIDE_PAUSE_MS)
                press(KeyEvent.KEYCODE_DPAD_LEFT, SLIDE_PAUSE_MS)
            }
        }

    @OptIn(ExperimentalMetricApi::class)
    private fun measureSwitches(
        openGames: Boolean,
        systemsDown: Int = 0,
        switches: MacrobenchmarkScope.() -> Unit,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics =
            listOf(
                FrameTimingMetric(),
                TraceSectionMetric("Recomposer:recompose", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("Recomposer:recompose", TraceSectionMetric.Mode.Max),
                TraceSectionMetric("AndroidOwner:measureAndLayout", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("Choreographer#doFrame%", TraceSectionMetric.Mode.Max),
            ),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            startHome(largeLibrary = true)
            if (openGames) openFirstSystemGames()
            repeat(systemsDown) { device.pressDPadDown() }
            device.waitForIdle()
        },
    ) {
        switches()
    }

    /**
     * One key press, then long enough for the 110 ms glide and fade to finish (single presses, not
     * a held key) and for the frames to stop for over 100 ms, so a trace analysis can tell
     * the press's frame.
     */
    private fun MacrobenchmarkScope.press(
        keyCode: Int,
        pauseMs: Long = PAUSE_MS,
    ) {
        device.pressKeyCode(keyCode)
        SystemClock.sleep(pauseMs)
    }

    private companion object {
        const val ITERATIONS = 5

        /** 16 systems down and back up: a typical library has 16. */
        const val SYSTEM_MOVES = 16
        const val PAUSE_MS = 250L

        /** After Enter or Back: the strip's 180 ms slide, then a pause without frames. */
        const val SLIDE_PAUSE_MS = 400L

        /** From Recent, past Favorites, Apps and two systems: a system with systems on both sides. */
        const val SYSTEMS_BEFORE_ENTER = 5
    }
}
