package com.plumemu.home

import androidx.compose.runtime.getValue
import com.plumemu.coreinput.strip.StripFocus
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.plumemu.R
import com.plumemu.game.RecentUndo
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GamePanelTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Remove from Recent through the real strip: START lists it only on a Recent row, and after
 * a removal the focus is on the next game, or on the (empty) Recent folder once Recent is empty.
 * Requirement change (Collections): Recent is a folder inside Collections, a grid unless the list setting holds it, so the
 * tests reach it through Collections and keep it a list (as they always read) unless they say a grid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameMenuRemoveFromRecentScreenTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private var content by mutableStateOf(FakeHomeContent.board())

    private fun show(asGrid: Boolean = false) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games), listCollections = if (asGrid) emptySet() else setOf(HomeContent.RECENT_ID), initialFocus = StripFocus()) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    private fun down(times: Int) = send(*List(times) { Action.Move(Direction.Down) }.toTypedArray())

    private fun hasRemoveRow() =
        compose.onAllNodesWithTag(GamePanelTags.row(GameMenuEntryId.RemoveFromRecent.name), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun isPanelOpen() = compose.onAllNodesWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** Recent drops [key]; everything else as it was. */
    private fun dropFromRecent(key: String) {
        val board = content
        content = board.rebuilt(recent = board.recentRows.filterNot { it.id == key })
    }

    @Test
    fun startOnARecentRowListsItAndOnFavoritesOrASystemListDoesNot() {
        show()
        send(*OpenRecentFolder.toTypedArray(), Action.Menu) // Recent is a folder inside Collections
        assertTrue("Recent: Remove from Recent is listed", hasRemoveRow())
        send(Action.Back, Action.Back, Action.Back, Action.Move(Direction.Up), Action.Enter, Action.Menu) // out of the panel, the folder, the folder list; Favorites
        assertTrue("the menu opened on Favorites", isPanelOpen())
        assertFalse("Favorites: not listed", hasRemoveRow())
        send(Action.Back, Action.Back)
        down(FakeHomeContent.BOARD_SNES_INDEX)
        send(Action.Enter, Action.Menu)
        assertTrue("the menu opened on a system list", isPanelOpen())
        assertFalse("a system list: not listed", hasRemoveRow())
    }

    @Test
    fun removingAMiddleRowFocusesTheNextGame() {
        games.onRemoveFromRecent = ::dropFromRecent
        show()
        send(*OpenRecentFolder.toTypedArray(), Action.Move(Direction.Down), Action.Menu)
        down(REMOVE_ROW) // Play, Play with, Favorite, Name, Info, Remove from Recent (Hide is last)
        send(Action.Enter)
        assertEquals(listOf("recent-1"), games.removedFromRecent)
        assertFalse("the menu is gone", isPanelOpen())
        send(Action.Play)
        assertEquals("the next game took its place", listOf("recent-2"), games.launched)
    }

    // Expectation changed with the requirement: the emptied Recent is a folder that stays open on its one line (no entry to fall back to).
    @Test
    fun removingTheOnlyRowLeavesTheEmptyRecentFolderFocused() {
        content = content.rebuilt(recent = content.recentRows.take(1))
        games.onRemoveFromRecent = ::dropFromRecent
        show()
        send(*OpenRecentFolder.toTypedArray(), Action.Menu)
        down(REMOVE_ROW)
        send(Action.Enter)
        assertEquals(listOf("recent-0"), games.removedFromRecent)
        send(Action.Play)
        assertTrue("no game is under the focus: the empty Recent folder is", games.launched.isEmpty())
        send(Action.Menu)
        assertFalse("START on the empty folder opens no game menu", isPanelOpen())
    }

    private fun removeFirstRecentGame(asGrid: Boolean = false) {
        games.onRemoveFromRecent = ::dropFromRecent
        show(asGrid)
        send(*OpenRecentFolder.toTypedArray(), Action.Menu)
        down(REMOVE_ROW)
        send(Action.Enter)
        assertEquals(listOf("recent-0"), games.removedFromRecent)
    }

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun noticeShown() = compose.onAllNodesWithText(str(R.string.home_notice_removed_from_recent), substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theNoticeNamesUndoAndXUndoesIt() {
        removeFirstRecentGame()
        assertTrue("the top bar says it", noticeShown())
        compose.onNodeWithTag(HomeTags.hint(Action.Search), useUnmergedTree = true).assert(hasAnyDescendant(hasText(str(R.string.home_hint_undo), substring = true)))
        send(Action.Search)
        assertEquals(listOf("recent-0"), games.restoredToRecent)
        assertFalse("the notice is gone once undone", noticeShown())
        compose.onNodeWithTag(HomeTags.hint(Action.Search), useUnmergedTree = true).assert(hasAnyDescendant(hasText(str(R.string.home_hint_search), substring = true)))
    }

    @Test
    fun aTapOnTheUndoHintUndoesIt() {
        removeFirstRecentGame()
        compose.onNodeWithTag(HomeTags.hint(Action.Search), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("recent-0"), games.restoredToRecent)
        assertFalse(noticeShown())
    }

    @Test
    fun whenTheNoticeTimesOutNothingMoreHappensAndXSearchesAgain() {
        removeFirstRecentGame()
        compose.mainClock.advanceTimeBy(RecentUndo.SHOWN_MS + 500)
        compose.waitForIdle()
        assertFalse("the notice is gone", noticeShown())
        assertTrue("the removal stays", games.restoredToRecent.isEmpty())
        compose.onNodeWithTag(HomeTags.hint(Action.Search), useUnmergedTree = true).assert(hasAnyDescendant(hasText(str(R.string.home_hint_search), substring = true)))
        send(Action.Search)
        assertTrue("X is Search again: no undo", games.restoredToRecent.isEmpty())
    }

    @Test
    fun aCoverGridHasNoSearchHintSoUndoGetsItsOwnAndATapUndoes() {
        removeFirstRecentGame(asGrid = true)
        compose.onNodeWithTag(HomeTags.hint(Action.Search), useUnmergedTree = true).assert(hasAnyDescendant(hasText(str(R.string.home_hint_undo), substring = true)))
        compose.onNodeWithTag(HomeTags.hint(Action.Search), useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("recent-0"), games.restoredToRecent)
    }

    @Test
    fun openingAnotherGamesMenuEndsTheOfferAndTheLine() {
        removeFirstRecentGame()
        assertTrue(noticeShown())
        send(Action.Menu)
        assertTrue("the menu of the next game is open", isPanelOpen())
        assertFalse("the Undo line is gone while the menu is open", noticeShown())
        send(Action.Search) // X belongs to the panel now
        assertTrue("X in the menu restored nothing", games.restoredToRecent.isEmpty())
        send(Action.Back)
        assertFalse("the offer does not come back", noticeShown())
        assertTrue(games.restoredToRecent.isEmpty())
    }

    @Test
    fun undoAfterTheListChangedRestoresTheRightGame() {
        removeFirstRecentGame()
        dropFromRecent("recent-2") // another game left Recent meanwhile
        compose.waitForIdle()
        send(Action.Search)
        assertEquals(listOf("recent-0"), games.restoredToRecent)
    }

    private companion object {
        /** Steps from Play to Remove from Recent: Play with, Favorite, Name, Info, Remove (Hide stays last). */
        const val REMOVE_ROW = 5
    }
}
