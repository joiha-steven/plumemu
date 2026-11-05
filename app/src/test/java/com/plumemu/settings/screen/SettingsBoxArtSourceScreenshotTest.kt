package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.account.UtcTimeZone
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreui.testing.ReferenceShots
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
    ) = ReferenceShots.capture(name) {
        val content = AccountShotContent.of(account)
        val settings = settingsForShot().let { it.copy(library = it.library.copy(boxArtSource = source)) }
        SettingsAt(AccountShotContent.focusOn(content, SettingsOption.BoxArtSource, page).copy(panelIndex = panelIndex), settings, content)
    }

    /** The row focused: its value names the source in force. */
    @Test fun row() = shot("settings_box_art_source_row", AccountStatus.SignedOut, BoxArtSourceSetting.LibretroOnly, page = null)

    /** No account, nothing chosen: Plumemu's own covers are current; the cursor on Box art only. */
    @Test fun panelSignedOut() = shot("settings_box_art_source_panel", AccountStatus.SignedOut, source = null, page = PanelPage.BoxArtSource, panelIndex = 1)

    /** Signed in: ScreenScraper is current and the cursor is on it. */
    @Test fun panelSignedIn() = shot("settings_box_art_source_panel_signed_in", AccountStatus.SignedIn("plumtester"), source = null, page = PanelPage.BoxArtSource)
}
