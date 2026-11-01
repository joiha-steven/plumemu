package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.account.SignInForm
import com.plumemu.account.SignInItem
import com.plumemu.account.SignInKind
import com.plumemu.account.SignInResult
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Accounts › RetroAchievements: the sign-in form empty, with a very long user name, checking and with each
 * answer's line (wrong key, unknown user, no connection, busy, rate-limited), then the signed-in panel, with a long
 * name and with a refused key. At 768x432 and 576x432, Graphite and Paper (ReferenceShots).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsRaAccountScreenshotTest {
    private fun formShot(
        name: String,
        username: String = "",
        result: SignInResult? = null,
        checking: Boolean = false,
    ) = ReferenceShots.capture(name) { RaSignInFormAt(username, result, checking) }

    private fun panelShot(
        name: String,
        status: RaAccountStatus,
    ) = ReferenceShots.capture(name) {
        val content = AccountShotContent.of(AccountStatus.SignedOut).copy(raAccount = status)
        val row = SettingsSection.Accounts.rowsIn(content).indexOf(SettingsOption.RetroAchievementsAccount)
        val focus = SettingsFocus(column = SettingsColumn.Panel, section = SettingsSection.Accounts, rememberedOption = mapOf(SettingsSection.Accounts to row), page = PanelPage.RaAccount)
        SettingsAt(focus, settingsForShot(), content)
    }

    @Test fun formEmpty() = formShot("settings_ra_sign_in_empty")

    @Test fun formLongName() = formShot("settings_ra_sign_in_long_name", username = LONG_NAME)

    @Test fun formChecking() = formShot("settings_ra_sign_in_checking", username = "plumtester", checking = true)

    @Test fun formWrongKey() = formShot("settings_ra_sign_in_wrong_key", username = "plumtester", result = SignInResult.RaWrongKey)

    @Test fun formUnknownUser() = formShot("settings_ra_sign_in_unknown_user", username = "plumtester", result = SignInResult.RaUnknownUser)

    @Test fun formOffline() = formShot("settings_ra_sign_in_offline", username = "plumtester", result = SignInResult.Offline)

    @Test fun formBusy() = formShot("settings_ra_sign_in_busy", username = "plumtester", result = SignInResult.RaBusy)

    @Test fun formRateLimited() = formShot("settings_ra_sign_in_rate_limited", username = "plumtester", result = SignInResult.RaRateLimited)

    @Test fun formNoBrowser() = formShot("settings_ra_sign_in_no_browser", username = "plumtester", result = SignInResult.NoBrowser)

    @Test fun signedIn() = panelShot("settings_ra_signed_in", RaAccountStatus.SignedIn("plumtester", 1234))

    @Test fun signedInLongName() = panelShot("settings_ra_signed_in_long_name", RaAccountStatus.SignedIn(LONG_NAME, 1_234_567))

    @Test fun keyRefused() = panelShot("settings_ra_refused", RaAccountStatus.SignedIn("plumtester", 1234, isKeyRefused = true))

    private companion object {
        const val LONG_NAME = "AVeryLongRetroAchievementsUserNameThatKeepsGoing1234"
    }
}

/** Settings with the RetroAchievements sign-in form open in its panel; [result] null with [checking] shows the checking line. */
@Composable
internal fun RaSignInFormAt(
    username: String,
    result: SignInResult?,
    checking: Boolean,
) {
    val content = AccountShotContent.of(AccountStatus.SignedOut)
    val focus = AccountShotContent.focusOn(content, SettingsOption.RetroAchievementsAccount, PanelPage.ScraperSignIn)
    val model =
        remember(username, result, checking) {
            SettingsModel(NoActions, focus).also {
                it.content = content
                val form = SignInForm(stackedButtons = true, kind = SignInKind.RetroAchievements)
                if (username.isNotEmpty()) form.onUsernameChange(username)
                if (checking || result != null) {
                    form.onPasswordChange("KeyKeyKey123456")
                    form.tap(form.items.indexOf(SignInItem.SignIn))
                    result?.let(form::showResult)
                }
                it.signIn.open(form)
            }
        }
    CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
        SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
    }
}
