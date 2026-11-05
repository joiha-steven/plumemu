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
 * One D-pad press every 250 ms down the games list, with and without box-art facts on every game
 * (a signed-in library), the second screen's window off and on. The "on" run needs the overlay
 * display: `adb shell settings put global overlay_display_devices 1240x1080/369`.
 *
 *     ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.plumemu.benchmark.FocusMoveBenchmark
 */
@RunWith(AndroidJUnit4::class)
class FocusMoveBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun moveEvery250msWithDetails() = measure(secondScreen = false, details = true)

    @Test
    fun moveEvery250msWithDetailsSecondScreen() = measure(secondScreen = true, details = true)

    @Test
    fun moveEvery250msPlain() = measure(secondScreen = false, details = false)

    @OptIn(ExperimentalMetricApi::class)
    private fun measure(
        secondScreen: Boolean,
        details: Boolean,
    ) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics =
            listOf(
                FrameTimingMetric(),
                TraceSectionMetric("Recomposer:recompose", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("AndroidOwner:measureAndLayout", TraceSectionMetric.Mode.Sum),
                TraceSectionMetric("Choreographer#doFrame%", TraceSectionMetric.Mode.Max),
            ),
        iterations = ITERATIONS,
        startupMode = StartupMode.WARM,
        compilationMode = CompilationMode.DEFAULT,
        setupBlock = {
            startHome(largeLibrary = true, secondScreen = secondScreen, details = details)
            openFirstSystemGames()
            device.waitForIdle()
        },
    ) {
        pressDown()
    }

    private fun MacrobenchmarkScope.pressDown() {
        repeat(MOVES) {
            device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN)
            SystemClock.sleep(PAUSE_MS)
        }
    }

    private companion object {
        const val ITERATIONS = 8
        const val MOVES = 40
        const val PAUSE_MS = 250L
    }
}
