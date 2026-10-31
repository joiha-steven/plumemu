package com.plumemu.setup

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasSetTextAction
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setup's box art sign-in typed with Plumemu's keyboard, through the real router:
 * every button with A or B confirming, a held B, the system's Back, taps on keys and fields, a
 * hardware keyboard, "Next" landing once on the password with the tray up, "Sign in" submitting
 * exactly once (counted at the fake account), and the wrong paths: empty fields, a refused sign-in,
 * presses while the account is checked. What must never happen: Android's keyboard, a text field to
 * connect to, a state written while typing is idle, typing keys after setup left, the
 * tray coming up by itself. Android 9 (sdk 28), where the IME shadow sees Compose's requests.
 * Passwords here are made-up test values and never appear in a test name or a failure message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SetupOwnKeyboardTest : SetupKeyboardPathHarness() {
    private fun typed(item: SignInItem) = textOf(SignInTags.typedText(item))

    private fun assertNoAndroidKeyboard(where: String) {
        ime.settle()
        assertFalse("no Android keyboard $where", ime.keyboardShows)
        assertTrue("no text field to connect to $where", compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        assertNull("no input connection $where", compose.runOnIdle { ime.view.onCreateInputConnection(EditorInfo()) })
    }

    private fun assertNothingWritesWhileIdle(where: String) {
        compose.waitForIdle()
        val writes = mutableListOf<Set<Any>>()
        val observer = Snapshot.registerApplyObserver { changed, _ -> writes += changed }
        try {
            compose.mainClock.autoAdvance = false
            compose.mainClock.advanceTimeBy(IDLE_MS)
            compose.mainClock.advanceTimeByFrame()
            assertTrue("no state changes while $where is typing and idle (a caret blink or animation would): ${writes.size} writes", writes.isEmpty())
        } finally {
            observer.dispose()
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun theFormOpensWithTheTrayDownAndNoAndroidKeyboard() {
        showTheForm()
        assertEquals(SignInItem.Username, form.focus)
        assertNoTray("when the form opens")
        assertNoAndroidKeyboard("when the form opens")
        assertEquals("a hardware keyboard may type on the field", TextEntry.Field, textEntry())
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertNoTray("after moving onto the password")
    }

    @Test
    fun everyButtonWithAConfirming() = everyButton(swapAB = false)

    @Test
    fun everyButtonWithBConfirming() = everyButton(swapAB = true)

    private fun everyButton(swapAB: Boolean) {
        showTheForm(swapAB)
        press(confirm)
        assertTrue("A opens the tray", isTrayUp)
        assertEquals(TextEntry.Keyboard, textEntry())
        assertTrue(hintText(Action.Menu).endsWith("Next"))
        assertTrue(hintText(Action.Erase()).endsWith("Delete"))
        // A types the focused key (q), Right then A the next (w); SELECT shifts one letter.
        press(confirm, KeyEvent.KEYCODE_DPAD_RIGHT, confirm, KeyEvent.KEYCODE_BUTTON_SELECT, confirm)
        assertEquals("qwW", form.username)
        // L2 / R2 to the start and the end, L1 / R1 one character.
        press(KeyEvent.KEYCODE_BUTTON_L2, confirm, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1, confirm, KeyEvent.KEYCODE_BUTTON_R1)
        assertEquals("wqwwW", form.username)
        // X a space, B erases it; Y the symbols and back.
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("wqwwW ", form.username)
        press(back)
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals(KeyboardPage.Symbols, form.usernameKeys.keys.page)
        assertTrue(hintText(Action.ToggleFavorite).endsWith("ABC"))
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals("the D-pad never leaves the keys", SignInItem.Username, form.focus)
        // START: Next, once, the tray still up on the password, its own keys.
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertEquals(SignInItem.Password, form.focus)
        assertTrue(isTrayUp)
        assertEquals(KeyboardPage.Letters, form.passwordKeys.keys.page)
        assertTrue(hintText(Action.Menu).endsWith("Sign in"))
        typeOnKeyboard("abc123")
        assertEquals("the password shows only dots", "\u2022".repeat(6), typed(SignInItem.Password))
        assertEquals("wqwwW", form.username)
        assertNoAndroidKeyboard("while typing the password")
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertEquals("one sign-in", 1, accounts.signIns.size)
        assertEquals("wqwwW", accounts.signIns.single().first)
        assertEquals("wqwwW", form.signedInAs)
        assertNoTray("after signing in")
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun bErasesAHeldBRepeatsAFreshBOnEmptyPutsTheTrayAwayAndTheNextBLeavesWithAConfirming() = bOnEmpty(swapAB = false)

    @Test
    fun bErasesAHeldBRepeatsAFreshBOnEmptyPutsTheTrayAwayAndTheNextBLeavesWithBConfirming() = bOnEmpty(swapAB = true)

    private fun bOnEmpty(swapAB: Boolean) {
        showTheForm(swapAB)
        press(confirm)
        typeOnKeyboard("plumtester")
        holdB()
        assertEquals("a held B erased the whole name", "", form.username)
        assertTrue("a held B never puts the tray away", isTrayUp)
        press(back)
        assertNoTray("after a fresh B on an empty field")
        assertEquals(SignInItem.Username, form.focus)
        assertEquals(TextEntry.Field, textEntry())
        press(back)
        assertNull("the next B leaves the form for the question", model.state.value.boxArt)
        assertEquals(SetupControl.UseBoxArt, model.state.value.focus)
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun theSystemsBackPutsTheTrayAwayAndAAgainBringsItBack() {
        showTheForm()
        press(confirm)
        typeOnKeyboard("plum")
        press(KeyEvent.KEYCODE_BACK)
        assertNoTray("after the system's Back")
        assertEquals("the form stays, the name too", "plum", form.username)
        assertEquals(TextEntry.Field, textEntry())
        press(confirm)
        assertTrue(isTrayUp)
        press(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BACK)
        assertNull("Back with the tray down leaves the form", model.state.value.boxArt)
    }

    @Test
    fun tapsOnFieldsAndKeys() {
        showTheForm()
        tap(USERNAME_FIELD)
        assertTrue("a tap on the field brings the tray up", isTrayUp)
        tap(keyboardKeyTag(KeyId(0, 0)))
        tap(keyboardKeyTag(KeyId(0, 1)))
        assertEquals("qw", form.username)
        assertEquals("the caret shows where typing goes", "qw", typed(SignInItem.Username))
        // The hide key puts the tray away; a tap on the password brings it back there.
        tap(keyboardKeyTag(KeyId(3, 1)))
        assertNoTray("after the hide key")
        tap(PASSWORD_FIELD)
        assertEquals(SignInItem.Password, form.focus)
        assertTrue(isTrayUp)
        tap(keyboardKeyTag(KeyId(1, 0)))
        // A tap on the username with the tray up keeps the same tray, now on the name.
        tap(USERNAME_FIELD)
        assertEquals(SignInItem.Username, form.focus)
        assertTrue(isTrayUp)
        // The tray's action key: Next, then Sign in, once.
        tap(keyboardKeyTag(KeyId(3, 3)))
        assertEquals(SignInItem.Password, form.focus)
        assertTrue(isTrayUp)
        tap(keyboardKeyTag(KeyId(3, 3)))
        assertEquals(1, accounts.signIns.size)
        assertEquals("qw", accounts.signIns.single().first)
    }

    @Test
    fun aHardwareKeyboardTypesWithTheTrayDownAndEnterIsNextThenSignIn() {
        showTheForm()
        typeOnKeyboard("plumtester")
        assertEquals("plumtester", form.username)
        assertNoTray("for a hardware keyboard")
        hardware(KeyEvent.KEYCODE_ENTER)
        assertEquals(SignInItem.Password, form.focus)
        assertNoTray("after Enter")
        typeOnKeyboard("abc")
        hardware(KeyEvent.KEYCODE_DEL)
        assertEquals(2, form.password.length)
        hardware(KeyEvent.KEYCODE_ENTER)
        assertEquals(1, accounts.signIns.size)
    }

    @Test
    fun emptyFieldsSayWhatIsMissingAndSubmitNothing() {
        showTheForm()
        press(confirm, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START)
        assertTrue(accounts.signIns.isEmpty())
        assertEquals(SignInResult.Missing, form.message)
        assertTrue(exists(SignInTags.MESSAGE))
        assertEquals("back to the empty name, the tray down", SignInItem.Username, form.focus)
        assertNoTray("after the refusal")
        press(confirm)
        typeOnKeyboard("plumtester")
        press(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START)
        assertTrue(accounts.signIns.isEmpty())
        assertEquals(SignInItem.Password, form.focus)
    }

    @Test
    fun aRefusedSignInKeepsTheFieldsAndAOnTheFieldBringsTheTrayBack() {
        accounts.answer = { _, _ -> SignInResult.WrongCredentials }
        showTheForm()
        press(confirm)
        typeOnKeyboard("plumtester")
        press(KeyEvent.KEYCODE_BUTTON_START)
        typeOnKeyboard("abc")
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertEquals(1, accounts.signIns.size)
        assertEquals(SignInResult.WrongCredentials, form.message)
        assertEquals("plumtester", form.username)
        assertEquals(3, form.password.length)
        assertEquals(SignInItem.Password, form.focus)
        assertNoTray("after the answer")
        press(confirm)
        assertTrue(isTrayUp)
    }

    @Test
    fun whileCheckingTheTrayIsDownAndNothingSignsInAgain() {
        accounts.holdsAnswers = true
        showTheForm()
        press(confirm)
        typeOnKeyboard("plumtester")
        press(KeyEvent.KEYCODE_BUTTON_START)
        typeOnKeyboard("abc")
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue(form.isChecking)
        assertNoTray("while checking")
        assertEquals(TextEntry.None, textEntry())
        press(KeyEvent.KEYCODE_BUTTON_START, confirm, confirm)
        hardware(KeyEvent.KEYCODE_ENTER)
        typeOnKeyboard("x")
        assertEquals("one sign-in while the first is checked", 1, accounts.signIns.size)
        assertEquals(3, form.password.length)
        accounts.release()
        compose.waitForIdle()
        assertEquals("plumtester", form.signedInAs)
    }

    @Test
    fun typingIdleWritesNothingAndHasNoAndroidKeyboard() {
        showTheForm()
        press(confirm)
        typeOnKeyboard("plum")
        assertNoAndroidKeyboard("on the username")
        assertNothingWritesWhileIdle("the username")
        press(KeyEvent.KEYCODE_BUTTON_START)
        typeOnKeyboard("abc")
        assertNoAndroidKeyboard("on the password")
        assertNothingWritesWhileIdle("the password")
    }

    @Test
    fun setupLeavingWhileTypingGivesBBack() {
        showTheForm()
        press(confirm)
        assertEquals(TextEntry.Keyboard, textEntry())
        shown = false
        compose.waitForIdle()
        assertEquals("no other screen gets an erase or a typed key", TextEntry.None, textEntry())
    }

    @Test
    fun skipLeavesWithNoTypingKeys() {
        showTheForm()
        press(confirm)
        press(KeyEvent.KEYCODE_BACK)
        // Down to Sign in, Right to Skip, A.
        press(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
        assertEquals(TextEntry.None, textEntry())
        press(confirm)
        assertEquals(SetupStep.Scan, model.state.value.step)
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun androidsKeyboardHasNoTray() {
        showTheForm(keyboard = KeyboardChoice.Android)
        press(confirm)
        assertNoTray("on Android's path")
        assertEquals(TextEntry.None, textEntry())
        assertTrue("Android's text fields", compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
