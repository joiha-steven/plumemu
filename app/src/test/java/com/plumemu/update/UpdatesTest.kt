package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.INSTALLED
import com.plumemu.update.UpdateFixtures.MANIFEST_URL
import com.plumemu.update.UpdateFixtures.apkBytes
import com.plumemu.update.UpdateFixtures.apkUrl
import com.plumemu.update.UpdateFixtures.manifestJson
import com.plumemu.update.UpdateGate.DAY_MS
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * The update coordinator with a fake server, store, inspector and installer: when it checks, what a
 * check stores, and the update from Update to Android's answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdatesTest {
    @get:Rule val folder = TemporaryFolder()

    private val now0 = 1_800_000_000_000L
    private var now = now0
    private var checkDaily = true
    private var network = NetworkKind.Wifi
    private val http = FakeUpdateHttp().apply { texts[MANIFEST_URL] = manifestJson(); files[apkUrl] = apkBytes }
    private val store = MemoryUpdateStore()
    private val inspector = FakeInspector()
    private val installer = FakeInstaller()
    private val logs = mutableListOf<String>()
    private val dir by lazy { File(folder.root, "updates") }

    private fun TestScope.updates(manifestUrl: String = MANIFEST_URL): Updates {
        val io = StandardTestDispatcher(testScheduler)
        return Updates(
            scope = this,
            io = io,
            checkDaily = { checkDaily },
            network = { network },
            http = http,
            manifestUrl = manifestUrl,
            installedVersionCode = { INSTALLED },
            deviceSdk = 23,
            store = store,
            downloader = ApkDownloader(http, dir, io),
            inspector = inspector,
            installer = installer,
            wallClock = { now },
            info = {},
            log = { logs += it },
        )
    }

    private fun manifestRequests() = http.requests.count { it == MANIFEST_URL }

    // When the daily check runs.

    @Test
    fun theFirstFrontChecksAndStoresWhatItFound() =
        runTest {
            val updates = updates()
            updates.onFront().join()
            assertEquals(1, manifestRequests())
            assertEquals(now0, store.record.lastSuccessAt)
            assertEquals(LastCheck(now0, CheckOutcome.Found), store.record.lastCheck)
            assertEquals("0.0.3-alpha", store.record.found?.versionName)
            assertEquals("0.0.3-alpha", updates.view.value.found?.versionName)
            assertTrue(updates.hasUpdate.value)
        }

    @Test
    fun aBuildWithNoManifestAddressNeverAsksAnyone() =
        runTest {
            val updates = updates(manifestUrl = "")
            updates.onFront().join()
            updates.checkNow().join()
            assertTrue("no request of any kind", http.requests.isEmpty())
            assertNull(store.record.lastCheck)
            assertFalse(updates.hasUpdate.value)
            assertFalse(updates.view.value.isChecking)
        }

    @Test
    fun theSwitchOffMeansNoRequest() =
        runTest {
            checkDaily = false
            updates().onFront().join()
            assertEquals(0, manifestRequests())
        }

    @Test
    fun aMeteredNetworkMeansNoDailyRequest() =
        runTest {
            network = NetworkKind.Metered
            updates().onFront().join()
            assertEquals(0, manifestRequests())
        }

    @Test
    fun underADayAfterASuccessNothingIsAsked() =
        runTest {
            store.record = UpdateRecord(lastSuccessAt = now0 - DAY_MS + 60_000)
            updates().onFront().join()
            assertEquals(0, manifestRequests())
            now = now0 + 60_000
            updates().onFront().join()
            assertEquals(1, manifestRequests())
        }

    @Test
    fun aClockMovedBackChecksAgain() =
        runTest {
            store.record = UpdateRecord(lastSuccessAt = now0 + DAY_MS)
            updates().onFront().join()
            assertEquals(1, manifestRequests())
            assertEquals(now0, store.record.lastSuccessAt)
        }

    @Test
    fun theManualCheckIgnoresTheSwitchAndTheDayButNeedsANetwork() =
        runTest {
            checkDaily = false
            network = NetworkKind.Metered
            store.record = UpdateRecord(lastSuccessAt = now0 - 1)
            val updates = updates()
            updates.checkNow().join()
            assertEquals(1, manifestRequests())
            network = NetworkKind.None
            now += 1_000
            updates.checkNow().join()
            assertEquals(1, manifestRequests())
            assertEquals(LastCheck(now, CheckOutcome.Failed(CheckFailure.Offline)), updates.view.value.lastCheck)
        }

    @Test
    fun aManualCheckWithoutANetworkKeepsWhatWasFound() =
        runTest {
            val updates = updates()
            updates.onFront().join()
            network = NetworkKind.None
            updates.checkNow().join()
            assertEquals(CheckOutcome.Failed(CheckFailure.Offline), updates.view.value.lastCheck?.outcome)
            assertEquals("0.0.3-alpha", updates.view.value.found?.versionName)
            assertTrue(updates.hasUpdate.value)
        }

    @Test
    fun twoFrontsAtOnceAskOnce() =
        runTest {
            val updates = updates()
            val a = updates.onFront()
            val b = updates.onFront()
            a.join()
            b.join()
            assertEquals(1, manifestRequests())
        }

    // Only a successful check moves the time.

    @Test
    fun aFailedCheckDoesNotMoveTheTimeAndKeepsWhatWasFound() =
        runTest {
            val earlier = (UpdateManifests.read(manifestJson(), MANIFEST_URL, INSTALLED, 23) as ManifestVerdict.Newer).manifest
            store.record = UpdateRecord(lastSuccessAt = now0 - 2 * DAY_MS, found = earlier)
            http.textFailure = UpdateNetException(NetFailure.Timeout)
            updates().onFront().join()
            assertEquals(now0 - 2 * DAY_MS, store.record.lastSuccessAt)
            assertEquals(LastCheck(now0, CheckOutcome.Failed(CheckFailure.Timeout)), store.record.lastCheck)
            assertEquals(earlier, store.record.found)
        }

    @Test
    fun aBadManifestIsAFailureAndLogged() =
        runTest {
            http.texts[MANIFEST_URL] = manifestJson { put("schema", 9) }
            updates().onFront().join()
            assertNull(store.record.lastSuccessAt)
            assertEquals(CheckOutcome.Failed(CheckFailure.BadManifest), store.record.lastCheck?.outcome)
        }

    @Test
    fun eachNetworkFailureIsStoredAsItsOwnKind() =
        runTest {
            val cases = mapOf(NetFailure.Offline to CheckFailure.Offline, NetFailure.Timeout to CheckFailure.Timeout, NetFailure.Http to CheckFailure.Http)
            for ((net, check) in cases) {
                http.textFailure = UpdateNetException(net, if (net == NetFailure.Http) 500 else 0)
                val updates = updates()
                updates.checkNow().join()
                assertEquals(CheckOutcome.Failed(check), updates.view.value.lastCheck?.outcome)
            }
            assertTrue(logs.all { it.startsWith("Update check") })
        }

    @Test
    fun upToDateMovesTheTimeAndForgetsAnOlderFind() =
        runTest {
            http.texts[MANIFEST_URL] = manifestJson { put("versionCode", INSTALLED) }
            val stale = (UpdateManifests.read(manifestJson(), MANIFEST_URL, INSTALLED, 23) as ManifestVerdict.Newer).manifest
            store.record = UpdateRecord(found = stale)
            val updates = updates()
            updates.checkNow().join()
            assertEquals(now0, store.record.lastSuccessAt)
            assertNull(store.record.found)
            assertFalse(updates.hasUpdate.value)
        }

    @Test
    fun aReleaseForANewerAndroidCountsAsASuccessfulCheck() =
        runTest {
            http.texts[MANIFEST_URL] = manifestJson { put("minSdk", 30) }
            updates().onFront().join()
            assertEquals(now0, store.record.lastSuccessAt)
            assertEquals(CheckOutcome.NeedsNewerAndroid, store.record.lastCheck?.outcome)
            assertNull(store.record.found)
        }

    @Test
    fun aRecordThatCannotBeSavedStillShowsTheCheck() =
        runTest {
            store.failWrites = true
            val updates = updates()
            updates.onFront().join()
            assertEquals(CheckOutcome.Found, updates.view.value.lastCheck?.outcome)
            assertTrue(logs.any { it.startsWith("Update record not saved") })
        }

    @Test
    fun anUpdateAlreadyInstalledIsForgottenAtStart() =
        runTest {
            val found = (UpdateManifests.read(manifestJson { put("versionCode", INSTALLED) }, MANIFEST_URL, INSTALLED - 1, 23) as ManifestVerdict.Newer).manifest
            store.record = UpdateRecord(lastSuccessAt = now0, lastCheck = LastCheck(now0, CheckOutcome.Found), found = found)
            val updates = updates()
            updates.onFront().join()
            assertNull(updates.view.value.found)
            assertEquals(CheckOutcome.UpToDate, updates.view.value.lastCheck?.outcome)
        }

    // The update.

    private suspend fun TestScope.found(): Updates =
        updates().also {
            it.onFront().join()
            assertTrue(it.view.value.found != null)
        }

    @Test
    fun updateDownloadsChecksAndHandsTheFileToAndroidThenDeletesIt() =
        runTest {
            val updates = found()
            updates.startUpdate()!!.join()
            assertEquals(1, installer.committed.size)
            assertArrayEquals(apkBytes, installer.committed.single().first)
            assertEquals(41L, installer.committed.single().second)
            assertEquals(UpdateStep.Installing(), updates.view.value.step)
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun androidsAnswersEndTheUpdate() =
        runTest {
            val updates = found()
            updates.startUpdate()!!.join()
            updates.onInstallStatus(InstallStatus.NeedsConfirm { true })
            assertEquals(UpdateStep.Installing(askedPerson = true), updates.view.value.step)
            updates.onInstallStatus(InstallStatus.Aborted)
            assertEquals(UpdateStep.InstallAborted, updates.view.value.step)
            updates.onInstallStatus(InstallStatus.Failed(1, "INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            updates.onInstallStatus(InstallStatus.Success)
            assertEquals(UpdateStep.Idle, updates.view.value.step)
            advanceUntilIdle()
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun afterAndroidsScreenWithNoAnswerUpdateCanRunAgain() =
        runTest {
            val updates = found()
            updates.startUpdate()!!.join()
            updates.onInstallStatus(InstallStatus.NeedsConfirm { true })
            // Android 6: the person cancelled "Install blocked", no status comes back.
            updates.startUpdate()!!.join()
            assertEquals(2, installer.committed.size)
        }

    @Test
    fun aSessionThatCannotBeWrittenFailsAndDeletesTheFile() =
        runTest {
            installer.commitFailure = IOException("no space")
            val updates = found()
            updates.startUpdate()!!.join()
            assertEquals(UpdateStep.Failed(UpdateFailure.InstallFailed), updates.view.value.step)
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun withoutThePermissionNothingIsDownloadedUntilThePersonComesBackWithIt() =
        runTest {
            installer.canInstall = false
            val updates = found()
            assertNull(updates.startUpdate())
            assertEquals(UpdateStep.NeedsPermission, updates.view.value.step)
            assertTrue(updates.openInstallPermission())
            assertEquals(1, installer.permissionScreens)
            // Back without allowing it: still waiting, nothing downloaded.
            updates.onStop()
            updates.onFront().join()
            assertEquals(UpdateStep.NeedsPermission, updates.view.value.step)
            assertEquals(0, http.requests.count { it == apkUrl })
            // Back with it allowed: the update goes on by itself.
            installer.canInstall = true
            updates.onStop()
            updates.onFront().join()
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun onMobileDataThePersonIsAskedFirst() =
        runTest {
            val updates = found()
            network = NetworkKind.Metered
            assertNull(updates.startUpdate())
            assertEquals(UpdateStep.AskMetered, updates.view.value.step)
            assertEquals(0, http.requests.count { it == apkUrl })
            updates.startUpdate(allowMetered = true)!!.join()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun offlineUpdateSaysSo() =
        runTest {
            val updates = found()
            network = NetworkKind.None
            assertNull(updates.startUpdate())
            assertEquals(UpdateStep.Failed(UpdateFailure.Offline), updates.view.value.step)
        }

    @Test
    fun aTamperedDownloadIsRefusedAndDeleted() =
        runTest {
            val updates = found()
            http.files[apkUrl] = apkBytes.copyOf().also { it[1000] = (it[1000] + 1).toByte() }
            updates.startUpdate()!!.join()
            assertEquals(UpdateStep.Failed(UpdateFailure.HashMismatch), updates.view.value.step)
            assertEquals(0, installer.committed.size)
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun eachApkCheckThatFailsStopsTheUpdateAndDeletesTheFile() =
        runTest {
            val cases =
                mapOf<UpdateFailure, () -> Unit>(
                    UpdateFailure.WrongPackage to { inspector.archive = { UpdateFixtures.goodArchive.copy(packageName = "com.example") } },
                    UpdateFailure.WrongVersion to { inspector.archive = { UpdateFixtures.goodArchive.copy(versionCode = 99) } },
                    UpdateFailure.WrongSigner to { inspector.archive = { UpdateFixtures.goodArchive.copy(signers = setOf("ab".repeat(32))) } },
                    UpdateFailure.NotAnApk to { inspector.archive = { null } },
                )
            val updates = found()
            for ((failure, arrange) in cases) {
                arrange()
                var seen: File? = null
                val inner = inspector.archive
                inspector.archive = { file -> seen = file; inner(file) }
                updates.startUpdate()!!.join()
                assertEquals(UpdateStep.Failed(failure), updates.view.value.step)
                assertFalse("$failure left ${seen?.name}", seen!!.exists())
            }
            assertEquals(0, installer.committed.size)
        }

    @Test
    fun cancelStopsTheDownloadAndSaysSo() =
        runTest {
            val updates = found()
            http.failAfterBytes = Int.MAX_VALUE
            val job = updates.startUpdate()!!
            updates.cancel()
            job.join()
            assertEquals(UpdateStep.Cancelled, updates.view.value.step)
            assertEquals(0, installer.committed.size)
            assertEquals(emptyList<String>(), dir.list().orEmpty().toList())
        }

    @Test
    fun aCheckedUpdateWaitsForTheFrontBeforeAndroidsScreen() =
        runTest {
            val updates = found()
            val job = updates.startUpdate()!!
            updates.onStop()
            job.join()
            assertEquals(UpdateStep.ReadyToInstall, updates.view.value.step)
            assertEquals(0, installer.committed.size)
            updates.onFront().join()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun aSecondUpdatePressWhileOneRunsDoesNothing() =
        runTest {
            val updates = found()
            val first = updates.startUpdate()
            assertNull(updates.startUpdate())
            first!!.join()
            assertEquals(1, installer.committed.size)
        }

    // Further cases.

    @Test
    fun anUnmeteredNetworkThatIsNotWifiDoesNotCheckDaily() =
        runTest {
            network = NetworkKind.Unmetered
            updates().onFront().join()
            assertEquals(0, manifestRequests())
        }

    @Test
    fun aFailingServerIsAskedAgainOnlyAfterSixHours() =
        runTest {
            http.textFailure = UpdateNetException(NetFailure.Http, 502)
            val updates = updates()
            updates.onFront().join()
            assertEquals(1, manifestRequests())
            now += UpdateGate.RETRY_MS - 60_000
            updates.onFront().join()
            assertEquals("not on every return", 1, manifestRequests())
            updates.checkNow().join()
            assertEquals("the person's check is not held back", 2, manifestRequests())
            now += 60_000
            updates.onFront().join()
            assertEquals(3, manifestRequests())
            http.textFailure = null
            now += UpdateGate.RETRY_MS
            updates.onFront().join()
            assertNull("a success clears the wait", store.record.lastAutoFailureAt)
        }

    @Test
    fun anUnexpectedFailureIsNotCalledOfflineAndIsLoggedByClass() =
        runTest {
            http.textCrash = IllegalStateException("boom")
            val updates = updates()
            updates.checkNow().join()
            assertEquals(CheckOutcome.Failed(CheckFailure.Unexpected), updates.view.value.lastCheck?.outcome)
            assertTrue(logs.any { it.contains("IllegalStateException") })
        }

    @Test
    fun beforePlumemuIsReportedInFrontAnUpdateWaitsToInstall() =
        runTest {
            val found = (UpdateManifests.read(manifestJson(), MANIFEST_URL, INSTALLED, 23) as ManifestVerdict.Newer).manifest
            store.record = UpdateRecord(lastSuccessAt = now0, found = found)
            val updates = updates()
            updates.checkNow().join()
            updates.startUpdate()!!.join()
            assertEquals(UpdateStep.ReadyToInstall, updates.view.value.step)
            assertEquals(0, installer.committed.size)
            updates.onFront().join()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun aCheckPressedDuringADownloadDoesNotRunAndTheDownloadGoesOn() =
        runTest {
            val updates = found()
            http.openGate = kotlinx.coroutines.CompletableDeferred()
            val job = updates.startUpdate()!!
            testScheduler.runCurrent()
            assertTrue(updates.view.value.isUpdateRunning)
            updates.checkNow().join()
            assertEquals(1, manifestRequests())
            http.openGate!!.complete(Unit)
            job.join()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun withNoPermissionScreenThePanelSaysWhereToAllowItAndStillGoesOn() =
        runTest {
            installer.canInstall = false
            installer.hasPermissionScreen = false
            val updates = found()
            updates.startUpdate()
            assertFalse(updates.openInstallPermission())
            assertEquals(UpdateStep.NoPermissionScreen, updates.view.value.step)
            installer.canInstall = true
            updates.onStop()
            updates.onFront().join()
            advanceUntilIdle()
            assertEquals(1, installer.committed.size)
        }

    @Test
    fun backWithThePermissionOnMobileDataAsksFirst() =
        runTest {
            installer.canInstall = false
            val updates = found()
            updates.startUpdate()
            assertEquals(UpdateStep.NeedsPermission, updates.view.value.step)
            installer.canInstall = true
            network = NetworkKind.Metered
            updates.onStop()
            updates.onFront().join()
            advanceUntilIdle()
            assertEquals(UpdateStep.AskMetered, updates.view.value.step)
            assertEquals(0, http.requests.count { it == apkUrl })
        }
}
