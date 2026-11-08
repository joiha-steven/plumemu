package com.plumemu.home

import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GameRef
import com.plumemu.game.rememberGameMenuController
import com.plumemu.home.search.SearchState
import com.plumemu.sound.Feel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * X types in Search's keyboard even while Remove from Recent's Undo is offered: the strip's router gives Search the
 * action before Undo sees it. (Search cannot be opened during an offer by the screen itself, X being Undo then, so the
 * two states are put together here.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class RecentUndoSearchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun xTypesASpaceInSearchAndTheOfferStays() {
        val games = FakeGameActions()
        val search = SearchState()
        lateinit var router: InputRouter
        lateinit var menu: com.plumemu.game.GameMenuController
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme {
                val content = FakeHomeContent.board()
                val state = rememberHomeStripState(content, emptySet(), false)
                menu = rememberGameMenuController(games)
                val keys = rememberGridSizeKeys(emptySet(), { _, _ -> }, {})
                val targets = HomeActionTargets(state, menu, search, Feel(), keys, {}, { content }, { content }, { HomeCallbacks(games = games) })
                FollowRouterActions(router, targets, onEachAction = {})
            }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            search.open()
            router.textEntry = search.textEntry
            menu.undo.offer(GameRef("recent-0", "Game"))
        }
        compose.waitForIdle()
        compose.runOnIdle { router.send(Action.Search) }
        compose.waitForIdle()
        assertNotNull("Undo is still offered", menu.undo.game)
        assertTrue("nothing was restored", games.restoredToRecent.isEmpty())
        assertEquals("X typed a space in Search", " ", search.query)
    }
}
