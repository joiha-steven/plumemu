package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.graph.RestoreState
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What Restore…'s panel says after a restore that left damaged pictures out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsRestorePanelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun show(restore: RestoreState) {
        val model = SettingsModel(NoActions, panelAt(SettingsOption.Restore, PanelPage.Restore, 0))
        model.content = SettingsContent(account = AccountStatus.SignedOut, restore = restore)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY })
            }
        }
    }

    private fun text(id: Int) = compose.onNodeWithText(context.getString(id))

    private fun damagedLine(count: Int) = compose.onNodeWithText(context.resources.getQuantityString(R.plurals.settings_restore_damaged_pictures, count, count.toString()))

    @Test
    fun oneDamagedPictureIsSaidUnderTheResult() {
        show(RestoreState.Done(isUndo = false, kind = BackupKind.Everything, fromMillis = MADE_AT, damagedMediaFiles = 1))
        damagedLine(1).assertIsDisplayed()
        compose.onNodeWithText("1 box art file was damaged and left out.").assertIsDisplayed()
    }

    @Test
    fun severalDamagedPicturesAreCounted() {
        show(RestoreState.Done(isUndo = false, kind = BackupKind.Everything, fromMillis = MADE_AT, damagedMediaFiles = 3))
        compose.onNodeWithText("3 box art files were damaged and left out.").assertIsDisplayed()
    }

    @Test
    fun noDamagedPicturesNoLine() {
        show(RestoreState.Done(isUndo = false, kind = BackupKind.Everything, fromMillis = MADE_AT))
        damagedLine(1).assertDoesNotExist()
        compose.onNodeWithText("files were damaged", substring = true).assertDoesNotExist()
    }

    private companion object {
        const val MADE_AT = 1_792_756_800_000L
    }
}
