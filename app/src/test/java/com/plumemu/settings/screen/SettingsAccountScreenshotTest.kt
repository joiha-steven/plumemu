package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.account.AccountShots
import com.plumemu.account.UtcTimeZone
import com.plumemu.account.AccountStatus
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Library's ScreenScraper rows: signed out, the
 * sign-in form in each state, signed in, wrong password, the daily limit, not available in this
 * build, and the box art region. A wrong password and the daily limit are the account
 * row's value (no status row); the run's states are in SettingsBoxArtScreenshotTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsAccountScreenshotTest {
    @get:Rule
    val utc = UtcTimeZone()

    private fun shot(
        name: String,
        account: AccountStatus,
        option: SettingsOption,
        page: PanelPage? = null,
    ) = ReferenceShots.capture(name) {
        val content = AccountShotContent.of(account)
        SettingsAt(AccountShotContent.focusOn(content, option, page), settingsForShot(), content)
    }

    private fun formShot(
        name: String,
        state: AccountShots.State,
    ) = ReferenceShots.capture(name) { SignInFormAt(state) }

    @Test fun signedOut() = shot("settings_account_signed_out", AccountStatus.SignedOut, SettingsOption.ScraperAccount)

    @Test fun signedIn() = shot("settings_account_signed_in", AccountStatus.SignedIn("plumtester"), SettingsOption.ScraperAccount)

    @Test fun signedInPanel() = shot("settings_account_sign_out", AccountStatus.SignedIn("plumtester"), SettingsOption.ScraperAccount, PanelPage.ScraperAccount)

    @Test fun wrongPassword() = shot("settings_account_refused", AccountStatus.SignedIn("plumtester", isRefused = true), SettingsOption.ScraperAccount)

    @Test
    fun wrongPasswordPanel() =
        shot("settings_account_refused_panel", AccountStatus.SignedIn("plumtester", isRefused = true), SettingsOption.ScraperAccount, PanelPage.ScraperAccount)

    @Test
    fun dailyLimit() =
        shot("settings_account_daily_limit", AccountStatus.SignedIn("plumtester", dailyLimitUntilMillis = AccountShots.RESUME_AT_MILLIS), SettingsOption.ScraperAccount)

    @Test fun notAvailable() = shot("settings_account_not_available", AccountStatus.NotInThisBuild, SettingsOption.ScraperStatus)

    @Test fun region() = shot("settings_box_art_region", AccountStatus.SignedIn("plumtester"), SettingsOption.BoxArtRegion)

    @Test fun formEmpty() = formShot("settings_sign_in_empty", AccountShots.State.Empty)

    @Test fun formChecking() = formShot("settings_sign_in_checking", AccountShots.State.Checking)

    @Test fun formWrong() = formShot("settings_sign_in_wrong", AccountShots.State.Wrong)

    @Test fun formOffline() = formShot("settings_sign_in_offline", AccountShots.State.Offline)

    @Test fun formClosed() = formShot("settings_sign_in_closed", AccountShots.State.Closed)

    @Test fun formDailyLimit() = formShot("settings_sign_in_daily_limit", AccountShots.State.DailyLimit)

    @Test fun formServerError() = formShot("settings_sign_in_server_error", AccountShots.State.ServerError)
}

/** The board's library with an account state, and the focus on one of its rows. */
internal object AccountShotContent {
    fun of(account: AccountStatus): SettingsContent = ShotContent.library.copy(account = account)

    fun focusOn(
        content: SettingsContent,
        option: SettingsOption,
        page: PanelPage? = null,
    ): SettingsFocus {
        val row = SettingsSection.Library.rowsIn(content).indexOf(option)
        return SettingsFocus(
            column = if (page == null) SettingsColumn.Options else SettingsColumn.Panel,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to row),
            page = page,
        )
    }
}

/** Settings with the sign-in form open in the account row's panel, in [state]. */
@Composable
internal fun SignInFormAt(state: AccountShots.State) {
    val content = AccountShotContent.of(AccountStatus.SignedOut)
    val focus = AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, PanelPage.ScraperSignIn)
    val model =
        remember(state) {
            SettingsModel(NoActions, focus).also {
                it.content = content
                it.signIn.open(AccountShots.form(state, stacked = true))
            }
        }
    CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
        SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
    }
}
