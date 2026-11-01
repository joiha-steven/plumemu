package com.plumemu.settings.screen

import com.plumemu.account.RA_KEY_PAGE_URL
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInResult
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The RetroAchievements form's "Open the key page" button: where it is, what opens, and the no-browser line. */
internal class SettingsRaKeyPageTest : SettingsModelHarness() {
    /** A on the row opens the form typing on the name (tray up); B puts the tray away and the form stays. */
    private fun openForm(option: SettingsOption) {
        model.content = model.content.copy(account = com.plumemu.account.AccountStatus.SignedOut)
        openSection(SettingsSection.Accounts)
        focusOption(option)
        press(Action.Enter)
        val form = requireNotNull(model.signIn.form)
        assertEquals(SignInItem.Username, form.focus)
        assertTrue("the form opens typing, the tray up", form.isTyping && form.isTrayUp)
        press(Action.Back)
        assertSame("B puts the tray away and the form stays", form, model.signIn.form)
        assertFalse(form.isTyping)
    }

    private fun openRaForm() = openForm(SettingsOption.RetroAchievementsAccount)

    private fun openScraperForm() = openForm(SettingsOption.ScraperAccount)

    private fun toKeyPageButton() {
        repeat(4) { press(Action.Move(Direction.Down)) }
    }

    @Test
    fun theButtonIsOnTheRaFormOnly() {
        openRaForm()
        assertEquals(SignInItem.OpenKeyPage, requireNotNull(model.signIn.form).items.last())
    }

    @Test
    fun theScreenScraperFormHasNoKeyPageButton() {
        openScraperForm()
        val items = requireNotNull(model.signIn.form).items
        assertFalse(SignInItem.OpenKeyPage in items)
        assertEquals(listOf(SignInItem.Username, SignInItem.Password, SignInItem.SignIn, SignInItem.Leave), items)
        // Down past Cancel stays on Cancel.
        repeat(5) { press(Action.Move(Direction.Down)) }
        assertEquals(SignInItem.Leave, requireNotNull(model.signIn.form).focus)
    }

    @Test
    fun theDpadReachesTheButtonAndUpComesBack() {
        openRaForm()
        val form = requireNotNull(model.signIn.form)
        toKeyPageButton()
        assertEquals(SignInItem.OpenKeyPage, form.focus)
        press(Action.Move(Direction.Down))
        assertEquals("the last item stays", SignInItem.OpenKeyPage, form.focus)
        press(Action.Move(Direction.Up))
        assertEquals(SignInItem.Leave, form.focus)
    }

    @Test
    fun aOpensExactlyTheKeyPage() {
        openRaForm()
        toKeyPageButton()
        press(Action.Enter)
        assertEquals(listOf("https://retroachievements.org/settings"), actions.webPages)
        assertEquals(RA_KEY_PAGE_URL, actions.webPages.single())
        assertNull(requireNotNull(model.signIn.form).message)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
    }

    @Test
    fun aTapOpensTheKeyPage() {
        openRaForm()
        val form = requireNotNull(model.signIn.form)
        model.onTapPanelItem(form.items.indexOf(SignInItem.OpenKeyPage), actions.shown, wide)
        assertEquals(listOf(RA_KEY_PAGE_URL), actions.webPages)
    }

    @Test
    fun noBrowserSaysSoAndNothingCrashes() {
        actions.hasBrowser = false
        openRaForm()
        toKeyPageButton()
        press(Action.Enter)
        val form = requireNotNull(model.signIn.form)
        assertEquals(SignInResult.NoBrowser, form.message)
        assertEquals(SignInItem.OpenKeyPage, form.focus)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        // Typing clears the line; the form still works.
        form.onPasswordChange("k")
        assertNull(form.message)
    }
}
