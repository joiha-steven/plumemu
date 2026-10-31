package com.plumemu.settings.screen

import com.plumemu.account.AccountShots
import com.plumemu.account.AccountStatus
import com.plumemu.account.UtcTimeZone
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import com.plumemu.scraper.AccountLimits
import com.plumemu.scraper.PauseReason
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Library › ScreenScraper's box art: the account
 * row's short forms, the panel in every state, the Re-download confirm and the systems picker.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsBoxArtScreenshotTest {
    @get:Rule
    val utc = UtcTimeZone()

    private fun shot(
        name: String,
        boxArt: ScrapeStatus,
        page: PanelPage? = null,
        account: AccountStatus = BoxArtShots.SIGNED_IN,
        content: SettingsContent = BoxArtShots.content(account, boxArt),
        tune: (SettingsFocus) -> SettingsFocus = { it },
    ) = ReferenceShots.capture(name) {
        SettingsAt(tune(AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, page)), settingsForShot(), content)
    }

    // The account row's value (the list, the row focused).

    @Test fun rowRunning() = shot("settings_box_art_row_running", BoxArtShots.running)

    @Test fun rowPaused() = shot("settings_box_art_row_paused", ScrapeStatus.PausedByPerson(BoxArtShots.counts))

    @Test fun rowWaitingForWifi() = shot("settings_box_art_row_waiting_wifi", ScrapeStatus.WaitingForWifi(BoxArtShots.counts))

    @Test fun rowDailyLimit() = shot("settings_box_art_row_daily_limit", BoxArtShots.dailyLimit)

    @Test fun rowSignInAgain() = shot("settings_box_art_row_sign_in_again", ScrapeStatus.Refused, account = BoxArtShots.REFUSED)

    @Test fun rowDone() = shot("settings_box_art_row_done", BoxArtShots.done)

    // The panel, Download missing focused.

    @Test fun panelRunning() = shot("settings_box_art_panel_running", BoxArtShots.running, PanelPage.ScraperAccount)

    @Test fun panelRunningNoEstimate() = shot("settings_box_art_panel_running_no_estimate", ScrapeStatus.Running(BoxArtShots.counts, null), PanelPage.ScraperAccount)

    @Test fun panelPaused() = shot("settings_box_art_panel_paused", ScrapeStatus.PausedByPerson(BoxArtShots.counts), PanelPage.ScraperAccount)

    @Test fun panelWaitingForWifi() = shot("settings_box_art_panel_waiting_wifi", ScrapeStatus.WaitingForWifi(BoxArtShots.counts), PanelPage.ScraperAccount)

    @Test fun panelWaitingForBattery() = shot("settings_box_art_panel_waiting_battery", ScrapeStatus.WaitingForBattery(BoxArtShots.counts), PanelPage.ScraperAccount)

    @Test fun panelDailyLimit() = shot("settings_box_art_panel_daily_limit", BoxArtShots.dailyLimit, PanelPage.ScraperAccount)

    @Test fun panelWrongPassword() = shot("settings_box_art_panel_wrong_password", ScrapeStatus.Refused, PanelPage.ScraperAccount, account = BoxArtShots.REFUSED)

    @Test fun panelRetrying() = shot("settings_box_art_panel_retrying", ScrapeStatus.Retrying(BoxArtShots.counts, AccountShots.RESUME_AT_MILLIS), PanelPage.ScraperAccount)

    @Test fun panelStarting() = shot("settings_box_art_panel_starting", ScrapeStatus.Starting(BoxArtShots.counts), PanelPage.ScraperAccount)

    @Test fun panelDone() = shot("settings_box_art_panel_done", BoxArtShots.done, PanelPage.ScraperAccount)

    @Test
    fun panelDoneGivenUp() =
        shot("settings_box_art_panel_done_given_up", BoxArtShots.done, PanelPage.ScraperAccount, content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.done).copy(boxArtGivenUp = 12))

    @Test fun panelEmptyLibrary() = shot("settings_box_art_panel_empty", BoxArtShots.empty, PanelPage.ScraperAccount, content = BoxArtShots.emptyContent)

    // The panel with the account's limits under the status line.

    @Test
    fun panelRunningWithLimits() =
        shot("settings_box_art_panel_running_limits", BoxArtShots.running, PanelPage.ScraperAccount, content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running).copy(accountLimits = BoxArtShots.limits))

    @Test
    fun panelDailyLimitWithLimits() =
        shot("settings_box_art_panel_daily_limit_limits", BoxArtShots.dailyLimit, PanelPage.ScraperAccount, content = BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.dailyLimit).copy(accountLimits = BoxArtShots.limitsUsedUp))

    // The confirm (Cancel focused) and the picker.

    @Test fun confirmAll() = shot("settings_box_art_confirm_all", BoxArtShots.running, PanelPage.ScraperConfirm) { it.copy(panelIndex = BoxArtConfirmItem.Cancel.ordinal) }

    @Test
    fun confirmSystems() =
        shot("settings_box_art_confirm_systems", BoxArtShots.running, PanelPage.ScraperConfirm, content = BoxArtShots.longSystems) {
            it.copy(panelIndex = BoxArtConfirmItem.Cancel.ordinal, redownloadSystems = setOf("nes", "fds"))
        }

    @Test fun confirmSignOut() = shot("settings_box_art_confirm_sign_out", BoxArtShots.running, PanelPage.ScraperSignOutConfirm) { it.copy(panelIndex = SignOutConfirmItem.Cancel.ordinal) }

    @Test fun confirmClear() = shot("settings_box_art_confirm_clear", BoxArtShots.running, PanelPage.ScraperClearConfirm) { it.copy(panelIndex = ClearConfirmItem.Cancel.ordinal) }

    @Test fun systemsNoneChosen() = shot("settings_box_art_systems_none", BoxArtShots.running, PanelPage.ScraperSystems, content = BoxArtShots.longSystems)

    @Test
    fun systemsSomeChosen() =
        shot("settings_box_art_systems_some", BoxArtShots.running, PanelPage.ScraperSystems, content = BoxArtShots.longSystems) {
            it.copy(panelIndex = 0, chosenSystems = setOf("nes", "fds"))
        }

    // A focused action at the end of the picker is scrolled into view one frame after the first,
    // which a reference shot does not wait for: TextSizeScreensTest's
    // textsize_largest_settings_box_art_systems_action shots show it, and check it is on screen.
}

/** The library of the shots with box art in a state. */
internal object BoxArtShots {
    val SIGNED_IN = AccountStatus.SignedIn("plumtester")
    val REFUSED = AccountStatus.SignedIn("plumtester", isRefused = true)

    val counts = ScrapeCounts(withBoxFront = 300, listed = 2_054, pending = 1_714)

    /** What ScreenScraper said about the account: 1,234 of 10,000 requests, one download at a time, 128 KB/s. */
    val limits = AccountLimits(maxThreads = 1, maxRequestsPerMinute = 300, requestsToday = 1_234, maxRequestsPerDay = 10_000, maxDownloadKilobytesPerSecond = 128)
    val limitsUsedUp = limits.copy(requestsToday = 10_000)

    /** 1,714 left at 5.7 a minute: "about 5 h left". */
    val running = ScrapeStatus.Running(counts, gamesPerMinute = 5.7)
    val dailyLimit = ScrapeStatus.Paused(counts, AccountShots.RESUME_AT_MILLIS, PauseReason.DailyQuota)
    val done = ScrapeStatus.Idle(ScrapeCounts(withBoxFront = 2_012, listed = 2_054, pending = 0))
    val empty = ScrapeStatus.Idle(ScrapeCounts(withBoxFront = 0, listed = 0, pending = 0))

    fun content(
        account: AccountStatus,
        boxArt: ScrapeStatus,
    ): SettingsContent = AccountShotContent.of(account).copy(boxArt = boxArt, boxArtBytes = BOX_ART_BYTES)

    /** The saved pictures' size in the shots: "312 MB". */
    const val BOX_ART_BYTES = 312_000_000L

    val emptyContent: SettingsContent = content(SIGNED_IN, empty).copy(systems = emptyList(), folders = emptyList(), gameCount = 0)

    /** Systems whose names wrap in the narrow panel. */
    val longSystems: SettingsContent =
        content(SIGNED_IN, running).copy(
            systems =
                listOf(
                    system("nes", "Nintendo Entertainment System"),
                    system("fds", "Family Computer Disk System"),
                    system("snes", "Super Nintendo"),
                    system("pce", "PC Engine / TurboGrafx-16 / PC Engine CD-ROM2"),
                    system("psx", "PlayStation"),
                    system("arcade", "Arcade (Final Burn Neo, MAME 2003-Plus and MAME current)"),
                ),
        )

    private fun system(
        id: String,
        name: String,
    ) = SystemEmulators(id, name, automatic = null, chosen = null, installed = emptyList())
}
