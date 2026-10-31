package com.plumemu.scraper.api

/**
 * What of an answer goes into the response cache: only what the scraper reads.
 *
 * A real `jeuInfos.php` answer is about 1.3 MB (the `hacks` list alone 940 KB, `roms` 120 KB) and
 * a name search 2.3 MB; cached raw for 30 days, a library would fill gigabytes. The cache keeps the
 * fields of [ApiGame] only (re-encoding drops every other one: `roms`, `rom`, `hacks`, `tips`,
 * `actions`), no `header`, `ssuser` or `serveurs`, and of the media only the kinds in
 * [GameMapper.MEDIA_TYPES] (box fronts, discs or cartridges, screenshots, title screens, manuals;
 * no fan art, no video), credentials stripped from their URLs and each kept once (ScreenScraper
 * lists many of them twice, identical). Every text field of a game is kept:
 * [com.plumemu.scraper.GameDetails] is made from this trimmed copy.
 */
internal object CachedAnswer {
    /**
     * [envelope] cut down to what is read. [keepGame] picks which name search results are kept:
     * the scraper keeps only the ones it would accept, so a 30-game search stores
     * the accepted ones (usually one or none).
     */
    fun trim(envelope: ApiEnvelope, keepGame: (ApiGame) -> Boolean = { true }): ApiEnvelope {
        val response = envelope.response ?: return ApiEnvelope()
        return ApiEnvelope(
            ApiResponse(
                jeu = response.jeu?.let(::trimGame),
                jeux = response.jeux.filter(keepGame).map(::trimGame),
            ),
        )
    }

    /**
     * The JSON text to store. Not run through [Redact.text]: blanking every password-like word
     * would also change titles and synopses ("Super *** Bros."), and the trimmed answer holds no
     * credential except in media URLs, which [trim] has stripped.
     */
    fun encode(envelope: ApiEnvelope): String = ScreenScraperJson.encodeToString(ApiEnvelope.serializer(), envelope)

    private fun trimGame(game: ApiGame): ApiGame = ApiGame(
        id = game.id,
        noms = game.noms,
        systeme = game.systeme,
        editeur = game.editeur,
        developpeur = game.developpeur,
        joueurs = game.joueurs,
        note = game.note,
        synopsis = game.synopsis,
        dates = game.dates,
        genres = game.genres,
        modes = game.modes,
        familles = game.familles,
        themes = game.themes,
        styles = game.styles,
        classifications = game.classifications,
        rotation = game.rotation,
        resolution = game.resolution,
        controles = game.controles,
        couleurs = game.couleurs,
        cloneof = game.cloneof,
        notgame = game.notgame,
        topstaff = game.topstaff,
        medias = game.medias
            .filter { it.type in GameMapper.MEDIA_TYPES }
            .map { it.copy(url = it.url?.let(Redact::stripCredentials)) }
            .distinct(),
    )
}
