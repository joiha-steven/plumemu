package com.plumemu.scraper.api

import com.plumemu.scraper.GameInfo
import com.plumemu.scraper.GameMatch
import com.plumemu.scraper.HtmlEntities
import com.plumemu.scraper.MatchedBy
import com.plumemu.scraper.MediaKind
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.SearchHit

/** Turns a ScreenScraper game into Plumemu's fields, in the person's region and language order. */
internal object GameMapper {
    /** ScreenScraper's own codes for "world" and "ScreenScraper default", tried after the person's. */
    private val FALLBACK_REGIONS = listOf("wor", "ss")
    private val FALLBACK_LANGUAGES = listOf("en")
    const val BOX_FRONT_TYPE = "box-2D"
    const val SUPPORT_TYPE = "support-2D"

    /**
     * ScreenScraper media type of each kind kept; nothing else is kept or cached (no fan art, no
     * video). Only box fronts and supports are downloaded (`ScrapeRun`); the rest are references.
     */
    val MEDIA_TYPES: Map<String, MediaKind> = mapOf(
        BOX_FRONT_TYPE to MediaKind.BoxFront,
        SUPPORT_TYPE to MediaKind.Support,
        "ss" to MediaKind.Screenshot,
        "sstitle" to MediaKind.TitleScreen,
        "manuel" to MediaKind.Manual,
    )

    /** The result for [game], found [by] hash, name or the person's pick; picked values, every reference, all details. */
    fun matched(game: ApiGame, by: MatchedBy, regions: List<String>, languages: List<String>): ScrapeResult.Matched {
        val media = media(game, regions)
        return ScrapeResult.Matched(
            match = GameMatch(game.id.orEmpty().trim(), by),
            info = info(game, regions, languages),
            media = media,
            details = DetailsMapper.details(game, media),
        )
    }

    /** One row of a person's search: the game's id, name, year and publisher, its first box front. */
    fun searchHit(game: ApiGame, regions: List<String>): SearchHit {
        val info = info(game, regions, emptyList())
        return SearchHit(
            screenScraperGameId = game.id.orEmpty().trim(),
            name = info.title,
            year = info.year,
            publisher = info.publisher,
            systemName = game.systeme?.text.decoded()?.takeIf { it.isNotBlank() },
            boxFront = pictures(game, regions, MediaKind.BoxFront).firstOrNull(),
        )
    }

    fun info(game: ApiGame, regions: List<String>, languages: List<String>): GameInfo {
        val regionOrder = order(regions, FALLBACK_REGIONS)
        val languageOrder = order(languages, FALLBACK_LANGUAGES)
        val genre = game.genres.firstOrNull { it.principale == "1" } ?: game.genres.firstOrNull()
        return GameInfo(
            title = game.noms.withText { it.text }.pick(regionOrder) { it.region }?.text.decoded(),
            synopsis = game.synopsis.withText { it.text }.pick(languageOrder) { it.langue }?.text.decoded()?.trim(),
            year = game.dates.withText { it.text }.pick(regionOrder) { it.region }?.text?.let(::yearOf),
            genre = genre?.noms?.withText { it.text }?.pick(languageOrder) { it.langue }?.text.decoded(),
            developer = game.developpeur?.text.decoded()?.takeIf { it.isNotBlank() },
            publisher = game.editeur?.text.decoded()?.takeIf { it.isNotBlank() },
            players = game.joueurs?.text.decoded()?.takeIf { it.isNotBlank() },
            rating = game.note?.text?.trim()?.toIntOrNull(),
        )
    }

    /**
     * Every kind in [MediaKind] order (box fronts, discs or cartridges, screenshots, title
     * screens, manuals), each kind the person's preferred region first; URLs stripped of
     * credentials.
     */
    fun media(game: ApiGame, regions: List<String>): List<MediaRef> =
        MediaKind.entries.flatMap { kind -> pictures(game, regions, kind) }

    private fun pictures(game: ApiGame, regions: List<String>, kind: MediaKind): List<MediaRef> {
        val regionOrder = order(regions, FALLBACK_REGIONS)
        return game.medias
            .filter { MEDIA_TYPES[it.type] == kind && !it.url.isNullOrBlank() }
            .sortedBy { media -> rank(regionOrder, media.region) }
            .map { media ->
                MediaRef(
                    kind = kind,
                    region = media.region,
                    url = Redact.stripCredentials(media.url.orEmpty()),
                    format = media.format,
                    md5 = media.md5?.takeIf { it.isNotBlank() },
                    size = media.size?.trim()?.toLongOrNull(),
                )
            }
    }

    private fun rank(order: List<String>, region: String?): Int {
        val index = region?.lowercase()?.let(order::indexOf) ?: -1
        return if (index < 0) Int.MAX_VALUE else index
    }

    private fun order(preferred: List<String>, fallback: List<String>): List<String> =
        (preferred.map { it.lowercase() } + fallback).distinct()

    private inline fun <T> List<T>.withText(text: (T) -> String?): List<T> = filter { !text(it).isNullOrBlank() }

    /** The first entry in [order]'s order, else the first entry at all. */
    private inline fun <T> List<T>.pick(order: List<String>, code: (T) -> String?): T? {
        for (wanted in order) firstOrNull { code(it)?.lowercase() == wanted }?.let { return it }
        return firstOrNull()
    }

    /** ScreenScraper's text fields carry HTML entities; decoded here, once, on the way out of the answer (the cache keeps the answer as given). */
    private fun String?.decoded(): String? = HtmlEntities.decodeOrNull(this)

    private fun yearOf(date: String): Int? = date.trim().take(4).toIntOrNull()?.takeIf { it in 1950..2999 }
}
