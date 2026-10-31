package com.plumemu.game

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.IdName
import com.plumemu.scraper.TextByLanguage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.plumemu.coreui.testing.ReferenceShots

/** What Info reads of a stored ScreenScraper answer: columns for most rows, the details JSON for the developer and the synopsis. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ScrapedFactsTest {
    private lateinit var db: PlumDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), PlumDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun close() = db.close()

    private fun store(
        key: String,
        outcome: ScrapeOutcome = ScrapeOutcome.MATCHED,
        year: Int? = 1994,
        publisher: String? = "Nintendo",
        details: GameDetails? = GameDetails(developer = IdName("1", "Nintendo R&amp;D1"), synopses = listOf(TextByLanguage("fr", "Bonjour"), TextByLanguage("en", "A hunter &amp; a planet."))),
        genre: String? = "Platform",
        players: String? = "1-2",
    ) = runBlocking {
        db.scrapedGameDao().insertOrReplace(
            ScrapedGameEntity(
                scrapeKey = key, gameId = null, outcome = outcome, screenScraperId = "1".takeIf { outcome == ScrapeOutcome.MATCHED },
                matchedBy = ScrapeMatchedBy.HASH.takeIf { outcome == ScrapeOutcome.MATCHED }, scrapedAt = 0, detailsJson = details?.toJson(),
                title = "t", year = year, publisher = publisher, players = players, genre = genre,
            ),
        )
    }

    private fun read(key: String) = runBlocking { readScrapedFacts(db.scrapedGameDao(), key) }

    @Test
    fun aMatchGivesEveryRowDecoded() {
        store("crc:1")
        assertEquals(ScrapedFacts(1994, "Nintendo", "Nintendo R&D1", "Platform", "1-2", "A hunter & a planet."), read("crc:1"))
    }

    @Test
    fun whatScreenScraperLeftOutIsBlankAndAnEmptyAnswerIsNull() {
        store("crc:2", year = 0, publisher = " ", genre = null, players = null, details = GameDetails())
        assertNull("a year of 0 is none, and nothing else is stored", read("crc:2"))
        store("crc:3", year = null, details = GameDetails(), publisher = "Capcom")
        assertEquals(ScrapedFacts(publisher = "Capcom", genre = "Platform", players = "1-2"), read("crc:3"))
    }

    @Test
    fun noMatchAndNoRowGiveNothing() {
        store("crc:4", outcome = ScrapeOutcome.NO_MATCH)
        assertNull(read("crc:4"))
        assertNull(read("crc:none"))
    }

    @Test
    fun detailsThatDoNotReadStillGiveTheColumns() {
        store("crc:5", details = null)
        assertEquals(ScrapedFacts(1994, "Nintendo", "", "Platform", "1-2", ""), read("crc:5"))
    }
}
