package com.plumemu.setup

import com.plumemu.account.SignInItem
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.TextEntry
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setup's "Use my account" opens the ScreenScraper form on the user name with the keyboard down (Sign in and Skip in view);
 * A on the field brings Plumemu's tray up (or asks for Android's keyboard); B puts only the tray away and the form stays;
 * Use my account again after going back to the question opens the form with the keyboard down again. Through the real router.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SetupSignInOpensTypingTest : SetupKeyboardPathHarness() {
    private fun assertTypingTheUsername(where: String) {
        assertEquals("the cursor on the user name $where", SignInItem.Username, form.focus)
        assertTrue("the tray is up $where", isTrayUp)
        assertEquals(TextEntry.Keyboard, textEntry())
        assertTrue("START is Next: typing in the user name", hintText(Action.Menu).endsWith("Next"))
    }

    @Test
    fun useMyAccountOpensTheFormWithTheTrayDownAndAOnTheUsernameBringsItUp() {
        showTheForm(startTyping = false)
        assertEquals("the cursor on the user name", SignInItem.Username, form.focus)
        assertNoTray("as the form opens")
        assertFalse(form.isTyping)
        press(confirm)
        assertTypingTheUsername("after A on the user name")
        typeOnKeyboard("plum")
        assertEquals("plum", form.username)
    }

    @Test
    fun bPutsOnlyTheTrayAwayAndTheFormStays() {
        showTheForm()
        press(back)
        assertNoTray("after B")
        assertNotNull("the form stays", model.state.value.boxArt)
        assertEquals(SignInItem.Username, form.focus)
        assertEquals(TextEntry.Field, textEntry())
    }

    @Test
    fun backToTheChoicesThenTheFirstChoiceOpensTypingAgain() {
        showTheForm()
        press(back, back)
        assertNull("the choices", model.state.value.boxArt)
        press(confirm)
        assertNoTray("when the form opens again")
        press(confirm)
        assertTypingTheUsername("after A on the user name again")
    }

    @Test
    fun androidsKeyboardIsAskedForWhenAOpensTheUsername() {
        showTheForm(keyboard = KeyboardChoice.Android, startTyping = false)
        assertFalse("the form opens with the keyboard down", form.isTyping)
        press(confirm)
        assertNoTray("on Android's path")
        assertTrue("the user name field asks for the keyboard", form.inField)
        assertEquals(SignInItem.Username, form.focus)
        ime.settle()
        assertTrue("Android's keyboard is asked for", ime.keyboardShows)
    }
}
