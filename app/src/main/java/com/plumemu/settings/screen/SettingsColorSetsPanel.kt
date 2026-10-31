package com.plumemu.settings.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.theme.ButtonColors
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.settings.LocalButtonLetters
import com.plumemu.settings.colorSet
import com.plumemu.settings.colorsCycle
import com.plumemu.settings.customButtonSet
import com.plumemu.settings.customColors
import com.plumemu.settings.toThemeSettings
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.setup.focusRing

/*
 * Settings › Appearance › Colors' panel: a tile per ready-made set, then Custom while
 * there is one, each a tiny home screen in that set's own colours with its name under it. D-pad
 * moves, A (or a second tap) uses the set through the row's own write and closes the panel, B goes
 * back to the row. Nothing applies while the cursor moves: the tiles are the preview.
 */

/** The tiles of the panel, in the row's cycle order: every set, then Custom (null) while there is one. */
internal fun colorSetsPanelEntries(settings: PlumSettings): List<ColorSet?> = settings.appearance.colorsCycle()

@Composable
internal fun ColorSetsPanel(
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val appearance = settings.appearance
    val entries = remember(appearance) { colorSetsPanelEntries(settings) }
    val looks = remember(appearance) { entries.map { tileLookOf(it, settings) } }
    val current = remember(appearance) { appearance.colorSet() }
    val columns = settingsGeometry(PlumTheme.layout).colorSetColumns
    // Read by each tile through its own derived state, never here: a move recomposes and redraws only
    // the tile it leaves and the one it reaches. Beside a focused row (wide) no tile has the focus.
    val focusedIndex: () -> Int =
        remember(model) {
            {
                val focus = model.focus
                if (focus.column == SettingsColumn.Panel && focus.page == PanelPage.ColorSets) focus.panelIndex else NO_TILE
            }
        }
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag(SettingsTags.COLOR_SETS_PANEL),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap),
    ) {
        PanelTitle(stringResource(R.string.settings_colors))
        PanelBody(stringResource(R.string.settings_colors_panel_help))
        Column(
            modifier = Modifier.fillMaxWidth().onRowTextLine().ringRoomAboveAndBelow(),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsColorTileGap),
        ) {
            entries.indices.chunked(columns).forEach { indices ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsColorTileGap)) {
                    for (index in indices) {
                        val entry = entries[index]
                        ColorTile(
                            index = index,
                            look = looks[index],
                            name = stringResource(colorTileName(entry)),
                            isCurrent = entry == current,
                            focusedIndex = focusedIndex,
                            touch = touch,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // A short last row keeps the tiles' width and their columns.
                    repeat(columns - indices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val NO_TILE = -1

/** What a tile is drawn with: the set's derived tokens and its glyph colours (null: the usual ringed glyphs). */
@Immutable
internal data class ColorTileLook(
    val tokens: PlumColors,
    val buttons: ButtonColors?,
)

/** The look of [entry]'s tile; Custom (null) in the colours Custom stands for (the current ones or the kept own colours). */
internal fun tileLookOf(
    entry: ColorSet?,
    settings: PlumSettings,
): ColorTileLook {
    if (entry != null) return ColorTileLook(PlumColors.derive(entry.backgroundColor, entry.textMode, entry.accentColor), entry.buttons)
    val theme = (settings.appearance.customColors() ?: settings.appearance).toThemeSettings(animationsOn = true)
    return ColorTileLook(PlumColors.derive(theme.backgroundColor, theme.textMode, theme.accentColor), settings.appearance.customButtonSet()?.buttons)
}

/**
 * One tile. Its parameters stay equal while the cursor moves, so it is skipped; only [focusedIndex],
 * read through a derived state, recomposes the tile whose focus flips (`SettingsColorsPanelRecompositionTest`).
 */
@Composable
private fun ColorTile(
    index: Int,
    look: ColorTileLook,
    name: String,
    isCurrent: Boolean,
    focusedIndex: () -> Int,
    touch: SettingsTouch,
    modifier: Modifier,
) {
    SideEffect { SettingsRecomposeProbe.colorTiles++ }
    val isFocused by remember(index, focusedIndex) { derivedStateOf { focusedIndex() == index } }
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val letters = LocalButtonLetters.current
    val confirm = stringResource(letters.confirm)
    val back = stringResource(letters.back)
    val measurer = rememberTextMeasurer()
    val keyCap = type.keyCap
    val shape = RoundedCornerShape(PlumDimens.settingsPaletteCorner)
    val tap = remember(index, touch) { Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)) }
    Column(
        modifier.bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset).then(tap),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsColorTileNameGap),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PlumDimens.SETTINGS_COLOR_TILE_ASPECT)
                .focusRing(isFocused, PlumDimens.settingsPaletteCorner, colors.accent, colors.focusGlow)
                // Built once per size and look: drawing allocates nothing.
                .drawWithCache { tinyHome(look, confirm, back, measurer, keyCap) }
                .border(PlumDimens.settingsSwatchEdge, colors.chip, shape)
                .testTag(SettingsTags.COLOR_TILE),
        )
        BasicText(
            text = name,
            style = if (isCurrent) type.colorTileNameCurrent.copy(color = colors.accent) else type.colorTileName.copy(color = colors.text2),
            // A long name in a large size takes a second line rather than being cut (ColorSetsPanelFitTest).
            maxLines = NAME_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(SettingsTags.COLOR_TILE_NAME),
        )
    }
}

private const val NAME_LINES = 2

/** A hint glyph of the tile: its fill (null: the `chip` ring), ring and laid-out letter. */
private class TileGlyph(
    val fill: Color?,
    val ring: Color,
    val letter: TextLayoutResult,
)

/**
 * The tile's picture, in fractions of its size so it reads the same at every width: the background,
 * a text line (the top bar), the focused row's fill with the accent dot and a `strong` title, a
 * `muted` meta line, and the first two hints (confirm and back, by the person's letters) in the
 * set's glyph colours or the usual `chip` ring. Colours and letters are made here, once per size.
 */
private fun CacheDrawScope.tinyHome(
    look: ColorTileLook,
    confirm: String,
    back: String,
    measurer: TextMeasurer,
    keyCap: TextStyle,
): DrawResult {
    val t = look.tokens
    val w = size.width
    val h = size.height
    val bg = t.bg.toComposeColor()
    val text2 = t.text2.toComposeColor()
    val sel = t.sel.toComposeColor()
    val accent = t.accent.toComposeColor()
    val strong = t.strong.toComposeColor()
    val muted = t.muted.toComposeColor()
    val tileCorner = CornerRadius(PlumDimens.settingsPaletteCorner.toPx())
    val corner = CornerRadius(TILE_CORNER * h)
    val left = SIDE * w
    val line = LINE * h
    val rowTop = ROW_TOP * h
    val rowHeight = ROW_H * h
    val rowLeft = left - ROW_BLEED * w
    val dot = DOT * h
    val titleLeft = left + dot + DOT_GAP * w
    val glyph = GLYPH * h
    val glyphY = HINTS_Y * h
    val ringWidth = GLYPH_RING * glyph
    val letterStyle = keyCap.copy(fontSize = (glyph * GLYPH_LETTER).toSp(), fontWeight = FontWeight.SemiBold, lineHeight = TextStyle.Default.lineHeight)
    val glyphs =
        arrayOf(confirm, back).map { letter ->
            val color = look.buttons?.forKeyCap(letter)
            val ink = color?.letter ?: t.text
            TileGlyph(
                fill = color?.fill?.toComposeColor(),
                ring = t.chip.toComposeColor(),
                letter = measurer.measure(letter, letterStyle.copy(color = ink.toComposeColor()), maxLines = 1),
            )
        }
    val ring = Stroke(ringWidth)
    return onDrawBehind {
        drawRoundRect(bg, cornerRadius = tileCorner)
        // The top bar's text.
        drawRoundRect(text2, Offset(left, TOP_LINE_Y * h), Size(TOP_LINE_W * w, line), corner)
        // The focused row: fill, accent dot, title; the meta line under it.
        drawRoundRect(sel, Offset(rowLeft, rowTop), Size(w - 2 * rowLeft, rowHeight), CornerRadius(ROW_CORNER * h))
        drawCircle(accent, dot / 2, Offset(left + dot / 2, rowTop + rowHeight / 2))
        drawRoundRect(strong, Offset(titleLeft, rowTop + (rowHeight - line) / 2), Size(TITLE_W * w, line), corner)
        drawRoundRect(muted, Offset(titleLeft, META_Y * h), Size(META_W * w, line), corner)
        // Two hints at the bottom: glyph and a short label each.
        var x = left
        for (each in glyphs) {
            val center = Offset(x + glyph / 2, glyphY)
            if (each.fill != null) drawCircle(each.fill, glyph / 2, center) else drawCircle(each.ring, glyph / 2 - ringWidth / 2, center, style = ring)
            val letter = each.letter
            drawText(letter, topLeft = Offset(center.x - letter.size.width / 2f, center.y - letter.size.height / 2f))
            x += glyph + LABEL_GAP * w
            drawRoundRect(muted, Offset(x, glyphY - line / 2), Size(LABEL_W * w, line), corner)
            x += LABEL_W * w + HINT_GAP * w
        }
    }
}

// The tiny home screen, in fractions of the tile's width (w) or height (h).
private const val SIDE = 0.1f // w: the text's left line
private const val LINE = 0.07f // h: a line of text
private const val TILE_CORNER = 0.035f // h
private const val TOP_LINE_Y = 0.12f // h
private const val TOP_LINE_W = 0.38f // w
private const val ROW_TOP = 0.28f // h
private const val ROW_H = 0.22f // h
private const val ROW_BLEED = 0.04f // w: the fill reaches past the text line
private const val ROW_CORNER = 0.06f // h
private const val DOT = 0.09f // h
private const val DOT_GAP = 0.04f // w
private const val TITLE_W = 0.46f // w
private const val META_Y = 0.6f // h
private const val META_W = 0.3f // w
private const val HINTS_Y = 0.83f // h: the glyphs' centre
private const val GLYPH = 0.2f // h
private const val GLYPH_RING = 0.09f // of the glyph
private const val GLYPH_LETTER = 0.62f // of the glyph
private const val LABEL_GAP = 0.03f // w
private const val LABEL_W = 0.12f // w
private const val HINT_GAP = 0.07f // w

/** A tile's name: the set's ([colorSetName]), or Custom. */
@StringRes
internal fun colorTileName(entry: ColorSet?): Int = entry?.let(::colorSetName) ?: R.string.settings_color_custom
