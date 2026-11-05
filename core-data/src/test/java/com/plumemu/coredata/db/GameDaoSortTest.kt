package com.plumemu.coredata.db

import com.plumemu.coredata.settings.SortGamesSetting
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Most played and Recently added (and the year check that hides Year), with their ties and missing values. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameDaoSortTest {
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

    private suspend fun idOf(documentId: String) = db.scanDao().gamesInFolder(folderId).single { it.documentId == documentId }.id

    private suspend fun snes(sort: SortGamesSetting) = db.gameDao().observeSystemGames("snes", sort).first().map { it.title }

    @Test
    fun mostPlayedPutsTheMostSecondsFirstAndTiesByName() = runTest {
        db.playDao().addSession(idOf("z"), endedAt = 100, seconds = 600)
        db.playDao().addSession(idOf("m"), endedAt = 200, seconds = 600)
        db.playDao().addSession(idOf("a"), endedAt = 300, seconds = 30)
        assertEquals("Mario and Zelda tie on time: by name; Actraiser has less", listOf("Mario", "Zelda", "Actraiser"), snes(SortGamesSetting.MostPlayed))
    }

    @Test
    fun mostPlayedLeavesGamesWithNoPlayTimeAfterByName() = runTest {
        db.playDao().addSession(idOf("z"), endedAt = 100, seconds = 60)
        // A play row of 0 seconds counts as no play time, not as the least played of the played games.
        db.playDao().addSession(idOf("m"), endedAt = 200, seconds = 0)
        assertEquals(listOf("Zelda", "Actraiser", "Mario"), snes(SortGamesSetting.MostPlayed))
        assertEquals("Nothing played: by name", listOf("Metroid"), db.gameDao().observeSystemGames("nes", SortGamesSetting.MostPlayed).first().map { it.title })
    }

    @Test
    fun mostPlayedOrdersFavoritesToo() = runTest {
        db.playDao().addSession(idOf("a"), endedAt = 100, seconds = 90)
        db.playDao().addSession(idOf("n"), endedAt = 100, seconds = 900)
        listOf("a", "z", "n").forEach { db.gameDao().setFavorite(idOf(it), true) }
        assertEquals(listOf("Metroid", "Actraiser", "Zelda"), db.gameDao().observeFavorites(SortGamesSetting.MostPlayed).first().map { it.title })
    }

    @Test
    fun recentlyAddedPutsTheNewestEntryFirstAndALaterBatchAboveTheEarlier() = runTest {
        // The setUp batch entered z, a, m: newest is Mario, then Actraiser, then Zelda.
        assertEquals(listOf("Mario", "Actraiser", "Zelda"), snes(SortGamesSetting.RecentlyAdded))
        db.scanDao().applyBatch(ScanBatch(folderId, systems = emptyList(), newGames = listOf(game(folderId, "b", title = "Bubble"))))
        assertEquals(listOf("Bubble", "Mario", "Actraiser", "Zelda"), snes(SortGamesSetting.RecentlyAdded))
        // Hiding and showing again keeps the entry's place (rows are updated in place).
        db.gameDao().setHidden(idOf("m"), true)
        db.gameDao().setHidden(idOf("m"), false)
        assertEquals(listOf("Bubble", "Mario", "Actraiser", "Zelda"), snes(SortGamesSetting.RecentlyAdded))
    }

    @Test
    fun recentlyAddedOrdersFavoritesToo() = runTest {
        listOf("z", "n").forEach { db.gameDao().setFavorite(idOf(it), true) }
        assertEquals(listOf("Metroid", "Zelda"), db.gameDao().observeFavorites(SortGamesSetting.RecentlyAdded).first().map { it.title })
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
    fun hasYearIsFalseWithNoYearAndTrueOnceOneGameHasIt() = runTest {
        assertFalse("nothing scraped", db.gameDao().observeHasYear().first())
        scrapedYear("z", null)
        assertFalse("a result with no year", db.gameDao().observeHasYear().first())
        scrapedYear("a", 0)
        assertFalse("a year of 0 is no year", db.gameDao().observeHasYear().first())
        scrapedYear("a", 1990)
        assertTrue(db.gameDao().observeHasYear().first())
        db.gameDao().setHidden(idOf("a"), true)
        assertFalse("a hidden game's year does not count", db.gameDao().observeHasYear().first())
    }

    private suspend fun scrapedRating(documentId: String, rating: Int?) {
        val id = idOf(documentId)
        db.scrapedGameDao().insertOrReplace(
            ScrapedGameEntity(
                scrapeKey = NameFixEntity.rowKey(id), gameId = id, outcome = ScrapeOutcome.MATCHED, screenScraperId = "1", matchedBy = ScrapeMatchedBy.HASH,
                scrapedAt = 0, detailsJson = "{}", title = null, year = null, publisher = null, players = null, genre = null, rating = rating,
            ),
        )
    }

    @Test
    fun ratingPutsTheHighestFirstAndTiesByNameThenId() = runTest {
        db.scanDao().applyBatch(ScanBatch(folderId, systems = emptyList(), newGames = listOf(game(folderId, "z2", title = "Zelda"))))
        scrapedRating("m", 15)
        scrapedRating("z", 18)
        scrapedRating("z2", 18)
        scrapedRating("a", 18)
        val rows = db.gameDao().observeSystemGames("snes", SortGamesSetting.Rating).first()
        assertEquals("18s by name, then 15", listOf("Actraiser", "Zelda", "Zelda", "Mario"), rows.map { it.title })
        assertEquals("the two Zeldas by id", listOf(idOf("z"), idOf("z2")), rows.filter { it.title == "Zelda" }.map { it.id })
    }

    @Test
    fun ratingLeavesGamesWithNoRatingAfterByName() = runTest {
        db.scanDao().applyBatch(ScanBatch(folderId, systems = emptyList(), newGames = listOf(game(folderId, "b", title = "Bubble"))))
        scrapedRating("m", 3)
        // A row with no rating, and one of 0 (never a real ScreenScraper rating), count as none; Bubble has no row at all.
        scrapedRating("z", null)
        scrapedRating("a", 0)
        assertEquals(listOf("Mario", "Actraiser", "Bubble", "Zelda"), snes(SortGamesSetting.Rating))
        assertEquals("nothing rated: by name", listOf("Metroid"), db.gameDao().observeSystemGames("nes", SortGamesSetting.Rating).first().map { it.title })
    }

    @Test
    fun ratingOrdersFavoritesToo() = runTest {
        scrapedRating("z", 12)
        scrapedRating("n", 17)
        listOf("a", "z", "n").forEach { db.gameDao().setFavorite(idOf(it), true) }
        assertEquals(listOf("Metroid", "Zelda", "Actraiser"), db.gameDao().observeFavorites(SortGamesSetting.Rating).first().map { it.title })
    }

    @Test
    fun hasRatingIsFalseWithNoRatingAndTrueOnceOneListedGameHasIt() = runTest {
        assertFalse("nothing scraped", db.gameDao().observeHasRating().first())
        scrapedRating("z", null)
        assertFalse("a result with no rating", db.gameDao().observeHasRating().first())
        scrapedRating("a", 0)
        assertFalse("a rating of 0 is no rating", db.gameDao().observeHasRating().first())
        scrapedYear("m", 1991)
        assertFalse("a year is not a rating", db.gameDao().observeHasRating().first())
        scrapedRating("a", 16)
        assertTrue(db.gameDao().observeHasRating().first())
        db.gameDao().setHidden(idOf("a"), true)
        assertFalse("a hidden game's rating does not count", db.gameDao().observeHasRating().first())
    }

    @Test
    fun everySortOfAFiveThousandGameSystemIsOneQuery() = runTest {
        val many = (0 until BIG).map { game(folderId, "big$it", systemId = "big", title = "Game ${"%05d".format((it * 7919) % BIG)}") }
        db.scanDao().applyBatch(ScanBatch(folderId, systems = listOf(SystemEntity("big", "Big")), newGames = many))
        val ids = db.scanDao().gamesInFolder(folderId).filter { it.systemId == "big" }.map { it.id }
        ids.filterIndexed { i, _ -> i % 3 == 0 }.forEach { db.playDao().addSession(it, endedAt = 100L + it, seconds = it % 50 + 1) }
        ids.filterIndexed { i, _ -> i % 4 == 0 }.forEach { id ->
            db.scrapedGameDao().insertOrReplace(
                ScrapedGameEntity(
                    scrapeKey = NameFixEntity.rowKey(id), gameId = id, outcome = ScrapeOutcome.MATCHED, screenScraperId = "1", matchedBy = ScrapeMatchedBy.HASH,
                    scrapedAt = 0, detailsJson = null, title = null, year = 1980 + (id % 20).toInt(), publisher = null, players = null, genre = null,
                    rating = (id % 21).toInt().takeIf { it > 0 },
                ),
            )
        }
        val timings = SortGamesSetting.entries.map { sort ->
            val start = System.nanoTime()
            val rows = db.gameDao().observeSystemGames("big", sort).first()
            assertEquals(BIG, rows.size)
            sort to (System.nanoTime() - start) / 1_000_000
        }
        println("SORT-TIMING $BIG games (JVM, in-memory, one query each): " + timings.joinToString { "${it.first} ${it.second} ms" })
        assertTrue("every sort under 2 s on the JVM: $timings", timings.all { it.second < 2_000 })
    }

    private companion object {
        const val BIG = 5_000
    }
}
