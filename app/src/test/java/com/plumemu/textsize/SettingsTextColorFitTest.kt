package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorShades
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.settings.screen.ColorPages
import com.plumemu.settings.screen.NoActions
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.NoTouch
import com.plumemu.settings.screen.SettingsGeometry
import com.plumemu.settings.screen.SettingsLayout
import com.plumemu.settings.screen.SettingsModel
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.TEST_VERSION
import com.plumemu.settings.screen.optionsAt
import com.plumemu.settings.screen.settingsForShot
import com.plumemu.settings.screen.settingsGeometry
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › Colors › Text color at every text size, the way
 * `SettingsStatisticsFitTest` checks Statistics: no text is ellipsized, clipped, too short or on top
 * of another, and what the cursor is on is fully on screen: the presets panel, the own-colour page,
 * a refused hex code with its "Use" row, and the notice that a background moved the text.
 */
abstract class SettingsTextColorFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Composable
    private fun Panel(steps: SettingsModel.(PlumSettings, SettingsGeometry) -> Unit) {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android), appearance = base.appearance.copy(textColor = TextColorSetting.Light))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model = remember { SettingsModel(NoActions, optionsAt(SettingsOption.TextColor)).also { it.steps(settings, geometry) } }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    private fun check(
        name: String,
        focusedTag: String,
        steps: SettingsModel.(PlumSettings, SettingsGeometry) -> Unit,
    ) {
        var size by mutableStateOf(TextSize.Largest)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { Panel(steps) } }
        val combinations =
            TextSize.entries.map { PlumFont.Jost to it }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "$name at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            textProblems(root, FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.COLOR_SCROLL), isLargest = each == TextSize.Largest)).mapTo(failures) { "$label: $it" }
            val focused = findTagged(root, focusedTag)
            if (focused == null) failures += "$label: the focused item is not drawn" else notFullyShown(focused, root)?.let { failures += "$label: the focused item $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }

    /** The Color row's panel index: right after the presets (Text color on Graphite). */
    private fun colorRow(): Int = ColorPages.presetCount(PanelPage.TextPresets, BackgroundPreset.Graphite.color)

    /** Opens the page through the panel, then a tap on the Color row (no preset previewed on the way). */
    private fun SettingsModel.openColourPage(
        settings: PlumSettings,
        geometry: SettingsGeometry,
    ) {
        onAction(Action.Enter, settings, geometry)
        onTapPanelItem(colorRow(), settings, geometry)
    }

    @Test
    fun presets() = check("text_color_presets", SettingsTags.panelItem(0)) { settings, geometry -> onAction(Action.Enter, settings, geometry) }

    /** The focused row must be whole at every size: Color, the shade the cursor rests on, the Color code row. */
    @Test
    fun colorRowFocused() = check("text_color_color", SettingsTags.panelItem(colorRow())) { settings, geometry -> openColourPage(settings, geometry) }

    @Test
    fun shadeRowFocused() =
        check("text_color_shade", SettingsTags.panelItem(colorRow() + 1)) { settings, geometry ->
            openColourPage(settings, geometry)
            onAction(Action.Move(Direction.Down), settings, geometry)
            // The cursor rests on the middle shade; the first one is the row's own first key.
            repeat(ColorShades.SHADE_COUNT) { onAction(Action.Move(Direction.Left), settings, geometry) }
        }

    @Test
    fun colorCodeRowFocused() =
        check("text_color_hex", SettingsTags.HEX_ROW) { settings, geometry ->
            openColourPage(settings, geometry)
            // Down to the Color code row, the last one: the cursor is on it without the field open.
            repeat(2) { onAction(Action.Move(Direction.Down), settings, geometry) }
        }

    /** A code too close to the background: the two-line message and the "Use #…" row the cursor lands on. */
    @Test
    fun refusedHex() =
        check("text_color_refused", SettingsTags.HEX_USE) { settings, geometry ->
            openColourPage(settings, geometry)
            onTapPanelItem(hexIndex(PanelPage.TextPresets), settings, geometry)
            onHexTextChange("222222")
            onHexEntered(hexText)
        }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsTextColorFit768Test : SettingsTextColorFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsTextColorFit837Test : SettingsTextColorFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsTextColorFit667Test : SettingsTextColorFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsTextColorFit576Test : SettingsTextColorFitBase(576, 432)
