package com.plumemu.graph

import com.plumemu.coredata.account.ScraperAccount
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coredata.backup.BackupUnpack
import com.plumemu.coredata.backup.SettingsJson
import com.plumemu.coredata.settings.PlumSettings
import java.io.File
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

/**
 * Where a restore keeps its files. Every folder is in
 * the app's own storage on the same partition as the database and the box art, so moving the
 * library aside and back is a rename: no copy, no extra space, no time.
 */
internal class RestorePlaces(
    /** The library database Room opens (`databases/plumemu.db`); its `-wal`, `-shm` and `-journal` go with it. */
    val databaseFile: File,
    /** The box-art folder (`no_backup/screenscraper-media`). */
    val mediaDir: File,
    /** `noBackupFilesDir`: not in Android's own backups. */
    root: File,
) {
    /** The file being restored, extracted and checked; the incoming side of a restore. */
    val workDir = File(root, "restore-work")

    /** The way back: what the device had before the last restore (one set; a new restore replaces it). */
    val wayBackDir = File(root, "before-restore")

    /**
     * Where a restore moves the device's library aside. It becomes [wayBackDir] only once the
     * restore has finished ([RestoreFiles.promoteNewWayBack]): until then the earlier set stays whole.
     */
    val newWayBackDir = File(root, "before-restore-new")

    /** What an Undo moves aside (the restored library) until the Undo has finished. */
    val undoAsideDir = File(root, "restore-undo")

    /** Folders on their way out, renamed here at once and deleted in the background. */
    val discardDir = File(root, "restore-discard")

    /** Written before the first move and removed at the end: one left at a start means a process death. */
    val markerFile = File(root, "restore-in-progress.json")

    /** How the last restore that restarted the app ended, for the Restore row after the restart. */
    val resultFile = File(root, "restore-result.json")
}

/** A restore puts a backup in place; an Undo puts the way-back set back. Both are the same swap. */
internal enum class RestoreOperation(val code: String) {
    Restore("restore"),
    Undo("undo"),
    ;

    /** What goes into place. */
    fun incoming(places: RestorePlaces): File = if (this == Restore) places.workDir else places.wayBackDir

    /** Where what was in place goes: the next way-back set for a restore, a folder deleted afterwards for an Undo. */
    fun aside(places: RestorePlaces): File = if (this == Restore) places.newWayBackDir else places.undoAsideDir
}

/** How far a swap got: the files are moving (a death puts them back), or they are in place and the settings and account are written (a death finishes). */
internal enum class RestoreStep(val code: String) {
    Moving("moving"),
    Applying("applying"),
}

internal data class RestoreMarker(
    val operation: RestoreOperation,
    val kind: BackupKind,
    val step: RestoreStep,
    /** When the restored backup was made (a restore) or the set was put aside (an Undo): the row's date. */
    val fromMillis: Long,
)

/** How settings in a folder are written: by the restore's flag rules (a backup's) or exactly (the way-back set's). */
internal enum class SettingsRule(val code: String) {
    FromBackup("fromBackup"),
    Exact("exact"),
}

/** What a swap does with the ScreenScraper account. */
internal sealed interface AccountPlan {
    /** The backup has no account: the device's own stays. */
    data object Keep : AccountPlan

    /** Signed out (the set was made while signed out, or the backup's password could not be read there). */
    data object SignOut : AccountPlan

    /** Exactly this account, already encrypted with this device's key. */
    data class Store(val account: ScraperAccount) : AccountPlan
}

/** A folder ready to be swapped in: its kind, its date, how its settings are written, and the parts themselves. */
internal class RestoreFolder(
    val kind: BackupKind,
    val fromMillis: Long,
    val rule: SettingsRule,
    val settings: PlumSettings,
    val account: AccountPlan,
    /** Box-art entries of the file left out as damaged, for the line after the restore; 0 for a way-back set. */
    val damagedMediaFiles: Int = 0,
)

/**
 * The file side of a restore: the
 * folders' small JSON files, the marker, and the moves. No method here copies the library: every
 * move is a rename, and a rename that fails throws, so the caller puts back what moved.
 *
 * The order of the moves is what makes a process death at any point safe ([rollBackFiles]): the
 * database's side files move before the database itself, and the box-art folder always exists
 * (made empty when there was none), so "the old one is in the aside folder" always means "it moved".
 *
 * Logs carry steps and exception classes, never a path or a value.
 */
internal class RestoreFiles(
    val places: RestorePlaces,
    private val log: (String) -> Unit,
) {
    private val databaseSides = listOf("-wal", "-shm", "-journal")

    // The folder's files.

    fun writeFolder(
        dir: File,
        folder: RestoreFolder,
    ) {
        dir.mkdirs()
        writeWhole(File(dir, SETTINGS_FILE), SettingsJson.encode(folder.settings).toString())
        writeWhole(File(dir, ACCOUNT_FILE), accountJson(folder.account).toString())
        // The descriptor last: a folder without it is not ready (a death while staging, an unfinished set).
        writeWhole(
            File(dir, FOLDER_FILE),
            JSONObject()
                .put("kind", folder.kind.code)
                .put("fromMillis", folder.fromMillis)
                .put("settingsRule", folder.rule.code)
                .put("damagedMediaFiles", folder.damagedMediaFiles)
                .toString(),
        )
    }

    /** The folder's parts; null when it is not a whole folder (no descriptor). Throws when a part cannot be read. */
    fun readFolder(dir: File): RestoreFolder? {
        val descriptor = File(dir, FOLDER_FILE)
        if (!descriptor.isFile) return null
        try {
            val d = JSONObject(descriptor.readText())
            val kind = BackupKind.entries.first { it.code == d.getString("kind") }
            val rule = SettingsRule.entries.first { it.code == d.getString("settingsRule") }
            val settings = SettingsJson.decode(File(dir, SETTINGS_FILE).readText()).settings
            val account = accountPlanOf(JSONObject(File(dir, ACCOUNT_FILE).readText()))
            // Absent in folders written before it existed.
            return RestoreFolder(kind, d.getLong("fromMillis"), rule, settings, account, d.optInt("damagedMediaFiles", 0))
        } catch (e: JSONException) {
            throw IOException("a restore folder could not be read", e)
        } catch (e: NoSuchElementException) {
            throw IOException("a restore folder names an unknown kind", e)
        }
    }

    /** The settings and account half of a folder, for the way-back set before any file moves. */
    fun writeSettingsAndAccount(
        dir: File,
        settings: PlumSettings,
        account: AccountPlan,
    ) {
        dir.mkdirs()
        writeWhole(File(dir, SETTINGS_FILE), SettingsJson.encode(settings).toString())
        writeWhole(File(dir, ACCOUNT_FILE), accountJson(account).toString())
    }

    /** The set's descriptor, written when the restore has finished: from then on Undo is offered. */
    fun completeFolder(
        dir: File,
        kind: BackupKind,
        fromMillis: Long,
    ) = writeWhole(
        File(dir, FOLDER_FILE),
        JSONObject().put("kind", kind.code).put("fromMillis", fromMillis).put("settingsRule", SettingsRule.Exact.code).toString(),
    )

    /** The settings and account saved in [dir] before the swap: what a failure puts back. */
    fun readSaved(dir: File): Pair<PlumSettings, AccountPlan> =
        try {
            SettingsJson.decode(File(dir, SETTINGS_FILE).readText()).settings to accountPlanOf(JSONObject(File(dir, ACCOUNT_FILE).readText()))
        } catch (e: JSONException) {
            throw IOException("the saved settings could not be read", e)
        }

    /** Bytes kept in the way-back set: what the Undo row says it costs. */
    fun sizeOf(dir: File): Long = sizeOfTree(dir)

    // Not walkTopDown: it asserts the root is a directory, which throws an Error when another
    // thread discards the folder mid-walk (seen as a flaky GraphRestoreSafetyTest teardown).
    // A folder that vanishes counts as empty.
    private fun sizeOfTree(file: File): Long =
        if (file.isFile) file.length() else file.listFiles()?.sumOf { sizeOfTree(it) } ?: 0L

    // The marker.

    fun writeMarker(marker: RestoreMarker) =
        writeWhole(
            places.markerFile,
            JSONObject()
                .put("operation", marker.operation.code)
                .put("kind", marker.kind.code)
                .put("step", marker.step.code)
                .put("fromMillis", marker.fromMillis)
                .toString(),
        )

    /** Null when there is none; throws when one is there but cannot be read. */
    fun readMarker(): RestoreMarker? {
        val file = places.markerFile
        if (!file.exists()) return null
        return try {
            val o = JSONObject(file.readText())
            RestoreMarker(
                operation = RestoreOperation.entries.first { it.code == o.getString("operation") },
                kind = BackupKind.entries.first { it.code == o.getString("kind") },
                step = RestoreStep.entries.first { it.code == o.getString("step") },
                fromMillis = o.getLong("fromMillis"),
            )
        } catch (e: Exception) {
            // Written whole and synced, so this is a damaged disk or a foreign file: the marker stays
            // (deleting it would forget a swap that may be half done), and the caller decides.
            throw IOException("the restore marker could not be read (${e.javaClass.simpleName})", e)
        }
    }

    fun clearMarker() {
        if (places.markerFile.exists() && !places.markerFile.delete()) log("Restore: the marker could not be removed")
    }

    // The moves.

    /** The device's library into [operation]'s aside folder: the database's side files first, the database, then the box art. */
    fun moveAside(operation: RestoreOperation) {
        val aside = operation.aside(places)
        val asideDatabase = File(aside, BackupUnpack.DATABASE_PATH)
        asideDatabase.parentFile?.mkdirs()
        // Always a file to move, as with the box art below (an empty file is an empty SQLite database).
        if (!places.databaseFile.exists()) {
            places.databaseFile.parentFile?.mkdirs()
            places.databaseFile.createNewFile()
        }
        for (side in databaseSides) moveIfThere(sideOf(places.databaseFile, side), sideOf(asideDatabase, side))
        moveIfThere(places.databaseFile, asideDatabase)
        // Always a folder to move: its presence in the aside folder then always means "the old one moved".
        if (!places.mediaDir.exists() && !places.mediaDir.mkdirs()) throw IOException("the box-art folder could not be made")
        rename(places.mediaDir, File(aside, BackupUnpack.MEDIA_FOLDER), "the box art aside")
    }

    /** [operation]'s incoming library into place. */
    fun placeIncoming(operation: RestoreOperation) {
        val incoming = operation.incoming(places)
        for (side in databaseSides) {
            val stray = sideOf(places.databaseFile, side)
            if (stray.exists() && !stray.delete()) throw IOException("a side file of the database could not be removed")
        }
        // The side files first, the database last, as moveAside does: a database in place then means all of it is.
        val incomingDatabase = File(incoming, BackupUnpack.DATABASE_PATH)
        for (side in databaseSides) moveIfThere(sideOf(incomingDatabase, side), sideOf(places.databaseFile, side))
        rename(incomingDatabase, places.databaseFile, "the database into place")
        val media = File(incoming, BackupUnpack.MEDIA_FOLDER)
        if (media.isDirectory) {
            rename(media, places.mediaDir, "the box art into place")
        } else if (!places.mediaDir.mkdirs()) {
            throw IOException("the box-art folder could not be made")
        }
    }

    /**
     * Puts the device's library back from [operation]'s aside folder, whatever point the moves
     * reached; running it again does nothing more. What is in place is the incoming one and goes back
     * to its folder (or to the discard folder). Throws when a move fails: the files stay where they
     * are, and the marker stays for the next try.
     */
    fun rollBackFiles(operation: RestoreOperation) {
        val aside = operation.aside(places)
        val incoming = operation.incoming(places)
        val asideMedia = File(aside, BackupUnpack.MEDIA_FOLDER)
        if (asideMedia.exists()) {
            moveOut(places.mediaDir, File(incoming, BackupUnpack.MEDIA_FOLDER))
            rename(asideMedia, places.mediaDir, "the box art back")
        }
        val asideDatabase = File(aside, BackupUnpack.DATABASE_PATH)
        if (asideDatabase.exists()) {
            // The old side files moved before the old database: everything in place now is the incoming one's.
            val incomingDatabase = File(incoming, BackupUnpack.DATABASE_PATH)
            incomingDatabase.parentFile?.mkdirs()
            moveOut(places.databaseFile, incomingDatabase)
            for (side in databaseSides) moveOut(sideOf(places.databaseFile, side), sideOf(incomingDatabase, side))
            rename(asideDatabase, places.databaseFile, "the database back")
        }
        // Side files moved before the database (all of them, or a death left some).
        for (side in databaseSides) moveIfThere(sideOf(asideDatabase, side), sideOf(places.databaseFile, side))
    }

    /**
     * At a start, before Room opens the file (the [com.plumemu.coredata.db.DatabaseGate]):
     *
     * - The files of a swap that died before they were all in place (or whose put-back could not
     *   move its marker back to "moving") go back. The marker stays: the settings and the account
     *   follow after the first frame ([GraphRestore.onStartup]).
     * - Whatever the marker says, or when it cannot be read: no database in place but one in an
     *   aside folder means a swap stopped halfway, and the library goes back.
     *
     * Running it again does nothing more. Throws when the library cannot be put back, or when the
     * marker cannot be read and no library is in place: the database must not open then (Room would
     * make an empty one where the library belongs).
     */
    @Synchronized
    fun recoverMovingFiles() {
        var isUnreadable = false
        val marker =
            try {
                readMarker()
            } catch (e: IOException) {
                log("Restore: ${e.message}; looking for the library without it")
                isUnreadable = true
                null
            }
        if (marker != null && needsFilesBack(marker)) {
            log("Restore: the last ${marker.operation.code} was interrupted before its files were in place; putting them back")
            rollBackFiles(marker.operation)
        }
        if (isUnreadable) {
            // The step is unknown, and onStartup then puts the device's settings back: the files must
            // match, from whatever point the swap reached (the incoming library may already be in place,
            // with the device's own aside). rollBackFiles is safe from any step; a finished swap is lost,
            // never the device's library.
            val swap = RestoreOperation.entries.firstOrNull { File(it.aside(places), BackupUnpack.DATABASE_PATH).exists() }
            if (swap != null) {
                log("Restore: the step of the last ${swap.code} is unknown; putting its files back")
                rollBackFiles(swap)
            }
        }
        if (!places.databaseFile.exists()) {
            val stopped = RestoreOperation.entries.firstOrNull { File(it.aside(places), BackupUnpack.DATABASE_PATH).exists() }
            if (stopped != null) {
                log("Restore: no library in place but one aside; putting it back")
                rollBackFiles(stopped)
            }
        }
        if (isUnreadable && !places.databaseFile.exists()) throw IOException("the restore marker cannot be read and no library is in place")
    }

    /** The files of [marker]'s swap must go back: they were moving, or a put-back left the marker at "applying". */
    fun needsFilesBack(marker: RestoreMarker): Boolean =
        marker.kind == BackupKind.Everything && (marker.step == RestoreStep.Moving || !isPlaced(marker.operation))

    /** True when [operation]'s incoming database is in place: there is one in place and none left to move. */
    fun isPlaced(operation: RestoreOperation): Boolean =
        places.databaseFile.exists() && !File(operation.incoming(places), BackupUnpack.DATABASE_PATH).exists()

    /** The operation whose aside folder holds saved settings, for a marker that cannot be read; null when none does. */
    fun operationWithSavedSettings(): RestoreOperation? = RestoreOperation.entries.firstOrNull { File(it.aside(places), SETTINGS_FILE).isFile }

    /**
     * A finished restore's set ([RestorePlaces.newWayBackDir], whole) replaces the earlier one. The
     * earlier set is discarded only now, so every failure before it kept the device's way back.
     * A new set that is not whole (a swap that died, or was put back) is discarded instead.
     */
    fun promoteNewWayBack() {
        val next = places.newWayBackDir
        if (!next.exists()) return
        if (!File(next, FOLDER_FILE).isFile) {
            discard(next)
            return
        }
        discard(places.wayBackDir)
        if (!next.renameTo(places.wayBackDir)) log("Restore: the new way-back set could not be put in its place")
    }

    /**
     * Folds a WAL or a hot journal into [operation]'s incoming database before it moves (an Undo's
     * set was moved aside from a live database), so one file goes into place. Opened alone: nothing
     * else uses that file.
     */
    fun foldIncoming(operation: RestoreOperation) {
        val database = File(operation.incoming(places), BackupUnpack.DATABASE_PATH)
        if (!database.exists() || databaseSides.none { sideOf(database, it).let { f -> f.exists() && f.length() > 0 } }) return
        val db = android.database.sqlite.SQLiteDatabase.openDatabase(database.path, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        try {
            val mode = db.rawQuery("PRAGMA journal_mode=DELETE", null).use { if (it.moveToFirst()) it.getString(0) else null }
            if (mode?.lowercase(java.util.Locale.ROOT) != "delete") throw IOException("the kept database stayed in journal mode $mode")
        } finally {
            db.close()
        }
    }

    // How the last one ended, across a restart.

    fun writeResult(result: RestoreResult) {
        val o = JSONObject().put("kind", result.kind.code)
        when (result) {
            is RestoreResult.Done ->
                o.put("outcome", if (result.isUndo) "undone" else "restored").put("fromMillis", result.fromMillis).put("damagedMediaFiles", result.damagedMediaFiles)
            is RestoreResult.Failed -> o.put("outcome", "failed").put("isUndo", result.isUndo).put("isPutBack", result.isPutBack)
            is RestoreResult.Interrupted -> o.put("outcome", "interrupted").put("isFinished", result.isFinished)
        }
        try {
            writeWhole(places.resultFile, o.toString())
        } catch (e: IOException) {
            // Only the row's line after the restart: the restore itself is done or put back.
            log("Restore: the result could not be kept for the next start (${e.javaClass.simpleName})")
        }
    }

    /** The result left by the last process, removed as it is read; null when there is none. */
    fun takeResult(): RestoreResult? {
        val file = places.resultFile
        if (!file.isFile) return null
        return try {
            val o = JSONObject(file.readText())
            val kind = BackupKind.entries.first { it.code == o.getString("kind") }
            when (o.getString("outcome")) {
                "restored" -> RestoreResult.Done(kind, o.getLong("fromMillis"), isUndo = false, damagedMediaFiles = o.optInt("damagedMediaFiles", 0))
                "undone" -> RestoreResult.Done(kind, o.getLong("fromMillis"), isUndo = true)
                "failed" -> RestoreResult.Failed(kind, o.getBoolean("isUndo"), o.getBoolean("isPutBack"))
                else -> RestoreResult.Interrupted(kind, o.getBoolean("isFinished"))
            }
        } catch (e: Exception) {
            log("Restore: the last result could not be read (${e.javaClass.simpleName})")
            null
        } finally {
            if (!file.delete()) log("Restore: the last result could not be removed")
        }
    }

    // Folders on their way out.

    /** Renames [dir] into the discard folder at once (deleted later by [emptyDiscard]); deletes it in place when that fails. */
    fun discard(dir: File) {
        if (!dir.exists()) return
        places.discardDir.mkdirs()
        val target = File(places.discardDir, "${dir.name}-${System.nanoTime()}")
        if (!dir.renameTo(target) && !dir.deleteRecursively()) log("Restore: a folder could not be removed")
    }

    /** Deletes what [discard] put aside; slow for a large box-art folder, so only on the I/O thread. */
    fun emptyDiscard() {
        if (places.discardDir.exists() && !places.discardDir.deleteRecursively()) log("Restore: the discarded files could not all be deleted")
    }

    private fun moveOut(
        from: File,
        preferred: File,
    ) {
        if (!from.exists()) return
        if (!preferred.exists() && from.renameTo(preferred)) return
        discard(from)
        if (from.exists()) throw IOException("a file in the library's place could not be moved out")
    }

    private fun moveIfThere(
        from: File,
        to: File,
    ) {
        if (from.exists()) rename(from, to, "a database file")
    }

    private fun rename(
        from: File,
        to: File,
        what: String,
    ) {
        if (to.exists()) throw IOException("could not move $what: the place is taken")
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) throw IOException("could not move $what")
    }

    private fun sideOf(
        database: File,
        side: String,
    ) = File(database.path + side)

    private fun writeWhole(
        file: File,
        text: String,
    ) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        // Synced before the rename: after a power cut the file is the old one or the whole new one.
        java.io.FileOutputStream(temp).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("a restore file could not be stored")
        }
    }

    private fun accountJson(plan: AccountPlan): JSONObject =
        when (plan) {
            AccountPlan.Keep -> JSONObject().put("plan", "keep")
            AccountPlan.SignOut -> JSONObject().put("plan", "signOut")
            // The ciphertext of this device's key, never the plain password.
            is AccountPlan.Store ->
                JSONObject()
                    .put("plan", "store")
                    .put("username", plan.account.username)
                    .put("ciphertext", plan.account.passwordCiphertext)
                    .put("refused", plan.account.isRefused)
        }

    private fun accountPlanOf(o: JSONObject): AccountPlan =
        when (o.getString("plan")) {
            "keep" -> AccountPlan.Keep
            "signOut" -> AccountPlan.SignOut
            else -> AccountPlan.Store(ScraperAccount(o.getString("username"), o.getString("ciphertext"), o.getBoolean("refused")))
        }

    private companion object {
        const val SETTINGS_FILE = "settings.json"
        const val ACCOUNT_FILE = "account.json"
        const val FOLDER_FILE = "folder.json"
    }
}

/** How a restore that restarted the app ended, kept in [RestorePlaces.resultFile] across the restart. */
internal sealed interface RestoreResult {
    val kind: BackupKind

    data class Done(
        override val kind: BackupKind,
        val fromMillis: Long,
        val isUndo: Boolean,
        val damagedMediaFiles: Int = 0,
    ) : RestoreResult

    data class Failed(
        override val kind: BackupKind,
        val isUndo: Boolean,
        val isPutBack: Boolean,
    ) : RestoreResult

    data class Interrupted(
        override val kind: BackupKind,
        val isFinished: Boolean,
    ) : RestoreResult
}
