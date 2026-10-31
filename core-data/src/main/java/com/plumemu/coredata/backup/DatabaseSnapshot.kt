package com.plumemu.coredata.backup

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * A consistent copy of the library database taken while the app runs (Back up… › Everything).
 *
 * Why this way: `VACUUM INTO` needs SQLite 3.27,
 * Android 6 ships 3.8, and Android exposes no online-backup call. So:
 *
 * 1. A `wal_checkpoint(TRUNCATE)` first, outside any lock, only to make the WAL small (it stops
 *    short while a reader uses the WAL; that is fine).
 * 2. Inside Room's write transaction (`BEGIN IMMEDIATE` with WAL, `BEGIN EXCLUSIVE` without): no
 *    connection can commit, so the main file and the WAL do not change except by a checkpoint, which
 *    only writes pages the WAL also holds. The two files are copied as they are, which is the state
 *    of the last commit. Every writer (a scan, a box-art store) waits for the whole copy; without a
 *    WAL (Room's choice on low-RAM devices) `BEGIN EXCLUSIVE` makes readers wait too, so the screen's
 *    reads wait as well. How long is [Snapshot.lockHeldMillis], logged by the app.
 * 3. The copy is opened on its own: SQLite replays the copied WAL into it, the journal is switched
 *    to DELETE (which must answer `delete`) so it is one self-contained file, and `quick_check` must
 *    answer `ok`. Any of these failing throws, and the copy goes together with its side files.
 *
 * Never call it on the main thread: it reads and writes the whole file (Room refuses it there).
 */
object DatabaseSnapshot {
    class Snapshot(
        val file: File,
        /** `PRAGMA user_version`, which Room keeps equal to its schema version. */
        val schemaVersion: Int,
        val gameCount: Int,
        /** How long Room's write lock was held for the copy: what writers (and without a WAL, readers) waited. */
        val lockHeldMillis: Long,
        /** Whether the copied WAL held frames, i.e. step 3 replayed commits the main file did not have. */
        val hadWalFrames: Boolean,
    )

    /**
     * [checkpointFirst] is false only in tests: it leaves the WAL as the commits left it, as a reader
     * holding the WAL does on a device, so the replay of step 3 is exercised.
     */
    fun take(
        database: RoomDatabase,
        into: File,
        log: (String) -> Unit = {},
        checkpointFirst: Boolean = true,
    ): Snapshot {
        val live = database.openHelper.writableDatabase
        val path = live.path ?: throw IOException("the database has no file (in memory)")
        into.parentFile?.mkdirs()
        deleteWithSideFiles(into)
        if (checkpointFirst) {
            try {
                live.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            } catch (e: Exception) {
                // Only to shorten the copy below; the copy is consistent without it.
                log("Backup: the checkpoint before the copy failed (${e.javaClass.simpleName}); copying the WAL as it is")
            }
        }
        val copiedWal = File("${into.path}-wal")
        var lockHeld = 0L
        database.runInTransaction {
            val start = System.nanoTime()
            File(path).copyTo(into, overwrite = true)
            val wal = File("$path-wal")
            if (wal.isFile && wal.length() > 0) wal.copyTo(copiedWal, overwrite = true)
            lockHeld = (System.nanoTime() - start) / NANOS_PER_MILLI
        }
        val hadWalFrames = copiedWal.isFile && copiedWal.length() > 0
        try {
            return fold(into, lockHeld, hadWalFrames)
        } catch (e: Exception) {
            // A copy that is not one checked file is never used: it goes with its WAL, never without it.
            deleteWithSideFiles(into)
            throw e
        }
    }

    /** Replays the copied WAL into [file], leaves it one file, checks it and reads its version and game count. */
    private fun fold(
        file: File,
        lockHeldMillis: Long,
        hadWalFrames: Boolean,
    ): Snapshot {
        val copy = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            // In DELETE mode SQLite folds the WAL in and removes it itself; any other answer leaves the copy unused.
            val mode = copy.rawQuery("PRAGMA journal_mode=DELETE", null).use { if (it.moveToFirst()) it.getString(0) else null }
            if (mode?.lowercase(Locale.ROOT) != "delete") throw IOException("the database copy stayed in journal mode $mode")
            val check = copy.rawQuery("PRAGMA quick_check", null).use { if (it.moveToFirst()) it.getString(0) else null }
            if (check != "ok") throw IOException("the database copy did not pass its check")
            val games = DatabaseUtils.longForQuery(copy, "SELECT COUNT(*) FROM game", null)
            return Snapshot(file, copy.version, games.toInt(), lockHeldMillis, hadWalFrames)
        } finally {
            copy.close()
        }
    }

    private const val NANOS_PER_MILLI = 1_000_000L

    private fun deleteWithSideFiles(file: File) = listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
}
