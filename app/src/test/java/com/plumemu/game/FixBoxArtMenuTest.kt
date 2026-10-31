package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import com.plumemu.keyboard.KeyboardChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fix box art… in the game menu, on a fake library: when it is listed, the field and
 * its keyboard, Search, picks and No box art with the gamepad and with touch, every outcome as a
 * plain line, and the work stopped by Back or by a newer search.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FixBoxArtMenuTest {
    private val game = GameRef("7", "Zork Quest 2")
    private val logs = mutableListOf<String>()

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) { logs += it }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun offered(key: String) = sampleFacts(title = "Game $key", canFixBoxArt = true)

    private fun GameMenuController.chooseRow(key: String) {
        val index = panel!!.content.focusableRows.indexOfFirst { it.key == key }
        assertTrue("$key is in the panel", index >= 0)
        onAction(Action.TapItem(Column.Details, index))
    }

    private fun GameMenuController.boxArt(): FixBoxArtState = (panel!!.screen as PanelScreen.FixBoxArt).typing

    private suspend fun TestScope.opened(
        actions: FakeGameActions = FakeGameActions(facts = ::offered),
        keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
    ): GameMenuController =
        controller(actions).also {
            it.keyboardKind = keyboard
            it.openMenu(game)
            it.chooseRow(GameMenuEntryId.FixBoxArt.name)
        }

    /** Down to Search and A (the gamepad's way). */
    private fun GameMenuController.search() {
        onAction(Action.Move(Direction.Down))
        onAction(Action.Enter)
    }

    @Test
    fun fixBoxArtIsListedAfterNameOnlyWhenBoxArtCanBeAskedFor() {
        assertEquals(listOf("Play", "PlayWith", "Favorite", "Hide", "Name", "Info"), gameMenuEntries(sampleFacts()).map { it.key })
        assertEquals(listOf("Play", "PlayWith", "Favorite", "Hide", "Name", "FixBoxArt", "Info"), gameMenuEntries(sampleFacts(canFixBoxArt = true)).map { it.key })
        assertEquals(UiText.Res(R.string.game_menu_fix_box_art), gameMenuEntries(sampleFacts(canFixBoxArt = true))[5].label)
    }

    @Test
    fun openingShowsTheTitleWithTheKeyboardDownAndAAsksForIt() =
        runMenu {
            val actions = FakeGameActions(facts = ::offered)
            val menu = opened(actions, KeyboardChoice.Android)
            val state = menu.boxArt()
            assertEquals("Zork Quest 2", state.query)
            assertEquals("PlayStation", state.systemName)
            assertTrue(state.inField)
            assertFalse("opening never opens the keyboard", state.keyboardUp)
            assertEquals(0, state.keyboard.asks)
            assertEquals(FixBoxArtPhase.Idle, state.phase)
            assertEquals(UiText.Res(R.string.game_fix_box_art_idle), fixBoxArtLine(state))
            assertTrue("nothing is asked before Search", actions.boxArtSearches.isEmpty())

            menu.onAction(Action.Enter)
            assertTrue("A on the field opens the keyboard", state.keyboardUp)
            assertTrue(actions.boxArtSearches.isEmpty())
            menu.onAction(Action.Move(Direction.Down))
            assertFalse("Down leaves the field and puts it away", state.keyboardUp)
            assertEquals(FixBoxArtState.SEARCH_ROW, state.cursor)
            menu.onAction(Action.Move(Direction.Up))
            assertTrue(state.inField)
            assertFalse("back on the field, the keyboard stays down", state.keyboardUp)
            menu.onAction(Action.Enter)
            assertEquals("A brings it back (an ask again)", 2, state.keyboard.asks)
            menu.close()
        }

    @Test
    fun searchListsTheGamesThenNoBoxArtLastAndTheirThumbnails() =
        runMenu {
            val thumb = File("thumb.img")
            val actions = FakeGameActions(facts = ::offered).apply { boxArtThumbnailOf = { thumb } }
            val menu = opened(actions)
            val state = menu.boxArt()
            menu.search()
            assertEquals(listOf("Zork Quest 2"), actions.boxArtSearches)
            assertEquals(FixBoxArtPhase.Results, state.phase)
            assertNull(fixBoxArtLine(state))
            assertEquals(
                listOf(BOX_ART_SEARCH_KEY, "box_art_hit_9001", "box_art_hit_9002", "box_art_hit_9003", BOX_ART_NONE_KEY),
                fixBoxArtRows(state).map { it.key },
            )
            val first = fixBoxArtRows(state)[1]
            assertEquals(UiText.Plain("Zork Quest"), first.label)
            assertEquals(UiText.Res(R.string.game_fix_name_release, listOf("1991", "Galfen Soft")), first.note)
            assertEquals(UiText.Plain("Super Nintendo"), first.value)
            assertEquals(UiText.Plain("1993"), fixBoxArtRows(state)[2].note)
            assertEquals(UiText.Res(R.string.game_fix_box_art_untitled), fixBoxArtRows(state)[3].label)
            assertEquals("a game without a box front asks no thumbnail", listOf("9001", "9002"), actions.thumbnailsAsked)
            assertEquals(thumb, state.thumbnailOf("9001"))
            assertNull(state.thumbnailOf("9003"))
            assertEquals("the cursor stays on Search: a second A searches again, never picks", FixBoxArtState.SEARCH_ROW, state.cursor)
            menu.close()
        }

    @Test
    fun theKeyboardsSearchKeySearchesOnceAndPutsItAway() =
        runMenu {
            val actions = FakeGameActions(facts = ::offered)
            val menu = opened(actions)
            val state = menu.boxArt()
            menu.onAction(Action.Enter)
            state.onQueryChange("zork quest")
            state.onKeyboardAction()?.let(menu::onAction)
            assertEquals(listOf("zork quest"), actions.boxArtSearches)
            assertFalse(state.keyboardUp)
            assertEquals(FixBoxArtState.SEARCH_ROW, state.cursor)
            menu.close()
        }

    @Test
    fun aBlankQueryAsksNothingAndGoesBackToTheField() =
        runMenu {
            val actions = FakeGameActions(facts = ::offered)
            val menu = opened(actions)
            val state = menu.boxArt()
            state.onQueryChange("   ")
            menu.search()
            assertTrue(actions.boxArtSearches.isEmpty())
            assertTrue(state.inField)
            assertFalse(state.keyboardUp)
            menu.close()
        }

    @Test
    fun aSecondSearchCancelsTheFirstAndItsAnswerIsDropped() =
        runMenu {
            val firstGate = CompletableDeferred<Unit>()
            var firstCancelled = false
            val actions =
                FakeGameActions(facts = ::offered).apply {
                    boxArtSearch = { query ->
                        if (query == "first") {
                            try {
                                firstGate.await()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                firstCancelled = true
                                throw e
                            }
                            BoxArtSearchOutcome.Found(sampleHits.take(1))
                        } else {
                            BoxArtSearchOutcome.NothingFound
                        }
                    }
                }
            val menu = opened(actions)
            val state = menu.boxArt()
            state.onQueryChange("first")
            menu.search()
            assertEquals(FixBoxArtPhase.Searching("first"), state.phase)
            assertEquals(UiText.Res(R.string.game_fix_box_art_searching), fixBoxArtLine(state))
            state.onQueryChange("second")
            menu.onAction(Action.Enter)
            assertTrue("the first search was cancelled", firstCancelled)
            firstGate.complete(Unit)
            assertEquals(FixBoxArtPhase.NothingFound("second"), state.phase)
            assertEquals(UiText.Res(R.string.game_fix_box_art_nothing_found, listOf("second", "PlayStation")), fixBoxArtLine(state))
            assertEquals(emptyList<BoxArtHit>(), state.hits)
            menu.close()
        }

    @Test
    fun backCancelsTheSearchAndTheThumbnailsAndReturnsToTheMenu() =
        runMenu {
            var cancelled = 0
            val actions =
                FakeGameActions(facts = { key -> offered(key) }).apply {
                    boxArtSearch = { BoxArtSearchOutcome.Found(sampleHits.take(2) + sampleHits.take(2).map { it.copy(id = it.id + "b") }) }
                    boxArtThumbnailOf = {
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled++
                        }
                    }
                }
            val menu = opened(actions)
            menu.search()
            assertEquals("at most two thumbnails at once", 2, actions.mostThumbnailsInFlight)
            assertEquals(2, actions.thumbnailsAsked.size)
            menu.onAction(Action.Back)
            assertEquals("both in flight were cancelled, the waiting ones never start", 2, cancelled)
            assertEquals(2, actions.thumbnailsAsked.size)
            val back = menu.panel!!
            assertTrue(back.screen is PanelScreen.Menu)
            assertEquals("the focus is on Fix box art…", GameMenuEntryId.FixBoxArt.name, back.content.focusableRows[back.focus].key)

            // A search still waiting is cancelled the same way.
            var searchCancelled = false
            actions.boxArtSearch = {
                try {
                    awaitCancellation()
                } finally {
                    searchCancelled = true
                }
            }
            menu.chooseRow(GameMenuEntryId.FixBoxArt.name)
            menu.search()
            menu.onAction(Action.Menu)
            assertTrue(searchCancelled)
            assertNull(menu.panel)
        }

    @Test
    fun aPickIsStoredAndTheConfirmationReturnsToTheMenu() =
        runMenu {
            val actions = FakeGameActions(facts = ::offered)
            val menu = opened(actions)
            menu.search()
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            assertEquals(listOf("7" to "9001"), actions.boxArtPicks)
            val message = menu.panel!!
            assertEquals(boxArtStoredContent(sampleHits[0]), (message.screen as PanelScreen.Message).content)
            assertEquals(UiText.Res(R.string.game_fix_box_art_saved_title), message.content.title)
            menu.onAction(Action.Enter)
            val back = menu.panel!!
            assertTrue("OK returns to the menu", back.screen is PanelScreen.Menu)
            assertEquals(GameMenuEntryId.FixBoxArt.name, back.content.focusableRows[back.focus].key)
        }

    @Test
    fun aTapOnNoBoxArtStoresItAndATapOnAGamePicksIt() =
        runMenu {
            val actions = FakeGameActions(facts = ::offered)
            val menu = opened(actions)
            val state = menu.boxArt()
            menu.onAction(Action.TapItem(Column.Details, FixBoxArtState.SEARCH_ROW))
            assertEquals("a tap on Search searches", 1, actions.boxArtSearches.size)
            menu.onAction(Action.TapItem(Column.Details, state.noBoxArtRow))
            assertEquals(listOf("7" to "-"), actions.boxArtPicks)
            assertEquals(boxArtStoredContent(null), (menu.panel!!.screen as PanelScreen.Message).content)
            assertEquals(UiText.Res(R.string.game_fix_box_art_none_title), menu.panel!!.content.title)

            menu.onAction(Action.Back)
            menu.chooseRow(GameMenuEntryId.FixBoxArt.name)
            menu.onAction(Action.TapItem(Column.Details, FixBoxArtState.SEARCH_ROW))
            menu.onAction(Action.TapItem(Column.Details, 2))
            assertEquals("the second game", "7" to "9002", actions.boxArtPicks.last())
        }

    @Test
    fun noBoxArtBeforeAnySearchIsTheRowAfterSearch() =
        runMenu {
            val actions = FakeGameActions(facts = ::offered)
            val menu = opened(actions)
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Move(Direction.Down))
            assertEquals(1, menu.boxArt().cursor)
            menu.onAction(Action.Move(Direction.Down))
            assertEquals("No box art is last: Down stops there", 1, menu.boxArt().cursor)
            menu.onAction(Action.Enter)
            assertEquals(listOf("7" to "-"), actions.boxArtPicks)
        }

    @Test
    fun theCursorOnNoBoxArtStaysThereWhenTheGamesArrive() =
        runMenu {
            val gate = CompletableDeferred<Unit>()
            val actions = FakeGameActions(facts = ::offered).apply { boxArtSearch = { gate.await(); BoxArtSearchOutcome.Found(sampleHits) } }
            val menu = opened(actions)
            val state = menu.boxArt()
            menu.search()
            menu.onAction(Action.Move(Direction.Down))
            assertEquals(1, state.cursor)
            gate.complete(Unit)
            assertEquals(state.noBoxArtRow, state.cursor)
            assertEquals(4, state.cursor)
            menu.close()
        }

    @Test
    fun whileAPickIsStoredNothingElseStartsAndASlowPictureSaveSaysItMayWait() =
        runTest {
            val gate = CompletableDeferred<BoxArtStoreOutcome>()
            val actions =
                FakeGameActions(facts = ::offered).apply {
                    boxArtPick = { _, onPictures ->
                        onPictures()
                        gate.await()
                    }
                }
            val menu = controller(actions)
            menu.openMenu(game)
            runCurrent()
            menu.chooseRow(GameMenuEntryId.FixBoxArt.name)
            val state = menu.boxArt()
            menu.search()
            runCurrent()
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            runCurrent()
            assertEquals(FixBoxArtPhase.Storing, state.phase)
            assertEquals(UiText.Res(R.string.game_fix_box_art_storing), fixBoxArtLine(state))
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            menu.onAction(Action.TapItem(Column.Details, FixBoxArtState.SEARCH_ROW))
            runCurrent()
            assertEquals("no second pick and no search while storing", 1, actions.boxArtPicks.size)
            assertEquals(1, actions.boxArtSearches.size)

            advanceTimeBy(FixBoxArtWork.WAIT_NOTICE_MILLIS + 1)
            assertEquals(FixBoxArtPhase.WaitingForPictures, state.phase)
            assertEquals(UiText.Res(R.string.game_fix_box_art_waiting), fixBoxArtLine(state))

            gate.complete(BoxArtStoreOutcome.Stored)
            runCurrent()
            assertTrue(menu.panel!!.screen is PanelScreen.Message)
        }

    @Test
    fun backWhileAPickIsFetchedCancelsItAndNothingIsShownLater() =
        runMenu {
            var cancelled = false
            val actions =
                FakeGameActions(facts = ::offered).apply {
                    boxArtPick = { _, _ ->
                        try {
                            awaitCancellation()
                        } finally {
                            cancelled = true
                        }
                    }
                }
            val menu = opened(actions)
            menu.search()
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            val fixPanel = menu.panel
            menu.onAction(Action.Back)
            assertTrue(cancelled)
            assertTrue(menu.panel!!.screen is PanelScreen.Menu)
            assertFalse(menu.panel === fixPanel)
        }

    @Test
    fun theThumbnailsAPickCancelledComeBackWhenItFails() =
        runMenu {
            val thumbs = CompletableDeferred<Unit>()
            val actions =
                FakeGameActions(facts = ::offered).apply {
                    boxArtThumbnailOf = { thumbs.await(); File("t-${it.id}") }
                    boxArtPick = { _, _ -> BoxArtStoreOutcome.StorageFailed }
                }
            val menu = opened(actions)
            val state = menu.boxArt()
            menu.search()
            assertEquals(listOf("9001", "9002"), actions.thumbnailsAsked)
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            assertEquals(FixBoxArtPhase.StoreFailed(BoxArtStoreOutcome.StorageFailed, sampleHits[0]), state.phase)
            assertEquals("asked again once the list is back", listOf("9001", "9002", "9001", "9002"), actions.thumbnailsAsked)
            thumbs.complete(Unit)
            assertEquals(File("t-9001"), state.thumbnailOf("9001"))
            assertEquals(File("t-9002"), state.thumbnailOf("9002"))
            menu.close()
        }

    @Test
    fun everySearchFailureHasItsPlainLine() =
        runMenu {
            val cases =
                listOf(
                    BoxArtSearchOutcome.Failed(BoxArtProblem.DailyLimit(5_000)) to UiText.Format(R.string.game_fix_box_art_daily_limit, listOf(UiText.ClockTime(5_000))),
                    BoxArtSearchOutcome.Failed(BoxArtProblem.ServerClosed(6_000)) to UiText.Format(R.string.game_fix_box_art_server_closed, listOf(UiText.ClockTime(6_000))),
                    BoxArtSearchOutcome.Failed(BoxArtProblem.SignInAgain) to UiText.Res(R.string.game_fix_box_art_sign_in_again),
                    BoxArtSearchOutcome.Failed(BoxArtProblem.Offline) to UiText.Res(R.string.game_fix_box_art_offline),
                    BoxArtSearchOutcome.Failed(BoxArtProblem.ServerError(503)) to UiText.Res(R.string.game_fix_box_art_server_error, listOf("503")),
                    BoxArtSearchOutcome.Failed(BoxArtProblem.NotAvailable) to UiText.Res(R.string.game_fix_box_art_not_available),
                    BoxArtSearchOutcome.Failed(BoxArtProblem.NotRead) to UiText.Res(R.string.game_fix_box_art_not_read),
                    BoxArtSearchOutcome.GameNotReady to UiText.Res(R.string.game_fix_box_art_game_not_ready),
                    BoxArtSearchOutcome.Found(emptyList()) to UiText.Res(R.string.game_fix_box_art_nothing_found, listOf("Zork Quest 2", "PlayStation")),
                )
            for ((outcome, line) in cases) {
                val actions = FakeGameActions(facts = ::offered).apply { boxArtSearch = { outcome } }
                val menu = opened(actions)
                menu.search()
                assertEquals("$outcome", line, fixBoxArtLine(menu.boxArt()))
                menu.close()
            }
        }

    @Test
    fun aSearchThatThrowsIsLoggedAndSaysSo() =
        runMenu {
            val actions = FakeGameActions(facts = ::offered).apply { boxArtSearch = { throw IllegalStateException("db closed") } }
            val menu = opened(actions)
            menu.search()
            assertEquals(UiText.Res(R.string.game_fix_box_art_not_read), fixBoxArtLine(menu.boxArt()))
            assertTrue(logs.any { "IllegalStateException" in it })
            menu.close()
        }

    @Test
    fun aFailedStoreKeepsTheGamesSoAnotherPickCanBeTried() =
        runMenu {
            val cases =
                listOf(
                    BoxArtStoreOutcome.GameGone to UiText.Res(R.string.game_fix_box_art_game_gone),
                    BoxArtStoreOutcome.NotNamedYet to UiText.Res(R.string.game_fix_box_art_not_named_yet),
                    BoxArtStoreOutcome.StorageFailed to UiText.Res(R.string.game_fix_box_art_storage_failed),
                    BoxArtStoreOutcome.NotFound to UiText.Res(R.string.game_fix_box_art_not_found),
                    BoxArtStoreOutcome.NoBoxFront to UiText.Format(R.string.game_fix_box_art_no_box_front, listOf(UiText.Plain("Zork Quest"))),
                    BoxArtStoreOutcome.Failed(BoxArtProblem.Offline) to UiText.Res(R.string.game_fix_box_art_offline),
                )
            for ((outcome, line) in cases) {
                val actions = FakeGameActions(facts = ::offered).apply { boxArtPick = { _, _ -> outcome } }
                val menu = opened(actions)
                val panel = menu.panel
                val state = menu.boxArt()
                menu.search()
                menu.onAction(Action.Move(Direction.Down))
                menu.onAction(Action.Enter)
                assertSame("the panel stays", panel, menu.panel)
                assertEquals(FixBoxArtPhase.StoreFailed(outcome, sampleHits[0]), state.phase)
                assertEquals("$outcome", line, fixBoxArtLine(state))
                assertEquals(sampleHits, state.hits)
                menu.onAction(Action.Enter)
                assertEquals("picked again", 2, actions.boxArtPicks.size)
                menu.close()
            }
        }
}
