package com.plumemu.scraper.api

import com.plumemu.scraper.ButtonColour
import com.plumemu.scraper.Classification
import com.plumemu.scraper.GameDetails
import com.plumemu.scraper.GameTag
import com.plumemu.scraper.IdName
import com.plumemu.scraper.MediaRef
import com.plumemu.scraper.TextByLanguage
import com.plumemu.scraper.TextByRegion

/**
 * A ScreenScraper game as [GameDetails]: every list kept whole and in ScreenScraper's order,
 * region and language codes as given, texts unchanged (HTML entities included: these details are stored, and the
 * person-facing read decodes them once, [HtmlEntities]); an entry whose text is blank is left out.
 * Numbers and flags are read from ScreenScraper's strings; one that does not read is null.
 */
internal object DetailsMapper {
    fun details(game: ApiGame, media: List<MediaRef>): GameDetails = GameDetails(
        names = game.noms.byRegion(),
        synopses = game.synopsis.byLanguage(),
        dates = game.dates.byRegion(),
        system = game.systeme.idName(),
        publisher = game.editeur.idName(),
        developer = game.developpeur.idName(),
        players = game.joueurs?.text.given(),
        rating = game.note?.text.number(),
        genres = game.genres.tags(),
        modes = game.modes.tags(),
        families = game.familles.tags(),
        themes = game.themes.tags(),
        styles = game.styles.tags(),
        classifications = game.classifications.mapNotNull { rating ->
            rating.text.given()?.let { Classification(rating.type.given(), it) }
        },
        rotation = game.rotation.number(),
        resolution = game.resolution.given(),
        controls = game.controles.number(),
        buttonColours = game.couleurs.mapNotNull { colour ->
            colour.hexa.given()?.let { ButtonColour(colour.controle.given(), it) }
        },
        cloneOf = game.cloneof.given()?.takeIf { it.trim() != NO_CLONE },
        notGame = game.notgame.flag(),
        topStaff = game.topstaff.flag(),
        media = media,
    )

    private const val NO_CLONE = "0"

    private fun List<RegionText>.byRegion(): List<TextByRegion> =
        mapNotNull { entry -> entry.text.given()?.let { TextByRegion(entry.region.given(), it) } }

    private fun List<LanguageText>.byLanguage(): List<TextByLanguage> =
        mapNotNull { entry -> entry.text.given()?.let { TextByLanguage(entry.langue.given(), it) } }

    private fun IdText?.idName(): IdName? {
        val given = this ?: return null
        return given.text.given()?.let { IdName(given.id.given(), it) }
    }

    /** A tag without a name in any language is still kept: its id says what it is. */
    private fun List<ApiGenre>.tags(): List<GameTag> = map { tag ->
        GameTag(
            id = tag.id.given(),
            shortName = tag.nomcourt.given(),
            main = tag.principale.flag() == true,
            parentId = tag.parentid.given(),
            names = tag.noms.byLanguage(),
        )
    }

    private fun String?.given(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.number(): Int? = this?.trim()?.toIntOrNull()

    private fun String?.flag(): Boolean? = when (this?.trim()?.lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }
}
