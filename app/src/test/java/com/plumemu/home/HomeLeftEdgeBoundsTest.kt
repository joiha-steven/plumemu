package com.plumemu.home

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › Left edge: what the 48 dp peek shows while a games list is focused.
 * System codes sit where the counts sit (so the column lines up with the names when it opens), whole at Largest,
 * and only Letters draws the rail.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeLeftEdgeBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        content: HomeContent,
        edge: LeftEdgeSetting,
        size: TextSize = TextSize.Medium,
        actions: List<Action> = gamesFocused(FakeHomeContent.BOARD_SNES_INDEX),
    ) = compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(content.withLeftEdge(edge), actions) } }

    private fun gamesFocused(index: Int) = listOf(Action.TapItem(Column.Systems, index), Action.Enter, Action.TapItem(Column.Games, 0))

    private fun edgeOf(index: Int) = compose.onNode(hasTestTag(SystemRowTags.COUNT) and hasAnyAncestor(hasTestTag(HomeTags.system(index))), useUnmergedTree = true)

    private fun rowBounds(index: Int): DpRect = compose.onNodeWithTag(HomeTags.system(index), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun railExists() = compose.onAllNodesWithTagCount(HomeTags.RAIL) > 0

    private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** The widest codes the platform data has, so a clip would show here first. */
    private fun widestCodes(): HomeContent =
        HomeContent.of(
            emptyList(), emptyList(), emptyList(),
            listOf("WWR", "MXR", "W3X", "G&W", "NGC", "X68", "WSC", "SGX").map { code -> SystemGames(code, "System $code", listOf(GameRowModel("$code-1", "Game", "USA")), code = code) },
        )

    @Test
    fun countsStayInThePeekAndNoRailIsDrawn() {
        show(FakeHomeContent.board(), LeftEdgeSetting.Counts)
        compose.waitForIdle()
        edgeOf(FakeHomeContent.BOARD_SNES_INDEX).assertTextEquals("248")
        assertTrue("no rail in Game counts", !railExists())
    }

    @Test
    fun codesStandWhereTheCountsStandAndNoRailIsDrawn() {
        show(FakeHomeContent.board(), LeftEdgeSetting.Codes)
        compose.waitForIdle()
        val expected = listOf("REC", "FAV", "APP", "SNS", "GBA", "PS1", "PSP", "NDS")
        expected.forEachIndexed { index, code -> edgeOf(index).assertTextEquals(code) }
        for (index in expected.indices) {
            val row = rowBounds(index)
            val edge = edgeOf(index).getUnclippedBoundsInRoot()
            assertEquals("row $index: the code ends where a count ends, the row padding from the row's end", row.right.value - PlumDimens.systemRowPadding.value, edge.right.value, 0.5f)
        }
        assertTrue("no rail in System codes", !railExists())
    }

    @Test
    fun withTheSystemColumnOpenCodesGiveWayToCounts() {
        // Codes only stand in for the counts in the peek; the open column keeps its counts.
        show(FakeHomeContent.board(), LeftEdgeSetting.Codes, actions = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)))
        compose.waitForIdle()
        edgeOf(FakeHomeContent.BOARD_SNES_INDEX).assertTextEquals("248")
    }

    private fun assertWidestCodesFitThePeek(size: TextSize) {
        show(widestCodes(), LeftEdgeSetting.Codes, size, gamesFocused(FIRST_SYSTEM_INDEX))
        compose.waitForIdle()
        val shown = (FIRST_SYSTEM_INDEX until FIRST_SYSTEM_INDEX + 8).filter { compose.onAllNodes(hasTestTag(HomeTags.system(it)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        assertTrue("some rows are on screen: $shown", shown.size >= 4)
        for (index in shown) {
            val row = rowBounds(index)
            val edge = edgeOf(index).getUnclippedBoundsInRoot()
            // The peek (48 dp, growing with the text) is the row's last stretch: the whole code lies inside it, with the row padding right of it.
            val peekLeft = row.right.value - PlumDimens.peekWidth.value * size.heightScale
            assertTrue("$size row $index: code ${edge.left.value}..${edge.right.value}, row ${row.left.value}..${row.right.value}, peek from $peekLeft", edge.left.value >= peekLeft + 0.5f)
            assertTrue("$size row $index: code ends at ${edge.right.value} within the row", edge.right.value <= row.right.value)
        }
    }

    @Test
    fun theWidestCodesFitThePeekAtMedium() = assertWidestCodesFitThePeek(TextSize.Medium)

    @Test
    fun theWidestCodesFitThePeekAtLargest() = assertWidestCodesFitThePeek(TextSize.Largest)

    @Test
    fun lettersStillDrawTheRailAndHideTheCounts() {
        show(FakeHomeContent.board(), LeftEdgeSetting.Letters)
        compose.waitForIdle()
        assertTrue("the rail is there in Letters", railExists())
    }
}

