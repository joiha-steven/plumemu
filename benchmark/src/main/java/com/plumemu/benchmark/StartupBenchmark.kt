package com.plumemu.benchmark

import android.os.Build
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start to the main screen's first frame (target: under 1 s on the Thor, 2.5 s on a weak
 * device), without any ahead-of-time compilation and with the Baseline Profile.
 *
 *     ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.plumemu.benchmark.StartupBenchmark
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartNoCompilation() = coldStart(CompilationMode.None())

    /** Partial compilation (the profile) installs from Android 7; on 6.0 this measures the same as None. */
    @Test
    fun coldStartBaselineProfile() = coldStart(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) CompilationMode.Partial() else CompilationMode.None())

    private fun coldStart(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            compilationMode = mode,
        ) {
            startHome()
        }

    private companion object {
        const val ITERATIONS = 10
    }
}
