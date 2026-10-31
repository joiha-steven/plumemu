package com.plumemu.home

import com.plumemu.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The 10,000-game content can be chosen only in the benchmark builds, never in debug or release. */
class BenchmarkContentTest {
    @Test
    fun debugBuildsCannotChooseTheBenchmarkContent() {
        // Unit tests compile against the debug build type, where the flag must be off.
        assertFalse(BuildConfig.BENCHMARK_CONTENT)
    }

    @Test
    fun theExtraIsIgnoredOutsideTheBenchmarkBuild() {
        val content = FakeHomeContent.forLaunch(isBenchmarkBuild = false, wantsLargeLibrary = true)
        assertEquals(FakeHomeContent.board().gameCount, content.gameCount)
    }

    @Test
    fun theBenchmarkBuildShowsTenThousandGamesOnlyWhenAsked() {
        assertEquals(10_000, FakeHomeContent.forLaunch(isBenchmarkBuild = true, wantsLargeLibrary = true).gameCount)
        assertEquals(FakeHomeContent.board().gameCount, FakeHomeContent.forLaunch(isBenchmarkBuild = true, wantsLargeLibrary = false).gameCount)
    }

    @Test
    fun theArtworkContentHasBoxArtAndTheBoardDoesNot() {
        assertEquals(true, FakeHomeContent.withArtwork().hasBoxArt)
        assertEquals(false, FakeHomeContent.board().hasBoxArt)
        assertEquals(false, FakeHomeContent.forLaunch(isBenchmarkBuild = true, wantsLargeLibrary = true).hasBoxArt)
    }
}
