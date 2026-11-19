package com.plumemu.graph

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.collections.PickerEffect
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Collections graph on a real (in-memory) library: folders with their counts, the picker, add, remove, create with a game. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GraphCollectionsTest {
    @get:Rule val timeout = graphTestTimeout()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val graph = GraphCollections(db) { NOW }

    @After
    fun tearDown() = db.close()

    private suspend fun twoGames(): List<Long> {
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(ScanBatch(folder, listOf(SystemEntity("snes", "snes")), listOf("a.sfc", "b.sfc").map { gameRow(folder, it) }))
        return db.scanDao().gamesInFolder(folder).sortedBy { it.documentId }.map { it.id }
    }

    private suspend fun folders() = withTimeout(WAIT_MS) { graph.folders().first() }

    private suspend fun picker(gameId: Long) = withTimeout(WAIT_MS) { graph.pickerFor(gameId).first() }

    @Test
    fun anEmptyLibraryHasThreeEmptyFoldersAndNewCollection() = runBlocking {
        assertEquals(
            listOf(
                Folder.BuiltIn(BuiltInFolder.Recent, 0),
                Folder.BuiltIn(BuiltInFolder.MostPlayed, 0),
                Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 0),
                Folder.NewCollection,
            ),
            folders(),
        )
    }

    @Test
    fun playedGamesCountInRecentAndMostPlayed() = runBlocking {
        val (a, b) = twoGames()
        db.playDao().addSession(a, endedAt = 1_000L, seconds = 60)
        db.playDao().addSession(b, endedAt = 2_000L, seconds = 30)
        val built = folders().filterIsInstance<Folder.BuiltIn>().associate { it.kind to it.count }
        assertEquals(2, built[BuiltInFolder.Recent])
        assertEquals(2, built[BuiltInFolder.MostPlayed])
    }

    @Test
    fun createWithTicksTheGameAndCountsItInTheFolder() = runBlocking {
        val (a, _) = twoGames()
        val id = checkNotNull(graph.createWith("RPG", a))
        assertEquals(setOf(id), picker(a).holding)
        val own = folders().filterIsInstance<Folder.Own>().single()
        assertEquals(Folder.Own(id, "RPG", 1), own)
    }

    @Test
    fun applyAddOnAnExistingCollectionRaisesItsCountToTwo() = runBlocking {
        val (a, b) = twoGames()
        val id = checkNotNull(graph.createWith("RPG", a))
        assertTrue(graph.apply(PickerEffect.Add(id), b))
        assertEquals(Folder.Own(id, "RPG", 2), folders().filterIsInstance<Folder.Own>().single())
    }

    @Test
    fun addingAGameThatLeftTheLibraryReturnsFalseAndTicksNothing() = runBlocking {
        val (a, b) = twoGames()
        val id = checkNotNull(graph.createWith("RPG", a))
        db.openHelper.writableDatabase.execSQL("DELETE FROM game WHERE id = $b")
        assertFalse(graph.apply(PickerEffect.Add(id), b))
        assertEquals(emptySet<Long>(), picker(b).holding)
        assertFalse("None and AskName write nothing", graph.apply(PickerEffect.AskName, a))
    }

    @Test
    fun recentsCountLeavesOutAHiddenGameAndOneRemovedFromRecent() = runBlocking {
        val (a, b) = twoGames()
        db.playDao().addSession(a, endedAt = 1_000L, seconds = 60)
        db.playDao().addSession(b, endedAt = 2_000L, seconds = 60)
        fun recentCount() = runBlocking { folders().filterIsInstance<Folder.BuiltIn>().single { it.kind == BuiltInFolder.Recent }.count }
        assertEquals(2, recentCount())
        db.gameDao().setHidden(a, true)
        assertEquals(1, recentCount())
        db.playDao().removeFromRecent(b)
        assertEquals(0, recentCount())
    }

    @Test
    fun theAppliedEffectsAddAndRemove() = runBlocking {
        val (a, b) = twoGames()
        val id = checkNotNull(graph.createWith("RPG", a))
        graph.apply(PickerEffect.Add(id), b)
        assertEquals(setOf(id), picker(b).holding)
        graph.apply(PickerEffect.Remove(id), b)
        assertEquals(emptySet<Long>(), picker(b).holding)
        graph.apply(PickerEffect.Remove(id), b) // twice: nothing breaks
        graph.apply(PickerEffect.AskName, b)
        graph.apply(PickerEffect.None, b)
        assertEquals(setOf(id), picker(a).holding)
    }

    @Test
    fun addingToACollectionDeletedMeanwhileWritesNothing() = runBlocking {
        val (a, _) = twoGames()
        val id = checkNotNull(graph.createWith("RPG", a))
        db.collectionDao().delete(id)
        graph.apply(PickerEffect.Add(id), a)
        assertEquals(emptyList<Long>(), picker(a).collections.map { it.id })
        assertEquals(emptySet<Long>(), picker(a).holding)
    }

    @Test
    fun aNameInUseGetsANumberAndAGoneGameMakesNoCollection() = runBlocking {
        val (a, b) = twoGames()
        graph.createWith("RPG", a)
        val second = checkNotNull(graph.createWith("rpg", b))
        assertEquals("rpg 2", picker(b).collections.single { it.id == second }.name)
        assertNull(graph.createWith("Ghost", a + 1000))
        assertEquals(2, picker(a).collections.size)
    }

    @Test
    fun aBlankNameThrowsAndMakesNothing() = runBlocking {
        val (a, _) = twoGames()
        assertTrue(runCatching { graph.createWith("  ", a) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(emptyList<Long>(), picker(a).collections.map { it.id })
    }

    private fun gameRow(folder: Long, name: String) =
        GameEntity(
            folderId = folder, documentId = name, parentDocumentId = "root", relativePath = name, fileName = name, sizeBytes = 1,
            modifiedAt = 1, title = name, sortName = name, regions = "", discGroup = null, discCount = 1, systemId = "snes", isPrimaryCopy = true,
        )

    /** The compiled-code restart holds while a collection write runs (`CompiledCodeRestart.busyWork`): counted while it runs, landed or thrown. */
    @Test
    fun aWriteIsCountedWhileItRunsAndNotAfterItLandsOrFails() = runBlocking {
        assertFalse(GraphCollections.isWriting)
        val seen = GraphCollections.writing { GraphCollections.isWriting }
        assertTrue("counted while it runs", seen)
        assertFalse("not after it landed", GraphCollections.isWriting)
        runCatching { GraphCollections.writing { error("disk") } }
        assertFalse("not after it failed", GraphCollections.isWriting)
        graph.create("RPG")
        assertFalse("a real write leaves nothing counted", GraphCollections.isWriting)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
