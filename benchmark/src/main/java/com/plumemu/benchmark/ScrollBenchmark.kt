package com.plumemu.benchmark

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
 * Scrolling the games list with the 10,000-game library: D-pad down held for 10 s, and touch flings. Frame timing per iteration.
 *
 *     ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.plumemu.benchmark.ScrollBenchmark
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun holdDpadDownTenSeconds() =
        measureScroll {
            holdDpadDown(HOLD_MS)
        }

    @Test
    fun flingTheList() =
        measureScroll {
            flingList(FLINGS)
        }

    private fun measureScroll(scroll: MacrobenchmarkScope.() -> Unit) =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.DEFAULT,
            setupBlock = {
                startHome(largeLibrary = true)
                openFirstSystemGames()
            },
        ) {
            scroll()
        }

    private companion object {
        const val ITERATIONS = 5
        const val HOLD_MS = 10_000L
        const val FLINGS = 8
    }
}
