package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Collections survive what the library goes through: rescans, moved files, folds,
 * a move onto a new launch row, Reset library, a removed file, an uninstalled app and a shortcut
 * pinned again. Each case reads the membership rows themselves, so an unbound row that was
 * silently dropped fails as loudly as a wrong list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionScanKeepTest {
    private lateinit var db: PlumDatabase
    private lateinit var library: ScannedLibrary
    private val dao get() = db.collectionDao()
    private val scan get() = db.scanDao()
    private val folderId get() = library.folderId

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        library = ScannedLibrary(db)
        library.pickFolder()
    }

    @After
    fun tearDown() = db.close()

    private data class Membership(val collectionId: Long, val gameId: Long?, val contentKey: String?, val pathKey: String)

    private fun memberships(): List<Membership> =
        db.openHelper.readableDatabase.query("SELECT collection_id, game_id, content_key, path_key FROM collection_game ORDER BY id").use { c ->
            buildList {
                while (c.moveToNext()) add(Membership(c.getLong(0), if (c.isNull(1)) null else c.getLong(1), if (c.isNull(2)) null else c.getString(2), c.getString(3)))
            }
        }

    private suspend fun titles(collectionId: Long) = dao.observeCollectionGames(collectionId).first().map { it.title }

    private suspend fun stored(id: Long): StoredGame = scan.gamesInFolder(folderId).single { it.id == id }.let { g ->
        scan.gamesInDirectory(folderId, g.parentDocumentId).single { it.id == id }
    }

    @Test
    fun aRescanKeepsMembershipsAndRefreshesAContentKeyReadLater() = runTest {
        val ids = library.scanIn(keyedGame(folderId, "a.sfc", listOf("code:SNS-A")))
        val rpgs = dao.create("RPGs")
        val a = ids.getValue("a.sfc")
        dao.add(rpgs, a)
        assertEquals(null, memberships().single().contentKey)

        // A whole-file read gave it a crc: key since; the next scan's end stores it.
        db.openHelper.writableDatabase.execSQL("UPDATE game SET fix_keys = 'code:SNS-A' || char(10) || 'crc:aaaa:1' WHERE id = $a")
        val row = stored(a)
        scan.applyBatch(ScanBatch(folderId, changedGames = listOf(row.copy(fields = row.fields.copy(title = "Alpha")))))
        scan.finishScan(ScanFinish(folderId))
        assertEquals(listOf(Membership(rpgs, a, "crc:aaaa:1", "content://tree/roms\na.sfc")), memberships())
        assertEquals(listOf("Alpha"), titles(rpgs))

        // A game that loses its fingerprint keys keeps the content key it had.
        db.openHelper.writableDatabase.execSQL("UPDATE game SET fix_keys = '' WHERE id = $a")
        scan.finishScan(ScanFinish(folderId))
        assertEquals("crc:aaaa:1", memberships().single().contentKey)
    }

    @Test
    fun aMovedFileKeepsItsMembershipsWithItsNewPathAndBothRowsMembershipsAreMergedOnce() = runTest {
        val ids = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")))
        val a = ids.getValue("a.sfc")
        val rpgs = dao.create("RPGs")
        val hacks = dao.create("Hacks")
        dao.add(rpgs, a)
        // The moved file's new row was listed while the scan ran: the person put it in both.
        val inserted = scan.applyBatch(ScanBatch(folderId, newGames = listOf(keyedGame(folderId, "sub-a.sfc", listOf("crc:aaaa:1"), relativePath = "sub/a.sfc"))))
        val newRow = inserted.single()
        dao.add(rpgs, newRow.id)
        dao.add(hacks, newRow.id)
        scan.finishScan(ScanFinish(folderId, moves = listOf(GameMove(keptGameId = a, newRow = newRow))))

        assertEquals(
            listOf(Membership(rpgs, a, "crc:aaaa:1", "content://tree/roms\nsub/a.sfc"), Membership(hacks, a, "crc:aaaa:1", "content://tree/roms\nsub/a.sfc")),
            memberships(),
        )
        assertEquals(listOf(a), scan.gamesInFolder(folderId).map { it.id })
        assertEquals(listOf(1, 1), dao.observeCollections().first().map { it.gameCount })
    }

    @Test
    fun aFoldKeepsTheFoldedRowsMembershipsOnTheKeptRowOnce() = runTest {
        val ids = library.scanIn(game(folderId, "game.m3u"), game(folderId, "disc2.cue"))
        val kept = ids.getValue("game.m3u")
        val folded = ids.getValue("disc2.cue")
        val rpgs = dao.create("RPGs")
        val discs = dao.create("Discs")
        dao.add(rpgs, kept)
        dao.add(rpgs, folded)
        dao.add(discs, folded)
        scan.finishScan(ScanFinish(folderId, folds = listOf(GameFold(keptGameId = kept, foldedGameId = folded))))

        assertEquals(listOf(rpgs to kept, discs to kept), memberships().map { it.collectionId to it.gameId })
        assertEquals(listOf("game.m3u"), titles(rpgs))
        assertEquals(listOf("game.m3u"), titles(discs))
    }

    @Test
    fun aMoveOntoANewLaunchRowKeepsMembershipsOnce() = runTest {
        val ids = library.scanIn(game(folderId, "disc1.cue"))
        val old = ids.getValue("disc1.cue")
        val rpgs = dao.create("RPGs")
        val discs = dao.create("Discs")
        dao.add(rpgs, old)
        val launch = scan.applyBatch(ScanBatch(folderId, newGames = listOf(game(folderId, "game.m3u")))).single()
        dao.add(rpgs, launch.id)
        dao.add(discs, launch.id)
        scan.finishScan(ScanFinish(folderId, moves = listOf(GameMove(keptGameId = old, newRow = launch, ontoNewLaunchRow = true))))

        assertEquals(
            listOf(Membership(rpgs, old, null, "content://tree/roms\ngame.m3u"), Membership(discs, old, null, "content://tree/roms\ngame.m3u")),
            memberships(),
        )
    }

    @Test
    fun resetLibraryKeepsCollectionsAndAFreshScanBindsByContentKeyThenByPath() = runTest {
        val ids = library.scanIn(
            keyedGame(folderId, "a.sfc", listOf("code:SNS-A", "crc:aaaa:1")),
            keyedGame(folderId, "b.sfc"),
            keyedGame(folderId, "c.sfc", listOf("crc:cccc:1")),
        )
        val rpgs = dao.create("RPGs")
        for (id in ids.values) dao.add(rpgs, id)

        scan.forgetLibrary()
        assertEquals(emptyList<GameListRow>(), dao.observeCollectionGames(rpgs).first())
        assertEquals(listOf("RPGs"), dao.observeCollections().first().map { it.name })
        assertEquals(listOf(0), dao.observeCollections().first().map { it.gameCount })
        assertEquals("kept, unbound", listOf<Long?>(null, null, null), memberships().map { it.gameId })

        // The fresh scan: a.sfc now lives elsewhere (found by its crc:), b.sfc has no key (found by path),
        // c.sfc is not there (stays unbound).
        val fresh = library.scanIn(
            keyedGame(folderId, "moved-a.sfc", listOf("code:SNS-A", "crc:aaaa:1"), relativePath = "elsewhere/a.sfc", title = "a.sfc"),
            keyedGame(folderId, "b.sfc"),
        )
        assertEquals(listOf("a.sfc", "b.sfc"), titles(rpgs))
        val rows = memberships()
        assertEquals(listOf(fresh.getValue("moved-a.sfc"), fresh.getValue("b.sfc"), null), rows.map { it.gameId })
        assertEquals("content://tree/roms\nelsewhere/a.sfc", rows[0].pathKey)
        assertEquals(1, rows.count { it.gameId == null })
    }

    @Test
    fun aRemovedFileStaysUnboundAndUnlistedAndComesBackWithTheFile() = runTest {
        val ids = library.scanIn(keyedGame(folderId, "a.sfc"), keyedGame(folderId, "b.sfc"))
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, ids.getValue("a.sfc"))
        dao.add(rpgs, ids.getValue("b.sfc"))

        scan.finishScan(ScanFinish(folderId, removedGameIds = listOf(ids.getValue("a.sfc"))))
        assertEquals(listOf("b.sfc"), titles(rpgs))
        assertEquals(1, dao.observeCollections().first().single().gameCount)
        assertEquals(2, memberships().size)
        // Later scans without the file never delete the row.
        scan.finishScan(ScanFinish(folderId))
        assertEquals(listOf(null, ids.getValue("b.sfc")), memberships().map { it.gameId })

        val back = library.scanIn(keyedGame(folderId, "a.sfc"))
        assertEquals(listOf("a.sfc", "b.sfc"), titles(rpgs))
        assertEquals(listOf(back.getValue("a.sfc"), ids.getValue("b.sfc")), memberships().map { it.gameId })
    }

    @Test
    fun aRebindThatWouldPutAGameTwiceInACollectionLeavesTheRowUnbound() = runTest {
        val ids = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")), keyedGame(folderId, "copy-a.sfc", listOf("crc:aaaa:1")))
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, ids.getValue("a.sfc"))
        dao.add(rpgs, ids.getValue("copy-a.sfc"))
        scan.forgetLibrary()
        // Only one copy comes back: both rows match it by content key; one binds, the other waits.
        val fresh = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")))
        assertEquals(listOf(fresh.getValue("a.sfc"), null), memberships().map { it.gameId })
        assertEquals(listOf("a.sfc"), titles(rpgs))
    }

    @Test
    fun twoCopiesOfOneFileInOneCollectionBothComeBackToTheirOwnFilesAfterResetLibrary() = runTest {
        val ids = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")), keyedGame(folderId, "copy-a.sfc", listOf("crc:aaaa:1")))
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, ids.getValue("a.sfc"))
        dao.add(rpgs, ids.getValue("copy-a.sfc"))
        scan.forgetLibrary()
        val fresh = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")), keyedGame(folderId, "copy-a.sfc", listOf("crc:aaaa:1")))
        assertEquals(listOf(fresh.getValue("a.sfc"), fresh.getValue("copy-a.sfc")), memberships().map { it.gameId })
        assertEquals(listOf("content://tree/roms\na.sfc", "content://tree/roms\ncopy-a.sfc"), memberships().map { it.pathKey })
    }

    @Test
    fun twoCollectionsHoldingTwoRegionalCopiesOfOneFileEachKeepTheirOwnCopy() = runTest {
        val us = keyedGame(folderId, "us-a.sfc", listOf("crc:aaaa:1"), relativePath = "us/a.sfc", isPrimaryCopy = false)
        val eu = keyedGame(folderId, "eu-a.sfc", listOf("crc:aaaa:1"), relativePath = "eu/a.sfc")
        val ids = library.scanIn(us, eu)
        val x = dao.create("X")
        val y = dao.create("Y")
        dao.add(x, ids.getValue("us-a.sfc"))
        dao.add(y, ids.getValue("eu-a.sfc"))
        scan.forgetLibrary()
        val fresh = library.scanIn(us, eu)
        assertEquals(
            listOf(Membership(x, fresh.getValue("us-a.sfc"), "crc:aaaa:1", "content://tree/roms\nus/a.sfc"), Membership(y, fresh.getValue("eu-a.sfc"), "crc:aaaa:1", "content://tree/roms\neu/a.sfc")),
            memberships(),
        )
    }

    @Test
    fun aDuplicateInAnotherFolderScannedFirstDoesNotTakeTheMembership() = runTest {
        val other = ScannedLibrary(db, treeUri = "content://tree/other").also { it.pickFolder() }
        val ids = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")))
        other.scanIn(keyedGame(other.folderId, "dup-a.sfc", listOf("crc:aaaa:1")))
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, ids.getValue("a.sfc"))
        scan.forgetLibrary()

        val dup = other.scanIn(keyedGame(other.folderId, "dup-a.sfc", listOf("crc:aaaa:1")))
        assertEquals("the folder that held it is not scanned yet", listOf<Long?>(null), memberships().map { it.gameId })
        val fresh = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")))
        assertEquals(listOf(fresh.getValue("a.sfc")), memberships().map { it.gameId })
        assertTrue(dup.getValue("dup-a.sfc") != fresh.getValue("a.sfc"))
    }

    @Test
    fun aFileMovedToAnotherFolderIsFoundByContentOnceItsOldFolderWasScanned() = runTest {
        val other = ScannedLibrary(db, treeUri = "content://tree/other").also { it.pickFolder() }
        val ids = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")))
        scan.markScanFinished(folderId, 1)
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, ids.getValue("a.sfc"))
        // The old folder's scan finds the file gone; the new folder's scan finds it.
        scan.finishScan(ScanFinish(folderId, removedGameIds = listOf(ids.getValue("a.sfc"))))
        scan.markScanFinished(folderId, 2)
        val moved = other.scanIn(keyedGame(other.folderId, "a.sfc", listOf("crc:aaaa:1")))
        assertEquals(listOf(Membership(rpgs, moved.getValue("a.sfc"), "crc:aaaa:1", "content://tree/other\na.sfc")), memberships())
    }

    @Test
    fun aDifferentFileNowAtTheOldPathIsNotTakenForTheMembership() = runTest {
        val ids = library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:aaaa:1")))
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, ids.getValue("a.sfc"))
        scan.forgetLibrary()
        library.scanIn(keyedGame(folderId, "a.sfc", listOf("crc:bbbb:1")))
        assertEquals(listOf<Long?>(null), memberships().map { it.gameId })
    }

    @Test
    fun anAppGameUninstalledAndInstalledAgainComesBackToItsCollection() = runTest {
        val app = NewAppGame(packageName = "com.example.game", label = "Example", sortName = "example", systemId = "android", systemName = "Android")
        db.appGameDao().sync(listOf(app), now = 1)
        val firstId = checkNotNull(db.appGameDao().records().single().gameId)
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, firstId)
        assertEquals("plumemu:installed-apps\napp:com.example.game", memberships().single().pathKey)

        db.appGameDao().sync(emptyList(), now = 2)
        assertEquals(listOf<Long?>(null), memberships().map { it.gameId })
        db.appGameDao().sync(listOf(app), now = 3)
        assertEquals(listOf("Example"), titles(rpgs))

        scan.forgetLibrary()
        db.appGameDao().sync(listOf(app), now = 4)
        assertEquals("after Reset library too", listOf("Example"), titles(rpgs))
    }

    @Test
    fun aShortcutPinnedAgainAfterResetLibraryComesBackToItsCollection() = runTest {
        val pin = NewPinnedShortcut("com.example.steam", "game_42", 0, "Portal", "portal", "steam", "Steam", addedAt = 1)
        val first = db.pinnedShortcutDao().add(pin).gameId
        val rpgs = dao.create("RPGs")
        dao.add(rpgs, first)
        scan.forgetLibrary()
        assertEquals(emptyList<String>(), titles(rpgs))
        val again = db.pinnedShortcutDao().add(pin.copy(addedAt = 2)).gameId
        assertEquals(listOf(again), memberships().map { it.gameId })
        assertTrue(titles(rpgs) == listOf("Portal"))
    }
}
