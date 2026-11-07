package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.PlumSwatch
import com.plumemu.coreui.components.pressFeel
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SwatchDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.setup.bringIntoViewWhen

/*
 * The swatch grid of the "+" page: rows of PaletteCuration.COLUMNS tiles inside the
 * same container as the slider rows and the Color code row (fill sel2, cornerRow, the row padding at the
 * sides, SliderRowPad above and below, one settingsPanelGap from the next), so the three share their left
 * and right edges. The tiles share the width, so nothing depends on the text size.
 */

/** The swatches in rows of eight; the one equal to [selected] (the opening or chosen colour, not the previewed one) carries the selected check, the one at [focusedIndex] a lit collar. */
@Composable
internal fun PaletteGrid(
    palette: List<Rgb>,
    selected: Rgb,
    focusedIndex: Int,
    inPanel: Boolean,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    // The collar takes its lip and groove from each cell, so the cells' gap shrinks by that much and the board's eight columns and the look of the spacing stay.
    val gap = ((if (PlumTheme.layout.widthClass == WidthClass.Narrow) SwatchDimens.swatchGapNarrow else SwatchDimens.swatchGapWide) - CollarSize.Key.sidePadding * 2).coerceAtLeast(0.dp)
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
        verticalArrangement = Arrangement.spacedBy(gap + CollarSize.Key.bottomPadding - CollarSize.Key.topPadding),
    ) {
        palette.chunked(PaletteCuration.COLUMNS).forEachIndexed { row, tiles ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                tiles.forEachIndexed { column, rgb ->
                    val i = row * PaletteCuration.COLUMNS + column
                    val isFocused = inPanel && i == focusedIndex
                    Swatch(
                        rgb.toComposeColor(),
                        PlumDimens.settingsPaletteCorner,
                        isFocused,
                        Modifier.weight(1f).aspectRatio(1f).swatchTap(i, touch).bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset).collarCell(CollarSize.Key),
                        size = Modifier.fillMaxSize(),
                        bleed = false,
                        selected = rgb == selected,
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
 * A colour in its collar ([PlumSwatch]): focus lights the groove and halo, a selected one carries the check in the ink that
 * reads on it; its edge is the cap's own. [size] is the cap's size inside the collar. A fixed-size swatch [bleed]s: the collar
 * falls in the gap around it, so the board's columns stay; a grid cell takes it from its own width instead.
 */
@Composable
internal fun Swatch(
    color: Color,
    corner: Dp,
    isFocused: Boolean,
    modifier: Modifier,
    size: Modifier,
    bleed: Boolean,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    val colors = PlumTheme.colors
    val line = PlumDimens.focusRingKeyline
    // Focus lights the groove in the accent, which a swatch of that colour swallows: there a line in the accent's own ink tells it, inside the cap's edge.
    val edge =
        if (isFocused) {
            Modifier.drawWithContent {
                drawContent()
                if (nearAccent(color, colors.accent)) {
                    val width = line.toPx()
                    val inset = width / 2
                    drawRoundRect(
                        colors.onAccent,
                        topLeft = Offset(inset, inset),
                        size = Size(this.size.width - width, this.size.height - width),
                        cornerRadius = CornerRadius(corner.toPx() - inset),
                        style = Stroke(width),
                    )
                }
            }
        } else {
            Modifier
        }
    PlumSwatch(
        color = { color },
        selected = selected,
        focused = isFocused,
        enabled = true,
        // Outside the collar, so the whole swatch dips with a press.
        modifier = modifier.pressFeel(isFocused, null, shape).then(if (bleed) Modifier.collarBleed(CollarSize.Key) else Modifier),
        corner = corner,
        size = size.then(edge),
    )
}

private const val RGB_ONLY = 0xFFFFFF

/** Close enough that the accent's focus light does not stand out on [swatch]: within twice the palette's "clearly different" distance. */
internal fun nearAccent(
    swatch: Color,
    accent: Color,
): Boolean = PaletteCuration.distance(Rgb(swatch.toArgb() and RGB_ONLY), Rgb(accent.toArgb() and RGB_ONLY)) < 2 * PaletteCuration.MIN_DISTANCE

/** The test tag of the grid container (SettingsTags is at the file-length limit). */
internal const val PALETTE_GRID_TAG = "settings_palette_grid"
