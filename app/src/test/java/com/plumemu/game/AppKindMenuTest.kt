package com.plumemu.game

import com.plumemu.R
import com.plumemu.coredata.db.AppKind
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The person's answer for an installed app: Show as game, Show as port and Not a game, from the Apps
 * entry's menu and from an app game's menu, with the gamepad (A) and with touch (a tap on the row).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppKindMenuTest {
    private val app = GameRef("com.example.alpha/com.example.alpha.Main", "Alpha")
    private val game = GameRef("42", "Celeste")
    private val actions = FakeGameActions()
    private val apps = FakeAppActions().apply { choosable = setOf(app.key) }

    private fun TestScope.controller() = GameMenuController(actions, this, apps) {}

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.keys() = checkNotNull(panel).content.focusableRows.map { it.key }

    private fun appGame(isPort: Boolean = false) = sampleFacts(title = "Celeste").copy(isAppGame = true, isPortApp = isPort)

    private fun GameMenuController.openGameMenu() {
        openMenu(game)
    }

    @Test
    fun theAppMenuOffersShowAsGameAndShowAsPortBetweenInfoAndUninstall() =
        run {
            apps.uninstallable = setOf(app.key)
            val menu = controller()
            menu.openAppMenu(app)
            assertEquals(listOf("Info", "ShowAsGame", "ShowAsPort", "Uninstall"), menu.keys())
            val labels = checkNotNull(menu.panel).content.focusableRows.map { (it.label as UiText.Res).id }
            assertEquals(listOf(R.string.app_menu_info, R.string.app_menu_show_as_game, R.string.app_menu_show_as_port, R.string.app_menu_uninstall), labels)
        }

    @Test
    fun anAppThatMayNotBeAGameHasNeitherEntry() =
        run {
            // Plumemu itself and Android's Settings are not in the set the actions allow.
            apps.choosable = emptySet()
            val menu = controller()
            menu.openAppMenu(app)
            assertEquals(listOf("Info"), menu.keys())
        }

    @Test
    fun showAsGameWithTheGamepadStoresTheAnswerAndClosesTheMenu() =
        run {
            val menu = controller()
            menu.openAppMenu(app)
            menu.onAction(Action.Move(com.plumemu.coreinput.Direction.Down))
            assertEquals("ShowAsGame", checkNotNull(menu.panel).content.focusableRows[checkNotNull(menu.panel).focus].key)
            menu.onAction(Action.Play)
            assertEquals(listOf(app.key to AppKind.GAME), apps.kinds)
            assertNull(menu.panel)
        }

    @Test
    fun showAsPortWithATapStoresTheAnswerAndClosesTheMenu() =
        run {
            val menu = controller()
            menu.openAppMenu(app)
            menu.onAction(Action.TapItem(Column.Details, 2))
            assertEquals(listOf(app.key to AppKind.PORT), apps.kinds)
            assertNull(menu.panel)
        }

    @Test
    fun aFailedAnswerIsToldOverTheMenuAndNothingMoves() =
        run {
            apps.kindError = IOException("disk full")
            val logs = mutableListOf<String>()
            val menu = GameMenuController(actions, this, apps) { logs += it }
            menu.openAppMenu(app)
            menu.onAction(Action.TapItem(Column.Details, 1))
            val shown = checkNotNull(menu.panel)
            assertEquals(UiText.Res(R.string.game_error_save_title), shown.content.title)
            assertTrue(apps.kinds.isEmpty())
            assertTrue("the log says what failed: $logs", logs.any { "app kind" in it && "IOException" in it })
            // B closes the message like any save failure; the app is still in Apps to try again.
            menu.onAction(Action.Back)
            assertNull(menu.panel)
        }

    @Test
    fun anAndroidGameOffersShowAsPortAndNotAGameAndAPortsAppShowAsGame() =
        run {
            actions.facts = { appGame(isPort = false) }
            val menu = controller()
            menu.openGameMenu()
            assertTrue("Show as port and Not a game on an Android game: ${menu.keys()}", "ShowAsPort" in menu.keys() && "NotAGame" in menu.keys() && "ShowAsGame" !in menu.keys())
            menu.onAction(Action.Back)

            actions.facts = { appGame(isPort = true) }
            menu.openGameMenu()
            assertTrue("Show as game and Not a game on a Ports app: ${menu.keys()}", "ShowAsGame" in menu.keys() && "NotAGame" in menu.keys() && "ShowAsPort" !in menu.keys())
        }

    @Test
    fun aRomGameHasNoneOfTheThree() =
        run {
            val menu = controller()
            menu.openGameMenu()
            assertTrue(menu.keys().none { it in setOf("ShowAsGame", "ShowAsPort", "NotAGame") })
        }

    @Test
    fun notAGameOnAnAppGameAsksFirstBecauseItsPlayTimeIsForgotten() =
        run {
            actions.facts = { appGame() }
            val menu = controller()
            menu.openGameMenu()
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf("NotAGame")))
            val ask = checkNotNull(menu.panel)
            assertEquals(UiText.Res(R.string.game_not_a_game_title), ask.content.title)
            assertEquals(listOf<UiText>(UiText.Res(R.string.game_app_not_a_game_body, listOf("Celeste"))), ask.content.body)
            assertEquals("Cancel has the focus", CANCEL_KEY, ask.content.focusableRows[ask.focus].key)
            assertTrue("nothing is stored before the answer", actions.appKinds.isEmpty())
            // A stray A cancels: back on the menu, still nothing stored.
            menu.onAction(Action.Play)
            assertTrue(checkNotNull(menu.panel).screen is PanelScreen.Menu)
            assertTrue(actions.appKinds.isEmpty())
        }

    @Test
    fun bCancelsTheNotAGameQuestion() =
        run {
            actions.facts = { appGame() }
            val menu = controller()
            menu.openGameMenu()
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf("NotAGame")))
            menu.onAction(Action.Back)
            assertTrue(checkNotNull(menu.panel).screen is PanelScreen.Menu)
            assertTrue(actions.appKinds.isEmpty())
        }

    @Test
    fun confirmingNotAGameWithATapSendsTheAppBackToApps() =
        run {
            actions.facts = { appGame() }
            val menu = controller()
            menu.openGameMenu()
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf("NotAGame")))
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(listOf(game.key to AppKind.APP), actions.appKinds)
            assertTrue("it is not the hide of Name…", actions.notGames.isEmpty() && actions.hidden.isEmpty())
            assertNull(menu.panel)
        }

    @Test
    fun confirmingNotAGameWithTheGamepadSendsTheAppBackToApps() =
        run {
            actions.facts = { appGame() }
            val menu = controller()
            menu.openGameMenu()
            repeat(menu.keys().indexOf("NotAGame")) { menu.onAction(Action.Move(com.plumemu.coreinput.Direction.Down)) }
            menu.onAction(Action.Play)
            menu.onAction(Action.Move(com.plumemu.coreinput.Direction.Down))
            menu.onAction(Action.Play)
            assertEquals(listOf(game.key to AppKind.APP), actions.appKinds)
            assertNull(menu.panel)
        }

    private class Recorder : FavoriteListener {
        val hidden = mutableListOf<String>()

        override fun requested(game: GameRef, isFavorite: Boolean) = Unit

        override fun failed(game: GameRef, isFavorite: Boolean) = Unit

        override fun hidden(game: GameRef) {
            hidden += game.key
        }
    }

    @Test
    fun onlyNotAGameTakesTheRowOutOfFavoritesShowAsPortKeepsIt() =
        run {
            actions.facts = { appGame() }
            val heard = Recorder()
            val menu = controller().also { it.favoriteListener = heard }
            menu.openGameMenu()
            repeat(menu.keys().indexOf("ShowAsPort")) { menu.onAction(Action.Move(com.plumemu.coreinput.Direction.Down)) }
            menu.onAction(Action.Play)
            assertEquals(listOf(game.key to AppKind.PORT), actions.appKinds)
            assertTrue("the row only changes system: its star stays", heard.hidden.isEmpty())

            menu.openGameMenu()
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf("NotAGame")))
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(listOf(game.key), heard.hidden)
        }

    @Test
    fun anAnswerTheLibraryCannotFollowYetIsSaidFromBothMenus() =
        run {
            apps.followed = false
            actions.kindFollowed = false
            actions.facts = { appGame() }
            val menu = controller()
            menu.openAppMenu(app)
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(UiText.Res(R.string.game_app_kind_later_title), checkNotNull(menu.panel).content.title)
            menu.onAction(Action.Back)
            assertNull(menu.panel)

            menu.openGameMenu()
            repeat(menu.keys().indexOf("ShowAsPort")) { menu.onAction(Action.Move(com.plumemu.coreinput.Direction.Down)) }
            menu.onAction(Action.Play)
            assertEquals(UiText.Res(R.string.game_app_kind_later_title), checkNotNull(menu.panel).content.title)
            assertEquals(listOf(game.key to AppKind.PORT), actions.appKinds)
        }

    @Test
    fun showAsPortFromTheGameMenuWithTheGamepad() =
        run {
            actions.facts = { appGame() }
            val menu = controller()
            menu.openGameMenu()
            repeat(menu.keys().indexOf("ShowAsPort")) { menu.onAction(Action.Move(com.plumemu.coreinput.Direction.Down)) }
            menu.onAction(Action.Play)
            assertEquals(listOf(game.key to AppKind.PORT), actions.appKinds)
            assertNull(menu.panel)
        }

    @Test
    fun aFailedAnswerFromTheGameMenuIsToldAndTheMenuIsKept() =
        run {
            actions.facts = { appGame() }
            actions.writeError = IOException("disk full")
            val menu = controller()
            menu.openGameMenu()
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf("ShowAsPort")))
            assertEquals(UiText.Res(R.string.game_error_save_title), checkNotNull(menu.panel).content.title)
            assertTrue(actions.appKinds.isEmpty())
            assertFalse(checkNotNull(menu.panel).screen is PanelScreen.Menu)
        }
}
