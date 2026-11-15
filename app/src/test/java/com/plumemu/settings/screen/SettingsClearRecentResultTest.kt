package com.plumemu.settings.screen

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Advanced › Reset… › Clear Recent…: the line under the panel's title says how it ended (a count, already empty, or failed). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsClearRecentResultTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun clearAnd(cleared: Int?) {
        val actions =
            object : SettingsActions by NoActions {
                override val recent =
                    object : SettingsRecentActions {
                        override fun clearRecent(onDone: (Int?) -> Unit) = onDone(cleared)
                    }
            }
        val model = SettingsModel(actions, panelAt(SettingsOption.Reset, PanelPage.Reset, ResetPanelItem.Recent.ordinal))
        model.content = SettingsContent().withGamesListed()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                SettingsLayout(settingsForShot(), model, NoTouch, TEST_VERSION, { BOARD_CLOCK }, { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
        val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)
        for (action in listOf(Action.Enter, Action.Move(Direction.Right), Action.Enter)) {
            compose.runOnIdle { model.onAction(action, PlumSettings(), geometry) }
            compose.waitForIdle()
        }
    }

    private fun assertNotice(
        cleared: Int?,
        text: String,
    ) {
        clearAnd(cleared)
        compose.onNodeWithTag(SettingsTags.NOTICE, useUnmergedTree = true).assertTextEquals(text)
    }

    @Test
    fun aCountIsSaidUnderTheTitle() = assertNotice(3, "Recent cleared: 3 games removed.")

    @Test
    fun anEmptyRecentSaysSo() = assertNotice(0, "Recent was already empty.")

    @Test
    fun aFailedClearSaysSo() = assertNotice(null, "Plumemu could not clear Recent. Try again.")
}
