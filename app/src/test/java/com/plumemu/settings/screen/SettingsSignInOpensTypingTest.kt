package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInTags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Opening a sign-in panel in Settings puts the cursor on its first empty field with Plumemu's tray
 * up: the user name, or the key / password when "Sign in again" filled the name. B puts only the
 * tray away and the panel stays. On the real screen with a real router. Names are made up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsSignInOpensTypingTest : SettingsKeyboardPathHarness() {
    private var ra: RaAccountStatus = RaAccountStatus.SignedOut

    override val content: SettingsContent get() = SettingsContent(account = AccountStatus.SignedOut, raAccount = ra)

    private val isFormOpen get() = exists(SignInTags.FORM)

    /** Which field the tray types in, told by START's word: "Next" on the user name, "Sign in" on the key. */
    private val trayField: SignInItem
        get() = if (hintText(Action.Menu).endsWith("Next")) SignInItem.Username else SignInItem.Password

    /** Settings › Accounts › [option] and A. */
    private fun openRow(option: SettingsOption) {
        show()
        repeat(SettingsSection.Accounts.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Accounts.rowsIn(content).indexOf(option)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
    }

    private fun assertTypingIn(item: SignInItem) {
        assertTrue("the sign-in panel is open", isFormOpen)
        assertTrue("the tray is up as the panel opens", isTrayUp)
        assertEquals(TextEntry.Keyboard, textEntry())
        assertEquals(item, trayField)
    }

    @Test
    fun theRetroAchievementsPanelOpensTypingTheUserName() {
        openRow(SettingsOption.RetroAchievementsAccount)
        assertTypingIn(SignInItem.Username)
        typeOnKeyboard("plum")
        assertEquals("the keys type in the user name", "plum", textOf(SignInTags.typedText(SignInItem.Username)))
    }

    @Test
    fun theScreenScraperPanelOpensTypingTheUserName() {
        openRow(SettingsOption.ScraperAccount)
        assertTypingIn(SignInItem.Username)
    }

    @Test
    fun signInAgainWithTheNameFilledOpensTypingTheKey() {
        ra = RaAccountStatus.SignedIn("plumtester", 1234, isKeyRefused = true)
        openRow(SettingsOption.RetroAchievementsAccount)
        // The account panel: Sign in again is its first item.
        send(Action.Enter)
        assertTypingIn(SignInItem.Password)
        assertEquals("plumtester", textOf(SignInTags.typedText(SignInItem.Username)))
    }

    @Test
    fun bPutsOnlyTheTrayAwayAndMovingFieldsKeepsItUp() {
        openRow(SettingsOption.RetroAchievementsAccount)
        // START: Next, the tray stays up on the key.
        send(Action.Menu)
        assertTypingIn(SignInItem.Password)
        press(back)
        assertFalse("B on an empty field puts the tray away", isTrayUp)
        assertTrue("the panel stays open", isFormOpen)
        assertEquals(TextEntry.Field, textEntry())
    }

    @Test
    fun androidsKeyboardIsAskedForAsThePanelOpens() {
        show(androidKeyboard = true)
        repeat(SettingsSection.Accounts.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.RetroAchievementsAccount)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        assertTrue(isFormOpen)
        assertFalse("no tray on Android's path", isTrayUp)
        ime.settle()
        assertTrue("Android's keyboard is asked for", ime.keyboardShows)
    }
}
