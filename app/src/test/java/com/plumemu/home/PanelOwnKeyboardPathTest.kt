package com.plumemu.home

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import com.plumemu.R
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.game.FixBoxArtTags
import com.plumemu.game.FixNameTags
import com.plumemu.game.GamePanelTags
import com.plumemu.game.resultKey
import com.plumemu.game.sampleFacts
import com.plumemu.home.search.SearchTags
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.settings.ButtonLetters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The game panel's fields with Plumemu's keyboard on the whole main screen,
 * through a real router fed real key events and real touches: Fix name…, Use my own name and Fix
 * box art…, each with "Confirm with B" off and on: A opens the tray (never the panel by itself);
 * the D-pad, A, B, X, Y, SELECT, START, L1 R1, L2 R2, Back and Escape; the action key acts once; a
 * held B; a hardware keyboard; taps on keys, the field and the hints; Android's keyboard never shows.
 * With Settings › Controls › Keyboard on Android, Search and the panels use Android's keyboard again.
 * Android 9 (sdk 28), as the other keyboard tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class PanelOwnKeyboardPathTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions(facts = { key -> sampleFacts(title = "Game $key", canFixBoxArt = true) })
    private lateinit var router: InputRouter
    private val ime = ImeProbe(compose)
    private var confirm = KeyEvent.KEYCODE_BUTTON_A
    private var back = KeyEvent.KEYCODE_BUTTON_B

    private fun show(
        swapAB: Boolean = false,
        keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
    ) {
        confirm = if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        back = if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        compose.setContent {
            ime.view = LocalView.current
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, InputSettings(swapAB = swapAB)) }
            PlumTheme { HomeScreen(FakeHomeContent.withFavorites(), router, HomeCallbacks(games = games), keyboard = keyboard) }
        }
        compose.waitForIdle()
    }

    private fun press(vararg keyCodes: Int) =
        keyCodes.forEach { code ->
            compose.runOnIdle { router.pressButton(code) }
            compose.waitForIdle()
        }

    private fun hardware(code: Int) {
        compose.runOnIdle {
            router.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, code, 0, 0, VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
            router.onKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_UP, code, 0, 0, VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD))
        }
        compose.waitForIdle()
    }

    private fun typeOnKeyboard(text: String) = text.forEach { hardware(KeyEvent.keyCodeFromString("KEYCODE_${it.uppercaseChar()}")) }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun textOf(tag: String): String =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty()

    private fun hintText(action: Action): String =
        compose.onNodeWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNode().let { node ->
            (listOf(node) + node.children).mapNotNull { it.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { t -> t.text } }.joinToString(" ")
        }

    private val isTrayUp get() = exists(FixNameTags.TRAY)

    private fun textEntry(): TextEntry = compose.runOnIdle { router.textEntry }

    /** One of the three fields: how to reach it from the menu and its tag. */
    private enum class Field(val tag: String) {
        FixName(FixNameTags.FIELD),
        OwnName(FixNameTags.OWN_FIELD),
        FixBoxArt(FixBoxArtTags.FIELD),
    }

    /** Search for "super", the first result's menu (START), then the field's panel by the D-pad. */
    private fun open(field: Field) {
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("super")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }
        press(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START)
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
        // Play, Play with, Favorite, Hide, Name…, Fix box art…, Info.
        when (field) {
            Field.FixBoxArt -> press(*IntArray(5) { KeyEvent.KEYCODE_DPAD_DOWN }, confirm)
            Field.FixName -> press(*IntArray(4) { KeyEvent.KEYCODE_DPAD_DOWN }, confirm, confirm)
            Field.OwnName -> press(*IntArray(4) { KeyEvent.KEYCODE_DPAD_DOWN }, confirm, KeyEvent.KEYCODE_DPAD_DOWN, confirm)
        }
        compose.waitUntil(TIMEOUT_MS) { exists(field.tag) }
    }

    private fun text(field: Field) = textOf(FixNameTags.typedText(field.tag))

    @Test
    fun fixNameEveryButtonWithAConfirming() = everyButton(Field.FixName, swapAB = false)

    @Test
    fun fixNameEveryButtonWithBConfirming() = everyButton(Field.FixName, swapAB = true)

    @Test
    fun ownNameEveryButtonWithAConfirming() = everyButton(Field.OwnName, swapAB = false)

    @Test
    fun ownNameEveryButtonWithBConfirming() = everyButton(Field.OwnName, swapAB = true)

    @Test
    fun fixBoxArtEveryButtonWithAConfirming() = everyButton(Field.FixBoxArt, swapAB = false)

    @Test
    fun fixBoxArtEveryButtonWithBConfirming() = everyButton(Field.FixBoxArt, swapAB = true)

    private fun everyButton(
        field: Field,
        swapAB: Boolean,
    ) {
        show(swapAB)
        open(field)
        val start = text(field)
        assertTrue("pre-filled with the title", start.isNotEmpty())
        assertFalse("the panel opens with the tray down", isTrayUp)
        assertEquals("a hardware keyboard may type into the field", TextEntry.Field, textEntry())
        assertTrue("A Type on the field", hintText(Action.Enter).endsWith("Type"))
        press(confirm)
        assertTrue("A opens the tray", isTrayUp)
        assertEquals(TextEntry.Keyboard, textEntry())
        // The hints while typing (their letters per confirm setting: theTypingHintsFollowTheConfirmSetting).
        assertTrue(hintText(Action.Enter).endsWith("Type"))
        assertTrue(hintText(Action.Erase()).endsWith("Delete"))
        assertTrue(hintText(Action.Search).endsWith("Space"))
        assertTrue(hintText(Action.Menu).endsWith(if (field == Field.OwnName) "Save" else "Search"))
        assertTrue(hintText(Action.ToggleFavorite).endsWith("123"))
        assertTrue(hintText(Action.ToggleView).endsWith("Shift"))
        assertTrue(hintText(Action.NextSystem).endsWith("Cursor"))
        // A types the focused key (q) at the caret (the end); the D-pad moves on the keys; B deletes; X types a space.
        press(confirm, KeyEvent.KEYCODE_DPAD_RIGHT, confirm)
        assertEquals(start + "qw", text(field))
        press(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_UP)
        assertTrue("Up from the top row stays on the keys", isTrayUp)
        press(back)
        assertEquals(start + "q", text(field))
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("${start}q ", text(field))
        // SELECT: one capital; Y: the digits page, back to letters.
        press(KeyEvent.KEYCODE_BUTTON_SELECT, confirm, confirm)
        assertEquals("${start}q Ww", text(field))
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertTrue(hintText(Action.ToggleFavorite).endsWith("ABC"))
        press(confirm, KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals("${start}q Ww2", text(field))
        // L2 / R2 to the ends, L1 / R1 by one: A types where the caret is (w focused).
        press(KeyEvent.KEYCODE_BUTTON_L2, confirm)
        assertEquals("w${start}q Ww2", text(field))
        press(KeyEvent.KEYCODE_BUTTON_R1, confirm)
        assertEquals("w${start.first()}w${start.drop(1)}q Ww2", text(field))
        press(KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1, confirm)
        assertEquals("w${start.first()}w${start.drop(1)}q Www2", text(field))
        // The system's Back and Escape put the tray away; the panel stays; A brings it back.
        press(KeyEvent.KEYCODE_BACK)
        assertFalse(isTrayUp)
        assertTrue(exists(field.tag))
        assertEquals(TextEntry.Field, textEntry())
        press(confirm)
        assertTrue(isTrayUp)
        press(KeyEvent.KEYCODE_ESCAPE)
        assertFalse(isTrayUp)
        assertTrue(exists(field.tag))
        // START: the field's action, once. Fix name with nothing matching: nothing to go to, as Android's search key.
        press(confirm)
        if (field == Field.FixName) {
            press(KeyEvent.KEYCODE_BUTTON_START)
            assertTrue("no result to go to: the tray stays", isTrayUp)
            press(KeyEvent.KEYCODE_BUTTON_R2)
            repeat(text(field).length) { press(back) }
            typeOnKeyboard("zork")
            compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.row(resultKey(0))) }
        }
        val typed = text(field)
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertFalse("the action puts the tray away", isTrayUp)
        when (field) {
            Field.FixName -> {
                assertTrue("Fix name: picks nothing", games.chosenTitles.isEmpty())
                assertTrue("the panel stays, on the results", exists(GamePanelTags.PANEL))
            }
            Field.OwnName -> {
                compose.waitUntil(TIMEOUT_MS) { games.ownNames.isNotEmpty() }
                assertEquals("Use my own name: saved once", 1, games.ownNames.size)
                assertEquals(typed.trim(), games.ownNames.single().second)
            }
            Field.FixBoxArt -> {
                compose.waitUntil(TIMEOUT_MS) { games.boxArtSearches.isNotEmpty() }
                assertEquals("Fix box art: searched once", listOf(typed.trim()), games.boxArtSearches)
            }
        }
        assertEquals("no typing keys off the field", TextEntry.None, textEntry())
        assertFalse("Android's keyboard never shows", ime.keyboardShows)
    }

    @Test
    fun theTypingHintsFollowTheConfirmSetting() {
        val words = listOf(R.string.account_hint_type, R.string.home_hint_delete, R.string.home_hint_space, R.string.home_hint_save, R.string.home_hint_symbols, R.string.home_hint_shift, R.string.home_hint_cursor)
        for (setting in ConfirmButtonSetting.entries) {
            val letters = ButtonLetters.of(setting)
            val hints = typingHints(onLetters = true, actionWord = R.string.home_hint_save, hasShift = true, letters = letters)
            assertEquals(words, hints.map { it.label })
            assertEquals(
                listOf(letters.confirm, letters.back, R.string.home_key_x, R.string.home_key_start, R.string.home_key_y, R.string.home_key_select, R.string.home_key_l1_r1),
                hints.map { it.key },
            )
            assertEquals(listOf(Action.Enter, Action.Erase(), Action.Search, Action.Menu, Action.ToggleFavorite, Action.ToggleView, Action.NextSystem), hints.map { it.action })
        }
        assertEquals("A on the confirm setting's letter", R.string.home_key_b, ButtonLetters.of(ConfirmButtonSetting.Bottom).confirm)
    }

    @Test
    fun aHeldBErasesToEmptyThenAFreshBHidesTheTrayAndTheNextOneLeaves() {
        show()
        open(Field.FixName)
        press(confirm)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)) }
        repeat(HELD_FRAMES) {
            Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("held, B erases everything", "", text(Field.FixName))
        assertTrue("held, B never hides the tray", isTrayUp)
        press(back)
        assertFalse("a fresh B on the empty field hides the tray", isTrayUp)
        assertTrue("the panel stays", exists(FixNameTags.FIELD))
        press(back)
        assertFalse("B with the tray down steps out of Fix name", exists(FixNameTags.FIELD))
        assertTrue(exists(GamePanelTags.PANEL))
    }

    @Test
    fun anEmptyFieldStartsWithACapital() {
        show()
        open(Field.OwnName)
        press(confirm, KeyEvent.KEYCODE_BUTTON_R2)
        repeat(text(Field.OwnName).length) { press(back) }
        assertEquals("", text(Field.OwnName))
        assertTrue("the empty field names itself", textOf(FixNameTags.placeholder(FixNameTags.OWN_FIELD)).isNotEmpty())
        press(confirm, confirm)
        assertEquals("Qq", text(Field.OwnName))
    }

    @Test
    fun aHardwareKeyboardTypesWithTheTrayDownAndUpAndEnterActsOnce() {
        show()
        open(Field.FixBoxArt)
        val start = text(Field.FixBoxArt)
        typeOnKeyboard("zx")
        assertEquals(start + "zx", text(Field.FixBoxArt))
        assertFalse("typing on a hardware keyboard does not bring the tray up", isTrayUp)
        hardware(KeyEvent.KEYCODE_DEL)
        hardware(KeyEvent.KEYCODE_DEL)
        assertEquals(start, text(Field.FixBoxArt))
        press(confirm)
        typeOnKeyboard("q")
        assertEquals(start + "q", text(Field.FixBoxArt))
        hardware(KeyEvent.KEYCODE_ENTER)
        compose.waitUntil(TIMEOUT_MS) { games.boxArtSearches.isNotEmpty() }
        assertEquals(listOf(start + "q"), games.boxArtSearches)
    }

    @Test
    fun touchKeysTheHideKeyTheFieldAndEveryHint() {
        show()
        open(Field.OwnName)
        val start = text(Field.OwnName)
        tap(FixNameTags.OWN_FIELD)
        assertTrue("a tap on the field opens the tray", isTrayUp)
        tap(keyboardKeyTag(KeyId(0, 0)))
        assertTrue("a tap on a key types it at the caret", text(Field.OwnName).contains("q"))
        assertEquals(start.length + 1, text(Field.OwnName).length)
        tap(HomeTags.hint(Action.Erase()))
        assertEquals("B Delete deletes", start, text(Field.OwnName))
        tap(HomeTags.hint(Action.Search))
        assertEquals("X Space types a space", start.length + 1, text(Field.OwnName).length)
        tap(HomeTags.hint(Action.ToggleFavorite))
        assertTrue(hintText(Action.ToggleFavorite).endsWith("ABC"))
        tap(HomeTags.hint(Action.ToggleFavorite))
        tap(HomeTags.hint(Action.ToggleView))
        tap(keyboardKeyTag(KeyId(0, 0)))
        assertTrue("SELECT Shift: a capital", text(Field.OwnName).contains("Q"))
        tap(HomeTags.hint(Action.NextSystem))
        tap(HomeTags.hint(Action.Enter))
        // The hide key: the tray goes down, the cursor stays on the field.
        tap(keyboardKeyTag(KeyId(3, 1)))
        assertFalse(isTrayUp)
        assertTrue(exists(FixNameTags.OWN_FIELD))
        tap(FixNameTags.OWN_FIELD)
        assertTrue(isTrayUp)
        val typed = text(Field.OwnName)
        tap(HomeTags.hint(Action.Menu))
        compose.waitUntil(TIMEOUT_MS) { games.ownNames.isNotEmpty() }
        assertEquals("START Save by touch saves once", listOf(typed.trim()), games.ownNames.map { it.second })
        assertFalse(ime.keyboardShows)
    }

    @Test
    fun theTraysActionKeySearchesOnce() {
        show()
        open(Field.FixBoxArt)
        press(confirm)
        tap(keyboardKeyTag(KeyId(3, 3)))
        compose.waitUntil(TIMEOUT_MS) { games.boxArtSearches.isNotEmpty() }
        assertEquals(1, games.boxArtSearches.size)
        assertFalse(isTrayUp)
    }

    @Test
    fun androidsKeyboardNeverShowsAndNoTextFieldExists() {
        show()
        for (field in Field.entries) {
            open(field)
            press(confirm)
            ime.settle()
            assertFalse("no Android keyboard in ${field.name}", ime.keyboardShows)
            assertTrue("no text field to connect to", compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
            assertEquals("no input connection", null, compose.runOnIdle { ime.view.onCreateInputConnection(android.view.inputmethod.EditorInfo()) })
            // Out of the panel and Search, back to the strip.
            press(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_START, back, back)
            assertEquals(TextEntry.None, textEntry())
        }
    }

    @Test
    fun withTheSettingOnAndroidSearchAndThePanelsUseAndroidsKeyboard() {
        show(keyboard = KeyboardChoice.Android)
        press(KeyEvent.KEYCODE_BUTTON_X)
        ime.settle()
        assertTrue("Search opens Android's keyboard", ime.keyboardShows)
        assertFalse("no tray in Search", exists(SearchTags.TRAY))
        // Typed through Android's field, then to the results and the menu.
        ime.taggedField(SearchTags.FIELD).performTextInput("super")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_START)
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
        press(*IntArray(4) { KeyEvent.KEYCODE_DPAD_DOWN }, confirm, confirm)
        compose.waitUntil(TIMEOUT_MS) { exists(FixNameTags.FIELD) }
        ime.settle()
        assertFalse("the panel opens with the keyboard down", ime.keyboardShows)
        press(confirm)
        ime.settle()
        assertTrue("A opens Android's keyboard in Fix name", ime.keyboardShows)
        assertFalse("no tray", isTrayUp)
        assertEquals("the router sends no typing keys", TextEntry.None, textEntry())
    }

    @Test
    fun nothingRunsWhileAPanelFieldIsTypingAndIdle() {
        show()
        open(Field.FixName)
        press(confirm, KeyEvent.KEYCODE_BUTTON_R2)
        repeat(text(Field.FixName).length) { press(back) }
        typeOnKeyboard("zork")
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.row(resultKey(0))) }
        compose.waitForIdle()
        val writes = mutableListOf<Set<Any>>()
        val observer = Snapshot.registerApplyObserver { changed, _ -> writes += changed }
        try {
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(IDLE_MS)
            compose.mainClock.advanceTimeByFrame()
            assertTrue("no state changes while typing is idle (a caret blink or animation would): $writes", writes.isEmpty())
        } finally {
            observer.dispose()
            compose.mainClock.autoAdvance = true
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** The virtual keyboard's device id: its key map types like a full keyboard. */
        const val VIRTUAL_KEYBOARD = -1

        /** Two seconds at 60 Hz: enough repeats to erase a title and more. */
        const val HELD_FRAMES = 120

        const val IDLE_MS = 10_000L
    }
}
