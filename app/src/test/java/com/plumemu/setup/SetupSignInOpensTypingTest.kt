package com.plumemu.setup

import com.plumemu.account.SignInItem
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.TextEntry
import com.plumemu.keyboard.KeyboardChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Setup's "Use my account" opens the ScreenScraper form typing in the user name, Plumemu's tray up
 * (or Android's keyboard asked for); B puts only the tray away and the form stays; Use my account
 * again after going back to the question opens typing again. Through the real router.
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
    fun useMyAccountOpensTypingTheUsername() {
        showTheForm()
        assertTypingTheUsername("as the form opens")
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
    fun backToTheQuestionThenUseMyAccountOpensTypingAgain() {
        showTheForm()
        press(back, back)
        assertNull("the question", model.state.value.boxArt)
        press(confirm)
        assertTypingTheUsername("when the form opens again")
    }

    @Test
    fun androidsKeyboardIsAskedForAsTheFormOpens() {
        showTheForm(keyboard = KeyboardChoice.Android)
        assertNoTray("on Android's path")
        assertTrue("the user name field asks for the keyboard", form.inField)
        assertEquals(SignInItem.Username, form.focus)
        ime.settle()
        assertTrue("Android's keyboard is asked for", ime.keyboardShows)
    }
}
