package com.plumemu.scraper.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The parts of ScreenScraper's JSON the scraper reads. Every field is optional and a string:
 * ScreenScraper writes numbers as strings, and leaves fields out freely.
 *
 * Field names checked against real answers,
 * except `themes` and `styles`, which the documentation names but the answers seen lack. Every field added
 * for the details reads leniently ([LenientText], [LenientList]): an odd shape gives
 * null or an empty list, never a failed answer, so the match path reads what it read before.
 * An answer is cached re-encoded from these classes only ([CachedAnswer]), so a field added here
 * is also kept in the cache; bump `ResponseCache.FORMAT` when a cached answer must be asked again.
 */
@Serializable
internal class ApiEnvelope(val response: ApiResponse? = null)

@Serializable
internal class ApiResponse(
    /** Server state; read for `closefornomember`, never cached. */
    val serveurs: ApiServers? = null,
    /** The account the answer counted against; never cached. */
    val ssuser: SsUser? = null,
    /** jeuInfos.php: the one game found. */
    val jeu: ApiGame? = null,
    /** jeuRecherche.php: the games found. No result is `[{}]`, one game without an id. */
    val jeux: List<ApiGame> = emptyList(),
)

@Serializable
internal class ApiServers(
    /** "1" while the API is closed to non-members. */
    val closefornomember: String? = null,
)

@Serializable
internal class SsUser(
    /**
     * The account's number. "0" when the answer came as anonymous: `jeuInfos.php` answers a wrong
     * username or password with HTTP 200 and `numid` "0" instead of an error.
     */
    val numid: String? = null,
    val maxthreads: String? = null,
    val maxrequestspermin: String? = null,
    val requeststoday: String? = null,
    val maxrequestsperday: String? = null,
    /** KB/s the account may download at (ScreenScraper's docs); logged by the timing lines only. */
    val maxdownloadspeed: String? = null,
)

@Serializable
internal class ApiGame(
    val id: String? = null,
    val noms: List<RegionText> = emptyList(),
    val systeme: IdText? = null,
    val editeur: IdText? = null,
    val developpeur: IdText? = null,
    val joueurs: IdText? = null,
    val note: IdText? = null,
    val synopsis: List<LanguageText> = emptyList(),
    val dates: List<RegionText> = emptyList(),
    @Serializable(with = LenientTagList::class) val genres: List<ApiGenre> = emptyList(),
    val medias: List<ApiMedia> = emptyList(),
    @Serializable(with = LenientTagList::class) val modes: List<ApiGenre> = emptyList(),
    @Serializable(with = LenientTagList::class) val familles: List<ApiGenre> = emptyList(),
    @Serializable(with = LenientTagList::class) val themes: List<ApiGenre> = emptyList(),
    @Serializable(with = LenientTagList::class) val styles: List<ApiGenre> = emptyList(),
    @Serializable(with = LenientClassificationList::class) val classifications: List<ApiClassification> = emptyList(),
    /** Degrees, "0" on consoles. */
    @Serializable(with = LenientText::class) val rotation: String? = null,
    /** Arcade: "384x224". */
    @Serializable(with = LenientText::class) val resolution: String? = null,
    /** Arcade: the number of controls, "6". */
    @Serializable(with = LenientText::class) val controles: String? = null,
    /** Arcade: a list of buttons and colours in `jeuInfos.php`, a one-letter string in `jeuRecherche.php`. */
    @Serializable(with = LenientColourList::class) val couleurs: List<ApiColour> = emptyList(),
    /** The game id this one is a clone of; "0" for none. */
    @Serializable(with = LenientText::class) val cloneof: String? = null,
    /** "true" or "false". */
    @Serializable(with = LenientText::class) val notgame: String? = null,
    /** "1" or "0". */
    @Serializable(with = LenientText::class) val topstaff: String? = null,
)

@Serializable
internal class RegionText(val region: String? = null, val text: String? = null)

@Serializable
internal class LanguageText(val langue: String? = null, val text: String? = null)

@Serializable
internal class IdText(val id: String? = null, val text: String? = null)

/**
 * A genre, and also a mode, family, theme or style: they share the shape. Read leniently in every
 * list ([LenientTagList]): genres were already read, and may only get more forgiving.
 */
@Serializable
internal class ApiGenre(
    @Serializable(with = LenientText::class) val id: String? = null,
    @Serializable(with = LenientText::class) val nomcourt: String? = null,
    @Serializable(with = LenientText::class) val principale: String? = null,
    @Serializable(with = LenientText::class) val parentid: String? = null,
    @Serializable(with = LenientLanguageTextList::class) val noms: List<LanguageText> = emptyList(),
)

@Serializable
internal class ApiClassification(
    @Serializable(with = LenientText::class) val type: String? = null,
    @Serializable(with = LenientText::class) val text: String? = null,
)

@Serializable
internal class ApiColour(
    @Serializable(with = LenientText::class) val controle: String? = null,
    @Serializable(with = LenientText::class) val hexa: String? = null,
)

/** A data class: the trimmed cache drops duplicate entries (each box front is listed twice). */
@Serializable
internal data class ApiMedia(
    val type: String? = null,
    val url: String? = null,
    val region: String? = null,
    val md5: String? = null,
    val format: String? = null,
    /** Bytes. */
    @Serializable(with = LenientText::class) val size: String? = null,
)

internal val ScreenScraperJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}
