package com.plumemu.game

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.sound.Outcome
import com.plumemu.sound.Reported
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the game panel reports for each press: moves, confirms, backs, and the presses that do nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMenuSoundsTest {
    private val game = GameRef("7", "Zork Quest 2")
    private val reported = Reported()

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) {}.also { reported.listenTo(it.feel) }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.press(action: Action): List<Outcome> {
        onAction(action)
        return reported.drain()
    }

    private fun GameMenuController.chooseRow(key: String): List<Outcome> {
        val index = panel!!.content.focusableRows.indexOfFirst { it.key == key }
        return press(Action.TapItem(Column.Details, index))
    }

    @Test
    fun movesConfirmsAndBacks() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            assertEquals(listOf(Outcome.Moved), menu.press(Action.Move(Direction.Down)))
            assertEquals(listOf(Outcome.WentBack), menu.press(Action.Back))
            assertEquals(emptyList<Outcome>(), menu.press(Action.Back)) // closed: not consumed, nothing to say
        }

    @Test
    fun aPlayPressedWhileALaunchRunsIsSilent() =
        runMenu {
            val actions = FakeGameActions().apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.openMenu(game)
            assertEquals("the first Play starts it", listOf(Outcome.Activated), menu.chooseRow("Play"))
            menu.openMenu(game)
            assertEquals(emptyList<Outcome>(), menu.chooseRow("Play"))
            actions.launchGate!!.complete(Unit)
        }

    @Test
    fun infoBackRowGoesBackAndFixNameMovesAreMoves() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            assertEquals(listOf(Outcome.Activated), menu.chooseRow("Info"))
            assertEquals(listOf(Outcome.WentBack), menu.press(Action.Enter))
            menu.chooseRow("Name")
            reported.drain()
            assertEquals(listOf(Outcome.Activated), menu.chooseRow("FixName"))
            val typing = (menu.panel!!.screen as PanelScreen.FixName).typing
            typing.onQueryChange("galfen")
            typing.showResults(typing.query, typing.finder!!.find(typing.query))
            assertEquals("the field to the first result", listOf(Outcome.Moved), menu.press(Action.Move(Direction.Down)))
            assertEquals("and back to the field", listOf(Outcome.Moved), menu.press(Action.Move(Direction.Up)))
            assertEquals("already on the field", emptyList<Outcome>(), menu.press(Action.Move(Direction.Up)))
        }
}
