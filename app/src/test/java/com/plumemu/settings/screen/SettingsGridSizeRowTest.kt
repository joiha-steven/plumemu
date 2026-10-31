package com.plumemu.settings.screen

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings › Appearance › Grid size on screen: its label, its value and its explanation, and no row without box art. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsGridSizeRowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun text(id: Int) = context.getString(id)

    private fun value(id: Int) = context.getString(R.string.settings_value_choice, text(id))

    /** Settings › Appearance with [option] focused, [account] and [stored] settings. */
    private fun show(
        account: AccountStatus,
        option: SettingsOption,
        stored: PlumSettings = PlumSettings(flags = SetupFlags(setupCompleted = true)),
    ) {
        val content = SettingsContent(account = account)
        val rows = SettingsSection.Appearance.rowsIn(content)
        val focus =
            SettingsFocus(
                column = SettingsColumn.Options,
                section = SettingsSection.Appearance,
                rememberedOption = mapOf(SettingsSection.Appearance to rows.indexOf(option).coerceAtLeast(0)),
            )
        compose.setContent { PlumTheme { SettingsAt(focus, stored, content) } }
    }

    @Test
    fun signedInTheRowShowsMediumAndExplainsItself() {
        // The default; text size at Large and weight Bold, so the one "‹ Medium ›" on screen is the grid size's.
        val stored = PlumSettings(appearance = AppearanceSettings(textSize = TextSizeSetting.Large, textWeight = TextWeightSetting.Bold), flags = SetupFlags(setupCompleted = true))
        show(AccountStatus.SignedIn("plumtester"), SettingsOption.GridSize, stored)
        compose.onAllNodesWithText(text(R.string.settings_grid_size)).assertCountEquals(2) // the row and its panel's title
        compose.onNodeWithText(value(R.string.settings_size_medium)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_grid_size_body)).assertIsDisplayed()
    }

    @Test
    fun aPickedSizeIsTheRowsValue() {
        // Small, not the default, at Large text so the one "‹ Small ›" on screen is the grid size's.
        val stored = PlumSettings(appearance = AppearanceSettings(gridSize = GridSizeSetting.Small, textSize = TextSizeSetting.Large), flags = SetupFlags(setupCompleted = true))
        show(AccountStatus.SignedIn("plumtester"), SettingsOption.GridSize, stored)
        compose.onNodeWithText(value(R.string.settings_size_small)).assertIsDisplayed()
    }

    @Test
    fun withoutAnAccountThereIsNoGridAndNoRow() {
        show(AccountStatus.SignedOut, SettingsOption.TextSize)
        compose.onAllNodesWithText(text(R.string.settings_text_size))[0].assertIsDisplayed()
        compose.onNodeWithText(text(R.string.settings_grid_size)).assertDoesNotExist()
    }
}
