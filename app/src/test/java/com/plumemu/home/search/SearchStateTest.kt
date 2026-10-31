package com.plumemu.home.search

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.home.GameRowModel
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search with Android's keyboard (the old path, [KeyboardChoice.Android], Settings › Controls ›
 * Keyboard set to Android): the field, the results, and what each press or tap asks of the screen. Plumemu's
 * keyboard: [SearchOwnKeyboardTest].
 */
class SearchStateTest {
    private val hits = List(3) { SearchHit(GameRowModel("g$it", "Game $it", "USA"), "snes") }

    private fun opened(withResults: Boolean = true) =
        SearchState(KeyboardChoice.Android).apply {
            open()
            onQueryChange("game")
            showResults("game", if (withResults) hits else emptyList())
        }

    private val down = Action.Move(Direction.Down)
    private val up = Action.Move(Direction.Up)

    @Test
    fun opensOnTheFieldWithNothingListed() {
        val search = SearchState(KeyboardChoice.Android).apply { open() }
        assertTrue(search.isOpen && search.inField)
        assertTrue(search.results.isEmpty())
    }

    /** The index answers off the main thread; an emptied field finds nothing at once, so Down cannot reach the old results. */
    @Test
    fun anEmptiedFieldHasNoResultsAtOnceBeforeTheIndexAnswers() {
        val search = opened()
        search.onQueryChange("")
        assertTrue(search.results.isEmpty())
        assertTrue(search.isAnswered)
        search.onAction(down)
        assertTrue("Down stays on the field", search.inField)
    }

    @Test
    fun downLeavesTheFieldForTheResultsAndUpFromTheFirstGoesBack() {
        val search = opened()
        search.onAction(down)
        assertFalse("the keyboard gives way", search.inField)
        assertEquals(0, search.row)
        search.onAction(down)
        search.onAction(down)
        search.onAction(down)
        assertEquals("stops at the last", 2, search.row)
        search.onAction(up)
        search.onAction(up)
        search.onAction(Action.Move(Direction.Up, isRepeat = true))
        assertFalse("a held Up stops on the first result", search.inField)
        search.onAction(up)
        assertTrue("a fresh Up returns to the field", search.inField)
    }

    @Test
    fun aOnTheFieldGoesToTheResultsAndOnAResultPlaysIt() {
        val search = opened()
        val asks = search.keyboard.asks
        assertNull(search.onAction(Action.Enter))
        assertTrue("A on the field opens the keyboard, it does not leave the field", search.inField)
        assertEquals(asks + 1, search.keyboard.asks)
        search.onAction(down)
        assertFalse("Down goes to the results", search.inField)
        search.onAction(down)
        assertEquals(SearchEffect.Play(hits[1]), search.onAction(Action.Enter))
        assertEquals(SearchEffect.OpenMenu(hits[1]), search.onAction(Action.Menu))
        assertEquals(SearchEffect.ToggleFavorite(hits[1]), search.onAction(Action.ToggleFavorite))
    }

    @Test
    fun onTheFieldStartAndYDoNothing() {
        val search = opened()
        assertNull(search.onAction(Action.Menu))
        assertNull(search.onAction(Action.ToggleFavorite))
        assertTrue(search.inField)
    }

    @Test
    fun withNoMatchTheCursorStaysOnTheField() {
        val search = opened(withResults = false)
        search.onAction(down)
        search.onAction(Action.Enter)
        assertTrue(search.inField)
        assertTrue(search.isAnswered)
    }

    @Test
    fun bClosesAndXGoesBackToTheField() {
        val search = opened()
        search.onAction(down)
        search.onAction(Action.Search)
        assertTrue("X edits the query again", search.inField)
        search.onAction(Action.Back)
        assertFalse(search.isOpen)
        assertEquals("", search.query)
    }

    @Test
    fun aTapFocusesAResultAndASecondTapPlaysItALongPressOpensItsMenu() {
        val search = opened()
        assertNull(search.onAction(Action.TapItem(Column.Games, 2)))
        assertEquals(2, search.row)
        assertEquals(SearchEffect.Play(hits[2]), search.onAction(Action.TapItem(Column.Games, 2)))
        assertEquals(SearchEffect.OpenMenu(hits[0]), search.onAction(Action.LongPressItem(Column.Games, 0)))
        assertEquals(0, search.row)
        assertNull("a tap past the list does nothing", search.onAction(Action.TapItem(Column.Games, 9)))
    }

    @Test
    fun anOldAnswerIsDroppedAndAShorterListKeepsTheCursorInside() {
        val search = opened()
        search.onAction(down)
        search.onAction(down)
        search.onAction(down)
        search.onQueryChange("game 1")
        search.showResults("game", hits) // the answer to the old query arrives late
        assertFalse(search.isAnswered)
        search.showResults("game 1", hits.take(1))
        assertEquals(0, search.row)
    }

    @Test
    fun theStripsOwnActionsAreIgnored() {
        val search = opened()
        for (action in listOf(Action.NextSystem, Action.PreviousSystem, Action.JumpLetterNext, Action.ToggleView, Action.OpenSettings)) {
            assertNull(search.onAction(action))
        }
        assertTrue(search.isOpen)
    }

    // Push right to play on a result: the first Right arms it, the second plays it.
    private val right = Action.Move(Direction.Right)

    private fun onResults(clock: LongArray = longArrayOf(0L)) =
        opened().apply {
            pushRightPlays = true
            pushRightArm = com.plumemu.coreinput.strip.PushRightArm { clock[0] }
            onAction(down)
        }

    @Test
    fun pushRightOffRightOnAResultDoesNothing() {
        val search = opened().apply { onAction(down) }
        assertNull(search.onAction(right))
        assertNull(search.onAction(right))
        assertFalse(search.isPushRightArmed)
    }

    @Test
    fun rightThenRightOnAResultPlaysIt() {
        val search = onResults()
        assertNull("the first Right only arms", search.onAction(right))
        assertTrue(search.isPushRightArmed)
        assertEquals(SearchEffect.Play(hits[0]), search.onAction(right))
        assertFalse(search.isPushRightArmed)
    }

    @Test
    fun rightDownRightAndRightAfterTheTimeoutDoNotPlay() {
        val clock = longArrayOf(0L)
        val search = onResults(clock)
        search.onAction(right)
        search.onAction(down)
        assertNull(search.onAction(right))
        clock[0] = com.plumemu.coreinput.strip.PushRightArm.TIMEOUT_MS + 1
        assertNull("armed too long ago", search.onAction(right))
    }

    @Test
    fun aHeldRightOnAResultNeverPlays() {
        val search = onResults()
        search.onAction(right)
        repeat(4) { assertNull(search.onAction(Action.Move(Direction.Right, isRepeat = true))) }
    }

    @Test
    fun resultsSwappedBetweenTheTwoRightsDoNotPlayTheGameNowOnTheRow() {
        val search = onResults()
        search.onAction(right)
        assertTrue(search.isPushRightArmed)
        // A late answer puts another game on the armed row.
        val other = listOf(SearchHit(GameRowModel("x", "Other", "USA"), "snes")) + hits
        search.showResults("game", other)
        assertFalse(search.isPushRightArmed)
        assertNull(search.onAction(right))
    }
}
