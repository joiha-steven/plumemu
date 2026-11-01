package com.plumemu.home

import com.plumemu.coreui.theme.CoverShape

/*
 * Boards for the poster-cover tests and shots: a Super Nintendo of twelve realistic titles (a long one and a Japanese one (escaped: sources stay English)
 * among them) and Recent holding games of several systems in their own shapes. Signed out (no box art) unless a source gives results.
 */

/** Twelve Super Nintendo titles: the board's real ones, one very long, one Japanese. */
internal val POSTER_SNES_TITLES =
    listOf(
        "Chrono Trigger", "Donkey Kong Country 2", "EarthBound", "Final Fantasy VI", "Mega Man X", "Super Mario World",
        "Super Metroid", "Yoshi's Island", "The Legend of Zelda: A Link to the Past",
        "Teenage Mutant Ninja Turtles IV: Turtles in Time Special Collector's Edition",
        "\u30BC\u30EB\u30C0\u306E\u4F1D\u8AAC \u795E\u3005\u306E\u30C8\u30E9\u30A4\u30D5\u30A9\u30FC\u30B9", "F-Zero",
    )

/** Index of the Super Nintendo entry in [posterSnesBoard] (Recent, Favorites, Super Nintendo; no Apps). */
internal const val POSTER_SNES_INDEX = 2

/** A library with only a Super Nintendo of [POSTER_SNES_TITLES], signed out of ScreenScraper. */
internal fun posterSnesBoard(): HomeContent {
    val rows =
        POSTER_SNES_TITLES.mapIndexed { i, title ->
            GameRowModel("snes-$i", title, listOf("USA", "EUR", "JPN")[i % 3], scrapeKey = artKey("snes-$i"), systemAbbr = "SNES", systemId = "snes")
        }
    return HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows, CoverShape(realAspect("snes", 0)), code = "SFC")), showApps = false)
}

/** [posterSnesBoard] signed in: details for every game, so a source can answer for it. */
internal fun posterSnesBoardWithDetails(): HomeContent {
    val board = posterSnesBoard()
    val snes = board.entries[POSTER_SNES_INDEX]
    val rows = snes.rows.mapIndexed { i, row -> row.copy(details = ArtworkFakes.detailsFor(row.title, i)) }
    return HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows, snes.coverShape, code = "SFC")), hasBoxArt = true, showApps = false)
}

/** [MixedGridLibrary]'s systems and Recent games, signed out: Recent is entry 0, every game in its own system's shape, no pictures. */
internal fun mixedSignedOutBoard(): HomeContent {
    val library = MixedGridLibrary
    val systems = library.entries.filter { it.kind == EntryKind.System }.map { SystemGames(it.id, it.name.orEmpty(), it.rows.map { row -> row.copy(details = null) }, it.coverShape) }
    val recent = library.entries[0].rows.map { it.copy(details = null) }
    return HomeContent.of(recent, emptyList(), emptyList(), systems, showApps = false)
}
