package com.plumemu.game

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fix box art after ScreenScraper's daily limit: Search cannot work, so the cursor never rests on it. */
class FixBoxArtDailyLimitTest {
    private var clock = 1_000L

    private fun limited(): FixBoxArtState =
        FixBoxArtState("Super Metroid", "Super Nintendo", now = { clock }).apply {
            onAction(Action.Move(Direction.Down))
            searching("Super Metroid")
            showSearch("Super Metroid", BoxArtSearchOutcome.Failed(BoxArtProblem.DailyLimit(5_000)))
        }

    @Test
    fun theCursorLandsOnNoBoxArtNotOnSearch() {
        val state = limited()
        assertTrue(state.isSearchBlocked)
        assertEquals(state.noBoxArtRow, state.cursor)
        assertFalse(fixBoxArtRows(state).first { it.key == BOX_ART_SEARCH_KEY }.isEnabled)
    }

    @Test
    fun downFromTheFieldSkipsSearchAndUpNeverReachesIt() {
        val state = limited()
        state.onAction(Action.Move(Direction.Up))
        assertEquals("up from the first usable row goes to the field", -1, state.cursor)
        state.onAction(Action.Move(Direction.Down))
        assertEquals(state.noBoxArtRow, state.cursor)
    }

    @Test
    fun theKeyboardsSearchKeyStartsNothingAndAOnSearchIsRefused() {
        val state = limited()
        state.onAction(Action.Move(Direction.Up))
        assertEquals(TypingOutcome.Handled, state.onAction(Action.TapItem(com.plumemu.coreinput.Column.Details, 0)))
        assertEquals("a tap on Search does not move the cursor", -1, state.cursor)
    }

    @Test
    fun theKeyboardsSearchKeyIsNotSilentItClosesTheKeyboardAndMovesToTheFirstActionableRow() {
        val state = limited()
        state.onAction(Action.Move(Direction.Up))
        state.focusField()
        assertTrue(state.keyboardUp)
        assertNull("nothing is handed back to run", state.onKeyboardAction())
        assertFalse("the tray goes away", state.keyboardUp)
        assertEquals(state.noBoxArtRow, state.cursor)
    }

    @Test
    fun searchIsUsableAgainOnceTheLimitsTimePasses() {
        val state = limited()
        assertEquals(4_000L, state.millisUntilSearchAllowed())
        assertTrue(state.isSearchBlocked)
        clock = 5_000L
        assertNull(state.millisUntilSearchAllowed())
        assertFalse(state.isSearchBlocked)
        assertTrue(fixBoxArtRows(state).first { it.key == BOX_ART_SEARCH_KEY }.isEnabled)
    }

    @Test
    fun theDelayedEffectsCallMarksTheLimitOver() {
        val state = limited()
        state.limitEnded()
        assertFalse(state.isSearchBlocked)
        state.searching("Super Metroid")
        state.showSearch("Super Metroid", BoxArtSearchOutcome.Failed(BoxArtProblem.DailyLimit(9_000)))
        assertTrue("a new answer blocks again", state.isSearchBlocked)
    }

    @Test
    fun otherFailuresKeepSearchUsable() {
        val state =
            FixBoxArtState("Super Metroid", "Super Nintendo").apply {
                searching("Super Metroid")
                showSearch("Super Metroid", BoxArtSearchOutcome.Failed(BoxArtProblem.Offline))
            }
        assertFalse(state.isSearchBlocked)
        assertTrue(fixBoxArtRows(state).first { it.key == BOX_ART_SEARCH_KEY }.isEnabled)
    }
}
