package com.plumemu.settings.screen

import androidx.compose.ui.unit.Dp
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.SettingsThreeDimens
import com.plumemu.coreui.theme.WidthClass
import kotlin.math.floor

/**
 * Preset swatches per row on the focused colour page ([colourPageWidth]): what fits between its text-column lines, 14 in on each side; Colors'
 * preview tiles per row: what fits on the row-text lines at the tile's least width for the text size.
 */
internal fun settingsGeometry(layout: PlumLayout): SettingsGeometry {
    val inner = colourPageWidth(layout) - PlumDimens.settingsPanelRowPadding * 2
    val columns = floor((inner + PlumDimens.settingsPresetSwatchGap) / (PlumDimens.settingsPresetSwatch + PlumDimens.settingsPresetSwatchGap)).toInt()
    val tileGap = PlumDimens.settingsColorTileGap
    val tiles = floor((colorTilesWidth(layout) + tileGap) / (PlumDimens.settingsColorTileMinWidth * layout.textScale + tileGap)).toInt()
    return SettingsGeometry(isNarrow = layout.widthClass == WidthClass.Narrow, presetColumns = columns.coerceAtLeast(1),
        defaultTextSize = TextSizeSetting.Default,
        colorSetColumns = tiles.coerceIn(1, PlumDimens.SETTINGS_COLOR_TILE_MAX_COLUMNS),
    )
}

/**
 * The width the colour page draws its presets across, which the model walks them by: in three columns the page covers the options and the right column
 * ([ThreeColumnPanel]); otherwise it is the panel, beside the options or the further stop.
 */
internal fun colourPageWidth(layout: PlumLayout): Dp =
    if (layout.isSettingsThreeColumns) layout.settingsOptionsWidth + SettingsThreeDimens.panelGap + SettingsThreeDimens.panelWidth else settingsPanelWidth(layout)

/** Colors' tiles span the panel between its row-text lines (14 in on each side). */
internal fun colorTilesWidth(layout: PlumLayout): Dp = settingsPanelWidth(layout) - PlumDimens.settingsPanelRowPadding * 2

/** The panel: 248 beside the options on wide screens; the options' width as a further stop on narrow ones. */
internal fun settingsPanelWidth(layout: PlumLayout): Dp =
    when {
        layout.isSettingsThreeColumns -> SettingsThreeDimens.panelWidth
        layout.widthClass == WidthClass.Wide -> PlumDimens.settingsPanelWidth
        else -> layout.settingsOptionsWidth
    }
