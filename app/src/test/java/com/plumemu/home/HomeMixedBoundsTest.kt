package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Recent with box art on Nova 576 at the narrow details stop the list is
 * slid out until 56 dp of it show, the right-hand text sits in 0 to 42 dp and a wide short name
 * (SCUMM, 47.6 dp) would be cut at the left. There the rows show the year as before; everywhere else
 * the short name stays.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeMixedBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val toRecent = listOf(Action.TapItem(Column.Systems, 0), Action.Enter)

    private fun showRecent(extra: List<Action>) = compose.setContent { PlumTheme { HomeAt(FakeHomeContent.mixedSystems(withBoxArt = true), toRecent + extra) } }

    @Test
    fun atTheNarrowDetailsStopTheRecentRowsShowTheYearNotAClippedShortName() {
        showRecent(listOf(Action.Move(Direction.Right)))
        assertEquals("no short name in the peek", 0, compose.onAllNodesWithText("SCUMM", useUnmergedTree = true).fetchSemanticsNodes().size)
        // Chrono Trigger's year (1995) is the right-hand text, fully inside the 56 dp peek.
        val year = compose.onAllNodesWithText("1995", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the year is shown in place of the short name", year.isNotEmpty())
        val left = compose.onAllNodesWithText("1995", useUnmergedTree = true)[0].getUnclippedBoundsInRoot().left
        assertTrue("the right-hand text starts at or after the left edge ($left)", left >= 0.dp)
    }

    @Test
    fun atTheGamesStopTheShortNameIsShownWhole() {
        showRecent(emptyList())
        val box = compose.onNodeWithText("SCUMM", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("SCUMM is inside the 400 dp list, left edge ${box.left}", box.left >= 72.dp && box.right <= 72.dp + 400.dp)
        assertEquals("Chrono Trigger shows SNES", 1, compose.onAllNodesWithText("SNES", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
