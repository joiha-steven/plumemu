package com.plumemu.coredata.backup

import com.plumemu.coredata.db.PlumDatabase
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The database copy a backup takes while the app runs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseSnapshotTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: PlumDatabase
    private val live: File get() = File(tmp.root, "plumemu.db")

    @Before
    fun setUp() {
        db = fileDatabase(live)
    }

    @After
    fun tearDown() = db.close()

    private fun checkpoint() = db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

    @Test
    fun theCopyIsOneFileWithEveryCommittedRowAndTheSchemaVersion() {
        db.addGames(listOf("Zelda", "Actraiser", "Mario"))
        assertEquals("the live database runs with a WAL, as on a device", "wal", db.openHelper.writableDatabase.query("PRAGMA journal_mode").use { it.moveToFirst(); it.getString(0) })
        val copy = File(tmp.root, "copy.db")
        val snapshot = snapshotOffMain(db, copy)
        assertEquals(db.openHelper.readableDatabase.version, snapshot.schemaVersion)
        assertEquals("the schema since Skipped sub-folders", 13, snapshot.schemaVersion)
        assertEquals(3, snapshot.gameCount)
        assertFalse("no WAL beside the copy", File("${copy.path}-wal").exists())
        assertFalse(File("${copy.path}-shm").exists())
        assertEquals(listOf("Actraiser", "Mario", "Zelda"), titlesIn(copy))
    }

    /**
     * A reader holding the WAL keeps the checkpoint from emptying it (Android gives no way to hold a
     * read transaction on a second connection here, so the checkpoint is left out instead): commits
     * only in the WAL must reach the copy through the replay.
     */
    @Test
    fun commitsOnlyInTheWalAreReplayedIntoTheCopy() {
        db.addGames(listOf("Zelda"))
        checkpoint()
        db.addGames(listOf("Metroid", "Kirby"), folderId = 1)
        assertTrue("the batch is in the WAL", File("${live.path}-wal").length() > 0)
        val mainOnly = File(tmp.root, "main-only.db").also { live.copyTo(it) }
        assertEquals("the main file alone does not have the batch", listOf("Zelda"), titlesIn(mainOnly))

        val snapshot = snapshotOffMain(db, File(tmp.root, "copy.db"), checkpointFirst = false)
        assertTrue(snapshot.hadWalFrames)
        assertEquals(listOf("Kirby", "Metroid", "Zelda"), titlesIn(snapshot.file))
        assertFalse(File("${snapshot.file.path}-wal").exists())
    }

    @Test
    fun aCopyTakenWhileAScanWritesIsNeverTorn() {
        val folder = db.addGames(listOf("Base 1", "Base 2", "Base 3"))
        val writing = AtomicBoolean(true)
        val batches = AtomicInteger()
        // Five games per transaction: a copy holds a whole number of batches, or the copy is torn.
        val writer =
            thread {
                while (writing.get() && batches.get() < MAX_BATCHES) {
                    db.addGames(List(5) { "Batch ${batches.get()} game $it" }, folderId = folder)
                    batches.incrementAndGet()
                }
            }
        val counts = mutableListOf<Int>()
        var duringCommits = 0
        try {
            repeat(SNAPSHOTS) { n ->
                val before = batches.get()
                val snapshot = snapshotOffMain(db, File(tmp.root, "copy-$n.db"), checkpointFirst = n % 2 == 0)
                if (batches.get() != before) duringCommits++
                assertTrue("whole batches only, got ${snapshot.gameCount}", (snapshot.gameCount - 3) % 5 == 0)
                assertEquals(snapshot.gameCount, titlesIn(snapshot.file).size)
                counts += snapshot.gameCount
            }
        } finally {
            writing.set(false)
            writer.join()
        }
        // The test means something only if the writer really committed while copies were taken.
        assertTrue("commits happened while copying: $duringCommits of $SNAPSHOTS", duringCommits > 0)
        assertTrue("the copies saw different libraries: $counts", counts.distinct().size > 1)
    }

    /**
     * How long writers wait: a 5,000-game library on the Mac (Robolectric, not a device). The number
     * is printed and bounded loosely so a regression shows.
     */
    @Test
    fun howLongALargeLibraryHoldsTheLock() {
        val folder = db.addGames(List(1_000) { "Game $it" })
        repeat(4) { batch -> db.addGames(List(1_000) { "Game ${batch + 1}-$it" }, folderId = folder) }
        val times = List(3) { n -> snapshotOffMain(db, File(tmp.root, "copy-$n.db")) }
        val snapshot = times.last()
        assertEquals(5_000, snapshot.gameCount)
        println("SNAPSHOT_TIMING games=5000 bytes=${snapshot.file.length()} lockHeldMs=${times.map { it.lockHeldMillis }}")
        assertTrue("held ${snapshot.lockHeldMillis} ms", times.minOf { it.lockHeldMillis } < LOOSE_BOUND_MS)
    }

    private companion object {
        const val MAX_BATCHES = 2_000
        const val SNAPSHOTS = 6
        const val LOOSE_BOUND_MS = 2_000L
    }
}
