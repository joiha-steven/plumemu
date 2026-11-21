package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The model walks the presets by the rows the colour page draws: Down from a preset lands on the swatch drawn below it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsColourPresetRowsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var model: SettingsModel
    private lateinit var geometry: SettingsGeometry
    private lateinit var settings: PlumSettings

    @Composable
    private fun Page() {
        settings = settingsForShot()
        geometry = settingsGeometry(PlumTheme.layout)
        model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Background)).also {
                    it.onAction(Action.Enter, settings, geometry)
                    // Up from where the page opens to the presets' first row, then Left to the first preset.
                    var guard = 0
                    while (it.colourPart != ColorPart.Preset && guard++ < 10) it.onAction(Action.Move(Direction.Up), settings, geometry)
                    guard = 0
                    while (it.focus.panelIndex > 0 && guard++ < 40) it.onAction(Action.Move(Direction.Up), settings, geometry)
                }
            }
        SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private fun check() {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { Page() } }
        compose.waitForIdle()
        assertEquals("the cursor is on the first preset", 0, model.focus.panelIndex)
        val first = compose.onNodeWithTag(SettingsTags.panelItem(0)).getUnclippedBoundsInRoot()
        compose.runOnIdle { model.onAction(Action.Move(Direction.Down), settings, geometry) }
        compose.waitForIdle()
        val at = model.focus.panelIndex
        assertEquals("the focused item is a preset", ColorPart.Preset, model.colourPart)
        val below = compose.onNodeWithTag(SettingsTags.panelItem(at)).getUnclippedBoundsInRoot()
        assertTrue("item $at is drawn on the row below (top ${below.top} vs ${first.top})", below.top > first.top)
        assertEquals("and in the same column (left ${below.left} vs ${first.left})", first.left, below.left)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun downFromTheFirstPresetReachesTheSwatchBelowAt768() = check()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun downFromTheFirstPresetReachesTheSwatchBelowAt576() = check()
}
