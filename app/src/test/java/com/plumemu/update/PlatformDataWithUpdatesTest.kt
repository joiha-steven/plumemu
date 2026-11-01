package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.INSTALLED
import com.plumemu.update.UpdateFixtures.apkBytes
import com.plumemu.update.UpdateFixtures.apkUrl
import com.plumemu.update.UpdateFixtures.manifestJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The platform data check inside the update check: it runs on both kinds of check and never changes the APK update's outcome. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlatformDataWithUpdatesTest {
    @get:Rule val folder = TemporaryFolder()

    private var now = 1_800_000_000_000L
    private var network = NetworkKind.Wifi
    private var checkDaily = true
    private val http = FakeUpdateHttp().apply { texts[UpdateFixtures.MANIFEST_URL] = manifestJson(); files[apkUrl] = apkBytes }
    private val updateStore = MemoryUpdateStore()
    private val logs = mutableListOf<String>()
    private val dataStore by lazy { PlatformDataStore(File(folder.root, "platform-data")) { logs += it } }

    private fun TestScope.updates(dataCheck: (suspend (String) -> Unit)?): Updates {
        val io = StandardTestDispatcher(testScheduler)
        val updater =
            PlatformDataUpdater(
                http = http,
                manifestUrl = PlatformDataFixtures.MANIFEST_URL,
                installedVersionCode = { PlatformDataFixtures.INSTALLED },
                bundled = { PlatformDataFixtures.bundled },
                store = dataStore,
                verifier = CertificateSignatureVerifier { listOf(PlatformDataFixtures.appKey.public) },
                io = io,
                info = {},
                log = { logs += it },
            )
        return Updates(
            scope = this,
            io = io,
            checkDaily = { checkDaily },
            network = { network },
            http = http,
            manifestUrl = UpdateFixtures.MANIFEST_URL,
            installedVersionCode = { INSTALLED },
            deviceSdk = 23,
            store = updateStore,
            downloader = ApkDownloader(http, File(folder.root, "updates"), io),
            inspector = FakeInspector(),
            installer = FakeInstaller(),
            wallClock = { now },
            dataCheck = dataCheck ?: updater::check,
            info = {},
            log = { logs += it },
        )
    }

    @Test
    fun theDailyCheckAlsoStoresNewerPlatformData() =
        runTest {
            PlatformDataFixtures.serve(http, PlatformDataFixtures.published())
            val updates = updates(null)
            updates.onFront().join()
            assertEquals(6, dataStore.read()?.dataVersion)
            assertEquals(CheckOutcome.Found, updates.view.value.lastCheck?.outcome)
            assertEquals(emptyList<String>(), logs)
        }

    @Test
    fun theManualCheckAlsoStoresNewerPlatformData() =
        runTest {
            PlatformDataFixtures.serve(http, PlatformDataFixtures.published())
            checkDaily = false
            val updates = updates(null)
            updates.onFront().join() // Plumemu in front; the daily switch is off, so only the manual check asks
            assertEquals(emptyList<String>(), http.requests)
            updates.checkNow().join()
            assertEquals(6, dataStore.read()?.dataVersion)
        }

    @Test
    fun aFailingDataCheckLeavesTheUpdateRecordViewAndGateAsIfItDidNotExist() =
        runTest {
            // The data manifest answers 404; the update manifest is fine.
            val updates = updates(null)
            updates.onFront().join()
            val view = updates.view.value
            assertEquals(CheckOutcome.Found, view.lastCheck?.outcome)
            assertNotNull(view.found)
            assertNotNull(updates.foundVersionName.value)
            assertEquals(false, view.isChecking)
            val record = updateStore.record
            assertEquals(now, record.lastSuccessAt)
            assertNull(record.lastAutoFailureAt)
            assertEquals(1, logs.size)
            assertTrue(logs[0].contains("Platform data check"))
            // Gate: the next front within the day does not ask again.
            http.requests.clear()
            updates.onFront().join()
            assertEquals(emptyList<String>(), http.requests)
        }

    @Test
    fun aDataCheckThatCrashesChangesNothingEither() =
        runTest {
            checkDaily = false
            val updates = updates { throw IllegalStateException("bug") }
            updates.onFront().join()
            updates.checkNow().join()
            assertEquals(CheckOutcome.Found, updates.view.value.lastCheck?.outcome)
            assertEquals(false, updates.view.value.isChecking)
            assertEquals(1, logs.size)
        }

    @Test
    fun noNetworkAsksNeitherManifest() =
        runTest {
            network = NetworkKind.None
            val updates = updates(null)
            updates.checkNow().join()
            assertEquals(emptyList<String>(), http.requests)
        }

    @Test
    fun aFoundUpdateIsOfferedAtOnceWhileTheDataFileIsStillDownloading() =
        runTest {
            PlatformDataFixtures.serve(http, PlatformDataFixtures.published())
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            http.openGate = gate
            val updates = updates(null)
            val front = updates.onFront()
            testScheduler.runCurrent()
            // The data file's download waits at the gate; the update check is over and final.
            val view = updates.view.value
            assertEquals(CheckOutcome.Found, view.lastCheck?.outcome)
            assertEquals(false, view.isChecking)
            assertNotNull(view.found)
            assertNull(dataStore.read())
            // Update is not blocked by it.
            assertNotNull(updates.startUpdate())
            assertTrue(updates.view.value.step is UpdateStep.Downloading)
            gate.complete(Unit)
            front.join()
            testScheduler.advanceUntilIdle()
            assertEquals(6, dataStore.read()?.dataVersion)
        }

    @Test
    fun aManualCheckOnMobileDataSkipsTheDataFileWithOneLogLine() =
        runTest {
            PlatformDataFixtures.serve(http, PlatformDataFixtures.published())
            network = NetworkKind.Metered
            val updates = updates(null)
            updates.checkNow().join()
            assertEquals(CheckOutcome.Found, updates.view.value.lastCheck?.outcome)
            assertEquals(listOf(UpdateFixtures.MANIFEST_URL), http.requests)
            assertNull(dataStore.read())
            assertEquals(1, logs.size)
            assertTrue(logs[0].contains("Wi-Fi"))
        }

    @Test
    fun aManualCheckOnAnUnmeteredNetworkThatIsNotWifiAlsoSkipsIt() =
        runTest {
            PlatformDataFixtures.serve(http, PlatformDataFixtures.published())
            network = NetworkKind.Unmetered
            updates(null).checkNow().join()
            assertNull(dataStore.read())
            assertEquals(1, logs.size)
        }

    @Test
    fun noDataIsRequestedWhenPlumemuLeftTheFrontBeforeTheDataCheckStarts() =
        runTest {
            PlatformDataFixtures.serve(http, PlatformDataFixtures.published())
            val updates = updates(null)
            val front = updates.onFront()
            updates.onStop() // a game started right away
            front.join()
            assertEquals(listOf(UpdateFixtures.MANIFEST_URL), http.requests)
            assertNull(dataStore.read())
            assertEquals(CheckOutcome.Found, updates.view.value.lastCheck?.outcome)
        }

    @Test
    fun aDataDownloadUnderWayIsCancelledWhenPlumemuLeavesTheFrontAndTheCurrentDataStays() =
        runTest {
            dataStore.write(StoredPlatformData(3, "2026-11-03", "ab".repeat(32), PlatformDataFixtures.json))
            PlatformDataFixtures.serve(http, PlatformDataFixtures.published())
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            http.openGate = gate
            val updates = updates(null)
            val front = updates.onFront()
            testScheduler.runCurrent() // the data file waits at the gate
            updates.onStop()
            gate.complete(Unit)
            front.join()
            testScheduler.advanceUntilIdle()
            assertEquals(3, dataStore.read()?.dataVersion)
            assertEquals(emptyList<String>(), logs)
        }
}
