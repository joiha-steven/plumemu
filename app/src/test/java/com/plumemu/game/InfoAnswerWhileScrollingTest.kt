package com.plumemu.game

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Info opens at once and reads the name's source and an arcade game's set in the background. Up and
 * Down meanwhile (people press quickly from the menu) scroll the facts; the answer must still show.
 */
class InfoAnswerWhileScrollingTest {
    private val game = GameRef("7", "Zork Quest 2")

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun TestScope.infoOpen(actions: FakeGameActions): GameMenuController {
        val menu = GameMenuController(actions, this) {}
        menu.openMenu(game)
        val index = menu.panel!!.content.focusableRows.indexOfFirst { it.key == GameMenuEntryId.Info.name }
        menu.onAction(Action.TapItem(Column.Details, index))
        return menu
    }

    private fun GameMenuController.info(): GameFacts = (panel!!.screen as PanelScreen.Info).facts

    /** What InfoFacts does while it shows facts taller than the panel: every press moves them. */
    private fun GameMenuController.factsCanScroll(): MutableList<Boolean> {
        val presses = mutableListOf<Boolean>()
        (panel!!.screen as PanelScreen.Info).scroll.mover = { isDown -> presses += isDown; true }
        return presses
    }

    @Test
    fun theArcadeSetArrivesAfterADown() =
        runMenu {
            val gate = CompletableDeferred<Unit>()
            val actions = FakeGameActions(facts = { sampleFacts(isArcade = true) }).apply { arcadeSetGate = gate }
            val menu = infoOpen(actions)
            val presses = menu.factsCanScroll()
            menu.onAction(Action.Move(Direction.Down))
            assertEquals("the facts scrolled", listOf(true), presses)
            gate.complete(Unit)
            assertEquals(ArcadeSetFacts("dinou", listOf("USA 930201")), menu.info().arcadeSet)
            menu.onAction(Action.Move(Direction.Down))
            assertEquals("the screen that came with the answer scrolls the same facts", listOf(true, true), presses)
        }

    @Test
    fun theNameSourceArrivesAfterAnUpAndADown() =
        runMenu {
            val gate = CompletableDeferred<Unit>()
            val name = NameFacts(NameOrigin.GameCode, NameCertainty.Sure, null, hasFix = false, sourceInPack = true)
            val actions = FakeGameActions(facts = { sampleFacts(name = name) }).apply { nameSourceGate = gate }
            val menu = infoOpen(actions)
            val presses = menu.factsCanScroll()
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Move(Direction.Up))
            assertEquals(listOf(true, false), presses)
            gate.complete(Unit)
            assertEquals("Test DAT", menu.info().name?.source)
        }

    @Test
    fun aDownWithNothingToScrollChangesNothing() =
        runMenu {
            val gate = CompletableDeferred<Unit>()
            val actions = FakeGameActions(facts = { sampleFacts(isArcade = true) }).apply { arcadeSetGate = gate }
            val menu = infoOpen(actions)
            // InfoFacts' answer when the facts fit (or that end shows): nothing moves.
            var asked = 0
            (menu.panel!!.screen as PanelScreen.Info).scroll.mover = { asked++; false }
            val before = menu.panel
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Move(Direction.Up))
            assertEquals("the scroll was asked", 2, asked)
            assertEquals("the panel is the very same one", true, menu.panel === before)
            gate.complete(Unit)
            assertEquals(ArcadeSetFacts("dinou", listOf("USA 930201")), menu.info().arcadeSet)
        }
}
