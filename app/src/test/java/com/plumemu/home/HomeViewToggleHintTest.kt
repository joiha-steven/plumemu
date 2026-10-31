package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * By touch the SELECT hint ("Grid view" /
 * "List view") is the only way to switch a list and its grid, while B has the system's back gesture, so B Back
 * gives way before SELECT. START Menu and Y Favorite are not in the drop order. A games list's bar at 576 x 432 has 528 dp;
 * A, Y, X, START and SELECT with four 20 dp gaps need, measured: at Largest 614 (Sans), 670 (Serif),
 * 628 (Mono), so there SELECT goes and START and Y stay; at the default size 507 (Sans), 515 (Mono), 542 (Serif),
 * so SELECT stays except in Serif, 14 dp short.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeViewToggleHintTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun isPlaced(action: Action): Boolean =
        compose.onAllNodesWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }

    /** Shows the main screen at [actions] in each font at [size] and lists "font: hint" for each of [kept] that is not drawn. */
    private fun droppedHints(
        actions: List<Action>,
        kept: List<Action>,
        size: TextSize = TextSize.Largest,
        gridIds: Set<String> = emptySet(),
        fonts: List<PlumFont> = PlumFont.entries,
    ): List<String> {
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) {
                HomeAt(FakeHomeContent.withArtwork(), actions, gridIds = gridIds)
            }
        }
        return fonts.flatMap { each ->
            font = each
            compose.waitForIdle()
            kept.filterNot { isPlaced(it) }.map { "$each: $it" }
        }
    }

    /** Serif at the default size is 14 dp short of SELECT (see the class comment). */
    private val fontsWithRoom = listOf(PlumFont.Sans, PlumFont.Mono)

    private val recentGames = listOf(Action.TapItem(Column.Systems, 0), Action.Enter)

    @Test
    fun aSystemsListKeepsGridViewAtTheDefaultSizeOn576() {
        assertEquals(emptyList<String>(), droppedHints(BoardGamesFocused, listOf(Action.ToggleView), TextSize.Default, fonts = fontsWithRoom))
    }

    @Test
    fun recentKeepsGridViewAtTheDefaultSizeOn576() {
        assertEquals(emptyList<String>(), droppedHints(recentGames, listOf(Action.ToggleView), TextSize.Default, fonts = fontsWithRoom))
    }

    @Test
    fun aSystemsListKeepsMenuAndFavoriteAtLargestOn576() {
        assertEquals(emptyList<String>(), droppedHints(BoardGamesFocused, listOf(Action.Menu, Action.ToggleFavorite)))
    }

    @Test
    fun recentKeepsMenuAndFavoriteAtLargestOn576() {
        assertEquals(emptyList<String>(), droppedHints(recentGames, listOf(Action.Menu, Action.ToggleFavorite)))
    }

    @Test
    fun aGridKeepsListViewAtLargestOn576() {
        val snes = FakeHomeContent.withArtwork().entries[FakeHomeContent.BOARD_SNES_INDEX].id
        assertEquals(emptyList<String>(), droppedHints(BoardGamesFocused, listOf(Action.ToggleView), gridIds = setOf(snes)))
    }
}
