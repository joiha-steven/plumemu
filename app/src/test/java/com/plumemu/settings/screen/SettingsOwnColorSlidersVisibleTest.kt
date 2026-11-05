package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** "Your own color": with the focus on each slider in turn, that row lies wholly inside the scrolling area (the panel scrolls it in). */
abstract class SettingsOwnColorSlidersVisibleBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var swatchCount = 0

    @Composable
    private fun Page(slider: ColorSlider, toSwatch: Boolean = false) {
        val base = settingsForShot()
        val settings: PlumSettings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Background)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    val presets = PanelPage.BackgroundPresets
                    val plus = ColorPages.presetCount(presets, settings.toThemeSettings().backgroundColor) - 1
                    model.onTapPanelItem(plus, settings, geometry)
                    if (model.focus.page == presets) model.onTapPanelItem(plus, settings, geometry)
                    val swatches = ColorPages.palette(model.focus.page!!, settings.toThemeSettings().backgroundColor).size
                    swatchCount = swatches
                    var guard = 0
                    while (!toSwatch && model.focus.panelIndex < swatches + slider.ordinal && guard++ < 30) model.onAction(Action.Move(Direction.Down), settings, geometry)
                }
            }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    /** The page opens at its top; when every row fits the viewport, all of them (the sliders and the hex row included) are visible then. */
    @Test
    fun thePageOpensAtItsTopAndShowsEveryRowWhenTheyFit() {
        compose.setContent { PlumTheme(ReferenceShots.Preset.Graphite.settings) { Page(ColorSlider.Hue, toSwatch = true) } }
        compose.waitForIdle()
        val scroll = compose.onNodeWithTag(SettingsTags.COLOR_SCROLL, useUnmergedTree = true).fetchSemanticsNode()
        val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
        val area = compose.onNodeWithTag(SettingsTags.COLOR_SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("opens at the top (max scroll ${range.maxValue()})", 0f, range.value(), 0.5f)
        if (range.maxValue() > 0f) return
        for (slider in ColorSlider.entries) {
            val row = compose.onNodeWithTag(SettingsTags.panelItem(swatchCount + slider.ordinal), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertTrue("${slider.name} ${row.top}..${row.bottom} is inside ${area.top}..${area.bottom}", row.top >= area.top && row.bottom <= area.bottom)
        }
    }

    @Test
    fun everySliderRowIsInsideTheScrollingAreaWhenFocused() {
        var look by mutableStateOf(Pair(ReferenceShots.Preset.Graphite, ColorSlider.Hue))
        compose.setContent { PlumTheme(look.first.settings) { key(look) { Page(look.second) } } }
        for (preset in ReferenceShots.Preset.entries) {
            for (slider in ColorSlider.entries) {
                look = Pair(preset, slider)
                compose.waitForIdle()
                val area = compose.onNodeWithTag(SettingsTags.COLOR_SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val row = compose.onNodeWithTag(SettingsTags.panelItem(swatchCount + slider.ordinal), useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue(
                    "${preset.name} ${slider.name} focused: row ${row.top}..${row.bottom} lies inside the area ${area.top}..${area.bottom}",
                    row.top >= area.top && row.bottom <= area.bottom,
                )
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w853dp-h480dp-land-360dpi")
class SettingsOwnColorSliders853VisibleTest : SettingsOwnColorSlidersVisibleBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsOwnColorSliders768VisibleTest : SettingsOwnColorSlidersVisibleBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsOwnColorSliders576VisibleTest : SettingsOwnColorSlidersVisibleBase()
