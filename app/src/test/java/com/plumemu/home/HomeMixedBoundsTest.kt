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
 * slid out wholly, so none of its text is on screen; everywhere else the short name stays.
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
    fun atTheNarrowDetailsStopNoRecentRowTextIsOnScreen() {
        showRecent(listOf(Action.Move(Direction.Right)))
        for (text in listOf("SCUMM", "1995")) {
            val nodes = compose.onAllNodesWithText(text, useUnmergedTree = true)
            for (i in 0 until nodes.fetchSemanticsNodes().size) assertTrue("$text is left of the screen edge", nodes[i].getUnclippedBoundsInRoot().right <= 1.dp)
        }
    }

    @Test
    fun atTheGamesStopTheShortNameIsShownWhole() {
        showRecent(emptyList())
        val box = compose.onNodeWithText("SCUMM", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("SCUMM is inside the list (480 dp, to the right margin: no details column at 576), left edge ${box.left}", box.left >= 72.dp && box.right <= 72.dp + 480.dp)
        assertEquals("Chrono Trigger shows SNES", 1, compose.onAllNodesWithText("SNES", useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}
