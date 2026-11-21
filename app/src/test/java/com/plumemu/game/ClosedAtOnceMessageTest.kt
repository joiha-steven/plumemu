package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** "<emulator> closed right away" tells the person to try another emulator: its first button does it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ClosedAtOnceMessageTest {
    private val game = GameRef("7", "Chrono Trigger")

    @Test
    fun playWithComesFirstBackReturnsToTheMessageAndOkCloses() =
        runTest(UnconfinedTestDispatcher()) {
            val menu = GameMenuController(FakeGameActions(), this) {}
            assertTrue(menu.showMessage(game, closedAtOnceMessage("Snes9x")))
            menu.onAction(Action.Enter)
            assertTrue("Play with opens from the message", menu.panel!!.screen is PanelScreen.PlayWith)
            menu.onAction(Action.Back)
            assertEquals(UiText.Res(R.string.game_closed_at_once_title, listOf("Snes9x")), menu.panel!!.content.title)
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            assertNull("OK closes", menu.panel)
        }
}
