package com.plumemu.home

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The games list runs to the hint bar and shows the rows that fit between the bars whole (`listRowsHeight`); the last whole row ends at or above the
 * hint bar (its end may stand in the bottom fade), and the next row runs on under the bar, showing no more than the room left.
 */
abstract class GamesListBottomBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected fun listEndsOnARowAboveTheHintBar(fits: Int) {
        val content = FakeHomeContent.large(10_000)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                HomeAt(content, listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter))
            }
        }
        compose.waitForIdle()
        val hint = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getBoundsInRoot()
        val shown = (0..40).filter { compose.onAllNodesWithTag(HomeTags.game(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        fun whole(row: Int): Boolean {
            val node = compose.onNodeWithTag(HomeTags.game(row), useUnmergedTree = true)
            val seen = node.getBoundsInRoot()
            val all = node.getUnclippedBoundsInRoot()
            // A row's node carries the gap after it, which the last row may leave outside the list.
            return seen.bottom.value - seen.top.value >= all.bottom.value - all.top.value - PlumDimens.listRowGap.value - 0.5f
        }
        val whole = shown.filter(::whole)
        assertEquals("whole rows shown (the rows that fit)", fits, whole.size)
        val first = compose.onNodeWithTag(HomeTags.game(whole.first()), useUnmergedTree = true).getBoundsInRoot()
        val last = compose.onNodeWithTag(HomeTags.game(whole.last()), useUnmergedTree = true).getBoundsInRoot()
        val rowsHeight = (last.bottom.value - first.top.value)
        val expected = fits * (first.bottom.value - first.top.value)
        assertTrue("rows span $rowsHeight dp of $expected", rowsHeight <= expected + 0.5f)
        assertTrue("last whole row ends at ${last.bottom.value}, not above the hint bar at ${hint.top.value}", last.bottom.value <= hint.top.value + 0.5f)
        // The row after it runs under the bar: what shows of it is the room left, and a row showing under a third of itself is left out by the list (ListsUnderBarsTest, SystemsColumnSliversTest).
        val next = shown.firstOrNull { it > whole.last() }?.let { compose.onNodeWithTag(HomeTags.game(it), useUnmergedTree = true).getBoundsInRoot() }
        if (next != null && next.bottom.value > next.top.value) assertTrue("the next row shows ${next.bottom.value - next.top.value} dp, from ${next.top.value} to ${next.bottom.value}", next.bottom.value <= hint.top.value + 0.5f && next.top.value >= last.bottom.value - 0.5f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GamesListBottom768Test : GamesListBottomBase() {
    @Test fun theListEndsOnARowAboveTheHintBar() = listEndsOnARowAboveTheHintBar(fits = 8)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GamesListBottom576Test : GamesListBottomBase() {
    @Test fun theListEndsOnARowAboveTheHintBar() = listEndsOnARowAboveTheHintBar(fits = 8)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class GamesListBottom832Test : GamesListBottomBase() {
    @Test fun theListEndsOnARowAboveTheHintBar() = listEndsOnARowAboveTheHintBar(fits = 8)
}
