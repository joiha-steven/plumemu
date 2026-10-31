package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library's ScreenScraper rows: which rows show for each account state,
 * the status line, signing in with the shared form, signing out, the box art region; gamepad and
 * touch.
 */
internal class SettingsAccountTest : SettingsModelHarness() {
    private fun show(account: AccountStatus) {
        model.content = model.content.copy(account = account)
    }

    private fun library() = SettingsSection.Library.rowsIn(model.content)

    private fun openAccountRow() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.ScraperAccount)
    }

    @Test
    fun noDeveloperCredentialsListsOnlyTheNotAvailableLine() {
        show(AccountStatus.NotInThisBuild)
        val rows = library()
        assertTrue(SettingsOption.ScraperStatus in rows)
        assertFalse("never asks for an account", SettingsOption.ScraperAccount in rows)
        assertFalse(SettingsOption.BoxArtRegion in rows)
        assertEquals(BoxArtLine.NotInThisBuild, boxArtLineOf(model.content.account, model.content.boxArt))
    }

    @Test
    fun signedOutListsSignInAndTheRegionWithNoStatusLine() {
        show(AccountStatus.SignedOut)
        val rows = library()
        assertEquals(listOf(SettingsOption.ScraperAccount, SettingsOption.BoxArtRegion), rows.filter { it in ACCOUNT_ROWS })
        assertNull(boxArtLineOf(model.content.account, model.content.boxArt))
    }

    @Test
    fun signedInListsNoStatusRowWhateverTheStateTheAccountRowSaysIt() {
        // The daily limit and the wrong password are no rows of their own.
        for (account in listOf(AccountStatus.SignedIn("plumtester"), AccountStatus.SignedIn("plumtester", isRefused = true), AccountStatus.SignedIn("plumtester", dailyLimitUntilMillis = 5_000L))) {
            show(account)
            assertEquals(listOf(SettingsOption.ScraperAccount, SettingsOption.BoxArtRegion), library().filter { it in ACCOUNT_ROWS })
        }
        show(AccountStatus.SignedIn("plumtester", isRefused = true))
        assertEquals(BoxArtLine.WrongPassword, boxArtLineOf(model.content.account, model.content.boxArt))
        show(AccountStatus.SignedIn("plumtester", dailyLimitUntilMillis = 5_000L))
        assertEquals("before the run's state arrives, the account's pause", BoxArtLine.DailyLimit(5_000L), boxArtLineOf(model.content.account, model.content.boxArt))
        show(AccountStatus.SignedIn("plumtester", isRefused = true, dailyLimitUntilMillis = 5_000L))
        assertEquals("a refusal matters more than a pause", BoxArtLine.WrongPassword, boxArtLineOf(model.content.account, model.content.boxArt))
    }

    @Test
    fun theAccountRowSitsAfterSortGamesAndBeforeTheGameLists() {
        show(AccountStatus.SignedOut)
        val rows = library()
        // Sort games, then System order (Systems too once a system has games), then the account.
        assertEquals(rows.indexOf(SettingsOption.SortGames) + 1, rows.indexOf(SettingsOption.SystemOrder))
        val lastLibraryRow = maxOf(rows.indexOf(SettingsOption.SystemOrder), rows.indexOf(SettingsOption.Systems))
        assertEquals(lastLibraryRow + 1, rows.indexOf(SettingsOption.ScraperAccount))
    }

    @Test
    fun aSignInByGamepadOpensTheFormChecksAndClosesOnSuccess() {
        show(AccountStatus.SignedOut)
        openAccountRow()
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        val form = requireNotNull(model.signIn.form)
        assertEquals(SignInItem.Username, form.focus)
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
        press(Action.Enter)
        val form = requireNotNull(model.signIn.form)
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
        press(Action.Enter)
        press(Action.Back)
        assertNull(model.signIn.form)
        assertEquals(SettingsColumn.Options, model.focus.column)

        press(Action.Enter)
        val stacked = requireNotNull(model.signIn.form)
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
        openSection(SettingsSection.Library)
        val row = library().indexOf(SettingsOption.ScraperAccount)
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
    fun theRegionStepsThroughItsFourValuesAndStoresThem() {
        show(AccountStatus.SignedOut)
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.BoxArtRegion)
        assertEquals(BoxArtRegionSetting.Usa, actions.settings.library.boxArtRegion)
        move(Direction.Right)
        assertEquals(BoxArtRegionSetting.Europe, actions.settings.library.boxArtRegion)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(BoxArtRegionSetting.World, actions.settings.library.boxArtRegion)
        press(Action.Enter)
        assertEquals("A wraps", BoxArtRegionSetting.Usa, actions.settings.library.boxArtRegion)
        move(Direction.Left)
        assertEquals("left stops at the first", BoxArtRegionSetting.Usa, actions.settings.library.boxArtRegion)
    }

    private companion object {
        val ACCOUNT_ROWS = setOf(SettingsOption.ScraperAccount, SettingsOption.ScraperStatus, SettingsOption.BoxArtRegion)
    }
}
