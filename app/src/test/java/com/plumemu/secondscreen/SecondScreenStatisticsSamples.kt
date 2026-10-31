package com.plumemu.secondscreen

import com.plumemu.home.RealLookingCoverImages
import com.plumemu.settings.screen.PlayStatistics
import com.plumemu.settings.screen.StatisticsShots
import com.plumemu.settings.screen.TopSystem

/** The statistics card's figures for its fit tests and shots: Settings › Statistics' own samples. */
internal object SecondScreenStatisticsSamples {
    /** Played, box art set up: three games (the third without a saved box front), a system, both library lines. */
    val withCovers: PlayStatistics = StatisticsShots.played

    /** Signed out of ScreenScraper: no cover slots, no box-art line, the coverage not counted. */
    val signedOut: PlayStatistics =
        withCovers.copy(
            hasBoxArt = false,
            gamesWithBoxArt = 0,
            topGames = withCovers.topGames.map { it.copy(cover = null) },
        )

    val nothingPlayed: PlayStatistics = StatisticsShots.nothingPlayed

    /** Every title as long as the longest the library may hold, and a long system name. */
    val longNames: PlayStatistics =
        withCovers.copy(
            topGames = withCovers.topGames.map { it.copy(title = LONG_TITLE) },
            topSystem = TopSystem("nes", "Nintendo Entertainment System (Famicom and Family Computer Disk System)", 19 * 3_600L + 45 * 60),
        )

    val cases: List<Pair<String, PlayStatistics>> =
        listOf("with covers" to withCovers, "signed out" to signedOut, "nothing played" to nothingPlayed, "long names" to longNames)

    /** Draws the saved box fronts as made-up covers (no real artwork in the repository). */
    fun covers(): RealLookingCoverImages =
        RealLookingCoverImages(
            withCovers.topGames.mapNotNull { game -> game.cover?.let { it.path to (game.title to game.systemName) } }.toMap(),
        )
}
