package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import com.plumemu.scraper.PauseReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Settings › Library › ScreenScraper's box art: what the panel offers in each state,
 * Download missing, Re-download all and its confirm, Choose systems… with its checkmarks, Pause and
 * Resume; gamepad and touch; and the one mapping from the run's state to a line.
 */
internal class SettingsBoxArtTest : SettingsModelHarness() {
    private val counts = ScrapeCounts(withBoxFront = 300, listed = 2_054, pending = 1_714)

    private fun show(
        boxArt: ScrapeStatus = ScrapeStatus.Running(counts, gamesPerMinute = 5.7),
        account: AccountStatus = AccountStatus.SignedIn("plumtester"),
        systems: List<SystemEmulators> = SYSTEMS,
    ) {
        model.content = model.content.copy(account = account, boxArt = boxArt, systems = systems)
    }

    private fun openPanel() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.ScraperAccount)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
    }

    private fun items() = accountPageItems(model.content)

    private fun focusItem(item: AccountPageItem) {
        while (model.focus.panelIndex < items().indexOf(item)) press(Action.Move(Direction.Down))
        while (model.focus.panelIndex > items().indexOf(item)) press(Action.Move(Direction.Up))
        assertEquals(item, items()[model.focus.panelIndex])
    }

    @Test
    fun signedInThePanelOffersTheActionsWithDownloadMissingFocusedFirst() {
        show()
        openPanel()
        assertEquals(
            listOf(AccountPageItem.DownloadMissing, AccountPageItem.RedownloadAll, AccountPageItem.ChooseSystems, AccountPageItem.ClearBoxArt, AccountPageItem.PauseOrResume, AccountPageItem.SignOut),
            items(),
        )
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun aRefusedPasswordOrAnEmptyLibraryOfferOnlyWhatCanWork() {
        show(account = AccountStatus.SignedIn("plumtester", isRefused = true))
        assertEquals(listOf(AccountPageItem.SignInAgain, AccountPageItem.SignOut), items())
        show(boxArt = ScrapeStatus.Idle(ScrapeCounts(0, 0, 0)), systems = emptyList())
        assertEquals("pictures may be left of games that are gone", listOf(AccountPageItem.ClearBoxArt, AccountPageItem.SignOut), items())
        show(boxArt = ScrapeStatus.PausedByPerson(ScrapeCounts(0, 0, 0)), systems = emptyList())
        assertEquals("Resume stays reachable", listOf(AccountPageItem.ClearBoxArt, AccountPageItem.PauseOrResume, AccountPageItem.SignOut), items())
        show(systems = SYSTEMS.take(1))
        assertEquals("one system: nothing to choose", listOf(AccountPageItem.DownloadMissing, AccountPageItem.RedownloadAll, AccountPageItem.ClearBoxArt, AccountPageItem.PauseOrResume, AccountPageItem.SignOut), items())
    }

    @Test
    fun aOnDownloadMissingAsksAgainEveryGameWithoutBoxArtAndStays() {
        show()
        openPanel()
        press(Action.Enter)
        assertEquals(listOf("Missing all"), actions.boxArtCalls)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertNull(model.work.notice)
    }

    @Test
    fun reDownloadAllAsksFirstCancelIsFocusedAndBackReturnsToItsButton() {
        show()
        openPanel()
        focusItem(AccountPageItem.RedownloadAll)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperConfirm, model.focus.page)
        assertNull("every game", model.focus.redownloadSystems)
        assertEquals(BoxArtConfirmItem.Cancel.ordinal, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals("Cancel asks nothing", emptyList<String>(), actions.boxArtCalls)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals(items().indexOf(AccountPageItem.RedownloadAll), model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals(items().indexOf(AccountPageItem.RedownloadAll), model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(listOf("All all"), actions.boxArtCalls)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
    }

    @Test
    fun theConfirmByTouch() {
        show()
        openPanel()
        val redownload = items().indexOf(AccountPageItem.RedownloadAll)
        model.onTapPanelItem(redownload, actions.shown, wide)
        model.onTapPanelItem(redownload, actions.shown, wide)
        assertEquals(PanelPage.ScraperConfirm, model.focus.page)
        model.onTapPanelItem(BoxArtConfirmItem.Redownload.ordinal, actions.shown, wide)
        model.onTapPanelItem(BoxArtConfirmItem.Redownload.ordinal, actions.shown, wide)
        assertEquals(listOf("All all"), actions.boxArtCalls)
    }

    @Test
    fun chooseSystemsChecksByAThenAsksForThoseOnly() {
        show()
        openPanel()
        focusItem(AccountPageItem.ChooseSystems)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSystems, model.focus.page)
        assertEquals("none checked: only the systems", SYSTEMS.size, systemsPickCount(model.content, model.focus))
        press(Action.Enter)
        assertEquals(setOf("snes"), model.focus.chosenSystems)
        press(Action.Move(Direction.Down))
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertEquals(setOf("snes", "psx"), model.focus.chosenSystems)
        press(Action.Move(Direction.Up))
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals("A again takes the check off", setOf("psx"), model.focus.chosenSystems)
        press(Action.Enter)
        assertEquals(SYSTEMS.size + 2, systemsPickCount(model.content, model.focus))
        repeat(SYSTEMS.size) { press(Action.Move(Direction.Down)) }
        assertEquals(SystemsPickAction.DownloadMissing, systemsPickActionAt(model.content, model.focus.panelIndex))
        press(Action.Enter)
        assertEquals(listOf("Missing [psx, snes]"), actions.boxArtCalls)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals(items().indexOf(AccountPageItem.ChooseSystems), model.focus.panelIndex)
        press(Action.Enter)
        assertEquals("the picker opens with nothing checked", emptySet<String>(), model.focus.chosenSystems)
    }

    @Test
    fun chooseSystemsByTouchChecksOnTheFirstTapAndReDownloadAsksFirst() {
        show()
        openPanel()
        val choose = items().indexOf(AccountPageItem.ChooseSystems)
        model.onTapPanelItem(choose, actions.shown, wide)
        model.onTapPanelItem(choose, actions.shown, wide)
        assertEquals(PanelPage.ScraperSystems, model.focus.page)
        model.onTapPanelItem(1, actions.shown, wide)
        assertEquals(setOf("gba"), model.focus.chosenSystems)
        model.onTapPanelItem(1, actions.shown, wide)
        assertEquals("a second tap takes it off", emptySet<String>(), model.focus.chosenSystems)
        model.onTapPanelItem(1, actions.shown, wide)
        val redownload = SYSTEMS.size + SystemsPickAction.Redownload.ordinal
        model.onTapPanelItem(redownload, actions.shown, wide)
        assertEquals("a button's first tap only focuses it", PanelPage.ScraperSystems, model.focus.page)
        model.onTapPanelItem(redownload, actions.shown, wide)
        assertEquals(PanelPage.ScraperConfirm, model.focus.page)
        assertEquals(setOf("gba"), model.focus.redownloadSystems)
        press(Action.Back)
        assertEquals("Back keeps the checks", PanelPage.ScraperSystems, model.focus.page)
        assertEquals(setOf("gba"), model.focus.chosenSystems)
        assertEquals(redownload, model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals(listOf("All [gba]"), actions.boxArtCalls)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun backFromThePickerReturnsToChooseSystems() {
        show()
        openPanel()
        focusItem(AccountPageItem.ChooseSystems)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        assertEquals(items().indexOf(AccountPageItem.ChooseSystems), model.focus.panelIndex)
    }

    @Test
    fun pauseAndResumeFollowTheState() {
        show()
        openPanel()
        focusItem(AccountPageItem.PauseOrResume)
        press(Action.Enter)
        assertEquals(listOf("pause"), actions.boxArtCalls)
        show(boxArt = ScrapeStatus.PausedByPerson(counts))
        press(Action.Enter)
        assertEquals(listOf("pause", "resume"), actions.boxArtCalls)
    }

    @Test
    fun aFailedAskOrPauseIsSaidInThePanel() {
        actions.boxArtWorks = false
        show()
        openPanel()
        press(Action.Enter)
        assertEquals(SettingsOption.ScraperAccount to SettingsNotice.BoxArtAskFailed, model.work.notice)
        focusItem(AccountPageItem.PauseOrResume)
        press(Action.Enter)
        assertEquals(SettingsOption.ScraperAccount to SettingsNotice.BoxArtPauseFailed, model.work.notice)
    }

    @Test
    fun aSystemGoneWhileThePickerIsOpenIsNoLongerChosen() {
        show()
        openPanel()
        focusItem(AccountPageItem.ChooseSystems)
        press(Action.Enter)
        press(Action.Enter)
        show(systems = SYSTEMS.drop(1))
        assertEquals(emptySet<String>(), chosenSystemsIn(model.content, model.focus))
        assertEquals(SYSTEMS.size - 1, systemsPickCount(model.content, model.focus))
    }

    // The panel's buttons change while it is open: the focus stays on its action.

    @Test
    fun aRefusalWhileThePanelIsOpenKeepsTheFocusOnAButton() {
        show()
        openPanel()
        focusItem(AccountPageItem.SignOut)
        show(account = AccountStatus.SignedIn("plumtester", isRefused = true))
        assertEquals("Sign out is still there: the focus stays on it", AccountPageItem.SignOut, items()[model.focus.panelIndex])
        show()
        focusItem(AccountPageItem.PauseOrResume)
        show(account = AccountStatus.SignedIn("plumtester", isRefused = true))
        assertEquals("Pause is gone: the focus is kept inside the list", items().lastIndex, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals("A on the focused Sign out asks first", PanelPage.ScraperSignOutConfirm, model.focus.page)
        press(Action.Move(Direction.Up))
        press(Action.Enter)
        assertEquals("then Sign out signs out", 1, actions.accountsFake.signOuts)
    }

    @Test
    fun aSystemAddedWhileThePanelIsOpenKeepsTheFocusOnPause() {
        show(systems = SYSTEMS.take(1))
        openPanel()
        focusItem(AccountPageItem.PauseOrResume)
        show(systems = SYSTEMS.take(2))
        assertEquals(AccountPageItem.PauseOrResume, items()[model.focus.panelIndex])
        press(Action.Enter)
        assertEquals("A pauses, it does not open the picker", listOf("pause"), actions.boxArtCalls)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
    }

    @Test
    fun aSystemAddedWhileThePickerIsOpenKeepsTheFocusOnItsRowOrButton() {
        show(systems = SYSTEMS.drop(1))
        openPanel()
        focusItem(AccountPageItem.ChooseSystems)
        press(Action.Enter)
        press(Action.Move(Direction.Down))
        press(Action.Enter)
        assertEquals(setOf("psx"), model.focus.chosenSystems)
        show(systems = SYSTEMS)
        assertEquals("psx", SYSTEMS[model.focus.panelIndex].systemId)
        press(Action.Move(Direction.Down))
        assertEquals(SystemsPickAction.DownloadMissing, systemsPickActionAt(model.content, model.focus.panelIndex))
        show(systems = SYSTEMS.drop(1))
        assertEquals(SystemsPickAction.DownloadMissing, systemsPickActionAt(model.content, model.focus.panelIndex))
    }

    // The one mapping from the run's state to the line (texts in SettingsBoxArtTextTest).

    @Test
    fun everyStateHasItsLine() {
        val signedIn = AccountStatus.SignedIn("plumtester")
        val cases =
            listOf(
                ScrapeStatus.Running(counts, 5.7) to BoxArtLine.Running(counts, TimeLeft.Hours(5)),
                ScrapeStatus.Running(counts, null) to BoxArtLine.Running(counts, null),
                ScrapeStatus.Running(null, 5.7) to BoxArtLine.Running(null, null),
                ScrapeStatus.Starting(counts) to BoxArtLine.Starting(counts),
                ScrapeStatus.PausedByPerson(counts) to BoxArtLine.PausedByPerson,
                ScrapeStatus.WaitingForWifi(counts) to BoxArtLine.WaitingForWifi,
                ScrapeStatus.WaitingForBattery(counts) to BoxArtLine.WaitingForBattery,
                ScrapeStatus.Paused(counts, 9_000L, PauseReason.DailyQuota) to BoxArtLine.DailyLimit(9_000L),
                ScrapeStatus.Paused(counts, 9_000L, PauseReason.ServerClosed) to BoxArtLine.ServerClosed(9_000L),
                ScrapeStatus.Retrying(counts, 7_000L) to BoxArtLine.Retrying(7_000L),
                ScrapeStatus.Idle(ScrapeCounts(2_012, 2_054, 0)) to BoxArtLine.Done(2_012, 2_054),
                ScrapeStatus.Idle(counts) to BoxArtLine.Some(300, 2_054),
                ScrapeStatus.Idle(null) to BoxArtLine.Unreadable,
                ScrapeStatus.Idle(ScrapeCounts(0, 0, 0)) to BoxArtLine.EmptyLibrary,
                ScrapeStatus.Starting(ScrapeCounts(0, 0, 0)) to BoxArtLine.EmptyLibrary,
                ScrapeStatus.PausedByPerson(ScrapeCounts(0, 0, 0)) to BoxArtLine.PausedByPerson,
            )
        for ((status, line) in cases) assertEquals("$status", line, boxArtLineOf(signedIn, status))
        assertEquals(BoxArtLine.WrongPassword, boxArtLineOf(AccountStatus.SignedIn("plumtester", isRefused = true), ScrapeStatus.Running(counts, 5.7)))
        assertEquals(BoxArtLine.NotInThisBuild, boxArtLineOf(AccountStatus.NotInThisBuild, ScrapeStatus.NotInThisBuild))
        assertNull(boxArtLineOf(AccountStatus.SignedOut, ScrapeStatus.SignedOut))
        assertNull("the run's state not here yet", boxArtLineOf(signedIn, ScrapeStatus.SignedOut))
    }

    @Test
    fun timeLeftIsRoundedToWhatAPersonReads() {
        assertNull(timeLeftOf(100, null))
        assertNull(timeLeftOf(0, 5.0))
        assertEquals(TimeLeft.UnderAMinute, timeLeftOf(3, 6.0))
        assertEquals(TimeLeft.Minutes(4), timeLeftOf(20, 6.0))
        assertEquals(TimeLeft.Minutes(40), timeLeftOf(236, 6.0))
        assertEquals(TimeLeft.Minutes(55), timeLeftOf(350, 6.0))
        assertEquals(TimeLeft.Hours(1), timeLeftOf(362, 6.0))
        assertEquals(TimeLeft.Hours(5), timeLeftOf(1_714, 5.7))
        assertEquals(TimeLeft.Hours(47), timeLeftOf(16_900, 6.0))
        assertEquals(TimeLeft.Days(3), timeLeftOf(26_000, 6.0))
    }

    @Test
    fun theSystemsRowAppearingWhileThePickerIsOpenKeepsTheCursorOnItsSystem() {
        show()
        openPanel()
        focusItem(AccountPageItem.ChooseSystems)
        press(Action.Enter)
        press(Action.Move(Direction.Down))
        assertEquals("gba", model.content.systems[model.focus.panelIndex].systemId)
        // One push: the Systems row appears (the ScreenScraper row shifts) and a system joins at the top.
        model.content =
            model.content.copy(
                librarySystems = listOf(LibrarySystem("snes", "Super Nintendo", 3)),
                systems = listOf(SystemEmulators("nes", "NES", null, null, emptyList())) + SYSTEMS,
            )
        assertEquals(PanelPage.ScraperSystems, model.focus.page)
        assertEquals("gba", model.content.systems[model.focus.panelIndex].systemId)
    }

    private companion object {
        val SYSTEMS =
            listOf(
                SystemEmulators("snes", "Super Nintendo", null, null, emptyList()),
                SystemEmulators("gba", "Game Boy Advance", null, null, emptyList()),
                SystemEmulators("psx", "PlayStation", null, null, emptyList()),
            )
    }
}
