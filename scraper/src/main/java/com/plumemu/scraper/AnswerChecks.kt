package com.plumemu.scraper

import com.plumemu.scraper.api.ApiGame

/** Which games of ScreenScraper's answers the scraper takes ([ScreenScraperScraper] asks, this judges). */
internal object AnswerChecks {
    /**
     * A name result counts only for the requested system and an exact title ([TitleMatch]): the
     * search returns up to 30 related games in no useful order. No result comes back as
     * "jeux":[{}]: a game without an id is not a game.
     */
    fun isNameMatch(game: ApiGame, title: String, systemeId: Int): Boolean =
        isGameOfSystem(game, systemeId) && TitleMatch.matches(title, game.noms.map { it.text })

    /** A real game (an id, not junk) of the requested system. */
    fun isGameOfSystem(game: ApiGame, systemeId: Int): Boolean =
        !game.id.isNullOrBlank() && !isJunk(game) && game.systeme?.id?.trim() == systemeId.toString()

    /**
     * A hash answer is never junk. A serial-only lookup (no crc, md5 or sha1) must also come from the
     * asked system: ScreenScraper matches serials across systems. Other lookups are trusted as before.
     */
    fun isHashAnswerOk(game: ApiGame, systemeId: Int?, serialOnly: Boolean, serialNative: Int? = null): Boolean =
        !isJunk(game) && (!serialOnly || SystemIds.serialAnswerAccepted(systemeId, game.systeme?.id, serialNative))

    /** ScreenScraper keeps non-games (download stations, flash carts) named "ZZZ(notgame):…". */
    fun isJunk(game: ApiGame): Boolean =
        game.noms.any { it.text?.trim()?.startsWith("ZZZ(notgame)", ignoreCase = true) == true }
}
