package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.graph.BoxArtClearResult
import com.plumemu.scraper.PictureFailure
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Accounts › Box art: under the choices, the work a person can do with no usable account, by the source in force. A person with
 * a usable account keeps all of it in ScreenScraper's panel; the same confirm and the same calls serve both.
 */
internal class SettingsBoxArtPanelWorkTest : SettingsModelHarness() {
    private val games = ScrapeStatus.Idle(ScrapeCounts(300, 2_054, 0))
    private val noGames = ScrapeStatus.Idle(ScrapeCounts(0, 0, 0))
    private val refused = AccountStatus.SignedIn("plumtester", isRefused = true)
    private val usable = AccountStatus.SignedIn("plumtester")

    private fun content(
        source: BoxArtSourceSetting?,
        account: AccountStatus = AccountStatus.SignedOut,
        boxArt: ScrapeStatus = games,
    ) = SettingsContent(account = account, chosenBoxArtSource = source, boxArt = boxArt)

    private fun work(content: SettingsContent) = boxArtPanelEntries(content).filterIsInstance<BoxArtPanelEntry.Work>().map { it.item }

    private fun openPanel(
        source: BoxArtSourceSetting?,
        account: AccountStatus = AccountStatus.SignedOut,
        boxArt: ScrapeStatus = games,
    ) {
        actions.settings = actions.settings.copy(library = actions.settings.library.copy(boxArtSource = source))
        model.content = content(source, account, boxArt)
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.BoxArtSource)
        press(Action.Enter)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
    }

    private fun toWork(item: AccountPageItem) {
        val at = boxArtPanelEntries(model.content).indexOf(BoxArtPanelEntry.Work(item))
        assertTrue("$item is listed", at >= 0)
        while (model.focus.panelIndex < at) move(Direction.Down)
        while (model.focus.panelIndex > at) move(Direction.Up)
    }

    @Test
    fun boxArtOnlySignedOutOffersWhatARunCanDo() {
        assertEquals(
            listOf(AccountPageItem.DownloadMissing, AccountPageItem.RedownloadAll, AccountPageItem.ClearBoxArt, AccountPageItem.BoxArtRegion),
            work(content(BoxArtSourceSetting.LibretroOnly)),
        )
    }

    @Test
    fun boxArtOnlyWithNoGamesHasNothingToDownload() {
        assertFalse(AccountPageItem.DownloadMissing in work(content(BoxArtSourceSetting.LibretroOnly, boxArt = noGames)))
        assertTrue(AccountPageItem.ClearBoxArt in work(content(BoxArtSourceSetting.LibretroOnly, boxArt = noGames)))
    }

    private val twoSystems = listOf(SystemEmulators("snes", "Super Nintendo", null, null, emptyList()), SystemEmulators("gba", "Game Boy Advance", null, null, emptyList()))

    @Test
    fun chooseSystemsNeedsTwoSystemsAndOwnCoversGetsNeitherItNorReDownload() {
        val many = content(BoxArtSourceSetting.LibretroOnly).copy(systems = twoSystems)
        assertTrue(AccountPageItem.ChooseSystems in work(many))
        assertFalse(AccountPageItem.ChooseSystems in work(many.copy(systems = twoSystems.take(1))))
        val own = work(content(BoxArtSourceSetting.OwnCovers).copy(systems = twoSystems))
        assertFalse(AccountPageItem.ChooseSystems in own || AccountPageItem.RedownloadAll in own)
        val noGames = work(content(BoxArtSourceSetting.LibretroOnly, boxArt = noGames).copy(systems = twoSystems))
        assertFalse(AccountPageItem.ChooseSystems in noGames || AccountPageItem.RedownloadAll in noGames)
    }

    @Test
    fun reDownloadAllAsksFirstOnCancelAndCancelComesBackToTheItem() {
        openPanel(BoxArtSourceSetting.LibretroOnly)
        toWork(AccountPageItem.RedownloadAll)
        val at = model.focus.panelIndex
        press(Action.Enter)
        assertEquals(PanelPage.ScraperConfirm, model.focus.page)
        assertEquals(BoxArtConfirmItem.Cancel.ordinal, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
        assertEquals(at, model.focus.panelIndex)
        assertTrue(actions.boxArtCalls.isEmpty())
        press(Action.Enter)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals(listOf("All all"), actions.boxArtCalls)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
        assertEquals(at, model.focus.panelIndex)
    }

    @Test
    fun chooseSystemsWorksHereAndComesBackHere() {
        actions.settings = actions.settings.copy(library = actions.settings.library.copy(boxArtSource = BoxArtSourceSetting.LibretroOnly))
        model.content = content(BoxArtSourceSetting.LibretroOnly).copy(systems = twoSystems)
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.BoxArtSource)
        press(Action.Enter)
        toWork(AccountPageItem.ChooseSystems)
        val at = model.focus.panelIndex
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSystems, model.focus.page)
        press(Action.Enter)
        assertEquals(setOf("snes"), model.focus.chosenSystems)
        repeat(model.content.systems.size) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals("Missing [snes]", actions.boxArtCalls.single())
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
        assertEquals(at, model.focus.panelIndex)
        assertEquals(emptySet<String>(), model.focus.chosenSystems)
    }

    @Test
    fun aPauseLeftBehindCanBeLiftedHere() {
        val paused = ScrapeStatus.PausedByPerson(ScrapeCounts(300, 2_054, 0))
        assertTrue(AccountPageItem.PauseOrResume in work(content(BoxArtSourceSetting.LibretroOnly, boxArt = paused)))
    }

    @Test
    fun ownCoversOffersOnlyClearBoxArtNotADownloadOrARegionThatWouldDoNothing() {
        assertEquals(listOf(AccountPageItem.ClearBoxArt), work(content(BoxArtSourceSetting.OwnCovers)))
        assertEquals("nothing chosen, signed out: own covers", listOf(AccountPageItem.ClearBoxArt), work(content(null)))
    }

    @Test
    fun screenScraperWithNobodySignedInOffersOnlyClearBoxArt() {
        assertEquals(listOf(AccountPageItem.ClearBoxArt), work(content(BoxArtSourceSetting.ScreenScraper)))
    }

    @Test
    fun aBuildWithoutCredentialsGetsTheSameAsSignedOut() {
        val build = AccountStatus.NotInThisBuild
        assertEquals(work(content(BoxArtSourceSetting.LibretroOnly)), work(content(BoxArtSourceSetting.LibretroOnly, build)))
        assertEquals(work(content(BoxArtSourceSetting.OwnCovers)), work(content(BoxArtSourceSetting.OwnCovers, build)))
        assertEquals("two choices, then the work", 2 + 4, boxArtPanelEntries(content(BoxArtSourceSetting.LibretroOnly, build)).size)
    }

    @Test
    fun aUsableAccountKeepsAllOfItInScreenScrapersPanel() {
        for (source in listOf(null, BoxArtSourceSetting.ScreenScraper, BoxArtSourceSetting.LibretroOnly, BoxArtSourceSetting.OwnCovers)) {
            assertEquals("$source", emptyList<AccountPageItem>(), work(content(source, usable)))
        }
    }

    @Test
    fun aRefusedAccountOnScreenScraperKeepsItsPanelAndOnAnotherSourceGetsTheWork() {
        assertEquals(emptyList<AccountPageItem>(), work(content(BoxArtSourceSetting.ScreenScraper, refused)))
        assertEquals(work(content(BoxArtSourceSetting.LibretroOnly)), work(content(BoxArtSourceSetting.LibretroOnly, refused)))
        assertFalse("its own panel has none of it", AccountPageItem.ClearBoxArt in accountPageItems(content(BoxArtSourceSetting.LibretroOnly, refused)))
    }

    @Test
    fun aOnDownloadMissingAsksForTheMissingGamesAndStaysInThePanel() {
        openPanel(BoxArtSourceSetting.LibretroOnly)
        toWork(AccountPageItem.DownloadMissing)
        press(Action.Enter)
        assertEquals(listOf("Missing all"), actions.boxArtCalls)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
    }

    @Test
    fun aFailedDownloadIsSaidInThisPanel() {
        actions.boxArtWorks = false
        openPanel(BoxArtSourceSetting.LibretroOnly, boxArt = ScrapeStatus.Failed(ScrapeCounts(300, 2_054, 0), PictureFailure.NoNetwork))
        toWork(AccountPageItem.DownloadMissing)
        press(Action.Enter)
        assertEquals(SettingsOption.BoxArtSource to SettingsNotice.BoxArtAskFailed, model.work.notice)
    }

    @Test
    fun clearAsksFirstWithCancelFocusedAndCancelOrBackReturnToTheItem() {
        openPanel(BoxArtSourceSetting.OwnCovers)
        toWork(AccountPageItem.ClearBoxArt)
        val at = model.focus.panelIndex
        press(Action.Enter)
        assertEquals(PanelPage.ScraperClearConfirm, model.focus.page)
        assertEquals(ClearConfirmItem.Cancel.ordinal, model.focus.panelIndex)
        assertEquals(SettingsOption.BoxArtSource, model.focusedOption)
        press(Action.Enter)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
        assertEquals(at, model.focus.panelIndex)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
        assertEquals(at, model.focus.panelIndex)
        assertTrue(actions.boxArtCalls.none { it == "clear" })
    }

    @Test
    fun theConfirmClearsOnceAndAFailureIsSaidHere() {
        actions.clearResult = BoxArtClearResult.InUse
        openPanel(BoxArtSourceSetting.LibretroOnly)
        toWork(AccountPageItem.ClearBoxArt)
        press(Action.Enter)
        move(Direction.Up)
        assertEquals(ClearConfirmItem.Clear.ordinal, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(listOf("clear"), actions.boxArtCalls)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
        assertEquals(SettingsOption.BoxArtSource to SettingsNotice.BoxArtClearInUse, model.work.notice)
    }

    @Test
    fun aStepsTheRegionWrappingAndWritesNothingElse() {
        openPanel(BoxArtSourceSetting.LibretroOnly)
        toWork(AccountPageItem.BoxArtRegion)
        val first = actions.settings.library.boxArtRegion
        repeat(BoxArtRegionSetting.entries.size - 1) { press(Action.Enter) }
        assertTrue(actions.settings.library.boxArtRegion != first)
        press(Action.Enter)
        assertEquals("a full turn comes back", first, actions.settings.library.boxArtRegion)
        assertEquals(PanelPage.BoxArtSource, model.focus.page)
    }

    @Test
    fun touchFocusesThenActs() {
        actions.settings = actions.settings.copy(library = actions.settings.library.copy(boxArtSource = BoxArtSourceSetting.LibretroOnly))
        model.content = content(BoxArtSourceSetting.LibretroOnly)
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.BoxArtSource)
        val at = boxArtPanelEntries(model.content).indexOf(BoxArtPanelEntry.Work(AccountPageItem.DownloadMissing))
        model.onTapPanelItem(at, actions.shown, wide)
        assertEquals("a tap on the previewed panel enters it on the item", at, model.focus.panelIndex)
        assertTrue(actions.boxArtCalls.isEmpty())
        model.onTapPanelItem(at, actions.shown, wide)
        assertEquals(listOf("Missing all"), actions.boxArtCalls)
    }

    @Test
    fun aChoiceStillAppliesAndClosesAndTheItemsBelowDoNotShiftIt() {
        openPanel(BoxArtSourceSetting.LibretroOnly)
        val own = boxArtSourceChoices(model.content.account).indexOf(BoxArtSourceSetting.OwnCovers)
        while (model.focus.panelIndex < own) move(Direction.Down)
        while (model.focus.panelIndex > own) move(Direction.Up)
        press(Action.Enter)
        assertEquals(listOf("source OwnCovers"), actions.boxArtCalls)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun choosingBoxArtAndGameInfoSignedOutStillOpensTheForm() {
        openPanel(BoxArtSourceSetting.LibretroOnly)
        while (model.focus.panelIndex > 0) move(Direction.Up)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperSignIn, model.focus.page)
        assertEquals(SettingsOption.BoxArtSource, model.focusedOption)
        assertTrue("nothing is stored before it signs in", actions.boxArtCalls.isEmpty())
    }

    @Test
    fun theCursorFollowsItsItemAndStaysInsideWhenTheWorkChanges() {
        openPanel(BoxArtSourceSetting.LibretroOnly)
        toWork(AccountPageItem.BoxArtRegion)
        model.content = content(BoxArtSourceSetting.LibretroOnly, boxArt = noGames)
        assertEquals("Download missing left: still on the region", BoxArtPanelEntry.Work(AccountPageItem.BoxArtRegion), boxArtPanelEntries(model.content)[model.focus.panelIndex])
        model.content = content(BoxArtSourceSetting.OwnCovers)
        assertEquals(boxArtPanelEntries(model.content).lastIndex, model.focus.panelIndex)
    }

    @Test
    fun aSignedInPersonsScreenScraperPanelIsUntouched() {
        model.content = content(BoxArtSourceSetting.ScreenScraper, usable)
        openSection(SettingsSection.Accounts)
        focusOption(SettingsOption.ScraperAccount)
        press(Action.Enter)
        assertEquals(PanelPage.ScraperAccount, model.focus.page)
        val clear = accountPageItems(model.content).indexOf(AccountPageItem.ClearBoxArt)
        repeat(clear) { move(Direction.Down) }
        press(Action.Enter)
        press(Action.Back)
        assertEquals("its confirm goes back to its own panel", PanelPage.ScraperAccount, model.focus.page)
        assertEquals(clear, model.focus.panelIndex)
        assertNull(model.work.notice)
    }
}
