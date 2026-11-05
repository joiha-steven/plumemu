package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings › Accounts › Box art: the three choices, how each is applied, and the sign-in ScreenScraper needs. */
internal class SettingsBoxArtSourceTest : SettingsModelHarness() {
    private fun show(account: AccountStatus) {
        model.content = model.content.copy(account = account)
    }

    private fun openPanel() {
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.BoxArtSource)
        press(Action.Enter)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
    }

    private fun stored() = actions.settings.library.boxArtSource

    private fun choose(source: BoxArtSourceSetting) {
        val index = boxArtSourceChoices(model.content.account).indexOf(source)
        val from = model.focus.panelIndex
        repeat(kotlin.math.abs(index - from)) { move(if (index > from) Direction.Down else Direction.Up) }
        press(Action.Enter)
    }

    @Test
    fun theBoxArtRowIsFirstInAccountsInEveryBuild() {
        for (account in listOf(AccountStatus.SignedOut, AccountStatus.SignedIn("plumtester"), AccountStatus.NotInThisBuild)) {
            show(account)
            assertEquals(SettingsOption.BoxArtSource, SettingsSection.Accounts.rowsIn(model.content).first())
        }
    }

    @Test
    fun aBuildWithoutScreenScraperOffersOnlyTheTwoChoicesThatNeedNoAccount() {
        assertEquals(listOf(BoxArtSourceSetting.LibretroOnly, BoxArtSourceSetting.OwnCovers), boxArtSourceChoices(AccountStatus.NotInThisBuild))
        assertEquals(BoxArtSourceSetting.entries, boxArtSourceChoices(AccountStatus.SignedOut))
    }

    @Test
    fun theValueFollowsTheAccountUntilAChoiceIsStored() {
        assertEquals(BoxArtSourceSetting.OwnCovers, boxArtSourceIn(actions.settings, AccountStatus.SignedOut))
        assertEquals(BoxArtSourceSetting.ScreenScraper, boxArtSourceIn(actions.settings, AccountStatus.SignedIn("plumtester")))
        assertEquals("a refused account is not usable", BoxArtSourceSetting.OwnCovers, boxArtSourceIn(actions.settings, AccountStatus.SignedIn("plumtester", isRefused = true)))
        val chosen = actions.settings.copy(library = actions.settings.library.copy(boxArtSource = BoxArtSourceSetting.LibretroOnly))
        assertEquals(BoxArtSourceSetting.LibretroOnly, boxArtSourceIn(chosen, AccountStatus.SignedIn("plumtester")))
    }

    @Test
    fun theRowOpensTheListOnTheSourceInForce() {
        show(AccountStatus.SignedIn("plumtester"))
        openPanel()
        assertEquals("ScreenScraper is first", 0, model.focus.panelIndex)
        press(Action.Back)
        actions.settings = actions.settings.copy(library = actions.settings.library.copy(boxArtSource = BoxArtSourceSetting.OwnCovers))
        press(Action.Enter)
        assertEquals(2, model.focus.panelIndex)
    }

    @Test
    fun boxArtOnlyIsStoredWithNoAccountAndTheListCloses() {
        show(AccountStatus.SignedOut)
        openPanel()
        choose(BoxArtSourceSetting.LibretroOnly)
        assertEquals(BoxArtSourceSetting.LibretroOnly, stored())
        assertEquals(listOf("source LibretroOnly"), actions.boxArtCalls)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull("no sign-in form", model.signIn.form)
    }

    @Test
    fun ownCoversIsStored() {
        show(AccountStatus.SignedIn("plumtester"))
        openPanel()
        choose(BoxArtSourceSetting.OwnCovers)
        assertEquals(BoxArtSourceSetting.OwnCovers, stored())
    }

    @Test
    fun screenScraperWhileSignedOutOpensTheSignInFormAndStoresNothingYet() {
        show(AccountStatus.SignedOut)
        openPanel()
        choose(BoxArtSourceSetting.ScreenScraper)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        assertNotNull(model.signIn.form)
        assertNull("not stored before ScreenScraper accepts", stored())
        assertTrue(actions.boxArtCalls.isEmpty())
    }

    @Test
    fun cancellingTheSignInLeavesTheSettingAsItWas() {
        show(AccountStatus.SignedOut)
        actions.settings = actions.settings.copy(library = actions.settings.library.copy(boxArtSource = BoxArtSourceSetting.LibretroOnly))
        openPanel()
        choose(BoxArtSourceSetting.ScreenScraper)
        press(Action.Back)
        press(Action.Back)
        assertEquals(BoxArtSourceSetting.LibretroOnly, stored())
        assertTrue(actions.boxArtCalls.isEmpty())
    }

    @Test
    fun aRefusedAccountAsksForTheSignInAgainBeforeScreenScraper() {
        show(AccountStatus.SignedIn("plumtester", isRefused = true))
        openPanel()
        choose(BoxArtSourceSetting.ScreenScraper)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        assertNull(stored())
    }

    @Test
    fun aFailedStoreIsSaidUnderTheRowAndTheSettingStays() {
        show(AccountStatus.SignedOut)
        actions.boxArtWorks = false
        openPanel()
        choose(BoxArtSourceSetting.LibretroOnly)
        assertNull(stored())
        assertEquals(SettingsOption.BoxArtSource to SettingsNotice.BoxArtSourceFailed, model.work.notice)
    }

    @Test
    fun aTapMovesTheCursorAndASecondTapAppliesLikeAnyListChoice() {
        show(AccountStatus.SignedOut)
        openPanel()
        model.onTapPanelItem(1, actions.shown, wide)
        assertNull("the first tap only moves the cursor", stored())
        model.onTapPanelItem(1, actions.shown, wide)
        assertEquals(BoxArtSourceSetting.LibretroOnly, stored())
    }

    private fun fillAndSignIn(password: String) {
        val form = requireNotNull(model.signIn.form)
        // The form opens with the keyboard down; the cursor walks down to Sign in.
        form.onUsernameChange("plumtester")
        form.onPasswordChange(password)
        press(Action.Move(Direction.Down))
        press(Action.Move(Direction.Down))
        press(Action.Enter)
    }

    @Test
    fun aSignInThatWorksStoresScreenScraperAndClosesTheForm() {
        show(AccountStatus.SignedOut)
        openPanel()
        choose(BoxArtSourceSetting.ScreenScraper)
        fillAndSignIn("hunter2")
        assertEquals(BoxArtSourceSetting.ScreenScraper, stored())
        assertNull(model.signIn.form)
    }

    @Test
    fun aSignInThatFailsLeavesTheSettingAndKeepsTheForm() {
        actions.accountsFake.answer = { _, _ -> com.plumemu.account.SignInResult.WrongCredentials }
        show(AccountStatus.SignedOut)
        openPanel()
        choose(BoxArtSourceSetting.ScreenScraper)
        fillAndSignIn("wrong")
        assertNull(stored())
        assertNotNull(model.signIn.form)
    }

    @Test
    fun aLateSignInAnswerAfterTheFormWasLeftDoesNotOverwriteTheChoice() {
        actions.accountsFake.holdsAnswers = true
        show(AccountStatus.SignedOut)
        openPanel()
        choose(BoxArtSourceSetting.ScreenScraper)
        fillAndSignIn("hunter2")
        assertNotNull("checking: the form is open", model.signIn.form)
        press(Action.Back)
        assertNull("the form is left", model.signIn.form)
        focusOption(SettingsOption.BoxArtSource)
        press(Action.Enter)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
        choose(BoxArtSourceSetting.LibretroOnly)
        assertEquals(BoxArtSourceSetting.LibretroOnly, stored())
        actions.accountsFake.release()
        assertEquals("the late answer leaves the person's choice", BoxArtSourceSetting.LibretroOnly, stored())
    }
}
