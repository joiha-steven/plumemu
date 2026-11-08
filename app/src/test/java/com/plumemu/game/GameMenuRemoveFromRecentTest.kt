package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Game menu › Remove from Recent: only in a menu opened from Recent, removes once, says when it could not. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMenuRemoveFromRecentTest {
    private val game = GameRef("7", "Chrono Trigger")

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) {}

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.keys() = panel!!.content.focusableRows.map { it.key }

    private fun GameMenuController.down(times: Int) = repeat(times) { onAction(Action.Move(Direction.Down)) }

    @Test
    fun theEntryPrecedesHideInAMenuOpenedFromRecent() =
        run {
            val menu = controller(FakeGameActions())
            menu.openMenu(game, fromRecent = true)
            val keys = menu.keys()
            assertEquals(GameMenuEntryId.Hide.name, keys[keys.indexOf(GameMenuEntryId.RemoveFromRecent.name) + 1])
            assertEquals(UiText.Res(R.string.game_menu_remove_from_recent), menu.panel!!.content.focusableRows.first { it.key == GameMenuEntryId.RemoveFromRecent.name }.label)
        }

    @Test
    fun noEntryFromAnyOtherListOrSearch() =
        run {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            assertFalse(GameMenuEntryId.RemoveFromRecent.name in menu.keys())
        }

    @Test
    fun theEntryRemovesTheGameOnceAndClosesTheMenu() =
        run {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game, fromRecent = true)
            menu.down(menu.keys().indexOf(GameMenuEntryId.RemoveFromRecent.name))
            menu.onAction(Action.Enter)
            assertEquals(listOf("7"), actions.removedFromRecent)
            assertNull("the list shows the change, the menu is gone", menu.panel)
        }

    @Test
    fun aTapOnTheEntryRemovesIt() =
        run {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game, fromRecent = true)
            menu.onAction(Action.TapItem(Column.Details, menu.keys().indexOf(GameMenuEntryId.RemoveFromRecent.name)))
            assertEquals(listOf("7"), actions.removedFromRecent)
        }

    @Test
    fun aFailedRemovalShowsTheSaveMessageAndRemovesNothing() =
        run {
            val actions = FakeGameActions().apply { writeError = IllegalStateException("disk") }
            val menu = controller(actions)
            menu.openMenu(game, fromRecent = true)
            menu.down(menu.keys().indexOf(GameMenuEntryId.RemoveFromRecent.name))
            menu.onAction(Action.Enter)
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            assertTrue(actions.removedFromRecent.isEmpty())
        }

    private fun TestScope.removeFromRecent(menu: GameMenuController) {
        menu.openMenu(game, fromRecent = true)
        menu.down(menu.keys().indexOf(GameMenuEntryId.RemoveFromRecent.name))
        menu.onAction(Action.Enter)
    }

    @Test
    fun aRemovalOffersUndoAndUndoPutsTheGameBackByItsKey() =
        run {
            val actions = FakeGameActions()
            val menu = controller(actions)
            removeFromRecent(menu)
            assertEquals(game, menu.undo.game)
            assertTrue(menu.undo.take())
            assertEquals(listOf("7"), actions.restoredToRecent)
            assertNull("the offer is spent", menu.undo.game)
            assertFalse("a second Undo does nothing", menu.undo.take())
            assertEquals(listOf("7"), actions.restoredToRecent)
        }

    @Test
    fun whenTheOfferTimesOutTheRemovalStaysAndUndoDoesNothing() =
        run {
            val actions = FakeGameActions()
            val menu = controller(actions)
            removeFromRecent(menu)
            testScheduler.advanceTimeBy(RecentUndo.SHOWN_MS - 1)
            assertEquals("still offered just before the end", game, menu.undo.game)
            testScheduler.advanceTimeBy(2)
            assertNull(menu.undo.game)
            assertFalse(menu.undo.take())
            assertEquals(listOf("7"), actions.removedFromRecent)
            assertTrue(actions.restoredToRecent.isEmpty())
        }

    @Test
    fun aFailedRemovalOffersNoUndo() =
        run {
            val menu = controller(FakeGameActions().apply { writeError = IllegalStateException("disk") })
            removeFromRecent(menu)
            assertNull(menu.undo.game)
        }

    @Test
    fun aFailedUndoShowsTheSaveMessageAndLogsIt() =
        run {
            val actions = FakeGameActions()
            val lines = mutableListOf<String>()
            val menu = GameMenuController(actions, this) { lines += it }
            removeFromRecent(menu)
            actions.writeError = IllegalStateException("disk")
            assertTrue(menu.undo.take())
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            assertTrue(lines.any { "restore to recent" in it && "could not be saved" in it })
            assertTrue(actions.restoredToRecent.isEmpty())
        }
}
