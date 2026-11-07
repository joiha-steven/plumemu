package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library's ScreenScraper rows: which rows show for each account state,
 * the status line, signing in with the shared form, signing out, the box art region; gamepad and
 * touch.
 */
internal class SettingsAccountTest : SettingsModelHarness() {
    private fun show(account: AccountStatus) {
        // Signed out, ScreenScraper as the source: its row is listed, and signs in.
        model.content = model.content.copy(account = account, chosenBoxArtSource = BoxArtSourceSetting.ScreenScraper.takeIf { account == AccountStatus.SignedOut })
    }

    private fun library() = SettingsSection.Library.rowsIn(model.content)

    /** Accounts' rows: ScreenScraper, then RetroAchievements. */
    private fun accounts() = SettingsSection.Accounts.rowsIn(model.content)

    private fun openAccountRow() {
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.ScraperAccount)
    }

    /**
     * A on the account row: the form opens typing on its first empty field (tray up); B puts the
     * tray away and the form stays, the cursor where it was.
     */
    private fun openFormAndPutTheTrayAway(first: SignInItem = SignInItem.Username): SignInForm {
        press(Action.Enter)
        val form = requireNotNull(model.signIn.form)
        assertEquals("the cursor opens on the first empty field", first, form.focus)
        assertFalse("the form opens with the keyboard down", form.isTyping || form.isTrayUp)
        assertSame("B puts the tray away and the form stays", form, model.signIn.form)
        assertFalse(form.isTyping)
        assertEquals(first, form.focus)
        return form
    }

    @Test
    fun noDeveloperCredentialsListsOnlyTheNotAvailableLine() {
        show(AccountStatus.NotInThisBuild)
        val rows = accounts()
        assertTrue(SettingsOption.ScraperStatus in rows)
        assertFalse("never asks for an account", SettingsOption.ScraperAccount in rows)
        assertFalse(SettingsOption.BoxArtRegion in library())
        assertEquals(BoxArtLine.NotInThisBuild, boxArtLineOf(model.content.account, model.content.boxArt))
    }

    @Test
    fun signedOutListsSignInInAccountsAndNoRegionRowWithNoStatusLine() {
        show(AccountStatus.SignedOut)
        assertEquals(listOf(SettingsOption.BoxArtSource, SettingsOption.ScraperAccount, SettingsOption.RetroAchievementsAccount), accounts())
        assertEquals("the region is no row: it is in the ScreenScraper panel", emptyList<SettingsOption>(), library().filter { it in ACCOUNT_ROWS })
        assertNull(boxArtLineOf(model.content.account, model.content.boxArt))
    }

    @Test
    fun signedInListsNoStatusRowWhateverTheStateTheAccountRowSaysIt() {
        // The daily limit and the wrong password are no rows of their own.
        for (account in listOf(AccountStatus.SignedIn("plumtester"), AccountStatus.SignedIn("plumtester", isRefused = true), AccountStatus.SignedIn("plumtester", dailyLimitUntilMillis = 5_000L))) {
            show(account)
            assertEquals(listOf(SettingsOption.BoxArtSource, SettingsOption.ScraperAccount, SettingsOption.RetroAchievementsAccount), accounts())
            assertEquals(emptyList<SettingsOption>(), library().filter { it in ACCOUNT_ROWS })
        }
        show(AccountStatus.SignedIn("plumtester", isRefused = true))
        assertEquals(BoxArtLine.WrongPassword, boxArtLineOf(model.content.account, model.content.boxArt))
        show(AccountStatus.SignedIn("plumtester", dailyLimitUntilMillis = 5_000L))
        assertEquals("before the run's state arrives, the account's pause", BoxArtLine.DailyLimit(5_000L), boxArtLineOf(model.content.account, model.content.boxArt))
        show(AccountStatus.SignedIn("plumtester", isRefused = true, dailyLimitUntilMillis = 5_000L))
        assertEquals("a refusal matters more than a pause", BoxArtLine.WrongPassword, boxArtLineOf(model.content.account, model.content.boxArt))
    }

    @Test
    fun theAccountsSectionComesBeforeAdvanced() {
        show(AccountStatus.SignedOut)
        assertEquals(SettingsSection.Advanced.ordinal - 1, SettingsSection.Accounts.ordinal)
        assertFalse("System order is in the Systems panel", SettingsOption.SystemOrder in library())
    }

    @Test
    fun aSignInByGamepadOpensTheFormChecksAndClosesOnSuccess() {
        show(AccountStatus.SignedOut)
        openAccountRow()
        val form = openFormAndPutTheTrayAway()
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        form.onUsernameChange("plumtester")
        form.onPasswordChange("hunter2")
        // A on a field opens its keyboard; the cursor walks down to Sign in.
        press(Action.Move(Direction.Down))
        assertEquals(SignInItem.Password, form.focus)
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertEquals(listOf("plumtester" to "hunter2"), actions.accountsFake.signIns)
        assertNull("the form closes once signed in", model.signIn.form)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.ScraperAccount, model.focusedOption)
    }

    @Test
    fun aFailedSignInStaysOnTheFormWithItsMessage() {
        actions.accountsFake.answer = { _, _ -> SignInResult.WrongCredentials }
        show(AccountStatus.SignedOut)
        openAccountRow()
        val form = openFormAndPutTheTrayAway()
        form.onUsernameChange("plumtester")
        form.onPasswordChange("wrong")
        press(Action.Move(Direction.Down))
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertEquals(SignInResult.WrongCredentials, form.message)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
    }

    @Test
    fun backAndCancelCloseTheFormWithoutAnAccount() {
        show(AccountStatus.SignedOut)
        openAccountRow()
        openFormAndPutTheTrayAway()
        press(Action.Back)
        assertNull("the second B closes the form", model.signIn.form)
        assertEquals(SettingsColumn.Options, model.focus.column)

        val stacked = openFormAndPutTheTrayAway()
        assertTrue("Sign in over Cancel in the narrow panel", stacked.stackedButtons)
        stacked.onAction(Action.Move(Direction.Down))
        stacked.onAction(Action.Move(Direction.Down))
        assertEquals(SignInItem.SignIn, stacked.focus)
        stacked.onAction(Action.Move(Direction.Down))
        assertEquals("Down reaches Cancel under Sign in", SignInItem.Leave, stacked.focus)
        stacked.onAction(Action.Move(Direction.Up))
        assertEquals(SignInItem.SignIn, stacked.focus)
        // Touch: Cancel is the form's fourth item.
        model.onTapPanelItem(3, actions.shown, wide)
        assertNull(model.signIn.form)
        assertTrue(actions.accountsFake.signIns.isEmpty())
    }

    @Test
    fun touchOpensTheFormAndPressesSignIn() {
        show(AccountStatus.SignedOut)
        openSection(SettingsSection.Accounts)
        // Box art is the first row and has the focus on entering the section: a tap on the account row focuses it, the second opens it.
        val row = accounts().indexOf(SettingsOption.ScraperAccount)
        model.onTapOption(row, actions.shown, wide)
        model.onTapOption(row, actions.shown, wide)
        val form = requireNotNull(model.signIn.form)
        model.onTapPanelItem(1, actions.shown, wide)
        assertEquals(SignInItem.Password, form.focus)
        form.onUsernameChange("plumtester")
        form.onPasswordChange("hunter2")
        model.onTapPanelItem(2, actions.shown, wide)
        assertEquals(1, actions.accountsFake.signIns.size)
    }

    @Test
    fun signedInOpensSignOutAndAOrATapSignsOut() {
        show(AccountStatus.SignedIn("plumtester"))
        openAccountRow()
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        val signOut = accountPageItems(model.content).indexOf(AccountPageItem.SignOut)
        assertEquals("Sign out is the last item", accountPageItems(model.content).lastIndex, signOut)
        repeat(signOut) { press(Action.Move(Direction.Down)) }
        press(Action.Enter)
        assertEquals("Sign out asks first and has signed nobody out yet", PanelPage.ScraperSignOutConfirm, model.focus.page)
        assertEquals(0, actions.accountsFake.signOuts)
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(1, actions.accountsFake.signOuts)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun signOutConfirmOpensOnCancelAndCancelOrBackKeepsTheAccount() {
        show(AccountStatus.SignedIn("plumtester"))
        openAccountRow()
        press(Action.Enter)
        val signOut = accountPageItems(model.content).indexOf(AccountPageItem.SignOut)
        repeat(signOut) { press(Action.Move(Direction.Down)) }
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSignOutConfirm, model.focus.page)
        assertEquals("Cancel has the focus", SignOutConfirmItem.Cancel.ordinal, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals("back on the Sign out button", signOut, model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals("nobody was signed out", 0, actions.accountsFake.signOuts)
    }

    @Test
    fun signOutConfirmByTouch() {
        show(AccountStatus.SignedIn("plumtester"))
        openAccountRow()
        press(Action.Enter)
        val signOut = accountPageItems(model.content).indexOf(AccountPageItem.SignOut)
        model.onTapPanelItem(signOut, actions.shown, wide)
        model.onTapPanelItem(signOut, actions.shown, wide)
        assertEquals(PanelPage.ScraperSignOutConfirm, model.focus.page)
        model.onTapPanelItem(SignOutConfirmItem.SignOut.ordinal, actions.shown, wide)
        model.onTapPanelItem(SignOutConfirmItem.SignOut.ordinal, actions.shown, wide)
        assertEquals(1, actions.accountsFake.signOuts)
    }

    @Test
    fun aFailedSignOutIsSaid() {
        actions.accountsFake.signOutWorks = false
        show(AccountStatus.SignedIn("plumtester"))
        openAccountRow()
        press(Action.Enter)
        val signOut = accountPageItems(model.content).indexOf(AccountPageItem.SignOut)
        model.onTapPanelItem(signOut, actions.shown, wide)
        model.onTapPanelItem(signOut, actions.shown, wide)
        model.onTapPanelItem(SignOutConfirmItem.SignOut.ordinal, actions.shown, wide)
        model.onTapPanelItem(SignOutConfirmItem.SignOut.ordinal, actions.shown, wide)
        assertEquals(SettingsOption.ScraperAccount to SettingsNotice.SignOutFailed, model.work.notice)
    }

    @Test
    fun aRefusedPasswordOffersSignInAgainWithTheNameFilledIn() {
        show(AccountStatus.SignedIn("plumtester", isRefused = true))
        openAccountRow()
        press(Action.Enter)
        assertEquals(listOf(AccountPageItem.SignInAgain, AccountPageItem.SignOut), accountPageItems(model.content))
        press(Action.Move(Direction.Down))
        assertEquals(1, model.focus.panelIndex)
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        assertEquals("plumtester", model.signIn.form?.username)
    }

    @Test
    fun aWrongPasswordIsSaidOnTheAccountRowAndItsPanelOpensSignInAgainOnEveryWidth() {
        for (geometry in listOf(wide, narrow)) {
            show(AccountStatus.SignedIn("plumtester", isRefused = true))
            if (model.focus.column != SettingsColumn.Sections) press(Action.Back, geometry)
            if (model.focus.column != SettingsColumn.Sections) press(Action.Back, geometry)
            while (model.focus.section != SettingsSection.Accounts) press(Action.Move(Direction.Down), geometry)
            press(Action.Enter, geometry)
            focusOption(SettingsOption.ScraperAccount)
            press(Action.Enter, geometry)
            assertEquals(PanelPage.ScraperAccount, model.focus.page)
            assertEquals(0, model.focus.panelIndex)
            press(Action.Enter, geometry)
            assertEquals(PanelPage.ScraperSignIn, model.focus.page)
            assertEquals("plumtester", model.signIn.form?.username)
            press(Action.Back, geometry)
        }
    }

    @Test
    fun theRegionInTheScreenScraperPanelStepsThroughItsFourValuesAndStoresThem() {
        show(AccountStatus.SignedIn("plumtester"))
        openAccountRow()
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        val region = accountPageItems(model.content).indexOf(AccountPageItem.BoxArtRegion)
        while (model.focus.panelIndex < region) move(Direction.Down)
        assertEquals(BoxArtRegionSetting.Usa, actions.settings.library.boxArtRegion)
        press(Action.Enter)
        assertEquals(BoxArtRegionSetting.Europe, actions.settings.library.boxArtRegion)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(BoxArtRegionSetting.World, actions.settings.library.boxArtRegion)
        press(Action.Enter)
        assertEquals("A wraps", BoxArtRegionSetting.Usa, actions.settings.library.boxArtRegion)
        assertEquals("the panel stays on the item", PanelPage.ScraperAccount, model.focus.page)
        assertEquals(region, model.focus.panelIndex)
        // Signed out the panel is the sign-in form: no region to pick.
        assertEquals(false, AccountPageItem.BoxArtRegion in accountPageItems(model.content.copy(account = AccountStatus.SignedIn("plumtester", isRefused = true))))
    }

    private companion object {
        val ACCOUNT_ROWS = setOf(SettingsOption.ScraperAccount, SettingsOption.ScraperStatus, SettingsOption.BoxArtRegion)
    }
}
