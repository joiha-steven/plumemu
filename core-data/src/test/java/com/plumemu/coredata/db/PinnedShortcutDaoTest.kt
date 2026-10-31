package com.plumemu.coredata.db

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Pinned shortcuts as games: listed like any game, once, never scanned or scraped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PinnedShortcutDaoTest {
    private lateinit var db: PlumDatabase
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                systems = listOf(SystemEntity("steam", "Valve Steam")),
                newGames = listOf(game(folderId, "Celeste.steam", systemId = "steam", title = "Celeste")),
            ),
        )
        db.scanDao().finishScan(ScanFinish(folderId))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aShortcutGameIsListedInItsSystemSortedWithTheFilesAndCounted() = runTest {
        db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce"))
        db.pinnedShortcutDao().add(shortcut("game_1", "Ziggurat"))

        assertEquals(listOf("Broforce", "Celeste", "Ziggurat"), db.gameDao().observeSystemGames("steam").first().map { it.title })
        assertEquals(listOf(SystemGameCount("steam", "Valve Steam", 3)), db.gameDao().observeSystemsWithGames().first())
    }

    @Test
    fun aShortcutGameCanBeFavouritedHiddenAndShowsInRecentAfterPlay() = runTest {
        val id = db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce")).gameId

        db.gameDao().setFavorite(id, true)
        assertEquals(listOf("Broforce"), db.gameDao().observeFavorites().first().map { it.title })

        db.playDao().addSession(id, endedAt = 90_000, seconds = 60)
        assertEquals(listOf("Broforce"), db.gameDao().observeRecent(20).first().map { it.title })
        assertEquals(60L, db.playDao().secondsPlayedBetween(0, 100_000))

        db.gameDao().setHidden(id, true)
        assertEquals(listOf("Celeste"), db.gameDao().observeSystemGames("steam").first().map { it.title })
        assertEquals(listOf("Broforce"), db.gameDao().observeHiddenGames().first().map { it.title })
    }

    @Test
    fun pinningTheSameShortcutAgainKeepsOneGameWithItsMarksAndShowsItAgain() = runTest {
        val first = db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce"))
        db.gameDao().setFavorite(first.gameId, true)
        db.gameDao().setHidden(first.gameId, true)
        db.pinnedShortcutDao().setAvailable(first.gameId, false)

        val again = db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce!"))

        assertTrue(first.isNew)
        assertEquals(PinnedShortcutAdded(first.gameId, isNew = false), again)
        val steam = db.gameDao().observeSystemGames("steam").first()
        assertEquals(listOf("Broforce", "Celeste"), steam.map { it.title })
        assertTrue("the favourite is kept", steam.first().isFavorite)
        val record = db.pinnedShortcutDao().byGame(first.gameId)!!
        assertEquals("Broforce!", record.label)
        assertTrue(record.isAvailable)
    }

    @Test
    fun pinningAgainFilesTheGameAgain() = runTest {
        val first = db.pinnedShortcutDao().add(shortcut("game_1", "Broforce", systemId = "shortcuts", systemName = "Shortcuts"))
        db.gameDao().setFavorite(first.gameId, true)

        val again = db.pinnedShortcutDao().add(shortcut("game_1", "Broforce"))

        assertEquals(first.gameId, again.gameId)
        assertEquals(listOf("Broforce", "Celeste"), db.gameDao().observeSystemGames("steam").first().map { it.title })
        assertEquals(emptyList<GameListRow>(), db.gameDao().observeSystemGames("shortcuts").first())
        assertTrue("its favourite moves with it", db.gameDao().game(first.gameId)!!.isFavorite)
    }

    @Test
    fun twoAcceptsOfOnePinAtOnceMakeOneGame() = runTest {
        val results = List(4) { async { db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce")) } }.awaitAll()

        assertEquals(1, results.map { it.gameId }.distinct().size)
        assertEquals(1, results.count { it.isNew })
        assertEquals(2, db.gameDao().observeSystemGames("steam").first().size)
    }

    @Test
    fun theSameIdFromAnotherAppOrUserIsAnotherGame() = runTest {
        val gameNative = db.pinnedShortcutDao().add(shortcut("game_1", "Broforce"))
        val droidDeck = db.pinnedShortcutDao().add(shortcut("game_1", "Broforce", packageName = "com.droiddeck.launcher"))
        val workProfile = db.pinnedShortcutDao().add(shortcut("game_1", "Broforce", userSerial = 10))

        assertEquals(3, listOf(gameNative, droidDeck, workProfile).map { it.gameId }.distinct().size)
        assertEquals(listOf(gameNative.gameId), db.pinnedShortcutDao().keysOf("app.gamenative", 0).map { it.gameId })
    }

    @Test
    fun theReservedFolderIsNeverAPickedFolderAndAScanLeavesItsGamesAlone() = runTest {
        val id = db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce")).gameId

        assertEquals(listOf(folderId), db.scanDao().folders().map { it.id })
        assertEquals(listOf(folderId), db.scanDao().observeFolders().first().map { it.id })
        // A rescan of the picked folder that finds nothing new does not touch the shortcut.
        db.scanDao().finishScan(ScanFinish(folderId))
        assertEquals("Broforce", db.gameDao().game(id)?.title)
    }

    // A shortcut under `steam` (DroidDeck's games) is asked by name
    // like a `.steam` file; every other shortcut is never scraped.
    @Test
    fun aSteamShortcutIsScrapedByNameButNoShortcutIsEverUnidentifiedOrIdentified() = runTest {
        val steam = db.pinnedShortcutDao().add(shortcut("game_1", "Hades")).gameId
        val windows = db.pinnedShortcutDao().add(shortcut("game_2", "Broforce", systemId = "windows", systemName = "Microsoft Windows")).gameId
        val other = db.pinnedShortcutDao().add(shortcut("game_3", "Ziggurat", systemId = "shortcuts", systemName = "Shortcuts")).gameId

        assertEquals(emptyList<HiddenGameRow>(), db.identifyDao().observeUnidentified(emptyList()).first())
        listOf(steam, windows, other).forEach { assertFalse(db.gameDao().game(it)!!.needsIdentify) }
        // The file's game as the identification pass leaves it: named, so a run may ask for it.
        db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 0")
        val pending = db.scrapedGameDao().pending(retryBefore = Long.MAX_VALUE, currentSystemId = null, limit = 10)
        assertEquals("the file's game and the steam shortcut are asked", listOf("Celeste", "Hades"), pending.map { it.title }.sorted())
        assertEquals("a shortcut is asked under its system", "steam", pending.single { it.title == "Hades" }.systemId)
        assertEquals(2, db.scrapedGameDao().countPending(Long.MAX_VALUE))
    }

    @Test
    fun backToAutomaticNeverMarksAShortcutGameToBeIdentified() = runTest {
        val id = db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce")).gameId
        assertTrue(db.identifyDao().setFix(id, NameFixKind.OWN_NAME, "My Broforce", "my broforce", null, fixedAt = 5))

        assertTrue(db.identifyDao().backToAutomatic(id))

        val game = db.gameDao().game(id)!!
        assertFalse("nothing would ever clear it", game.needsIdentify)
        assertEquals("Broforce", game.title)
    }

    @Test
    fun fixBoxArtCannotAskForAShortcutGame() = runTest {
        val id = db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce")).gameId
        assertNull(db.scrapedGameDao().toScrape(id))
    }

    @Test
    fun boxArtCountsMatchWhatARunAsksAndTheLibraryCountsEveryShortcut() = runTest {
        val steam = db.pinnedShortcutDao().add(shortcut("game_1", "Hades")).gameId
        val windows = db.pinnedShortcutDao().add(shortcut("game_2", "Broforce", systemId = "windows", systemName = "Microsoft Windows")).gameId
        listOf(steam, windows).forEach { id ->
            db.scrapedGameDao().insertOrReplace(
                ScrapedGameEntity(
                    scrapeKey = NameFixEntity.rowKey(id), gameId = id, outcome = ScrapeOutcome.MATCHED, screenScraperId = "1", matchedBy = null,
                    scrapedAt = 1, detailsJson = null, title = null, year = null, publisher = null, players = null, genre = null, hasBoxFront = true,
                ),
            )
        }

        assertEquals(3, db.scrapedGameDao().countListed())
        assertEquals("Celeste and the steam shortcut; the windows one is not a run's", 2, db.scrapedGameDao().countListedForBoxArt())
        assertEquals(1, db.scrapedGameDao().countWithBoxFront())
    }

    @Test
    fun theRecordGoesWithItsGame() = runTest {
        val id = db.pinnedShortcutDao().add(shortcut("game_274190", "Broforce")).gameId
        db.scanDao().finishScan(ScanFinish(folderId, removedGameIds = listOf(id)))

        assertNull(db.pinnedShortcutDao().byGame(id))
        assertEquals(emptyList<PinnedShortcutKey>(), db.pinnedShortcutDao().keys())
    }

    @Test
    fun aGameFromARomFolderHasNoRecord() = runTest {
        val fileGame = db.gameDao().observeSystemGames("steam").first().single().id
        assertNull(db.pinnedShortcutDao().byGame(fileGame))
    }

    private fun shortcut(
        id: String,
        label: String,
        packageName: String = "app.gamenative",
        userSerial: Long = 0,
        systemId: String = "steam",
        systemName: String = "Valve Steam",
    ) = NewPinnedShortcut(
        packageName = packageName,
        shortcutId = id,
        userSerial = userSerial,
        label = label,
        sortName = label.lowercase(),
        systemId = systemId,
        systemName = systemName,
        addedAt = 1_000,
    )
}
