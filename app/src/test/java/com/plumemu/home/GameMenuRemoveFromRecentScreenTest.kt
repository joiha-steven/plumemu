package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
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

    private fun show() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games)) }
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
        down(4) // Play, Play with, Favorite, Hide, Remove from Recent
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
        down(4)
        send(Action.Enter)
        assertEquals(listOf("recent-0"), games.removedFromRecent)
        send(Action.Play)
        assertTrue("no game is under the focus: the Recent entry is", games.launched.isEmpty())
        send(Action.Menu)
        assertFalse("START on the systems column opens no game menu", isPanelOpen())
    }
}
