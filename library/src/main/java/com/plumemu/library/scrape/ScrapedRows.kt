package com.plumemu.library.scrape

import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.HtmlEntities
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.RomHints
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.media.SavedMedia

/** A delivered result as the `scraped_game` row stored for it. Pure. */
internal object ScrapedRows {
    /**
     * [discToFetchAgain]: the run's main pass delivered a game the person asked for again; its disc
     * or cartridge saved before, if any, is in [media] but is still to be fetched again by the disc pass.
     */
    fun matched(
        scrapeKey: String,
        gameId: Long?,
        result: ScrapeResult.Matched,
        hints: RomHints?,
        media: List<SavedMedia>,
        now: Long,
        discToFetchAgain: Boolean = false,
    ) = ScrapedGameEntity(
        scrapeKey = scrapeKey,
        gameId = gameId,
        outcome = ScrapeOutcome.MATCHED,
        screenScraperId = result.match.screenScraperGameId,
        matchedBy = ScraperTrust.matchedBy(result.match.matchedBy, hints),
        scrapedAt = now,
        detailsJson = result.details.toJson(),
        // Plain text: the naming pass (`matchedTitles`) writes it as the shown game name. publisher, players and the
        // details keep ScreenScraper's entities and are decoded once on read (`LibraryHomeArt`).
        title = result.info.title,
        year = result.info.year,
        publisher = result.info.publisher?.let(HtmlEntities::encode) ?: result.details.publisher?.name?.takeIf { it.isNotBlank() },
        players = result.info.players?.let(HtmlEntities::encode),
        genre = mainGenreInEnglish(result.details),
        pictures = ScrapedPictures.encode(media.map { ScrapedPicture(kindName(it.kind), it.sizeName, it.path, it.width, it.height) }),
        hasBoxFront = media.any { it.kind == MediaKind.BoxFront },
        // The run's main pass delivers the box front only; its disc pass, a failed disc or cartridge without it.
        supportMissing = result.media.any { it.kind == MediaKind.Support } && (discToFetchAgain || media.none { it.kind == MediaKind.Support }),
    )

    fun noMatch(scrapeKey: String, gameId: Long?, now: Long) = ScrapedGameEntity(
        scrapeKey = scrapeKey,
        gameId = gameId,
        outcome = ScrapeOutcome.NO_MATCH,
        screenScraperId = null,
        matchedBy = null,
        scrapedAt = now,
        detailsJson = null,
        title = null,
        year = null,
        publisher = null,
        players = null,
        genre = null,
    )

    /** The main genre (else the first) by its English name, else its short name or any name. */
    fun mainGenreInEnglish(details: GameDetails): String? {
        val genre = details.genres.firstOrNull { it.main } ?: details.genres.firstOrNull() ?: return null
        val names = genre.names.filter { it.text.isNotBlank() }
        return (names.firstOrNull { it.language == "en" }?.text ?: genre.shortName?.takeIf { it.isNotBlank() } ?: names.firstOrNull()?.text)?.trim()
    }

    /** The `MediaKind` serial names, as [ScrapedPicture.kind] stores them. */
    fun kindName(kind: MediaKind): String = when (kind) {
        MediaKind.BoxFront -> ScrapedPicture.BOX_FRONT
        MediaKind.Support -> ScrapedPicture.SUPPORT
        MediaKind.Screenshot -> "screenshot"
        MediaKind.TitleScreen -> "title-screen"
        MediaKind.Manual -> "manual"
    }
}
