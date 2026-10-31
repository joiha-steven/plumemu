package com.plumemu.game

import android.util.Log
import com.plumemu.content.LibraryHomeArt
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.scraper.HtmlEntities
import kotlinx.coroutines.CancellationException
import com.plumemu.scraper.GameDetails as ScrapedDetails

/**
 * What ScreenScraper said about a game, for Info's rows and its description. Every text is
 * decoded and trimmed; blank means ScreenScraper gave none and the row is left out.
 * Only read while box art is set up.
 */
internal data class ScrapedFacts(
    val year: Int? = null,
    val publisher: String = "",
    val developer: String = "",
    val genre: String = "",
    val players: String = "",
    /** The English synopsis, whole; Info makes it readable with Up and Down. */
    val description: String = "",
) {
    val isEmpty: Boolean get() = year == null && publisher.isEmpty() && developer.isEmpty() && genre.isEmpty() && players.isEmpty() && description.isEmpty()
}

private const val TAG = "PlumGameInfo"

/**
 * The stored ScreenScraper answer under [scrapeKey]; null when there is none, it was a no-match, or
 * it holds nothing to show. Year, publisher, genre and players are the columns copied out of the
 * details; the developer and the synopsis come from the stored details JSON.
 */
internal suspend fun readScrapedFacts(
    dao: ScrapedGameDao,
    scrapeKey: String,
): ScrapedFacts? =
    try {
        val row = dao.get(scrapeKey)?.takeIf { it.outcome == ScrapeOutcome.MATCHED }
        val details = row?.detailsJson?.let(ScrapedDetails::fromJson)
        row?.let {
            ScrapedFacts(
                // 0 is not a year.
                year = it.year?.takeIf { year -> year > 0 },
                publisher = clean(it.publisher),
                developer = clean(details?.developer?.name),
                genre = clean(it.genre),
                players = clean(it.players),
                description = details?.let(LibraryHomeArt::englishSynopsis).orEmpty(),
            ).takeUnless(ScrapedFacts::isEmpty)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Info still opens without these rows; the class only.
        Log.w(TAG, "Scraped facts could not be read (${e.javaClass.simpleName})")
        null
    }

private fun clean(text: String?): String = HtmlEntities.decode(text.orEmpty()).trim()
