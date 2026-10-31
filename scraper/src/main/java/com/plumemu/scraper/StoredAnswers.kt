package com.plumemu.scraper

/**
 * Judges a stored answer (the library's `scraped_game.details_json`, written by [GameDetails.toJson])
 * by today's rules, for the library's one-time repairs of results older rules got wrong. The same
 * check as a live lookup's (`AnswerChecks.isHashAnswerOk`), so a repair forgets exactly what a lookup
 * would now refuse.
 */
public object StoredAnswers {
    /**
     * Whether a hash answer stored as [detailsJson] would pass today's check for a lookup that sent a
     * serial, for a game of Plumemu system [plumemuSystemId] whose serial was read from the format of
     * [serialSystemId] (`RomHints.serialSystemId`; null when unknown). Accepted: the asked system, the
     * serial's own system, or the Game Boy / Game Boy Color pair of either (`SystemIds.serialAnswerAccepted`).
     * True when [plumemuSystemId] has no ScreenScraper id (nothing was checked then either). Null when
     * the stored text names no system (not readable, or written without one): it cannot be judged.
     */
    public fun serialAnswerFitsSystem(detailsJson: String, plumemuSystemId: String, serialSystemId: String?): Boolean? {
        val requested = SystemIds.screenScraperId(plumemuSystemId) ?: return true
        val answered = GameDetails.fromJson(detailsJson)?.system?.id?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return SystemIds.serialAnswerAccepted(requested, answered, serialSystemId?.let(SystemIds::screenScraperId))
    }

    /** ScreenScraper's system id for [plumemuSystemId], or null when it has none (for tests and logs). */
    public fun screenScraperSystemId(plumemuSystemId: String): Int? = SystemIds.screenScraperId(plumemuSystemId)
}
