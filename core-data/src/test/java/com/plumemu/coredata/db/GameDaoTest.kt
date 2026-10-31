package com.plumemu.coredata.db

import com.plumemu.coredata.settings.SortGamesSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameDaoTest {
    private lateinit var db: PlumDatabase
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                systems = listOf(SystemEntity("snes", "Super Nintendo"), SystemEntity("nes", "NES")),
                newGames = listOf(
                    game(folderId, "z", title = "Zelda"),
                    game(folderId, "a", title = "Actraiser"),
                    game(folderId, "m", title = "Mario"),
                    game(folderId, "m-eur", title = "Mario", isPrimaryCopy = false),
                    game(folderId, "n", systemId = "nes", title = "Metroid"),
                ),
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    private companion object {
        const val INVALIDATION_SETTLE_MS = 300L
    }

    private suspend fun idOf(documentId: String) =
        db.scanDao().gamesInFolder(folderId).single { it.documentId == documentId }.id

    @Test
    fun launchTargetJoinsTheGameWithItsPickedFolder() = runTest {
        val target = db.gameDao().launchTarget(idOf("z"))
        assertEquals(GameLaunchTarget(idOf("z"), "snes", "z", "z", "t"), target)
    }

    @Test
    fun launchTargetOfAGameThatIsGoneIsNull() = runTest {
        assertEquals(null, db.gameDao().launchTarget(9_999))
    }

    @Test
    fun listRowsCarryTheSortNameTheListIsOrderedBy() = runTest {
        // The home screen's letter rail files a game under the first letter of this, so it must be the ordering key itself.
        val rows = db.gameDao().observeSystemGames("snes").first()
        assertEquals(listOf("actraiser", "mario", "zelda"), rows.map { it.sortName })
        assertEquals(rows.map { it.sortName }, rows.map { it.sortName }.sorted())
        assertEquals(listOf("metroid"), db.gameDao().observeSystemGames("nes").first().map { it.sortName })
    }

    @Test
    fun systemListIsSortedAndShowsOneCopyPerRegionalGroup() = runTest {
        val rows = db.gameDao().observeSystemGames("snes").first()
        assertEquals(listOf("Actraiser", "Mario", "Zelda"), rows.map { it.title })
    }

    @Test
    fun hiddenGamesLeaveTheListAndFavoritesFollowTheMark() = runTest {
        db.gameDao().setHidden(idOf("a"), true)
        db.gameDao().setFavorite(idOf("z"), true)
        db.gameDao().setFavorite(idOf("n"), true)
        assertEquals(listOf("Mario", "Zelda"), db.gameDao().observeSystemGames("snes").first().map { it.title })
        assertEquals(listOf("Metroid", "Zelda"), db.gameDao().observeFavorites().first().map { it.title })
    }

    @Test
    fun recentIsNewestFirstAndAddsUpPlayTime() = runTest {
        db.playDao().addSession(idOf("a"), endedAt = 100, seconds = 30)
        db.playDao().addSession(idOf("z"), endedAt = 200, seconds = 40)
        db.playDao().addSession(idOf("a"), endedAt = 300, seconds = 50)
        assertEquals(listOf("Actraiser", "Zelda"), db.gameDao().observeRecent(10).first().map { it.title })
        assertEquals(80L, db.playDao().play(idOf("a"))?.totalSeconds)
    }

    @Test
    fun byLastPlayedPutsTheNewestFirstAndNeverPlayedAfterByName() = runTest {
        db.playDao().addSession(idOf("z"), endedAt = 100, seconds = 30)
        db.playDao().addSession(idOf("m"), endedAt = 200, seconds = 30)
        val snes = db.gameDao().observeSystemGames("snes", SortGamesSetting.LastPlayed).first()
        assertEquals(listOf("Mario", "Zelda", "Actraiser"), snes.map { it.title })
        assertEquals(listOf("Actraiser", "Mario", "Zelda"), db.gameDao().observeSystemGames("snes", SortGamesSetting.Name).first().map { it.title })

        db.gameDao().setFavorite(idOf("a"), true)
        db.gameDao().setFavorite(idOf("z"), true)
        assertEquals(listOf("Zelda", "Actraiser"), db.gameDao().observeFavorites(SortGamesSetting.LastPlayed).first().map { it.title })
        assertEquals(listOf("Actraiser", "Zelda"), db.gameDao().observeFavorites(SortGamesSetting.Name).first().map { it.title })
    }

    private suspend fun scrapedYear(documentId: String, year: Int?) {
        val id = idOf(documentId)
        db.scrapedGameDao().insertOrReplace(
            ScrapedGameEntity(
                scrapeKey = NameFixEntity.rowKey(id), gameId = id, outcome = ScrapeOutcome.MATCHED, screenScraperId = "1", matchedBy = ScrapeMatchedBy.HASH,
                scrapedAt = 0, detailsJson = "{}", title = null, year = year, publisher = null, players = null, genre = null,
            ),
        )
    }

    @Test
    fun byYearPutsTheOldestFirstAndGamesWithNoYearAfterByName() = runTest {
        // Mario has a row with no year, Zelda 1991, Actraiser 1990 and Mario's other copy is not listed.
        scrapedYear("z", 1991)
        scrapedYear("a", 1990)
        scrapedYear("m", null)
        assertEquals(listOf("Actraiser", "Zelda", "Mario"), db.gameDao().observeSystemGames("snes", SortGamesSetting.Year).first().map { it.title })

        // Two games of one year, then by name; a year of 0 is no year.
        scrapedYear("m", 1991)
        assertEquals(listOf("Actraiser", "Mario", "Zelda"), db.gameDao().observeSystemGames("snes", SortGamesSetting.Year).first().map { it.title })
        scrapedYear("m", 0)
        assertEquals(listOf("Actraiser", "Zelda", "Mario"), db.gameDao().observeSystemGames("snes", SortGamesSetting.Year).first().map { it.title })

        db.gameDao().setFavorite(idOf("m"), true)
        db.gameDao().setFavorite(idOf("z"), true)
        db.gameDao().setFavorite(idOf("n"), true)
        assertEquals("Metroid has no scraped row at all", listOf("Zelda", "Mario", "Metroid"), db.gameDao().observeFavorites(SortGamesSetting.Year).first().map { it.title })
    }

    @Test
    fun byYearWithNothingScrapedIsByName() = runTest {
        assertEquals(listOf("Actraiser", "Mario", "Zelda"), db.gameDao().observeSystemGames("snes", SortGamesSetting.Year).first().map { it.title })
    }

    @Test
    fun recentLeavesAHiddenGameAndAGameThatIsGone() = runTest {
        db.playDao().addSession(idOf("a"), endedAt = 100, seconds = 30)
        db.playDao().addSession(idOf("z"), endedAt = 200, seconds = 30)
        db.playDao().addSession(idOf("n"), endedAt = 300, seconds = 30)
        db.gameDao().setHidden(idOf("z"), true)
        db.openHelper.writableDatabase.execSQL("DELETE FROM game WHERE id = ${idOf("n")}")
        assertEquals(listOf("Actraiser"), db.gameDao().observeRecent(10).first().map { it.title })
    }

    @Test
    fun everyListRowCarriesItsSystemId() = runTest {
        db.playDao().addSession(idOf("n"), endedAt = 100, seconds = 30)
        db.gameDao().setFavorite(idOf("n"), true)
        assertEquals(listOf("nes"), db.gameDao().observeRecent(10).first().map { it.systemId })
        assertEquals(listOf("nes"), db.gameDao().observeFavorites().first().map { it.systemId })
        assertEquals(listOf("snes"), db.gameDao().observeSystemGames("snes").first().map { it.systemId }.distinct())
        assertEquals(listOf("nes"), db.gameDao().observeFavorites(SortGamesSetting.LastPlayed).first().map { it.systemId })
        assertEquals(listOf("snes"), db.gameDao().observeSystemGames("snes", SortGamesSetting.LastPlayed).first().map { it.systemId }.distinct())
    }

    @Test
    fun hiddenGamesAreListedAndUnhidingReturnsThemToTheirLists() = runTest {
        assertEquals(emptyList<HiddenGameRow>(), db.gameDao().observeHiddenGames().first())
        db.gameDao().setHidden(idOf("z"), true)
        db.gameDao().setHidden(idOf("n"), true)
        assertEquals(
            listOf(HiddenGameRow(idOf("n"), "Metroid", "", "nes"), HiddenGameRow(idOf("z"), "Zelda", "", "snes")),
            db.gameDao().observeHiddenGames().first(),
        )
        db.gameDao().setHidden(idOf("z"), false)
        assertEquals(listOf("Metroid"), db.gameDao().observeHiddenGames().first().map { it.title })
        assertEquals(listOf("Actraiser", "Mario", "Zelda"), db.gameDao().observeSystemGames("snes").first().map { it.title })
    }

    @Test
    fun hiddenGamesListOneRowPerArcadeGameAndEveryConsoleCopy() = runTest {
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                listOf(SystemEntity("arcade", "Arcade")),
                listOf(
                    game(folderId, "dinou", systemId = "arcade", title = "Cadillacs and Dinosaurs"),
                    game(folderId, "dino", systemId = "arcade", title = "Cadillacs and Dinosaurs", isPrimaryCopy = false),
                ),
            ),
        )
        db.openHelper.writableDatabase.execSQL("UPDATE game SET id_method = 'ARCADE_NAME' WHERE system_id = 'arcade'")
        for (id in listOf("dinou", "dino", "m", "m-eur")) db.gameDao().setHidden(idOf(id), true)
        assertEquals(
            "the arcade game once (its shown copy), both console copies as before",
            listOf(idOf("dinou"), idOf("m"), idOf("m-eur")).sorted(),
            db.gameDao().observeHiddenGames().first().map { it.id }.sorted(),
        )
    }

    @Test
    fun aFavoriteThatIsNoLongerThePrimaryCopyStaysInFavoritesOnly() = runTest {
        // A rescan may make another regional copy the primary one; the person's mark stays where it was.
        db.gameDao().setFavorite(idOf("m-eur"), true)
        assertEquals(listOf(idOf("m-eur")), db.gameDao().observeFavorites().first().map { it.id })
        assertEquals("the system list shows one copy, the primary", listOf(idOf("a"), idOf("m"), idOf("z")), db.gameDao().observeSystemGames("snes").first().map { it.id })
        db.gameDao().setFavorite(idOf("m-eur"), false)
        assertEquals(emptyList<GameListRow>(), db.gameDao().observeFavorites().first())
    }

    @Test
    fun aFavoriteSurvivesARescanThatRewritesTheGame() = runTest {
        db.gameDao().setFavorite(idOf("z"), true)
        val stored = db.scanDao().gamesInDirectory(folderId, "root").single { it.fields.documentId == "z" }
        db.scanDao().applyBatch(ScanBatch(folderId, changedGames = listOf(stored.copy(fields = stored.fields.copy(title = "Zelda II")))))
        assertEquals(listOf("Zelda II"), db.gameDao().observeFavorites().first().map { it.title })
        db.gameDao().setFavorite(idOf("z"), false)
        assertEquals(emptyList<GameListRow>(), db.gameDao().observeFavorites().first())
    }

    @Test
    fun systemsWithGamesCountOnlyVisiblePrimaryCopies() = runTest {
        val counts = db.gameDao().observeSystemsWithGames().first()
        assertEquals(listOf(SystemGameCount("nes", "NES", 1), SystemGameCount("snes", "Super Nintendo", 3)), counts)
    }

    @Test
    fun folderCountsAreVisiblePrimaryCopiesAndTheirSystemsPerFolder() = runTest {
        val second = db.scanDao().insertFolder(RomFolderEntity(treeUri = "sd", displayName = "Games", addedAt = 0))
        db.scanDao().insertFolder(RomFolderEntity(treeUri = "empty", displayName = "Empty", addedAt = 0))
        db.scanDao().applyBatch(ScanBatch(second, newGames = listOf(game(second, "g", systemId = "nes", title = "Gradius"))))
        db.gameDao().setHidden(idOf("a"), true)
        val counts = db.gameDao().observeFolderCounts().first().sortedBy { it.folderId }
        // First folder: Zelda, Mario (one copy) and Metroid on two systems; Actraiser is hidden.
        assertEquals(listOf(FolderGameCount(folderId, 3, 2), FolderGameCount(second, 1, 1)), counts)
    }

    @Test
    fun aWriteToAnotherSystemDoesNotReEmitThisSystemsList() = runBlocking {
        val emissions = CopyOnWriteArrayList<List<GameListRow>>()
        val job = launch(Dispatchers.Default) { db.gameDao().observeSystemGames("snes").collect { emissions += it } }
        waitUntil { emissions.size == 1 }
        db.scanDao().applyBatch(ScanBatch(folderId, newGames = listOf(game(folderId, "n2", systemId = "nes", title = "Zelda II"))))
        db.gameDao().observeSystemGames("nes").first { it.size == 2 }
        Thread.sleep(INVALIDATION_SETTLE_MS) // Room re-runs the snes query on its own thread.
        db.scanDao().applyBatch(ScanBatch(folderId, newGames = listOf(game(folderId, "s4", title = "Starfox"))))
        waitUntil { emissions.last().size == 4 }
        job.cancel()
        assertEquals(2, emissions.size)
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in 5 s" }
            Thread.sleep(10)
        }
    }

    @Test
    fun emptyLibraryGivesEmptyLists() = runTest {
        db.scanDao().deleteFolder(folderId)
        assertEquals(emptyList<GameListRow>(), db.gameDao().observeSystemGames("snes").first())
        assertEquals(emptyList<SystemGameCount>(), db.gameDao().observeSystemsWithGames().first())
    }
}
