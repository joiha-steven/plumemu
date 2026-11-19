package com.plumemu.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.collections.Folder
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Library › Collections' writes on a real in-memory library: rename (saved, a name in use gets " 2", blank and
 * too long refused), delete (memberships gone, games kept) and order (persisted, and the home folders follow it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphSettingsCollectionsTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val logged = mutableListOf<String>()

    @After
    fun tearDown() = db.close()

    private fun TestScope.settings() = GraphSettingsCollections(GraphCollections(db, { 1_000L }), this, UnconfinedTestDispatcher(testScheduler), logged::add)

    /** Asks the graph and waits for its answer (Room runs on its own threads). */
    private suspend fun ask(call: ((Boolean) -> Unit) -> Unit): Boolean {
        val answer = CompletableDeferred<Boolean>()
        call { answer.complete(it) }
        return answer.await()
    }

    private suspend fun create(name: String) = db.collectionDao().create(name, 1)

    private suspend fun names() = db.collectionDao().observeCollections().first().map { it.name }

    private suspend fun game(): Long {
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
        val row =
            GameEntity(
                folderId = folder, documentId = "a.sfc", parentDocumentId = "root", relativePath = "a.sfc", fileName = "a.sfc", sizeBytes = 1,
                modifiedAt = 1, title = "A", sortName = "a", regions = "", discGroup = null, discCount = 1, systemId = "snes", isPrimaryCopy = true,
            )
        db.scanDao().applyBatch(ScanBatch(folder, listOf(SystemEntity("snes", "snes")), listOf(row)))
        return db.scanDao().gamesInFolder(folder).single().id
    }

    @Test
    fun renameIsSavedAndANameInUseGetsTwo() = runTest {
        val rpg = create("RPG")
        create("Couch")
        val graph = settings()
        val answers = listOf(ask { graph.rename(rpg, "  Platformers ", it) }, ask { graph.rename(rpg, "couch", it) })
        assertEquals(listOf(true, true), answers)
        assertEquals(listOf("couch 2", "Couch"), names())
    }

    @Test
    fun aBlankOrTooLongNameIsRefusedAndNothingChanges() = runTest {
        val rpg = create("RPG")
        val graph = settings()
        val answers = listOf(ask { graph.rename(rpg, "   ", it) }, ask { graph.rename(rpg, "x".repeat(41), it) })
        assertEquals(listOf(false, false), answers)
        assertEquals(listOf("RPG"), names())
        assertEquals("logged by class only", 2, logged.count { it.endsWith("(IllegalArgumentException)") })
    }

    @Test
    fun renamingACollectionThatIsGoneAnswersFalse() = runTest {
        val graph = settings()
        assertFalse(ask { graph.rename(99, "Gone", it) })
    }

    @Test
    fun deleteRemovesTheCollectionAndItsMembershipsAndKeepsTheGames() = runTest {
        val rpg = create("RPG")
        val keep = create("Keep")
        val gameId = game()
        db.collectionDao().add(rpg, gameId, 1)
        db.collectionDao().add(keep, gameId, 1)
        val graph = settings()
        assertTrue(ask { graph.delete(rpg, it) })
        assertEquals(listOf("Keep"), names())
        assertEquals("the other collection still holds the game", listOf(keep), db.collectionDao().observeCollectionsOf(gameId).first())
        assertTrue("the game is still in the library", db.gameDao().game(gameId) != null)
    }

    @Test
    fun theOrderIsStoredAndTheHomeFoldersFollowIt() = runTest {
        val a = create("A")
        val b = create("B")
        val c = create("C")
        val graph = settings()
        assertTrue(ask { graph.reorder(listOf(c, a, b), it) })
        assertEquals(listOf("C", "A", "B"), names())
        val own = GraphCollections(db, { 1_000L }).folders().first().filterIsInstance<Folder.Own>()
        assertEquals(listOf("C", "A", "B"), own.map { it.name })
        assertTrue(own.map { it.id } == listOf(c, a, b))
        assertFalse(logged.isNotEmpty())
    }
}
