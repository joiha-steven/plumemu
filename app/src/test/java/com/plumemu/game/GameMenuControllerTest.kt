package com.plumemu.game

import java.io.IOException
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.launcher.EmulatorOffer
import com.plumemu.launcher.FileProblem
import com.plumemu.launcher.LaunchOutcome
import com.plumemu.launcher.ShortcutProblem
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

/** The controller's rules: one launch at a time, the menu's moves, Back, writes and failures. */
@OptIn(ExperimentalCoroutinesApi::class)
class GameMenuControllerTest {
    private val game = GameRef("7", "Chrono Trigger")
    private val logs = mutableListOf<String>()

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) { logs += it }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.focusedKey() = panel?.content?.focusableRows?.getOrNull(panel!!.focus)?.key

    private fun GameMenuController.down(times: Int = 1) = repeat(times) { onAction(Action.Move(Direction.Down)) }

    @Test
    fun aSecondPlayWhileOneRunsIsIgnored() =
        runMenu {
            val actions = FakeGameActions().apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.play(game)
            menu.play(game)
            assertEquals(listOf("7"), actions.launched)
            assertTrue(menu.isLaunching)
            actions.launchGate!!.complete(Unit)
            assertFalse(menu.isLaunching)
            menu.play(game)
            assertEquals("after the first one ends, Play works again", listOf("7", "7"), actions.launched)
            assertTrue(logs.any { it.contains("ignored") && !it.contains("Chrono") })
        }

    @Test
    fun startedShowsNothingAndAFailureShowsItsMessage() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.play(game)
            assertNull(menu.panel)
            actions.outcome = { LaunchOutcome.FileMissing(7, "Chrono Trigger (USA).sfc", FileProblem.Gone) }
            menu.play(game)
            val message = menu.panel!!
            assertEquals(UiText.Res(R.string.game_error_file_gone_title), message.content.title)
            assertNull("Back closes a message", message.parent)
        }

    @Test
    fun noEmulatorReadsTheListingForItsCandidates() =
        runMenu {
            val actions = FakeGameActions(outcome = { LaunchOutcome.NoEmulatorForSystem(7, "psx", emptyList()) })
            actions.listing = { sampleListing(installed = emptyList(), automatic = null) }
            val menu = controller(actions)
            menu.play(game)
            val content = menu.panel!!.content
            assertEquals(listOf("get:epsxe", "get:risky"), content.items.filterIsInstance<PanelItem.Row>().map { it.row.key })
            assertEquals("a legal-risk emulator has no Get it, so it is not focusable", listOf("get:epsxe"), content.focusableRows.map { it.key })
        }

    @Test
    fun theMenuOpensOnPlayAndMovesWrappingOnAFreshPressOnly() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            assertTrue(menu.panel!!.screen is PanelScreen.Menu)
            assertEquals("Play", menu.focusedKey())
            menu.onAction(Action.Move(Direction.Up, isRepeat = true))
            assertEquals("a held move stops at the top", "Play", menu.focusedKey())
            menu.onAction(Action.Move(Direction.Up))
            assertEquals("a fresh press wraps", "Info", menu.focusedKey())
            menu.down()
            assertEquals("Play", menu.focusedKey())
        }

    @Test
    fun backAndLeftCloseTheMenuAndStartClosesFromAnyDepth() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            menu.onAction(Action.Back)
            assertNull(menu.panel)
            menu.openMenu(game)
            menu.onAction(Action.Move(Direction.Left))
            assertNull(menu.panel)
            menu.openMenu(game)
            menu.down()
            menu.onAction(Action.Enter) // Play with
            assertTrue(menu.panel!!.screen is PanelScreen.PlayWith)
            menu.onAction(Action.Menu)
            assertNull(menu.panel)
            assertFalse("nothing is consumed once closed", menu.onAction(Action.Enter))
        }

    @Test
    fun backFromPlayWithAndInfoReturnsToTheMenuOnTheSameEntry() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            menu.down()
            menu.onAction(Action.Enter)
            assertTrue(menu.panel!!.screen is PanelScreen.PlayWith)
            assertEquals("the first row has the focus, not the current one", AUTOMATIC_KEY, menu.focusedKey())
            menu.onAction(Action.Back)
            assertEquals("PlayWith", menu.focusedKey())
            // Past Favorite, Hide and Name….
            menu.down(4)
            menu.onAction(Action.Enter)
            assertTrue(menu.panel!!.screen is PanelScreen.Info)
            assertEquals("Info's one row, Back, has the focus", INFO_BACK_KEY, menu.focusedKey())
            menu.onAction(Action.Enter)
            assertEquals("A on Back returns to the menu", "Info", menu.focusedKey())
            menu.onAction(Action.Enter)
            menu.onAction(Action.Back)
            assertEquals("so does B", "Info", menu.focusedKey())
        }

    /** Info has one row to stand on: Up and Down go to its facts' scroll (InfoScroll), Back keeps the focus, A still goes back. */
    @Test
    fun upAndDownInInfoScrollTheFactsAndKeepBackFocused() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            // Past Play with, Favorite, Hide and Name….
            menu.down(5)
            menu.onAction(Action.Enter)
            val info = menu.panel!!.screen as PanelScreen.Info
            // What InfoFacts does while it shows: here, a scroll that always has room.
            val presses = mutableListOf<Boolean>()
            info.scroll.mover = { isDown -> presses += isDown; true }
            val shown = menu.panel
            menu.down(3)
            assertEquals("Down three times", listOf(true, true, true), presses)
            menu.onAction(Action.Move(Direction.Up))
            assertEquals("Up once", listOf(true, true, true, false), presses)
            assertTrue("the panel is the very same one while the facts scroll", menu.panel === shown)
            assertEquals("Back keeps the focus while the facts scroll", INFO_BACK_KEY, menu.focusedKey())
            menu.onAction(Action.Enter)
            assertEquals("A on Back returns to the menu", "Info", menu.focusedKey())
            menu.onAction(Action.Enter)
            assertTrue("Info opens again with a scroll of its own", (menu.panel!!.screen as PanelScreen.Info).scroll !== info.scroll)
        }

    @Test
    fun favoriteAndHideWriteThenClose() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down(2)
            menu.onAction(Action.Enter)
            assertEquals(listOf("7" to true), actions.favorites)
            assertNull(menu.panel)
            actions.facts = { sampleFacts(isFavorite = true) }
            menu.openMenu(game)
            menu.down(2)
            menu.onAction(Action.Enter)
            assertEquals(listOf("7" to true, "7" to false), actions.favorites)
            menu.openMenu(game)
            menu.down(3)
            menu.onAction(Action.Enter)
            assertEquals(listOf("7"), actions.hidden)
            assertEquals("Hide says where the game went", UiText.Res(R.string.game_hidden_title), menu.panel!!.content.title)
            assertNull("B closes the message", menu.panel!!.parent)
            menu.onAction(Action.Enter)
            assertNull(menu.panel)
        }

    @Test
    fun aFailedHideShowsTheSaveMessageNotTheHiddenOne() =
        runMenu {
            val actions = FakeGameActions().apply { writeError = IllegalStateException("disk") }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down(3)
            menu.onAction(Action.Enter)
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
        }

    @Test
    fun aRefusedPinnedShortcutSaysWhyAndNamesItsApp() =
        runMenu {
            val actions = FakeGameActions(outcome = { LaunchOutcome.ShortcutRefused(7, "DroidDeck", ShortcutProblem.NotHomeApp) })
            val menu = controller(actions)
            menu.play(game)
            assertEquals(UiText.Res(R.string.game_error_shortcut_not_home_title), menu.panel!!.content.title)
            assertEquals(listOf(UiText.Res(R.string.game_error_shortcut_not_home_body)), menu.panel!!.content.body)
            menu.onAction(Action.Enter)
            assertNull("OK closes it", menu.panel)

            actions.outcome = { LaunchOutcome.ShortcutRefused(7, "DroidDeck", ShortcutProblem.Unavailable) }
            menu.play(game)
            assertEquals(UiText.Res(R.string.game_error_shortcut_unavailable_title_app, listOf("DroidDeck")), menu.panel!!.content.title)
            assertEquals(listOf(UiText.Res(R.string.game_error_shortcut_unavailable_body)), menu.panel!!.content.body)
            menu.onAction(Action.Enter)
            actions.outcome = { LaunchOutcome.ShortcutRefused(7, null, ShortcutProblem.Unavailable) }
            menu.play(game)
            assertEquals("the app is gone: no name", UiText.Res(R.string.game_error_shortcut_unavailable_title), menu.panel!!.content.title)
            menu.onAction(Action.Enter)

            actions.outcome = { LaunchOutcome.ShortcutRefused(7, "GameNative", ShortcutProblem.StartFailed) }
            menu.play(game)
            assertEquals(UiText.Res(R.string.game_error_shortcut_title), menu.panel!!.content.title)
            assertEquals(listOf(UiText.Res(R.string.game_error_shortcut_body_app, listOf("GameNative"))), menu.panel!!.content.body)
            menu.onAction(Action.Enter)
            actions.outcome = { LaunchOutcome.ShortcutRefused(7, null, ShortcutProblem.StartFailed) }
            menu.play(game)
            assertEquals(listOf(UiText.Res(R.string.game_error_shortcut_body)), menu.panel!!.content.body)
        }

    @Test
    fun aMessageForTheScreenIsRefusedWhileAPanelIsOpen() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            assertFalse(menu.showMessage(game, closedAtOnceMessage("Snes9x")))
            menu.close()
            assertTrue(menu.showMessage(game, closedAtOnceMessage("Snes9x")))
            assertEquals(UiText.Res(R.string.game_closed_at_once_title, listOf("Snes9x")), menu.panel!!.content.title)
        }

    @Test
    fun aTapOnAPanelRowChoosesItAndOtherColumnsAreIgnored() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.TapItem(Column.Games, 3))
            assertTrue("a strip tap does nothing while the panel is open", menu.panel!!.screen is PanelScreen.Menu)
            menu.onAction(Action.TapItem(Column.Details, 3))
            assertEquals(listOf("7"), actions.hidden)
            menu.close() // the Hidden message that replaced the menu
            menu.openMenu(game)
            menu.onAction(Action.LongPressItem(Column.Details, 5))
            assertTrue(menu.panel!!.screen is PanelScreen.Info)
        }

    @Test
    fun pickingAnEmulatorStoresItThenPlays() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down()
            menu.onAction(Action.Enter)
            menu.down(2) // RetroArch (Beetle PSX)
            menu.onAction(Action.Enter)
            assertEquals(listOf("7" to Beetle.id), actions.choices)
            assertEquals(listOf("7"), actions.launched)
            assertNull("started: the panel closes", menu.panel)
            menu.openMenu(game)
            menu.down()
            menu.onAction(Action.Enter)
            menu.onAction(Action.Enter) // Automatic
            assertEquals(listOf("7" to Beetle.id, "7" to null), actions.choices)
        }

    @Test
    fun getItOpensTheStorePageOrSaysItCannot() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down()
            menu.onAction(Action.Enter)
            menu.down(3) // ePSXe, not installed
            menu.onAction(Action.Enter)
            assertEquals(listOf(Epsxe.packageName), actions.storePages)
            assertTrue("the list stays for when the person comes back", menu.panel!!.screen is PanelScreen.PlayWith)
            actions.storeOpens = false
            menu.onAction(Action.Enter)
            assertEquals(UiText.Res(R.string.game_error_store_title), menu.panel!!.content.title)
            menu.onAction(Action.Back)
            assertTrue("Back returns to the list", menu.panel!!.screen is PanelScreen.PlayWith)
        }

    @Test
    fun rescanFromAMessageAsksForOneAndCloses() =
        runMenu {
            val actions = FakeGameActions(outcome = { LaunchOutcome.FileMissing(7, "x.sfc", FileProblem.FolderUnreachable) })
            val menu = controller(actions)
            menu.play(game)
            menu.onAction(Action.Enter)
            assertEquals(1, actions.rescans)
            assertNull(menu.panel)
        }

    @Test
    fun aFailedWriteOrReadShowsAMessageAndLogsTheClassOnly() =
        runMenu {
            val actions = FakeGameActions().apply { writeError = IOException("disk I/O error /storage/emulated/0/ROMs/x.sfc") }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down(3)
            menu.onAction(Action.Enter)
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            menu.close()
            actions.readError = IllegalStateException("closed")
            menu.openMenu(game)
            assertEquals(UiText.Res(R.string.game_error_database_title), menu.panel!!.content.title)
            assertTrue(logs.any { it.contains("IOException") })
            assertFalse("no path or message in a log line", logs.any { it.contains("/storage") || it.contains("disk I/O") })
        }

    @Test
    fun aGameGoneFromTheLibrarySaysSoInsteadOfAMenu() =
        runMenu {
            val menu = controller(FakeGameActions(facts = { null }))
            menu.openMenu(game)
            assertEquals(UiText.Res(R.string.game_error_not_in_library_title), menu.panel!!.content.title)
        }
    @Test
    fun backWhilePlayWithIsReadKeepsTheListFromPoppingUp() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            actions.readGate = CompletableDeferred()
            menu.down()
            menu.onAction(Action.Enter) // Play with, still reading
            menu.onAction(Action.Back)
            assertNull(menu.panel)
            actions.readGate!!.complete(Unit)
            assertNull("the list does not appear after B", menu.panel)
        }

    @Test
    fun backWhileTheMenuIsReadGivesUpOnItAndNothingReachesTheStrip() =
        runMenu {
            val actions = FakeGameActions().apply { readGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.openMenu(game)
            assertTrue("a move while it opens is held back from the strip", menu.onAction(Action.Move(Direction.Down)))
            assertTrue("B is taken", menu.onAction(Action.Back))
            actions.readGate!!.complete(Unit)
            assertNull("the menu does not appear after B", menu.panel)
            assertFalse("afterwards the strip has the keys again", menu.onAction(Action.Back))
        }

    @Test
    fun aLaunchResultLeavesAMenuOpenedMeanwhileAlone() =
        runMenu {
            val other = GameRef("8", "EarthBound")
            val actions = FakeGameActions().apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.play(game)
            menu.openMenu(other)
            actions.launchGate!!.complete(Unit)
            assertEquals("a start does not close the other game's menu", other, menu.panel!!.game)
            menu.close()
            actions.launchGate = CompletableDeferred()
            actions.outcome = { LaunchOutcome.FileMissing(7, "x.sfc", FileProblem.Gone) }
            menu.play(game)
            menu.openMenu(other)
            val otherMenu = menu.panel
            actions.launchGate!!.complete(Unit)
            assertEquals("a failure is shown over it", UiText.Res(R.string.game_error_file_gone_title), menu.panel!!.content.title)
            assertEquals(game, menu.panel!!.game)
            menu.onAction(Action.Back)
            assertTrue("B returns to the other game's menu", menu.panel === otherMenu)
        }

    @Test
    fun aFailureAfterTheMenuWasClosedIsShown() =
        runMenu {
            val actions = FakeGameActions(outcome = { LaunchOutcome.FileMissing(7, "x.sfc", FileProblem.Gone) }).apply { launchGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.onAction(Action.Enter) // Play
            menu.onAction(Action.Back)
            actions.launchGate!!.complete(Unit)
            assertEquals(UiText.Res(R.string.game_error_file_gone_title), menu.panel!!.content.title)
        }

    @Test
    fun theFirstLaunchAsksAndAKeepsTheRecommendedOneForTheSystem() =
        runMenu {
            var chosen = false
            val offers = listOf(
                EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation"),
                EmulatorOffer("retroarch_mednafen_psx", "RetroArch (Beetle PSX)", "com.retroarch.aarch64"),
            )
            val actions = FakeGameActions(outcome = { key -> if (chosen) FakeGameActions.started(key) else LaunchOutcome.EmulatorNotChosen(7, "psx", "PlayStation", offers, "duckstation") })
            var listingReads = 0
            actions.listing = { listingReads++; sampleListing() }
            val menu = controller(actions)
            menu.play(game)
            assertEquals(UiText.Res(R.string.game_choose_emulator_title, listOf("PlayStation")), menu.panel!!.content.title)
            assertEquals("the question needs no second detection", 0, listingReads)
            assertEquals("duckstation", menu.focusedKey())
            chosen = true
            menu.onAction(Action.Enter)
            assertEquals("kept for the system, not only this game", listOf("7" to "duckstation"), actions.systemChoices)
            assertEquals(emptyList<Any>(), actions.choices)
            assertEquals("picked, then played", listOf("7", "7"), actions.launched)
            assertNull("started: the panel closes", menu.panel)
        }

    @Test
    fun backingOutOfTheFirstLaunchQuestionStoresNothingAndStartsNothing() =
        runMenu {
            val offers = listOf(EmulatorOffer("duckstation", "DuckStation", "com.github.stenzek.duckstation"))
            val actions = FakeGameActions(outcome = { LaunchOutcome.EmulatorNotChosen(7, "psx", "PlayStation", offers, "duckstation") })
            val menu = controller(actions)
            menu.play(game)
            assertEquals(UiText.Res(R.string.game_choose_emulator_title, listOf("PlayStation")), menu.panel!!.content.title)
            menu.onAction(Action.Back)
            assertNull(menu.panel)
            assertEquals(emptyList<Any>(), actions.systemChoices)
            assertEquals(emptyList<Any>(), actions.choices)
            assertEquals("only the one launch that asked", listOf("7"), actions.launched)
        }

    @Test
    fun onlyLegalRiskEmulatorsInstalledLetThePersonPickOne() =
        runMenu {
            val risky = listOf(EmulatorOffer("risky", "Risky Emu", "org.example.risky", isLegalRisk = true))
            val actions = FakeGameActions(outcome = { LaunchOutcome.EmulatorNotChosen(7, "switch", "Switch", risky, null) })
            val menu = controller(actions)
            menu.play(game)
            assertEquals(UiText.Res(R.string.game_choose_emulator_title, listOf("Switch")), menu.panel!!.content.title)
            assertEquals("risky", menu.focusedKey())
            menu.onAction(Action.Enter)
            assertEquals(listOf("7" to "risky"), actions.systemChoices)
            assertEquals("picked, then played", listOf("7", "7"), actions.launched)
        }

    @Test
    fun rescanWhileAScanRunsSaysSo() =
        runMenu {
            val actions = FakeGameActions(outcome = { LaunchOutcome.FileMissing(7, "x.sfc", FileProblem.FolderUnreachable) }).apply { scanBusy = true }
            val menu = controller(actions)
            menu.play(game)
            menu.onAction(Action.Enter)
            assertEquals(0, actions.rescans)
            assertEquals(UiText.Res(R.string.game_scan_busy_title), menu.panel!!.content.title)
            menu.onAction(Action.Back)
            assertEquals("Back returns to the message", UiText.Res(R.string.game_error_folder_title), menu.panel!!.content.title)
        }

    @Test
    fun aMoveWhilePlayWithIsReadDoesNotLoseIt() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            actions.readGate = CompletableDeferred()
            menu.down()
            menu.onAction(Action.Enter)
            menu.down()
            actions.readGate!!.complete(Unit)
            assertTrue("Play with opens", menu.panel!!.screen is PanelScreen.PlayWith)
        }

    @Test
    fun aMoveWhileFavoriteIsWrittenStillClosesTheMenu() =
        runMenu {
            val actions = FakeGameActions().apply { writeGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down(2)
            menu.onAction(Action.Enter)
            menu.down()
            actions.writeGate!!.complete(Unit)
            assertEquals(listOf("7" to true), actions.favorites)
            assertNull("the menu closes as after any favorite", menu.panel)
        }

    @Test
    fun aMoveWhileTheChoiceIsSavedStillPlays() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down()
            menu.onAction(Action.Enter)
            actions.writeGate = CompletableDeferred()
            menu.down(2)
            menu.onAction(Action.Enter)
            menu.down()
            actions.writeGate!!.complete(Unit)
            assertEquals(listOf("7" to Beetle.id), actions.choices)
            assertEquals("saved, then played", listOf("7"), actions.launched)
        }

    @Test
    fun theMenusFavoriteIsWrittenEvenWhenThePersonLeavesTheMenuFirst() =
        runMenu {
            val actions = FakeGameActions().apply { writeGate = CompletableDeferred() }
            val menu = controller(actions)
            val heard = mutableListOf<String>()
            menu.favoriteListener =
                object : FavoriteListener {
                    override fun requested(game: GameRef, isFavorite: Boolean) {
                        heard += "requested $isFavorite"
                    }

                    override fun failed(game: GameRef, isFavorite: Boolean) {
                        heard += "failed $isFavorite"
                    }

                    override fun hidden(game: GameRef) {
                        heard += "hidden"
                    }
                }
            menu.openMenu(game)
            menu.down(2)
            menu.onAction(Action.Enter)
            // B before the slow write ran: the list already shows the star, so the write must land.
            menu.onAction(Action.Back)
            assertNull(menu.panel)
            actions.writeGate!!.complete(Unit)
            assertEquals(listOf("7" to true), actions.favorites)
            assertEquals(listOf("requested true"), heard)
        }

    @Test
    fun startOrATapOutsideBeforeTheWriteStillWritesIt() =
        runMenu {
            val actions = FakeGameActions().apply { writeGate = CompletableDeferred() }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down(2)
            menu.onAction(Action.Enter)
            menu.onAction(Action.Menu)
            actions.writeGate!!.complete(Unit)
            assertEquals(listOf("7" to true), actions.favorites)
        }

    @Test
    fun aMenuFavoriteThatFailsAfterTheMenuClosedStillSaysSo() =
        runMenu {
            val actions = FakeGameActions().apply {
                writeGate = CompletableDeferred()
                writeError = IOException("disk I/O error")
            }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.down(2)
            menu.onAction(Action.Enter)
            menu.onAction(Action.Back)
            assertNull(menu.panel)
            actions.writeGate!!.complete(Unit)
            assertTrue("the save message shows over the list", menu.panel?.screen is PanelScreen.Message)
        }

    @Test
    fun hidingAGameTellsTheListener() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            val hidden = mutableListOf<String>()
            menu.favoriteListener =
                object : FavoriteListener {
                    override fun requested(game: GameRef, isFavorite: Boolean) = Unit

                    override fun failed(game: GameRef, isFavorite: Boolean) = Unit

                    override fun hidden(game: GameRef) {
                        hidden += game.key
                    }
                }
            menu.openMenu(game)
            menu.down(3)
            menu.onAction(Action.Enter)
            assertEquals(listOf("7"), actions.hidden)
            assertEquals(listOf("7"), hidden)
        }
}
