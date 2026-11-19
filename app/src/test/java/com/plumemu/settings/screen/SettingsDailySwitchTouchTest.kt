package com.plumemu.settings.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
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

/** Check for updates daily is About's row under Version: one tap or one A changes it, as it does every other switch. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsDailySwitchTouchTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)
    private val content = SettingsContent(hasUpdates = true)

    private class Writes : SettingsActions by NoActions {
        var settings: PlumSettings = PlumSettings()
        var writes = 0

        override fun update(transform: (PlumSettings) -> PlumSettings) {
            settings = transform(settings)
            writes++
        }
    }

    private fun shown(focus: SettingsFocus): Pair<Writes, SettingsModel> {
        val actions = Writes()
        val model = SettingsModel(actions, focus)
        model.content = content
        val touch = SettingsTouch({}, { model.onTapOption(it, actions.settings, geometry) }, { model.onTapValue(it, actions.settings, geometry) }, {}, {}, {}, {})
        compose.setContent { SettingsLayout(actions.settings, model, touch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" }) }
        compose.waitForIdle()
        return actions to model
    }

    @Test
    fun theRowSitsRightUnderVersion() {
        val rows = SettingsSection.About.rowsIn(content)
        assertEquals(rows.indexOf(SettingsOption.Version) + 1, rows.indexOf(SettingsOption.CheckUpdatesDaily))
        assertEquals(OptionKind.Switch, SettingsOption.CheckUpdatesDaily.kind)
    }

    @Test
    fun oneTapOnTheRowChangesItOnce() {
        val (actions, _) = shown(optionsAt(SettingsOption.Version))
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.CheckUpdatesDaily), useUnmergedTree = true).performScrollTo().performTouchInput { click() }
        compose.waitForIdle()
        assertEquals(1, actions.writes)
        assertEquals(false, actions.settings.updates.checkDaily)
    }

    @Test
    fun oneAOnTheRowChangesItOnce() {
        val (actions, model) = shown(optionsAt(SettingsOption.CheckUpdatesDaily))
        model.onAction(Action.Enter, actions.settings, geometry)
        compose.waitForIdle()
        assertEquals(1, actions.writes)
        assertEquals(false, actions.settings.updates.checkDaily)
        compose.onNodeWithTag(SettingsTags.option(SettingsOption.CheckUpdatesDaily), useUnmergedTree = true).assertIsDisplayed()
    }

    /** A backup holding "check daily off" restores with the stored key unchanged, and the row shows off. */
    @Test
    fun aRestoredBackupWithTheDailyCheckOffShowsTheRowOff() {
        val stored = PlumSettings.Defaults.copy(updates = PlumSettings.Defaults.updates.copy(checkDaily = false))
        val restored = com.plumemu.coredata.backup.SettingsJson.decode(com.plumemu.coredata.backup.SettingsJson.encode(stored).toString()).settings
        assertEquals(false, restored.updates.checkDaily)
        assertEquals(false, SettingsOption.CheckUpdatesDaily.switchValue(restored))
    }
}
