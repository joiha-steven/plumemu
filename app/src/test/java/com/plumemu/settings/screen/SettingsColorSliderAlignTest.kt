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
import com.plumemu.coreui.theme.CustomPalette
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.toThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The slider rows' edges on the "+" page of Background: the three rows start and
 * end where the Color code row does, their label and value sit on the row-text lines (14 in),
 * the rows are one panel gap apart like the hex row under them, and the thumb stays between the
 * text lines. At Medium and Largest, both reference widths.
 */
abstract class SettingsColorSliderAlignBase {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Composable
    private fun Page() {
        val base = settingsForShot()
        val settings = base.copy(controls = base.controls.copy(keyboard = KeyboardSetting.Android))
        val geometry = settingsGeometry(PlumTheme.layout)
        val model =
            remember {
                SettingsModel(NoActions, optionsAt(SettingsOption.Background)).also { model ->
                    model.onAction(Action.Enter, settings, geometry)
                    val plus = ColorPages.presetCount(PanelPage.BackgroundPresets, settings.toThemeSettings().backgroundColor) - 1
                    model.onTapPanelItem(plus, settings, geometry)
                    if (model.focus.page == PanelPage.BackgroundPresets) model.onTapPanelItem(plus, settings, geometry)
                    model.onAction(Action.Move(Direction.Down), settings, geometry)
                }
            }
        SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { "21:40" }, battery = { "82%" })
    }

    private fun Dp.near(other: Dp) = kotlin.math.abs(value - other.value) <= 0.5f

    @Test
    fun rowsShareTheHexRowsEdgesAndTheirTextIsOnTheRowTextLines() {
        var size by mutableStateOf(TextSize.Medium)
        var theme by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(theme.copy(textSize = size)) { Page() } }
        val first = backgroundGrid.size
        val failures = mutableListOf<String>()
        for ((name, each, look) in listOf(Triple("Graphite", TextSize.Medium, PlumThemeSettings.Graphite), Triple("Graphite", TextSize.Largest, PlumThemeSettings.Graphite), Triple("Paper", TextSize.Largest, PlumThemeSettings.Paper))) {
            size = each
            theme = look
            compose.waitForIdle()
            checkGrid("$name $each", failures)
            val hex = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot()
            val rows = (0..2).map { compose.onNodeWithTag(SettingsTags.panelItem(first + it)).getUnclippedBoundsInRoot() }
            val line = PlumDimens.gameRowPadding
            rows.forEachIndexed { i, row ->
                if (!row.left.near(hex.left)) failures += "$each row $i left ${row.left} != hex ${hex.left}"
                if (!row.right.near(hex.right)) failures += "$each row $i right ${row.right} != hex ${hex.right}"
            }
            val label = compose.onNodeWithText("Hue", useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (!label.left.near(rows[0].left + line)) failures += "$each Hue label ${label.left} not on the row-text line ${rows[0].left + line}"
            val gaps = (0..1).map { rows[it + 1].top - rows[it].bottom } + (hex.top - rows[2].bottom)
            if (gaps.any { !it.near(gaps[0]) }) failures += "$each rows are not evenly spaced: $gaps"
            if (!gaps[0].near(PlumDimens.settingsPanelGap)) failures += "$each gap ${gaps[0]} != settingsPanelGap"
            val heights = rows.map { it.bottom - it.top }
            if (heights.any { !it.near(heights[0]) }) failures += "$each rows differ in height: $heights"
            if (heights[0].value < PlumDimens.settingsHexRowHeight.value - 0.5f) failures += "$each row shorter than the hex row"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** The swatch grid's container has the sliders' and the hex row's left and right edges, its tiles sit inside its padding, and none is cut. */
    private fun checkGrid(
        label: String,
        failures: MutableList<String>,
    ) {
        val hex = compose.onNodeWithTag(SettingsTags.HEX_ROW).getUnclippedBoundsInRoot()
        val grid = compose.onNodeWithTag(PALETTE_GRID_TAG).getUnclippedBoundsInRoot()
        val hue = compose.onNodeWithTag(SettingsTags.panelItem(backgroundGrid.size)).getUnclippedBoundsInRoot()
        if (!grid.left.near(hex.left)) failures += "$label grid left ${grid.left} != hex ${hex.left}"
        if (!grid.right.near(hex.right)) failures += "$label grid right ${grid.right} != hex ${hex.right}"
        if (!grid.left.near(hue.left) || !grid.right.near(hue.right)) failures += "$label grid ${grid.left}..${grid.right} != Hue row ${hue.left}..${hue.right}"
        if (!(hue.top - grid.bottom).near(PlumDimens.settingsPanelGap)) failures += "$label gap grid to Hue ${hue.top - grid.bottom} != settingsPanelGap"
        val pad = PlumDimens.settingsPanelRowPadding
        val tiles = backgroundGrid.indices.map { compose.onNodeWithTag(SettingsTags.panelItem(it)).getUnclippedBoundsInRoot() }
        tiles.forEachIndexed { i, t ->
            if (t.left < grid.left + pad - 0.5f.dp || t.right > grid.right - pad + 0.5f.dp || t.top < grid.top || t.bottom > grid.bottom) failures += "$label tile $i ${t.left}..${t.right} sticks out of the grid"
            if (!(t.right - t.left).near(t.bottom - t.top)) failures += "$label tile $i is not square"
            if ((t.right - t.left).value < MIN_TILE) failures += "$label tile $i only ${t.right - t.left} wide"
        }
        if (!tiles.first().left.near(grid.left + pad)) failures += "$label first tile ${tiles.first().left} not at the padding ${grid.left + pad}"
        val rightmost = tiles.maxOf { it.right }
        if (!rightmost.near(grid.right - pad)) failures += "$label last column ends at $rightmost, not at ${grid.right - pad}"
    }

    private companion object {
        const val MIN_TILE = 22f
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsColorSliderAlign768Test : SettingsColorSliderAlignBase()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsColorSliderAlign576Test : SettingsColorSliderAlignBase()
