package com.plumemu.settings.screen

import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.hasSetTextAction
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyId
import com.plumemu.coreui.components.keyboard.keyboardKeyTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings › Library › Sign in to ScreenScraper typed with Plumemu's keyboard,
 * on the real screen with a real router: every button with A or B confirming, a held B, the
 * system's Back, taps on keys and fields, a hardware keyboard, "Next" landing once on the password
 * with the tray up, "Sign in" submitting exactly once (counted at the fake account), and the wrong
 * paths: empty fields, a refused sign-in, presses while the account is checked. What must never
 * happen: Android's keyboard, a text field to connect to, a state written while typing is idle,
 * typing keys after Settings left, the tray down when the form opens. Android 9 (sdk 28).
 * Passwords here are made-up test values and never appear in a test name or a failure message.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsSignInOwnKeyboardTest : SettingsKeyboardPathHarness() {
    override val content: SettingsContent get() = SettingsContent(account = AccountStatus.SignedOut, chosenBoxArtSource = BoxArtSourceSetting.ScreenScraper)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val isFormOpen get() = exists(SignInTags.FORM)

    private fun typed(item: SignInItem) = textOf(SignInTags.typedText(item))

    /** What the username field shows. */
    private val username get() = typed(SignInItem.Username)

    /** How many dots the password field shows. */
    private val passwordLength get() = typed(SignInItem.Password).length

    /** With the tray up, which field it types in, told by START's word: "Next" on the username, "Sign in" on the password. */
    private val trayField: SignInItem
        get() = if (hintText(Action.Menu).endsWith("Next")) SignInItem.Username else SignInItem.Password

    /** Settings, then Library › the account row and A: the sign-in panel, the cursor on the username, the tray up. */
    private fun openTheForm(
        swapAB: Boolean = false,
        androidKeyboard: Boolean = false,
    ) {
        show(swapAB, androidKeyboard)
        repeat(SettingsSection.Accounts.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.ScraperAccount)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        assertTrue("the sign-in panel is open", isFormOpen)
        assertFalse("the form opens with the tray down", isTrayUp)
        // A on the user name brings the keyboard up (the form no longer opens typing).
        send(Action.Enter)
    }

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
    fun aOnTheUsernameBringsTheTrayUpWithNoAndroidKeyboard() {
        openTheForm()
        assertTrue("the tray is up when the form opens", isTrayUp)
        assertNoAndroidKeyboard("when the form opens")
        assertEquals(TextEntry.Keyboard, textEntry())
        assertTrue("START says Next on the username", hintText(Action.Menu).endsWith("Next"))
        assertEquals(SignInItem.Username, trayField)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue("the D-pad stays on the keys", isTrayUp)
        assertEquals(SignInItem.Username, trayField)
    }

    @Test
    fun everyButtonWithAConfirming() = everyButton(swapAB = false)

    @Test
    fun everyButtonWithBConfirming() = everyButton(swapAB = true)

    private fun everyButton(swapAB: Boolean) {
        openTheForm(swapAB)
        assertTrue("the form opens with the tray up", isTrayUp)
        assertEquals(TextEntry.Keyboard, textEntry())
        assertTrue(hintText(Action.Menu).endsWith("Next"))
        press(confirm, KeyEvent.KEYCODE_DPAD_RIGHT, confirm, KeyEvent.KEYCODE_BUTTON_SELECT, confirm)
        assertEquals("qwW", username)
        press(KeyEvent.KEYCODE_BUTTON_L2, confirm, KeyEvent.KEYCODE_BUTTON_R2, KeyEvent.KEYCODE_BUTTON_L1, confirm, KeyEvent.KEYCODE_BUTTON_R1)
        assertEquals("wqwwW", username)
        press(KeyEvent.KEYCODE_BUTTON_X)
        assertEquals("wqwwW ", username)
        press(back)
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertTrue("Y: the symbols", hintText(Action.ToggleFavorite).endsWith("ABC"))
        press(KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals("the D-pad never leaves the keys", SignInItem.Username, trayField)
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue("Next keeps the tray up", isTrayUp)
        assertEquals(SignInItem.Password, trayField)
        typeOnKeyboard("abc123")
        assertEquals("the password shows only dots", "\u2022".repeat(6), typed(SignInItem.Password))
        assertNoAndroidKeyboard("while typing the password")
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertEquals("one sign-in", 1, actions.accountsFake.signIns.size)
        assertEquals("wqwwW", actions.accountsFake.signIns.single().first)
        assertFalse("a sign-in that worked closes the panel", isFormOpen)
        assertFalse(isTrayUp)
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun bErasesAHeldBRepeatsAFreshBOnEmptyPutsTheTrayAwayAndTheNextBLeaves() {
        openTheForm()
        typeOnKeyboard("plumtester")
        holdB()
        assertEquals("a held B erased the whole name", "", username)
        assertTrue("a held B never puts the tray away", isTrayUp)
        press(back)
        assertFalse(isTrayUp)
        assertEquals(TextEntry.Field, textEntry())
        press(back)
        assertFalse("the next B closes the form", isFormOpen)
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun theSystemsBackPutsTheTrayAwayAndTheFormStays() {
        openTheForm(swapAB = true)
        typeOnKeyboard("plum")
        press(KeyEvent.KEYCODE_BACK)
        assertFalse(isTrayUp)
        assertTrue(isFormOpen)
        assertEquals("plum", username)
        press(confirm)
        assertTrue("A brings it back", isTrayUp)
    }

    @Test
    fun tapsOnFieldsAndKeys() {
        openTheForm()
        tap(SignInTags.USERNAME)
        assertTrue(isTrayUp)
        tap(keyboardKeyTag(KeyId(0, 0)))
        tap(keyboardKeyTag(KeyId(0, 1)))
        assertEquals("qw", username)
        tap(keyboardKeyTag(KeyId(3, 1)))
        assertFalse("the hide key", isTrayUp)
        tap(SignInTags.PASSWORD)
        assertTrue(isTrayUp)
        assertEquals(SignInItem.Password, trayField)
        tap(keyboardKeyTag(KeyId(1, 0)))
        tap(keyboardKeyTag(KeyId(3, 3)))
        assertEquals("the action key signs in once", 1, actions.accountsFake.signIns.size)
        assertEquals("qw", actions.accountsFake.signIns.single().first)
    }

    @Test
    fun aHardwareKeyboardTypesWithTheTrayDownAndEnterIsNextThenSignIn() {
        openTheForm()
        press(back)
        assertFalse("a fresh B put the tray away", isTrayUp)
        typeOnKeyboard("plumtester")
        assertFalse(isTrayUp)
        hardware(KeyEvent.KEYCODE_ENTER)
        assertFalse(isTrayUp)
        typeOnKeyboard("abc")
        assertEquals("Enter moved typing to the password", 3, passwordLength)
        hardware(KeyEvent.KEYCODE_ENTER)
        assertEquals(1, actions.accountsFake.signIns.size)
    }

    @Test
    fun emptyFieldsAndARefusedSignInKeepTheFormAndSubmitNothingMore() {
        actions.accountsFake.answer = { _, _ -> SignInResult.WrongCredentials }
        openTheForm()
        press(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_START)
        assertTrue(actions.accountsFake.signIns.isEmpty())
        assertEquals(context.getString(R.string.account_error_missing), textOf(SignInTags.MESSAGE))
        assertFalse(isTrayUp)
        press(confirm)
        assertEquals("back on the empty name", SignInItem.Username, trayField)
        typeOnKeyboard("plumtester")
        press(KeyEvent.KEYCODE_BUTTON_START)
        typeOnKeyboard("abc")
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertEquals(1, actions.accountsFake.signIns.size)
        assertEquals(context.getString(R.string.account_error_wrong), textOf(SignInTags.MESSAGE))
        assertEquals("plumtester", username)
        assertEquals(3, passwordLength)
        assertFalse("the answer never brings the tray up", isTrayUp)
        press(confirm)
        assertTrue(isTrayUp)
        assertEquals("on the password, ready to type it again", SignInItem.Password, trayField)
    }

    @Test
    fun whileCheckingNothingSignsInAgain() {
        actions.accountsFake.holdsAnswers = true
        openTheForm()
        typeOnKeyboard("plumtester")
        press(KeyEvent.KEYCODE_BUTTON_START)
        typeOnKeyboard("abc")
        press(KeyEvent.KEYCODE_BUTTON_START)
        assertTrue("Checking…", exists(SignInTags.CHECKING))
        assertFalse(isTrayUp)
        assertEquals(TextEntry.None, textEntry())
        press(KeyEvent.KEYCODE_BUTTON_START, confirm, confirm)
        hardware(KeyEvent.KEYCODE_ENTER)
        assertEquals(1, actions.accountsFake.signIns.size)
    }

    @Test
    fun typingIdleWritesNothing() {
        openTheForm()
        typeOnKeyboard("plum")
        assertNoAndroidKeyboard("on the username")
        assertNothingWritesWhileIdle("the username")
        press(KeyEvent.KEYCODE_BUTTON_START)
        typeOnKeyboard("abc")
        assertNothingWritesWhileIdle("the password")
    }

    @Test
    fun settingsLeavingWhileTypingGivesBBack() {
        openTheForm()
        assertEquals(TextEntry.Keyboard, textEntry())
        shown = false
        compose.waitForIdle()
        assertEquals(TextEntry.None, textEntry())
    }

    @Test
    fun androidsKeyboardHasNoTray() {
        openTheForm(androidKeyboard = true)
        assertFalse(isTrayUp)
        assertEquals(TextEntry.None, textEntry())
        assertTrue(compose.onAllNodes(hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    }
}
