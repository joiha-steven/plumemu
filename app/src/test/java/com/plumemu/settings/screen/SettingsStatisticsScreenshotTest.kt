package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Statistics, which the board does not draw: a played library with
 * box art set up (two made-up covers and the stand-in tile of a game without one), and a library
 * never played. Each at the default text size and at Largest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsStatisticsScreenshotTest {
    /** The cursor on the most-played game, the explanation beside it on wide screens. */
    @Composable
    private fun Played() = StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(2), content = StatisticsShots.contentOf(StatisticsShots.played)) }

    @Composable
    private fun NothingPlayed() = SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed))

    @Test
    fun played() = ReferenceShots.capture("settings_statistics") { Played() }

    @Test
    fun playedLargest() = ReferenceShots.capture("settings_statistics", textSize = TextSize.Largest) { Played() }

    @Test
    fun nothingPlayed() = ReferenceShots.capture("settings_statistics_empty") { NothingPlayed() }

    @Test
    fun nothingPlayedLargest() = ReferenceShots.capture("settings_statistics_empty", textSize = TextSize.Largest) { NothingPlayed() }

    /** The cursor on a most-played game with a cover: the game panel beside the list. */
    @Composable
    private fun GamePanel(index: Int) = StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(index), content = StatisticsShots.contentOf(StatisticsShots.played)) }

    @Test
    fun gamePanel() = ReferenceShots.capture("settings_statistics_game_panel") { GamePanel(2) }

    @Test
    fun gamePanelLargest() = ReferenceShots.capture("settings_statistics_game_panel", textSize = TextSize.Largest) { GamePanel(2) }

    /** A game with no box front and no stored play log: the stand-in tile and the play time only. */
    @Test
    fun gamePanelNoArt() = ReferenceShots.capture("settings_statistics_game_panel_no_art") { GamePanel(4) }

    @Test
    fun gamePanelNoArtLargest() = ReferenceShots.capture("settings_statistics_game_panel_no_art", textSize = TextSize.Largest) { GamePanel(4) }
}
