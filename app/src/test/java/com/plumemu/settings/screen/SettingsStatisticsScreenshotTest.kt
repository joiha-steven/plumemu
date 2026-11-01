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
 * Settings › Statistics' page, which the board does not draw: a played library with box art set up (two made-up covers and
 * the stand-in tile of a game without one), the same library never played, and the preview with the sections focused.
 * Each at the default text size and at Largest, at the two reference sizes (768x432 and 576x432, `capture`'s default).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsStatisticsScreenshotTest {
    /** Entered, the cursor on the most-played game. */
    @Composable
    private fun Played() = StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(StatisticsShots.played)) }

    @Composable
    private fun NothingPlayed() = SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed))

    /** The sections column focused: only the tiles. */
    @Composable
    private fun Preview() =
        StatisticsShots.WithCovers {
            SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Statistics), content = StatisticsShots.contentOf(StatisticsShots.played))
        }

    @Test
    fun played() = ReferenceShots.capture("settings_statistics") { Played() }

    @Test
    fun playedLargest() = ReferenceShots.capture("settings_statistics", textSize = TextSize.Largest) { Played() }

    @Test
    fun nothingPlayed() = ReferenceShots.capture("settings_statistics_empty") { NothingPlayed() }

    @Test
    fun nothingPlayedLargest() = ReferenceShots.capture("settings_statistics_empty", textSize = TextSize.Largest) { NothingPlayed() }

    @Test
    fun preview() = ReferenceShots.capture("settings_statistics_preview") { Preview() }

    @Test
    fun previewLargest() = ReferenceShots.capture("settings_statistics_preview", textSize = TextSize.Largest) { Preview() }
}
