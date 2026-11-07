package com.plumemu.settings.screen

import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every card of the Licences list, however tall, stands whole in the list's window when focused, walked to or opened on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsLicenseCardWholeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry

    private fun show(index: Int) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                geometry = settingsGeometry(PlumTheme.layout)
                model = remember { SettingsModel(NoActions, panelAt(SettingsOption.Licenses, PanelPage.Licenses, index)).also { it.content = SettingsContent(gameCount = 1) } }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun problemWith(index: Int): String? {
        val card = bounds(SettingsTags.panelItem(index))
        val window = bounds(SettingsTags.LICENSE_LIST)
        val panel = bounds(SettingsTags.PANEL)
        return when {
            card.top.value < window.top.value - ROUNDING -> "card $index starts at ${card.top}, above its list's top ${window.top}"
            card.top.value < panel.top.value - ROUNDING -> "card $index starts at ${card.top}, above the panel's top ${panel.top}"
            card.bottom.value > window.bottom.value + ROUNDING -> "card $index ends at ${card.bottom}, past its list's end ${window.bottom}"
            else -> null
        }
    }

    @Test
    fun walkedToTheLastLicenceEveryCardIsWhole() {
        show(0)
        val problems = mutableListOf<String>()
        for (index in 0 until LicenseNotices.all.size) {
            problemWith(index)?.let { problems += it }
            compose.runOnIdle { model.onAction(Action.Move(Direction.Down), settings, geometry) }
            compose.waitForIdle()
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun openedOnTheLastLicenceItsCardIsWhole() {
        val last = LicenseNotices.all.lastIndex
        show(last)
        assertTrue(problemWith(last).toString(), problemWith(last) == null)
    }

    private companion object {
        /** A row's place is rounded to whole px at xhdpi. */
        const val ROUNDING = 0.5f
    }
}
