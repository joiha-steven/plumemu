package com.plumemu.scraper

import com.plumemu.scraper.AnswerChecks.isNameMatch
import com.plumemu.scraper.api.ApiGame
import com.plumemu.scraper.api.GameMapper

/**
 * A hack ([ScrapeRequest.hack]) ScreenScraper has no box art for takes its parent's: the game named like the title's
 * head before " - " or ":" (brackets removed), on the same system, found by one name search. Only one game named exactly
 * so counts ([TitleMatch.matches], then the exact-name tie-break as in `ScreenScraperScraper.searchByName`); anything
 * else, or a parent without a box front either, keeps the caller's result. The hack keeps its own [ScrapeRequest.title]
 * and, from the entry it matched without a box front, every info field that entry had.
 */
internal object HackParent {
    /**
     * [result] (the normal flow's) or, for NoMatch / a match with no box front, the parent's result. [search] asks
     * `jeuRecherche.php` (cached). A stop it meets (quota, thread limit, network loss, pause) is the game's result: the
     * game must not be stored as NoMatch, or delivered without a box, because the search could not be made.
     */
    suspend fun take(
        request: ScrapeRequest,
        result: ScrapeResult,
        log: (String) -> Unit,
        search: suspend (params: Map<String, String>, keep: (ApiGame) -> Boolean) -> ScreenScraperScraper.Lookup,
    ): ScrapeResult {
        if (!request.hack) return result // not an error: most games are no hack, so no log line
        val own = when {
            result is ScrapeResult.NoMatch -> null
            result is ScrapeResult.Matched && result.media.none { it.kind == MediaKind.BoxFront } -> result.info
            else -> return result
        }
        val systemeId = SystemIds.screenScraperId(request.systemId) ?: return result
        // The shown title may be a ScreenScraper name without the separator ("Crash Bandicoot N. Sane Trilogy"):
        // the file name still has it.
        val parent = TitleMatch.parentTitle(request.title) ?: TitleMatch.parentTitle(request.rom.fileName.substringBeforeLast('.'))
        if (parent == null) {
            log("hack parent search for ${request.rom.fileName}: outcome=no-parent-title (no subtitle separator in the title or the file name)")
            return result
        }
        val accepted = { game: ApiGame -> isNameMatch(game, parent, systemeId) }
        val found = when (val answer = search(mapOf("recherche" to parent, "systemeid" to systemeId.toString()), accepted)) {
            is ScreenScraperScraper.Lookup.Stop -> return answer.result
            ScreenScraperScraper.Lookup.NotFound -> null
            is ScreenScraperScraper.Lookup.Found -> pick(answer.response.response?.jeux.orEmpty().filter(accepted), parent)
        }
        val parentResult = found?.let { GameMapper.matched(it, MatchedBy.Parent, request.regions, request.languages) }
            ?.takeIf { r -> r.media.any { it.kind == MediaKind.BoxFront } }
        log("hack parent search for ${request.rom.fileName}: ${if (parentResult != null) "outcome=matched-parent" else "outcome=parent-none"}")
        return parentResult?.let { withOwnTitle(it, request.title, own) } ?: result
    }

    /** The one game among [candidates]: a single id, or the exactly named ones when the rest are only editions; else null. */
    private fun pick(candidates: List<ApiGame>, parent: String): ApiGame? {
        fun ids(list: List<ApiGame>) = list.map { it.id.orEmpty().trim() }.distinct()
        var left = candidates
        if (ids(left).size > 1) {
            val (exact, losers) = left.partition { TitleMatch.exactName(parent, it.noms.map { n -> n.text }) }
            if (exact.isNotEmpty() && !losers.any { TitleMatch.looksLikeOtherRelease(it.noms.map { n -> n.text }) }) left = exact
        }
        return if (ids(left).size == 1) left.first() else null
    }

    private fun withOwnTitle(parent: ScrapeResult.Matched, title: String, own: GameInfo?): ScrapeResult.Matched {
        val info = parent.info
        return parent.copy(
            info = GameInfo(
                title = title,
                synopsis = own?.synopsis ?: info.synopsis,
                year = own?.year ?: info.year,
                genre = own?.genre ?: info.genre,
                developer = own?.developer ?: info.developer,
                publisher = own?.publisher ?: info.publisher,
                players = own?.players ?: info.players,
                rating = own?.rating ?: info.rating,
            ),
        )
    }
}
