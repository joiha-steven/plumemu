package com.plumemu.settings.screen

import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Accounts › RetroAchievements › Sign out's confirm, Cancel focused: at 832x468, 768x432 and 576x432, Graphite and Paper, and at Largest. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsRaSignOutShotTest {
    private fun shot(
        name: String,
        textSize: TextSize = TextSize.Small,
    ) = ReferenceShots.capture(name, textSize = textSize, sizes = ReferenceShots.Size.WithPocket6) {
        val content = AccountShotContent.of(AccountStatus.SignedOut).copy(raAccount = RaAccountStatus.SignedIn("plumtester", 1234))
        val focus = AccountShotContent.focusOn(content, SettingsOption.RetroAchievementsAccount, PanelPage.RaSignOutConfirm)
        SettingsAt(focus.copy(panelIndex = SignOutConfirmItem.Cancel.ordinal), settingsForShot(), content)
    }

    @Test fun confirm() = shot("settings_ra_confirm_sign_out")

    @Test fun confirmLargest() = shot("settings_ra_confirm_sign_out", TextSize.Largest)
}
