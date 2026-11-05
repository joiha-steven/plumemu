package com.plumemu.graph

import com.plumemu.scraper.GameDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The reader the app gives the schema 16 back-fill reads what the scraper stores, as the details show it. */
class GraphDetailsRatingTest {
    @Test
    fun readsTheRatingTheScraperStored() {
        assertEquals(17, GraphDetailsRating.of(GameDetails(rating = 17, players = "1").toJson()))
        assertEquals(20, GraphDetailsRating.of(GameDetails(rating = 20).toJson()))
        assertEquals(1, GraphDetailsRating.of(GameDetails(rating = 1).toJson()))
    }

    @Test
    fun noRatingAZeroAnOutOfRangeOneOrTextThatDoesNotReadIsNone() {
        assertNull(GraphDetailsRating.of(GameDetails(players = "2").toJson()))
        assertNull("ScreenScraper never gives a real 0", GraphDetailsRating.of(GameDetails(rating = 0).toJson()))
        assertNull(GraphDetailsRating.of(GameDetails(rating = 21).toJson()))
        assertNull(GraphDetailsRating.of("""{"format":1,"details":{"rating":"great"}}"""))
        assertNull(GraphDetailsRating.of("""{"format":1,"details":{"rating":1"""))
        assertNull(GraphDetailsRating.of(""))
    }
}
