package com.plumemu.settings.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A finger on Check for updates daily: one tap changes it, as it does every other switch. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsDailySwitchTouchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)

    private class Writes : SettingsActions by NoActions {
        var settings: PlumSettings = PlumSettings()
        var writes = 0

        override fun update(transform: (PlumSettings) -> PlumSettings) {
            settings = transform(settings)
            writes++
        }
    }

    private fun tapDaily(focus: SettingsFocus): Writes {
        val actions = Writes()
        val model = SettingsModel(actions, focus)
        model.content = SettingsContent()
        val daily = updatesPanelItems(model.content.update).indexOf(UpdatePanelItem.CheckDaily)
        val touch = SettingsTouch({}, {}, {}, { model.onTapPanelItem(it, actions.settings, geometry) }, {}, {}, {})
        compose.setContent { SettingsLayout(actions.settings, model, touch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" }) }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.panelItem(daily), useUnmergedTree = true).performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        return actions
    }

    /** A backup holding "check daily off" restores, and the switch in Version's panel shows off. */
    @Test
    fun aRestoredBackupWithTheDailyCheckOffShowsTheSwitchOff() {
        val stored = PlumSettings.Defaults.copy(updates = PlumSettings.Defaults.updates.copy(checkDaily = false))
        val restored = com.plumemu.coredata.backup.SettingsJson.decode(com.plumemu.coredata.backup.SettingsJson.encode(stored).toString()).settings
        assertEquals(false, restored.updates.checkDaily)
        val model = SettingsModel(Writes(), panelAt(SettingsOption.Version, PanelPage.Update, 0))
        model.content = SettingsContent()
        val daily = updatesPanelItems(model.content.update).indexOf(UpdatePanelItem.CheckDaily)
        compose.setContent { SettingsLayout(restored, model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" }) }
        compose.waitForIdle()
        compose.onNodeWithTag(SettingsTags.panelItem(daily), useUnmergedTree = true).performScrollTo()
        // The panel row is drawn from the restored settings: its value is the switch's stored state, off.
        assertEquals(OptionValue.Switch(false), OptionValue.Switch(SettingsOption.CheckUpdatesDaily.switchValue(restored)))
        compose.onNodeWithTag(SettingsTags.panelItem(daily), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun oneTapInTheOpenPanel() = assertEquals(1, tapDaily(panelAt(SettingsOption.Version, PanelPage.Update, 0)).writes)

    @Test
    fun oneTapInThePanelBesideTheRow() = assertEquals(1, tapDaily(optionsAt(SettingsOption.Version)).writes)
}
