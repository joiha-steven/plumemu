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
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Settings › Advanced › Reset library on the real graph (real database file, real scanner): the
 * games and what the person attached to them go, the picked folders and Android's lasting grants
 * on them stay, and the folders are scanned again. Real data: this is the test that it never
 * touches a grant.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ResetLibraryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val logged = CopyOnWriteArrayList<String>()
    private val graph = AppGraph(context, log = { logged += it })
    private val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AROMs")

    @After
    fun tearDown() {
        graph.database.close()
        context.deleteDatabase("plumemu.db")
    }

    @Test
    fun resetForgetsGamesKeepsFoldersAndGrantsAndScansAgain() = runBlocking {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        val scanDao = graph.database.scanDao()
        val folderId = scanDao.insertFolder(RomFolderEntity(treeUri = tree.toString(), displayName = "ROMs", addedAt = 1))
        scanDao.applyBatch(ScanBatch(folderId, systems = listOf(SystemEntity("snes", "snes")), newGames = listOf(game(folderId))))
        val gameId = scanDao.gamesInFolder(folderId).single().id
        graph.database.gameDao().setFavorite(gameId, true)
        graph.database.playDao().addSession(gameId, endedAt = 5, seconds = 30)

        withTimeout(TIMEOUT_MS) { graph.resetLibrary().join() }

        val grants = context.contentResolver.persistedUriPermissions.map { it.uri }
        assertTrue("the grant is still held", tree in grants)
        assertEquals("the folder row stays", listOf(tree.toString()), scanDao.folders().map { it.treeUri })
        assertEquals(0, scanDao.gamesInFolder(folderId).size)
        assertEquals(null, graph.database.playDao().play(gameId))
        assertFalse("the scan ended (the fake folder cannot be read on the JVM)", graph.startup.scanState.value.isBusy)
        assertTrue("logs carry no path", logged.none { "primary" in it || "ROMs" in it })
    }

    private fun game(folderId: Long) =
        GameEntity(
            folderId = folderId,
            documentId = "doc-1",
            parentDocumentId = "root",
            relativePath = "snes/a.sfc",
            fileName = "a.sfc",
            sizeBytes = 1,
            modifiedAt = 1,
            title = "A",
            sortName = "a",
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
