package com.plumemu.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The app's Recent writes on a real (in-memory) library: Recent changes, play time does not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphRecentTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val lines = mutableListOf<String>()
    private val recent = GraphRecent(db) { lines += it }

    @After
    fun tearDown() = db.close()

    private suspend fun playedTwo(): List<Long> {
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(folder, listOf(SystemEntity("snes", "snes")), listOf("a.sfc", "b.sfc").map { gameRow(folder, it) }),
        )
        val ids = db.scanDao().gamesInFolder(folder).sortedBy { it.documentId }.map { it.id }
        ids.forEachIndexed { i, id -> db.playDao().addSession(id, endedAt = (i + 1) * 1_000L, seconds = 60) }
        return ids
    }

    private suspend fun shown() = db.gameDao().observeRecent(20).first().map { it.id }

    @Test
    fun removeAndClearChangeRecentOnlyAndSayWhatTheyDid() = runBlocking {
        val (a, b) = playedTwo()
        assertTrue(recent.removeFromRecent(b))
        assertEquals(listOf(a), shown())
        assertFalse("a game never played was not in Recent", recent.removeFromRecent(b + 100))
        assertEquals(1, recent.clearRecent())
        assertEquals(emptyList<Long>(), shown())
        assertEquals(120L, db.playDao().totalSeconds())
        assertTrue("lines carry ids and counts only: $lines", lines.none { "sfc" in it })
    }

    private fun gameRow(folder: Long, name: String) =
        GameEntity(
            folderId = folder, documentId = name, parentDocumentId = "root", relativePath = name, fileName = name, sizeBytes = 1,
            modifiedAt = 1, title = name, sortName = name, regions = "", discGroup = null, discCount = 1, systemId = "snes", isPrimaryCopy = true,
        )
}
