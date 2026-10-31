package com.plumemu.secondscreen

import com.plumemu.home.GameDetails

/** Samples of the details card (layout C): games with a ScreenScraper result. Covers from SecondScreenBoxArtTest.kt. */
internal const val LONG_DESCRIPTION =
    "The Super Metroid, the last of its kind, has been stolen from the Ceres space colony by Ridley. " +
        "Samus Aran follows him to the planet Zebes, where the Space Pirates have rebuilt their base deep " +
        "underground. She must explore its caverns, find the upgrades hidden in them and stop Mother Brain " +
        "before the creature is turned into a weapon that could threaten the whole galaxy."

internal val FULL_DETAILS =
    GameDetails(year = 1994, publisher = "Nintendo", genre = "Action, Adventure", players = "1", description = LONG_DESCRIPTION, rating = 17)

internal val SHORT_DETAILS =
    GameDetails(year = 1995, publisher = "Square", genre = "Role-playing", players = "1", description = "A band of rebels travels through time to save the world.", rating = 18)

/** A detail missing in turn: the name of the case and the details without it. */
internal val DETAILS_EACH_MISSING: List<Pair<String, GameDetails>> =
    listOf(
        "no year" to FULL_DETAILS.copy(year = null),
        "no publisher" to FULL_DETAILS.copy(publisher = ""),
        "no year or publisher" to FULL_DETAILS.copy(year = null, publisher = ""),
        "no genre" to FULL_DETAILS.copy(genre = ""),
        "no rating" to FULL_DETAILS.copy(rating = null),
        "no genre or rating" to FULL_DETAILS.copy(genre = "", rating = null),
        "only a rating" to GameDetails(year = null, publisher = "", genre = "", players = "4", description = "", rating = 16),
        "no description" to FULL_DETAILS.copy(description = ""),
        "only a year" to GameDetails(year = 1994, publisher = "", genre = "", players = "", description = ""),
        "only a description" to GameDetails(year = null, publisher = "", genre = "", players = "", description = LONG_DESCRIPTION),
    )

/** Nothing to say: a result with no facts and no synopsis. */
internal val EMPTY_DETAILS = GameDetails(year = null, publisher = "", genre = "", players = "", description = "")

internal fun SecondScreenContent.Game.withDetails(
    details: GameDetails?,
    picture: com.plumemu.home.ArtPicture? = null,
) = copy(scrapeKey = "crc:1", art = picture, details = details)

/** The file Play launches, as the game row holds it; the card shows it without its extension. */
internal const val DETAILS_FILE_NAME = "Super Metroid (Japan, USA) (En,Ja).sfc"

/** A file name far longer than the card's line at any size: one line, cut with "…". */
internal const val LONG_FILE_NAME =
    "Final Fantasy VII - International Edition (Japan) (Disc 1) (Rev 1) (Special Collector's Edition with the Bonus Disc, the Art Book and More).cue"

internal val DETAILS_GAME = GAME_WITH_STAR.withDetails(FULL_DETAILS).copy(fileName = DETAILS_FILE_NAME)

/** A genre too long for the line beside a landscape box: it is cut, the rating stays. */
internal val LONG_GENRE_DETAILS = FULL_DETAILS.copy(genre = "Action, Adventure, Platform, Metroidvania, Shoot 'em up", rating = 17)
internal val PORTRAIT_GAME =
    SecondScreenContent.Game("Persona 3 Portable", "PSP", "USA", isFavorite = true, lastPlayedDaysAgo = 2, playTimeMinutes = 95)
        .copy(emulator = emulatorNamed("PPSSPP"), fileName = "Persona 3 Portable (USA).iso")
        .withDetails(
            GameDetails(
                year = 2009,
                publisher = "Atlus",
                genre = "Role-playing",
                players = "1",
                rating = 15,
                description =
                    "A transfer student arrives in a city where a hidden hour falls between one day and the next. " +
                        "Students who stay awake in it fight the shadows that roam a tower risen from their school, " +
                        "while the days are spent on classes, friends and clubs until the full moon comes again.",
            ),
        )
