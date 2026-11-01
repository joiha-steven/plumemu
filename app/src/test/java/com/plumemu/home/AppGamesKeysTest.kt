package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.FakeAppActions
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An all-app-games grid (Android games) looks like Apps' grid but its rows are games: A plays through the game path (not the
 * Apps open path), START opens the game menu, Y toggles Favorite, and L1 / R1 do nothing (no Grid size write, no "Size" hint).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppGamesKeysTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var router: InputRouter
    private val apps = FakeAppActions()
    private val games = FakeGameActions()
    private val sizes = mutableListOf<GridSize>()
    private var content by mutableStateOf(appGamesBoard())

    private fun show() {
        val android = content.entries[ANDROID_GAMES_ENTRY].id
        compose.setContent {
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope) }
            PlumTheme(PlumThemeSettings.Default) {
                CompositionLocalProvider(LocalHomeAppIcons provides FakeAppIcons()) {
                    HomeScreen(content, router, HomeCallbacks(apps = apps, games = games, onGridSize = { size, _ -> sizes += size }), gridSystems = setOf(android))
                }
            }
        }
        compose.waitForIdle()
        send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Enter)
    }

    private fun send(vararg actions: Action) {
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }
    }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aPlaysTheGameNotTheAppsOpenPath() {
        show()
        assertTrue("Android games is drawn as the icon grid", count(HomeTags.APP_NAME) > 0)
        send(Action.Play)
        assertEquals("the game path launched it", listOf(AndroidGameRows[0].id), games.launched)
        assertEquals("not the Apps open path", emptyList<String>(), apps.opened)
    }

    @Test
    fun startOpensTheGameMenuNotTheAppMenu() {
        show()
        send(Action.Menu)
        assertEquals(1, count(GamePanelTags.PANEL))
        assertFalse("no App info row of the app menu", hasText("App info"))
        assertEquals(emptyList<String>(), apps.infoOpened)
    }

    @Test
    fun yTogglesFavorite() {
        show()
        send(Action.ToggleFavorite)
        assertEquals(listOf(AndroidGameRows[0].id to true), games.favorites)
    }

    @Test
    fun l1AndR1WriteNoGridSizeAndTheBarHasNoSizeHint() {
        show()
        assertTrue(count(HomeTags.APP_NAME) > 0)
        assertFalse("no Size hint", hasText("Size"))
        send(Action.NextSystem, Action.PreviousSystem, Action.NextSystem)
        assertEquals("no grid size asked", emptyList<GridSize>(), sizes.toList())
        assertTrue("still the same grid", count(HomeTags.APP_NAME) > 0)
        assertEquals("still on Android games' first game", 0, games.launched.size)
    }
}
