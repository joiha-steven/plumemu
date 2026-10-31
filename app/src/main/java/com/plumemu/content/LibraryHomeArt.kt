package com.plumemu.content

import android.util.Log
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameDao
import com.plumemu.coredata.db.ScrapedGameSummary
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.graph.ScrapeMediaSizes
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.GameArt
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeArtSource
import com.plumemu.home.DetailsExtras
import com.plumemu.scraper.GameDetails as ScrapedDetails
import com.plumemu.scraper.HtmlEntities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The home screen's ScreenScraper results from Room (`scraped_game`): the rows of the
 * keys a list or grid shows ([ScrapedGameDao.observeByKeys], 500 keys a statement), mapped off the
 * main thread to the saved box fronts and the facts copied out of the details; the synopsis and
 * the rating are read from the stored details JSON for the focused game only. Pictures are the files the engine
 * saved ([ScrapeMediaSizes]); nothing here touches the network.
 *
 * A failure (the database closed, a query that throws) is logged by class and shows no art, never
 * a crash; the list keeps its titles and regions.
 */
internal class LibraryHomeArt(
    private val scraped: () -> ScrapedGameDao,
    override val images: CoverImages,
    private val mapping: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String) -> Unit = { Log.e(TAG, it) },
) : HomeArtSource {
    override fun observe(keys: List<String>): Flow<Map<String, GameArt>> {
        if (keys.isEmpty()) return flowOf(emptyMap())
        // Built on collection: a DAO's observe throws at once on a closed database.
        return flow { emitAll(scraped().observeByKeys(keys)) }
            .map { rows -> rows.mapNotNull { row -> artOf(row)?.let { row.scrapeKey to it } }.toMap() }
            .catch { e ->
                if (e is CancellationException) throw e
                log("Box art could not be read (${e.javaClass.simpleName})")
                emit(emptyMap())
            }.flowOn(mapping)
    }

    override suspend fun extras(key: String): DetailsExtras =
        try {
            val json = scraped().get(key)?.detailsJson ?: return DetailsExtras.None
            withContext(mapping) {
                ScrapedDetails.fromJson(json)?.let { DetailsExtras(englishSynopsis(it), it.rating?.takeIf { rating -> rating in 1..MAX_RATING }) } ?: DetailsExtras.None
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("Box art synopsis could not be read (${e.javaClass.simpleName})")
            DetailsExtras.None
        }

    companion object {
        private const val TAG = "PlumHomeArt"

        /** ScreenScraper's ratings are out of 20; anything else, and 0 (never a real rating there), is none. */
        private const val MAX_RATING = 20

        /** ScreenScraper's code for English synopses. */
        private const val ENGLISH = "en"

        /** What the screen shows of [row]: null for a "no match". */
        internal fun artOf(row: ScrapedGameSummary): GameArt? {
            if (row.outcome != ScrapeOutcome.MATCHED) return null
            val fronts = ScrapedPictures.decode(row.pictures).filter { it.kind == ScrapedPicture.BOX_FRONT }
            val grid = fronts.firstOrNull { it.sizeName == ScrapeMediaSizes.Grid.name }
            val large = fronts.firstOrNull { it.sizeName == ScrapeMediaSizes.Large.name }
            return GameArt(
                // Each falls back on the other: one saved size is better than a title placeholder.
                grid = (grid ?: large)?.let { pictureOf(it, row.scrapedAt) },
                large = (large ?: grid)?.let { pictureOf(it, row.scrapedAt) },
                details =
                    GameDetails(
                        year = row.year,
                        publisher = HtmlEntities.decode(row.publisher.orEmpty()),
                        genre = HtmlEntities.decode(row.genre.orEmpty()),
                        players = HtmlEntities.decode(row.players.orEmpty()),
                        description = "",
                    ),
            )
        }

        /**
         * The English synopsis; "" when there is none (the others are kept, not shown). Stored text keeps
         * ScreenScraper's entities (`ScrapedRows`, `GameDetails` as given), so this is the one place it is decoded:
         * rows from before the scraper decoded and rows from now read the same way.
         */
        internal fun englishSynopsis(details: ScrapedDetails): String =
            details.synopses.firstOrNull { it.language == ENGLISH && it.text.isNotBlank() }?.text?.trim().orEmpty().let(HtmlEntities::decode)

        private fun pictureOf(
            picture: ScrapedPicture,
            version: Long,
        ) = ArtPicture(picture.path, picture.width, picture.height, version)
    }
}
