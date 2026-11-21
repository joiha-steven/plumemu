package com.plumemu.settings.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.collar
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

/*
 * Background, Text color and Accent: the presets, then Color (a strip of hues), Shade (the shades
 * of that hue that read) and the hex code, typed with Plumemu's keyboard or Android's as Settings ›
 * Controls › Keyboard says. Beside the options only the presets show. A colour applies as soon as the
 * cursor reaches it, so the whole screen shows it at once (design/rules.md › Colour).
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
    // A Text page no colour reads on has only its message.
    val blocked = ColorPages.kindOf(option) == ColorKind.Text && ColorPages.textBlockedOn(ColorPages.backgroundOf(settings))
    if (inPanel && ColorPages.kindOf(page) != null && !blocked) {
        ColourPage(option, page, index, settings, model, touch)
    } else {
        PresetsPage(option, index, inPanel, settings, model, touch)
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
    val theme = settings.toThemeSettings()
    val kind = ColorPages.kindOf(option)
    val swatches = presetSwatches(kind, theme)
    if (kind == ColorKind.Text && ColorPages.textBlockedOn(theme.backgroundColor)) {
        // A background an older version stored, which no text colour reads on: say so, offer nothing that would fail.
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
            PanelTitle(stringResource(option.label))
            PanelBody(stringResource(R.string.settings_text_color_blocked))
        }
        return
    }
    PanelHeaderYields(
        Modifier.fillMaxHeight(),
        PlumDimens.settingsPanelGap,
        // The look preview comes first where it fits whole with the swatches; the swatches win, then the title and text.
        leading = { LookPreviewPart(PreviewKind.Look) },
        header = listOf({ PanelTitle(stringResource(option.label)) }, { PanelBody(stringResource(bodyOf(kind))) }),
        main = {
            PresetKeys(kind, swatches, index = focusedIndex, touch)
        },
        rest = { if (inPanel) ColorNotice(model) },
    )
}

private fun presetSwatches(
    kind: ColorKind?,
    theme: com.plumemu.coreui.theme.PlumThemeSettings,
): List<Color> =
    when (kind) {
        ColorKind.Background -> ColorPages.backgroundPresets.map { it.color.toComposeColor() } + ColorPages.vividBackgrounds.map { it.color.toComposeColor() }
        ColorKind.Text -> ColorPages.textPresetsOn(theme.backgroundColor).map { it.toRgb().toComposeColor() }
        else -> ColorPages.accentPresets.map { it.colorFor(theme.textMode).toComposeColor() }
    }

/** The presets' own corner for [kind]: rounded squares for Background, circles for Text and Accent. */
private fun presetCorner(kind: ColorKind?): Dp = if (kind != ColorKind.Accent) PlumDimens.settingsPresetSwatchCorner else PlumDimens.settingsPresetSwatch / 2

/** The preset keys (Accent: then By system); the one at [index] carries the focus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetKeys(
    kind: ColorKind?,
    swatches: List<Color>,
    index: Int,
    touch: SettingsTouch,
) {
    val corner = presetCorner(kind)
    FlowRow(
        modifier = Modifier.onRowTextLine().testTag(COLOUR_PRESETS_TAG).padding(vertical = PlumDimens.settingsSwatchInset),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPresetSwatchGap),
    ) {
        swatches.forEachIndexed { i, color ->
            Swatch(color, corner, isFocused = i == index, Modifier.swatchTap(i, touch), size = Modifier.size(PlumDimens.settingsPresetSwatch), bleed = true)
        }
        if (kind == ColorKind.Accent) {
            BySystemTile(isFocused = ColorPages.bySystemIndex == index, corner = corner, Modifier.swatchTap(ColorPages.bySystemIndex, touch))
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
            .semantics { contentDescription = name }
            // Outside the collar and the clip, so the whole tile dips with a press.
            .pressFeel(isFocused, null, shape)
            .collarBleed(CollarSize.Key)
            .collar(CollarSize.Key, isFocused, corner)
            .size(PlumDimens.settingsPresetSwatch)
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

/**
 * A colour page in the panel: title row (the name of the colour in effect at its right end), presets, Color, Shade, the Color code
 * row and the notice. Plumemu's keyboard lays the rows above its tray while it is up.
 */
@Composable
private fun ColourPage(
    option: SettingsOption,
    page: PanelPage,
    focusedIndex: Int,
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val kind = ColorPages.kindOf(page) ?: return
    val theme = settings.toThemeSettings()
    val colorIndex = model.colorIndex(page)
    // Only the count of shades and so the Color code row's place are read here: a strip move recomposes the page when they change, not at every stop.
    val shadeCount by remember(model, page) { derivedStateOf { model.colorShades(page).size } }
    val hexIndex = colorIndex + 1 + shadeCount
    // The colour in effect, read where it is drawn: a finger on the strip then recomposes those rows and not the panel.
    val shown = { ColorPages.drawnColor(kind, model.colorPreview.over(settings)) }
    val top: @Composable () -> Unit = {
        ColourPageTitle(stringResource(option.label)) { ShownColourName(kind, settings, model) }
        PresetKeys(kind, remember(kind, theme) { presetSwatches(kind, theme) }, focusedIndex, touch)
        ColorStripRow(ColorPages.roleOf(kind), { model.colorStop }, shown, focusedIndex == colorIndex, colorIndex, touch)
        if (shadeCount > 0) ShadeRow({ model.colorShades(page) }, shown, colorIndex + 1, focusedIndex, presetCorner(kind), touch)
    }
    val hex: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
            HexRow(shown, isFocused = focusedIndex == hexIndex, model, touch, hexIndex)
            ColorNotice(model)
        }
    }
    val ownKeyboard = model.hexTyping?.takeIf { it.keyboardKind == KeyboardChoice.Plumemu }
    if (ownKeyboard == null) {
        // Scrolls when the text is large (the rows are taller than the panel), keeping the focused item in view.
        PanelScrollColumn(PlumDimens.settingsPanelGap, tag = SettingsTags.COLOR_SCROLL) {
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

/** The name of the colour in effect (the title row's right end), read here so a drag recomposes this text alone. */
@Composable
private fun RowScope.ShownColourName(
    kind: ColorKind,
    settings: PlumSettings,
    model: SettingsModel,
) {
    val now = model.colorPreview.over(settings)
    val a = now.appearance
    val theme = now.toThemeSettings()
    val name =
        when (kind) {
            ColorKind.Text -> stringResource(textColorName(a.textColor, theme.backgroundColor))
            ColorKind.Background -> colorName(a.background, isBackground = true, a.colorSet())
            ColorKind.Accent -> colorName(a.accent, isBackground = false, a.colorSet())
        }
    ColorName(name)
}

internal fun Modifier.swatchTap(
    index: Int,
    touch: SettingsTouch,
): Modifier = tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index))
