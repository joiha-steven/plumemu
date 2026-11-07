package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.account.RaAccountStatus
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInKind
import com.plumemu.account.SignInResult
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Settings › Accounts › RetroAchievements: the row, the sign-in form (user name and Web API key),
 * each answer's line, the signed-in page and Sign out, and the focus order through the Accounts
 * section; gamepad and touch.
 */
internal class SettingsRaAccountTest : SettingsModelHarness() {
    @Before
    fun scraperIsInThisBuild() {
        model.content = model.content.copy(account = AccountStatus.SignedOut, chosenBoxArtSource = BoxArtSourceSetting.ScreenScraper)
    }

    private fun show(status: RaAccountStatus) {
        model.content = model.content.copy(raAccount = status)
    }

    private fun accounts() = SettingsSection.Accounts.rowsIn(model.content)

    private fun openRaRow() {
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.RetroAchievementsAccount)
    }

    /**
     * A on the row: the form opens typing on the user name (tray up); B puts the tray away and the
     * form stays with the cursor on the name.
     */
    private fun openFormAndPutTheTrayAway() {
        press(Action.Enter)
        val form = requireNotNull(model.signIn.form)
        assertEquals(SignInItem.Username, form.focus)
        assertFalse("the form opens with the keyboard down", form.isTyping || form.isTrayUp)
        assertSame("B puts the tray away and the form stays", form, model.signIn.form)
        assertFalse(form.isTyping)
        assertEquals(SignInItem.Username, form.focus)
    }

    /** The form is open: type [name] and [key], walk down to Sign in and press A. */
    private fun signInWith(
        name: String,
        key: String,
    ) {
        val form = requireNotNull(model.signIn.form)
        form.onUsernameChange(name)
        form.onPasswordChange(key)
        press(Action.Move(Direction.Down))
        press(Action.Move(Direction.Down))
        press(Action.Enter)
    }

    @Test
    fun accountsListsScreenScraperThenRetroAchievementsAndKeepsOnlyTheRegionInLibrary() {
        assertEquals(listOf(SettingsOption.BoxArtSource, SettingsOption.ScraperAccount, SettingsOption.RetroAchievementsAccount), accounts())
        assertTrue(SettingsOption.RetroAchievementsAccount !in SettingsSection.Library.rowsIn(model.content))
    }

    @Test
    fun theGamepadWalksTheAccountsSectionInOrder() {
        openSection(SettingsSection.Accounts)
        assertEquals(SettingsOption.BoxArtSource, model.focusedOption)
        move(Direction.Down)
        assertEquals(SettingsOption.ScraperAccount, model.focusedOption)
        move(Direction.Down)
        assertEquals(SettingsOption.RetroAchievementsAccount, model.focusedOption)
        move(Direction.Down)
        assertEquals("the last row stays", SettingsOption.RetroAchievementsAccount, model.focusedOption)
        move(Direction.Up)
        assertEquals(SettingsOption.ScraperAccount, model.focusedOption)
    }

    @Test
    fun theFormTakesUserNameThenKeyThenSignInThenCancelInOrder() {
        openRaRow()
        openFormAndPutTheTrayAway()
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        val form = requireNotNull(model.signIn.form)
        assertEquals(SignInKind.RetroAchievements, form.kind)
        press(Action.Move(Direction.Down))
        assertEquals(SignInItem.Password, form.focus)
        press(Action.Move(Direction.Down))
        assertEquals(SignInItem.SignIn, form.focus)
        press(Action.Move(Direction.Down))
        assertEquals("the narrow panel stacks Cancel under Sign in", SignInItem.Leave, form.focus)
    }

    @Test
    fun aSignInByGamepadSendsTheNameAndTheKeyAndClosesTheFormOnSuccess() {
        openRaRow()
        openFormAndPutTheTrayAway()
        signInWith("hung", "KeyKeyKey123")
        assertEquals(listOf("hung" to "KeyKeyKey123"), actions.raAccountsFake.signIns)
        assertTrue("never asks ScreenScraper", actions.accountsFake.signIns.isEmpty())
        assertNull("the form closes once signed in", model.signIn.form)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.RetroAchievementsAccount, model.focusedOption)
    }

    @Test
    fun eachFailureStaysOnTheFormWithItsOwnMessage() {
        val failures = listOf(SignInResult.RaWrongKey, SignInResult.RaUnknownUser, SignInResult.Offline, SignInResult.RaBusy, SignInResult.RaRateLimited, SignInResult.NotSaved)
        openRaRow()
        press(Action.Enter)
        val form = requireNotNull(model.signIn.form)
        form.onUsernameChange("hung")
        form.onPasswordChange("wrong")
        for (failure in failures) {
            actions.raAccountsFake.answer = { _, _ -> failure }
            // A tap on Sign in presses it again with the same fields.
            model.onTapPanelItem(2, actions.shown, wide)
            assertEquals(failure, form.message)
            assertEquals("the step stays open on $failure", PanelPage.ScraperSignIn, model.focus.page)
        }
        assertEquals(failures.size, actions.raAccountsFake.signIns.size)
    }

    @Test
    fun aWrongKeyReturnsToTheKeyAndAnUnknownNameToTheName() {
        actions.raAccountsFake.answer = { _, _ -> SignInResult.RaWrongKey }
        openRaRow()
        openFormAndPutTheTrayAway()
        signInWith("hung", "wrong")
        assertEquals(SignInItem.Password, requireNotNull(model.signIn.form).focus)
        actions.raAccountsFake.answer = { _, _ -> SignInResult.RaUnknownUser }
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertEquals(SignInItem.Username, requireNotNull(model.signIn.form).focus)
    }

    @Test
    fun emptyFieldsAskNothing() {
        openRaRow()
        openFormAndPutTheTrayAway()
        press(Action.Move(Direction.Down))
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertEquals(SignInResult.Missing, requireNotNull(model.signIn.form).message)
        assertTrue(actions.raAccountsFake.signIns.isEmpty())
    }

    @Test
    fun touchOpensTheFormAndPressesSignIn() {
        openSection(SettingsSection.Accounts)
        val row = accounts().indexOf(SettingsOption.RetroAchievementsAccount)
        // ScreenScraper has the focus on entering: a first tap focuses this row, a second opens it.
        model.onTapOption(row, actions.shown, wide)
        model.onTapOption(row, actions.shown, wide)
        val form = requireNotNull(model.signIn.form)
        assertEquals(SignInKind.RetroAchievements, form.kind)
        model.onTapPanelItem(1, actions.shown, wide)
        assertEquals(SignInItem.Password, form.focus)
        form.onUsernameChange("hung")
        form.onPasswordChange("KeyKeyKey123")
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals(listOf("hung" to "KeyKeyKey123"), actions.raAccountsFake.signIns)
    }

    @Test
    fun backAndCancelCloseTheFormWithoutAnAccount() {
        openRaRow()
        openFormAndPutTheTrayAway()
        press(Action.Back)
        assertNull("the second B closes the form", model.signIn.form)
        assertEquals(SettingsColumn.Options, model.focus.column)
        openFormAndPutTheTrayAway()
        press(Action.Move(Direction.Down))
        press(Action.Move(Direction.Down))
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertNull(model.signIn.form)
        assertTrue(actions.raAccountsFake.signIns.isEmpty())
    }

    @Test
    fun signedInOpensTheSignedInPageAndSignOutAsksFirstThenForgetsTheAccount() {
        show(RaAccountStatus.SignedIn("hung", 1234))
        openRaRow()
        press(Action.Enter)
        assertEquals(PanelPage.RaAccount, model.focus.page)
        assertEquals(listOf(RaPageItem.SignOut), raPageItems(model.content))
        press(Action.Enter)
        assertEquals("Sign out asks first", PanelPage.RaSignOutConfirm, model.focus.page)
        assertEquals("Cancel has the focus", SignOutConfirmItem.Cancel.ordinal, model.focus.panelIndex)
        assertEquals("nobody is signed out yet", 0, actions.raAccountsFake.signOuts)
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(1, actions.raAccountsFake.signOuts)
        assertEquals("the panel closes once the account is gone", SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun cancelAndBackOnTheSignOutConfirmKeepTheAccount() {
        show(RaAccountStatus.SignedIn("hung", 1234))
        openRaRow()
        press(Action.Enter)
        press(Action.Enter)
        press(Action.Enter) // Cancel
        assertEquals(PanelPage.RaAccount, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(PanelPage.RaAccount, model.focus.page)
        assertEquals("nobody was signed out", 0, actions.raAccountsFake.signOuts)
    }

    @Test
    fun signOutByTouchAsksFirstToo() {
        show(RaAccountStatus.SignedIn("hung", 1234))
        openRaRow()
        press(Action.Enter)
        // Sign out is the only item and has the focus: one tap presses it.
        model.onTapPanelItem(0, actions.shown, wide)
        assertEquals(PanelPage.RaSignOutConfirm, model.focus.page)
        assertEquals(0, actions.raAccountsFake.signOuts)
        model.onTapPanelItem(SignOutConfirmItem.SignOut.ordinal, actions.shown, wide)
        model.onTapPanelItem(SignOutConfirmItem.SignOut.ordinal, actions.shown, wide)
        assertEquals(1, actions.raAccountsFake.signOuts)
    }

    @Test
    fun aFailedSignOutIsSaidUnderTheRow() {
        actions.raAccountsFake.signOutWorks = false
        show(RaAccountStatus.SignedIn("hung", 1234))
        openRaRow()
        press(Action.Enter)
        press(Action.Enter)
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(SettingsOption.RetroAchievementsAccount to SettingsNotice.SignOutFailed, model.work.notice)
    }

    @Test
    fun aRefusedKeyOffersSignInAgainWithTheNameFilledIn() {
        show(RaAccountStatus.SignedIn("hung", 1234, isKeyRefused = true))
        openRaRow()
        press(Action.Enter)
        assertEquals(listOf(RaPageItem.SignInAgain, RaPageItem.SignOut), raPageItems(model.content))
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        val form = requireNotNull(model.signIn.form)
        assertEquals(SignInKind.RetroAchievements, form.kind)
        assertEquals("hung", form.username)
    }
}
