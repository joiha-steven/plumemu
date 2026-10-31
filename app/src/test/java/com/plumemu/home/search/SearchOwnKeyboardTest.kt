package com.plumemu.home.search

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.home.GameRowModel
import com.plumemu.sound.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Search with Plumemu's keyboard: every
 * action in typing, results and the field with the tray down. The buttons' letters (A/B after
 * "Confirm with B") are the router's: [com.plumemu.home.SearchOwnKeyboardPathTest] presses real keys.
 */
class SearchOwnKeyboardTest {
    private val hits = List(3) { SearchHit(GameRowModel("g$it", "Game $it", "USA"), "snes") }
    private val sounds = mutableListOf<Outcome>()

    private fun opened() = SearchState().apply { feel.sink = { sounds += it }; open() }

    /** Typing with [query] typed and its results answered ([withResults] or none). */
    private fun typed(
        query: String = "game",
        withResults: Boolean = true,
    ) = opened().apply {
        onAction(Action.Typed(TypedKey.Text(query)))
        showResults(query, if (withResults) hits else emptyList())
    }

    private fun SearchState.isTyping() = isOpen && inField && isTrayUp

    private fun SearchState.isOnResults() = isOpen && !inField && !isTrayUp

    private fun SearchState.isOnFieldTrayDown() = isOpen && inField && !isTrayUp

    private fun move(direction: Direction, isRepeat: Boolean = false) = Action.Move(direction, isRepeat)

    @Test
    fun entersTypingOnTheLettersWithQFocusedAndAnEmptyQuery() {
        val search = opened()
        assertTrue(search.isTyping())
        assertEquals("", search.query)
        assertEquals(0, search.caret)
        assertEquals(KeyboardPage.Letters, search.keys.page)
        assertEquals(KeyId(0, 0), search.keys.focus)
        assertFalse("no shift in Search", search.keys.hasShift)
        assertEquals(TextEntry.Keyboard, search.textEntry)
        assertFalse("Android's keyboard is never asked for", search.keyboardUp)
    }

    @Test
    fun aTypesTheFocusedKeyAndTheDPadMovesOnTheKeysWithTheMoveSound() {
        val search = opened()
        search.onAction(Action.Enter)
        assertEquals("q", search.query)
        search.onAction(move(Direction.Right))
        assertEquals(listOf(Outcome.Moved), sounds)
        search.onAction(Action.Enter)
        search.onAction(move(Direction.Down))
        search.onAction(Action.Play) // the A Play hint cannot show here, but it presses too
        assertEquals("w sits between a and s: going down, the right one", "qws", search.query)
        assertEquals(3, search.caret)
        assertTrue(search.isTyping())
    }

    @Test
    fun theKeysDeleteSpaceActionAndHideKeysDoTheirJobs() {
        val search = typed("ab")
        search.onKeyboardEvent(KeyboardEvent.Space)
        search.onKeyboardEvent(KeyboardEvent.Type('c'))
        search.onKeyboardEvent(KeyboardEvent.Delete)
        search.onKeyboardEvent(KeyboardEvent.Delete)
        assertEquals("ab", search.query)
        search.onKeyboardEvent(KeyboardEvent.Action)
        assertTrue("the action key goes to the results", search.isOnResults())
        assertEquals(0, search.row)
        search.onAction(Action.Search)
        search.onKeyboardEvent(KeyboardEvent.Hide)
        assertTrue("hide with results: the results", search.isOnResults())
        val none = typed("zork", withResults = false)
        none.onKeyboardEvent(KeyboardEvent.Action)
        assertTrue("no results: the action key does nothing", none.isTyping())
        none.onKeyboardEvent(KeyboardEvent.Hide)
        assertTrue("hide with no results: the field with the tray down", none.isOnFieldTrayDown())
        none.onKeyboardEvent(KeyboardEvent.Delete)
        none.onKeyboardEvent(KeyboardEvent.Delete)
        none.onKeyboardEvent(KeyboardEvent.Delete)
        none.onKeyboardEvent(KeyboardEvent.Delete)
        none.onKeyboardEvent(KeyboardEvent.Delete)
        assertTrue("the delete key never closes", none.isOpen)
    }

    @Test
    fun aFreshUpFromTheTopRowGoesToTheFirstResultAHeldOneStopsDownFromTheBottomDoesNothing() {
        val search = typed()
        search.onAction(move(Direction.Up, isRepeat = true))
        assertTrue("a held Up stops on the top row", search.isTyping())
        search.onAction(move(Direction.Up))
        assertTrue(search.isOnResults())
        assertEquals(0, search.row)
        val none = typed(withResults = false)
        none.onAction(move(Direction.Up))
        assertTrue("no results: it stays", none.isTyping())
        repeat(5) { none.onAction(move(Direction.Down)) }
        assertEquals("the bottom row", 3, none.keys.focus.row)
        assertTrue("Down from the bottom row: nothing", none.isTyping())
    }

    @Test
    fun bErasesBeforeTheCaretAHeldBStopsAtEmptyAndOnlyAFreshBOnEmptyCloses() {
        val search = typed("abc")
        search.onAction(Action.Erase())
        assertEquals("ab", search.query)
        repeat(10) { search.onAction(Action.Erase(isRepeat = true)) }
        assertEquals("", search.query)
        assertTrue("a held B never closes", search.isOpen)
        search.onAction(Action.Erase())
        assertFalse("a fresh B on an empty query closes", search.isOpen)
        assertEquals(Outcome.WentBack, sounds.last())
        val atStart = typed("abc")
        atStart.onAction(Action.JumpLetterPrevious)
        atStart.onAction(Action.Erase())
        assertEquals("nothing before the caret: nothing erased, still open", "abc", atStart.query)
        assertTrue(atStart.isOpen)
    }

    @Test
    fun theSystemsBackHidesTheTray() {
        val search = typed()
        search.onAction(Action.Back)
        assertTrue("with results: the first one", search.isOnResults())
        val none = typed(withResults = false)
        none.onAction(Action.Back)
        assertTrue("without: the field, tray down", none.isOnFieldTrayDown())
        assertEquals(TextEntry.Field, none.textEntry)
        none.onAction(Action.Back)
        assertFalse("Back again closes", none.isOpen)
    }

    @Test
    fun xTypesASpaceYChangesThePageStartGoesToTheResultsSelectDoesNothing() {
        val search = typed("a")
        search.onAction(Action.Search)
        assertEquals("a ", search.query)
        search.onAction(Action.ToggleFavorite)
        assertEquals(KeyboardPage.Symbols, search.keys.page)
        search.onAction(Action.Enter)
        assertEquals("the digits page: 1", "a 1", search.query)
        search.onAction(Action.ToggleFavorite)
        assertEquals(KeyboardPage.Letters, search.keys.page)
        assertNull(search.onAction(Action.ToggleView))
        assertTrue(search.isTyping())
        assertNull(search.onAction(Action.Menu))
        assertTrue("START goes to the results, never a menu", search.isOnResults())
        val none = typed(withResults = false)
        none.onAction(Action.Menu)
        assertTrue("START with no results: nothing", none.isTyping())
    }

    @Test
    fun l1R1MoveTheCaretL2R2JumpToTheEndsAndTypingGoesAtTheCaret() {
        val search = typed("mario")
        search.onAction(Action.PreviousSystem)
        search.onAction(Action.PreviousSystem)
        assertEquals(3, search.caret)
        search.onAction(Action.Enter) // q
        assertEquals("marqio", search.query)
        search.onAction(Action.JumpLetterPrevious)
        assertEquals(0, search.caret)
        search.onAction(Action.PreviousSystem)
        assertEquals("stops at the start", 0, search.caret)
        search.onAction(Action.NextSystem)
        assertEquals(1, search.caret)
        search.onAction(Action.Erase())
        assertEquals("arqio", search.query)
        search.onAction(Action.JumpLetterNext)
        assertEquals(5, search.caret)
        search.onAction(Action.NextSystem)
        assertEquals("stops at the end", 5, search.caret)
    }

    @Test
    fun aHardwareKeyboardTypesEditsMovesTheCaretAndEnterGoesToTheResults() {
        val search = opened()
        search.onAction(Action.Typed(TypedKey.Text("zeld")))
        search.onAction(Action.Typed(TypedKey.Text("a")))
        assertEquals("zelda", search.query)
        search.onAction(Action.Typed(TypedKey.CaretLeft))
        search.onAction(Action.Typed(TypedKey.CaretLeft))
        search.onAction(Action.Typed(TypedKey.Backspace))
        search.onAction(Action.Typed(TypedKey.ForwardDelete))
        assertEquals("zea", search.query)
        search.onAction(Action.Typed(TypedKey.CaretRight))
        assertEquals(3, search.caret)
        repeat(5) { search.onAction(Action.Typed(TypedKey.Backspace)) }
        assertEquals("", search.query)
        assertTrue("Backspace never closes", search.isOpen)
        search.onAction(Action.Typed(TypedKey.Text("game")))
        search.showResults("game", hits)
        search.onAction(Action.Typed(TypedKey.Enter))
        assertTrue(search.isOnResults())
    }

    @Test
    fun onTheResultsAPlaysBClosesYFavoritesStartOpensTheMenuXAndUpGoBackToTyping() {
        val search = typed()
        search.onAction(Action.Menu)
        assertEquals(TextEntry.None, search.textEntry)
        search.onAction(move(Direction.Down))
        assertEquals(SearchEffect.Play(hits[1]), search.onAction(Action.Enter))
        assertEquals(SearchEffect.Play(hits[1]), search.onAction(Action.Play))
        assertEquals(SearchEffect.ToggleFavorite(hits[1]), search.onAction(Action.ToggleFavorite))
        assertEquals(SearchEffect.OpenMenu(hits[1]), search.onAction(Action.Menu))
        search.onAction(Action.Search)
        assertTrue("X: typing again", search.isTyping())
        search.onAction(Action.Menu)
        search.onAction(move(Direction.Down))
        search.onAction(move(Direction.Up))
        search.onAction(move(Direction.Up, isRepeat = true))
        assertTrue("a held Up stops on the first result", search.isOnResults())
        search.onAction(move(Direction.Up))
        assertTrue("a fresh Up: typing again", search.isTyping())
        search.onAction(Action.Menu)
        search.onAction(Action.Back)
        assertFalse(search.isOpen)
    }

    @Test
    fun onTheFieldWithTheTrayDownAOrXTypesAgainAndAHardwareKeyboardStillTypes() {
        for (again in listOf(Action.Enter, Action.Search, Action.Play)) {
            val search = typed("zork", withResults = false)
            search.onAction(Action.Back)
            search.onAction(Action.ToggleFavorite)
            search.onAction(Action.NextSystem)
            assertTrue(search.isOnFieldTrayDown())
            search.onAction(again)
            assertTrue("$again types again", search.isTyping())
        }
        val search = typed("zork", withResults = false)
        search.onAction(Action.Back)
        search.onAction(Action.Typed(TypedKey.Backspace))
        assertEquals("zor", search.query)
        search.showResults("zor", hits)
        search.onAction(move(Direction.Down))
        assertTrue("results arrived: Down goes to them", search.isOnResults())
    }

    @Test
    fun aTapOnAResultFocusesItAndHidesTheTrayASecondPlaysALongPressOpensItsMenu() {
        val search = typed()
        assertNull(search.onAction(Action.TapItem(Column.Games, 2)))
        assertTrue(search.isOnResults())
        assertEquals(2, search.row)
        assertEquals(SearchEffect.Play(hits[2]), search.onAction(Action.TapItem(Column.Games, 2)))
        search.onAction(Action.Search)
        assertEquals(SearchEffect.OpenMenu(hits[1]), search.onAction(Action.LongPressItem(Column.Games, 1)))
        assertTrue(search.isOnResults())
        assertNull("a tap past the list does nothing", search.onAction(Action.TapItem(Column.Games, 9)))
    }

    @Test
    fun aTapOnTheQueryTypesAgainWithTheCaretWhereItLanded() {
        val search = typed("mario")
        search.onAction(Action.Menu)
        search.focusQueryAt(2)
        assertTrue(search.isTyping())
        assertEquals(2, search.caret)
        search.focusQueryAt(99)
        assertEquals(5, search.caret)
    }

    @Test
    fun theTypedKeyAndEraseActionsAreHandledLikeTheirButtonsWhenTheRouterLags() {
        // START then B before the router heard of the results: the person meant Back.
        val search = typed()
        search.onAction(Action.Menu)
        search.onAction(Action.Erase(isRepeat = true))
        assertTrue(search.isOpen)
        search.onAction(Action.Erase())
        assertFalse(search.isOpen)
    }

    /**
     * The index answers a query off the main thread, so a press can come before the answer. An
     * empty query finds nothing, and Search knows that at once: erased to empty, START, Up from
     * the top row and Back must not go to the old query's results still listed (seen as a flaky
     * SearchOwnKeyboardPathTest: START after six B's left typing about one run in twelve).
     */
    @Test
    fun anErasedQueryHasNoResultsAtOnceBeforeTheIndexAnswers() {
        for (leave in listOf(Action.Menu, move(Direction.Up), Action.Back)) {
            val search = typed("ab")
            repeat(2) { search.onAction(Action.Erase()) }
            assertEquals("", search.query)
            assertTrue("no results for an empty query ($leave)", search.results.isEmpty())
            assertTrue("answered: nothing to wait for ($leave)", search.isAnswered)
            search.onAction(leave)
            assertFalse("$leave does not go to the old query's results", search.isOnResults())
            search.showResults("", emptyList()) // the index's late answer changes nothing
            assertFalse(search.isOnResults())
        }
        val spaces = typed("ab")
        spaces.onAction(Action.JumpLetterPrevious)
        spaces.onAction(Action.Search)
        repeat(2) { spaces.onAction(Action.JumpLetterNext); spaces.onAction(Action.Erase()) }
        assertEquals("a query of spaces finds nothing either", " ", spaces.query)
        assertTrue(spaces.results.isEmpty())
    }

    @Test
    fun aNewSearchStartsFreshOnTheLetters() {
        val search = typed("abc")
        search.onAction(Action.ToggleFavorite)
        search.onAction(move(Direction.Right))
        search.close()
        search.open()
        assertEquals(KeyboardPage.Letters, search.keys.page)
        assertEquals(KeyId(0, 0), search.keys.focus)
        assertEquals("", search.query)
        assertTrue(search.isTyping())
    }
}
