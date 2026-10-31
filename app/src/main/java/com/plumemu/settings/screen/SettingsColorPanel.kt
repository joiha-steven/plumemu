package com.plumemu.settings.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.components.borderDrawn
import com.plumemu.coreui.components.focusPillSample
import com.plumemu.coreui.components.pressFeel
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SystemAccents
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.game.TypingPanelLayout
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.settings.ColorRefusal
import com.plumemu.settings.colorSet
import com.plumemu.settings.toRgb
import com.plumemu.settings.toThemeSettings
import com.plumemu.setup.focusRing

/*
 * Background and Accent: the presets with "+" for your own colour, then (after "+") the palette of
 * 8 hues by its shades and the hex code, typed with Plumemu's keyboard or Android's as Settings ›
 * Controls › Keyboard says. A colour applies
 * as soon as the cursor reaches it, so the whole screen shows it at once; the ring marks the current
 * colour (design/rules.md › Colour).
 */

@Composable
internal fun ColorPanel(
    option: SettingsOption,
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val inPanel = focus.column == SettingsColumn.Panel
    // Beside a focused option the panel shows the page the panel would open at.
    val (page, index) = if (inPanel && focus.page != null) focus.page to focus.panelIndex else ColorPages.openingFor(option, settings)
    when (page) {
        PanelPage.BackgroundCustom, PanelPage.TextCustom, PanelPage.AccentCustom -> CustomPalettePage(page, index, inPanel, settings, model, touch)
        else -> PresetsPage(option, index, inPanel, settings, model, touch)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetsPage(
    option: SettingsOption,
    focusedIndex: Int,
    inPanel: Boolean,
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val colors = PlumTheme.colors
    val theme = settings.toThemeSettings()
    val kind = ColorPages.kindOf(option)
    // Background and Text color show the sample rows (how the list reads); Accent color its own sample.
    val isBackground = kind != ColorKind.Accent
    val swatches: List<Color> =
        when (kind) {
            ColorKind.Background -> ColorPages.backgroundPresets.map { it.color.toComposeColor() } + ColorPages.vividBackgrounds.map { it.color.toComposeColor() }
            ColorKind.Text -> ColorPages.textPresetsOn(theme.backgroundColor).map { it.toRgb().toComposeColor() }
            else -> ColorPages.accentPresets.map { it.colorFor(theme.textMode).toComposeColor() }
        }
    if (kind == ColorKind.Text && ColorPages.textBlockedOn(theme.backgroundColor)) {
        // A background an older version stored, which no text colour reads on: say so, offer nothing that would fail.
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
            PanelTitle(stringResource(option.label))
            PanelBody(stringResource(R.string.settings_text_color_blocked))
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
        PanelTitle(stringResource(option.label))
        PanelBody(stringResource(bodyOf(kind)))
        FlowRow(
            modifier = Modifier.onRowTextLine().padding(vertical = PlumDimens.settingsSwatchInset),
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap),
        ) {
            val corner = if (isBackground) PlumDimens.settingsPresetSwatchCorner else PlumDimens.settingsPresetSwatch / 2
            swatches.forEachIndexed { i, color ->
                Swatch(color, PlumDimens.settingsPresetSwatch, corner, isFocused = i == focusedIndex, edge = isBackground, Modifier.swatchTap(i, touch))
            }
            if (kind == ColorKind.Accent) {
                BySystemTile(isFocused = ColorPages.bySystemIndex == focusedIndex, corner = corner, Modifier.swatchTap(ColorPages.bySystemIndex, touch))
            }
            val add = if (kind == ColorKind.Accent) swatches.size + 1 else swatches.size
            Box(
                modifier =
                    Modifier
                        .size(PlumDimens.settingsPresetSwatch)
                        .pressFeel(add == focusedIndex, null, RoundedCornerShape(corner))
                        .focusRing(add == focusedIndex, corner, colors.accent, colors.focusGlow)
                        .border(PlumDimens.switchOffRing, colors.chip, RoundedCornerShape(corner))
                        .swatchTap(add, touch),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(stringResource(R.string.settings_color_add_glyph), style = PlumTheme.type.settingsSwatchGlyph.copy(color = colors.text2))
            }
        }
        if (inPanel) ColorNotice(model)
        if (isBackground) {
            val isText = kind == ColorKind.Text
            val name = if (isText) stringResource(textColorName(settings.appearance.textColor, theme.backgroundColor)) else colorName(settings.appearance.background, isBackground = true, settings.appearance.colorSet())
            // The name first, then the sample rows one by one (direct children, so they drop from the end), each only if it still fits under the swatches.
            RowsThatFit(Modifier, PlumDimens.listRowGap) {
                // 12 under the name plus the rows' 4 top padding, less the 2 gap the rows are stacked with.
                Box(Modifier.padding(bottom = PlumDimens.settingsPanelGap + PlumDimens.settingsPreviewTopPadding - PlumDimens.listRowGap)) {
                    ColorNameLine(name, (if (isText) theme.textColor else theme.backgroundColor).toHex())
                }
                PreviewRows()
            }
        } else {
            // Name and hex like the other two pages (By system has no single colour: no hex); with the sample, left out from the end when there is no room.
            RowsThatFit(Modifier, PlumDimens.settingsPanelGap) {
                ColorNameLine(colorName(settings.appearance.accent, isBackground = false, settings.appearance.colorSet()), theme.accentColor.toHex().takeUnless { settings.appearance.accent is ColorSetting.BySystem })
                AccentSampleRow()
            }
        }
    }
}

/** Accent color › By system: a dot of four systems' colours, right after the presets. */
@Composable
private fun BySystemTile(
    isFocused: Boolean,
    corner: Dp,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val name = stringResource(R.string.settings_accent_by_system_description)
    val shape = RoundedCornerShape(corner)
    BySystemDisc(
        modifier
            .size(PlumDimens.settingsPresetSwatch)
            .semantics { contentDescription = name }
            // Outside the ring and the clip, so the ring dips with the tile.
            .pressFeel(isFocused, null, shape)
            .focusRing(isFocused, corner, colors.accent, colors.focusGlow)
            .clip(shape)
            .border(PlumDimens.settingsSwatchEdge, colors.chip, shape),
    )
}

/**
 * Four systems' colours in quarters: the glyph of "By system" in the panel and, small, in the
 * Accent row, where one colour would be wrong for every system but the one in view.
 */
@Composable
internal fun BySystemDisc(modifier: Modifier) {
    val sample = SystemAccents.SAMPLE.map { it.toComposeColor() }
    Canvas(modifier) {
        sample.forEachIndexed { i, color -> drawArc(color, startAngle = i * QUARTER_TURN, sweepAngle = QUARTER_TURN, useCenter = true) }
    }
}

private const val QUARTER_TURN = 90f

private fun bodyOf(kind: ColorKind?): Int =
    when (kind) {
        ColorKind.Text -> R.string.settings_text_color_body
        ColorKind.Accent -> R.string.settings_accent_body
        else -> R.string.settings_background_body
    }

/**
 * One line under the swatches about the last colour write: a pick
 * that was refused as unreadable, with the nearest colour that reads, or a background change that
 * moved the text or the accent to keep it readable. Nothing when the write changed nothing.
 */
@Composable
private fun ColorNotice(model: SettingsModel) {
    val write = model.colorWrite ?: return
    val refusal = write.refusal
    val text =
        when {
            refusal != null -> stringResource(refusalMessage(refusal), refusal.nearest.toHex())
            write.textAdjusted && write.accentAdjusted -> stringResource(R.string.settings_color_adjusted_both)
            write.textAdjusted -> stringResource(R.string.settings_color_adjusted_text)
            write.accentAdjusted -> stringResource(R.string.settings_color_adjusted_accent)
            else -> return
        }
    val colors = PlumTheme.colors
    BasicText(text, style = PlumTheme.type.panelBody, color = { colors.text }, modifier = Modifier.onRowTextLine().testTag(SettingsTags.COLOR_NOTICE))
}

private fun refusalMessage(refusal: ColorRefusal): Int =
    when (refusal.role) {
        ColorRole.Text -> R.string.settings_color_refused_text
        ColorRole.Accent -> R.string.settings_color_refused_accent
        null -> R.string.settings_color_refused_background
    }

/** The board's three game rows under the background swatches: how the list reads on this colour. */
@Composable
private fun PreviewRows() {  // emits three rows, no wrapper
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    // Decoration, emitted as separate rows for the caller's RowsThatFit: at the larger text sizes only those that fit show.
    run {
        val rows = listOf(R.string.settings_sample_game_1 to R.string.settings_sample_year_1, R.string.settings_sample_game_2 to R.string.settings_sample_year_2, R.string.settings_sample_game_3 to R.string.settings_sample_year_3)
        rows.forEachIndexed { i, (title, year) ->
            val focused = i == 1
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PlumTheme.layout.settingsPreviewRowHeight)
                        .then(if (focused) Modifier.focusPillSample() else Modifier)
                        .padding(horizontal = PlumDimens.settingsPanelRowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val style = if (focused) type.setupRowTitleFocused.copy(color = colors.strong) else type.setupRowTitle.copy(color = colors.text2)
                BasicText(stringResource(title), style = style, maxLines = 1, modifier = Modifier.weight(1f))
                BasicText(stringResource(year), style = type.settingsPanelMeta.copy(color = if (focused) colors.meta else colors.muted), maxLines = 1)
            }
        }
    }
}

/**
 * [content]'s rows stacked [gap] apart, as many from the top as fit the height left; the rest are
 * not drawn. For decoration only: nothing in it can take the focus.
 */
@Composable
private fun RowsThatFit(
    modifier: Modifier,
    gap: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        // Unbounded height: a row taller than what is left is left out whole, not squeezed into it.
        val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val placeables = ArrayList<Placeable>()
        var used = 0
        for (measurable in measurables) {
            val placeable = measurable.measure(loose)
            val next = used + (if (placeables.isEmpty()) 0 else gapPx) + placeable.height
            if (next > constraints.maxHeight) break
            placeables += placeable
            used = next
        }
        val width = placeables.maxOfOrNull { it.width } ?: 0
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), used) {
            var y = 0
            for (placeable in placeables) {
                placeable.placeRelative(0, y)
                y += placeable.height + gapPx
            }
        }
    }
}

@Composable
private fun CustomPalettePage(
    page: PanelPage,
    focusedIndex: Int,
    inPanel: Boolean,
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val theme = settings.toThemeSettings()
    val kind = ColorPages.kindOf(page)
    // In the panel the grid is the list the cursor walks (fixed when the page opened); beside the options nothing walks it yet.
    val palette = if (inPanel) model.customPalette(page) else ColorPages.palette(page, theme.backgroundColor)
    val current =
        when (kind) {
            ColorKind.Background -> theme.backgroundColor
            ColorKind.Text -> theme.textColor
            else -> theme.accentColor
        }
    // The check marks the colour in effect when the page opened (then the one chosen), not the one the ring previews.
    val selected = if (inPanel) model.selectedColour ?: current else current
    // The title, the note and the swatches: what gives way from the top while Plumemu's tray is up.
    val top: @Composable () -> Unit = {
        PanelTitle(stringResource(R.string.settings_custom_title))
        PaletteGrid(palette, selected, focusedIndex, inPanel, touch)
        // Beside the options the sliders show the colour now shown; in the panel, where the sliders have put it.
        ColorSliderRows(
            // On the grid the sliders show the colour being previewed (what the swatch wrote); on a slider they show its own state, which keeps the hue of a grey.
            hsl = { if (inPanel && focusedIndex >= palette.size) model.sliderHsl else Hsl.of(current) },
            reach = remember(page, theme.backgroundColor) { ColorPages.sliderReach(page, theme.backgroundColor) },
            firstIndex = palette.size,
            focusedIndex = if (inPanel) focusedIndex else -1,
            touch = touch,
            finger = { model.sliderFinger },
        )
        if (inPanel) ColorNotice(model)
    }
    val hexIndex = palette.size + ColorSlider.entries.size
    val hex: @Composable () -> Unit = {
        // The colour a finger on a slider shows (read here, so a move recomposes this row and not the panel).
        val shown = model.colorPreview.over(settings).toThemeSettings()
        val dragged =
            when (kind) {
                ColorKind.Background -> shown.backgroundColor
                ColorKind.Text -> shown.textColor
                else -> shown.accentColor
            }
        HexRow(dragged.toComposeColor(), dragged.toHex(), isFocused = inPanel && focusedIndex == hexIndex, model, touch, hexIndex)
    }
    val ownKeyboard = model.hexTyping?.takeIf { inPanel && it.keyboardKind == KeyboardChoice.Plumemu }
    if (ownKeyboard == null) {
        // Scrolls when the text is large (the palette, the message and the hex rows are taller than the panel), keeping the focused item in view.
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag(SettingsTags.COLOR_SCROLL), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
            top()
            hex()
        }
        return
    }
    // Plumemu's keyboard: laid out above its tray while it is up, as the game panel's fields are.
    TypingPanelLayout(
        ownKeyboard,
        keyboardCovers = settingsKeyboardCovers(),
        modifier = Modifier.fillMaxHeight(),
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) { top() }
            Spacer(Modifier.height(PlumDimens.settingsPanelGap))
        },
        body = hex,
    )
}

internal fun Modifier.swatchTap(
    index: Int,
    touch: SettingsTouch,
): Modifier = tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index))
