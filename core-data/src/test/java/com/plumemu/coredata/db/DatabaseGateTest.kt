package com.plumemu.coredata.db

import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The library file's gate: an interrupted restore is put right
 * before Room's first open, once; a failure there keeps the file shut instead of letting Room make
 * an empty library; after a restore shuts it, nothing in the process opens the file again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseGateTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var db: PlumDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(PlumDatabase.FILE_NAME)
    }

    private fun count(database: PlumDatabase): Int = runBlocking(Dispatchers.IO) { database.query("SELECT COUNT(*) FROM game", null).use { it.moveToFirst(); it.getInt(0) } }

    @Test
    fun theCheckRunsOnceBeforeTheFirstOpen() {
        var runs = 0
        var fileExistedAtTheCheck: Boolean? = null
        val gate =
            DatabaseGate {
                runs++
                fileExistedAtTheCheck = context.getDatabasePath(PlumDatabase.FILE_NAME).exists()
            }
        val database = PlumDatabase.open(context, gate).also { db = it }
        assertEquals("building Room opens nothing", 0, runs)
        assertEquals(0, count(database))
        assertEquals(0, count(database))
        assertEquals(1, runs)
        assertEquals("before Room made the file", false, fileExistedAtTheCheck)
    }

    @Test
    fun aCheckThatFailsKeepsTheFileShutAndRunsAgain() {
        var runs = 0
        val gate =
            DatabaseGate {
                runs++
                if (runs == 1) throw IOException("the files could not be put back")
            }
        val database = PlumDatabase.open(context, gate).also { db = it }
        try {
            count(database)
            fail("the first open must fail")
        } catch (e: Exception) {
            assertTrue("no empty library was made", !context.getDatabasePath(PlumDatabase.FILE_NAME).exists())
        }
        assertEquals(0, count(database))
        assertEquals(2, runs)
    }

    @Test
    fun nothingOpensTheFileAfterItIsShut() {
        val gate = DatabaseGate()
        val database = PlumDatabase.open(context, gate).also { db = it }
        assertEquals(0, count(database))
        gate.shut()
        database.close()
        // A restore moves the file away now: a later query must not make a new one in its place.
        val file = context.getDatabasePath(PlumDatabase.FILE_NAME)
        assertTrue(file.renameTo(java.io.File(file.parentFile, "moved.db")))
        try {
            count(database)
            fail("a query after the shut must not reopen the file")
        } catch (e: Exception) {
            // The gate's refusal.
        }
        for (side in listOf("", "-wal", "-journal")) assertFalse("no plumemu.db$side made again", java.io.File(file.path + side).exists())
    }

    /** A write in flight when a restore closes Room: the close waits for it, or gives up and leaves Room open. */
    @Test
    fun theCloseWaitsForAWriteInFlightOrGivesUp() {
        val gate = DatabaseGate()
        val database = PlumDatabase.open(context, gate).also { db = it }
        assertEquals(0, count(database))
        val inTransaction = java.util.concurrent.CountDownLatch(1)
        val finish = java.util.concurrent.CountDownLatch(1)
        val writer =
            kotlin.concurrent.thread {
                val w = database.openHelper.writableDatabase
                w.beginTransaction()
                try {
                    w.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
                    inTransaction.countDown()
                    finish.await()
                    w.setTransactionSuccessful()
                } finally {
                    w.endTransaction()
                }
            }
        inTransaction.await()
        assertFalse("refused while the write is open", gate.closeWhenQuiet(database, 300))
        assertFalse("nothing shut", gate.isShut)
        // The write ends while the next close waits: the close goes ahead after it.
        kotlin.concurrent.thread {
            Thread.sleep(200)
            finish.countDown()
        }
        assertTrue(gate.closeWhenQuiet(database, 5_000))
        writer.join()
        assertTrue(gate.isShut)
        val file = context.getDatabasePath(PlumDatabase.FILE_NAME)
        for (side in listOf("-wal", "-journal")) assertFalse("nothing left beside the file: $side", java.io.File(file.path + side).let { it.exists() && it.length() > 0 })
    }

    /**
     * A writer that had the database before the shut and queued behind the close's own lock: the
     * close waits for its transaction to end before Room closes, so nothing writes while files move.
     */
    @Test
    fun aWriterQueuedBehindTheCloseEndsBeforeRoomCloses() {
        val gate = DatabaseGate()
        val database = PlumDatabase.open(context, gate).also { db = it }
        assertEquals(0, count(database))
        val w = database.openHelper.writableDatabase
        val go = java.util.concurrent.CountDownLatch(1)
        val ended = java.util.concurrent.atomic.AtomicBoolean(false)
        val woke = java.util.concurrent.CountDownLatch(1)
        val writer =
            kotlin.concurrent.thread {
                go.await()
                woke.countDown()
                w.beginTransaction()
                try {
                    w.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
                    Thread.sleep(300)
                    w.setTransactionSuccessful()
                } finally {
                    w.endTransaction()
                    ended.set(true)
                }
            }
        val closed =
            gate.closeWhenQuiet(database, 5_000) {
                // The close holds the write lock: let the writer queue behind it, and wait until it does.
                go.countDown()
                // Waiting on `go` also reads as not RUNNABLE; on a slow machine the loop below
                // passed before the writer had even woken, so the close ran ahead of it.
                woke.await(5, java.util.concurrent.TimeUnit.SECONDS)
                val deadline = System.currentTimeMillis() + 5_000
                while (writer.state == Thread.State.RUNNABLE || writer.state == Thread.State.NEW) {
                    if (System.currentTimeMillis() > deadline) break
                    Thread.sleep(5)
                }
            }
        assertTrue(closed)
        assertTrue("the queued write ended before Room closed", ended.get())
        writer.join(5_000)
    }
}
