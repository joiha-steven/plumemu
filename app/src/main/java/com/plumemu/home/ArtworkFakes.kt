package com.plumemu.home

/**
 * Stand-in details for [FakeHomeContent.withArtwork] until ScreenScraper data exists.
 * The board's titles and facts (RP5-2-games, Nova-3-details, RP5-3 and RP5-4 grids); every other
 * game gets plain generated facts. Data, not UI text: shown as given, like titles.
 */
internal object ArtworkFakes {
    /** Systems whose box art stands upright (docs/design/rules.md › Grid). */
    val portraitSystems: Set<String> = setOf("psp", "ps2", "gc", "nds")

    /** Titles the grid boards show, in their order; the Super Nintendo list adds Zelda ninth. */
    val boardTitles: Map<String, List<String>> =
        mapOf(
            "snes" to
                listOf(
                    "Chrono Trigger",
                    "Donkey Kong Country 2",
                    "EarthBound",
                    "Final Fantasy VI",
                    "Mega Man X",
                    "Super Mario World",
                    "Super Metroid",
                    "Yoshi's Island",
                    "Zelda: A Link to the Past",
                ),
            "psp" to
                listOf(
                    "Crisis Core",
                    "Daxter",
                    "God of War: Chains of Olympus",
                    "LocoRoco",
                    "Lumines",
                    "Monster Hunter Freedom Unite",
                    "Patapon",
                    "Persona 3 Portable",
                    "Tactics Ogre",
                    "Valkyria Chronicles II",
                ),
        )

    /** Year and publisher of each board title (RP5-2-games years; the grid boards' meta lines). */
    private val yearAndPublisher: Map<String, Pair<Int, String>> =
        mapOf(
            "Chrono Trigger" to (1995 to "Square"),
            "Donkey Kong Country 2" to (1995 to "Nintendo"),
            "EarthBound" to (1994 to "Nintendo"),
            "Final Fantasy VI" to (1994 to "Square"),
            "Mega Man X" to (1993 to "Capcom"),
            "Super Mario World" to (1990 to "Nintendo"),
            "Super Metroid" to (1994 to "Nintendo"),
            "Yoshi's Island" to (1995 to "Nintendo"),
            "Zelda: A Link to the Past" to (1991 to "Nintendo"),
            "Crisis Core" to (2008 to "Square Enix"),
            "Daxter" to (2006 to "Sony"),
            "God of War: Chains of Olympus" to (2008 to "Sony"),
            "LocoRoco" to (2006 to "Sony"),
            "Lumines" to (2005 to "Ubisoft"),
            "Monster Hunter Freedom Unite" to (2009 to "Capcom"),
            "Patapon" to (2008 to "Sony"),
            "Persona 3 Portable" to (2009 to "Atlus"),
            "Tactics Ogre" to (2011 to "Square Enix"),
            "Valkyria Chronicles II" to (2010 to "Sega"),
        )

    /** The details board's game (Nova-3-details, RP5-2-games): every fact filled in. */
    private val superMetroid =
        GameDetails(
            year = 1994,
            publisher = "Nintendo",
            genre = "Action, Adventure",
            players = "1",
            rating = 17,
            description =
                "Samus Aran returns to planet Zebes to recover a stolen Metroid larva, exploring a vast " +
                    "connected world and gaining new abilities along the way.",
            play = PlayFacts(lastPlayedDaysAgo = 1, playTimeMinutes = 252, emulator = "Snes9x · RetroArch"),
        )

    /** Details for [title], the [index]-th game of its list. */
    fun detailsFor(
        title: String,
        index: Int,
    ): GameDetails {
        if (title == "Super Metroid") return superMetroid
        val (year, publisher) = yearAndPublisher[title] ?: ((GENERATED_FIRST_YEAR + index % GENERATED_YEARS) to "Stand-in Publisher")
        return GameDetails(
            year = year,
            publisher = publisher,
            genre = "Action",
            players = (1 + index % 2).toString(),
            rating = GENERATED_LOWEST_RATING + index % GENERATED_RATINGS,
            description = "",
            play = PlayFacts(lastPlayedDaysAgo = null, playTimeMinutes = 0, emulator = "RetroArch"),
        )
    }

    private const val GENERATED_FIRST_YEAR = 1990
    private const val GENERATED_YEARS = 10

    /** Generated ratings out of 20: 12 to 18, so the list shows whole and half marks out of 10. */
    private const val GENERATED_LOWEST_RATING = 12
    private const val GENERATED_RATINGS = 7
}
