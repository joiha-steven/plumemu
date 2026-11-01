package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.MANIFEST_URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
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
 * "What's new" after Plumemu updated itself: the release that is now installed keeps its notes until the card was
 * shown, the card is counted once per version, and all of it survives a restart (the record file).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdatesWhatsNewTest {
    @get:Rule val folder = TemporaryFolder()

    private val file by lazy { File(folder.root, "update-record.json") }
    private val found =
        UpdateManifest(
            41, "0.0.3-alpha", UpdateFixtures.apkUrl, 1234, "ab".repeat(32), 23, "2026-10-23", listOf("One."), null,
            listOf(NoteSection(SectionKind.New, listOf("A."))),
        )

    /** A fresh process: the coordinator over the record file, as installed [installedCode]. */
    private fun TestScope.process(
        installedCode: Long,
        io: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        store: UpdateStore = FileUpdateStore(file) {},
    ): Updates {
        return Updates(
            scope = this,
            io = io,
            checkDaily = { false },
            network = { NetworkKind.None },
            http = FakeUpdateHttp(),
            manifestUrl = MANIFEST_URL,
            installedVersionCode = { installedCode },
            deviceSdk = 23,
            store = store,
            downloader = ApkDownloader(FakeUpdateHttp(), File(folder.root, "updates"), io),
            inspector = FakeInspector(),
            installer = FakeInstaller(),
            wallClock = { 1_800_000_000_000L },
            info = {},
            log = {},
        )
    }

    private fun writeRecord(record: UpdateRecord) = FileUpdateStore(file) {}.write(record)

    @Test
    fun theReleaseNowInstalledKeepsItsNotesForTheCardAndTheUpdateIsForgotten() =
        runTest {
            writeRecord(UpdateRecord(found = found))
            val updates = process(installedCode = 41)
            updates.onFront().join()
            assertEquals(found, updates.whatsNew.value)
            assertNull(updates.view.value.found)
            assertNull(updates.foundVersionName.value)
        }

    @Test
    fun theNotesSurviveARestartUntilTheCardWasShown() =
        runTest {
            writeRecord(UpdateRecord(found = found))
            process(41).onFront().join()
            // Process death before the card: the next start still has the notes.
            val again = process(41)
            again.onFront().join()
            assertEquals(found, again.whatsNew.value)
        }

    @Test
    fun theCardIsCountedOnceAndNeverReturnsAfterARestart() =
        runTest {
            writeRecord(UpdateRecord(found = found))
            val updates = process(41)
            updates.onFront().join()
            updates.whatsNewShown(41).join()
            assertNull(updates.whatsNew.value)
            val stored = FileUpdateStore(file) {}.read()
            assertNull(stored.whatsNew)
            assertEquals(41L, stored.whatsNewShownCode)
            val again = process(41)
            again.onFront().join()
            assertNull(again.whatsNew.value)
        }

    @Test
    fun anAppInstalledByOtherMeansHasNoNotesAndNoCard() =
        runTest {
            val updates = process(41)
            updates.onFront().join()
            assertNull(updates.whatsNew.value)
            assertTrue(FileUpdateStore(file) {}.read().whatsNew == null)
        }

    @Test
    fun notesOfAnOlderReleaseAreDroppedWhenAnotherVersionIsInstalled() =
        runTest {
            writeRecord(UpdateRecord(whatsNew = found))
            val updates = process(installedCode = 50)
            updates.onFront().join()
            assertNull(updates.whatsNew.value)
        }

    @Test
    fun aReleaseStillWaitingIsNotAWhatsNew() =
        runTest {
            writeRecord(UpdateRecord(found = found))
            val updates = process(installedCode = 40)
            updates.onFront().join()
            assertNull(updates.whatsNew.value)
            assertEquals("0.0.3-alpha", updates.foundVersionName.value)
        }

    @Test
    fun theRecordRoundTripsTheCardFields() {
        val record = UpdateRecord(found = null, whatsNew = found, whatsNewShownCode = 40)
        writeRecord(record)
        assertEquals(record, FileUpdateStore(file) {}.read())
    }

    @Test
    fun aRecordWithoutTheFieldsLoadsWithNone() {
        writeRecord(UpdateRecord(found = found))
        val read = FileUpdateStore(file) {}.read()
        assertNull(read.whatsNew)
        assertEquals(0L, read.whatsNewShownCode)
    }

    /**
     * The first read at start is slow (it holds the old record, notes still in it) while the card's report finishes. The
     * late call must not save its stale copy over the report, or the card would come again at the next start.
     */
    @Test
    fun aSlowFirstReadDoesNotBringTheNotesBackAfterTheCardWasShown() =
        runTest {
            writeRecord(UpdateRecord(found = found))
            val readStarted = CountDownLatch(1)
            val release = CountDownLatch(1)
            val reads = AtomicInteger()
            val real = FileUpdateStore(file) {}
            val slowFirst =
                object : UpdateStore {
                    override fun read(): UpdateRecord {
                        val old = real.read()
                        if (reads.getAndIncrement() == 0) {
                            readStarted.countDown()
                            release.await(10, TimeUnit.SECONDS)
                        }
                        return old
                    }

                    override fun write(record: UpdateRecord) = real.write(record)
                }
            val updates = process(41, io = Dispatchers.IO, store = slowFirst)
            val late = updates.onFront()
            withContext(Dispatchers.IO) { readStarted.await(10, TimeUnit.SECONDS) }
            updates.whatsNewShown(41).join()
            release.countDown()
            late.join()
            val stored = real.read()
            assertNull(stored.whatsNew)
            assertEquals(41L, stored.whatsNewShownCode)
        }
}
