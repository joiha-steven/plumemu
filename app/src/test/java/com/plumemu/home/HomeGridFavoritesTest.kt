package com.plumemu.home

import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GamePanelTags
import com.plumemu.game.sampleFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Favorites in a system shown as a grid: Y toggles the focused cover's game, and so does the long-press menu's Favorite entry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeGridFavoritesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private val content = FakeHomeContent.board()
    private val snes = content.entries[FakeHomeContent.BOARD_SNES_INDEX]

    private fun show() {
        // The menu's entry reads what was stored last, as the database would answer.
        games.facts = { key -> sampleFacts(title = "Game $key", isFavorite = games.favorites.lastOrNull { it.first == key }?.second ?: false) }
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme { HomeScreen(content, router, HomeCallbacks(games = games), gridSystems = setOf(snes.id)) }
        }
        compose.waitForIdle()
    }

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    private fun openGridOnSecondGame() {
        send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter)
        send(Action.Move(Direction.Right))
    }

    private fun isPanelOpen() = compose.onAllNodesWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun yAddsTheFocusedGameToFavoritesAndTheNextYTakesItOut() {
        show()
        openGridOnSecondGame()
        val key = snes.rows[1].id
        send(Action.ToggleFavorite)
        assertEquals("Y adds the focused cover's game", listOf(key to true), games.favorites)
        send(Action.ToggleFavorite)
        assertEquals("Y again takes it out", listOf(key to true, key to false), games.favorites)
    }

    @Test
    fun theLongPressMenuFavoriteEntryAddsAndRemoves() {
        show()
        openGridOnSecondGame()
        val key = snes.rows[1].id
        repeat(2) { round ->
            compose.onNodeWithTag(HomeTags.cover(1), useUnmergedTree = true).performTouchInput { longClick() }
            compose.waitForIdle()
            assertTrue("round $round: a long press opens the menu", isPanelOpen())
            compose.onNodeWithTag(GamePanelTags.row(GameMenuEntryId.Favorite.name), useUnmergedTree = true).performTouchInput { click() }
            compose.waitForIdle()
        }
        assertEquals("the entry adds, then removes", listOf(key to true, key to false), games.favorites)
    }
}
