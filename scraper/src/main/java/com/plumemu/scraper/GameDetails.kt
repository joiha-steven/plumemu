package com.plumemu.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything ScreenScraper's `jeuInfos.php` answer gives about a game for free, kept as given so
 * no new lookup is needed later: one entry per region or per language, codes
 * as ScreenScraper writes them ("us", "wor", "ss", "en"), texts unchanged, entries with a blank
 * text left out. Picking one value for the person is [GameInfo]'s job.
 *
 * Anything ScreenScraper leaves out is null or empty. The library stores it with [toJson] and
 * reads it back with [fromJson]; every `@SerialName` is part of that stored format, so a Kotlin
 * rename never changes the JSON. A field added later needs a default (old JSON lacks it).
 */
@Serializable
public data class GameDetails(
    /** The game's name in each region ("ss" is ScreenScraper's own default). */
    @SerialName("names") val names: List<TextByRegion> = emptyList(),
    /** The synopsis in each language. */
    @SerialName("synopses") val synopses: List<TextByLanguage> = emptyList(),
    /** Release dates per region, as given: a full date, a year alone or a year and month. */
    @SerialName("dates") val dates: List<TextByRegion> = emptyList(),
    /** ScreenScraper's system (its own id and name). */
    @SerialName("system") val system: IdName? = null,
    @SerialName("publisher") val publisher: IdName? = null,
    @SerialName("developer") val developer: IdName? = null,
    /** As given: "1", "1-2", "1-4". */
    @SerialName("players") val players: String? = null,
    /** ScreenScraper's note, out of 20. */
    @SerialName("rating") val rating: Int? = null,
    @SerialName("genres") val genres: List<GameTag> = emptyList(),
    /** Play modes (single player, co-op...). */
    @SerialName("modes") val modes: List<GameTag> = emptyList(),
    /** Game families (series). */
    @SerialName("families") val families: List<GameTag> = emptyList(),
    @SerialName("themes") val themes: List<GameTag> = emptyList(),
    @SerialName("styles") val styles: List<GameTag> = emptyList(),
    /** Age ratings: PEGI, ESRB, CERO... */
    @SerialName("classifications") val classifications: List<Classification> = emptyList(),
    /** Screen rotation in degrees (arcade; consoles answer 0). */
    @SerialName("rotation") val rotation: Int? = null,
    /** Native resolution as given, "384x224" (arcade). */
    @SerialName("resolution") val resolution: String? = null,
    /** Number of controls (arcade). */
    @SerialName("controls") val controls: Int? = null,
    /** The cabinet's button colours (arcade). */
    @SerialName("buttonColours") val buttonColours: List<ButtonColour> = emptyList(),
    /** The ScreenScraper game id this game is a clone of; null when it is none ("0"). */
    @SerialName("cloneOf") val cloneOf: String? = null,
    /** ScreenScraper marks a BIOS, a demo disc or a tool as "not a game". */
    @SerialName("notGame") val notGame: Boolean? = null,
    /** ScreenScraper's staff pick. */
    @SerialName("topStaff") val topStaff: Boolean? = null,
    /** The same references as [ScrapeResult.Matched.media], so the stored JSON holds them. */
    @SerialName("media") val media: List<MediaRef> = emptyList(),
) {
    /** This value as JSON, wrapped with [JSON_FORMAT]: `{"format":1,"details":{...}}`. */
    public fun toJson(): String = GameDetailsJson.encode(this)

    public companion object {
        /**
         * The version written by [toJson]. Raise it only when a field changes meaning; a field
         * added with a default needs no new version: old readers skip it, new readers default it.
         */
        public const val JSON_FORMAT: Int = 1

        /**
         * Reads what [toJson] wrote, of any version, older or newer than [JSON_FORMAT]: what this
         * version understands is read, the rest skipped. Field by field: an unknown field is
         * skipped, a missing, null or wrong-typed one takes its default; in a list, an entry that
         * does not read (a media reference of an unknown kind or without a URL) is dropped alone.
         * Null only for text without a `details` object (not JSON, or not this JSON). Never throws.
         */
        public fun fromJson(text: String): GameDetails? = GameDetailsJson.decode(text)

        /**
         * The `format` written in [text], or null when there is none. A caller that wants a field
         * a newer format gives (or an older one lacked) may ask the game again when it differs
         * from [JSON_FORMAT]; [fromJson] reads it anyway.
         */
        public fun jsonFormatOf(text: String): Int? = GameDetailsJson.format(text)
    }
}

@Serializable
public data class TextByRegion(
    @SerialName("region") val region: String? = null,
    @SerialName("text") val text: String = "",
)

@Serializable
public data class TextByLanguage(
    @SerialName("language") val language: String? = null,
    @SerialName("text") val text: String = "",
)

/** A publisher, developer or system: ScreenScraper's id and the name. */
@Serializable
public data class IdName(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String = "",
)

/**
 * A genre, mode, family, theme or style: ScreenScraper's id, its short name, whether it is the
 * game's main one, its parent's id (a genre's broader genre), and its name in each language.
 */
@Serializable
public data class GameTag(
    @SerialName("id") val id: String? = null,
    @SerialName("shortName") val shortName: String? = null,
    @SerialName("main") val main: Boolean = false,
    @SerialName("parentId") val parentId: String? = null,
    @SerialName("names") val names: List<TextByLanguage> = emptyList(),
)

/** An age rating: the system ("PEGI") and its value ("12"). */
@Serializable
public data class Classification(
    @SerialName("type") val type: String? = null,
    @SerialName("value") val value: String = "",
)

/** An arcade button and its colour: the control ("P1_BUTTON1") and the colour ("FF0000"). */
@Serializable
public data class ButtonColour(
    @SerialName("control") val control: String? = null,
    @SerialName("hex") val hex: String = "",
)
