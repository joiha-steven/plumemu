package com.plumemu.account

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.coreui.components.keyboard.ShiftMode
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sign-in form typed with Plumemu's keyboard, without a screen: the game
 * panel's button map on both fields, "Next" to the password with the tray kept up, "Sign in" once,
 * what the router is told, and the wrong paths (empty fields, a failed answer, keys while checking).
 * Passwords here are made-up test values and never appear in a test name or a failure message.
 */
class SignInOwnKeyboardTest {
    private val form = SignInForm(keyboardKind = KeyboardChoice.Plumemu)
    private val submits = mutableListOf<SignInStep.Submit>()

    private fun act(action: Action): SignInStep = form.onAction(action).also { if (it is SignInStep.Submit) submits += it }

    private fun key(event: KeyboardEvent): SignInStep = form.onKeyboardKey(event).also { if (it is SignInStep.Submit) submits += it }

    private fun typed(text: String) = act(Action.Typed(TypedKey.Text(text)))

    private val a = Action.Enter
    private val start = Action.Menu

    @Test
    fun opensOnTheUsernameWithTheTrayDownAndOnlyHardwareKeysTaken() {
        assertEquals(SignInItem.Username, form.focus)
        assertFalse("the tray never comes up by itself", form.isTrayUp)
        assertEquals("a hardware keyboard may type on the field", TextEntry.Field, form.textEntry)
        act(Action.Move(Direction.Down))
        assertFalse("moving onto the password brings nothing up", form.isTrayUp)
        act(Action.Move(Direction.Down))
        assertEquals("on Sign in nothing types", TextEntry.None, form.textEntry)
    }

    @Test
    fun aOnTheUsernameBringsTheTrayUpAndAPressesTheFocusedKey() {
        act(a)
        assertTrue(form.isTrayUp)
        assertEquals("B erases while the tray is up", TextEntry.Keyboard, form.textEntry)
        assertEquals(KeyboardPage.Letters, form.usernameKeys.keys.page)
        act(a) // q, the first key
        act(Action.Move(Direction.Right))
        act(a) // w
        assertEquals("qw", form.username)
        assertEquals("the D-pad moves on the keys, not the form", SignInItem.Username, form.focus)
    }

    @Test
    fun selectShiftsOneLetterAndNoCapitalComesByItself() {
        act(a)
        act(Action.ToggleView)
        assertEquals(ShiftMode.Once, form.usernameKeys.keys.shift)
        act(a)
        act(a)
        assertEquals("one capital, then lower case", "Qq", form.username)
        act(Action.Erase())
        act(Action.Erase())
        assertEquals("", form.username)
        assertEquals("an empty name gets no capital by itself (names are typed as they are)", ShiftMode.Off, form.usernameKeys.keys.shift)
        act(a)
        assertEquals("q", form.username)
    }

    @Test
    fun yTurnsToTheSymbolsAndXTypesASpace() {
        act(a)
        act(Action.ToggleFavorite)
        assertEquals(KeyboardPage.Symbols, form.usernameKeys.keys.page)
        act(Action.Search)
        assertEquals(" ", form.username)
    }

    @Test
    fun bErasesThenAFreshBOnEmptyPutsTheTrayAwayAndAHeldOneDoesNot() {
        act(a)
        typed("ab")
        act(Action.Erase())
        act(Action.Erase(isRepeat = true))
        assertEquals("", form.username)
        act(Action.Erase(isRepeat = true))
        assertTrue("a held B never puts the tray away", form.isTrayUp)
        act(Action.Erase())
        assertFalse("a fresh B on an empty field does", form.isTrayUp)
        assertEquals("the cursor stays on the field", SignInItem.Username, form.focus)
        assertEquals(TextEntry.Field, form.textEntry)
        assertEquals("the next B is the host's (it leaves)", SignInStep.NotHandled, act(Action.Back))
    }

    @Test
    fun theSystemsBackAndTheHideKeyPutTheTrayAwayAndAAgainBringsItBack() {
        act(a)
        assertEquals(SignInStep.Stay(), act(Action.Back))
        assertFalse(form.isTrayUp)
        act(a)
        assertTrue(form.isTrayUp)
        key(KeyboardEvent.Hide)
        assertFalse(form.isTrayUp)
        assertEquals(SignInItem.Username, form.focus)
    }

    @Test
    fun nextLandsOnThePasswordOnceWithTheTrayStillUp() {
        act(a)
        typed("plumtester")
        act(start)
        assertEquals(SignInItem.Password, form.focus)
        assertTrue("the tray stays up", form.isTrayUp)
        assertEquals("the password's own keys", form.passwordKeys, form.typing?.fieldKeys)
        assertEquals(com.plumemu.R.string.account_sign_in, signInActionWord(form))
        typed("x")
        assertEquals("typing goes to the password now", "plumtester", form.username)
        assertEquals(1, form.password.length)
        assertTrue("nothing submitted", submits.isEmpty())
    }

    @Test
    fun theTraysActionKeyIsNextThenSignInAndSignsInExactlyOnce() {
        act(a)
        typed("plumtester")
        key(KeyboardEvent.Action)
        assertEquals(SignInItem.Password, form.focus)
        typed("test-only")
        key(KeyboardEvent.Action)
        assertEquals(1, submits.size)
        assertEquals("plumtester", submits.single().username)
        assertTrue(form.isChecking)
        assertFalse("the tray goes down while the account is checked", form.isTrayUp)
        assertEquals(TextEntry.None, form.textEntry)
        // Signing in again while checking is ignored, whatever presses it.
        key(KeyboardEvent.Action)
        act(start)
        act(a)
        act(Action.Typed(TypedKey.Enter))
        assertEquals(1, submits.size)
    }

    @Test
    fun startOnThePasswordSignsInOnce() {
        form.onUsernameChange("plumtester")
        act(Action.Move(Direction.Down))
        act(a)
        typed("test-only")
        act(start)
        assertEquals(1, submits.size)
        assertEquals(SignInItem.SignIn, form.focus)
    }

    @Test
    fun aHardwareEnterIsNextThenSignInAndKeepsTheTrayDown() {
        typed("plumtester")
        assertFalse(form.isTrayUp)
        act(Action.Typed(TypedKey.Enter))
        assertEquals(SignInItem.Password, form.focus)
        assertFalse("a hardware keyboard needs no tray", form.isTrayUp)
        typed("test-only")
        act(Action.Typed(TypedKey.Enter))
        assertEquals(1, submits.size)
    }

    @Test
    fun aCaretPlacedByATapIsWhereTypingGoesAndL1R1MoveIt() {
        form.onUsernameChange("plumtster")
        form.placeCaret(SignInItem.Username, 5)
        act(Action.TapItem(Column.Details, 0))
        assertTrue("a tap on the field brings the tray up", form.isTrayUp)
        typed("e")
        assertEquals("plumtester", form.username)
        act(Action.JumpLetterPrevious)
        typed(">")
        act(Action.NextSystem)
        act(Action.PreviousSystem)
        act(Action.JumpLetterNext)
        typed("<")
        assertEquals(">plumtester<", form.username)
    }

    @Test
    fun aTapOnThePasswordWhileTypingTheNameKeepsTheTrayUp() {
        act(a)
        act(Action.TapItem(Column.Details, form.items.indexOf(SignInItem.Password)))
        assertEquals(SignInItem.Password, form.focus)
        assertTrue(form.isTrayUp)
        assertEquals("taps on keys type too", SignInStep.Stay(), key(KeyboardEvent.Type('k')))
        assertEquals(1, form.password.length)
    }

    @Test
    fun anEmptyUsernameOrPasswordSaysSoAndSubmitsNothing() {
        act(a)
        act(start) // Next, the name empty
        act(start) // Sign in
        assertTrue(submits.isEmpty())
        assertEquals(SignInResult.Missing, form.message)
        assertEquals("back to the empty name", SignInItem.Username, form.focus)
        assertFalse(form.isTrayUp)
        act(a)
        typed("plumtester")
        act(start)
        act(start)
        assertTrue(submits.isEmpty())
        assertEquals(SignInResult.Missing, form.message)
        assertEquals("to the empty password", SignInItem.Password, form.focus)
    }

    @Test
    fun aFailedSignInKeepsTheFieldsAndAOnAFieldBringsTheTrayBack() {
        act(a)
        typed("plumtester")
        act(start)
        typed("test-only")
        act(start)
        form.showResult(SignInResult.WrongCredentials)
        assertEquals("plumtester", form.username)
        assertEquals("the password stays as typed", 9, form.password.length)
        assertEquals(SignInItem.Password, form.focus)
        assertFalse("the answer never brings the tray up", form.isTrayUp)
        act(a)
        assertTrue(form.isTrayUp)
        act(start)
        assertEquals("a second try submits again, once", 2, submits.size)
    }

    @Test
    fun aSignInThatWorksLeavesNothingTyping() {
        act(a)
        typed("plumtester")
        act(start)
        typed("test-only")
        act(start)
        form.showResult(SignInResult.SignedIn("plumtester"))
        assertEquals(TextEntry.None, form.textEntry)
        assertFalse(form.isTrayUp)
        assertEquals("", form.password)
        assertEquals(SignInStep.Done, act(a))
    }

    @Test
    fun androidsKeyboardHasNoTypingStateAndNoTypingKeys() {
        val android = SignInForm()
        assertNull(android.typing)
        android.onAction(a)
        assertTrue(android.inField)
        assertFalse(android.isTrayUp)
        assertEquals(TextEntry.None, android.textEntry)
        assertEquals("Back is the host's on Android's path", SignInStep.NotHandled, android.onAction(Action.Back))
    }
}
