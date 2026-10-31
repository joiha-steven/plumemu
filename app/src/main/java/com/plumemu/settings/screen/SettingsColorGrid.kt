package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.borderDrawn
import com.plumemu.coreui.components.pressFeel
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.setup.focusRing

/*
 * The swatch grid of the "+" page: rows of PaletteCuration.COLUMNS tiles inside the
 * same container as the slider rows and the Color code row (fill sel2, cornerRow, the row padding at the
 * sides, SliderRowPad above and below, one settingsPanelGap from the next), so the three share their left
 * and right edges. The tiles share the width, so nothing depends on the text size.
 */

/** The swatches in rows of eight; the one equal to [selected] (the opening or chosen colour, not the previewed one) carries the selected check, the one at [focusedIndex] the focus ring. */
@Composable
internal fun PaletteGrid(
    palette: List<Rgb>,
    selected: Rgb,
    focusedIndex: Int,
    inPanel: Boolean,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val gap = if (PlumTheme.layout.widthClass == WidthClass.Narrow) PlumDimens.swatchGapNarrow else PlumDimens.swatchGapWide
    // Explicit rows: a FlowRow sized to the exact width wraps at seven when pixels round up.
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(PALETTE_GRID_TAG)
                .drawWithContent {
                    drawRoundRect(colors.sel2, cornerRadius = CornerRadius(PlumDimens.cornerRow.toPx()))
                    drawContent()
                }.padding(horizontal = PlumDimens.settingsPanelRowPadding, vertical = PlumDimens.settingsSliderRowPad),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        palette.chunked(PaletteCuration.COLUMNS).forEachIndexed { row, tiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                tiles.forEachIndexed { column, rgb ->
                    val i = row * PaletteCuration.COLUMNS + column
                    val isFocused = inPanel && i == focusedIndex
                    Swatch(
                        rgb.toComposeColor(),
                        Dp.Unspecified,
                        PlumDimens.settingsPaletteCorner,
                        isFocused,
                        edge = true,
                        Modifier.weight(1f).aspectRatio(1f).swatchTap(i, touch).bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset),
                        selected = rgb == selected,
                        markColor = TextMode.bestOn(rgb).color.toComposeColor(),
                    )
                }
                // A short last row keeps its tiles the width of the others.
                repeat(PaletteCuration.COLUMNS - tiles.size) { EmptyTile() }
            }
        }
    }
}

@Composable
private fun RowScope.EmptyTile() {
    Box(Modifier.weight(1f))
}

/**
 * A colour tile. Focused: the accent ring, 3 dp away (one focus language, design/rules.md). Selected (the colour in
 * use): a check mark inside the tile in the text colour that reads on it, no ring, so a tile can be both.
 */
@Composable
internal fun Swatch(
    color: Color,
    size: Dp,
    corner: Dp,
    isFocused: Boolean,
    edge: Boolean,
    modifier: Modifier,
    selected: Boolean = false,
    markColor: Color = Color.Unspecified,
) {
    val colors = PlumTheme.colors
    val shape = RoundedCornerShape(corner)
    val stroke = PlumDimens.settingsSliderThumbRing
    Box(
        modifier
            .then(if (size == Dp.Unspecified) Modifier else Modifier.size(size))
            // Outside the ring, so the ring dips with the swatch.
            .pressFeel(isFocused, null, shape)
            .focusRing(isFocused, corner, { colors.accent }, { colors.focusGlow })
            .background(color, shape)
            .then(if (edge) Modifier.borderDrawn(PlumDimens.settingsSwatchEdge, { colors.chip }, corner) else Modifier)
            .then(
                if (selected) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawCheck(markColor, stroke.toPx())
                    }
                } else {
                    Modifier
                },
            ),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheck(
    color: Color,
    width: Float,
) {
    val w = size.width
    val h = size.height
    val path =
        Path().apply {
            moveTo(w * CHECK_LEFT, h * CHECK_MID_Y)
            lineTo(w * CHECK_BOTTOM_X, h * CHECK_BOTTOM_Y)
            lineTo(w * CHECK_RIGHT, h * CHECK_TOP_Y)
        }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// The check's corners as fractions of the tile.
private const val CHECK_LEFT = 0.28f
private const val CHECK_MID_Y = 0.52f
private const val CHECK_BOTTOM_X = 0.43f
private const val CHECK_BOTTOM_Y = 0.67f
private const val CHECK_RIGHT = 0.72f
private const val CHECK_TOP_Y = 0.35f

/** The test tag of the grid container (SettingsTags is at the file-length limit). */
internal const val PALETTE_GRID_TAG = "settings_palette_grid"
