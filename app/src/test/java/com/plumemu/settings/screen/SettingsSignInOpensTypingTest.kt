package com.plumemu.settings.screen

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
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
 * Opening a sign-in panel in Settings puts the cursor on its first empty field (the user name, or the key / password when
 * "Sign in again" filled the name) with the keyboard DOWN, so Cancel and RetroAchievements' key page button are in view;
 * A on the field brings Plumemu's tray up, and moving off the field puts it away again. On the real screen with a real
 * router. Names are made up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w768dp-h432dp-land-xhdpi")
internal class SettingsSignInOpensTypingTest : SettingsKeyboardPathHarness() {
    private var ra: RaAccountStatus = RaAccountStatus.SignedOut

    override val content: SettingsContent get() = SettingsContent(account = AccountStatus.SignedOut, chosenBoxArtSource = BoxArtSourceSetting.ScreenScraper, raAccount = ra)

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

    private fun assertOpenWithTheTrayDown() {
        assertTrue("the sign-in panel is open", isFormOpen)
        assertFalse("the tray is down as the panel opens", isTrayUp)
        assertEquals(TextEntry.Field, textEntry())
    }

    private fun assertShown(vararg items: SignInItem) {
        val window = compose.onRoot().getBoundsInRoot()
        for (item in items) {
            val box = compose.onNodeWithTag(SignInTags.item(item), useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$item is inside the window: $box in $window", box.top >= window.top && box.bottom <= window.bottom)
        }
    }

    @Test
    fun theRetroAchievementsPanelOpensWithTheTrayDownAndCancelAndTheKeyPageInView() {
        openRow(SettingsOption.RetroAchievementsAccount)
        assertOpenWithTheTrayDown()
        assertShown(SignInItem.SignIn, SignInItem.Leave, SignInItem.OpenKeyPage)
        // A on the user name brings the tray up and it types there.
        send(Action.Enter)
        assertTrue("A on the field brings the tray up", isTrayUp)
        assertEquals(SignInItem.Username, trayField)
        typeOnKeyboard("plum")
        assertEquals("the keys type in the user name", "plum", textOf(SignInTags.typedText(SignInItem.Username)))
    }

    @Test
    fun theScreenScraperPanelOpensWithTheTrayDownAndCancelInView() {
        openRow(SettingsOption.ScraperAccount)
        assertOpenWithTheTrayDown()
        assertShown(SignInItem.SignIn, SignInItem.Leave)
    }

    @Test
    fun signInAgainWithTheNameFilledOpensOnTheKeyWithTheTrayDown() {
        ra = RaAccountStatus.SignedIn("plumtester", 1234, isKeyRefused = true)
        openRow(SettingsOption.RetroAchievementsAccount)
        // The account panel: Sign in again is its first item.
        send(Action.Enter)
        assertOpenWithTheTrayDown()
        assertEquals("plumtester", textOf(SignInTags.typedText(SignInItem.Username)))
        assertShown(SignInItem.Leave, SignInItem.OpenKeyPage)
        send(Action.Enter)
        assertTrue(isTrayUp)
        assertEquals(SignInItem.Password, trayField)
    }

    @Test
    fun withTheTrayUpBPutsItAwayAndTheKeyPageButtonIsReachableAndInView() {
        openRow(SettingsOption.RetroAchievementsAccount)
        send(Action.Enter)
        assertTrue(isTrayUp)
        press(back)
        assertFalse("B puts the tray away", isTrayUp)
        assertTrue("the panel stays", isFormOpen)
        send(Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Down), Action.Move(Direction.Down))
        assertShown(SignInItem.OpenKeyPage)
    }

    @Test
    fun bOnTheOpenFormWithTheTrayDownClosesIt() {
        openRow(SettingsOption.RetroAchievementsAccount)
        press(back)
        assertFalse("B closes the panel", isFormOpen)
    }

    @Test
    fun androidsKeyboardStaysDownAsThePanelOpens() {
        show(androidKeyboard = true)
        repeat(SettingsSection.Accounts.shownIndex) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        repeat(SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.RetroAchievementsAccount)) { send(Action.Move(Direction.Down)) }
        send(Action.Enter)
        assertTrue(isFormOpen)
        assertFalse("no tray on Android's path", isTrayUp)
        ime.settle()
        assertFalse("Android's keyboard is not up until A", ime.keyboardShows)
    }
}
