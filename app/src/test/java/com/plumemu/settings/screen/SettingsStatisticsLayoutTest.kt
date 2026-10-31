package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.textsize.findTagged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Statistics' left column: one text start for every row (with a cover, without one,
 * focused or not, with and without box art set up), the same room above and below a title whatever
 * its lines, and the total as the page's big figure above the small lines.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatisticsLayoutTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var focus by mutableIntStateOf(0)

    private fun show(
        figures: PlayStatistics,
        focusedIndex: Int,
    ) {
        focus = focusedIndex
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                StatisticsShots.WithCovers { SettingsAt(StatisticsShots.focusAt(focus), content = StatisticsShots.contentOf(figures)) }
            }
        }
        compose.waitForIdle()
    }

    private fun textStarts(rowCount: Int): List<Float> {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        return (0 until rowCount).map { index ->
            val text = findTagged(root, SettingsTags.statisticText(index))
            assertNotNull("row $index has no text block", text)
            text!!.positionInRoot.x
        }
    }

    private fun assertOneEdge(
        figures: PlayStatistics,
        focusedIndexes: List<Int>,
    ) {
        show(figures, focusedIndexes.first())
        val rows = statisticRowsOf(StatisticsState.Read(figures)).size
        for (focused in focusedIndexes) {
            focus = focused
            compose.waitForIdle()
            val starts = textStarts(rows)
            assertEquals("text starts with the cursor on $focused: $starts", 1, starts.map { Math.round(it * 10) }.toSet().size)
        }
    }

    @Test
    fun everyRowsTextStartsOnOneLineWithCoversAndWithout() {
        // Row 2 is the first game (a cover), 4 the game with no saved art, 5 the system, 6 a plain figure.
        assertOneEdge(StatisticsShots.played, listOf(0, 2, 4, 5, 6))
    }

    @Test
    fun everyRowsTextStartsOnOneLineWithoutBoxArtSetUp() {
        val noArt = StatisticsShots.played.copy(hasBoxArt = false)
        assertOneEdge(noArt, listOf(0, 2, 6))
    }

    @Test
    fun aThreeLineTitleKeepsTheRoomAOneLineTitleHas() {
        val long = "The Legend of Zelda: A Link to the Past Four Swords Anniversary Edition Collector's Version Director's Cut Part Two"
        val figures = StatisticsShots.played.let { it.copy(topGames = listOf(it.topGames[0], it.topGames[1].copy(title = long), it.topGames[2])) }
        show(figures, 0)
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()

        // Whole-node numbers (a node's bounds are clipped to the screen, its position and size are not).
        fun room(index: Int): Pair<Float, Float> {
            val row = findTagged(root, SettingsTags.statistic(index))!!
            val text = findTagged(root, SettingsTags.statisticText(index))!!
            val top = text.positionInRoot.y - row.positionInRoot.y
            return top to (row.size.height - top - text.size.height)
        }
        val oneLine = room(2)
        val manyLines = room(3)
        val tall = findTagged(root, SettingsTags.statistic(3))!!.size.height
        val short = findTagged(root, SettingsTags.statistic(2))!!.size.height
        assert(tall > short + 20) { "the long title did not wrap to extra lines ($short vs $tall px)" }
        // Text is centred in the row, so equal room above and below; and the same above in both rows.
        assertEquals("one line, room above and below", oneLine.first, oneLine.second, 1f)
        assertEquals("three lines, room above and below", manyLines.first, manyLines.second, 1f)
        assertEquals("room above a one-line and a three-line title", oneLine.first, manyLines.first, 1f)
    }
}
