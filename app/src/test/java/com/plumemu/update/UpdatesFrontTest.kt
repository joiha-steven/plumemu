package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.INSTALLED
import com.plumemu.update.UpdateFixtures.MANIFEST_URL
import com.plumemu.update.UpdateFixtures.apkBytes
import com.plumemu.update.UpdateFixtures.apkUrl
import com.plumemu.update.UpdateFixtures.manifestJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Nothing of the update opens over a game, and a press right after Cancel shows at once:
 * Android's confirm screen, the install of a ready update and the reopen after
 * it all follow whether Plumemu is in front. Same fakes as [UpdatesTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdatesFrontTest {
    @get:Rule val folder = TemporaryFolder()

    private val now0 = 1_800_000_000_000L
    private val http = FakeUpdateHttp().apply { texts[MANIFEST_URL] = manifestJson(); files[apkUrl] = apkBytes }
    private val store = MemoryUpdateStore()
    private val installer = FakeInstaller()

    private fun TestScope.updates(): Updates {
        val io = StandardTestDispatcher(testScheduler)
        val dir = File(folder.root, "updates")
        return Updates(
            scope = this,
            io = io,
            checkDaily = { true },
            network = { NetworkKind.Wifi },
            http = http,
            manifestUrl = MANIFEST_URL,
            installedVersionCode = { INSTALLED },
            deviceSdk = 23,
            store = store,
            downloader = ApkDownloader(http, dir, io),
            inspector = FakeInspector(),
            installer = installer,
            wallClock = { now0 },
            info = {},
            log = {},
        )
    }

    private suspend fun TestScope.found(): Updates =
        updates().also {
            it.onFront().join()
            assertTrue(it.view.value.found != null)
        }

    @Test
    fun androidsConfirmScreenWaitsForTheFront() =
        runTest {
            val updates = found()
            updates.startUpdate()!!.join()
            updates.onStop()
            var shown = 0
            updates.onInstallStatus(InstallStatus.NeedsConfirm { shown++; true })
            assertEquals("not over a game", 0, shown)
            updates.onFront().join()
            assertEquals(1, shown)
            assertEquals(UpdateStep.Installing(askedPerson = true), updates.view.value.step)
            updates.onStop()
            updates.onFront().join()
            assertEquals("shown once", 1, shown)
        }

    @Test
    fun aConfirmScreenThatCannotOpenFailsAndForgetsTheReopen() =
        runTest {
            val updates = found()
            updates.startUpdate()!!.join()
            assertTrue(installer.reopenMarker)
            updates.onInstallStatus(InstallStatus.NeedsConfirm { false })
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            assertFalse(installer.reopenMarker)
        }

    @Test
    fun aReadyUpdateIsNotInstalledWhenPlumemuLeftBeforeTheFrontWorkRan() =
        runTest {
            val found = (UpdateManifests.read(manifestJson(), MANIFEST_URL, INSTALLED, 23) as ManifestVerdict.Newer).manifest
            store.record = UpdateRecord(lastSuccessAt = now0, found = found)
            val updates = updates()
            updates.checkNow().join()
            updates.startUpdate()!!.join()
            assertEquals(UpdateStep.ReadyToInstall, updates.view.value.step)
            updates.onFront()
            updates.onStop()
            advanceUntilIdle()
            assertEquals(0, installer.committed.size)
            assertEquals(UpdateStep.ReadyToInstall, updates.view.value.step)
            updates.onFront().join()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun leavingDuringASilentInstallForgetsTheReopen() =
        runTest {
            val updates = found()
            updates.startUpdate()!!.join()
            assertEquals(UpdateStep.Installing(), updates.view.value.step)
            assertTrue(installer.reopenMarker)
            updates.onStop()
            assertFalse(installer.reopenMarker)
        }

    @Test
    fun leavingWhileTheSessionIsWrittenForgetsTheReopen() =
        runTest {
            val updates = found()
            installer.duringCommit = { updates.onStop() }
            updates.startUpdate()!!.join()
            assertEquals(1, installer.committed.size)
            assertFalse(installer.reopenMarker)
        }

    @Test
    fun leavingAfterAndroidsScreenOpenedKeepsTheReopen() =
        runTest {
            val updates = found()
            updates.startUpdate()!!.join()
            updates.onInstallStatus(InstallStatus.NeedsConfirm { true })
            updates.onStop()
            assertTrue("the person is on Android's screen, not in a game", installer.reopenMarker)
        }

    @Test
    fun aPressRightAfterCancelShowsTheDownloadAtOnce() =
        runTest {
            val updates = found()
            http.openGate = CompletableDeferred()
            updates.startUpdate()!!
            testScheduler.runCurrent()
            updates.cancel()
            val steps = mutableListOf<UpdateStep>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { updates.view.collect { steps += it.step } }
            val second = updates.startUpdate()!!
            assertTrue("the press shows something at once: ${updates.view.value.step}", updates.view.value.step is UpdateStep.Downloading)
            testScheduler.runCurrent()
            http.openGate!!.complete(Unit)
            second.join()
            val afterPress = steps.dropWhile { it !is UpdateStep.Downloading }
            assertFalse("the cancelled download never shows Cancelled over the new one: $afterPress", UpdateStep.Cancelled in afterPress)
            assertEquals(1, installer.committed.size)
        }
}
