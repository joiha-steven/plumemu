package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Size change keeps the grid and the cells that stay in their row: they change size by layout and are not created again (a
 * rebuilt cell asks for its picture in the same frame), while the focused game stays focused and shown whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridSizeKeepsCellsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun aSizeChangeDoesNotCreateTheFirstRowsCellsAgainAndKeepsTheFocusShown() {
        val created = HashMap<Int, Int>()
        val probe = GridCellProbe { created[it] = (created[it] ?: 0) + 1 }
        var size by mutableStateOf(GridSize.Large)
        lateinit var state: StripState
        compose.setContent {
            PlumTheme {
                ProvideGridSize(size) {
                    CompositionLocalProvider(LocalGridCellProbe provides probe) {
                        HomeWithArt(rememberSource { realCoverSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it })
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals("game 0 is created once at first", 1, created[0])
        for (next in listOf(GridSize.Medium, GridSize.Small, GridSize.Large)) {
            size = next
            compose.waitForIdle()
            assertEquals("game 0 is created once after $next", 1, created[0])
            assertEquals("the focus stays on game 0", 0, compose.runOnIdle { state.focus.row })
            assertTrue("game 0 is still drawn at $next", compose.onAllNodesWithTag(HomeTags.cover(0), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val box = compose.onNodeWithTag(HomeTags.cover(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("game 0 is inside the grid at $next", box.top.value >= grid.top.value - 0.75f && box.bottom.value <= grid.bottom.value + 0.75f)
        }
    }
}
