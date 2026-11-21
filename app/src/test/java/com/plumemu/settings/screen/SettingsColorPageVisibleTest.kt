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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The colour page: with the focus on Color, a shade and the Color code row in turn, that row lies wholly inside the scrolling area (the panel scrolls it in). */
abstract class SettingsColorPageVisibleBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** The test tag of the row for [part] on the page being shown. */
    private var tags = mapOf<ColorPart, String>()

    @Composable
    private fun Page(part: ColorPart) {
        val base = settingsForShot()
        val settings: PlumSettings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Background)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    var guard = 0
                    while (model.colourPart != part && guard++ < 40) model.onAction(Action.Move(Direction.Down), settings, geometry)
                    val page = checkNotNull(model.focus.page)
                    tags =
                        mapOf(
                            ColorPart.Color to SettingsTags.panelItem(model.colorIndex(page)),
                            ColorPart.Shade to SettingsTags.panelItem(model.colorIndex(page) + 1),
                            ColorPart.Hex to SettingsTags.HEX_ROW,
                        )
                }
            }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    /** The page opens at its top (the presets) in the scrolling area. */
    @Test
    fun thePageOpensAtItsTop() {
        compose.setContent { PlumTheme(ReferenceShots.Preset.Graphite.settings) { Page(ColorPart.Preset) } }
        compose.waitForIdle()
        val scroll = compose.onNodeWithTag(SettingsTags.COLOR_SCROLL, useUnmergedTree = true).fetchSemanticsNode()
        val range = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals("opens at the top (max scroll ${range.maxValue()})", 0f, range.value(), 0.5f)
    }

    @Test
    fun everyRowUnderThePresetsIsInsideTheScrollingAreaWhenFocused() {
        var look by mutableStateOf(Pair(ReferenceShots.Preset.Graphite, ColorPart.Color))
        compose.setContent { PlumTheme(look.first.settings) { key(look) { Page(look.second) } } }
        for (preset in ReferenceShots.Preset.entries) {
            for (part in listOf(ColorPart.Color, ColorPart.Shade, ColorPart.Hex)) {
                look = Pair(preset, part)
                compose.waitForIdle()
                val area = compose.onNodeWithTag(SettingsTags.COLOR_SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val row = compose.onNodeWithTag(checkNotNull(tags[part]), useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue(
                    "${preset.name} ${part.name} focused: row ${row.top}..${row.bottom} lies inside the area ${area.top}..${area.bottom}",
                    row.top >= area.top && row.bottom <= area.bottom,
                )
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w853dp-h480dp-land-360dpi")
class SettingsColorPage853VisibleTest : SettingsColorPageVisibleBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorPage768VisibleTest : SettingsColorPageVisibleBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorPage576VisibleTest : SettingsColorPageVisibleBase()
