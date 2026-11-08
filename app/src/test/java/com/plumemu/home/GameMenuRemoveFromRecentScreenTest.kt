package com.plumemu.home

import androidx.compose.runtime.getValue
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
 * a removal the focus is on the next game, or on the Recent entry once Recent is empty.
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

    private fun show(gridSystems: Set<String> = emptySet()) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games), gridSystems = gridSystems) }
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
        content = HomeContent.of(
            recent = board.entries[0].rows.filterNot { it.id == key },
            favorites = board.entries[1].rows,
            apps = board.entries[2].rows,
            systems = board.entries.drop(FIRST_SYSTEM_INDEX).map { SystemGames(it.id, it.name.orEmpty(), it.rows) },
        )
    }

    @Test
    fun startOnARecentRowListsItAndOnFavoritesOrASystemListDoesNot() {
        show()
        send(Action.Enter, Action.Menu) // Recent is the first entry
        assertTrue("Recent: Remove from Recent is listed", hasRemoveRow())
        send(Action.Back, Action.Back, Action.Move(Direction.Down), Action.Enter, Action.Menu)
        assertTrue("the menu opened on Favorites", isPanelOpen())
        assertFalse("Favorites: not listed", hasRemoveRow())
        send(Action.Back, Action.Back)
        down(FakeHomeContent.BOARD_SNES_INDEX - 1)
        send(Action.Enter, Action.Menu)
        assertTrue("the menu opened on a system list", isPanelOpen())
        assertFalse("a system list: not listed", hasRemoveRow())
    }

    @Test
    fun removingAMiddleRowFocusesTheNextGame() {
        games.onRemoveFromRecent = ::dropFromRecent
        show()
        send(Action.Enter, Action.Move(Direction.Down), Action.Menu)
        down(REMOVE_ROW) // Play, Play with, Favorite, Name, Info, Remove from Recent (Hide is last)
        send(Action.Enter)
        assertEquals(listOf("recent-1"), games.removedFromRecent)
        assertFalse("the menu is gone", isPanelOpen())
        send(Action.Play)
        assertEquals("the next game took its place", listOf("recent-2"), games.launched)
    }

    @Test
    fun removingTheOnlyRowFocusesTheRecentEntry() {
        content = HomeContent.of(
            recent = content.entries[0].rows.take(1),
            favorites = content.entries[1].rows,
            apps = content.entries[2].rows,
            systems = content.entries.drop(FIRST_SYSTEM_INDEX).map { SystemGames(it.id, it.name.orEmpty(), it.rows) },
        )
        games.onRemoveFromRecent = ::dropFromRecent
        show()
        send(Action.Enter, Action.Menu)
        down(REMOVE_ROW)
        send(Action.Enter)
        assertEquals(listOf("recent-0"), games.removedFromRecent)
        send(Action.Play)
        assertTrue("no game is under the focus: the Recent entry is", games.launched.isEmpty())
        send(Action.Menu)
        assertFalse("START on the systems column opens no game menu", isPanelOpen())
    }

    private fun removeFirstRecentGame(gridSystems: Set<String> = emptySet()) {
        games.onRemoveFromRecent = ::dropFromRecent
        show(gridSystems)
        send(Action.Enter, Action.Menu)
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
        removeFirstRecentGame(gridSystems = setOf(HomeContent.RECENT_ID))
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
