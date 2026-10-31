package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.StatisticsShots
import com.plumemu.settings.screen.StatisticsState
import com.plumemu.settings.screen.statisticRowsOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Statistics at every text size, the way `TextSizeScreensTest` checks the other
 * Settings sections (kept apart: that file is at the length limit): at the six sizes in Plum Sans
 * and at Large and Largest in Serif and Mono, no text is ellipsized, clipped, too short or on top
 * of another, and the focused row is fully on screen: the first game (cover, two-line title), the
 * version at the end of the list, the calm line of a library never played, and the failed read.
 */
abstract class SettingsStatisticsFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun check(
        name: String,
        focusedIndex: Int,
        ignoring: (String) -> Boolean = { false },
        screen: @Composable () -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { screen() } }
        val combinations =
            TextSize.entries.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            textProblems(root, FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS), isLargest = each == TextSize.Largest)).filterNot(ignoring).mapTo(failures) { "$label: $it" }
            val focused = findTagged(root, SettingsTags.statistic(focusedIndex))
            if (focused == null) failures += "$label: the focused row is not drawn" else notFullyShown(focused, root)?.let { failures += "$label: the focused row $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theFirstGame() =
        check("statistics_game", 1) {
            StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(1), content = StatisticsShots.contentOf(StatisticsShots.played)) }
        }

    /** The game panel beside a most-played game (cover, title, system, facts, "A to play"): whole at every size and font. */
    @Test
    fun theGamePanelWithArt() =
        check("statistics_panel_art", 2) {
            StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(2), content = StatisticsShots.contentOf(StatisticsShots.played)) }
        }

    /** A game with no saved box front and no stored play log: the stand-in tile and only the play time. */
    @Test
    fun theGamePanelWithoutArtOrPlayLog() =
        check("statistics_panel_no_art", 4) {
            StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(4), content = StatisticsShots.contentOf(StatisticsShots.played)) }
        }

    @Test
    fun theGamePanelWithAVeryLongTitle() {
        val long = "The Legend of Zelda: The Adventures of a Link to the Past and Other Very Long Stories Told Twice"
        val figures = StatisticsShots.played.copy(topGames = StatisticsShots.played.topGames.map { it.copy(title = long) })
        // A 96-letter title ends in "…" (the row after three lines, the panel after two); anything else wrong still fails.
        check("statistics_panel_long_title", 2, ignoring = { it.endsWith("is ellipsized") }) { StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(2), content = StatisticsShots.contentOf(figures)) } }
    }

    @Test
    fun theVersionAtTheEnd() {
        val last = statisticRowsOf(StatisticsState.Read(StatisticsShots.played)).lastIndex
        check("statistics_version", last) {
            StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(last), content = StatisticsShots.contentOf(StatisticsShots.played)) }
        }
    }

    /**
     * At Largest the page scrolled to its end ends above the hint bar: the last row is whole and its
     * bottom is above the bar's top (the bar's first key, B Back, stands for the bar), in the three
     * fonts. Before the fix the last row was half under the bar.
     */
    @Test
    fun theLastRowClearsTheHintBar() {
        val last = statisticRowsOf(StatisticsState.Read(StatisticsShots.played)).lastIndex
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest, font = font)) {
                StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(last), content = StatisticsShots.contentOf(StatisticsShots.played)) }
            }
        }
        val failures = mutableListOf<String>()
        for (each in listOf(PlumFont.Sans, PlumFont.Serif, PlumFont.Mono)) {
            font = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val row = findTagged(root, SettingsTags.statistic(last))
            val hint = findTagged(root, SettingsTags.HINT_BACK)
            if (row == null || hint == null) {
                failures += "${each.name}: row or hint bar not drawn"
            } else if (row.boundsInRoot.bottom > hint.boundsInRoot.top + 0.5f) {
                failures += "${each.name}: the last row ends at ${row.boundsInRoot.bottom}, the hint bar starts at ${hint.boundsInRoot.top} on ${widthDp}x$heightDp"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The same when a finger scrolled the page to its end with the cursor still on the first row. */
    @Test
    fun scrolledToTheEndTheLastRowClearsTheHintBar() {
        val last = statisticRowsOf(StatisticsState.Read(StatisticsShots.played)).lastIndex
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(StatisticsShots.played)) }
            }
        }
        repeat(6) { compose.onNodeWithTag(SettingsTags.OPTION_LIST).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val row = findTagged(root, SettingsTags.statistic(last))
        val hint = findTagged(root, SettingsTags.HINT_BACK)
        assertTrue("row or hint bar not drawn", row != null && hint != null)
        assertTrue(
            "the last row ends at ${row!!.boundsInRoot.bottom}, the hint bar starts at ${hint!!.boundsInRoot.top} on ${widthDp}x$heightDp",
            row.boundsInRoot.bottom <= hint.boundsInRoot.top + 0.5f,
        )
    }

    /** A five-digit hour total is one line, inside the column and whole (or cut with an ellipsis, never clipped). */
    @Test
    fun aHugeTotalStaysInItsRow() {
        val figures = StatisticsShots.played.copy(totalSeconds = 12_345 * 3_600L + 59 * 60)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(figures)) }
            }
        }
        compose.waitForIdle()
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val figure = findTagged(root, SettingsTags.statisticValue(0))
        val row = findTagged(root, SettingsTags.statistic(0))
        assertTrue("the total is not drawn", figure != null && row != null)
        assertTrue("the total is cut by the screen: ${notFullyShown(figure!!, root)}", notFullyShown(figure, root) == null)
        assertTrue("the total pokes out of its row (${figure.boundsInRoot} in ${row!!.boundsInRoot})", figure.boundsInRoot.right <= row.boundsInRoot.right + 0.5f)
    }

    /** The failed read: a two-line label and a two-line line under it at the largest sizes. */
    @Test
    fun unreadable() = check("statistics_unreadable", 0) { SettingsAt(StatisticsShots.focusAt(0), content = SettingsContent(statistics = StatisticsState.Failed)) }

    @Test
    fun nothingPlayed() =
        check("statistics_empty", 0) { SettingsAt(StatisticsShots.focusAt(0), content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed)) }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatisticsFit768Test : SettingsStatisticsFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsStatisticsFit837Test : SettingsStatisticsFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsStatisticsFit667Test : SettingsStatisticsFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsStatisticsFit576Test : SettingsStatisticsFitBase(576, 432)
