package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The focused row stays on screen: a held key in the options column, and a panel entered far down a long list. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsListFollowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var model: SettingsModel
    private val settings = PlumSettings()
    private val geometry = SettingsGeometry(isNarrow = false, presetColumns = 5, defaultTextSize = TextSizeSetting.Medium)

    private fun show(
        focus: SettingsFocus,
        content: SettingsContent,
    ) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                model = remember { SettingsModel(NoActions, focus).also { it.content = content } }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { null }, battery = { null })
            }
        }
    }

    private fun down() = compose.runOnIdle { model.onAction(Action.Move(Direction.Down), settings, geometry) }

    @Test
    fun aHeldDownKeyInEmulatorsKeepsTheFocusedRowOnScreen() {
        val systems = ShotContent.library.systems
        val content = ShotContent.library.copy(systems = List(40) { systems[it % systems.size].copy(systemId = "sys$it", name = "System $it") })
        show(
            SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Emulators, rememberedOption = mapOf(SettingsSection.Emulators to 0)),
            content,
        )
        compose.mainClock.autoAdvance = false
        // A key repeat every 30 ms, faster than the 180 ms scroll: the scroll must follow the newest focus, not queue.
        repeat(36) { step ->
            down()
            compose.mainClock.advanceTimeBy(16)
            // Never trailing by more than a row while the scroll is in flight (queued scrolls lag more and more).
            if (step >= 12) {
                val row = compose.onNodeWithTag(SettingsTags.system(step + 1)).getBoundsInRoot()
                val list = compose.onNodeWithTag(SettingsTags.OPTION_LIST).getBoundsInRoot()
                assertTrue("row ${step + 1} trails the list's end by ${row.top - list.bottom}", row.top < list.bottom + 42.dp)
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag(SettingsTags.system(36)).assertIsDisplayed()
    }

    @Test
    fun aPanelEnteredFarDownTheListFollowsTheFocus() {
        val games = List(10_000) { SettingsGame(it + 1L, "Game $it", "Super Nintendo") }
        val content = ShotContent.library.copy(hiddenGames = games)
        val index = SettingsSection.Library.rowsIn(content).indexOf(SettingsOption.HiddenGames)
        show(
            SettingsFocus(
                column = SettingsColumn.Panel,
                section = SettingsSection.Library,
                rememberedOption = mapOf(SettingsSection.Library to index),
                page = PanelPage.HiddenGames,
                panelIndex = 500,
            ),
            content,
        )
        compose.onNodeWithTag(SettingsTags.panelItem(500)).assertIsDisplayed()
        repeat(12) {
            down()
            compose.waitForIdle()
        }
        compose.onNodeWithTag(SettingsTags.panelItem(512)).assertIsDisplayed()
    }
}
