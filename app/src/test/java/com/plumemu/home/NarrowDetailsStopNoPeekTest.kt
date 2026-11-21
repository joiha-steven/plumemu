package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The narrow details stop shows no peek of the games list: the list is wholly off screen, the details start at the list's margin, and Back (B, its hint) returns to the list. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class NarrowDetailsStopNoPeekTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val toGames = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter)
    private var state: StripState? = null

    private fun atDetailsStop() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(FakeHomeContent.withArtwork(), toGames, onAction = { state?.dispatch(it) }, onState = { state = it }) } }
        compose.waitForIdle()
        compose.runOnIdle { state!!.dispatch(Action.Move(Direction.Right)) }
        compose.waitForIdle()
        assertEquals(Column.Details, compose.runOnIdle { state!!.focus.column })
    }

    @Test
    fun noPartOfTheGamesListIsOnScreenAtTheDetailsStop() {
        atDetailsStop()
        val right = compose.onAllNodesWithTag(HomeTags.GAMES_PAGE, useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot().right
        assertTrue("the list ends at or left of the screen edge, right = $right", right <= 1.dp)
    }

    @Test
    fun theDetailsStartAtTheListsLeftMargin() {
        atDetailsStop()
        val left = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).getUnclippedBoundsInRoot().left
        assertEquals("details start at the 24 dp margin", 24f, left.value, 0.5f)
    }

    @Test
    fun theBackHintReturnsToTheList() {
        atDetailsStop()
        compose.onNodeWithTag(HomeTags.hint(Action.Back), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(Column.Games, compose.runOnIdle { state!!.focus.column })
    }

    @Test
    fun theBackKeyReturnsToTheList() {
        atDetailsStop()
        compose.runOnIdle { state!!.dispatch(Action.Back) }
        compose.waitForIdle()
        assertEquals(Column.Games, compose.runOnIdle { state!!.focus.column })
    }
}
