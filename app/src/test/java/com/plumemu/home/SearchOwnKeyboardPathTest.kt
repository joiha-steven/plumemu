package com.plumemu.home

import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.InputSettings
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FakeGameActions
import com.plumemu.game.GamePanelTags
import com.plumemu.home.search.SearchTags
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search with Plumemu's keyboard on the whole main screen, through a real router fed real key
 * events (as MainActivity hands them) and real touches: every button in typing, on the results and
 * on the field with the tray down, with "Confirm with B" off and on; a held B; a hardware keyboard;
 * taps on keys, the hide key, the query line, results and every hint; Android's keyboard never
 * shows; nothing runs while typing is idle. Android 9 (sdk 28), as the other keyboard tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
class SearchOwnKeyboardPathTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private lateinit var router: InputRouter
    private val ime = ImeProbe(compose)

    private fun show(swapAB: Boolean = false) {
        compose.setContent {
            ime.view = LocalView.current
            val scope = rememberCoroutineScope()
            router = remember { InputRouter(scope, InputSettings(swapAB = swapAB)) }
            PlumTheme { HomeScreen(FakeHomeContent.withFavorites(), router, HomeCallbacks(games = games)) }
        }
        compose.waitForIdle()
    }

    private fun press(vararg keyCodes: Int) =
        keyCodes.forEach { code ->
            compose.runOnIdle { router.pressButton(code) }
            compose.waitForIdle()
        }

    private fun hardwareKey(
        action: Int,
        code: Int,
    ) = KeyEvent(0L, 0L, action, code, 0, 0, KeyCharacterMapVirtual, 0, 0, InputDevice.SOURCE_KEYBOARD)

    /** Typed on a Bluetooth keyboard, key by key (letters only). */
    private fun typeOnKeyboard(text: String) {
        for (char in text) {
            val code = KeyEvent.keyCodeFromString("KEYCODE_${char.uppercaseChar()}")
            compose.runOnIdle {
                router.onKeyEvent(hardwareKey(KeyEvent.ACTION_DOWN, code))
                router.onKeyEvent(hardwareKey(KeyEvent.ACTION_UP, code))
            }
        }
        compose.waitForIdle()
    }

    private fun hardware(code: Int) {
        compose.runOnIdle {
            router.onKeyEvent(hardwareKey(KeyEvent.ACTION_DOWN, code))
            router.onKeyEvent(hardwareKey(KeyEvent.ACTION_UP, code))
        }
        compose.waitForIdle()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun query(): String =
        compose.onAllNodesWithTag(SearchTags.QUERY, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty()

    private fun hintText(action: Action): String =
        compose.onNodeWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNode().let { node ->
            (listOf(node) + node.children).mapNotNull { it.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { t -> t.text } }.joinToString(" ")
        }

    private val isOpen get() = exists(SearchTags.TOP_BAR)
    private val isTyping get() = isOpen && exists(SearchTags.TRAY)

    /** On the results: the tray is down and A says Play. */
    private val isOnResults get() = isOpen && !exists(SearchTags.TRAY) && exists(HomeTags.hint(Action.Play))

    /** The field with the tray down: no tray, A says Type. */
    private val isOnFieldTrayDown get() = isOpen && !exists(SearchTags.TRAY) && exists(HomeTags.hint(Action.Enter))

    private fun waitForResults() = compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.result(0)) }

    @Test
    fun everyButtonWithAConfirming() = everyButton(swapAB = false)

    @Test
    fun everyButtonWithBConfirming() = everyButton(swapAB = true)

    private fun everyButton(swapAB: Boolean) {
        show(swapAB)
        val confirm = if (swapAB) KeyEvent.KEYCODE_BUTTON_B else KeyEvent.KEYCODE_BUTTON_A
        val back = if (swapAB) KeyEvent.KEYCODE_BUTTON_A else KeyEvent.KEYCODE_BUTTON_B
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertTrue("X opens Search, typing", isTyping)
        assertEquals("", query())
        // Typing: A types the focused key (q), the D-pad moves, B erases, X types a space.
        press(confirm, KeyEvent.KEYCODE_DPAD_RIGHT, confirm)
        assertEquals("qw", query())
        press(back)
        assertEquals("q", query())
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("q ", query())
        press(back, back)
        assertTrue("B down to empty never closes by itself", isTyping)
        // Y: 123 / ABC. SELECT: nothing (no shift).
        assertTrue(hintText(Action.ToggleFavorite).contains("123"))
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertTrue(hintText(Action.ToggleFavorite).contains("ABC"))
        press(confirm)
        assertEquals("the digits page keeps the column: w's place is 2", "2", query())
        press(KeyEvent.KEYCODE_BUTTON_Y, back, KeyEvent.KEYCODE_BUTTON_SELECT)
        assertTrue(isTyping)
        assertEquals("", query())
        // L1 R1 move the caret, L2 R2 jump to the ends; A types where the caret is (w is focused).
        typeOnKeyboard("ab")
        press(KeyEvent.KEYCODE_BUTTON_L1, confirm)
        assertEquals("awb", query())
        press(KeyEvent.KEYCODE_BUTTON_L2, confirm)
        assertEquals("wawb", query())
        press(KeyEvent.KEYCODE_BUTTON_R2, confirm)
        assertEquals("wawbw", query())
        press(KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R1, confirm)
        assertEquals("wwawbw", query())
        press(KeyEvent.KEYCODE_BUTTON_R2)
        repeat(6) { press(back) }
        assertEquals("", query())
        // START goes to the results (none yet: nothing); with results it does.
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue(isTyping)
        typeOnKeyboard("super")
        waitForResults()
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue("START: the results, the tray down", isOnResults)
        // Results: Down, Y favorite, START menu (B closes it), X typing, Up typing, A plays, B closes.
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals(1, games.favorites.size)
        press(KeyEvent.KEYCODE_BUTTON_START)
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
        press(back)
        assertFalse(exists(GamePanelTags.PANEL))
        assertTrue(isOnResults)
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertTrue("X: typing again", isTyping)
        // A fresh Up from the keys' top row goes to the first result; from it, a fresh Up comes back.
        press(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(isOnResults)
        press(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(isTyping)
        press(KeyEvent.KEYCODE_BUTTON_START, confirm)
        assertEquals("A plays the focused result", 1, games.launched.size)
        press(back)
        assertFalse("B on the results closes Search", isOpen)
        // The field with the tray down: the system's Back and Escape put the tray away; A or X bring it back; B closes.
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("zzzz")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.NO_MATCH) }
        press(KeyEvent.KEYCODE_BACK)
        assertTrue("Back with no results: the field, tray down", isOnFieldTrayDown)
        press(confirm)
        assertTrue(isTyping)
        press(KeyEvent.KEYCODE_ESCAPE)
        assertTrue(isOnFieldTrayDown)
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertTrue(isTyping)
        press(KeyEvent.KEYCODE_BACK)
        press(back)
        assertFalse(isOpen)
        // With results, the system's Back goes to them; B there closes.
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("mario")
        waitForResults()
        press(KeyEvent.KEYCODE_BACK)
        assertTrue(isOnResults)
        press(back)
        assertFalse(isOpen)
        // A fresh B on an empty query closes Search.
        press(KeyEvent.KEYCODE_BUTTON_X, back)
        assertFalse(isOpen)
    }

    @Test
    fun aHeldBErasesStopsAtEmptyAndDoesNotCloseThenAFreshBCloses() {
        show()
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("zelda")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)) }
        repeat(HELD_FRAMES) {
            Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle { router.onKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B)) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals("", query())
        assertTrue("held, B stops at an empty query", isTyping)
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertFalse("a fresh B then closes", isOpen)
    }

    @Test
    fun aHardwareKeyboardTypesDeletesMovesTheCaretAndEnterGoesToTheResults() {
        show()
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("supr")
        hardware(KeyEvent.KEYCODE_DPAD_LEFT) // from a virtual (non-alphabetic in Robolectric) device: the D-pad
        hardware(KeyEvent.KEYCODE_DEL)
        assertEquals("sup", query())
        typeOnKeyboard("er")
        hardware(KeyEvent.KEYCODE_SPACE)
        hardware(KeyEvent.KEYCODE_FORWARD_DEL)
        assertEquals("super ", query())
        hardware(KeyEvent.KEYCODE_DEL)
        waitForResults()
        hardware(KeyEvent.KEYCODE_ENTER)
        assertTrue("Enter: the results", isOnResults)
        press(KeyEvent.KEYCODE_BUTTON_X)
        press(KeyEvent.KEYCODE_BACK) // results: tray down at the first result
        press(KeyEvent.KEYCODE_BUTTON_B)
        // The tray down on the field still takes a keyboard's letters.
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("qqq")
        compose.waitUntil(TIMEOUT_MS) { exists(SearchTags.NO_MATCH) }
        press(KeyEvent.KEYCODE_BACK)
        assertTrue(isOnFieldTrayDown)
        hardware(KeyEvent.KEYCODE_DEL)
        assertEquals("qq", query())
    }

    @Test
    fun aTapOnTheTrayBesideTheCentredKeysDoesNothing() {
        show()
        tap(HomeTags.hint(Action.Search))
        typeOnKeyboard("super")
        waitForResults()
        assertEquals("super", query())
        assertTrue("results are listed under the tray", exists(SearchTags.result(0)))
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        val keyRow = compose.onNodeWithTag(keyboardKeyTag(KeyId(1, 0)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // 10 dp from either edge, level with the second row: left and right of the 476 dp block (146 dp each side at 768).
        for (x in listOf(10f * density, root.width - 10f * density)) {
            compose.onRoot().performTouchInput { click(Offset(x, keyRow.center.y)) }
            compose.waitForIdle()
            assertEquals("nothing typed at x=$x", "super", query())
            assertTrue("still typing, no result opened (x=$x)", isTyping)
        }
        // The focus stayed on q (the hardware keyboard does not move it): A types q.
        tap(HomeTags.hint(Action.Enter))
        assertEquals("superq", query())
    }

    @Test
    fun touchKeysTheHideKeyTheQueryLineResultsAndEveryHint() {
        show()
        tap(HomeTags.hint(Action.Search))
        tap(keyboardKeyTag(KeyId(0, 1)))
        assertEquals("a tap on w types it", "w", query())
        tap(keyboardKeyTag(KeyId(2, 7)))
        assertEquals("the delete key", "", query())
        tap(keyboardKeyTag(KeyId(3, 1)))
        assertTrue("the hide key, no results: the field with the tray down", isOnFieldTrayDown)
        tap(SearchTags.FIELD)
        assertTrue("a tap on the query line: typing", isTyping)
        // The caret lands where the line was tapped.
        typeOnKeyboard("mario")
        compose.onNodeWithTag(SearchTags.FIELD, useUnmergedTree = true).performTouchInput { click(centerLeft) }
        compose.waitForIdle()
        typeOnKeyboard("x")
        assertEquals("tapped before the m", "xmario", query())
        compose.onNodeWithTag(SearchTags.FIELD, useUnmergedTree = true).performTouchInput { click(centerRight) }
        compose.waitForIdle()
        typeOnKeyboard("y")
        assertEquals("tapped past the end", "xmarioy", query())
        // Every typing hint does its button's job.
        repeat(7) { tap(HomeTags.hint(Action.Erase())) }
        assertEquals("", query())
        typeOnKeyboard("super")
        tap(keyboardKeyTag(KeyId(0, 1)))
        tap(HomeTags.hint(Action.Enter))
        assertEquals("A Type: the focused key (w, tapped last)", "superww", query())
        tap(HomeTags.hint(Action.Search))
        assertEquals("X Space", "superww ", query())
        repeat(3) { tap(HomeTags.hint(Action.Erase())) }
        assertEquals("B Delete", "super", query())
        tap(HomeTags.hint(Action.ToggleFavorite))
        assertTrue("Y 123 turns the page", hintText(Action.ToggleFavorite).contains("ABC"))
        tap(HomeTags.hint(Action.ToggleFavorite))
        tap(HomeTags.hint(Action.NextSystem)) // L1 R1 Cursor: one right, already at the end
        waitForResults()
        tap(HomeTags.hint(Action.Menu))
        assertTrue("START Search: the results", isOnResults)
        // Results' hints: X Type, Y Favorite, START Menu, A Play, B Back.
        tap(HomeTags.hint(Action.Search))
        assertTrue("X Type", isTyping)
        tap(HomeTags.hint(Action.Menu))
        tap(HomeTags.hint(Action.ToggleFavorite))
        assertEquals(1, games.favorites.size)
        tap(HomeTags.hint(Action.Menu))
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
        press(KeyEvent.KEYCODE_BUTTON_B)
        tap(HomeTags.hint(Action.Play))
        assertEquals(1, games.launched.size)
        tap(HomeTags.hint(Action.Back))
        assertFalse(isOpen)
        // A result: the first tap focuses it (the tray goes down), the second plays; a long press opens its menu.
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("super")
        waitForResults()
        tap(SearchTags.result(1))
        assertTrue(isOnResults)
        assertEquals(1, games.launched.size)
        tap(SearchTags.result(1))
        assertEquals(2, games.launched.size)
        press(KeyEvent.KEYCODE_BUTTON_X)
        compose.onNodeWithTag(SearchTags.result(0), useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
    }

    /**
     * A favorite written from the results fails after the person went back to typing: its message
     * opens over Search. While it shows, B is the panel's Back (it closes it), not an erase the panel
     * would drop; closed, B erases in Search again.
     */
    @Test
    fun aFailedWriteOpeningItsMessageOverTypingTakesBAndGivesItBack() {
        show()
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("super")
        waitForResults()
        press(KeyEvent.KEYCODE_BUTTON_START)
        games.writeGate = CompletableDeferred()
        games.writeError = IOException("disk I/O error")
        press(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X)
        assertTrue("typing again while the write waits", isTyping)
        compose.runOnIdle { games.writeGate!!.complete(Unit) }
        compose.waitUntil(TIMEOUT_MS) { exists(GamePanelTags.PANEL) }
        typeOnKeyboard("x")
        assertEquals("a keyboard's letters do not reach Search under the message", "super", query())
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertFalse("B closes the message", exists(GamePanelTags.PANEL))
        assertTrue(isTyping)
        assertEquals("super", query())
        press(KeyEvent.KEYCODE_BUTTON_B)
        assertEquals("then B erases in Search again", "supe", query())
    }

    @Test
    fun androidsKeyboardNeverShowsAndNoTextFieldExists() {
        show()
        press(KeyEvent.KEYCODE_BUTTON_X)
        ime.settle()
        typeOnKeyboard("mario")
        tap(SearchTags.FIELD)
        ime.settle()
        assertFalse("no Android keyboard in Search", ime.keyboardShows)
        assertTrue("no text field to connect to", compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertEquals("no input connection", null, compose.runOnIdle { ime.view.onCreateInputConnection(android.view.inputmethod.EditorInfo()) })
    }

    @Test
    fun nothingRunsWhileTypingIsIdleAndTheTrayIsNotThereBeforeSearch() {
        show()
        assertFalse("nothing of the keyboard at start", exists(SearchTags.TRAY) || exists(keyboardKeyTag(KeyId(0, 0))))
        press(KeyEvent.KEYCODE_BUTTON_X)
        typeOnKeyboard("mario")
        waitForResults()
        compose.waitForIdle()
        val writes = mutableListOf<Set<Any>>()
        val observer = Snapshot.registerApplyObserver { changed, _ -> writes += changed }
        try {
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(IDLE_MS)
            compose.mainClock.advanceTimeByFrame()
            assertTrue("no state changes while typing is idle (a caret blink or animation would): ${writes.flatten()}", writes.isEmpty())
        } finally {
            observer.dispose()
            compose.mainClock.autoAdvance = true
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        /** The virtual keyboard's device id: its key map types like a full keyboard. */
        const val KeyCharacterMapVirtual = -1

        /** Two seconds at 60 Hz: enough repeats to erase five characters and more. */
        const val HELD_FRAMES = 120

        const val IDLE_MS = 10_000L
    }
}
