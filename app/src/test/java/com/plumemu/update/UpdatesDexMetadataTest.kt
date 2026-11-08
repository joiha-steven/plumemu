package com.plumemu.update

import android.content.pm.PackageInstaller
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.INSTALLED
import com.plumemu.update.UpdateFixtures.MANIFEST_URL
import com.plumemu.update.UpdateFixtures.apkBytes
import com.plumemu.update.UpdateFixtures.apkUrl
import com.plumemu.update.UpdateFixtures.dmP
import com.plumemu.update.UpdateFixtures.dmPUrl
import com.plumemu.update.UpdateFixtures.dmS
import com.plumemu.update.UpdateFixtures.dmSUrl
import com.plumemu.update.UpdateFixtures.manifestJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Dex metadata in the update: the device's `.dm` goes into the APK's session; whatever goes wrong
 * with it, the APK still installs and the person is never told about the `.dm`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdatesDexMetadataTest {
    @get:Rule val folder = TemporaryFolder()

    private val http =
        FakeUpdateHttp().apply {
            texts[MANIFEST_URL] = manifestJson { put("dexMetadata", UpdateFixtures.dexMetadataJson()) }
            files[apkUrl] = apkBytes
            files[dmPUrl] = dmP
            files[dmSUrl] = dmS
        }
    private val installer = FakeInstaller()
    private val logs = mutableListOf<String>()
    private val dir by lazy { File(folder.root, "updates") }

    private suspend fun TestScope.found(sdk: Int): Updates {
        val io = StandardTestDispatcher(testScheduler)
        val updates =
            Updates(
                scope = this,
                io = io,
                checkDaily = { true },
                network = { NetworkKind.Wifi },
                http = http,
                manifestUrl = MANIFEST_URL,
                installedVersionCode = { INSTALLED },
                deviceSdk = sdk,
                store = MemoryUpdateStore(),
                downloader = ApkDownloader(http, dir, io),
                inspector = FakeInspector(),
                installer = installer,
                wallClock = { 1_800_000_000_000L },
                info = {},
                log = { logs += it },
            )
        updates.onFront().join()
        assertTrue(updates.view.value.found != null)
        return updates
    }

    private fun dmRequests() = http.requests.filter { it.endsWith(".dm") }

    @Test
    fun eachDeviceGetsTheDmOfItsApiLevelInTheApksSession() =
        runTest {
            for ((sdk, expected) in listOf(31 to dmS, 34 to dmS, 28 to dmP, 30 to dmP)) {
                installer.committed.clear()
                installer.committedDexMetadata.clear()
                val updates = found(sdk)
                updates.startUpdate()!!.join()
                assertArrayEquals("API $sdk", apkBytes, installer.committed.single().first)
                assertArrayEquals("API $sdk", expected, installer.committedDexMetadata.single())
                assertEquals(UpdateStep.Installing(), updates.view.value.step)
                assertEquals("API $sdk: the .dm is deleted once the session has it", listOf("plumemu-41.apk"), dir.list().orEmpty().toList())
                updates.onInstallStatus(InstallStatus.Success)
                advanceUntilIdle()
            }
        }

    @Test
    fun belowAndroid9NoDmIsAskedForAndTheApkGoesAlone() =
        runTest {
            val updates = found(27)
            updates.startUpdate()!!.join()
            assertEquals(emptyList<String>(), dmRequests())
            assertNull(installer.committedDexMetadata.single())
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun aDmThatFailsToDownloadLeavesTheApkToInstallAlone() =
        runTest {
            http.files[dmSUrl] = dmP // the wrong file: its SHA-256 is not the manifest's
            val updates = found(33)
            updates.startUpdate()!!.join()
            assertEquals(listOf(dmSUrl), dmRequests())
            assertArrayEquals(apkBytes, installer.committed.single().first)
            assertNull(installer.committedDexMetadata.single())
            assertEquals(UpdateStep.Installing(), updates.view.value.step)
            assertTrue(logs.toString(), logs.single().startsWith("Update dex metadata not used: "))
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun aDmThatCannotBeWrittenLeavesTheApkToInstallAlone() =
        runTest {
            installer.dexMetadataWriteFails = true
            val updates = found(33)
            updates.startUpdate()!!.join()
            assertArrayEquals(apkBytes, installer.committed.single().first)
            assertNull(installer.committedDexMetadata.single())
            assertEquals(UpdateStep.Installing(), updates.view.value.step)
            // Nothing kept for a retry: the session held the APK alone.
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
            updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_INVALID_APK"))
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun aSessionRefusedForItsDmIsTriedOnceMoreWithTheApkAlone() =
        runTest {
            val updates = found(33)
            updates.startUpdate()!!.join()
            assertArrayEquals(dmS, installer.committedDexMetadata.single())
            updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_BAD_DEX_METADATA: profile mismatch"))
            advanceUntilIdle()
            assertEquals(2, installer.committed.size)
            assertArrayEquals(apkBytes, installer.committed[1].first)
            assertNull("the retry carries no dm", installer.committedDexMetadata[1])
            assertEquals("the person is not told", UpdateStep.Installing(), updates.view.value.step)
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
            // The retry's own failure is shown, and never tried again.
            updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_INVALID_APK"))
            advanceUntilIdle()
            assertEquals(2, installer.committed.size)
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            assertEquals(1, logs.count { it.contains("installing the APK alone") })
            assertTrue(logs.last(), logs.last().startsWith("Update failed: InstallFailed (installer status ${PackageInstaller.STATUS_FAILURE_INVALID}: INSTALL_FAILED_INVALID_APK"))
        }

    @Test
    fun aFailureADmCannotCauseIsShownWithoutARetry() =
        runTest {
            val updates = found(33)
            updates.startUpdate()!!.join()
            updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun thePersonsNoIsNeverRetried() =
        runTest {
            val updates = found(33)
            updates.startUpdate()!!.join()
            updates.onInstallStatus(InstallStatus.NeedsConfirm { true })
            updates.onInstallStatus(InstallStatus.Aborted)
            updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_INVALID, null))
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
        }
    @Test
    fun androidsAnswerArrivingBeforeTheCommitCallReturnsStillGetsTheRetry() =
        runTest {
            val updates = found(33)
            installer.afterCommit = {
                updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_BAD_DEX_METADATA"))
            }
            updates.startUpdate()!!.join()
            advanceUntilIdle()
            assertEquals(2, installer.committed.size)
            assertArrayEquals(apkBytes, installer.committed[1].first)
            assertNull("the retry carries no dm", installer.committedDexMetadata[1])
            assertEquals("the person is not told", UpdateStep.Installing(), updates.view.value.step)
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
            // Nothing stale is left for a later answer: it is shown, not retried.
            updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_INVALID, "INSTALL_FAILED_INVALID_APK"))
            advanceUntilIdle()
            assertEquals(2, installer.committed.size)
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
        }

    @Test
    fun aCommitThatThrowsLeavesNoRetryBehind() =
        runTest {
            val updates = found(33)
            installer.commitFailure = java.io.IOException("no space")
            updates.startUpdate()!!.join()
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            installer.commitFailure = null
            updates.onInstallStatus(InstallStatus.Failed(PackageInstaller.STATUS_FAILURE_INVALID, "late answer of an older session"))
            advanceUntilIdle()
            assertEquals(0, installer.committed.size)
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            assertTrue(logs.toString(), logs.none { it.contains("installing the APK alone") })
            assertTrue(logs.last(), logs.last().contains("late answer of an older session"))
        }
}
