package com.plumemu.game

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.launcher.choice.ChoiceSource
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.LaunchOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Emulators to get. Back from a download, an open panel that talks of them shows what is installed
 * now; a row kept with nothing to get is passed over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMenuEmulatorRefreshTest {
    private val game = GameRef("7", "Chrono Trigger")

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) {}

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.down(times: Int = 1) = repeat(times) { onAction(Action.Move(Direction.Down)) }

    private fun GameMenuController.focusedKey() = panel?.content?.focusableRows?.getOrNull(panel!!.focus)?.key

    private fun openPlayWith(menu: GameMenuController) {
        menu.openMenu(game)
        menu.down()
        menu.onAction(Action.Enter)
    }

    @Test
    fun theMissingEmulatorMessageClosesOnceItIsInstalled() =
        runMenu {
            val offer = EmulatorOffer(Epsxe.id, Epsxe.name, Epsxe.page)
            val actions = FakeGameActions(outcome = { LaunchOutcome.EmulatorMissing(7, offer, ChoiceSource.PersonForGame) })
            actions.listing = { sampleListing(installed = listOf(DuckStation), notInstalled = listOf(Epsxe)) }
            val menu = controller(actions)
            menu.play(game)
            val message = menu.panel!!
            menu.emulatorsMayHaveChanged()
            assertSame("not installed: the message stays as it is", message, menu.panel)
            actions.listing = { sampleListing(installed = listOf(DuckStation, Epsxe), notInstalled = emptyList()) }
            menu.emulatorsMayHaveChanged()
            assertNull("installed: nothing is left to say", menu.panel)
        }

    @Test
    fun playWithShowsTheEmulatorNowInstalledAndKeepsAValidFocus() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            openPlayWith(menu)
            menu.down(3)
            assertEquals("get:${Epsxe.id}", menu.focusedKey())
            val before = menu.panel!!
            menu.emulatorsMayHaveChanged()
            assertSame("nothing changed: the same panel", before, menu.panel)
            actions.listing = { sampleListing(installed = listOf(DuckStation, Beetle, Epsxe), notInstalled = listOf(RiskyEmu)) }
            menu.emulatorsMayHaveChanged()
            val after = menu.panel!!
            assertTrue(after.screen is PanelScreen.PlayWith)
            assertTrue("the installed ePSXe is a row to play with", after.content.focusableRows.any { it.command is PanelCommand.PlayWith && it.label == UiText.Plain(Epsxe.name) })
            assertFalse("no Get it for it any more", after.content.focusableRows.any { it.command == PanelCommand.GetEmulator(Epsxe.page.orEmpty(), Epsxe.name) })
            assertTrue("the focus is on a row", after.focus in after.content.focusableRows.indices)
            assertEquals(before.parent, after.parent)
        }

    @Test
    fun theMenuBehindPlayWithShowsTheEmulatorInstalledMeanwhile() =
        runMenu {
            val actions = FakeGameActions()
            actions.facts = { sampleFacts().copy(emulator = EmulatorToStart("DuckStation", isInstalled = false)) }
            val menu = controller(actions)
            openPlayWith(menu)
            fun playWithValue() = menu.panel!!.parent!!.content.focusableRows.first { it.key == GameMenuEntryId.PlayWith.name }.value
            assertNull("not installed: the row shows no emulator", playWithValue())
            actions.facts = { sampleFacts().copy(emulator = EmulatorToStart("DuckStation", isInstalled = true)) }
            actions.listing = { sampleListing(installed = listOf(DuckStation, Beetle, Epsxe), notInstalled = listOf(RiskyEmu)) }
            menu.emulatorsMayHaveChanged()
            assertEquals(UiText.Plain("DuckStation"), playWithValue())
            menu.back()
            assertEquals("Back lands on the menu with the new value", UiText.Plain("DuckStation"), menu.panel!!.content.focusableRows.first { it.key == GameMenuEntryId.PlayWith.name }.value)
        }

    @Test
    fun theMenuHasNothingToReRead() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            val menuPanel = menu.panel!!
            menu.emulatorsMayHaveChanged()
            assertSame(menuPanel, menu.panel)
        }

    @Test
    fun aReReadStillLandsWhenTheCursorMovedWhileItRan() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            openPlayWith(menu)
            val focusBefore = menu.panel!!.focus
            val gate = CompletableDeferred<Unit>()
            actions.readGate = gate
            actions.listing = { sampleListing(installed = listOf(DuckStation, Beetle, Epsxe), notInstalled = listOf(RiskyEmu)) }
            menu.emulatorsMayHaveChanged()
            menu.down()
            val moved = menu.panel!!
            assertEquals(focusBefore + 1, moved.focus)
            gate.complete(Unit)
            val after = menu.panel!!
            assertTrue("the installed ePSXe is a row to play with", after.content.focusableRows.any { it.command is PanelCommand.PlayWith && it.label == UiText.Plain(Epsxe.name) })
            assertTrue("the person's step stays", after.focus in after.content.focusableRows.indices)
        }

    private fun playWithValue(menu: GameMenuController) = menu.panel!!.content.focusableRows.first { it.key == GameMenuEntryId.PlayWith.name }.value

    @Test
    fun noEmulatorLeftIsShownOnTheMenuBehindNotTheOldName() =
        runMenu {
            val actions = FakeGameActions()
            actions.facts = { sampleFacts().copy(emulator = EmulatorToStart("DuckStation", isInstalled = true)) }
            val menu = controller(actions)
            openPlayWith(menu)
            assertEquals(UiText.Plain("DuckStation"), menu.panel!!.parent!!.content.focusableRows.first { it.key == GameMenuEntryId.PlayWith.name }.value)
            actions.facts = { sampleFacts().copy(emulator = null) }
            actions.listing = { sampleListing(installed = emptyList(), notInstalled = listOf(DuckStation, Beetle, Epsxe)) }
            menu.emulatorsMayHaveChanged()
            menu.back()
            assertNull("no emulator is left: the row names none", playWithValue(menu))
        }

    @Test
    fun aFailedFactsReadLeavesTheMenuNamingTheOldEmulator() =
        runMenu {
            val actions = FakeGameActions()
            actions.facts = { sampleFacts().copy(emulator = EmulatorToStart("DuckStation", isInstalled = true)) }
            val menu = controller(actions)
            openPlayWith(menu)
            actions.facts = { null }
            actions.listing = { sampleListing(installed = listOf(DuckStation, Beetle, Epsxe), notInstalled = emptyList()) }
            menu.emulatorsMayHaveChanged()
            menu.back()
            assertEquals(UiText.Plain("DuckStation"), playWithValue(menu))
        }

    @Test
    fun theCursorPassesOverRowsWithNothingToGet() =
        runMenu {
            val actions = FakeGameActions()
            actions.listing = { sampleListing(current = NoPageEmu.id, notInstalled = listOf(NoPageEmu, Epsxe, NoPageEmu.copy(id = "nopage2"), RiskyEmu)) }
            val menu = controller(actions)
            openPlayWith(menu)
            menu.down(3) // over the current choice, which has no page
            assertEquals("get:${Epsxe.id}", menu.focusedKey())
            menu.down() // past the last one with nothing to get: back to the top
            assertEquals(AUTOMATIC_KEY, menu.focusedKey())
            menu.down(3)
            menu.onAction(Action.Enter)
            assertEquals(listOf(Epsxe.page), actions.downloadPages)
        }

    @Test
    fun anOpenNoEmulatorMessageWithNoPageClosesOnOk() =
        runMenu {
            val actions = FakeGameActions(outcome = { LaunchOutcome.NoEmulatorForSystem(7, "psx", emptyList()) })
            actions.listing = { sampleListing(installed = emptyList(), automatic = null, notInstalled = listOf(NoPageEmu)) }
            val menu = controller(actions)
            menu.play(game)
            assertEquals("ok", menu.focusedKey())
            menu.onAction(Action.Enter)
            assertEquals(null, menu.panel)
            assertTrue(actions.downloadPages.isEmpty())
        }
}
