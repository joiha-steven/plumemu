package com.plumemu.library.scrape

import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.HtmlEntities
import com.plumemu.scraper.IdName
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.TextByLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A row keeps ScreenScraper's text as the answer gave it (entities encoded) and the screen decodes it once, so a
 * game whose text really contains `&quot;` (sent as `&amp;quot;`) is shown as written. The read half is
 * `LibraryHomeArtTest` in app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapedRowsEntitiesTest {
    private val answered = "&amp;quot;x&amp;quot; and &quot;y&quot;"
    private val shown = "&quot;x&quot; and \"y\""

    @Test fun `a stored row decodes once to what the answer said`() {
        val result = ScrapeResult.Matched(
            match = GameMatch("1", MatchedBy.Hash),
            // The scraper hands over info already decoded, and details as given.
            info = GameInfo(HtmlEntities.decode(answered), HtmlEntities.decode(answered), null, null, null, HtmlEntities.decode(answered), HtmlEntities.decode("1 &amp; 2"), null),
            media = emptyList(),
            details = GameDetails(synopses = listOf(TextByLanguage("en", answered)), publisher = IdName("3", answered)),
        )
        val row = ScrapedRows.matched("crc:a", null, result, null, emptyList(), now = 1)
        assertEquals("the title is stored plain: the naming pass shows it as the game name", HtmlEntities.decode(answered), row.title)
        assertEquals(shown, HtmlEntities.decode(row.publisher!!))
        assertEquals("1 & 2", HtmlEntities.decode(row.players!!))
        assertEquals(shown, HtmlEntities.decode(GameDetails.fromJson(row.detailsJson!!)!!.synopses.single().text))
    }

    private fun rowRated(rating: Int?) =
        ScrapedRows.matched(
            "crc:a",
            null,
            ScrapeResult.Matched(GameMatch("1", MatchedBy.Hash), GameInfo("Zelda", "Zelda", null, null, null, null, null, null), emptyList(), GameDetails(rating = rating)),
            null,
            emptyList(),
            now = 1,
        )

    @Test fun `the rating column is written with the details, as the details read back`() {
        val row = rowRated(16)
        assertEquals(16, row.rating)
        assertEquals("the same value a migration reads from the stored details", GameDetails.listedRatingOf(row.detailsJson!!), row.rating)
        assertEquals("ScreenScraper's 0 is no rating", null, rowRated(0).rating)
        assertEquals(null, rowRated(null).rating)
        assertEquals("a no-match has none", null, ScrapedRows.noMatch("crc:a", null, now = 1).rating)
    }
}
