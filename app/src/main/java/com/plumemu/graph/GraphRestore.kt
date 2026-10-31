package com.plumemu.graph

import com.plumemu.coredata.account.ScraperAccount
import com.plumemu.coredata.backup.BackupAccount
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupProgress
import com.plumemu.coredata.backup.BackupUnpack
import com.plumemu.coredata.backup.RestoreRefusal
import com.plumemu.coredata.backup.RestoreRefusedException
import com.plumemu.coredata.backup.RestoreSettings
import com.plumemu.coredata.backup.UnpackedBackup
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.settleButtonSet
import java.io.File
import java.io.IOException
import java.io.InputStream
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Settings › Advanced › Restore… and Undo last restore. One at a time, in the process's [scope]:
 *
 * 1. [read]: the picked file is extracted into a private folder and checked ([BackupUnpack]), its
 *    database migrated and its box-art paths moved to this device ([prepareDatabase]), its account
 *    encrypted with this device's key. Nothing on the device changes; the person sees [RestoreState.Confirm].
 * 2. [confirm] (or [undo]): the device's settings and account are saved aside, then for an
 *    Everything file Room is closed and shut, the library moved aside and the file's moved in (all
 *    renames: [RestoreFiles]), then the settings and the account are written. The app then restarts
 *    ([relaunch]) so every part of the graph opens the new database.
 * 3. A failure on the way puts everything back from the aside folder and says so. A process death is
 *    found by the marker: before Room opens ([RestoreFiles.recoverMovingFiles], the database gate) the
 *    files go back, after the first frame ([onStartup]) the swap is finished or the rest put back.
 *
 * Refused while a backup or a scan runs; a box-art run is stopped before the swap. Logs carry steps,
 * counts and exception classes, never the account, a path or a file name.
 */
internal class GraphRestore(
    private val files: RestoreFiles,
    private val appSchemaVersion: Int,
    /** The stored settings, read once; a read error throws (SettingsRepository.readOnce). */
    private val readSettings: suspend () -> PlumSettings,
    /** One settings write (SettingsRepository.update). */
    private val writeSettings: suspend ((PlumSettings) -> PlumSettings) -> Unit,
    private val accounts: RestoreAccounts,
    /** [RestoredDatabase.prepare] for this device's box-art folder: (file, the file's media root, this device's). */
    private val prepareDatabase: (File, String?, String) -> Unit,
    /**
     * Waits (bounded) for the box-art run and any write in flight to stop, then shuts the database
     * gate and closes Room: from then on this process never opens the file again. False when they
     * did not stop in time: nothing was shut, and the restore is refused.
     */
    private val closeDatabase: suspend () -> Boolean,
    private val isBackupRunning: () -> Boolean,
    private val isScanning: () -> Boolean,
    /** Takes the scan slot without scanning; false while a scan runs or waits. */
    private val holdScans: () -> Boolean,
    private val releaseScans: () -> Unit,
    /** Cancels a running or waiting box-art run before the files move. */
    private val stopBoxArt: () -> Unit,
    /** Starts the app again in a new process (the database, the graph, the settings all read anew). */
    private val relaunch: () -> Unit,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Told how each restore or Undo ended, once (also after the restart a library swap needs), for the statistics (`restore_done`). */
    private val onResult: (RestoreResult) -> Unit = {},
    private val freeBytes: (File) -> Long = { it.usableSpace },
    private val log: (String) -> Unit,
) {
    private val places = files.places
    private val mutableState = MutableStateFlow<RestoreState>(RestoreState.Idle)
    val state: StateFlow<RestoreState> = mutableState.asStateFlow()

    private val mutableWayBack = MutableStateFlow<WayBackSet?>(null)

    /** The way-back set; read from storage at [onStartup] and after each restore, Undo or Forget. */
    val wayBack: StateFlow<WayBackSet?> = mutableWayBack.asStateFlow()

    /** True from the press of Restore (or Undo) until the app restarts: a backup must not start then. */
    val isReplacing: Boolean get() = mutableState.value is RestoreState.Replacing

    /** Guards the state's changes between the main thread's calls. */
    private val lock = Any()

    /** One piece of file work at a time: reading, replacing, cancelling, the start-up check. */
    private val work = Mutex()

    private var job: Job? = null

    private var fileWorkHeld = false

    /** Takes the pictures for Clear box art or the sweep; false while a restore replaces (same lock as [confirm]). */
    fun holdForFileWork(): Boolean = synchronized(lock) { (mutableState.value !is RestoreState.Replacing).also { if (it) fileWorkHeld = true } }

    fun releaseFileWork() = synchronized(lock) { fileWorkHeld = false }

    /** Reads and checks [input] (the picker's answer); false while one is read or replaced. Main thread. */
    fun read(input: RestoreInput): Boolean {
        synchronized(lock) {
            val now = mutableState.value
            if (now is RestoreState.Reading || now is RestoreState.Replacing) return false
            val busy = busyProblem()
            if (busy != null) {
                mutableState.value = RestoreState.Refused(busy)
                return true
            }
            mutableState.value = RestoreState.Reading()
        }
        // Atomic: a Cancel before the work starts still reaches readInto, which cleans up.
        job = scope.launch(io, start = CoroutineStart.ATOMIC) { work.withLock { readInto(input) } }
        return true
    }

    /** Cancel while reading (stops within one 64 KB buffer) or at the confirm: the extracted file is deleted. */
    fun cancel() {
        synchronized(lock) {
            when (mutableState.value) {
                is RestoreState.Reading -> {
                    job?.cancel()
                    return
                }
                is RestoreState.Confirm -> mutableState.value = RestoreState.Idle
                else -> return
            }
        }
        scope.launch(io) { work.withLock { discardStagedIfIdle() } }
    }

    /** The person pressed Restore on the confirm panel; false when there is nothing to confirm. Main thread. */
    fun confirm(): Boolean {
        val summary =
            synchronized(lock) {
                val now = mutableState.value as? RestoreState.Confirm ?: return false
                val busy = busyProblem() ?: keptSetProblem(now.summary.kind)
                if (busy != null) {
                    mutableState.value = RestoreState.Refused(busy)
                    null
                } else {
                    mutableState.value = RestoreState.Replacing(isUndo = false, withLibrary = now.summary.kind == BackupKind.Everything)
                    now.summary
                }
            }
        if (summary == null) {
            scope.launch(io) { work.withLock { discardStagedIfIdle() } }
            return true
        }
        scope.launch(io, start = CoroutineStart.ATOMIC) { work.withLock { replace(RestoreOperation.Restore) } }
        return true
    }

    /** Undo last restore, after its panel: the way-back set swaps back the same way. Main thread. */
    fun undo(): Boolean {
        synchronized(lock) {
            val now = mutableState.value
            if (now is RestoreState.Reading || now is RestoreState.Replacing) return false
            val set = mutableWayBack.value ?: return false
            // The file read for a confirm goes: the Undo replaces what the device has now.
            if (now is RestoreState.Confirm) mutableState.value = RestoreState.Idle
            val busy = busyProblem()
            if (busy != null) {
                mutableState.value = RestoreState.Refused(busy)
                return true
            }
            mutableState.value = RestoreState.Replacing(isUndo = true, withLibrary = set.kind == BackupKind.Everything)
        }
        scope.launch(io, start = CoroutineStart.ATOMIC) {
            work.withLock {
                // A file read but not confirmed is dropped: the Undo replaces what the device has now.
                files.discard(places.workDir)
                replace(RestoreOperation.Undo)
            }
        }
        return true
    }

    /** "Forget the copy": the way-back set is deleted and Undo is no longer offered. */
    fun forgetCopy() {
        if (mutableState.value is RestoreState.Replacing) return
        scope.launch(io) {
            work.withLock {
                if (mutableState.value is RestoreState.Replacing) return@withLock
                files.discard(places.wayBackDir)
                refreshWayBack()
                files.emptyDiscard()
                log("Restore: the way-back copy was deleted")
            }
        }
    }

    /** The person saw how the last one ended and moved on. A read, a confirm or a swap going on stays. */
    fun forgetResult() {
        mutableState.update {
            when (it) {
                is RestoreState.Refused, is RestoreState.Done, is RestoreState.Failed, is RestoreState.Interrupted -> RestoreState.Idle
                else -> it
            }
        }
    }

    /**
     * After the first frame (StartupWork): a swap the last process did not finish is finished (its
     * files were in place) or put back (the gate already moved the files back before Room opened);
     * the result of a restore that restarted the app is shown; folders left by a dead read are
     * deleted. True when the startup scan should not run this time: the library was just replaced,
     * and a scan is the person's choice after a restore. I/O thread.
     */
    suspend fun onStartup(): Boolean =
        work.withLock {
            var skipScan = false
            val marker = markerOrInferred()
            if (marker != null) {
                skipScan = marker.kind == BackupKind.Everything
                val isFinishable = marker.step == RestoreStep.Applying && (marker.kind != BackupKind.Everything || files.isPlaced(marker.operation))
                if (isFinishable) finishInterrupted(marker) else putBackInterrupted(marker)
            } else if (mutableState.value == RestoreState.Idle) {
                // A finished restore whose set was not moved into place yet; then what a dead read left.
                files.promoteNewWayBack()
                files.discard(places.workDir)
                files.discard(places.undoAsideDir)
            }
            files.takeResult()?.let { result ->
                onResult(result)
                skipScan = skipScan || result.kind == BackupKind.Everything
                if (mutableState.value == RestoreState.Idle) mutableState.value = stateOf(result)
            }
            refreshWayBack()
            // Its own job: deleting a replaced box-art folder can take long, and the startup scan waits for this answer.
            scope.launch(io) { work.withLock { files.emptyDiscard() } }
            skipScan
        }

    /**
     * A Settings file never replaces a kept Everything set: that set is the
     * person's earlier library. They undo or forget it first; a Settings set may be replaced.
     */
    private fun keptSetProblem(kind: BackupKind): RestoreProblem? =
        RestoreProblem.EarlierRestoreKept.takeIf { kind == BackupKind.Settings && mutableWayBack.value?.kind == BackupKind.Everything }

    private fun busyProblem(): RestoreProblem? =
        when {
            isBackupRunning() -> RestoreProblem.BackupRunning
            fileWorkHeld -> RestoreProblem.BoxArtBusy
            isScanning() -> RestoreProblem.ScanRunning
            else -> null
        }

    /**
     * The marker; when it cannot be read, a stand-in that puts the device back: the gate already put
     * the library in place, and the aside folder with saved settings tells which swap it was. Null
     * when there is no marker, or nothing left to put back (the unreadable marker then goes).
     */
    private fun markerOrInferred(): RestoreMarker? =
        try {
            files.readMarker()
        } catch (e: IOException) {
            val operation = files.operationWithSavedSettings()
            log("Restore: ${e.message}; ${if (operation == null) "nothing to put back" else "putting the device back"}")
            if (operation == null) {
                if (places.databaseFile.exists()) places.markerFile.delete()
                null
            } else {
                RestoreMarker(operation, BackupKind.Everything, RestoreStep.Moving, 0L)
            }
        }

    private fun discardStagedIfIdle() {
        val now = mutableState.value
        if (now is RestoreState.Reading || now is RestoreState.Confirm || now is RestoreState.Replacing) return
        files.discard(places.workDir)
        files.emptyDiscard()
    }

    private suspend fun readInto(input: RestoreInput) {
        var rethrow: Throwable? = null
        val end: RestoreState =
            try {
                files.discard(places.workDir)
                val unpacked =
                    input.open().use { stream ->
                        BackupUnpack.unpack(stream, places.workDir, appSchemaVersion, freeBytes, log) { progress -> mutableState.value = RestoreState.Reading(progress) }
                    }
                stage(unpacked)
            } catch (e: CancellationException) {
                rethrow = e
                RestoreState.Idle
            } catch (e: RestoreRefusedException) {
                // The message names no path and no value (BackupUnpack's own words).
                log("Restore refused (${e.refusal}): ${e.message}")
                RestoreState.Refused(RestoreProblem.of(e.refusal))
            } catch (e: Throwable) {
                if (e !is Exception) rethrow = e
                log("Restore: the file could not be read (${e.javaClass.simpleName})")
                RestoreState.Refused(RestoreProblem.Unreadable)
            }
        if (end !is RestoreState.Confirm) {
            files.discard(places.workDir)
            files.emptyDiscard()
        }
        mutableState.value = end
        rethrow?.let { throw it }
    }

    /** The checked file made ready to swap in: the database migrated and moved to this device's folder, the account encrypted. */
    private suspend fun stage(unpacked: UnpackedBackup): RestoreState {
        val manifest = unpacked.manifest
        unpacked.database?.let { database ->
            try {
                prepareDatabase(database, manifest.mediaRoot, places.mediaDir.absolutePath)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                log("Restore: the file's database could not be migrated (${e.javaClass.simpleName})")
                throw RestoreRefusedException(RestoreRefusal.BadDatabase, "the database could not be made ready", e)
            }
        }
        if (unpacked.unknownSettingKeys.isNotEmpty()) log("Restore: ${unpacked.unknownSettingKeys.size} settings have values this version does not know; they get their defaults")
        val file = unpacked.account
        val (plan, account) =
            when {
                file == null || !accounts.isAvailable -> AccountPlan.Keep to AccountInFile.None
                file.password == null -> AccountPlan.SignOut to AccountInFile.SignInAgain
                else -> AccountPlan.Store(accounts.encryptForRestore(file)) to AccountInFile.SignsIn
            }
        files.writeFolder(places.workDir, RestoreFolder(manifest.kind, manifest.createdAtMillis, SettingsRule.FromBackup, unpacked.settings, plan, unpacked.damagedMediaFiles))
        log("Restore: a ${manifest.kind.code} file was read (${unpacked.mediaFiles} box-art files, ${unpacked.damagedMediaFiles} damaged and left out); waiting for the person")
        return RestoreState.Confirm(RestoreSummary(manifest.kind, manifest.createdAtMillis, manifest.gameCount ?: 0, manifest.mediaFileCount, account, unpacked.damagedMediaFiles))
    }

    /** The swap (class KDoc, step 2). Runs under [work]; ends with a relaunch when the library moved. */
    private suspend fun replace(operation: RestoreOperation) {
        val isUndo = operation == RestoreOperation.Undo
        val folder =
            try {
                files.readFolder(operation.incoming(places))
            } catch (e: IOException) {
                log("Restore: what was to be put in place could not be read (${e.javaClass.simpleName})")
                null
            }
        if (folder == null) {
            if (!isUndo) files.discard(places.workDir)
            mutableState.value = RestoreState.Failed(isUndo, isPutBack = true)
            refreshWayBack()
            return
        }
        val withLibrary = folder.kind == BackupKind.Everything
        val refusal = if (isUndo) null else keptSetProblem(folder.kind)
        if (refusal != null || (withLibrary && !holdScans())) {
            // Refused: the extracted file goes at once (an Undo's incoming folder is the set itself and stays).
            if (!isUndo) files.discard(places.workDir)
            mutableState.value = RestoreState.Refused(refusal ?: RestoreProblem.ScanRunning)
            return
        }
        val aside = operation.aside(places)
        val marker = RestoreMarker(operation, folder.kind, if (withLibrary) RestoreStep.Moving else RestoreStep.Applying, folder.fromMillis)
        // 1. The device's settings and account, kept aside. Nothing has changed yet: a failure just stops here.
        val saved =
            try {
                val settings = readSettings()
                val account = if (accounts.isAvailable) accounts.storedForRestore()?.let { AccountPlan.Store(it) } ?: AccountPlan.SignOut else AccountPlan.Keep
                // A fresh aside folder (a leftover of a swap that died goes). The earlier way-back set is
                // not touched here: it is replaced only when this restore has finished.
                files.discard(aside)
                // An Undo's set was moved aside from a live database: its WAL is folded in first.
                if (isUndo) files.foldIncoming(operation)
                files.writeSettingsAndAccount(aside, settings, account)
                files.writeMarker(marker)
                settings to account
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                log("Restore: the device's settings or account could not be kept (${e.javaClass.simpleName}); nothing was changed")
                files.clearMarker()
                if (withLibrary) releaseScans()
                mutableState.value = RestoreState.Failed(isUndo, isPutBack = true)
                refreshWayBack()
                return
            }
        // 2. The files (an Everything set), then the settings and the account.
        if (withLibrary) {
            guardedStep("stop the box-art run") { stopBoxArt() }
            val isQuiet =
                try {
                    closeDatabase()
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    log("Restore: the library could not be closed (${e.javaClass.simpleName})")
                    false
                }
            if (!isQuiet) {
                // Nothing moved and Room is still open: the restore is refused, the device as it was.
                log("Restore: the library stayed busy; refused")
                files.clearMarker()
                files.discard(aside)
                if (!isUndo) files.discard(places.workDir)
                releaseScans()
                mutableState.value = RestoreState.Refused(RestoreProblem.LibraryBusy)
                refreshWayBack()
                return
            }
        }
        val isClosed = withLibrary
        try {
            if (withLibrary) {
                files.moveAside(operation)
                files.placeIncoming(operation)
                files.writeMarker(marker.copy(step = RestoreStep.Applying))
            }
            apply(folder)
        } catch (e: Throwable) {
            log("Restore: replacing failed (${e.javaClass.simpleName}); putting the device back")
            val isPutBack = putBack(marker, saved)
            if (isPutBack) log("Restore: everything was put back") else log("Restore: not everything could be put back; the next start tries again")
            end(RestoreResult.Failed(folder.kind, isUndo, isPutBack), relaunchAfter = isClosed)
            if (!isClosed && withLibrary) releaseScans()
            if (e !is Exception) throw e
            return
        }
        // 3. Done: the way-back set is complete (a restore), or the restored library goes (an Undo).
        finish(operation, folder)
        log("Restore: ${if (isUndo) "the undo" else "a ${folder.kind.code} restore"} finished")
        end(RestoreResult.Done(folder.kind, folder.fromMillis, isUndo, folder.damagedMediaFiles), relaunchAfter = withLibrary)
    }

    /** Writes [folder]'s settings (by its rule) and its account. */
    private suspend fun apply(folder: RestoreFolder) {
        val withLibrary = folder.kind == BackupKind.Everything
        writeSettings { current ->
            if (folder.rule == SettingsRule.FromBackup) {
                // An older backup has no button set: settle it now, not at the next start.
                val merged = RestoreSettings.merge(folder.settings, current, withLibrary)
                merged.copy(appearance = merged.appearance.settleButtonSet())
            } else {
                folder.settings
            }
        }
        putAccount(folder.account)
    }

    private suspend fun putAccount(plan: AccountPlan) {
        if (plan == AccountPlan.Keep || !accounts.isAvailable) return
        accounts.putForRestore((plan as? AccountPlan.Store)?.account)
    }

    /**
     * Puts the device back after a failure: the files from the aside folder, then the saved
     * settings and account. The marker is moved to "putting back" first, so a death in here, or a
     * file that would not move, is put back at the next start. True when all of it is back.
     */
    private suspend fun putBack(
        marker: RestoreMarker,
        saved: Pair<PlumSettings, AccountPlan>,
    ): Boolean {
        var isWhole = true
        try {
            files.writeMarker(marker.copy(step = RestoreStep.Moving))
        } catch (e: IOException) {
            log("Restore: the marker could not be updated (${e.javaClass.simpleName})")
        }
        if (marker.kind == BackupKind.Everything) {
            isWhole = guardedStep("put the files back") { files.rollBackFiles(marker.operation) } && isWhole
        }
        isWhole = guardedStep("put the settings back") { writeSettings { saved.first } } && isWhole
        isWhole = guardedStep("put the account back") { putAccount(saved.second) } && isWhole
        if (isWhole) cleanUpAfterPutBack(marker.operation)
        return isWhole
    }

    private fun cleanUpAfterPutBack(operation: RestoreOperation) {
        if (operation == RestoreOperation.Restore) {
            // The aside folder was the next way-back set, unfinished; the earlier set stays. The extracted file goes too.
            files.discard(places.newWayBackDir)
            files.discard(places.workDir)
        } else {
            // The set's files are back in it: Undo stays offered.
            files.discard(places.undoAsideDir)
        }
        files.clearMarker()
    }

    /**
     * Ends a swap whose files and settings are in place, in an order a death cannot undo: first
     * what makes it finished (the new set whole; for an Undo, the used set gone), then the marker,
     * then the clean-up. A death before the marker goes finishes again at the next start
     * ([finishInterrupted]); after it, [onStartup] only moves the new set into place.
     */
    private fun finish(
        operation: RestoreOperation,
        folder: RestoreFolder,
    ) {
        if (operation == RestoreOperation.Restore) {
            files.completeFolder(places.newWayBackDir, folder.kind, clock())
            files.clearMarker()
            // Only now does the earlier set go: every failure before kept it.
            files.promoteNewWayBack()
            files.discard(places.workDir)
        } else {
            files.discard(places.wayBackDir)
            files.clearMarker()
            files.discard(places.undoAsideDir)
        }
    }

    /** How it ended: kept for after the restart when the library moved, else shown now. */
    private fun end(
        result: RestoreResult,
        relaunchAfter: Boolean,
    ) {
        if (relaunchAfter) {
            files.writeResult(result)
            log("Restore: restarting the app on the new library")
            relaunch()
            return
        }
        refreshWayBack()
        files.emptyDiscard()
        mutableState.value = stateOf(result)
        onResult(result)
    }

    /**
     * At a start with the marker at "applying" and the files in place (checked by the caller): the
     * settings and account are written again (the same values) and the swap finished. An Undo whose
     * set was already discarded had written them. A failure keeps the marker for the next start and
     * says so; recovery never closes the library or restarts the app (a disk that stays full must
     * not become a restart loop).
     */
    private suspend fun finishInterrupted(marker: RestoreMarker) {
        log("Restore: the last ${marker.operation.code} was interrupted after its files moved; finishing it")
        try {
            val folder = files.readFolder(marker.operation.incoming(places))
            if (folder != null) {
                apply(folder)
                finish(marker.operation, folder)
            } else if (marker.operation == RestoreOperation.Undo) {
                files.clearMarker()
                files.discard(places.undoAsideDir)
            } else {
                throw IOException("what was to be put in place is gone")
            }
            mutableState.value = RestoreState.Interrupted(isFinished = true)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            log("Restore: finishing failed (${e.javaClass.simpleName}); trying again at the next start")
            mutableState.value = RestoreState.Failed(marker.operation == RestoreOperation.Undo, isPutBack = false)
        }
    }

    /** At a start with the marker at "moving": the gate put the files back before Room opened; the settings and account follow. */
    private suspend fun putBackInterrupted(marker: RestoreMarker) {
        log("Restore: the last ${marker.operation.code} was interrupted; putting the device back")
        try {
            // Normally done by the database gate already; again here (it does nothing then) in case the database never opened.
            if (marker.kind == BackupKind.Everything) files.recoverMovingFiles()
            val (settings, account) = files.readSaved(marker.operation.aside(places))
            writeSettings { settings }
            putAccount(account)
            cleanUpAfterPutBack(marker.operation)
            mutableState.value = RestoreState.Interrupted(isFinished = false)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            // The marker stays: the next start tries again, and Room keeps its library meanwhile.
            log("Restore: putting the device back failed (${e.javaClass.simpleName}); trying again at the next start")
            mutableState.value = RestoreState.Failed(marker.operation == RestoreOperation.Undo, isPutBack = false)
        }
    }

    private fun refreshWayBack() {
        val folder =
            try {
                files.readFolder(places.wayBackDir)
            } catch (e: IOException) {
                log("Restore: the way-back set could not be read (${e.javaClass.simpleName})")
                null
            }
        mutableWayBack.value = folder?.let { WayBackSet(it.kind, it.fromMillis, files.sizeOf(places.wayBackDir)) }
    }

    private suspend fun guardedStep(
        what: String,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (e: Exception) {
            log("Restore: could not $what (${e.javaClass.simpleName})")
            false
        }

    private fun stateOf(result: RestoreResult): RestoreState =
        when (result) {
            is RestoreResult.Done -> RestoreState.Done(result.isUndo, result.kind, result.fromMillis, result.damagedMediaFiles)
            is RestoreResult.Failed -> RestoreState.Failed(result.isUndo, result.isPutBack)
            is RestoreResult.Interrupted -> RestoreState.Interrupted(result.isFinished)
        }
}
