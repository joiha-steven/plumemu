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
                    TopGame(1, "Super Metroid", "Super Nintendo", "snes", 12 * 3_600L + 5 * 60, ArtPicture(METROID_COVER, 560, 400)),
                    TopGame(2, "The Legend of Zelda: A Link to the Past", "Super Nintendo", "snes", 7 * 3_600L + 40 * 60, ArtPicture(ZELDA_COVER, 560, 400)),
                    // No saved box front: the poster cover.
                    TopGame(3, "Metroid Fusion", "Game Boy Advance", "gba", 55 * 60L),
                    TopGame(4, "Sonic the Hedgehog 2", "Mega Drive", "md", 48 * 60L),
                    TopGame(5, "Pokemon Emerald", "Game Boy Advance", "gba", 41 * 60L),
                ),
            topSystems =
                listOf(
                    TopSystem("snes", "Super Nintendo", 19 * 3_600L + 45 * 60),
                    TopSystem("gba", "Game Boy Advance", 96 * 60L),
                    TopSystem("md", "Mega Drive", 48 * 60L),
                    TopSystem("psx", "PlayStation", 22 * 60L),
                    TopSystem("nes", "Nintendo Entertainment System", 9 * 60L),
                ),
            // Sunday first, today = Wednesday (index 3): a varied week that adds up to thisWeekSeconds.
            weekDaySeconds = listOf(25 * 60L, 0L, 55 * 60L, 50 * 60L, 0L, 0L, 0L),
            todayIndex = 3,
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
