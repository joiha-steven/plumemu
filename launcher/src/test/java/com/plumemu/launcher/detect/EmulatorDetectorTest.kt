package com.plumemu.launcher.detect

import androidx.test.core.app.ApplicationProvider
import com.plumemu.launcher.AndroidPackageLookupFactory
import com.plumemu.launcher.FakePackages
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.TestCatalogs
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class EmulatorDetectorTest {
    private val log = RecordingLog()
    private val detector = EmulatorDetector(AndroidPackageLookupFactory.create(), log, UnconfinedTestDispatcher())

    @Test
    fun nothingInstalledFindsNothing() = runTest {
        assertNull(detector.installed.value)
        assertEquals(InstalledEmulators.NONE, detector.detect(TestCatalogs.shipped))
        assertEquals(InstalledEmulators.NONE, detector.installed.value)
    }

    @Test
    fun installedEmulatorsAndTheirVariantsAreFound() = runTest {
        FakePackages.install("com.retroarch.ra32", "com.retroarch.browser.retroactivity.RetroActivityFuture")
        FakePackages.install("com.github.stenzek.duckstation", "com.github.stenzek.duckstation.EmulationActivity")
        FakePackages.install("com.example.notanemulator", "com.example.Main")
        val found = detector.detect(TestCatalogs.shipped)
        assertEquals(setOf("com.retroarch.ra32", "com.github.stenzek.duckstation"), found.packages)
        assertTrue(found.emulatorIds.containsAll(listOf("duckstation", "retroarch-snes9x", "retroarch-bsnes")))
        assertFalse(found.emulatorIds.contains("ppsspp"))
        assertEquals(found, detector.installed.value)
        assertTrue(log.lines.single().contains("2 installed"))
    }

    @Test
    fun aPackageWhoseActivityIsGoneDoesNotCount() = runTest {
        // An update renamed the activity the platform data names: starting it would fail.
        FakePackages.install("com.github.stenzek.duckstation", "com.github.stenzek.duckstation.OtherActivity")
        assertFalse(detector.detect(TestCatalogs.shipped).has("com.github.stenzek.duckstation"))
        // Said in the log, with the package and the activity tried, so "installed but not found" has a cause.
        val warning = log.lines.single { it.contains("does not resolve") }
        assertTrue(warning, warning.contains("com.github.stenzek.duckstation"))
        assertTrue(warning, warning.contains("com.github.stenzek.duckstation.EmulationActivity"))
        // Once per package and process, not on every detection.
        detector.detect(TestCatalogs.shipped)
        assertEquals(1, log.lines.count { it.contains("does not resolve") })
    }

    @Test
    fun anOlderBuildsActivityMissingIsNotWarnedWhenTheCurrentBuildResolves() = runTest {
        // Flycast's data lists com.flycast.emulator.MainActivity and, from ES-DE, the older
        // com.reicast.emulator.MainActivity under the same package. Today's Flycast has only the first:
        // it is found, and the old name not resolving is no cause for a warning.
        FakePackages.install("com.flycast.emulator", "com.flycast.emulator.MainActivity")
        val found = detector.detect(TestCatalogs.shipped)
        assertTrue(found.emulatorIds.contains("flycast"))
        assertTrue(found.has("com.flycast.emulator"))
        assertEquals(emptyList<String>(), log.lines.filter { it.contains("does not resolve") })
    }

    @Test
    fun anAppWhosePackageNameAnEmulatorBuildBorrowsIsNotThatEmulator() = runTest {
        // ES-DE lists Eden's build that installs as com.miHoYo.Yuanshen and GameHub's as com.tencent.ig;
        // the real game of that name lacks the emulator's activity and must not count.
        FakePackages.install("com.miHoYo.Yuanshen", "com.miHoYo.GetMobileInfo.MainActivity")
        FakePackages.install("com.tencent.ig", "com.epicgames.ue4.SplashActivity")
        val found = detector.detect(TestCatalogs.shipped)
        assertEquals(InstalledEmulators.NONE, found)
        assertFalse(found.emulatorIds.contains("eden"))
    }

    @Test
    fun anEmulatorBuildUnderABorrowedPackageNameIsFound() = runTest {
        FakePackages.install("com.miHoYo.Yuanshen", "org.yuzu.yuzu_emu.activities.EmulationActivity")
        assertTrue(detector.detect(TestCatalogs.shipped).emulatorIds.contains("eden"))
    }

    @Test
    fun anUninstallShowsOnTheNextDetection() = runTest {
        FakePackages.install("org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity")
        assertTrue(detector.detect(TestCatalogs.shipped).has("org.ppsspp.ppsspp"))
        FakePackages.uninstall("org.ppsspp.ppsspp")
        assertFalse(detector.detect(TestCatalogs.shipped).has("org.ppsspp.ppsspp"))
    }
}
