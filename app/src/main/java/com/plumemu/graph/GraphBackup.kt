package com.plumemu.graph

import androidx.room.RoomDatabase
import com.plumemu.coredata.backup.BackupAccount
import com.plumemu.coredata.backup.BackupContents
import com.plumemu.coredata.backup.BackupDamagedException
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupManifest
import com.plumemu.coredata.backup.BackupPart
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coredata.backup.BackupZip
import com.plumemu.coredata.backup.DatabaseSnapshot
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.screen.SettingsHost
import java.io.File
import java.io.FilterOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where Settings › Advanced › Back up… is. */
internal sealed interface BackupState {
    data object Idle : BackupState

    /**
     * [progress] is null while the settings, the account and the database are read; then media files
     * written of those listed. [isChecking]: the file is written and being read back (BackupZip.verify).
     */
    data class Running(
        val kind: BackupKind,
        val progress: BackupProgress? = null,
        val isChecking: Boolean = false,
    ) : BackupState

    /** Saved as [fileName] (null when the picker's app does not say), [bytes] long; [missingMedia] files were deleted meanwhile. */
    data class Done(
        val kind: BackupKind,
        val fileName: String?,
        val bytes: Long,
        val missingMedia: Int = 0,
    ) : BackupState

    /** The person cancelled; [deleted] says whether the unfinished file could be removed. */
    data class Cancelled(val deleted: Boolean) : BackupState

    /** Something failed (logged); [deleted] says whether the unfinished file could be removed; [reason] picks the line. */
    data class Failed(
        val deleted: Boolean,
        val reason: BackupFailure = BackupFailure.Other,
    ) : BackupState

    /** The process died during the last backup (found at the next start); [fileName] is its torn file. */
    data class Interrupted(
        val deleted: Boolean,
        val fileName: String?,
    ) : BackupState
}

/** Why a backup failed, as far as the person's line differs. */
internal enum class BackupFailure {
    Other,

    /** Written, but it did not read back whole (BackupZip.verify). */
    Damaged,

    /** Written, but the picker's app would not give it back to read, so it could not be checked. */
    NotReadBack,

    /** Not started: Clear box art or the box-art sweep was deleting the pictures (a restore's twin is `RestoreProblem.BoxArtBusy`). */
    BoxArtBusy,
}

/** The picked document could not be opened to read it back; [cause] is the provider's refusal. */
private class NotReadBackException(
    cause: Exception,
) : IOException("the written document could not be read back", cause)

/** The document the person picked in Android's save picker. */
internal interface BackupOutput {
    /** The document's URI, kept in the [BackupMarker] to find it again after a restart. */
    val id: String

    /** Keeps write access across a restart (best effort; false when the provider does not offer it). */
    fun keep(): Boolean

    /** Gives back what [keep] took. */
    fun release()

    /** Throws when the picker's app refuses to write. */
    fun open(): OutputStream

    /**
     * The written document read back, for the check before "Saved". Throws SecurityException or
     * FileNotFoundException when the picker's app will not give it back.
     */
    fun read(): InputStream

    /** Removes the document; false when the picker's app cannot (it says nothing about why). */
    fun delete(): Boolean

    /** The name the picker's app gave it (it may have added "(1)"); null when it does not say. */
    fun name(): String?
}

/**
 * Settings › Advanced › Back up…: one backup at a time, in the process's
 * [scope], so closing Settings does not stop it. The zip is streamed into the picked document
 * ([BackupZip]); the database is copied under Room's write lock ([DatabaseSnapshot]), never on the
 * main thread. Cancel or a failure deletes the unfinished document and says so ([BackupState]); a
 * process death is found at the next start through the [markers] ([recoverInterrupted]).
 * Logs name exception classes and counts only: never the account, a file name or a path.
 */
internal class GraphBackup(
    /** The stored settings, read once; a read error must throw (SettingsRepository.readOnce). */
    private val settings: suspend () -> PlumSettings,
    /** The account with its password decrypted ([GraphScraperAccounts.forBackup]); null when signed out; a read error throws. */
    private val account: suspend () -> BackupAccount?,
    private val database: () -> RoomDatabase,
    private val mediaDir: () -> File,
    /** A private folder for the database copy; emptied after each backup and at an interrupted one's recovery. */
    private val workDir: () -> File,
    private val markers: BackupMarkers,
    /** The document behind a marker's id, to delete an interrupted backup's file. */
    private val outputFor: (String) -> BackupOutput,
    private val appVersionName: String,
    private val appVersionCode: Long,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Told how each backup of [BackupKind] ended (saved, failed, cancelled), for the statistics (`backup_made`). */
    private val onEnded: (BackupKind, BackupState) -> Unit = { _, _ -> },
    private val log: (String) -> Unit,
    /** False while a restore replaces the library (Settings › Advanced › Restore…): no backup starts then. */
    private val mayStart: () -> Boolean = { true },
) {
    private val mutableState = MutableStateFlow<BackupState>(BackupState.Idle)
    val state: StateFlow<BackupState> = mutableState.asStateFlow()

    /** Guards "is one running" between [start] and [recoverInterrupted]: recovery never touches a running backup's marker. */
    private val lock = Any()

    private var job: Job? = null

    @Volatile private var isCancelledByPerson = false

    /** True while Clear box art or the sweep deletes pictures: no backup starts then (see [holdForFileWork]). */
    private var fileWorkHeld = false

    /** Takes the pictures for file work; false when a backup runs. Atomic with [start], which checks it under the same lock. */
    fun holdForFileWork(): Boolean =
        synchronized(lock) {
            if (mutableState.value is BackupState.Running) return false
            fileWorkHeld = true
            true
        }

    fun releaseFileWork() = synchronized(lock) { fileWorkHeld = false }

    /** Starts a [kind] backup into [output]; false (nothing started) while one runs or a restore replaces the library. Main thread. */
    fun start(
        kind: BackupKind,
        output: BackupOutput,
    ): Boolean {
        synchronized(lock) {
            // The state, not the job: the end is set only after the work cleaned up (the database copy,
            // the marker), so a new backup never shares a file with the one before.
            if (mutableState.value is BackupState.Running) return false
            if (fileWorkHeld) {
                // Said to the person: the picked file is removed by the picker, and nothing else would tell why.
                log("Backup not started: box art is being cleared or tidied")
                mutableState.value = BackupState.Failed(deleted = true, reason = BackupFailure.BoxArtBusy)
                return false
            }
            if (!mayStart()) return false
            isCancelledByPerson = false
            mutableState.value = BackupState.Running(kind)
        }
        // Atomic: a Cancel before the work starts still reaches run(), which deletes the document and says so.
        job = scope.launch(io, start = CoroutineStart.ATOMIC) { run(kind, output) }
        return true
    }

    /** Cancel: the zip stops within one 64 KB write and the document is deleted. */
    fun cancel() {
        val running = job?.takeIf { it.isActive } ?: return
        isCancelledByPerson = true
        running.cancel()
    }

    /** The person saw the result and moved on: it is not shown again. A running backup is not touched. */
    fun forgetResult() {
        mutableState.update { if (it is BackupState.Running) it else BackupState.Idle }
    }

    /**
     * At start-up, after the first frame (StartupWork): a marker left by a backup the process did not
     * finish means a torn file, which may hold the password. It is deleted (best effort), the database
     * copy is cleaned, the lasting access released, and the Back up row says so. I/O thread.
     */
    fun recoverInterrupted() {
        // The lock covers only the local part: the provider calls (deleting the file, releasing the access)
        // can take seconds on a cloud provider, and start() takes the lock on the main thread.
        val marker =
            synchronized(lock) {
                if (mutableState.value is BackupState.Running) return
                val marker = markers.read() ?: return
                workDir().deleteRecursively()
                marker
            }
        log("Backup: the last one (${marker.kind.code}) was interrupted; deleting its unfinished file")
        val deleted = cleanUpInterrupted(marker)
        synchronized(lock) {
            // A backup started meanwhile has already cleaned this marker up and written its own (run()).
            if (mutableState.value is BackupState.Running) return
            if (markers.read() == marker) markers.clear()
            mutableState.value = BackupState.Interrupted(deleted, marker.name)
        }
    }

    /** Deletes an interrupted backup's document and releases its access; true when the file is gone. */
    private fun cleanUpInterrupted(marker: BackupMarker): Boolean {
        val output = outputFor(marker.documentId)
        val deleted = deleteQuietly(output)
        releaseQuietly(output)
        return deleted
    }

    private fun releaseQuietly(output: BackupOutput) {
        try {
            output.release()
        } catch (e: Exception) {
            log("Backup: releasing the access threw ${e.javaClass.simpleName}")
        }
    }

    private suspend fun run(
        kind: BackupKind,
        output: BackupOutput,
    ) {
        val snapshotFile = File(workDir(), SNAPSHOT_NAME)
        var rethrow: Throwable? = null
        var hasMarker = false
        val end: BackupState =
            try {
                output.keep()
                val name = output.name()
                // An interrupted backup the start-up check has not reached yet (it runs after the first
                // frame of the home screen): its marker is about to be overwritten, so clean it up now.
                markers.read()?.let { old ->
                    log("Backup: an earlier one (${old.kind.code}) was interrupted; deleting its unfinished file first")
                    cleanUpInterrupted(old)
                }
                markers.write(BackupMarker(output.id, kind, clock(), name))
                hasMarker = true
                val contents = contentsOf(kind, snapshotFile)
                val counted = CountingStream(output.open())
                val written = counted.use { BackupZip.write(contents, it) { progress -> mutableState.value = BackupState.Running(kind, progress) } }
                // Read back as Restore will, entry by entry with its CRC, before it may say Saved: on the
                // Thor (build 380) a backup said Saved with one entry that did not inflate.
                mutableState.value = BackupState.Running(kind, isChecking = true)
                openReadBack(output).use { BackupZip.verify(it, written) }
                // The file is whole: from here no clean-up step may leave a marker that would delete it at the next start.
                markers.clear()
                hasMarker = false
                if (written.mediaMissing > 0) log("Backup: ${written.mediaMissing} box-art files were deleted while it ran")
                BackupState.Done(kind, name, counted.count, written.mediaMissing)
            } catch (e: CancellationException) {
                // Room cancels its own work when the database closes: only the person's Cancel is a cancel.
                rethrow = e
                if (isCancelledByPerson) BackupState.Cancelled(deleted = deleteQuietly(output)) else failed(e, output)
            } catch (e: BackupDamagedException) {
                failed(e, output, BackupFailure.Damaged)
            } catch (e: NotReadBackException) {
                failed(e, output, BackupFailure.NotReadBack)
            } catch (e: Throwable) {
                // An Error too: the row must never stay "Backing up…" for the life of the process.
                if (e !is Exception) rethrow = e
                failed(e, output)
            } finally {
                snapshotFile.delete()
            }
        releaseQuietly(output)
        // Only a marker this backup wrote: a failure to write it must not erase an earlier interrupted one.
        if (hasMarker) markers.clear()
        // Last, after the clean-up: from here a new backup may start.
        mutableState.value = end
        onEnded(kind, end)
        rethrow?.let { throw it }
    }

    private fun failed(
        e: Throwable,
        output: BackupOutput,
        reason: BackupFailure = BackupFailure.Other,
    ): BackupState {
        // The damage's message says which entry and how (no name, no path); other failures by class only.
        log(
            when (reason) {
                BackupFailure.Damaged -> "Backup failed: the file did not read back whole (${e.message})"
                BackupFailure.NotReadBack -> "Backup failed: the file could not be read back to check it (${e.cause?.javaClass?.simpleName})"
                BackupFailure.BoxArtBusy -> "Backup not started: box art is being cleared or tidied"
                BackupFailure.Other -> "Backup failed (${e.javaClass.simpleName})"
            },
        )
        return BackupState.Failed(deleted = deleteQuietly(output), reason = reason)
    }

    /** The written document opened again; a provider's refusal is [NotReadBackException], so the line can say so. */
    private fun openReadBack(output: BackupOutput): InputStream =
        try {
            output.read()
        } catch (e: SecurityException) {
            throw NotReadBackException(e)
        } catch (e: FileNotFoundException) {
            throw NotReadBackException(e)
        }

    private suspend fun contentsOf(
        kind: BackupKind,
        snapshotFile: File,
    ): BackupContents {
        val stored = settings()
        val account = account()
        val isEverything = kind == BackupKind.Everything
        val snapshot = if (isEverything) DatabaseSnapshot.take(database(), snapshotFile, log) else null
        snapshot?.let { log("Backup: the database copy held writers for ${it.lockHeldMillis} ms (${it.file.length() / BYTES_PER_KB} KB)") }
        val root = mediaDir()
        val media = if (isEverything) BackupZip.listMedia(root) else emptyList()
        val includes =
            listOfNotNull(
                BackupPart.Settings,
                BackupPart.Account.takeIf { account != null },
                BackupPart.Database.takeIf { isEverything },
                BackupPart.Media.takeIf { isEverything },
            )
        val manifest =
            BackupManifest(
                kind = kind,
                createdAtMillis = clock(),
                appVersionName = appVersionName,
                appVersionCode = appVersionCode,
                includes = includes,
                databaseSchemaVersion = snapshot?.schemaVersion,
                gameCount = snapshot?.gameCount,
                databaseBytes = snapshot?.file?.length(),
                mediaFileCount = media.size,
                mediaBytes = media.sumOf { it.size },
                mediaRoot = if (isEverything) root.absolutePath else null,
            )
        return BackupContents(manifest, stored, account, snapshot?.file, media)
    }

    private fun deleteQuietly(output: BackupOutput): Boolean {
        val deleted =
            try {
                output.delete()
            } catch (e: Exception) {
                log("Backup: deleting the unfinished file threw ${e.javaClass.simpleName}")
                false
            }
        if (!deleted) log("Backup: the unfinished file could not be deleted; the person is told to delete it")
        return deleted
    }

    /** Counts the bytes that reach the document: the size the done line names. */
    private class CountingStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            out.write(b, off, len)
            count += len
        }
    }

    private companion object {
        const val SNAPSHOT_NAME = "plumemu-snapshot.db"
        const val BYTES_PER_KB = 1024
    }
}

/** What Settings drives of [backup] (Settings › Advanced › Back up…): Cancel and forgetting the result. */
internal fun backupWorkOf(backup: GraphBackup): SettingsHost.BackupWork =
    object : SettingsHost.BackupWork {
        override fun cancel() = backup.cancel()

        override fun forgetResult() = backup.forgetResult()
    }
