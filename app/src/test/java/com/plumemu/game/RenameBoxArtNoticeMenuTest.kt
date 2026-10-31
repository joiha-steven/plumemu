package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * After a rename, the game menu says why box art was not looked up by the new name (no Wi-Fi,
 * sign-in, pause, daily limit, server closed), and says nothing when it was looked up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RenameBoxArtNoticeMenuTest {
    private val game = GameRef("7", "Zork Quest 2")

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) {}

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.chooseRow(key: String) {
        val index = panel!!.content.focusableRows.indexOfFirst { it.key == key }
        onAction(Action.TapItem(Column.Details, index))
    }

    /** Name… › Use my own name › "My Zork" › Save, by touch. */
    private fun GameMenuController.saveOwnName() {
        openMenu(game)
        chooseRow(GameMenuEntryId.Name.name)
        chooseRow(NameEntryId.OwnName.name)
        (panel!!.screen as PanelScreen.OwnName).typing.onTextChange("My Zork")
        onAction(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
    }

    @Test
    fun aRenameThatCouldBeLookedUpClosesThePanelAndSaysNothing() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)

            menu.saveOwnName()

            assertEquals(listOf("7" to "My Zork"), actions.ownNames)
            assertNull(menu.panel)
        }

    @Test
    fun noWifiIsSaidOverTheList() =
        runMenu {
            val actions = FakeGameActions().apply { renameNotice = RenameBoxArtNotice.NoWifi }
            val menu = controller(actions)

            menu.saveOwnName()

            assertEquals("the name is saved", listOf("7" to "My Zork"), actions.ownNames)
            val shown = menu.panel!!
            assertEquals(PanelScreen.Message(renameBoxArtMessage(RenameBoxArtNotice.NoWifi)), shown.screen)
            assertEquals(UiText.Res(R.string.game_rename_box_art_title), shown.content.title)
            assertEquals(listOf(UiText.Res(R.string.game_rename_box_art_no_wifi)), shown.content.body)
            menu.chooseRow("ok")
            assertNull(menu.panel)
        }

    @Test
    fun aFailedWriteShowsTheSaveFailureNotTheNotice() =
        runMenu {
            val actions = FakeGameActions().apply {
                renameNotice = RenameBoxArtNotice.NoWifi
                writeError = java.io.IOException("disk")
            }
            val menu = controller(actions)

            menu.saveOwnName()

            assertEquals(PanelScreen.Message(saveFailedMessage()), menu.panel!!.screen)
        }

    @Test
    fun everyReasonHasItsOwnLineAndTheTimeWhenKnown() {
        val cases =
            listOf(
                RenameBoxArtNotice.NoWifi to UiText.Res(R.string.game_rename_box_art_no_wifi),
                RenameBoxArtNotice.SignInNeeded to UiText.Res(R.string.game_rename_box_art_sign_in),
                RenameBoxArtNotice.Paused to UiText.Res(R.string.game_rename_box_art_paused),
                RenameBoxArtNotice.DailyLimit(5_000) to UiText.Format(R.string.game_rename_box_art_daily_limit, listOf(UiText.ClockTime(5_000))),
                RenameBoxArtNotice.ServerClosed(6_000) to UiText.Format(R.string.game_rename_box_art_server_closed, listOf(UiText.ClockTime(6_000))),
            )
        for ((notice, line) in cases) {
            val message = renameBoxArtMessage(notice)
            assertEquals("$notice", UiText.Res(R.string.game_rename_box_art_title), message.title)
            assertEquals("$notice", listOf(line), message.body)
            assertEquals("$notice", listOf("ok"), message.focusableRows.map { it.key })
        }
    }
}
