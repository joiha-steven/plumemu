package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An app game's menu has App info (Android's page) instead of Info; a file game keeps Info; a refusal is told. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppGameInfoMenuTest {
    private val game = GameRef("7", "Celeste")

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) { }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.keys() = panel!!.content.focusableRows.map { it.key }

    private fun GameMenuController.choose(id: AppKindEntryId) = onAction(Action.TapItem(Column.Details, keys().indexOf(id.name)))

    @Test
    fun anAppGameListsAppInfoAndNoInfoAndPressingItOpensAndroidsPage() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(isAppGame = true) })
            val menu = controller(actions)
            menu.openMenu(game)
            assertTrue(AppKindEntryId.AppInfo.name in menu.keys())
            assertFalse(GameMenuEntryId.Info.name in menu.keys())
            val row = menu.panel!!.content.focusableRows.first { it.key == AppKindEntryId.AppInfo.name }
            assertEquals(R.string.app_menu_info, (row.label as UiText.Res).id)
            menu.choose(AppKindEntryId.AppInfo)
            assertEquals(listOf("7"), actions.appInfos)
            assertNull("Android's screen is coming up: the menu closes", menu.panel)
        }

    @Test
    fun aFileGameKeepsInfoAndNoAppInfo() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            assertTrue(GameMenuEntryId.Info.name in menu.keys())
            assertFalse(AppKindEntryId.AppInfo.name in menu.keys())
        }

    @Test
    fun aRefusalIsSaidOverTheMenuNotSilent() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(isAppGame = true) }).apply { appInfoResult = AppToolResult.Failed }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.choose(AppKindEntryId.AppInfo)
            val shown = checkNotNull(menu.panel)
            assertTrue(shown.screen is PanelScreen.Message)
            assertEquals(listOf(UiText.Res(R.string.app_tool_error_body)), shown.content.body)
        }
}
