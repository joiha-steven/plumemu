package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coreinput.Action
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import com.plumemu.scraper.PictureFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** When box art failed, A and a tap on the Box art row say "Try again" and ask a run for the games without box art only. */
internal class SettingsBoxArtRetryTest : SettingsModelHarness() {
    private val counts = ScrapeCounts(withBoxFront = 300, listed = 412, pending = 0)

    private fun show(
        boxArt: ScrapeStatus,
        account: AccountStatus = AccountStatus.SignedIn("plumtester"),
    ) {
        model.content = model.content.copy(account = account, boxArt = boxArt)
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.ScraperAccount)
    }

    private val failed get() = ScrapeStatus.Failed(counts, PictureFailure.NoNetwork)

    @Test
    fun aFailureNamesTheKeyTryAgainAndAQueuesARunForTheMissingGamesOnly() {
        show(failed)
        assertEquals(R.string.settings_hint_try_again, confirmHint(model))
        press(Action.Enter)
        assertEquals(listOf("Missing all"), actions.boxArtCalls)
        assertEquals("the panel did not open", SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun aTapQueuesTheRunToo() {
        show(ScrapeStatus.Failed(counts, PictureFailure.ServerDidNotAnswer))
        val index = model.optionIndex
        model.onTapOption(index, actions.shown, wide)
        assertEquals(listOf("Missing all"), actions.boxArtCalls)
    }

    @Test
    fun noneFoundCanBeTriedAgainToo() {
        show(ScrapeStatus.Idle(ScrapeCounts(withBoxFront = 0, listed = 412, pending = 0)))
        assertEquals(R.string.settings_hint_try_again, confirmHint(model))
        press(Action.Enter)
        assertEquals(listOf("Missing all"), actions.boxArtCalls)
    }

    @Test
    fun runningAndDoneKeepAsOpensItsOldWay() {
        show(ScrapeStatus.Running(counts, null), AccountStatus.SignedIn("plumtester"))
        assertEquals(R.string.settings_hint_open, confirmHint(model))
        press(Action.Enter)
        assertTrue("no run was asked: ${actions.boxArtCalls}", actions.boxArtCalls.isEmpty())
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
    }

    @Test
    fun doneKeepsItsHintToo() {
        show(ScrapeStatus.Idle(counts), AccountStatus.SignedIn("plumtester"))
        assertEquals(R.string.settings_hint_open, confirmHint(model))
        press(Action.Enter)
        assertTrue(actions.boxArtCalls.isEmpty())
    }
}
