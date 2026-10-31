package com.plumemu.game

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coredata.db.GameIdentificationRow
import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.graph.factsOf
import com.plumemu.home.panelCanChoose
import com.plumemu.keyboard.KeyboardChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The naming entries of the game menu: which are shown, Fix name…, Use my own name,
 * Not a game with its confirm step, Back to automatic, and Info's "Name from", with the gamepad
 * (moves, A, B) and with touch (taps on rows, which arrive as panel-column taps).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NameFixMenuTest {
    private val game = GameRef("7", "Zork Quest 2")
    private val logs = mutableListOf<String>()

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) { logs += it }

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.chooseRow(key: String) {
        val index = panel!!.content.focusableRows.indexOfFirst { it.key == key }
        assertTrue("$key is in the panel", index >= 0)
        onAction(Action.TapItem(Column.Details, index))
    }

    /** Name… from the menu, then [id] in the sub-panel (touch). */
    private fun GameMenuController.chooseEntry(id: NameEntryId) {
        if (panel?.screen !is PanelScreen.NameMenu) chooseRow(GameMenuEntryId.Name.name)
        chooseRow(id.name)
    }

    private fun GameMenuController.fixName(): FixNameState = (panel!!.screen as PanelScreen.FixName).typing

    private fun GameMenuController.ownName(): OwnNameState = (panel!!.screen as PanelScreen.OwnName).typing

    /** What `FollowTitleResults` does in the panel: the finder's answer for the current query. */
    private fun FixNameState.answer() = showResults(query, finder!!.find(query))

    private fun keys(facts: GameFacts) = nameMenuEntries(facts).map { it.key }

    @Test
    fun theMenuHasOneNameRowAndItsSubPanelOffersBackToAutomaticOnlyWithAFix() {
        assertEquals(listOf("Play", "PlayWith", "Favorite", "Hide", "Name", "Info"), gameMenuEntries(sampleFacts()).map { it.key })
        val plain = keys(sampleFacts(name = NameFacts(NameOrigin.NameMatch, NameCertainty.Likely, null, hasFix = false)))
        assertEquals(listOf("FixName", "OwnName", "NotAGame"), plain)
        val fixed = keys(sampleFacts(name = NameFacts(NameOrigin.OwnName, null, null, hasFix = true)))
        assertEquals(listOf("FixName", "OwnName", "NotAGame", "BackToAutomatic"), fixed)
        assertFalse("unknown naming facts offer no Back to automatic", "BackToAutomatic" in keys(sampleFacts(name = null)))
    }

    @Test
    fun nameOpensTheSubPanelByGamepadAndBackReturnsToNameInTheMenu() =
        runMenu {
            val menu = controller(FakeGameActions())
            menu.openMenu(game)
            repeat(4) { menu.onAction(Action.Move(Direction.Down)) }
            menu.onAction(Action.Enter)
            assertTrue(menu.panel!!.screen is PanelScreen.NameMenu)
            assertEquals("Fix name… has the focus", "FixName", menu.panel!!.content.focusableRows[menu.panel!!.focus].key)
            menu.onAction(Action.Back)
            val back = menu.panel!!
            assertTrue(back.screen is PanelScreen.Menu)
            assertEquals("the focus is on Name…", "Name", back.content.focusableRows[back.focus].key)
        }

    @Test
    fun fixNameOpensAtOnceAndSaysItIsReadingUntilTheListArrives() =
        runMenu {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val actions = FakeGameActions().apply { readGate = gate }
            val menu = controller(actions)
            actions.readGate = null
            menu.openMenu(game)
            actions.readGate = gate
            menu.chooseEntry(NameEntryId.FixName)
            val state = menu.fixName()
            assertTrue("open before the list is read", state.isLoading)
            assertFalse(panelCanChoose(menu.panel!!))
            gate.complete(Unit)
            assertFalse(state.isLoading)
            state.answer()
            assertEquals("Zork Quest II", state.results.first().name)
        }

    @Test
    fun fixNameOpensWithTheShownNameAndAPickWithTheGamepadStoresIt() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.FixName)
            val state = menu.fixName()
            assertEquals("Zork Quest 2", state.query)
            assertTrue(state.inField)
            state.answer()
            assertEquals("Zork Quest II", state.results.first().name)
            assertTrue(panelCanChoose(menu.panel!!))
            menu.onAction(Action.Move(Direction.Down))
            assertFalse("Down leaves the field for the results", state.inField)
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Move(Direction.Up))
            menu.onAction(Action.Enter)
            assertEquals(listOf("7" to sampleTitles[1]), actions.chosenTitles)
            assertNull("the pick closes the menu; the list shows the new name", menu.panel)
        }

    @Test
    fun fixNameTapPicksAResultAndUpFromTheFirstGoesBackToTheField() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions)
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.FixName)
            val state = menu.fixName()
            state.onQueryChange("galfen")
            state.answer()
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Move(Direction.Up))
            assertTrue(state.inField)
            menu.onAction(Action.Move(Direction.Left))
            assertTrue("Left while typing does not leave the panel", menu.panel?.screen is PanelScreen.FixName)
            menu.onAction(Action.TapItem(Column.Details, 0))
            assertEquals(listOf("7" to sampleTitles[3]), actions.chosenTitles)
        }

    @Test
    fun fixNameWithNoMatchHasNothingToChooseAndBackReturnsToTheMenu() =
        runMenu {
            val menu = controller(FakeGameActions()).also { it.keyboardKind = KeyboardChoice.Android }
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.FixName)
            val state = menu.fixName()
            state.onQueryChange("qwxv plomb")
            state.answer()
            assertTrue(state.isAnswered)
            assertEquals(emptyList<TitleChoice>(), state.results)
            assertFalse("no hint for an A that does nothing", panelCanChoose(menu.panel!!))
            menu.onAction(Action.Enter)
            assertTrue(state.inField)
            menu.onAction(Action.Back)
            assertTrue("B returns to Name…", menu.panel?.screen is PanelScreen.NameMenu)
        }

    @Test
    fun aSystemWithoutAListSaysSoAndOffersOwnName() =
        runMenu {
            val actions = FakeGameActions(titles = { TitleList.NoList("Test System") })
            val menu = controller(actions)
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.FixName)
            val content = menu.panel!!.content
            assertEquals(listOf(UiText.Res(R.string.game_fix_name_no_list, listOf("Test System"))), content.body)
            assertEquals(listOf(NameEntryId.OwnName.name, INFO_BACK_KEY), content.focusableRows.map { it.key })
            menu.onAction(Action.Enter)
            assertEquals("Zork Quest 2", menu.ownName().text)

            val broken = controller(FakeGameActions(titles = { TitleList.Unavailable }))
            broken.openMenu(game)
            broken.chooseEntry(NameEntryId.FixName)
            assertEquals(listOf(UiText.Res(R.string.game_fix_name_list_unavailable)), broken.panel!!.content.body)
        }

    @Test
    fun ownNameRefusesBlankAndSavesTheTrimmedName() =
        runMenu {
            val actions = FakeGameActions()
            val menu = controller(actions).also { it.keyboardKind = KeyboardChoice.Android }
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.OwnName)
            val state = menu.ownName()
            state.onTextChange("   ")
            menu.onAction(Action.Enter)
            assertFalse("A on the field opens the keyboard; it does not save", state.isRefused)
            menu.onAction(Action.Move(Direction.Down))
            menu.onAction(Action.Enter)
            assertTrue(state.isRefused)
            assertEquals(emptyList<Pair<String, String>>(), actions.ownNames)
            state.onTextChange("  My Zork  ")
            assertFalse("typing clears the refusal", state.isRefused)
            menu.onAction(Action.Move(Direction.Down))
            assertFalse(state.inField)
            // Touch: a tap on Save (row 0 of the panel column).
            menu.onAction(Action.TapItem(Column.Details, OwnNameState.SAVE_ROW))
            assertEquals(listOf("7" to "My Zork"), actions.ownNames)
            assertNull(menu.panel)
        }

    @Test
    fun notAGameAsksFirstWithCancelFocused() =
        runMenu {
            val actions = FakeGameActions()
            val hidden = mutableListOf<String>()
            val menu = controller(actions)
            menu.favoriteListener = object : FavoriteListener {
                override fun requested(game: GameRef, isFavorite: Boolean) = Unit

                override fun failed(game: GameRef, isFavorite: Boolean) = Unit

                override fun hidden(game: GameRef) {
                    hidden += game.key
                }
            }
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.NotAGame)
            val confirm = menu.panel!!
            assertEquals(CANCEL_KEY, confirm.content.focusableRows[confirm.focus].key)
            menu.onAction(Action.Enter)
            assertTrue("Cancel goes back to Name…, nothing stored", menu.panel?.screen is PanelScreen.NameMenu)
            assertEquals(emptyList<String>(), actions.notGames)
            menu.chooseEntry(NameEntryId.NotAGame)
            menu.onAction(Action.TapItem(Column.Details, 1))
            assertEquals(listOf("7"), actions.notGames)
            assertEquals("the lists drop it like a hidden game", listOf("7"), hidden)
            assertNull(menu.panel)
        }

    @Test
    fun backToAutomaticAsksTheLibraryAndCloses() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(name = NameFacts(NameOrigin.ChosenTitle, null, "Test DAT", hasFix = true)) })
            val menu = controller(actions)
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.BackToAutomatic)
            assertEquals(listOf("7"), actions.backToAutomatic)
            assertNull(menu.panel)
        }

    @Test
    fun backToAutomaticFinishesWhenBIsPressedRightAfterA() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(name = NameFacts(NameOrigin.OwnName, null, null, hasFix = true)) })
            val menu = controller(actions)
            menu.openMenu(game)
            actions.writeGate = kotlinx.coroutines.CompletableDeferred()
            menu.chooseEntry(NameEntryId.BackToAutomatic)
            menu.onAction(Action.Back)
            menu.onAction(Action.Back)
            assertNull("the person has left the menu", menu.panel)
            actions.writeGate!!.complete(Unit)
            assertEquals("the write (and the naming it starts) still happened", listOf("7"), actions.backToAutomatic)
        }

    @Test
    fun theMenuDoesNotReadTheNamePackInfoDoes() =
        runMenu {
            val name = NameFacts(NameOrigin.GameCode, NameCertainty.Sure, null, hasFix = false, sourceInPack = true)
            val actions = FakeGameActions(facts = { sampleFacts(name = name) })
            val menu = controller(actions)
            menu.openMenu(game)
            assertEquals(0, actions.sourceReads)
            menu.chooseRow(GameMenuEntryId.Info.name)
            assertEquals(1, actions.sourceReads)
            assertEquals("Test DAT", (menu.panel!!.screen as PanelScreen.Info).facts.name?.source)
        }

    @Test
    fun infoOfAnArcadeGameShowsItsSetReadOnlyWhenInfoOpens() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(isArcade = true) })
            val menu = controller(actions)
            menu.openMenu(game)
            assertEquals("the menu opens no pack", 0, actions.arcadeSetReads)
            menu.chooseRow(GameMenuEntryId.Info.name)
            assertEquals(1, actions.arcadeSetReads)
            assertEquals(ArcadeSetFacts("dinou", listOf("USA 930201")), (menu.panel!!.screen as PanelScreen.Info).facts.arcadeSet)
        }

    @Test
    fun infoOfASetThePackDoesNotListOrOfAnotherSystemShowsNoSet() =
        runMenu {
            val notListed = FakeGameActions(facts = { sampleFacts(isArcade = true) }).apply { arcadeSetAnswer = null }
            val menu = controller(notListed)
            menu.openMenu(game)
            menu.chooseRow(GameMenuEntryId.Info.name)
            assertEquals(1, notListed.arcadeSetReads)
            assertEquals(null, (menu.panel!!.screen as PanelScreen.Info).facts.arcadeSet)

            val console = FakeGameActions(facts = { sampleFacts() })
            val other = controller(console)
            other.openMenu(game)
            other.chooseRow(GameMenuEntryId.Info.name)
            assertEquals("not asked for a console game", 0, console.arcadeSetReads)
            assertEquals(null, (other.panel!!.screen as PanelScreen.Info).facts.arcadeSet)
        }

    @Test
    fun aFailedNamingWriteSaysSoAndLogsNoName() =
        runMenu {
            val actions = FakeGameActions().apply { writeError = IOException("disk full at /storage/Zork Quest 2.gba") }
            val menu = controller(actions)
            menu.openMenu(game)
            menu.chooseEntry(NameEntryId.FixName)
            val state = menu.fixName()
            state.answer()
            menu.onAction(Action.TapItem(Column.Details, 0))
            assertEquals(UiText.Res(R.string.game_error_save_title), menu.panel!!.content.title)
            assertTrue(logs.any { it.contains("fix name: game 7 could not be saved (IOException)") })
            assertTrue(logs.none { it.contains("Zork") || it.contains("storage") })
        }

    @Test
    fun infoSaysWhereTheNameCameFromForEachWay() {
        fun row(method: String?, confidence: String?, trust: String = "IDENTIFIER_EXACT") =
            GameIdentificationRow(7, "snes", "Zork Quest", "zq", trust, method, confidence, null, "crc:0000abcd:1024", false)
        assertEquals(NameFacts(NameOrigin.GameCode, NameCertainty.Sure, null, false, sourceInPack = true), factsOf(row("PRODUCT_CODE", "EXACT"), null))
        assertEquals(NameFacts(NameOrigin.FileContents, NameCertainty.Likely, null, false, sourceInPack = true), factsOf(row("CRC", "LIKELY"), null))
        assertEquals(NameFacts(NameOrigin.ArcadeName, NameCertainty.Sure, null, false, sourceInPack = true), factsOf(row("ARCADE_NAME", "EXACT"), null))
        assertEquals(NameFacts(NameOrigin.TitleInFile, NameCertainty.Sure, null, false), factsOf(row("HEADER_TITLE", "EXACT"), null))
        assertEquals(NameFacts(NameOrigin.NameMatch, NameCertainty.Likely, null, false, sourceInPack = true), factsOf(row("NAME_MATCH", "LIKELY", "IDENTIFIER_LIKELY"), null))
        assertEquals(NameFacts(NameOrigin.FileName, NameCertainty.Unknown, null, false), factsOf(row("FILE_NAME", "UNKNOWN", "FILE_NAME"), null))
        assertEquals(NameFacts(NameOrigin.NotReadYet, NameCertainty.Unknown, null, false), factsOf(row(null, null, "FILE_NAME"), null))
        // After Back to automatic and before the pass: the file name, whatever the old method was.
        assertEquals(NameOrigin.FileName, factsOf(row("CRC", "EXACT", "FILE_NAME"), null).origin)
        val own = NameFixEntity("crc:0000abcd:1024", NameFixKind.OWN_NAME, "Mine", null, null, 1)
        assertEquals(NameFacts(NameOrigin.OwnName, null, null, true), factsOf(row("CRC", "EXACT", "PERSON_FIX"), own))
        val chosen = own.copy(kind = NameFixKind.CHOSEN_TITLE, titleSource = "Other DAT")
        assertEquals(NameFacts(NameOrigin.ChosenTitle, null, "Other DAT", true), factsOf(row("CRC", "EXACT", "PERSON_FIX"), chosen))

        assertEquals(
            listOf(
                UiText.Format(R.string.game_name_from_with_certainty, listOf(UiText.Res(R.string.game_name_from_code), UiText.Res(R.string.game_name_certainty_sure))),
                UiText.Res(R.string.game_name_source, listOf("Test DAT")),
            ),
            nameFromLines(NameFacts(NameOrigin.GameCode, NameCertainty.Sure, "Test DAT", false)),
        )
        assertEquals(listOf(UiText.Res(R.string.game_name_from_own_name)), nameFromLines(NameFacts(NameOrigin.OwnName, null, null, true)))
        assertEquals(listOf(UiText.Res(R.string.game_name_from_file_name)), nameFromLines(NameFacts(NameOrigin.FileName, NameCertainty.Unknown, null, false)))
    }

    @Test
    fun releaseNoteShowsWhatTheListHas() {
        assertEquals(UiText.Res(R.string.game_fix_name_release, listOf("USA, Europe", "1991")), releaseNote(sampleTitles[0]))
        assertEquals(UiText.Plain("Japan"), releaseNote(sampleTitles[2]))
        assertEquals(UiText.Plain("1995"), releaseNote(sampleTitles[3]))
        assertNull(releaseNote(sampleTitles[4]))
    }
}
