package com.plumemu.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The screen composes with 10,000 games and keeps the focused row on screen at the far end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeLargeLibraryTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun tenThousandGamesComposeAndTheLastRowComesIntoView() {
        val content = FakeHomeContent.large(10_000)
        lateinit var state: StripState
        compose.setContent {
            PlumTheme { HomeAt(content, listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter), onState = { state = it }) }
        }
        compose.onNodeWithTag(HomeTags.game(0), useUnmergedTree = true).assertIsDisplayed()
        // Up from the first row wraps to the last one, 555 rows down.
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Up)) }
        compose.waitForIdle()
        val last = content.entries[FakeHomeContent.BOARD_SNES_INDEX].count - 1
        assertEquals(last, state.focus.row)
        compose.onNodeWithTag(HomeTags.game(last), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.game(0), useUnmergedTree = true).assertDoesNotExist()
        // Down wraps to the top; then ten steps down go past the eight rows that fit, one row at a time.
        repeat(11) {
            compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
            compose.waitForIdle()
            compose.onNodeWithTag(HomeTags.game(state.focus.row), useUnmergedTree = true).assertIsDisplayed()
        }
        assertEquals(10, state.focus.row)
        // Scrolled just enough to keep two rows past the focus (the glide's room and one of
        // context, design/rules.md): row 12 at the bottom, so rows 0 to 2 have left the screen
        // (eight rows 40 apart fit at 768x432).
        // Row 2 is still composed, out of sight: the glide keeps one row past each edge.
        compose.onNodeWithTag(HomeTags.game(12), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.game(1), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.game(2), useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithTag(HomeTags.game(5), useUnmergedTree = true).assertIsDisplayed()
    }
}
