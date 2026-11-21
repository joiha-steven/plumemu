package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rows under the presets on Background's colour page: the Color row, the Shade row and the Color
 * code row start and end at the same edges, the Color label sits on the row-text line, the Color and
 * Shade rows are one panel gap apart, and the shades sit inside the Shade row's padding. At Small and
 * Largest, both reference widths.
 */
abstract class SettingsColorRowsAlignBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var colorRow = 0
    private var shadeKeys = 0

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Background)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    var guard = 0
                    while (model.colourPart != ColorPart.Color && guard++ < 40) model.onAction(Action.Move(Direction.Down), settings, geometry)
                    colorRow = model.focus.panelIndex
                    shadeKeys = model.colorShades(checkNotNull(model.focus.page)).size
                }
            }
        SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private fun Dp.near(other: Dp) = kotlin.math.abs(value - other.value) <= 0.5f

    @Test
    fun rowsShareTheColorCodeRowsEdgesAndTheColorLabelIsOnTheRowTextLine() {
        var size by mutableStateOf(TextSize.Small)
        var theme by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(theme.copy(textSize = size)) { Page() } }
        val failures = mutableListOf<String>()
        for ((name, each, look) in listOf(Triple("Graphite", TextSize.Small, PlumThemeSettings.Graphite), Triple("Graphite", TextSize.Largest, PlumThemeSettings.Graphite), Triple("Paper", TextSize.Largest, PlumThemeSettings.Paper))) {
            size = each
            theme = look
            compose.waitForIdle()
            val tag = "$name $each"
            val hex = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot()
            val color = compose.onNodeWithTag(SettingsTags.panelItem(colorRow)).getUnclippedBoundsInRoot()
            val shade = compose.onNodeWithTag(SHADE_ROW_TAG).getUnclippedBoundsInRoot()
            for ((row, bounds) in listOf("Color" to color, "Shade" to shade)) {
                if (!bounds.left.near(hex.left)) failures += "$tag $row left ${bounds.left} != Color code ${hex.left}"
                if (!bounds.right.near(hex.right)) failures += "$tag $row right ${bounds.right} != Color code ${hex.right}"
            }
            val label = compose.onNodeWithText("Color", useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (!label.left.near(color.left + PlumDimens.gameRowPadding)) failures += "$tag Color label ${label.left} not on the row-text line ${color.left + PlumDimens.gameRowPadding}"
            if (!(shade.top - color.bottom).near(PlumDimens.settingsPanelGap)) failures += "$tag gap Color to Shade ${shade.top - color.bottom} != settingsPanelGap"
            if ((color.bottom - color.top).value < PlumDimens.settingsHexRowHeight.value - 0.5f) failures += "$tag Color row shorter than the Color code row"
            val pad = PlumDimens.settingsPanelRowPadding
            for (i in 0 until shadeKeys) {
                val key = compose.onNodeWithTag(SettingsTags.panelItem(colorRow + 1 + i)).getUnclippedBoundsInRoot()
                if (key.left < shade.left + pad - 0.5f.dp || key.right > shade.right - pad + 0.5f.dp || key.top < shade.top || key.bottom > shade.bottom) failures += "$tag shade $i ${key.left}..${key.right} sticks out of its row"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorRowsAlign768Test : SettingsColorRowsAlignBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorRowsAlign576Test : SettingsColorRowsAlignBase()
