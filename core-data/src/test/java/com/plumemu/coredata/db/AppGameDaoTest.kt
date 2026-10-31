package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Installed apps as games: synced from the installed list, listed like any game, never scanned or scraped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppGameDaoTest {
    private lateinit var db: PlumDatabase
    private var folderId = 0L
    private var scannedId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                systems = listOf(SystemEntity("ports", "Ports")),
                newGames = listOf(game(folderId, "Quake.port", systemId = "ports", title = "Quake")),
            ),
        )
        db.scanDao().finishScan(ScanFinish(folderId))
        scannedId = db.scanDao().gamesInFolder(folderId).single().id
    }

    @After
    fun tearDown() = db.close()

    private fun app(pkg: String, label: String, systemId: String = "android") =
        NewAppGame(pkg, label, label.lowercase(), systemId, if (systemId == "android") "Android games" else "Ports")

    private suspend fun gameIdOf(pkg: String) = db.appGameDao().records().single { it.packageName == pkg }.gameId

    @Test
    fun syncAddsAppGamesListedInTheirSystems() = runTest {
        val result = db.appGameDao().sync(listOf(app("com.a.genshin", "Genshin"), app("com.harbourmasters.soh", "Ship of Harkinian", "ports")), now = 5)

        assertEquals(AppGameSync(added = 2, updated = 0, removed = 0), result)
        assertEquals(listOf("Genshin"), db.gameDao().observeSystemGames("android").first().map { it.title })
        assertEquals(listOf("Quake", "Ship of Harkinian"), db.gameDao().observeSystemGames("ports").first().map { it.title })
        val id = gameIdOf("com.a.genshin")
        assertEquals("com.a.genshin", db.appGameDao().byGame(id))
        assertTrue(db.appGameDao().isAppGameRow(id))
        assertFalse("not a pinned shortcut", db.pinnedShortcutDao().isShortcutRow(id))
    }

    @Test
    fun syncAgainUpdatesTitleAndSystemAndKeepsFavouritePlayTimeAndHidden() = runTest {
        db.appGameDao().sync(listOf(app("com.a.soh", "SoH")), now = 5)
        val id = gameIdOf("com.a.soh")
        db.gameDao().setFavorite(id, true)
        db.gameDao().setHidden(id, true)
        db.playDao().addSession(id, endedAt = 90_000, seconds = 60)

        val result = db.appGameDao().sync(listOf(app("com.a.soh", "Ship of Harkinian", "ports")), now = 6)

        assertEquals(AppGameSync(added = 0, updated = 1, removed = 0), result)
        assertEquals("the game id is kept", id, gameIdOf("com.a.soh"))
        val row = db.gameDao().game(id)!!
        assertEquals("Ship of Harkinian", row.title)
        assertEquals("ship of harkinian", row.sortName)
        assertEquals("ports", row.systemId)
        assertTrue(row.isFavorite)
        assertTrue("a hidden app game stays hidden", row.isHidden)
        assertEquals(60L, db.playDao().play(id)?.totalSeconds)
    }

    @Test
    fun aNameThePersonGaveSurvivesSyncAndCountsNoUpdate() = runTest {
        db.appGameDao().sync(listOf(app("com.a.soh", "SoH")), now = 5)
        val id = gameIdOf("com.a.soh")
        assertTrue(db.identifyDao().setFix(id, NameFixKind.OWN_NAME, "Ship of Harkinian", "ship of harkinian", null, fixedAt = 5))

        assertEquals(AppGameSync(0, 0, 0), db.appGameDao().sync(listOf(app("com.a.soh", "SoH")), now = 6))
        assertEquals("Ship of Harkinian", db.gameDao().game(id)!!.title)

        // A new label is stored as the file name; the person's name stays shown.
        assertEquals(AppGameSync(0, 1, 0), db.appGameDao().sync(listOf(app("com.a.soh", "SoH 2", "ports")), now = 7))
        val row = db.gameDao().game(id)!!
        assertEquals("Ship of Harkinian", row.title)
        assertEquals("ship of harkinian", row.sortName)
        assertEquals("SoH 2", row.fileTitle)
        assertEquals("ports", row.systemId)
    }

    @Test
    fun syncWithNothingChangedCountsNoUpdate() = runTest {
        db.appGameDao().sync(listOf(app("com.a.game", "Game")), now = 5)
        assertEquals(AppGameSync(0, 0, 0), db.appGameDao().sync(listOf(app("com.a.game", "Game")), now = 6))
    }

    @Test
    fun syncRemovesPackagesNoLongerListed() = runTest {
        db.appGameDao().sync(listOf(app("com.a.one", "One"), app("com.a.two", "Two")), now = 5)
        val two = gameIdOf("com.a.two")

        val result = db.appGameDao().sync(listOf(app("com.a.one", "One")), now = 6)

        assertEquals(AppGameSync(added = 0, updated = 0, removed = 1), result)
        assertNull(db.gameDao().game(two))
        assertNull(db.appGameDao().byGame(two))
        assertEquals(listOf("One"), db.gameDao().observeSystemGames("android").first().map { it.title })
    }

    @Test
    fun aKeptPackageMissingFromTheListKeepsItsGameFavouriteAndPlayTime() = runTest {
        db.appGameDao().sync(listOf(app("com.a.one", "One"), app("com.a.two", "Two")), now = 5)
        val two = gameIdOf("com.a.two")
        db.gameDao().setFavorite(two, true)
        db.playDao().addSession(two, endedAt = 90_000, seconds = 60)

        val result = db.appGameDao().sync(listOf(app("com.a.one", "One")), now = 6, keep = setOf("com.a.two"))

        assertEquals(AppGameSync(added = 0, updated = 0, removed = 0), result)
        assertEquals(two, gameIdOf("com.a.two"))
        assertTrue(db.gameDao().game(two)!!.isFavorite)
        assertEquals(60L, db.playDao().play(two)?.totalSeconds)

        val again = db.appGameDao().sync(listOf(app("com.a.one", "One")), now = 7, keep = setOf("com.a.two", "com.a.new"))
        assertEquals("keep never adds a package", AppGameSync(0, 0, 0), again)
        assertEquals(listOf("com.a.one", "com.a.two"), db.appGameDao().records().map { it.packageName }.sorted())
    }

    @Test
    fun syncWithAnEmptyListRemovesEveryAppGameButNotShortcutsOrScannedGames() = runTest {
        db.appGameDao().sync(listOf(app("com.a.one", "One"), app("com.a.two", "Two", "ports")), now = 5)
        val shortcut = db.pinnedShortcutDao().add(
            NewPinnedShortcut("app.gamenative", "game_1", 0, "Broforce", "broforce", "steam", "Valve Steam", addedAt = 5),
        ).gameId

        assertEquals(AppGameSync(added = 0, updated = 0, removed = 2), db.appGameDao().sync(emptyList(), now = 6))

        assertEquals(emptyList<AppGameEntity>(), db.appGameDao().records())
        assertNotNull(db.gameDao().game(shortcut))
        assertNotNull(db.gameDao().game(scannedId))
        assertEquals(listOf("Quake"), db.gameDao().observeSystemGames("ports").first().map { it.title })
    }

    @Test
    fun aPackageGivenTwiceIsListedOnce() = runTest {
        val result = db.appGameDao().sync(listOf(app("com.a.one", "One"), app("com.a.one", "One again")), now = 5)
        assertEquals(1, result.added)
        assertEquals(listOf("One again"), db.gameDao().observeSystemGames("android").first().map { it.title })
    }

    @Test
    fun theReservedFolderIsNeverAPickedFolderNorAnUnknownOrAnsweredFolder() = runTest {
        db.appGameDao().sync(listOf(app("com.a.one", "One")), now = 5)
        // As if something had written directory rows under the reserved folder: still never offered.
        val reserved = db.scanDao().folderByTreeUri(AppGameDao.FOLDER_TREE_URI)!!.id
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO scanned_directory (folder_id, document_id, relative_path, last_modified, unknown_files) VALUES ($reserved, 'd', 'odd', 1, 3)")
        sql.execSQL("INSERT INTO folder_system (folder_id, relative_path, system_id, chosen_at) VALUES ($reserved, 'odd', 'snes', 1)")

        assertEquals(listOf(folderId), db.scanDao().folders().map { it.id })
        assertEquals(listOf(folderId), db.scanDao().observeFolders().first().map { it.id })
        assertEquals(emptyList<UnknownFolderRow>(), db.folderSystemDao().observeUnknownFolders().first())
        assertEquals(emptyList<AnsweredFolderRow>(), db.folderSystemDao().observeAnsweredFolders().first())
    }

    @Test
    fun anAppGameIsNeverIdentifiedNorOfferedToBoxArtRunsNorCounted() = runTest {
        db.appGameDao().sync(listOf(app("com.a.one", "One", "ports")), now = 5)
        val id = gameIdOf("com.a.one")

        assertFalse(db.gameDao().game(id)!!.needsIdentify)
        db.identifyDao().markSystemsToNameAgain(listOf("ports"))
        assertFalse("nothing would ever clear the mark", db.gameDao().game(id)!!.needsIdentify)
        db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 0")
        val pending = db.scrapedGameDao().pending(retryBefore = Long.MAX_VALUE, currentSystemId = null, limit = 10)
        assertEquals("only the file's game is asked", listOf("Quake"), pending.map { it.title })
        assertEquals(1, db.scrapedGameDao().countPending(Long.MAX_VALUE))
        assertNull(db.scrapedGameDao().toScrape(id))
        assertEquals(2, db.scrapedGameDao().countListed())
        assertEquals(1, db.scrapedGameDao().countListedForBoxArt())
    }

    @Test
    fun backToAutomaticNeverMarksAnAppGameToBeIdentified() = runTest {
        db.appGameDao().sync(listOf(app("com.a.one", "One")), now = 5)
        val id = gameIdOf("com.a.one")
        assertTrue(db.identifyDao().setFix(id, NameFixKind.OWN_NAME, "Mine", "mine", null, fixedAt = 5))

        assertTrue(db.identifyDao().backToAutomatic(id))

        val row = db.gameDao().game(id)!!
        assertFalse(row.needsIdentify)
        assertEquals("One", row.title)
    }

    @Test
    fun aScannedGameOrAShortcutIsNotAnAppGameRow() = runTest {
        val shortcut = db.pinnedShortcutDao().add(
            NewPinnedShortcut("app.gamenative", "game_1", 0, "Broforce", "broforce", "steam", "Valve Steam", addedAt = 5),
        ).gameId
        assertFalse(db.appGameDao().isAppGameRow(scannedId))
        assertFalse(db.appGameDao().isAppGameRow(shortcut))
        assertNull(db.appGameDao().byGame(scannedId))
    }

    @Test
    fun choicesAreStoredReplacedAndCleared() = runTest {
        val dao = db.appGameDao()
        dao.setChoice("com.a.one", AppKind.GAME, now = 1)
        dao.setChoice("com.a.two", AppKind.PORT, now = 2)
        dao.setChoice("com.a.one", AppKind.APP, now = 3)

        assertEquals(mapOf("com.a.one" to AppKind.APP, "com.a.two" to AppKind.PORT), dao.choices())
        assertEquals(dao.choices(), dao.observeChoices().first())

        dao.clearChoice("com.a.two")
        assertEquals(mapOf("com.a.one" to AppKind.APP), dao.observeChoices().first())
    }

    @Test
    fun anUnknownStoredKindReadsAsNoChoice() = runTest {
        db.appGameDao().setChoice("com.a.one", AppKind.GAME, now = 1)
        db.openHelper.writableDatabase.execSQL("INSERT INTO app_kind_choice (package_name, kind, chosen_at) VALUES ('com.a.two', 'emulator', 2)")

        assertEquals(mapOf("com.a.one" to AppKind.GAME), db.appGameDao().choices())
        assertEquals(mapOf("com.a.one" to AppKind.GAME), db.appGameDao().observeChoices().first())
    }

    @Test
    fun resetLibraryRemovesAppGamesButKeepsThePersonsChoices() = runTest {
        db.appGameDao().sync(listOf(app("com.a.one", "One")), now = 5)
        db.appGameDao().setChoice("com.a.one", AppKind.GAME, now = 1)

        db.scanDao().forgetLibrary()

        assertEquals(emptyList<AppGameEntity>(), db.appGameDao().records())
        assertEquals(mapOf("com.a.one" to AppKind.GAME), db.appGameDao().choices())
        // The next sync lists it again.
        assertEquals(1, db.appGameDao().sync(listOf(app("com.a.one", "One")), now = 6).added)
    }
}
