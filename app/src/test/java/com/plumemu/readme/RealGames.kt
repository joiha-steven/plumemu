package com.plumemu.readme

import com.plumemu.home.EntryKind
import com.plumemu.home.GameDetails
import com.plumemu.home.HomeContent

/*
 * Real, well-known titles for the README's lists and grids, so no screen shows a numbered stand-in
 * ("Dreamcast title 1"). Titles, years and publishers only: no artwork, which is drawn by
 * PosterCovers.kt. The fixtures' own placeholder rows past a system's list here are dropped.
 */

internal data class ReadmeGame(
    val title: String,
    val year: Int,
    val publisher: String,
    val region: String = "USA",
)

private val SNES =
    listOf(
        ReadmeGame("Chrono Trigger", 1995, "Square"),
        ReadmeGame("Contra III: The Alien Wars", 1992, "Konami"),
        ReadmeGame("Donkey Kong Country 2", 1995, "Nintendo"),
        ReadmeGame("EarthBound", 1994, "Nintendo"),
        ReadmeGame("F-Zero", 1991, "Nintendo"),
        ReadmeGame("Final Fantasy VI", 1994, "Square", "JPN"),
        ReadmeGame("Illusion of Gaia", 1994, "Enix"),
        ReadmeGame("Kirby Super Star", 1996, "Nintendo"),
        ReadmeGame("Mega Man X", 1993, "Capcom"),
        ReadmeGame("Secret of Mana", 1993, "Square"),
        ReadmeGame("Star Fox", 1993, "Nintendo"),
        ReadmeGame("Street Fighter II Turbo", 1993, "Capcom"),
        ReadmeGame("Super Castlevania IV", 1991, "Konami"),
        ReadmeGame("Super Mario Kart", 1992, "Nintendo"),
        ReadmeGame("Super Mario RPG", 1996, "Square"),
        ReadmeGame("Super Mario World", 1990, "Nintendo", "EUR"),
        ReadmeGame("Super Metroid", 1994, "Nintendo"),
        ReadmeGame("Super Punch-Out!!", 1994, "Nintendo"),
        ReadmeGame("Yoshi's Island", 1995, "Nintendo"),
        ReadmeGame("Zelda: A Link to the Past", 1991, "Nintendo", "JPN"),
    )

private val DREAMCAST =
    listOf(
        ReadmeGame("Crazy Taxi", 2000, "Sega"),
        ReadmeGame("Ikaruga", 2002, "Sega", "JPN"),
        ReadmeGame("Jet Set Radio", 2000, "Sega"),
        ReadmeGame("Marvel vs. Capcom 2", 2000, "Capcom"),
        ReadmeGame("Phantasy Star Online", 2000, "Sega"),
        ReadmeGame("Power Stone", 1999, "Capcom"),
        ReadmeGame("Rez", 2001, "Sega", "JPN"),
        ReadmeGame("Shenmue", 1999, "Sega", "JPN"),
        ReadmeGame("Skies of Arcadia", 2000, "Sega"),
        ReadmeGame("Sonic Adventure", 1999, "Sega"),
        ReadmeGame("Soulcalibur", 1999, "Namco"),
        ReadmeGame("Space Channel 5", 1999, "Sega", "EUR"),
    )

private val PLAYSTATION =
    listOf(
        ReadmeGame("Castlevania: Symphony of the Night", 1997, "Konami"),
        ReadmeGame("Chrono Cross", 2000, "Square"),
        ReadmeGame("Crash Bandicoot", 1996, "Sony"),
        ReadmeGame("Final Fantasy Tactics", 1997, "Square"),
        ReadmeGame("Final Fantasy VII", 1997, "Square"),
        ReadmeGame("Gran Turismo 2", 1999, "Sony", "EUR"),
        ReadmeGame("Metal Gear Solid", 1998, "Konami"),
        ReadmeGame("Parasite Eve", 1998, "Square"),
        ReadmeGame("Resident Evil 2", 1998, "Capcom"),
        ReadmeGame("Ridge Racer Type 4", 1998, "Namco", "JPN"),
        ReadmeGame("Silent Hill", 1999, "Konami"),
        ReadmeGame("Spyro the Dragon", 1998, "Sony"),
        ReadmeGame("Suikoden II", 1998, "Konami"),
        ReadmeGame("Tekken 3", 1998, "Namco"),
        ReadmeGame("Tomb Raider", 1996, "Eidos"),
        ReadmeGame("Vagrant Story", 2000, "Square"),
        ReadmeGame("Wipeout 2097", 1996, "Sony", "EUR"),
        ReadmeGame("Xenogears", 1998, "Square"),
    )

private val ZELDA_SYSTEMS =
    mapOf(
        "nes" to listOf(ReadmeGame("The Legend of Zelda", 1987, "Nintendo"), ReadmeGame("Zelda II: The Adventure of Link", 1988, "Nintendo")),
        "n64" to listOf(ReadmeGame("The Legend of Zelda: Majora's Mask", 2000, "Nintendo"), ReadmeGame("The Legend of Zelda: Ocarina of Time", 1998, "Nintendo")),
        "gb" to listOf(ReadmeGame("The Legend of Zelda: Link's Awakening", 1993, "Nintendo")),
        "gbc" to listOf(ReadmeGame("The Legend of Zelda: Oracle of Seasons", 2001, "Nintendo")),
        "gba" to listOf(ReadmeGame("The Legend of Zelda: The Minish Cap", 2004, "Nintendo")),
    )

internal val READMEGAMES: Map<String, List<ReadmeGame>> = mapOf("snes" to SNES, "dreamcast" to DREAMCAST, "psx" to PLAYSTATION) + ZELDA_SYSTEMS

/** The synopsis of the games shown with their details panel, plain and factual. */
internal val READMEDESCRIPTIONS: Map<String, String> =
    mapOf(
        "Super Metroid" to "Samus Aran returns to planet Zebes to recover a stolen Metroid larva, exploring a vast connected world and gaining new abilities along the way.",
        "Crazy Taxi" to "Drive a cab through a busy city, picking up fares and racing the clock to drop them off. Tips depend on how fast and how daringly you drive.",
        "Chrono Trigger" to "A boy and his friends travel through time, from the distant past to the end of the world, to stop a catastrophe that will destroy the future.",
        "Super Mario World" to "Mario and Luigi run, jump and ride Yoshi across Dinosaur Land to rescue Princess Toadstool from Bowser.",
        "Zelda: A Link to the Past" to "Link sets out to rescue seven maidens and stop a wizard who has opened a passage to a dark mirror of Hyrule.",
    )

/** Row of [title] in system [id]'s list here. */
internal fun readmeIndexOf(
    id: String,
    title: String,
): Int = READMEGAMES.getValue(id).indexOfFirst { it.title == title }

/** [content] with the systems named in [READMEGAMES] holding exactly those titles (ids and order of the fixture's rows reused). */
internal fun withRealGames(content: HomeContent): HomeContent =
    content.withEntries(
        content.entries.map { entry ->
            val games = READMEGAMES[entry.id]
            if (entry.kind != EntryKind.System || games == null) {
                entry
            } else {
                entry.copy(rows = games.mapIndexed { i, g -> entry.rows[i].copy(title = g.title, region = g.region) })
            }
        },
    )

/** Year, publisher and a rating for every title here, as the details column and the grid's bar read them. */
internal fun readmeDetails(title: String): GameDetails? {
    val game = READMEGAMES.values.flatten().firstOrNull { it.title == title } ?: return null
    if (title == "Super Metroid") return GameDetails(year = 1994, publisher = "Nintendo", genre = "Action, Adventure", players = "1", rating = 17, description = "")
    val rating = 14 + (title.length % 6)
    return GameDetails(year = game.year, publisher = game.publisher, genre = "Action", players = "1", rating = rating, description = "")
}

/** [content] with system [id] cut to its first [count] rows, so a grid ends on a whole row. */
internal fun limitedTo(
    content: HomeContent,
    id: String,
    count: Int,
): HomeContent = content.withEntries(content.entries.map { if (it.id == id) it.copy(rows = it.rows.take(count)) else it })
