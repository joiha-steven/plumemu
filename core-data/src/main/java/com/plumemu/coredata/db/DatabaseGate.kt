package com.plumemu.coredata.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * The one way Room reaches the library file (Settings › Advanced › Restore…):
 *
 * - [beforeFirstOpen] runs once, on the thread of Room's first open (never the main thread: Room
 *   opens on its own threads at the first query), before the file is touched. A restore that a
 *   process death stopped halfway puts the files back there, before Room could create an empty
 *   database in their place. When it throws, the open fails (the main screen shows the library
 *   error) and it runs again at the next open: an empty library must never be made over a copy.
 * - After [shut], nothing opens the file again in this process: Room's compatibility layer reopens
 *   a closed file on the next query, and a query that slipped in while a restore moves the file
 *   would create an empty database, or a stray `-wal`, beside the swapped one. The process restarts
 *   after a restore, so the gate is never opened again.
 */
class DatabaseGate(
    private val beforeFirstOpen: () -> Unit = {},
) {
    @Volatile
    var isShut: Boolean = false
        private set

    @Volatile
    private var hasPassed = false

    private val lock = Any()

    /** From now on every open of the file throws; the caller then closes Room. */
    fun shut() {
        isShut = true
    }

    /**
     * Closes [database] for a restore once no write is in flight: a helper thread begins a write
     * transaction, which waits for any transaction another thread holds (a scan's batch, a box-art
     * store). Once it has the lock the gate is shut, the transaction ended and Room closed, so no
     * connection writes a journal or a WAL next to a file that is about to move. False (nothing
     * shut, Room still open) when the lock did not come within [timeoutMs]: the restore is refused.
     * Never on the main thread.
     */
    fun closeWhenQuiet(
        database: RoomDatabase,
        timeoutMs: Long,
        afterShut: () -> Unit = {},
    ): Boolean {
        val held = CountDownLatch(1)
        val connection = AtomicReference<SupportSQLiteDatabase>()
        val release = CountDownLatch(1)
        val abandoned = AtomicBoolean(false)
        val failure = arrayOfNulls<Throwable>(1)
        val holder =
            thread(name = "plumemu-restore-quiet", isDaemon = true) {
                try {
                    val db = database.openHelper.writableDatabase
                    connection.set(db)
                    db.beginTransaction()
                    try {
                        held.countDown()
                        // Given up on: end at once. Else hold the lock until the gate is shut.
                        if (!abandoned.get()) release.await(timeoutMs, TimeUnit.MILLISECONDS)
                    } finally {
                        db.endTransaction()
                    }
                } catch (e: Throwable) {
                    failure[0] = e
                    held.countDown()
                }
            }
        if (!held.await(timeoutMs, TimeUnit.MILLISECONDS) || failure[0] != null) {
            abandoned.set(true)
            release.countDown()
            return false
        }
        shut()
        afterShut()
        release.countDown()
        holder.join(timeoutMs)
        // A writer that had the database before the shut may be queued behind the helper's lock: one
        // more transaction waits for it to end, so no write is running when the files move.
        connection.get()?.let { db ->
            try {
                db.beginTransaction()
                db.endTransaction()
            } catch (e: RuntimeException) {
                // Closed under it already: nothing can write.
            }
        }
        database.close()
        return true
    }

    private fun pass() {
        if (isShut) throw IllegalStateException("the library database is shut for a restore")
        if (hasPassed) return
        synchronized(lock) {
            if (hasPassed) return
            beforeFirstOpen()
            hasPassed = true
        }
    }

    internal fun around(factory: SupportSQLiteOpenHelper.Factory): SupportSQLiteOpenHelper.Factory =
        SupportSQLiteOpenHelper.Factory { configuration -> Gated(factory.create(configuration)) }

    private inner class Gated(
        private val helper: SupportSQLiteOpenHelper,
    ) : SupportSQLiteOpenHelper by helper {
        override val writableDatabase: SupportSQLiteDatabase
            get() {
                pass()
                return helper.writableDatabase
            }

        override val readableDatabase: SupportSQLiteDatabase
            get() {
                pass()
                return helper.readableDatabase
            }
    }
}
