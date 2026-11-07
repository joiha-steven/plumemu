package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accounts' ScreenScraper row, listed only while ScreenScraper is the source in force or an account is signed in, a refused one
 * too; a build without credentials keeps its status line. When the row leaves under the cursor, the cursor lands on Box art.
 */
internal class SettingsAccountRowsTest : SettingsModelHarness() {
    private val box = SettingsOption.BoxArtSource
    private val scraper = SettingsOption.ScraperAccount
    private val ra = SettingsOption.RetroAchievementsAccount
    private val usable = AccountStatus.SignedIn("plumtester")
    private val refused = AccountStatus.SignedIn("plumtester", isRefused = true)
    private val sources = listOf(null, BoxArtSourceSetting.ScreenScraper, BoxArtSourceSetting.LibretroOnly, BoxArtSourceSetting.OwnCovers)

    private fun content(
        account: AccountStatus,
        source: BoxArtSourceSetting?,
    ) = SettingsContent(account = account, chosenBoxArtSource = source)

    private fun rows(
        account: AccountStatus,
        source: BoxArtSourceSetting?,
    ) = SettingsSection.Accounts.rowsIn(content(account, source))

    @Test
    fun signedOutTheRowIsListedOnlyWithScreenScraperAsTheSource() {
        assertEquals("nothing chosen: own covers", listOf(box, ra), rows(AccountStatus.SignedOut, null))
        assertEquals(listOf(box, scraper, ra), rows(AccountStatus.SignedOut, BoxArtSourceSetting.ScreenScraper))
        assertEquals(listOf(box, ra), rows(AccountStatus.SignedOut, BoxArtSourceSetting.LibretroOnly))
        assertEquals(listOf(box, ra), rows(AccountStatus.SignedOut, BoxArtSourceSetting.OwnCovers))
    }

    @Test
    fun signedInTheRowIsListedOnEverySource() {
        for (source in sources) assertEquals("$source", listOf(box, scraper, ra), rows(usable, source))
    }

    @Test
    fun aRefusedAccountKeepsTheRowSoSignInAgainStaysReachable() {
        for (source in sources) assertEquals("$source", listOf(box, scraper, ra), rows(refused, source))
        assertTrue(AccountPageItem.SignInAgain in accountPageItems(content(refused, BoxArtSourceSetting.OwnCovers)))
    }

    @Test
    fun aBuildWithoutCredentialsKeepsItsStatusRowOnEverySource() {
        for (source in sources) assertEquals("$source", listOf(box, SettingsOption.ScraperStatus, ra), rows(AccountStatus.NotInThisBuild, source))
    }

    private fun onScraperRow(account: AccountStatus = usable) {
        model.content = content(account, BoxArtSourceSetting.LibretroOnly)
        openSection(SettingsSection.Accounts)
        focusOption(scraper)
    }

    private fun assertOnBoxArtRow() {
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(box, model.focusedOption)
    }

    @Test
    fun theRowLeavingUnderTheCursorLandsOnBoxArtNotOnAnotherAccount() {
        onScraperRow()
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.LibretroOnly)
        assertOnBoxArtRow()
    }

    @Test
    fun thePanelClosesWhenItsRowLeaves() {
        onScraperRow()
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.OwnCovers)
        assertOnBoxArtRow()
    }

    @Test
    fun aConfirmDeepInThePanelClosesToo() {
        onScraperRow()
        press(Action.Enter)
        repeat(accountPageItems(model.content).indexOf(AccountPageItem.ClearBoxArt)) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals(PanelPage.ScraperClearConfirm, model.focus.page)
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.LibretroOnly)
        assertOnBoxArtRow()
        press(Action.Enter)
        assertEquals("a press after it opens Box art's own panel", PanelPage.BoxArtSource, model.focus.page)
        assertTrue(actions.boxArtCalls.none { it == "clear" })
    }

    @Test
    fun aSignInFormOpenOnTheRowLeavesWithIt() {
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.ScreenScraper)
        openSection(SettingsSection.Accounts)
        focusOption(scraper)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.OwnCovers)
        assertOnBoxArtRow()
        assertNull("the typed words do not wait for the next opening", model.signIn.form)
    }

    @Test
    fun aSignInFormOpenFromBoxArtStaysWhenTheRowComesAndGoes() {
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.OwnCovers)
        openSection(SettingsSection.Accounts)
        focusOption(box)
        press(Action.Enter)
        while (model.focus.panelIndex > 0) move(Direction.Up)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        val form = model.signIn.form
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.ScreenScraper)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        assertEquals(box, model.focusedOption)
        assertEquals(form, model.signIn.form)
    }

    @Test
    fun theCursorOnRetroAchievementsStaysThereWhenTheRowComesOrGoes() {
        model.content = content(usable, BoxArtSourceSetting.OwnCovers)
        openSection(SettingsSection.Accounts)
        focusOption(ra)
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.OwnCovers)
        assertEquals(ra, model.focusedOption)
        model.content = content(usable, BoxArtSourceSetting.OwnCovers)
        assertEquals(ra, model.focusedOption)
    }

    @Test
    fun anOpenPanelOfRetroAchievementsStaysOpenWhenTheRowGoes() {
        model.content = content(usable, BoxArtSourceSetting.OwnCovers).copy(raAccount = com.plumemu.account.RaAccountStatus.SignedIn("plumtester", 0))
        openSection(SettingsSection.Accounts)
        focusOption(ra)
        press(Action.Enter)
        assertEquals(PanelPage.RaAccount, model.focus.page)
        model.content = model.content.copy(account = AccountStatus.SignedOut)
        assertEquals(PanelPage.RaAccount, model.focus.page)
        assertEquals(ra, model.focusedOption)
    }

    @Test
    fun signingOutLandsOnBoxArt() {
        onScraperRow()
        press(Action.Enter)
        val signOut = accountPageItems(model.content).indexOf(AccountPageItem.SignOut)
        repeat(signOut) { move(Direction.Down) }
        press(Action.Enter)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals("closes on its row first", scraper, model.focusedOption)
        model.content = content(AccountStatus.SignedOut, BoxArtSourceSetting.LibretroOnly)
        assertOnBoxArtRow()
    }
}
