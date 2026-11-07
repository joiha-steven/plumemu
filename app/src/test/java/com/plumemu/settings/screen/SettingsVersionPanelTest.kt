package com.plumemu.settings.screen

import androidx.compose.runtime.remember
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
import com.plumemu.platforms.PlatformDataVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** About › Version with the platform data's lines at Largest: where they run past the panel, the gamepad reaches the last one. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsVersionPanelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val settings = PlumSettings(flags = SetupFlags(setupCompleted = true))
    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry

    private fun press(action: Action) = compose.runOnIdle { model.onAction(action, settings, geometry) }

    private fun show(textSize: TextSize = TextSize.Largest) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize)) {
                geometry = settingsGeometry(PlumTheme.layout)
                model = remember { SettingsModel(NoActions, optionsAt(SettingsOption.Version)).also { it.content = SettingsContent(platformData = PlatformDataVersion(6, "0".repeat(64), "2026-11-01")) } }
                SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
            }
        }
        compose.waitForIdle()
    }

    private fun assertTheKeysReachTheEnd() {
        repeat(LAST_LINE_PRESSES) { press(Action.Move(Direction.Down)) }
        compose.waitForIdle()
        assertFalse("nothing is left below", model.licenseCanScroll(true))
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theLinesPastTheWidePanelAreReachedByKeys() {
        show()
        press(Action.Move(Direction.Right))
        assertEquals("Right enters the panel", PanelPage.ReadOnly, model.focus.page)
        compose.waitForIdle()
        assertTrue("this case needs text past the panel", model.licenseCanScroll(true))
        assertTheKeysReachTheEnd()
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Version, model.focusedOption)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theHintOffersMoreAndRightEntersOnlyWhenTheTextRunsPastThePanel() {
        show()
        compose.onNodeWithTag(SettingsTags.HINT_MORE, useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun withTextThatFitsNoMoreIsOfferedAndRightKeepsTheFocus() {
        show(TextSize.Small)
        compose.onNodeWithTag(SettingsTags.HINT_MORE, useUnmergedTree = true).assertDoesNotExist()
        press(Action.Move(Direction.Right))
        compose.waitForIdle()
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(null, model.focus.page)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun onANarrowScreenTheStopReachesTheLastLine() {
        show()
        press(Action.Move(Direction.Right))
        assertEquals(PanelPage.ReadOnly, model.focus.page)
        assertTheKeysReachTheEnd()
    }

    private companion object {
        const val LAST_LINE_PRESSES = 6
    }
}
