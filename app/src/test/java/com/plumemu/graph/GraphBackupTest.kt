package com.plumemu.graph

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.backup.BackupAccount
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupManifest
import com.plumemu.coredata.backup.SettingsJson
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.testing.ReferenceShots
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Advanced › Back up… on the graph: a real library database in
 * a file, a box-art folder, and the picked document as a plain file. What each kind holds, Cancel,
 * and a failing document, each ending with the right state and no unfinished file left behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scopeJob = SupervisorJob()
    private val logLines = CopyOnWriteArrayList<String>()
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val settings = PlumSettings(advanced = AdvancedSettings(sounds = SoundLevel.Off))
    private val account = BackupAccount("plumtester", "s3cret-pass", isRefused = false)
    private val db: PlumDatabase by lazy {
        Room.databaseBuilder(context, PlumDatabase::class.java, File(tmp.root, "plumemu.db").path)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }
    private val mediaDir: File by lazy { File(tmp.root, "screenscraper-media") }
    private val workDir: File by lazy { File(tmp.root, "work") }

    private val markers: FileBackupMarkers by lazy { FileBackupMarkers(File(tmp.root, "no_backup/backup-in-progress.json")) { logLines += it } }

    /** Documents by id, as the provider would find them again after a restart. */
    private val documents = HashMap<String, FileDocument>()

    private fun backup(
        account: suspend () -> BackupAccount? = { this.account },
        settings: suspend () -> PlumSettings = { this.settings },
        mayStart: () -> Boolean = { true },
    ) = GraphBackup(
            settings = settings,
            account = account,
            database = { db },
            mediaDir = { mediaDir },
            workDir = { workDir },
            markers = markers,
            outputFor = { id -> documents[id] ?: FileDocument(File(id)) },
            appVersionName = "0.0.3-alpha",
            appVersionCode = 31,
            scope = CoroutineScope(Dispatchers.Default + scopeJob + CoroutineExceptionHandler { _, e -> uncaught += e }),
            io = Dispatchers.IO,
            clock = { 1_792_756_800_000L },
            log = { logLines += it },
            mayStart = mayStart,
        )

    @After
    fun tearDown() = runBlocking {
        scopeJob.cancelAndJoin()
        db.close()
    }

    private fun library() = runBlocking {
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(ScanBatch(folder, listOf(SystemEntity("snes", "Super Nintendo")), listOf(game(folder, "Zelda"), game(folder, "Mario"))))
        File(mediaDir, "1234").mkdirs()
        File(mediaDir, "1234/box-front-small-120x168.webp").writeBytes(ByteArray(300_000) { (it % 13).toByte() })
        File(mediaDir, "98").mkdirs()
        File(mediaDir, "98/box-front-small-120x168.webp").writeBytes(ByteArray(40) { 1 })
    }

    private fun game(
        folderId: Long,
        title: String,
    ) = GameEntity(
        folderId = folderId,
        documentId = title,
        parentDocumentId = "root",
        relativePath = title,
        fileName = "$title.sfc",
        sizeBytes = 1,
        modifiedAt = 1,
        title = title,
        sortName = title.lowercase(),
        regions = "",
        discGroup = null,
        discCount = 1,
        systemId = "snes",
        isPrimaryCopy = true,
    )

    /** Waits (bounded) for the backup to end and gives how it ended. */
    private fun ended(backup: GraphBackup): BackupState =
        runBlocking { withTimeout(TIMEOUT_MS) { backup.state.first { it !is BackupState.Running && it != BackupState.Idle } } }

    private fun entries(file: File): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(file.inputStream()).use { zip -> generateSequence { zip.nextEntry }.forEach { out[it.name] = zip.readBytes() } }
        return out
    }

    @Test
    fun everythingHoldsADatabaseThatOpensWithItsRowsTheBoxArtAndTheAccount() {
        library()
        val document = FileDocument(File(tmp.root, "Plumemu backup 2026-10-23.zip"))
        val backup = backup()
        assertTrue(backup.start(BackupKind.Everything, document))
        val done = ended(backup) as BackupState.Done
        assertEquals("Plumemu backup 2026-10-23.zip", done.fileName)
        assertEquals("the size is what reached the document", document.file.length(), done.bytes)

        val zip = entries(document.file)
        assertEquals("manifest.json", zip.keys.first())
        val manifest = BackupManifest.fromJson(zip.getValue("manifest.json").decodeToString())
        assertEquals(BackupKind.Everything, manifest.kind)
        assertEquals("the schema since Skipped sub-folders", 13, manifest.databaseSchemaVersion)
        assertEquals(2, manifest.gameCount)
        assertEquals(2, manifest.mediaFileCount)
        assertEquals(300_040L, manifest.mediaBytes)
        assertEquals(mediaDir.absolutePath, manifest.mediaRoot)
        assertEquals("0.0.3-alpha", manifest.appVersionName)
        assertEquals(settings, SettingsJson.decode(zip.getValue("settings.json").decodeToString()).settings)
        assertEquals(account, BackupAccount.fromJson(zip.getValue("account.json").decodeToString()))
        val restored = File(tmp.root, "restored.db").apply { writeBytes(zip.getValue("database/plumemu.db")) }
        val opened = Room.databaseBuilder(context, PlumDatabase::class.java, restored.path).allowMainThreadQueries().build()
        try {
            assertEquals(listOf("Mario", "Zelda"), opened.query("SELECT title FROM game ORDER BY title", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } })
        } finally {
            opened.close()
        }
        assertEquals(300_000, zip.getValue("media/1234/box-front-small-120x168.webp").size)
        assertTrue(zip.containsKey("media/98/box-front-small-120x168.webp"))
        assertFalse("the database copy is removed", File(workDir, "plumemu-snapshot.db").exists())
        assertEquals("the file was read back before Saved", 1, document.reads)
        assertFalse("nothing logs the password", logLines.any { it.contains("s3cret") || it.contains("plumtester") })
    }

    /** Restore…: while a restore replaces the library, no backup starts. */
    @Test
    fun noBackupStartsWhileARestoreReplacesTheLibrary() {
        val backup = backup(mayStart = { false })
        assertFalse(backup.start(BackupKind.Everything, FileDocument(File(tmp.root, "backup.zip"))))
        assertEquals(BackupState.Idle, backup.state.value)
        assertFalse("nothing was written", markers.read() != null)
    }

    @Test
    fun noBackupStartsWhileBoxArtIsClearedAndNoClearStartsWhileABackupRuns() {
        library()
        val backup = backup()
        assertTrue(backup.holdForFileWork())
        assertFalse(backup.start(BackupKind.Everything, FileDocument(File(tmp.root, "held.zip"))))
        assertEquals("the person is told why nothing happened", BackupState.Failed(deleted = true, reason = BackupFailure.BoxArtBusy), backup.state.value)
        backup.releaseFileWork()
        val document = FileDocument(File(tmp.root, "free.zip"))
        assertTrue(backup.start(BackupKind.Everything, document))
        assertFalse("a running backup is not shared", backup.holdForFileWork())
        ended(backup)
    }

    @Test
    fun settingsHoldNoDatabaseAndNoBoxArt() {
        library()
        val document = FileDocument(File(tmp.root, "Plumemu settings 2026-10-23.zip"))
        val backup = backup()
        backup.start(BackupKind.Settings, document)
        assertTrue(ended(backup) is BackupState.Done)
        assertEquals(setOf("manifest.json", "settings.json", "account.json"), entries(document.file).keys)
    }

    @Test
    fun signedOutThereIsNoAccountEntry() {
        val document = FileDocument(File(tmp.root, "settings.zip"))
        val backup = backup(account = { null })
        backup.start(BackupKind.Settings, document)
        assertTrue(ended(backup) is BackupState.Done)
        assertEquals(setOf("manifest.json", "settings.json"), entries(document.file).keys)
    }

    @Test
    fun cancelStopsTheBackupAndDeletesTheUnfinishedFile() {
        library()
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        val document = FileDocument(File(tmp.root, "backup.zip"), onFirstWrite = { reached.countDown(); release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS) })
        val backup = backup()
        backup.start(BackupKind.Everything, document)
        assertTrue("the zip started writing", reached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        backup.cancel()
        release.countDown()
        assertEquals(BackupState.Cancelled(deleted = true), ended(backup))
        assertFalse("the unfinished file is gone", document.file.exists())
        assertFalse(File(workDir, "plumemu-snapshot.db").exists())
    }

    @Test
    fun aDocumentThatFailsToWriteEndsAsFailedAndIsDeleted() {
        library()
        val document = FileDocument(File(tmp.root, "backup.zip"), failAfterBytes = 1_000)
        val backup = backup()
        backup.start(BackupKind.Everything, document)
        assertEquals(BackupState.Failed(deleted = true), ended(backup))
        assertFalse(document.file.exists())
        assertTrue("the log names the failure by class", logLines.any { it == "Backup failed (IOException)" })
    }

    @Test
    fun aDocumentThatCannotBeDeletedSaysSo() {
        val document = FileDocument(File(tmp.root, "backup.zip"), failAfterBytes = 0, canDelete = false)
        val backup = backup()
        backup.start(BackupKind.Settings, document)
        assertEquals(BackupState.Failed(deleted = false), ended(backup))
    }

    /**
     * A file that does not read back whole (the Thor's backup of build 380 had one entry that did not
     * inflate) never says Saved: it fails as damaged, is deleted, and its marker goes.
     */
    @Test
    fun aBackupThatDoesNotReadBackWholeEndsDamagedAndIsDeleted() {
        library()
        val document = FileDocument(File(tmp.root, "backup.zip"), damageOnRead = true)
        val backup = backup()
        backup.start(BackupKind.Everything, document)
        assertEquals(BackupState.Failed(deleted = true, reason = BackupFailure.Damaged), ended(backup))
        assertFalse("the damaged file is gone", document.file.exists())
        assertEquals(1, document.reads)
        assertEquals(null, markers.read())
        assertEquals("the lasting access is given back", 1, document.released)
        assertTrue(logLines.toString(), logLines.any { it.startsWith("Backup failed: the file did not read back whole (entry ") })
    }

    @Test
    fun aDamagedBackupThatCannotBeDeletedSaysSo() {
        val document = FileDocument(File(tmp.root, "backup.zip"), canDelete = false, damageOnRead = true)
        val backup = backup()
        backup.start(BackupKind.Settings, document)
        assertEquals(BackupState.Failed(deleted = false, reason = BackupFailure.Damaged), ended(backup))
    }

    /**
     * A document the picker's app will not give back to read (it refuses, or says it is not there)
     * cannot be checked: it is not Saved, it is deleted, and the line says why.
     */
    @Test
    fun aDocumentThePickersAppWillNotGiveBackFailsAsNotReadBackAndIsDeleted() {
        for (refusal in listOf(SecurityException("no read grant"), java.io.FileNotFoundException("gone"))) {
            logLines.clear()
            val document = FileDocument(File(tmp.root, "backup.zip"), readRefusal = refusal)
            val backup = backup()
            backup.start(BackupKind.Settings, document)
            assertEquals(BackupState.Failed(deleted = true, reason = BackupFailure.NotReadBack), ended(backup))
            assertFalse(document.file.exists())
            val name = refusal.javaClass.simpleName
            assertTrue(logLines.toString(), logLines.any { it == "Backup failed: the file could not be read back to check it ($name)" })
        }
        val kept = FileDocument(File(tmp.root, "kept.zip"), canDelete = false, readRefusal = SecurityException("no read grant"))
        val backup = backup()
        backup.start(BackupKind.Settings, kept)
        assertEquals(BackupState.Failed(deleted = false, reason = BackupFailure.NotReadBack), ended(backup))
    }

    /** A read that breaks once the document is open is an ordinary failure, not "choose another place". */
    @Test
    fun aReadBackThatBreaksMidwayFails() {
        val document = FileDocument(File(tmp.root, "backup.zip"), readBreaks = true)
        val backup = backup()
        backup.start(BackupKind.Settings, document)
        assertEquals(BackupState.Failed(deleted = true), ended(backup))
    }

    /** While the written file is read back the panel says so (not "N of N files"), and Cancel still works then. */
    @Test
    fun theCheckSaysCheckingAndCancelStopsItAndDeletesTheFile() {
        library()
        var seen: BackupState? = null
        lateinit var backup: GraphBackup
        val document =
            FileDocument(File(tmp.root, "backup.zip"), onRead = {
                seen = backup.state.value
                backup.cancel()
            })
        backup = backup()
        backup.start(BackupKind.Everything, document)
        assertEquals(BackupState.Cancelled(deleted = true), ended(backup))
        assertEquals(BackupState.Running(BackupKind.Everything, isChecking = true), seen)
        assertFalse(document.file.exists())
        assertEquals(null, markers.read())
    }

    @Test
    fun aSecondStartWhileOneRunsDoesNothingAndForgetKeepsTheRunningOne() {
        library()
        val release = CountDownLatch(1)
        val reached = CountDownLatch(1)
        val first = FileDocument(File(tmp.root, "first.zip"), onFirstWrite = { reached.countDown(); release.await(TIMEOUT_MS, TimeUnit.MILLISECONDS) })
        val backup = backup()
        backup.start(BackupKind.Everything, first)
        assertTrue(reached.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        assertFalse(backup.start(BackupKind.Settings, FileDocument(File(tmp.root, "second.zip"))))
        backup.forgetResult()
        assertTrue(backup.state.value is BackupState.Running)
        release.countDown()
        assertTrue(ended(backup) is BackupState.Done)
        backup.forgetResult()
        assertEquals(BackupState.Idle, backup.state.value)
    }

    @Test
    fun theMarkerExistsWhileItRunsAndGoesWithTheLastingAccessAtTheEnd() {
        library()
        var markerWhileWriting: BackupMarker? = null
        val document = FileDocument(File(tmp.root, "backup.zip"), onFirstWrite = { markerWhileWriting = markers.read() })
        val backup = backup()
        backup.start(BackupKind.Everything, document)
        assertTrue(ended(backup) is BackupState.Done)
        assertEquals(document.file.path, markerWhileWriting?.documentId)
        assertEquals(BackupKind.Everything, markerWhileWriting?.kind)
        assertEquals("backup.zip", markerWhileWriting?.name)
        assertEquals(null, markers.read())
        assertEquals(1, document.kept)
        assertEquals(1, document.released)
    }

    /** The process died mid-backup: the marker is still there at the next start. */
    private fun interruptedBackupLeftBehind(canDelete: Boolean): FileDocument {
        val torn = File(tmp.root, "Plumemu backup 2026-10-23.zip").apply { writeBytes(ByteArray(4_096) { 1 }) }
        val document = FileDocument(torn, canDelete = canDelete).also { documents[torn.path] = it }
        markers.write(BackupMarker(torn.path, BackupKind.Everything, 1L, torn.name))
        File(workDir, "plumemu-snapshot.db").apply { parentFile?.mkdirs() }.writeBytes(ByteArray(10))
        return document
    }

    @Test
    fun anInterruptedBackupIsDeletedAtTheNextStartAndSaidOnTheRow() {
        val document = interruptedBackupLeftBehind(canDelete = true)
        val backup = backup()
        backup.recoverInterrupted()
        assertFalse("the torn file (maybe holding the password) is gone", document.file.exists())
        assertFalse("the database copy is cleaned", File(workDir, "plumemu-snapshot.db").exists())
        assertEquals("the lasting access is given back", 1, document.released)
        assertEquals(null, markers.read())
        assertEquals(BackupState.Interrupted(deleted = true, fileName = "Plumemu backup 2026-10-23.zip"), backup.state.value)
        backup.recoverInterrupted()
        assertEquals("a second check finds nothing", BackupState.Interrupted(deleted = true, fileName = "Plumemu backup 2026-10-23.zip"), backup.state.value)
    }

    @Test
    fun anInterruptedBackupThatCannotBeDeletedIsNamed() {
        val document = interruptedBackupLeftBehind(canDelete = false)
        val backup = backup()
        backup.recoverInterrupted()
        assertTrue(document.file.exists())
        assertEquals(BackupState.Interrupted(deleted = false, fileName = "Plumemu backup 2026-10-23.zip"), backup.state.value)
        assertEquals(null, markers.read())
    }

    /** The zip is whole before the access is given back: a failing release must not leave a marker that deletes it later. */
    @Test
    fun aWholeBackupWhoseAccessCannotBeReleasedIsKeptAtTheNextStart() {
        library()
        val document = FileDocument(File(tmp.root, "backup.zip").apply { createNewFile() }, releaseThrows = true)
        val backup = backup()
        backup.start(BackupKind.Everything, document)
        assertTrue("ended ${backup.state.value}", ended(backup) is BackupState.Done)
        assertEquals(null, markers.read())
        val nextStart = backup()
        nextStart.recoverInterrupted()
        assertTrue("the good backup is kept", document.file.exists())
        assertEquals(BackupState.Idle, nextStart.state.value)
    }

    /** Back up… reached before the start-up check ran: the earlier torn file is deleted first, not orphaned. */
    @Test
    fun aBackupStartedBeforeTheStartCheckCleansTheInterruptedOneFirst() {
        val torn = interruptedBackupLeftBehind(canDelete = true)
        val document = FileDocument(File(tmp.root, "second.zip").apply { createNewFile() })
        val backup = backup()
        backup.start(BackupKind.Settings, document)
        assertTrue("ended ${backup.state.value}", ended(backup) is BackupState.Done)
        assertFalse("the torn file is gone", torn.file.exists())
        assertEquals(1, torn.released)
        assertTrue(document.file.exists())
        assertEquals(null, markers.read())
    }

    @Test
    fun withoutAMarkerTheStartCheckDoesNothing() {
        val backup = backup()
        backup.recoverInterrupted()
        assertEquals(BackupState.Idle, backup.state.value)
    }

    @Test
    fun aSettingsReadErrorFailsTheBackupInsteadOfSavingDefaults() {
        // The picker has made the empty document before the backup starts.
        val document = FileDocument(File(tmp.root, "backup.zip").apply { createNewFile() })
        val backup = backup(settings = { throw IOException("storage busy") })
        backup.start(BackupKind.Settings, document)
        assertEquals(BackupState.Failed(deleted = true), ended(backup))
        assertFalse(document.file.exists())
    }

    @Test
    fun anAccountReadErrorFailsTheBackupInsteadOfLeavingTheAccountOut() {
        val document = FileDocument(File(tmp.root, "backup.zip").apply { createNewFile() })
        val backup = backup(account = { throw IOException("storage busy") })
        backup.start(BackupKind.Settings, document)
        assertEquals(BackupState.Failed(deleted = true), ended(backup))
        assertFalse(document.file.exists())
    }

    @Test
    fun anErrorEndsTheBackupAsFailedInsteadOfLeavingItRunning() {
        // The picker has made the empty document before the backup starts.
        val document = FileDocument(File(tmp.root, "backup.zip").apply { createNewFile() })
        val backup = backup(settings = { throw OutOfMemoryError("test") })
        backup.start(BackupKind.Settings, document)
        assertEquals(BackupState.Failed(deleted = true), ended(backup))
        assertEquals(null, markers.read())
        runBlocking { withTimeout(TIMEOUT_MS) { scopeJob.children.toList().forEach { it.join() } } }
        assertTrue("the Error still reaches the scope's handler: ${uncaught.toList()}", uncaught.any { it is OutOfMemoryError })
    }

    /**
     * The picked document as a file: it can fail after some bytes, hold its first write, refuse to be
     * deleted, give back a byte changed in the middle when read ([damageOnRead]), or refuse to be read.
     */
    private class FileDocument(
        val file: File,
        private val failAfterBytes: Long? = null,
        private val canDelete: Boolean = true,
        private val onFirstWrite: (() -> Unit)? = null,
        private val releaseThrows: Boolean = false,
        private val damageOnRead: Boolean = false,
        private val readRefusal: Exception? = null,
        private val readBreaks: Boolean = false,
        private val onRead: (() -> Unit)? = null,
    ) : BackupOutput {
        var reads = 0

        override fun read(): InputStream {
            reads++
            onRead?.invoke()
            readRefusal?.let { throw it }
            val bytes = file.readBytes()
            if (damageOnRead) {
                val at = bytes.size / 2
                bytes[at] = (bytes[at].toInt() xor 0x55).toByte()
            }
            if (readBreaks) {
                return object : InputStream() {
                    override fun read(): Int = throw IOException("the provider's stream broke")
                }
            }
            return bytes.inputStream()
        }

        override fun open(): OutputStream {
            val real = FileOutputStream(file)
            return object : OutputStream() {
                var written = 0L
                var first = true

                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {
                    if (first) {
                        first = false
                        onFirstWrite?.invoke()
                    }
                    if (failAfterBytes != null && written + len > failAfterBytes) throw IOException("no space left")
                    real.write(b, off, len)
                    written += len
                }

                override fun close() = real.close()
            }
        }

        override fun delete(): Boolean = canDelete && file.delete()

        override fun name(): String = file.name

        override val id: String get() = file.path

        var kept = 0
        var released = 0

        override fun keep(): Boolean {
            kept++
            return true
        }

        override fun release() {
            released++
            if (releaseThrows) throw IllegalStateException("provider gone")
        }
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
