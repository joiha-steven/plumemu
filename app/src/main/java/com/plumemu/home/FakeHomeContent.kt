package com.plumemu.home

import com.plumemu.coreui.theme.CoverShape

/**
 * Stand-in content until the library is wired in. D-STAGE WIRING POINT: `MainActivity` passes
 * [board] to `HomeScreen`; step D replaces that call with content built from the library (Room)
 * and this object then serves only tests and the benchmark.
 *
 * Deterministic: the same call always gives the same content. Titles, regions and counts of
 * the first systems are the design board's:
 * Recent 12, Favorites 9, Apps 41, Super Nintendo 248 … Mega Drive 173, "18 systems · 1,284 games".
 */
object FakeHomeContent {
    /** The board's seven systems in its order, then eleven more so the totals read 18 and 1,284. */
    private val boardSystems: List<Triple<String, String, Int>> =
        listOf(
            Triple("snes", "Super Nintendo", 248),
            Triple("gba", "Game Boy Advance", 96),
            Triple("psx", "PlayStation", 142),
            Triple("psp", "PSP", 64),
            Triple("nds", "Nintendo DS", 88),
            Triple("gc", "GameCube", 37),
            Triple("megadrive", "Mega Drive", 173),
            Triple("n64", "Nintendo 64", 61),
            Triple("nes", "NES", 58),
            Triple("gb", "Game Boy", 44),
            Triple("gbc", "Game Boy Color", 39),
            Triple("ps2", "PlayStation 2", 52),
            Triple("dreamcast", "Dreamcast", 27),
            Triple("saturn", "Saturn", 23),
            Triple("mastersystem", "Master System", 31),
            Triple("gamegear", "Game Gear", 26),
            Triple("pcengine", "PC Engine", 41),
            Triple("neogeo", "Neo Geo", 34),
        )

    /** The platform data's codes of the board's systems (Left edge › System codes), so a shot shows what the real column shows. */
    private val boardCodes: Map<String, String> =
        mapOf(
            "snes" to "SNS", "gba" to "GBA", "psx" to "PS1", "psp" to "PSP", "nds" to "NDS", "gc" to "GC", "megadrive" to "MD",
            "n64" to "N64", "nes" to "NES", "gb" to "GB", "gbc" to "GBC", "ps2" to "PS2", "dreamcast" to "DC", "saturn" to "SAT",
            "mastersystem" to "SMS", "gamegear" to "GG", "pcengine" to "PCE", "neogeo" to "NEO",
        )

    /** The Super Nintendo rows the board shows, with the no-art regions of RP5-2-games. */
    private val boardSnesGames: List<Pair<String, String>> =
        listOf(
            "Chrono Trigger" to "USA",
            "Donkey Kong Country 2" to "USA",
            "EarthBound" to "USA",
            "Final Fantasy VI" to "JPN",
            "Mega Man X" to "USA",
            "Super Mario World" to "EUR",
            "Super Metroid" to "USA",
            "Yoshi's Island" to "USA",
        )

    /** The row the board's systems screen remembers for Super Nintendo (Super Metroid). */
    const val BOARD_REMEMBERED_ROW = 6

    /** Where Super Nintendo sits in the system column (after Recent, Favorites, Apps). */
    const val BOARD_SNES_INDEX = FIRST_SYSTEM_INDEX

    private val regions = listOf("USA", "EUR", "JPN")

    /** The short names (platform data `abbr`) of the board's systems. */
    private val boardAbbrs =
        mapOf(
            "snes" to "SNES", "gba" to "GBA", "psx" to "PS1", "psp" to "PSP", "nds" to "DS", "gc" to "GC",
            "megadrive" to "MD", "n64" to "N64", "nes" to "NES", "gb" to "GB", "gbc" to "GBC", "ps2" to "PS2",
            "dreamcast" to "DC", "saturn" to "SAT", "mastersystem" to "SMS", "gamegear" to "GG",
            "pcengine" to "PCE", "neogeo" to "NEO",
        )

    /** Recent and Favorites rows from several systems: the case where the short name matters. */
    private val mixedGames: List<Triple<String, String, String>> =
        listOf(
            Triple("Chrono Trigger", "USA", "SNES"), Triple("Pokemon Emerald", "USA", "GBA"), Triple("The Secret of Monkey Island", "USA", "SCUMM"),
            Triple("Final Fantasy VII", "USA", "PS1"), Triple("Sonic the Hedgehog 2", "USA", "MD"),
            Triple("Super Mario 64", "USA", "N64"), Triple("The Legend of Zelda", "USA", "NES"),
            Triple("Crisis Core: Final Fantasy VII", "EUR", "PSP"), Triple("Metroid Prime", "USA", "GC"),
            Triple("Castlevania: Symphony of the Night", "USA", "PS1"), Triple("Kirby's Dream Land", "JPN", "GB"),
            Triple("Phoenix Wright: Ace Attorney", "USA", "DS"), Triple("Shenmue", "JPN", "DC"),
        )

    /** The id of the board system with short name [abbr]; "scumm" for the one the board does not list. */
    private fun systemIdOf(abbr: String): String = boardAbbrs.entries.firstOrNull { it.value == abbr }?.key ?: abbr.lowercase()

    /**
     * The board with Recent and Favorites holding games of many systems (short names in the right-hand
     * column); [withBoxArt] gives them details (years) and turns box art on.
     */
    fun mixedSystems(withBoxArt: Boolean = false): HomeContent {
        val rows =
            mixedGames.mapIndexed { i, (title, region, abbr) ->
                val row = GameRowModel("mixed-$i", title, region, systemAbbr = abbr, systemId = systemIdOf(abbr))
                if (withBoxArt) row.copy(details = ArtworkFakes.detailsFor(title, i)) else row
            }
        // A few more games with one name across systems, so a search for "final fantasy" finds several systems.
        val finalFantasy =
            mapOf(
                "gba" to "Final Fantasy Tactics Advance", "psx" to "Final Fantasy VII", "psp" to "Crisis Core: Final Fantasy VII",
                "nds" to "Final Fantasy Crystal Chronicles: Ring of Fates", "gb" to "Final Fantasy Legend",
            )
        val systems =
            boardSystems.map { (id, name, count) ->
                val extra = finalFantasy[id]?.let { listOf(GameRowModel("$id-ff", it, "USA", systemAbbr = boardAbbrs.getValue(id), systemId = id)) }.orEmpty()
                SystemGames(id, name, gamesFor(id, name, count) + extra, code = boardCodes[id].orEmpty())
            }
        return HomeContent.of(rows, rows.take(FAVORITES_COUNT).map { it.copy(isFavorite = true) }, apps(), systems, hasBoxArt = withBoxArt)
    }

    private val appNames =
        listOf("Browser", "Calculator", "Calendar", "Camera", "Clock", "Contacts", "Files", "Gallery", "Music", "Settings")

    /** The board's content. */
    fun board(): HomeContent = withSystems(boardSystems)

    /**
     * Intent extra (boolean) the scrolling benchmark sends for [large] content. Honoured only in
     * the benchmark builds (`BuildConfig.BENCHMARK_CONTENT`); debug and release ignore it.
     */
    const val EXTRA_LARGE_LIBRARY = "com.plumemu.benchmark.LARGE_LIBRARY"

    /** What `MainActivity` shows: 10,000 games only when a benchmark build asks for them, else [board]. */
    fun forLaunch(
        isBenchmarkBuild: Boolean,
        wantsLargeLibrary: Boolean,
    ): HomeContent = if (isBenchmarkBuild && wantsLargeLibrary) large() else board()

    /**
     * The board's content with box art: details for every game,
     * years in the list, the grid boards' covers (RP5-3 Super Nintendo with "Zelda: A Link to the
     * Past" ninth, RP5-4 PSP) and each system's cover shape. Covers are stand-in tiles.
     */
    fun withArtwork(): HomeContent {
        val systems =
            boardSystems.map { (id, name, count) ->
                val titles = ArtworkFakes.boardTitles[id].orEmpty()
                val games =
                    gamesFor(id, name, count).mapIndexed { i, game ->
                        val title = titles.getOrNull(i) ?: game.title
                        game.copy(title = title, details = ArtworkFakes.detailsFor(title, i))
                    }
                SystemGames(id, name, games, if (id in ArtworkFakes.portraitSystems) CoverShape.Portrait else CoverShape.Landscape, code = boardCodes[id].orEmpty())
            }
        fun withDetails(rows: List<GameRowModel>) = rows.mapIndexed { i, row -> row.copy(details = ArtworkFakes.detailsFor(row.title, i)) }
        return HomeContent.of(withDetails(recent()), withDetails(favorites()), apps(), systems, hasBoxArt = true)
    }

    /**
     * No ROM folder yet: Recent and Favorites empty, the 41 apps, no systems. Plumemu may already
     * be the home screen, so Apps must work.
     */
    fun noGames(): HomeContent = HomeContent.of(emptyList(), emptyList(), apps(), emptyList())

    /** The board with games everywhere but no favorite: Favorites is empty and the library is not (the "press Y" line). */
    fun noFavorites(): HomeContent = HomeContent.of(recent(), emptyList(), apps(), boardSystems.map { (id, name, count) -> SystemGames(id, name, gamesFor(id, name, count), code = boardCodes[id].orEmpty()) })

    /** The board's content with a Super Nintendo title far too long for one line (the ellipsis case). */
    fun longTitle(): HomeContent {
        val systems = boardSystems.map { (id, name, count) -> SystemGames(id, name, gamesFor(id, name, count), code = boardCodes[id].orEmpty()) }
        val snes = systems.first()
        val long = snes.games.first().copy(title = LONG_TITLE)
        val patched = listOf(snes.copy(games = listOf(long) + snes.games.drop(1))) + systems.drop(1)
        return HomeContent.of(recent(), favorites(), apps(), patched)
    }

    /**
     * The board with Super Nintendo games [FAVORITE_ROWS] marked favorite (the star) and Favorites
     * listing those same rows; with [longTitle] the first one has the long title (cut before its star).
     */
    fun withFavorites(longTitle: Boolean = false): HomeContent {
        val systems = boardSystems.map { (id, name, count) -> SystemGames(id, name, gamesFor(id, name, count), code = boardCodes[id].orEmpty()) }
        val snes = systems.first()
        val marked =
            snes.games.mapIndexed { i, row ->
                val titled = if (i == 0 && longTitle) row.copy(title = LONG_TITLE) else row
                if (i in FAVORITE_ROWS) titled.copy(isFavorite = true) else titled
            }
        return HomeContent.of(recent(), marked.filter { it.isFavorite }, apps(), listOf(snes.copy(games = marked)) + systems.drop(1))
    }

    /** The Super Nintendo rows [withFavorites] marks. */
    val FAVORITE_ROWS: Set<Int> = setOf(0, 2, 3, 6)

    /**
     * [totalGames] games spread over the board's eighteen systems (the scrolling benchmark
     * uses 10,000). Each system keeps its board name; titles are numbered.
     */
    fun large(totalGames: Int = 10_000): HomeContent {
        val perSystem = totalGames / boardSystems.size
        val extra = totalGames % boardSystems.size
        val counts = boardSystems.mapIndexed { i, (id, name, _) -> Triple(id, name, perSystem + if (i < extra) 1 else 0) }
        return withSystems(counts)
    }

    private fun withSystems(systems: List<Triple<String, String, Int>>): HomeContent =
        HomeContent.of(
            recent = recent(),
            favorites = favorites(),
            apps = apps(),
            systems = systems.map { (id, name, count) -> SystemGames(id, name, gamesFor(id, name, count), code = boardCodes[id].orEmpty()) },
        )

    private fun gamesFor(
        systemId: String,
        systemName: String,
        count: Int,
    ): List<GameRowModel> =
        List(count) { i ->
            val board = if (systemId == "snes") boardSnesGames.getOrNull(i) else null
            GameRowModel(
                id = "$systemId-$i",
                title = board?.first ?: "$systemName title ${i + 1}",
                region = board?.second ?: regions[i % regions.size],
                systemAbbr = boardAbbrs[systemId].orEmpty(),
                systemId = systemId,
            )
        }

    private fun recent(): List<GameRowModel> =
        List(RECENT_COUNT) { i ->
            val (title, region) = boardSnesGames[(i * 3) % boardSnesGames.size]
            GameRowModel("recent-$i", title, region, systemAbbr = "SNES", systemId = "snes")
        }

    private fun favorites(): List<GameRowModel> =
        List(FAVORITES_COUNT) { i ->
            val (title, region) = boardSnesGames[(i * 5 + 1) % boardSnesGames.size]
            GameRowModel("favorite-$i", title, region, systemAbbr = "SNES", systemId = "snes")
        }

    private fun apps(): List<GameRowModel> =
        List(APPS_COUNT) { i ->
            val name = appNames[i % appNames.size]
            GameRowModel("app-$i", if (i < appNames.size) name else "$name ${i / appNames.size + 1}", region = "")
        }

    private const val RECENT_COUNT = 12
    private const val FAVORITES_COUNT = 9
    private const val APPS_COUNT = 41
    private const val LONG_TITLE =
        "The Legend of Zelda: A Link to the Past and Four Swords, Special Anniversary Collector's Edition"
}
