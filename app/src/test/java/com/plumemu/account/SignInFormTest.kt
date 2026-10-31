package com.plumemu.account

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sign-in form's rules for both inputs, without a screen (setup and Settings share them). */
class SignInFormTest {
    private fun move(direction: Direction) = Action.Move(direction)

    private fun filled(): SignInForm =
        SignInForm().apply {
            onUsernameChange("plumtester")
            onPasswordChange("hunter2")
        }

    @Test
    fun opensOnTheUsernameWithTheKeyboardDown() {
        val form = SignInForm()
        assertEquals(SignInItem.Username, form.focus)
        assertFalse("the keyboard never comes up by itself", form.inField)
        assertEquals(listOf(SignInItem.Username, SignInItem.Password, SignInItem.SignIn, SignInItem.Leave), form.items)
    }

    @Test
    fun theGamepadWalksFieldsAndButtonsAndNeverGetsStuckInAField() {
        val form = SignInForm()
        assertEquals(SignInStep.Stay(moved = true), form.onAction(move(Direction.Down)))
        assertEquals(SignInItem.Password, form.focus)
        form.onAction(move(Direction.Down))
        assertEquals(SignInItem.SignIn, form.focus)
        assertFalse("the keyboard goes once the cursor leaves the fields", form.inField)
        form.onAction(move(Direction.Right))
        assertEquals(SignInItem.Leave, form.focus)
        form.onAction(move(Direction.Left))
        assertEquals(SignInItem.SignIn, form.focus)
        form.onAction(move(Direction.Up))
        assertEquals(SignInItem.Password, form.focus)
        form.onAction(move(Direction.Up))
        assertEquals(SignInItem.Username, form.focus)
        assertEquals("nothing above the username", SignInStep.Stay(moved = false), form.onAction(move(Direction.Up)))
    }

    @Test
    fun aOnAFieldOpensItsKeyboardThereAndAOnSignInSignsIn() {
        val form = filled()
        assertEquals("A on the username: its keyboard, the cursor stays", SignInStep.Stay(), form.onAction(Action.Enter))
        assertEquals(SignInItem.Username, form.focus)
        assertTrue(form.inField)
        form.onAction(move(Direction.Down))
        assertFalse("moving onto the password opens nothing", form.inField)
        assertEquals("A on the password opens its keyboard, it does not sign in", SignInStep.Stay(), form.onAction(Action.Enter))
        assertTrue(form.inField)
        form.onAction(move(Direction.Down))
        assertFalse("the keyboard goes once the cursor leaves the field", form.inField)
        assertEquals(SignInStep.Submit("plumtester", "hunter2"), form.onAction(Action.Enter))
        assertTrue(form.isChecking)
        assertFalse("no keyboard while checking", form.inField)
    }

    @Test
    fun eachAAsksForTheKeyboardAgainSoItComesBackAfterTheSystemClosedIt() {
        val form = SignInForm()
        form.onAction(Action.Enter)
        val first = form.keyboardAsks
        form.onAction(Action.Enter)
        assertEquals("a second A asks again", first + 1, form.keyboardAsks)
        assertTrue(form.inField)
    }

    @Test
    fun theKeyboardsNextGoesToThePasswordAndItsDoneToSignIn() {
        val form = filled()
        form.onAction(Action.Enter)
        form.onKeyboardNext()
        assertEquals(SignInItem.Password, form.focus)
        assertTrue("the password's keyboard is up", form.inField)
        form.onKeyboardDone()
        assertEquals("Done leaves the cursor on Sign in for the host's Enter", SignInItem.SignIn, form.focus)
        assertFalse(form.inField)
        assertEquals(SignInStep.Submit("plumtester", "hunter2"), form.onAction(Action.Enter))
    }

    @Test
    fun theKeyboardsNextAndDoneDoNothingOffTheirField() {
        val form = filled()
        form.onKeyboardDone()
        assertEquals("Done belongs to the password", SignInItem.Username, form.focus)
        form.onAction(move(Direction.Down))
        form.onKeyboardNext()
        assertEquals("Next belongs to the username", SignInItem.Password, form.focus)
        assertFalse(form.inField)
    }

    @Test
    fun aWrongPasswordPutsTheCursorOnThePasswordWithTheKeyboardDown() {
        val form = filled()
        form.onAction(move(Direction.Down))
        form.onAction(Action.Enter)
        form.onKeyboardDone()
        form.onAction(Action.Enter)
        form.showResult(SignInResult.WrongCredentials)
        assertEquals(SignInItem.Password, form.focus)
        assertFalse("the answer does not bring the keyboard up", form.inField)
        form.onAction(Action.Enter)
        assertTrue("A brings it up", form.inField)
    }

    @Test
    fun stopTypingPutsTheKeyboardAwayAndKeepsTheCursor() {
        val form = SignInForm()
        form.onAction(Action.Enter)
        form.stopTyping()
        assertFalse(form.inField)
        assertEquals(SignInItem.Username, form.focus)
    }

    @Test
    fun anEmptyFieldIsSaidAndNothingIsAsked() {
        val form = SignInForm()
        form.onAction(move(Direction.Down))
        form.onAction(move(Direction.Down))
        assertEquals(SignInStep.Stay(moved = true), form.onAction(Action.Enter))
        assertEquals(SignInResult.Missing, form.message)
        assertEquals("the cursor goes to what is missing", SignInItem.Username, form.focus)
        assertFalse(form.isChecking)

        form.onUsernameChange("  plumtester ")
        assertNull("typing clears the message", form.message)
        form.tap(form.items.indexOf(SignInItem.SignIn))
        assertEquals(SignInItem.Password, form.focus)
    }

    @Test
    fun theNameIsTrimmedButThePasswordIsSentAsTyped() {
        val form = SignInForm()
        form.onUsernameChange("  plumtester ")
        form.onPasswordChange(" pass word ")
        assertEquals(SignInStep.Submit("plumtester", " pass word "), form.tap(form.items.indexOf(SignInItem.SignIn)))
    }

    @Test
    fun whileCheckingOnlyBackIsTheHostsAndTypingWaits() {
        val form = filled()
        form.tap(form.items.indexOf(SignInItem.SignIn))
        assertEquals(SignInStep.Stay(), form.onAction(Action.Enter))
        assertEquals(SignInStep.Stay(), form.onAction(move(Direction.Up)))
        assertEquals(SignInStep.Stay(), form.tap(form.items.indexOf(SignInItem.Leave)))
        form.onPasswordChange("changed")
        assertEquals("hunter2", form.password)
        assertEquals(SignInStep.NotHandled, form.onAction(Action.Back))
    }

    @Test
    fun everyFailureIsShownAndTheFormStays() {
        val failures =
            listOf(
                SignInResult.WrongCredentials,
                SignInResult.Offline,
                SignInResult.ServerClosed,
                SignInResult.DailyLimit(1_000L),
                SignInResult.ServerError(500),
                SignInResult.AppRefused,
                SignInResult.NotSaved,
                SignInResult.NotAvailable,
            )
        for (failure in failures) {
            val form = filled()
            form.onAction(Action.TapItem(Column.Details, 2))
            form.showResult(failure)
            assertEquals("$failure", failure, form.message)
            assertFalse("$failure", form.isChecking)
            assertNull("$failure", form.signedInAs)
            val expected = if (failure == SignInResult.WrongCredentials) SignInItem.Password else SignInItem.SignIn
            assertEquals("$failure: where the cursor waits", expected, form.focus)
            assertEquals("$failure: the fields keep what was typed", "plumtester", form.username)
        }
    }

    @Test
    fun aSignInThatWorksShowsTheNameAndOnlyContinue() {
        val form = filled()
        form.tap(form.items.indexOf(SignInItem.SignIn))
        form.showResult(SignInResult.SignedIn("plumtester"))
        assertEquals("plumtester", form.signedInAs)
        assertEquals("the password is not kept in the form", "", form.password)
        assertEquals(listOf(SignInItem.Continue), form.items)
        assertEquals(SignInItem.Continue, form.focus)
        assertEquals(SignInStep.Done, form.onAction(Action.Enter))
        assertEquals("touch", SignInStep.Done, form.tap(0))
    }

    @Test
    fun touchTypesInATappedFieldAndPressesATappedButton() {
        val form = filled()
        assertEquals(SignInStep.Stay(moved = true), form.tap(1))
        assertEquals(SignInItem.Password, form.focus)
        assertTrue(form.inField)
        assertEquals(SignInStep.Leave, form.tap(3))
        assertEquals(SignInStep.Stay(), form.tap(9))
    }

    @Test
    fun aFormOpenedForAStoredAccountIsSignedIn() {
        val form = SignInForm(signedInAs = "plumtester")
        assertEquals(SignInItem.Continue, form.focus)
        assertFalse(form.inField)
    }

    @Test
    fun theSubmitStepNeverPrintsThePassword() {
        val text = SignInStep.Submit("plumtester", "hunter2").toString()
        assertFalse(text, "hunter2" in text || "plumtester" in text)
    }
}
