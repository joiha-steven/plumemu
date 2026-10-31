package com.plumemu.graph

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.settings.screen.FolderAddState
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Library › ROM folders on the real graph (real database file, Android's grants):
 * removing a folder takes its games, their person data and its grant, and nothing of the other
 * folder. Real data: this is the test that no other grant is ever released.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class RemoveFolderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val logged = CopyOnWriteArrayList<String>()
    private val graph = AppGraph(context, log = { logged += it })

    // Answers on the calling thread: the test blocks the main looper.
    private val library = GraphSettingsLibrary(graph, context, Dispatchers.IO, main = Dispatchers.Unconfined, log = { logged += it }, info = { logged += it })
    private val roms = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AROMs")
    private val sd = Uri.parse("content://com.android.externalstorage.documents/tree/1234-ABCD%3AGames")
    private val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    @Test
    fun removingAFolderTakesOnlyItsGamesAndItsGrant() = runBlocking {
        val scanDao = graph.database.scanDao()
        val kept = addFolder(roms, "doc-k")
        val removed = addFolder(sd, "doc-r")
        val keptGame = scanDao.gamesInFolder(kept).single().id
        val removedGame = scanDao.gamesInFolder(removed).single().id
        graph.database.gameDao().setFavorite(keptGame, true)
        graph.database.gameDao().setFavorite(removedGame, true)
        graph.database.playDao().addSession(removedGame, endedAt = 5, seconds = 30)

        val done = CompletableDeferred<Boolean>()
        library.removeFolder(removed) { done.complete(it) }
        assertTrue(withTimeout(TIMEOUT_MS) { done.await() })

        val grants = context.contentResolver.persistedUriPermissions.map { it.uri }
        assertFalse("the removed folder's grant is released", sd in grants)
        assertTrue("the other folder's grant is still held", roms in grants)
        assertEquals("only the other folder's row is left", listOf(roms.toString()), scanDao.folders().map { it.treeUri })
        assertEquals(0, scanDao.gamesInFolder(removed).size)
        assertEquals(null, graph.database.playDao().play(removedGame))
        assertEquals("the other folder keeps its games and favourites", listOf(true), scanDao.gamesInFolder(kept).map { it.isFavorite })
        assertFalse("the scan that follows has ended", graph.startup.scanState.value.isBusy)
        assertTrue("logs carry no path or name", logged.none { "primary" in it || "ROMs" in it || "Games" in it || "1234" in it })
    }

    @Test
    fun aSecondRemovalOfTheSameFolderIsRefusedAndBothAreSeenByEverySettings() = runBlocking {
        val removed = addFolder(sd, "doc-r")
        val gate = CompletableDeferred<Unit>()
        // A scan holds the slot, so the removal waits and stays visible.
        graph.startup.removeFolder { gate.await() }
        val first = CompletableDeferred<Boolean>()
        library.removeFolder(removed) { first.complete(it) }
        val second = CompletableDeferred<Boolean>()
        library.removeFolder(removed) { second.complete(it) }
        val shown = withTimeout(TIMEOUT_MS) { library.content.first { removed in it.removingFolderIds } }
        assertTrue("a Settings opened now still says Removing…", removed in shown.removingFolderIds)
        gate.complete(Unit)
        assertTrue(withTimeout(TIMEOUT_MS) { first.await() })
        // The refused ask is answered with the running removal's outcome, so its "Removing…" ends too.
        assertTrue(withTimeout(TIMEOUT_MS) { second.await() })
        assertEquals("removed once", 1, logged.count { it.contains("folder $removed removed") })
        assertTrue(logged.any { it.contains("folder $removed is already being removed") })
        assertTrue(withTimeout(TIMEOUT_MS) { library.content.first { it.removingFolderIds.isEmpty() } }.removingFolderIds.isEmpty())
    }

    @Test
    fun detectionRunsOnceAtATimeForTheWholeProcess() = runBlocking {
        val first = CompletableDeferred<Boolean>()
        library.detectEmulators { first.complete(it) }
        val second = CompletableDeferred<Boolean>()
        library.detectEmulators { second.complete(it) }
        val outcome = withTimeout(TIMEOUT_MS) { first.await() }
        // The second ask joins the running detection and is answered with its outcome ("Detecting…" ends).
        assertEquals(outcome, withTimeout(TIMEOUT_MS) { second.await() })
        assertEquals("ran once", if (outcome) 0 else 1, logged.count { it.contains("emulator detection failed") })
        assertFalse(library.isDetecting.value)
    }

    @Test
    fun aFolderThatIsAlreadyGoneIsDoneAndReleasesNothing() = runBlocking {
        val kept = addFolder(roms, "doc-k")
        val done = CompletableDeferred<Boolean>()
        library.removeFolder(kept + 100) { done.complete(it) }
        assertTrue(withTimeout(TIMEOUT_MS) { done.await() })
        assertTrue(roms in context.contentResolver.persistedUriPermissions.map { it.uri })
        assertEquals(listOf(kept), graph.database.scanDao().folders().map { it.id })
        assertTrue("says nothing matched, by id", logged.any { it.contains("no folder ${kept + 100} to remove") })
        assertFalse("never claims a removal", logged.any { it.contains("removed") })
    }

    @Test
    fun aDatabaseThatCannotBeReadGivesAPlainLineNotACrash() = runBlocking {
        graph.database.close()
        val content = withTimeout(TIMEOUT_MS) { library.content.first() }
        assertTrue(content.loadFailed)
        assertTrue(logged.any { it.startsWith("Settings: library content failed (") })
    }

    @Test
    fun theAddedLineGoesWhenTheScanItAskedForEnds() = runBlocking {
        library.onPicked(roms.toString())
        assertEquals(FolderAddState.Idle, withTimeout(TIMEOUT_MS) { library.folderAdd.first { it == FolderAddState.Idle } })
        assertFalse(graph.startup.scanState.value.isBusy)
    }

    @Test
    fun aPickedFolderIsKeptRecordedAndPickingItAgainSaysItIsThere() = runBlocking {
        // Holds the scan slot: the "added" line stays until the scan it asked for ends.
        val gate = CompletableDeferred<Unit>()
        graph.startup.removeFolder { gate.await() }
        library.onPicked(roms.toString())
        assertEquals(FolderAddState.Added, withTimeout(TIMEOUT_MS) { library.folderAdd.first { it != FolderAddState.Adding } })
        assertEquals(listOf(roms.toString()), graph.database.scanDao().folders().map { it.treeUri })
        assertTrue(roms in context.contentResolver.persistedUriPermissions.map { it.uri })

        library.startFolderPick()
        library.onPicked(roms.toString())
        assertEquals(FolderAddState.AlreadyAdded, withTimeout(TIMEOUT_MS) { library.folderAdd.first { it != FolderAddState.Adding && it != FolderAddState.Idle } })
        library.onPicked(null)
        assertEquals("a cancelled picker says nothing", FolderAddState.Idle, library.folderAdd.value)
        check(gate.complete(Unit))
    }

    @Test
    fun contentListsEveryFolderAndMarksOneWithoutItsGrant() = runBlocking {
        addFolder(roms, "doc-k")
        addFolder(sd, "doc-r")
        context.contentResolver.releasePersistableUriPermission(sd, flags)
        val content = withTimeout(TIMEOUT_MS) { library.content.first { it.folders.size == 2 } }
        assertEquals(listOf(false, true), content.folders.map { it.isUnavailable })
        assertEquals(listOf(1, 1), content.folders.map { it.gameCount })
        assertEquals(2, content.gameCount)
    }

    private suspend fun addFolder(
        tree: Uri,
        documentId: String,
    ): Long {
        context.contentResolver.takePersistableUriPermission(tree, flags)
        val scanDao = graph.database.scanDao()
        val id = scanDao.insertFolder(RomFolderEntity(treeUri = tree.toString(), displayName = "x", addedAt = 1))
        scanDao.applyBatch(ScanBatch(id, systems = listOf(SystemEntity("snes", "snes")), newGames = listOf(game(id, documentId))))
        return id
    }

    private fun game(
        folderId: Long,
        documentId: String,
    ) = GameEntity(
        folderId = folderId,
        documentId = documentId,
        parentDocumentId = "root",
        relativePath = "snes/$documentId.sfc",
        fileName = "$documentId.sfc",
        sizeBytes = 1,
        modifiedAt = 1,
        title = documentId,
        sortName = documentId,
        regions = "",
        discGroup = null,
        discCount = 1,
        systemId = "snes",
        isPrimaryCopy = true,
    )

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
