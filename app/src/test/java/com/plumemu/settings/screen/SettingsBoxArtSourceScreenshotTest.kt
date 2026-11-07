package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.account.UtcTimeZone
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Accounts › Box art: the row, and its list of the three choices with their lines, at both reference sizes and looks. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsBoxArtSourceScreenshotTest {
    @get:Rule
    val utc = UtcTimeZone()

    private fun shot(
        name: String,
        account: AccountStatus,
        source: BoxArtSourceSetting?,
        page: PanelPage?,
        panelIndex: Int = 0,
        boxArt: ScrapeStatus = ScrapeStatus.NotInThisBuild,
    ) = ReferenceShots.capture(name) {
        val content = AccountShotContent.of(account).copy(chosenBoxArtSource = source, boxArt = boxArt)
        val settings = settingsForShot().let { it.copy(library = it.library.copy(boxArtSource = source)) }
        SettingsAt(AccountShotContent.focusOn(content, SettingsOption.BoxArtSource, page).copy(panelIndex = panelIndex), settings, content)
    }

    /** The row focused: its value names the source in force. */
    @Test fun row() = shot("settings_box_art_source_row", AccountStatus.SignedOut, BoxArtSourceSetting.LibretroOnly, page = null)

    /** No account, nothing chosen: Plumemu's own covers are current; the cursor on Box art only. */
    @Test fun panelSignedOut() = shot("settings_box_art_source_panel", AccountStatus.SignedOut, source = null, page = PanelPage.BoxArtSource, panelIndex = 1)

    /** Box art only with no account: how the run is going, and under the choices the work it allows; the cursor on Download missing. */
    @Test fun panelBoxArtOnly() =
        shot("settings_box_art_source_panel_libretro", AccountStatus.SignedOut, BoxArtSourceSetting.LibretroOnly, PanelPage.BoxArtSource, panelIndex = 3, boxArt = ScrapeStatus.Idle(ScrapeCounts(300, 412, 0)))

    /** Clear box art's confirm under own covers says nothing of a download or of an account. */
    @Test fun clearConfirmOwnCovers() =
        shot("settings_box_art_source_clear_own", AccountStatus.SignedOut, BoxArtSourceSetting.OwnCovers, PanelPage.ScraperClearConfirm, panelIndex = ClearConfirmItem.Cancel.ordinal)

    /** Signed in: ScreenScraper is current and the cursor is on it. */
    @Test fun panelSignedIn() = shot("settings_box_art_source_panel_signed_in", AccountStatus.SignedIn("plumtester"), source = null, page = PanelPage.BoxArtSource)
}
