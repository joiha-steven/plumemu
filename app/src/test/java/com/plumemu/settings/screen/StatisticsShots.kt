package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import com.plumemu.home.ArtPicture
import com.plumemu.home.FakeArtSource
import com.plumemu.home.ProvideHomeArt
import com.plumemu.home.RealLookingCoverImages

/** Statistics' figures for shots and fit checks: a played library with box art, and one never played. */
internal object StatisticsShots {
    val played =
        PlayStatistics(
            // 23 h 10 min: the three games, the rest of the system and a few other games.
            totalSeconds = 23 * 3_600L + 10 * 60 + 37,
            hasPlayed = true,
            thisWeekSeconds = 2 * 3_600L + 10 * 60,
            hasSessions = true,
            topGames =
                listOf(
                    TopGame(1, "Super Metroid", "Super Nintendo", 12 * 3_600L + 5 * 60, ArtPicture(METROID_COVER, 560, 400), timesPlayed = 41, lastPlayedDaysAgo = 0),
                    TopGame(2, "The Legend of Zelda: A Link to the Past", "Super Nintendo", 7 * 3_600L + 40 * 60, ArtPicture(ZELDA_COVER, 560, 400), timesPlayed = 17, lastPlayedDaysAgo = 3),
                    // No saved box front: the stand-in tile. Plays logged before sessions were kept: no times played, no day (left out of the panel).
                    TopGame(3, "Metroid Fusion", "Game Boy Advance", 55 * 60L),
                ),
            topSystem = TopSystem("snes", "Super Nintendo", 19 * 3_600L + 45 * 60),
            playedGames = 38,
            libraryGames = 412,
            gamesWithBoxArt = 301,
            boxArtGames = 412,
            hasBoxArt = true,
        )

    /** Nothing played; the library and its box art are there. */
    val nothingPlayed = PlayStatistics(libraryGames = 412, gamesWithBoxArt = 301, boxArtGames = 412, hasBoxArt = true)

    fun contentOf(figures: PlayStatistics) = SettingsContent(statistics = StatisticsState.Read(figures))

    private const val METROID_COVER = "/media/1/box-front-grid.webp"
    private const val ZELDA_COVER = "/media/2/box-front-grid.webp"

    /** Draws the covers as the home screen's review shots do (made-up box fronts, no real artwork). */
    @Composable
    fun WithCovers(content: @Composable () -> Unit) {
        val images = RealLookingCoverImages(mapOf(METROID_COVER to ("Super Metroid" to "Super Nintendo"), ZELDA_COVER to ("Zelda" to "Super Nintendo")))
        ProvideHomeArt(FakeArtSource(emptyMap(), emptyMap(), images), hasBoxArt = false, content = content)
    }

    /** The focus on Statistics' row [index]. */
    fun focusAt(index: Int) = SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Statistics, rememberedOption = mapOf(SettingsSection.Statistics to index))
}
