package com.plumemu.game

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.coreui.components.keyboard.ShiftMode
import com.plumemu.keyboard.KeyboardChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The game panel's fields with Plumemu's keyboard, through the controller as
 * the main screen hands it actions: Fix name…, Use my own name and Fix box art… each open with the
 * tray down; A opens it; every button while it is up (D-pad on the keys, A, B, held B, X, Y,
 * SELECT, START, L1 R1, L2 R2, Back); the action key acts exactly once and as the Android IME
 * action did; a hardware keyboard types with the tray up or down; taps on keys; what the router is
 * told. With the setting on Android the panels keep the old path. The button-to-action mapping
 * (and "Confirm with B") is core-input's, checked through real key events in
 * `home/PanelOwnKeyboardPathTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelOwnKeyboardTest {
    private val game = GameRef("7", "Zork Quest 2")

    private fun TestScope.controller(actions: FakeGameActions) = GameMenuController(actions, this) {}

    private fun runMenu(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    private fun GameMenuController.chooseRow(key: String) {
        val index = requireNotNull(panel).content.focusableRows.indexOfFirst { it.key == key }
        assertTrue("$key is in the panel", index >= 0)
        onAction(Action.TapItem(Column.Details, index))
    }

    private fun TestScope.fixName(
        actions: FakeGameActions = FakeGameActions(),
        kind: KeyboardChoice = KeyboardChoice.Plumemu,
    ): Pair<GameMenuController, FixNameState> {
        val menu = controller(actions).also { it.keyboardKind = kind }
        menu.openMenu(game)
        menu.chooseRow(GameMenuEntryId.Name.name)
        menu.chooseRow(NameEntryId.FixName.name)
        val state = (requireNotNull(menu.panel).screen as PanelScreen.FixName).typing
        // What `FollowTitleResults` does in the panel.
        state.showResults(state.query, requireNotNull(state.finder).find(state.query))
        return menu to state
    }

    private fun TestScope.ownName(
        actions: FakeGameActions = FakeGameActions(),
        kind: KeyboardChoice = KeyboardChoice.Plumemu,
    ): Pair<GameMenuController, OwnNameState> {
        val menu = controller(actions).also { it.keyboardKind = kind }
        menu.openMenu(game)
        menu.chooseRow(GameMenuEntryId.Name.name)
        menu.chooseRow(NameEntryId.OwnName.name)
        return menu to (requireNotNull(menu.panel).screen as PanelScreen.OwnName).typing
    }

    private fun TestScope.boxArt(actions: FakeGameActions): Pair<GameMenuController, FixBoxArtState> {
        val menu = controller(actions)
        menu.openMenu(game)
        menu.chooseRow(GameMenuEntryId.FixBoxArt.name)
        return menu to (requireNotNull(menu.panel).screen as PanelScreen.FixBoxArt).typing
    }

    private fun GameMenuController.press(
        action: Action,
        times: Int = 1,
    ) = repeat(times) { onAction(action) }

    private fun clear(
        menu: GameMenuController,
        typing: TypingState,
    ) {
        menu.press(Action.JumpLetterNext)
        menu.press(Action.Erase())
        while (typing.fieldText.isNotEmpty()) menu.press(Action.Erase(isRepeat = true))
    }

    @Test
    fun eachFieldOpensWithTheTrayDownAndAOpensIt() =
        runMenu {
            val (fixMenu, fix) = fixName()
            val (ownMenu, own) = ownName()
            val (artMenu, art) = boxArt(FakeGameActions(facts = { sampleFacts(title = "Zork Quest 2", canFixBoxArt = true) }))
            for ((menu, typing) in listOf(fixMenu to fix, ownMenu to own, artMenu to art)) {
                assertEquals(KeyboardChoice.Plumemu, typing.keyboardKind)
                assertTrue("the cursor is on the field", typing.inField)
                assertFalse("opening a panel never brings the tray up", typing.isTrayUp)
                assertEquals("a hardware keyboard types into the field", TextEntry.Field, menu.textEntry)
                menu.press(Action.Enter)
                assertTrue("A opens the tray", typing.isTrayUp)
                assertEquals("B erases while the tray is up", TextEntry.Keyboard, menu.textEntry)
                assertEquals("the caret at the end of the pre-filled text", typing.fieldText.length, typing.fieldKeys.caret)
                assertEquals(KeyboardPage.Letters, typing.fieldKeys.keys.page)
                assertTrue("titles have capitals", typing.fieldKeys.keys.hasShift)
                menu.close()
                assertEquals("no panel, no typing keys", TextEntry.None, menu.textEntry)
            }
        }

    @Test
    fun theDpadMovesOnTheKeysAndNeverLeavesTheTray() =
        runMenu {
            val (menu, fix) = fixName()
            menu.press(Action.Enter)
            val start = fix.fieldKeys.keys.focus
            menu.press(Action.Move(Direction.Up))
            assertTrue("Up from the top row stays on the keys", fix.isTrayUp)
            assertEquals(start, fix.fieldKeys.keys.focus)
            menu.press(Action.Move(Direction.Right))
            assertEquals(KeyId(0, 1), fix.fieldKeys.keys.focus)
            menu.press(Action.Move(Direction.Down), times = 6)
            assertTrue("Down from the bottom row stays on the keys", fix.isTrayUp)
            assertTrue(fix.inField)
            assertEquals("no result picked by a move", -1, fix.cursor)
            menu.press(Action.Move(Direction.Left))
            assertTrue("Left is not the panel's Back while typing", menu.panel?.screen is PanelScreen.FixName)
        }

    @Test
    fun aTypesTheFocusedKeyAtTheCaretAndXTypesASpace() =
        runMenu {
            val (menu, own) = ownName()
            menu.press(Action.Enter)
            menu.press(Action.Search)
            menu.press(Action.Enter)
            assertEquals("Zork Quest 2 q", own.text)
            menu.press(Action.JumpLetterPrevious)
            assertEquals("L2: the caret to the start", 0, own.fieldKeys.caret)
            menu.press(Action.Enter)
            assertEquals("qZork Quest 2 q", own.text)
            menu.press(Action.NextSystem, times = 2)
            assertEquals("R1: one character right", 3, own.fieldKeys.caret)
            menu.press(Action.PreviousSystem)
            assertEquals("L1: one character left", 2, own.fieldKeys.caret)
            menu.press(Action.Erase())
            assertEquals("B deletes before the caret", "qork Quest 2 q", own.text)
            menu.press(Action.JumpLetterNext)
            assertEquals("R2: the caret to the end", own.text.length, own.fieldKeys.caret)
        }

    @Test
    fun ySwitchesThePageAndSelectShiftsOnceThenLocks() =
        runMenu {
            val (menu, own) = ownName()
            menu.press(Action.Enter)
            menu.press(Action.ToggleFavorite)
            assertEquals(KeyboardPage.Symbols, own.fieldKeys.keys.page)
            menu.press(Action.Enter)
            assertEquals("Zork Quest 21", own.text)
            menu.press(Action.ToggleFavorite)
            assertEquals(KeyboardPage.Letters, own.fieldKeys.keys.page)
            menu.press(Action.ToggleView)
            assertEquals(ShiftMode.Once, own.fieldKeys.keys.shift)
            menu.press(Action.Enter, times = 2)
            assertEquals("shift once: one capital", "Zork Quest 21Qq", own.text)
            menu.press(Action.ToggleView, times = 2)
            assertEquals(ShiftMode.Locked, own.fieldKeys.keys.shift)
            menu.press(Action.Enter, times = 2)
            assertEquals("locked: every letter", "Zork Quest 21QqQQ", own.text)
        }

    @Test
    fun anEmptyFieldStartsWithACapital() =
        runMenu {
            val (menu, fix) = fixName()
            menu.press(Action.Enter)
            clear(menu, fix)
            assertEquals(ShiftMode.Once, fix.fieldKeys.keys.shift)
            menu.press(Action.Enter, times = 2)
            assertEquals("Qq", fix.query)
        }

    @Test
    fun aHeldBErasesToEmptyAndStopsAFreshBOnEmptyHidesTheTrayOnly() =
        runMenu {
            val (menu, fix) = fixName()
            menu.press(Action.Enter)
            menu.press(Action.Erase())
            assertEquals("Zork Quest ", fix.query)
            menu.press(Action.Erase(isRepeat = true), times = 40)
            assertEquals("", fix.query)
            assertTrue("a held B never hides the tray", fix.isTrayUp)
            menu.press(Action.Erase(isRepeat = true), times = 5)
            assertTrue(fix.isTrayUp)
            menu.press(Action.Erase())
            assertFalse("a fresh B on an empty field hides the tray", fix.isTrayUp)
            assertTrue("the cursor stays on the field", fix.inField)
            assertTrue("the panel stays open", menu.panel?.screen is PanelScreen.FixName)
            assertEquals(TextEntry.Field, menu.textEntry)
            menu.press(Action.Erase())
            assertTrue("an erase that crossed the tray going down does nothing", menu.panel?.screen is PanelScreen.FixName)
        }

    @Test
    fun backHidesTheTrayThenStepsOutOfThePanel() =
        runMenu {
            val (menu, fix) = fixName()
            menu.press(Action.Enter)
            menu.press(Action.Back)
            assertFalse("Back (or Escape) hides the tray", fix.isTrayUp)
            assertTrue(fix.inField)
            assertTrue(menu.panel?.screen is PanelScreen.FixName)
            menu.press(Action.Enter)
            assertTrue("A brings it back", fix.isTrayUp)
            menu.onKeyboardEvent(KeyboardEvent.Hide)
            assertFalse("the hide key puts it away", fix.isTrayUp)
            menu.press(Action.Back)
            assertTrue("Back with the tray down leaves the panel", menu.panel?.screen is PanelScreen.NameMenu)
        }

    @Test
    fun fixNamesStartGoesToTheResultsAndPicksNothing() =
        runMenu {
            val actions = FakeGameActions()
            val (menu, fix) = fixName(actions)
            menu.press(Action.Enter)
            assertTrue(fix.canChoose)
            menu.press(Action.Menu)
            assertFalse("the tray goes down", fix.isTrayUp)
            assertFalse("the cursor is on the results", fix.inField)
            assertEquals(0, fix.cursor)
            assertTrue("the panel stays", menu.panel?.screen is PanelScreen.FixName)
            assertTrue("nothing picked", actions.chosenTitles.isEmpty())
            assertEquals(TextEntry.None, menu.textEntry)
        }

    @Test
    fun ownNamesStartSavesOnceAndABlankNameIsRefused() =
        runMenu {
            val actions = FakeGameActions()
            val (menu, own) = ownName(actions)
            menu.press(Action.Enter)
            menu.press(Action.Menu)
            assertEquals("saved exactly once", listOf("7" to "Zork Quest 2"), actions.ownNames)

            val (blankMenu, blank) = ownName(actions)
            blankMenu.press(Action.Enter)
            clear(blankMenu, blank)
            blankMenu.onKeyboardEvent(KeyboardEvent.Action)
            assertTrue(blank.isRefused)
            assertTrue("back on the field", blank.inField)
            assertFalse("with the tray down", blank.isTrayUp)
            assertEquals("nothing more saved", 1, actions.ownNames.size)
        }

    @Test
    fun fixBoxArtsStartSearchesOnce() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(title = "Zork Quest 2", canFixBoxArt = true) })
            val (menu, art) = boxArt(actions)
            menu.press(Action.Enter)
            menu.press(Action.Menu)
            assertEquals("one search", listOf("Zork Quest 2"), actions.boxArtSearches)
            assertFalse(art.isTrayUp)
            assertEquals(FixBoxArtState.SEARCH_ROW, art.cursor)
            menu.close()
        }

    @Test
    fun theTraysActionKeyAndAHardwareEnterDoWhatStartDoes() =
        runMenu {
            val actions = FakeGameActions(facts = { sampleFacts(title = "Zork Quest 2", canFixBoxArt = true) })
            val (menu, art) = boxArt(actions)
            menu.press(Action.Enter)
            menu.onKeyboardEvent(KeyboardEvent.Action)
            assertEquals(1, actions.boxArtSearches.size)
            menu.press(Action.Move(Direction.Up))
            assertTrue(art.inField)
            menu.press(Action.Typed(TypedKey.Enter))
            assertEquals("a hardware Enter on the field searches too, once", 2, actions.boxArtSearches.size)
            menu.close()
        }

    @Test
    fun aHardwareKeyboardTypesWithTheTrayDownAndUp() =
        runMenu {
            val (menu, fix) = fixName()
            assertFalse(fix.isTrayUp)
            menu.press(Action.Typed(TypedKey.Text(" X")))
            assertEquals("Zork Quest 2 X", fix.query)
            assertFalse("typing on a hardware keyboard does not bring the tray up", fix.isTrayUp)
            menu.press(Action.Typed(TypedKey.CaretLeft))
            menu.press(Action.Typed(TypedKey.Backspace))
            assertEquals("Zork Quest 2X", fix.query)
            menu.press(Action.Typed(TypedKey.ForwardDelete))
            assertEquals("Zork Quest 2", fix.query)
            menu.press(Action.Enter)
            menu.press(Action.Typed(TypedKey.Text("!")))
            assertEquals("Zork Quest 2!", fix.query)
        }

    @Test
    fun tapsOnKeysTypeAndATapOnTheLinePutsTheCaretThere() =
        runMenu {
            val (menu, own) = ownName()
            own.focusFieldAt(4)
            assertTrue("a tap on the field opens the tray", own.isTrayUp)
            assertEquals(4, own.fieldKeys.caret)
            menu.onKeyboardEvent(KeyboardEvent.Type('X'))
            menu.onKeyboardEvent(KeyboardEvent.Space)
            assertEquals("ZorkX  Quest 2", own.text)
            menu.onKeyboardEvent(KeyboardEvent.Delete)
            assertEquals("ZorkX Quest 2", own.text)
            own.focusFieldAt(99)
            assertEquals("a tap past the end puts the caret at the end", own.text.length, own.fieldKeys.caret)
        }

    @Test
    fun aKeyMoveIsHeardAndTypingIsNot() =
        runMenu {
            val (menu, own) = ownName()
            menu.press(Action.Enter)
            val heard = mutableListOf<com.plumemu.sound.Outcome>()
            menu.feel.sink = { heard += it }
            menu.press(Action.Move(Direction.Right))
            assertEquals(listOf(com.plumemu.sound.Outcome.Moved), heard)
            assertNotEquals(KeyId(0, 0), own.fieldKeys.keys.focus)
        }

    @Test
    fun withAndroidsKeyboardThePanelsKeepTheOldPath() =
        runMenu {
            val (menu, fix) = fixName(kind = KeyboardChoice.Android)
            assertEquals(KeyboardChoice.Android, fix.keyboardKind)
            menu.press(Action.Enter)
            assertTrue("A asks for Android's keyboard", fix.keyboardUp)
            assertFalse("no tray", fix.isTrayUp)
            assertEquals("the router sends nothing for typing", TextEntry.None, menu.textEntry)
            menu.press(Action.Move(Direction.Down))
            assertFalse("Down leaves the field", fix.inField)
            assertNull("the IME's search key picks nothing", fix.onKeyboardAction())

            val (ownMenu, own) = ownName(kind = KeyboardChoice.Android)
            ownMenu.press(Action.Enter)
            ownMenu.press(Action.Menu)
            assertNull("START closes the panel as before", ownMenu.panel)
            assertEquals("Zork Quest 2", own.text)
        }
}
