package com.plumemu.settings.screen

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.plumemu.coreui.components.borderDrawn
import com.plumemu.coreui.components.focusPill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.secondscreen.isSecondScreenOn
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.RecentSizeSetting
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.language.AppLanguages
import com.plumemu.language.languageName
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.GridSizeSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TypeRole
import com.plumemu.game.THUMBNAIL_SLOT
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.home.rememberCountFormat
import com.plumemu.settings.colorSet
import com.plumemu.settings.toThemeSettings
import androidx.compose.ui.res.pluralStringResource
import com.plumemu.setup.SetupSwitch

/*
 * The options column's rows (the board's RP5-S1-settings): 40 high, 2 apart, 14 inside, the label
 * at the left and the value at the right: text with », ‹ › for left / right choices, a switch, or a
 * colour dot followed by its name (design/rules.md › Settings).
 */

/** How an option row looks, in the one focus language (design/rules.md › Focus and current). */
internal enum class OptionLook {
    /** The cursor: `sel` fill, accent dot, `strong` text 500, value `meta`. */
    Focused,

    /** Options have focus, another row: `text2`, value `muted`. */
    Normal,

    /** Its panel has the focus: accent text, no fill. */
    Current,

    /** The sections column has focus: the options are a preview, all `muted`. */
    Preview,
}

/** What an option shows at the right. */
@Immutable
internal sealed interface OptionValue {
    data class Text(val text: String) : OptionValue

    data class Switch(val isOn: Boolean) : OptionValue

    /** [live]: the theme colour the dot is drawn in while a finger drags a colour slider (the dot follows it, [color] is the stored one). */
    data class Swatch(val color: Color, val name: String, val hasRing: Boolean, val bySystem: Boolean = false, val live: LiveSwatch? = null) : OptionValue
}

@Composable
internal fun OptionRow(
    option: SettingsOption,
    value: OptionValue,
    look: OptionLook,
    modifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
    label: String = stringResource(option.label),
    tag: String = SettingsTags.option(option),
    valueTag: String = SettingsTags.value(option),
    /** A line under the label (Statistics), up to two lines; Confirm with B has its own. */
    detail: String? = null,
    /** Drawn before the label (Statistics' covers). */
    leading: (@Composable () -> Unit)? = null,
    /** A game's title may need a third line at the largest sizes (Statistics). */
    labelLines: Int = 2,
    /** Statistics: the cover slot's room is kept in a row with no cover, so every row's text starts on one line. */
    keepsLeadingRoom: Boolean = false,
    /** Room above and below the text: Statistics' three-line titles keep what a one-line row has; the two-line Confirm row (audit B5) keeps clear of its ring. */
    verticalPadding: Dp = 0.dp,
    /** Statistics: the page's big figure (the total) in place of the label; the label is then the line under it. */
    headline: String? = null,
    /** Tags the text block (label and detail), so a test can measure where the text starts. */
    textTag: String? = null,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val focused = look == OptionLook.Focused
    val isStatusLine = option == SettingsOption.ScraperStatus && look != OptionLook.Current
    val labelColor: () -> Color =
        when {
            // The box art status is information only: its sentence is the row's label, drawn like a value (secondary text colour).
            isStatusLine -> if (focused) { { colors.meta } } else { { colors.muted } }
            look == OptionLook.Focused -> { { colors.strong } }
            look == OptionLook.Normal -> { { colors.text2 } }
            look == OptionLook.Current -> { { colors.accent } }
            else -> { { colors.muted } }
        }
    val valueColor: () -> Color = if (focused) { { colors.meta } } else { { colors.muted } }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PlumTheme.layout.rowHeight)
                .focusPill(focused)
                .padding(horizontal = PlumDimens.gameRowPadding, vertical = verticalPadding)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(PlumDimens.focusDotGap))
        } else if (keepsLeadingRoom) {
            Spacer(Modifier.width(THUMBNAIL_SLOT + PlumDimens.focusDotGap))
        }
        Column(Modifier.weight(1f).then(if (textTag != null) Modifier.testTag(textTag) else Modifier)) {
            val style = if (focused || look == OptionLook.Current) type.settingsOptionFocused else type.settingsOption
            if (headline != null) {
                // The figure leads; the label under it is the small line (the second screen's card does the same).
                val figureColor: () -> Color = if (look == OptionLook.Normal) { { colors.text } } else labelColor
                Box(valueModifier.testTag(valueTag)) { BasicText(headline, style = type[TypeRole.SecondTitleCompact], color = figureColor, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                BasicText(label, style = type.smallFact, color = valueColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                BasicText(label, style = style, color = labelColor, maxLines = labelLines, overflow = TextOverflow.Ellipsis)
            }
            // The confirm option names the buttons on a second line; the board's 16 sp label would not fit.
            val shownDetail = detail ?: if (option == SettingsOption.ConfirmBottom) stringResource(R.string.settings_confirm_bottom_detail) else null
            if (shownDetail != null) BasicText(shownDetail, style = type.smallFact, color = valueColor, maxLines = if (detail != null) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        if (headline == null) {
            Spacer(Modifier.width(PlumDimens.rowTrailingGap))
            Box(valueModifier.testTag(valueTag)) { OptionValueView(value, valueColor) }
        }
    }
}

@Composable
private fun OptionValueView(
    value: OptionValue,
    color: () -> Color,
) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.meta
    when (value) {
        // A long value (an emulator, "Ask on the first game") may take two lines, in at most half the row.
        is OptionValue.Text ->
            if (value.text.isNotEmpty()) BasicText(value.text, style = style, color = color, maxLines = 2, modifier = Modifier.widthIn(max = PlumTheme.layout.settingsOptionsWidth / 2))
        is OptionValue.Switch ->
            SetupSwitch(
                isOn = value.isOn,
                stateText = stringResource(if (value.isOn) R.string.settings_switch_on else R.string.settings_switch_off),
            )
        is OptionValue.Swatch ->
            SwatchValue(value, style, color)
    }
}

/** The value [option] (row [index] of its section) shows for [settings] and [content]. */
@Composable
internal fun optionValue(
    option: SettingsOption,
    settings: PlumSettings,
    versionName: String,
    content: SettingsContent = SettingsContent(),
    index: Int = 0,
    isDetecting: Boolean = false,
): OptionValue {
    // Per appearance, not per row and recomposition: drawing a failing stored colour settles it (a search).
    val theme = remember(settings.appearance, settings.advanced.animations) { settings.toThemeSettings() }
    val colorSet = remember(settings.appearance) { settings.appearance.colorSet() }
    return when (option) {
        SettingsOption.Background ->
            OptionValue.Swatch(theme.backgroundColor.toComposeColor(), colorName(settings.appearance.background, isBackground = true, colorSet), hasRing = true, live = LiveSwatch.Background)
        SettingsOption.Accent ->
            OptionValue.Swatch(theme.accentColor.toComposeColor(), colorName(settings.appearance.accent, isBackground = false, colorSet), hasRing = false, bySystem = settings.appearance.accent is ColorSetting.BySystem, live = LiveSwatch.Accent)
        SettingsOption.TextColor ->
            OptionValue.Swatch(theme.textColor.toComposeColor(), stringResource(textColorName(settings.appearance.textColor, theme.backgroundColor)), hasRing = false, live = LiveSwatch.Text)
        SettingsOption.Language ->
            OptionValue.Text(stringResource(R.string.settings_value_opens, languageName(AppLanguages.byTag(settings.appearance.language)?.tag)))
        SettingsOption.Colors, SettingsOption.Font, SettingsOption.TextSize, SettingsOption.TextWeight, SettingsOption.LeftEdge, SettingsOption.GridSize, SettingsOption.SortGames, SettingsOption.RecentSize, SettingsOption.SystemOrder,
        SettingsOption.SecondScreenInGame, SettingsOption.BoxArtRegion, SettingsOption.Keyboard, SettingsOption.SwitchSystemWith, SettingsOption.Sounds,
        ->
            OptionValue.Text(stringResource(R.string.settings_value_choice, stringResource(choiceName(option, option.choiceIndex(settings, textSizeInUse())))))
        SettingsOption.ConfirmBottom, SettingsOption.Animations, SettingsOption.Vibration, SettingsOption.UsageStatistics, SettingsOption.ScanOnStartup, SettingsOption.CheckUpdatesDaily,
        SettingsOption.HoldToScrollFaster, SettingsOption.PushRightToPlay, SettingsOption.ShowButtonHints, SettingsOption.ShowApps, SettingsOption.ShowClock, SettingsOption.ShowBattery, SettingsOption.SystemInfo,
        -> OptionValue.Switch(option.switchValue(settings))
        SettingsOption.SecondScreen -> OptionValue.Switch(isSecondScreenOn(settings.secondScreen, content.secondScreen))
        SettingsOption.RomFolders -> {
            val count = content.folders.size
            OptionValue.Text(stringResource(R.string.settings_value_opens, pluralStringResource(R.plurals.settings_folders_count, count, rememberCountFormat().format(count))))
        }
        SettingsOption.Systems -> OptionValue.Text(stringResource(R.string.settings_value_opens, systemsShownText(content, settings)))
        SettingsOption.Rescan -> OptionValue.Text(if (content.isScanning) stringResource(R.string.settings_rescan_running) else "")
        SettingsOption.SystemEmulator -> OptionValue.Text(stringResource(R.string.settings_value_opens, emulatorValue(content.systems.getOrNull(index))))
        SettingsOption.UnknownFolder -> OptionValue.Text(stringResource(R.string.settings_value_opens, unknownFolderValue(content, content.unknownFolderAtRow(index))))
        SettingsOption.DetectEmulators -> OptionValue.Text(if (isDetecting) stringResource(R.string.settings_detect_running) else "")
        // Shown as the board's switch; A asks Android, which is the only one that can change it.
        SettingsOption.UseAsHome -> OptionValue.Switch(content.isHomeApp)
        SettingsOption.Version -> OptionValue.Text(versionName)
        SettingsOption.UpdateAvailable -> OptionValue.Text(updateRowValue(content.update))
        SettingsOption.CheckForUpdates -> OptionValue.Text(checkRowValue(content.update))
        SettingsOption.Backup -> OptionValue.Text(backupRowValue(content.backup))
        SettingsOption.Restore -> OptionValue.Text(restoreRowValue(content.restore))
        SettingsOption.UndoRestore -> OptionValue.Text(undoRowValue(content))
        SettingsOption.ResetAll, SettingsOption.ResetLibrary, SettingsOption.ClearRecent, SettingsOption.Licenses, SettingsOption.HiddenGames,
        SettingsOption.UnidentifiedGames, SettingsOption.ReportProblem,
        ->
            OptionValue.Text(stringResource(R.string.settings_value_chevron))
        // A page to read beside the row: A opens nothing, so no "»".
        SettingsOption.Privacy, SettingsOption.ExportLogs -> OptionValue.Text("")
        // Signed in: how box art is going in a word or two ("340 of 2,054", "Paused"), else the name; signed out: "Sign in »".
        SettingsOption.ScraperAccount ->
            when (val account = content.account) {
                is AccountStatus.SignedIn ->
                    OptionValue.Text(stringResource(R.string.settings_value_opens, boxArtShortText(boxArtLineOf(account, content.boxArt)) ?: account.username))
                else -> OptionValue.Text(stringResource(R.string.settings_value_opens, stringResource(R.string.settings_account_value_sign_in)))
            }
        // The line is the row's label (it may need two lines); nothing on the right.
        SettingsOption.ScraperStatus -> OptionValue.Text("")
        SettingsOption.Statistic -> OptionValue.Text(statisticValue(content.statisticRows.getOrNull(index), versionName))
    }
}

/** The text size drawing the screen now, as the setting: the person's pick, or the width's default. */
@Composable
internal fun textSizeInUse(): TextSizeSetting = TextSizeSetting.valueOf(PlumTheme.type.textSize.name)

/**
 * What an unknown folder's row says at the right: "3 files" while it waits for an answer, then the system or
 * "Ignored"; "Saving…" while the answer is stored (it waits for a running scan).
 */
@Composable
internal fun unknownFolderValue(
    content: SettingsContent,
    row: SettingsFolderRow?,
): String {
    row ?: return ""
    return when {
        row.key in content.answeringFolders -> stringResource(R.string.settings_unknown_folder_saving)
        row.answer is FolderAnswer.OfSystem -> row.answer.name
        row.answer == FolderAnswer.Ignored -> stringResource(R.string.settings_unknown_folder_ignored)
        // An answer that stopped working (its system left the platform data) has no count until the next scan lists it.
        row.files <= 0 -> stringResource(R.string.settings_unknown_folder_choose)
        else -> pluralStringResource(R.plurals.settings_unknown_folder_files, row.files, rememberCountFormat().format(row.files))
    }
}

/** What a system's row says starts its games: the person's choice, "Asks on the first game", or "None installed". */
@Composable
internal fun emulatorValue(system: SystemEmulators?): String {
    val chosen = system?.chosen
    return when {
        chosen != null && chosen.isInstalled -> chosen.name
        chosen != null -> stringResource(R.string.settings_emulator_missing, chosen.name)
        !system?.installed.isNullOrEmpty() -> stringResource(R.string.settings_emulator_ask)
        else -> stringResource(R.string.game_info_no_emulator)
    }
}

/** The label of row [index] of [option]: a system's name for the Emulators rows, "Hidden games (n)" with its count. */
@Composable
internal fun optionLabel(
    option: SettingsOption,
    content: SettingsContent,
    index: Int,
): String =
    when (option) {
        SettingsOption.SystemEmulator -> content.systems.getOrNull(index)?.name.orEmpty()
        SettingsOption.UnknownFolder ->
            content.unknownFolderAtRow(index)?.let { row -> if (row.answer == FolderAnswer.Unanswered) stringResource(R.string.settings_unknown_folder, row.name) else row.name }.orEmpty()
        SettingsOption.HiddenGames -> stringResource(R.string.settings_hidden_games, rememberCountFormat().format(content.hiddenGames.size))
        SettingsOption.UnidentifiedGames -> stringResource(R.string.settings_unidentified_games, rememberCountFormat().format(content.unidentifiedGames.size))
        SettingsOption.ScraperStatus -> boxArtLineOf(content.account, content.boxArt)?.let { boxArtLineText(it) }.orEmpty()
        SettingsOption.Statistic -> statisticLabel(content.statisticRows.getOrNull(index))
        else -> stringResource(option.label)
    }

/** The name of a ‹ › option's value [index]. */
internal fun choiceName(
    option: SettingsOption,
    index: Int,
): Int =
    when (option) {
        // The sets in order, then Custom (SettingsOption.choiceIndex counts the same way).
        SettingsOption.Colors -> ColorSet.entries.getOrNull(index)?.let(::colorSetName) ?: R.string.settings_color_custom
        SettingsOption.Font -> fontName(FontSetting.entries[index])
        SettingsOption.TextSize ->
            when (TextSizeSetting.entries[index]) {
                TextSizeSetting.Small -> R.string.settings_size_small
                TextSizeSetting.Medium -> R.string.settings_size_medium
                TextSizeSetting.Large -> R.string.settings_size_large
                TextSizeSetting.Larger -> R.string.settings_size_larger
                TextSizeSetting.Huge -> R.string.settings_size_huge
                TextSizeSetting.Largest -> R.string.settings_size_largest
            }
        SettingsOption.TextWeight ->
            when (TextWeightSetting.entries[index]) {
                TextWeightSetting.Thin -> R.string.settings_weight_thin
                TextWeightSetting.Medium -> R.string.settings_weight_medium
                TextWeightSetting.Bold -> R.string.settings_weight_bold
            }
        SettingsOption.LeftEdge -> leftEdgeNameRes(index)
        SettingsOption.GridSize ->
            when (GridSizeSetting.entries[index]) {
                GridSizeSetting.Small -> R.string.settings_size_small
                GridSizeSetting.Medium -> R.string.settings_size_medium
                GridSizeSetting.Large -> R.string.settings_size_large
            }
        SettingsOption.SortGames ->
            when (SortGamesSetting.entries[index]) {
                SortGamesSetting.Name -> R.string.settings_sort_by_name
                SortGamesSetting.LastPlayed -> R.string.settings_sort_by_last_played
                SortGamesSetting.Year -> R.string.settings_sort_by_year
            }
        SettingsOption.RecentSize ->
            when (RecentSizeSetting.entries[index]) {
                RecentSizeSetting.Ten -> R.string.settings_recent_size_10
                RecentSizeSetting.Twenty -> R.string.settings_recent_size_20
                RecentSizeSetting.Fifty -> R.string.settings_recent_size_50
            }
        SettingsOption.SystemOrder -> systemOrderName(index)
        SettingsOption.SwitchSystemWith -> if (index == SwitchSystemWithSetting.Triggers.ordinal) R.string.settings_switch_system_triggers else R.string.settings_switch_system_bumpers
        SettingsOption.Keyboard -> if (index == KeyboardSetting.Plumemu.ordinal) R.string.settings_keyboard_plumemu else R.string.settings_keyboard_android
        SettingsOption.Sounds ->
            when (SoundLevel.entries[index]) {
                SoundLevel.Off -> R.string.settings_sounds_off
                SoundLevel.Quiet -> R.string.settings_sounds_quiet
                SoundLevel.Loud -> R.string.settings_sounds_loud
            }
        SettingsOption.SecondScreenInGame ->
            when (InGameSetting.entries[index]) {
                InGameSetting.Clock -> R.string.settings_second_screen_clock
                InGameSetting.Black -> R.string.settings_second_screen_black
            }
        SettingsOption.BoxArtRegion -> when (BoxArtRegionSetting.entries[index]) {
            BoxArtRegionSetting.Usa -> R.string.settings_region_usa
            BoxArtRegionSetting.Europe -> R.string.settings_region_europe
            BoxArtRegionSetting.Japan -> R.string.settings_region_japan
            BoxArtRegionSetting.World -> R.string.settings_region_world
        }
        else -> R.string.settings_value_chevron
    }

/**
 * The name the Text color row and panel show: Light and Dark by name, a tinted preset offered on [background]
 * by its own name (Cream, Navy…), any other colour "Custom" (also when no background is given).
 */
internal fun textColorName(
    text: TextColorSetting,
    background: Rgb? = null,
): Int =
    when (text) {
        TextColorSetting.Light -> R.string.settings_text_light
        TextColorSetting.Dark -> R.string.settings_text_dark
        is TextColorSetting.Custom -> background?.let { ColorPages.textPresetNameOn(it, text) } ?: R.string.settings_color_custom
    }

internal fun fontName(font: FontSetting): Int =
    when (font) {
        FontSetting.Sans -> R.string.settings_font_sans
        FontSetting.Serif -> R.string.settings_font_serif
        FontSetting.Mono -> R.string.settings_font_mono
    }

/**
 * The name the Background or Accent row shows for [setting]. An own colour is "Custom", unless it is
 * the colour of a ready-made set (the current one first) or of a vivid preset: then that name ("Pocket"), not a code.
 */
@Composable
internal fun colorName(
    setting: ColorSetting,
    isBackground: Boolean,
    set: ColorSet?,
): String =
    when (setting) {
        is ColorSetting.Custom -> {
            // Each row is named by its own colour: the set or vivid preset whose colour it is, even when another row has moved away from that set.
            val rgb = setting.rgb and RGB_MASK
            val ofPrevious = set?.takeIf { !isBackground && Rgb(rgb) in it.previousAccents }
            val named = ofPrevious ?: set?.takeIf { (if (isBackground) it.backgroundColor else it.accentColor).value == rgb } ?: ColorSet.entries.firstOrNull { (if (isBackground) it.backgroundColor else it.accentColor).value == rgb }
                ?: ColorSet.entries.takeUnless { isBackground }?.firstOrNull { Rgb(rgb) in it.previousAccents }
            val vivid = if (isBackground) ColorPages.vividBackgrounds.firstOrNull { it.color.value == rgb } else null
            when {
                vivid != null -> stringResource(vivid.name)
                named != null -> stringResource(colorSetName(named))
                else -> stringResource(R.string.settings_color_custom)
            }
        }
        is ColorSetting.BySystem -> stringResource(R.string.settings_accent_by_system)
        is ColorSetting.Preset ->
            if (isBackground) {
                stringResource(backgroundName(BackgroundPreset.entries.firstOrNull { it.name == setting.name } ?: BackgroundPreset.Black))
            } else {
                stringResource(accentName(AccentPreset.entries.firstOrNull { it.name == setting.name } ?: AccentPreset.Default))
            }
    }

/** Settings › Appearance › Colors: a ready-made set's name (never a console's). */
internal fun colorSetName(set: ColorSet): Int =
    when (set) {
        ColorSet.Plumemu -> R.string.settings_colors_plumemu
        ColorSet.Paper -> R.string.settings_colors_paper
        ColorSet.Pocket -> R.string.settings_colors_pocket
        ColorSet.DotMatrix -> R.string.settings_colors_dot_matrix
        ColorSet.Super -> R.string.settings_colors_super
        ColorSet.Indigo -> R.string.settings_colors_indigo
        ColorSet.Charcoal -> R.string.settings_colors_charcoal
        ColorSet.Ivory -> R.string.settings_colors_ivory
        ColorSet.Banana -> R.string.settings_colors_banana
        ColorSet.Cream -> R.string.settings_colors_cream
    }

private const val RGB_MASK = 0xFFFFFF

internal fun backgroundName(preset: BackgroundPreset): Int =
    when (preset) {
        BackgroundPreset.Graphite -> R.string.settings_bg_graphite
        BackgroundPreset.Black -> R.string.settings_bg_black
        BackgroundPreset.Midnight -> R.string.settings_bg_midnight
        BackgroundPreset.Plum -> R.string.settings_bg_plum
        BackgroundPreset.Forest -> R.string.settings_bg_forest
        BackgroundPreset.Paper -> R.string.settings_bg_paper
        BackgroundPreset.Sand -> R.string.settings_bg_sand
        BackgroundPreset.Mist -> R.string.settings_bg_mist
    }

internal fun accentName(preset: AccentPreset): Int =
    when (preset) {
        AccentPreset.Red -> R.string.settings_accent_red
        AccentPreset.Amber -> R.string.settings_accent_amber
        AccentPreset.Lilac -> R.string.settings_accent_lilac
        AccentPreset.Blue -> R.string.settings_accent_blue
        AccentPreset.Green -> R.string.settings_accent_green
        AccentPreset.Mono -> R.string.settings_accent_mono
    }

/** A tap target that accessibility services can press too; [onTap] acts through the model. */
internal fun Modifier.tapTarget(onTap: () -> Unit): Modifier =
    semantics {
        role = Role.Button
        onClick {
            onTap()
            true
        }
    }.pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) }

internal object SettingsTags {
    /** The multi-colour glyph the Accent row shows for "By system". */
    const val VALUE_BY_SYSTEM = "settings_value_by_system"

    const val SCREEN = "settings_screen"
    const val STRIP = "settings_strip"
    const val SECTIONS = "settings_sections"
    const val OPTIONS = "settings_options"
    const val PANEL = "settings_panel"
    const val PANEL_LIST = "settings_panel_list"

    /** Library › Systems: the panel, its list, and in each row the name, the "Hidden" label and the drag handle. */
    const val SYSTEMS_PANEL = "settings_systems_panel"

    /** The hint bar's "↑↓ Move" while a system is carried, and "Y Hide" / "Y Show" on a system. */
    const val HINT_MOVE = "settings_hint_move"
    const val HINT_HIDE = "settings_hint_hide"
    const val SYSTEMS_LIST = "settings_systems_list"
    const val SYSTEM_NAME = "settings_system_name"
    const val SYSTEM_COUNT = "settings_system_count"
    const val SYSTEM_HIDDEN = "settings_system_hidden"
    const val SYSTEM_HANDLE = "settings_system_handle"
    const val ACCOUNT_PANEL = "settings_account_panel"

    /** Advanced › Back up…'s panel, which scrolls when the text is large. */
    const val BACKUP_PANEL = "settings_backup_panel"
    const val UPDATE_PANEL = "settings_update_panel"
    const val RESTORE_PANEL = "settings_restore_panel"
    const val UNDO_PANEL = "settings_undo_restore_panel"
    const val BOX_ART_SYSTEMS = "settings_box_art_systems"
    const val CHECK_MARK = "settings_check_mark"
    const val HEX_FIELD = "settings_hex_field"

    /** The typed code and the current code shown in its place while empty (Plumemu's keyboard). */
    const val HEX_TEXT = "settings_hex_field_text"
    const val HEX_PLACEHOLDER = "settings_hex_field_placeholder"

    /** Plumemu's keyboard tray over Settings. */
    const val TRAY = "settings_keyboard_tray"

    /** The hint bar while a field's tray is up (its hints are tagged as the main screen's, `HomeTags.hint`). */
    const val TYPING_HINTS = "settings_typing_hints"
    const val HEX_ROW = "settings_hex_row"
    const val HEX_USE = "settings_hex_use"
    const val HEX_ERROR = "settings_hex_error"
    const val COLOR_NOTICE = "settings_color_notice"

    /** An own-colour page, which scrolls when the text is large. */
    const val COLOR_SCROLL = "settings_color_scroll"

    /** What is under the hex field with Plumemu's keyboard ("Use this colour", the wrong-code line): it scrolls above the tray. */
    const val HEX_UNDER = "settings_hex_under"
    const val LICENSE_LIST = "settings_license_list"
    const val LICENSE_TEXT = "settings_license_text"
    const val HINT_CONFIRM = "settings_hint_confirm"
    const val HINT_BACK = "settings_hint_back"
    const val HINT_SECTION = "settings_hint_section"
    const val HINT_MORE = "settings_hint_more"
    const val SAVE_FAILED = "settings_save_failed"

    fun section(section: SettingsSection) = "settings_section_${section.name}"

    const val NOTICE = "settings_notice"
    const val OPTION_LIST = "settings_option_list"
    const val LOAD_FAILED = "settings_load_failed"

    fun option(option: SettingsOption) = "settings_option_${option.name}"

    fun value(option: SettingsOption) = "settings_value_${option.name}"

    /** A row of Emulators: one per system, by its index among them. */
    fun system(index: Int) = "settings_option_system_$index"

    /** A row of Library's unknown folders, by the folder's ids (the list reorders as folders are answered). */
    fun unknownFolder(key: FolderKey) = "settings_option_unknown_${key.folderId}_${key.relativePath}"

    fun unknownFolderValue(key: FolderKey) = "settings_value_unknown_${key.folderId}_${key.relativePath}"

    const val UNKNOWN_FOLDER_PANEL = "settings_unknown_folder_panel"

    fun systemValue(index: Int) = "settings_value_system_$index"

    fun panelItem(index: Int) = "settings_panel_item_$index"

    /** Appearance › Colors' panel, each tile's picture and each tile's name. */
    const val COLOR_SETS_PANEL = "settings_color_sets_panel"
    const val COLOR_TILE = "settings_color_tile"
    const val COLOR_TILE_NAME = "settings_color_tile_name"

    /** A row of Statistics, its figure and its cover, by the row's index. */
    fun statistic(index: Int) = "settings_option_statistic_$index"

    fun statisticValue(index: Int) = "settings_value_statistic_$index"

    const val STATISTIC_GROUP = "settings_statistic_group"

    fun statisticText(index: Int) = "settings_statistic_text_$index"

    fun statisticCover(index: Int) = "settings_statistic_cover_$index"
}
