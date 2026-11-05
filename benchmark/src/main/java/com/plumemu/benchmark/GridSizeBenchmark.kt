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
 * Cover grid of Nintendo DS (88 games, box art served by the stand-in art source): L1 / R1 change
 * the grid size and R2 / L2 switch the system, one press at a time with a pause long enough for the
 * covers to land and the frames to stop. Run by hand on the emulator (benchmark/README.md).
 */
@RunWith(AndroidJUnit4::class)
class GridSizeBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Larger, back, smaller, back: eight size steps in a round of two. */
    @Test
    fun sizeSteps() =
        measure {
            repeat(ROUNDS) {
                press(KeyEvent.KEYCODE_BUTTON_R1)
                press(KeyEvent.KEYCODE_BUTTON_L1)
                press(KeyEvent.KEYCODE_BUTTON_L1)
                press(KeyEvent.KEYCODE_BUTTON_R1)
            }
        }

    /** The next system's grid six times (R2), then back (L2). */
    @Test
    fun systemSwitches() =
        measure {
            repeat(SWITCHES) { press(KeyEvent.KEYCODE_BUTTON_R2) }
            repeat(SWITCHES) { press(KeyEvent.KEYCODE_BUTTON_L2) }
        }

    @OptIn(ExperimentalMetricApi::class)
    private fun measure(steps: MacrobenchmarkScope.() -> Unit) =
        rule.measureRepeated(
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
                startHome(covers = true)
                openGridOnDs()
            },
        ) {
            steps()
        }

    private fun MacrobenchmarkScope.press(keyCode: Int) {
        device.pressKeyCode(keyCode)
        SystemClock.sleep(PAUSE_MS)
    }

    private companion object {
        const val ITERATIONS = 5
        const val ROUNDS = 2
        const val SWITCHES = 6
        const val PAUSE_MS = 1_200L
    }
}
