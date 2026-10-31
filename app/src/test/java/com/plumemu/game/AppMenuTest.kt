package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Apps entry's menu through the game panel's controller: its rows, the two actions, a refusal that is told. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppMenuTest {
    private val app = GameRef("com.example.alpha/com.example.alpha.Main", "Alpha")
    private val logs = mutableListOf<String>()

    private fun TestScope.controller(apps: AppActions) = GameMenuController(FakeGameActions(), this, apps) { logs += it }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.rows() = checkNotNull(panel).content.focusableRows

    @Test
    fun theMenuIsTitledWithTheAppAndOffersAppInfoThenUninstall() =
        runMenu {
            val menu = controller(FakeAppActions().apply { uninstallable = setOf(app.key) })
            assertTrue(menu.openAppMenu(app))
            val open = checkNotNull(menu.panel)
            assertTrue(open.screen is PanelScreen.AppMenu)
            assertEquals(UiText.Plain("Alpha"), open.content.title)
            assertEquals(listOf("Info", "Uninstall"), menu.rows().map { it.key })
            // The labels a screen reader and the eye get are the same words.
            assertEquals(listOf(R.string.app_menu_info, R.string.app_menu_uninstall), menu.rows().map { (it.label as UiText.Res).id })
        }

    @Test
    fun uninstallIsHiddenForAnAppAndroidCannotRemove() =
        runMenu {
            val menu = controller(FakeAppActions())
            menu.openAppMenu(app)
            assertEquals(listOf("Info"), menu.rows().map { it.key })
        }

    @Test
    fun appInfoOpensAndroidsPageAndClosesTheMenu() =
        runMenu {
            val apps = FakeAppActions().apply { uninstallable = setOf(app.key) }
            val menu = controller(apps)
            menu.openAppMenu(app)
            menu.onAction(Action.Play)
            assertEquals(listOf(app.key), apps.infoOpened)
            assertTrue(apps.uninstalled.isEmpty())
            assertNull(menu.panel)
        }

    @Test
    fun uninstallStartsAndroidsConfirmationAndClosesTheMenu() =
        runMenu {
            val apps = FakeAppActions().apply { uninstallable = setOf(app.key) }
            val menu = controller(apps)
            menu.openAppMenu(app)
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(listOf(app.key), apps.uninstalled)
            assertTrue(apps.infoOpened.isEmpty())
            assertNull(menu.panel)
        }

    @Test
    fun aScreenAndroidCannotOpenIsSaidOverTheMenuAndBGoesBackToIt() =
        runMenu {
            val apps = FakeAppActions().apply {
                uninstallable = setOf(app.key)
                toolResult = AppToolResult.Failed
            }
            val menu = controller(apps)
            menu.openAppMenu(app)
            val appMenu = checkNotNull(menu.panel)
            menu.onAction(Action.Play)
            val shown = checkNotNull(menu.panel)
            assertTrue(shown.screen is PanelScreen.Message)
            assertEquals(UiText.Res(R.string.app_error_start_title, listOf("Alpha")), shown.content.title)
            assertEquals(listOf(UiText.Res(R.string.app_tool_error_body)), shown.content.body)
            menu.onAction(Action.Back)
            assertEquals(appMenu, menu.panel)
        }

    @Test
    fun aRowThatLeftTheListSaysSoAndOffersOk() =
        runMenu {
            val apps = FakeAppActions().apply { toolResult = AppToolResult.NotListed }
            val menu = controller(apps)
            menu.openAppMenu(app)
            menu.onAction(Action.Play)
            assertEquals(listOf(UiText.Res(R.string.app_error_not_listed_body)), checkNotNull(menu.panel).content.body)
        }

    @Test
    fun aSecondMenuIsNotOpenedOverTheFirst() =
        runMenu {
            val menu = controller(FakeAppActions())
            assertTrue(menu.openAppMenu(app))
            assertFalse(menu.openAppMenu(GameRef("other/Main", "Other")))
            assertEquals("Alpha", (checkNotNull(menu.panel).content.title as UiText.Plain).text)
        }

    @Test
    fun startClosesTheMenuLikeAGamesMenu() =
        runMenu {
            val menu = controller(FakeAppActions())
            menu.openAppMenu(app)
            menu.onAction(Action.Menu)
            assertNull(menu.panel)
        }

    @Test
    fun anAWhileTheMenuIsStillOpeningIsSwallowedAndBCancelsIt() =
        runMenu {
            val apps = FakeAppActions().apply { readGate = CompletableDeferred() }
            val menu = controller(apps)
            assertTrue(menu.openAppMenu(app))
            // The read has not finished: A must not fall through to the strip and launch the app.
            assertTrue(menu.onAction(Action.Play))
            assertTrue(apps.opened.isEmpty())
            menu.onAction(Action.Back)
            apps.readGate?.complete(Unit)
            assertNull(menu.panel)
        }

    @Test
    fun closingWhileTheMenuIsOpeningLeavesNoMenuBehind() =
        runMenu {
            val apps = FakeAppActions().apply { readGate = CompletableDeferred() }
            val menu = controller(apps)
            menu.openAppMenu(app)
            menu.close()
            apps.readGate?.complete(Unit)
            assertNull(menu.panel)
        }

    @Test
    fun aDoublePressOnUninstallStartsOneUninstall() =
        runMenu {
            val apps = FakeAppActions().apply {
                uninstallable = setOf(app.key)
                uninstallGate = CompletableDeferred()
            }
            val menu = controller(apps)
            menu.openAppMenu(app)
            menu.onAction(Action.TapItem(Column.Details, 1))
            menu.onAction(Action.TapItem(Column.Details, 1))
            apps.uninstallGate?.complete(Unit)
            assertEquals(listOf(app.key), apps.uninstalled)
        }
}
