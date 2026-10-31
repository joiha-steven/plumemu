package com.plumemu.launcher.detect

import com.plumemu.launcher.AndroidPackageLookupFactory
import com.plumemu.launcher.FakePackages
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.TestCatalogs
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Play with… and the no-emulator message read the last detection until a package change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmulatorDetectorCacheTest {
    private val log = RecordingLog()
    private val detector = EmulatorDetector(AndroidPackageLookupFactory.create(), log, UnconfinedTestDispatcher())

    private fun detections() = log.lines.count { it.contains("installed package(s)") }

    @Test
    fun twoListingsWithNoChangeBetweenDetectOnce() = runTest {
        FakePackages.install("com.github.stenzek.duckstation", "com.github.stenzek.duckstation.EmulationActivity")
        val first = detector.current(TestCatalogs.shipped)
        val second = detector.current(TestCatalogs.shipped)
        assertEquals(1, detections())
        assertEquals(first, second)
        assertTrue(second.has("com.github.stenzek.duckstation"))
    }

    @Test
    fun aDetectionMadeElsewhereIsReused() = runTest {
        detector.detect(TestCatalogs.shipped) // after the first frame, or Settings › Detect
        detector.current(TestCatalogs.shipped)
        assertEquals(1, detections())
    }

    @Test
    fun aPackageChangeMakesTheNextListingDetectAgain() = runTest {
        assertFalse(detector.current(TestCatalogs.shipped).has("com.github.stenzek.duckstation"))
        FakePackages.install("com.github.stenzek.duckstation", "com.github.stenzek.duckstation.EmulationActivity")
        assertFalse("no change told yet: the last detection", detector.current(TestCatalogs.shipped).has("com.github.stenzek.duckstation"))
        detector.packagesChanged()
        assertTrue(detector.current(TestCatalogs.shipped).has("com.github.stenzek.duckstation"))
        assertEquals(2, detections())
        detector.current(TestCatalogs.shipped)
        assertEquals("refreshed once, then reused", 2, detections())
    }
}
