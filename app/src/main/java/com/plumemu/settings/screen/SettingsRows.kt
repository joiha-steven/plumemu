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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.account.RaAccountStatus
import com.plumemu.coredata.settings.BoxArtRegionSetting
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.KeyboardSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coredata.settings.SoundLevel
import com.plumemu.coredata.settings.SwitchSystemWithSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.TextWeightSetting
import com.plumemu.coreui.components.KeyState
import com.plumemu.coreui.components.StripeAnchor
import com.plumemu.coreui.components.borderDrawn
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.components.rememberKeyState
import com.plumemu.coreui.theme.AccentPreset
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.FocusSlab
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.graph.BackupState
import com.plumemu.graph.RestoreState
import com.plumemu.home.rememberCountFormat
import com.plumemu.language.AppLanguages
import com.plumemu.language.languageName
import com.plumemu.secondscreen.isSecondScreenOn
import com.plumemu.settings.colorSet
import com.plumemu.settings.toThemeSettings
import com.plumemu.setup.SetupSwitch

/*
 * The options column's rows (the board's RP5-S1-settings): 40 high, 2 apart, 14 inside, the label
 * at the left and the value at the right, in normal text brightness: plain text, a choice (chevrons
 * at its sides on the focused row), text ending in a chevron where A opens a page, a switch, or a
 * colour dot followed by its name (design/rules.md › Settings).
 */

/** How an option row looks, in the one focus language (design/rules.md › Focus and current). */
internal enum class OptionLook {
    /** The cursor: `sel` fill, accent dot, `strong` text 500, value `text`. */
    Focused,

    /** Options have focus, another row: `text2`, value `text`. */
    Normal,

    /** Its panel has the focus: accent text, no fill. */
    Current,

    /** The sections column has focus: the options are a preview, all `muted` (the value too). */
    Preview,
}

/** The words of a value that is text, a choice or an opening row; null for a switch or a swatch. */
internal val OptionValue.words: String?
    get() =
        when (this) {
            is OptionValue.Text -> text
            is OptionValue.Choice -> text
            is OptionValue.Opens -> text
            is OptionValue.Switch, is OptionValue.Swatch -> null
        }

/** What an option shows at the right. */
@Immutable
internal sealed interface OptionValue {
    /** Plain text: a figure, a version, a state word; nothing to step or open. */
    data class Text(val text: String) : OptionValue

    /**
     * A value that Left / Right step: its chevrons show on the focused row only. [opensPanel] (Colors, whose A opens its tiles) keeps the
     * right chevron shown at rest as every row that opens a page does; the left one still comes with the focus.
     */
    data class Choice(val text: String, val opensPanel: Boolean = false) : OptionValue

    /** A row that opens a page: the text (empty for none) ends in a chevron; [isSignedIn] puts an accent check before it. */
    data class Opens(val text: String, val isSignedIn: Boolean = false) : OptionValue

    data class Switch(val isOn: Boolean) : OptionValue

    /** [live]: the theme colour the dot is drawn in while a finger drags a colour slider (the dot follows it, [color] is the stored one). */
    data class Swatch(val color: Color, val name: String, val hasRing: Boolean, val bySystem: Boolean = false, val live: LiveSwatch? = null) : OptionValue
}

@Composable
private fun optionLabelStyle(
    isBold: Boolean,
    isPanelSized: Boolean,
): TextStyle {
    val type = PlumTheme.type
    return when {
        isPanelSized -> if (isBold) type.settingsPanelItemFocused else type.meta
        else -> if (isBold) type.settingsOptionFocused else type.settingsOption
    }
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
    /** Room above and below the text: the two-line Confirm row (audit B5) keeps clear of its ring. */
    verticalPadding: Dp = 0.dp,
    /** The value on its own line under the label, for a column too narrow to hold both whole. */
    valueBelow: Boolean = false,
    /** The label never wraps: a brand's one long word must not break inside it, and a value that does not fit beside it goes under it, whole ([LabelThenValue]). */
    keepsLabelOnOneLine: Boolean = option == SettingsOption.ScraperAccount || option == SettingsOption.RetroAchievementsAccount,
    /** A row heading a list of panel rows: its label is their size. */
    isPanelSized: Boolean = false,
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
            look == OptionLook.Normal -> { { colors.text } }
            look == OptionLook.Current -> { { colors.accent } }
            else -> { { colors.muted } }
        }
    // The line under a label (Confirm with B) is secondary text: dimmer on every row but the focused one.
    val secondaryColor: () -> Color = if (focused) { { colors.meta } } else { { colors.muted } }
    // Values are not dimmed (decision 0011): a row at rest reads them in the board's body ink, a focused one in the slab's bright trailing ink
    // (the games list's region); only the preview, where the whole column is dimmed, mutes them.
    val valueColor: () -> Color =
        when (look) {
            OptionLook.Preview -> { { colors.muted } }
            OptionLook.Normal -> { { colors.body } }
            OptionLook.Focused -> { { colors.slab.region } }
            else -> { { colors.text } }
        }
    // A switch's knob widens with a tap anywhere on its row.
    val knob = rememberKeyState()
    // The accent bar of the two-line Confirm row stands beside its label, not beside the line under it.
    val anchor = remember { StripeAnchor() }
    val titleAnchored = option == SettingsOption.ConfirmBottom || valueBelow || keepsLabelOnOneLine
    // On a screen too narrow for the preview column the values lie past its edge: none is ever whole there, and none shows a sliver of itself.
    val layout = PlumTheme.layout
    val valuePastScreen = look == OptionLook.Preview && layout.widthClass == WidthClass.Narrow && layout.settingsOptionsPreviewWidth == layout.settingsOptionsWidth
    val valueShown = if (valuePastScreen) Modifier.alpha(0f) else Modifier
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PlumTheme.layout.rowHeight)
                .focusPill(focused, mirror = knob, stripe = if (titleAnchored) anchor else null)
                .padding(start = PlumDimens.gameRowPadding, end = PlumDimens.panelTextEnd, top = verticalPadding, bottom = verticalPadding)
                .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = optionLabelStyle(isBold = focused || look == OptionLook.Current, isPanelSized)
        if (keepsLabelOnOneLine) {
            val leadRoom = remember { ValueLeadRoom() }
            LabelThenValue(PlumDimens.rowTrailingGap, PlumDimens.settingsTwoLineRowPadding, leadRoom, Modifier.weight(1f)) {
                BasicText(label, style = style, color = labelColor, maxLines = 1, softWrap = false, onTextLayout = anchor::measured, modifier = anchor.title())
                Box(valueModifier.then(valueShown).testTag(valueTag)) { OptionValueView(value, valueColor, focused, valueTag, knob, leadRoom) }
            }
            return@Row
        }
        val labelPart =
            @Composable {
                Column {
                    BasicText(label, style = style, color = labelColor, maxLines = 2, overflow = TextOverflow.Ellipsis, onTextLayout = if (titleAnchored) anchor::measured else null, modifier = if (titleAnchored) anchor.title() else Modifier)
                    // The confirm option names the buttons on a second line; the board's 16 sp label would not fit.
                    if (option == SettingsOption.ConfirmBottom) {
                        BasicText(stringResource(R.string.settings_confirm_bottom_detail), style = type.smallFact, color = secondaryColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (valueBelow) Box(valueModifier.testTag(valueTag)) { OptionValueView(value, valueColor, focused, valueTag, knob) }
                }
            }
        if (valueBelow) {
            Box(Modifier.weight(1f)) { labelPart() }
            return@Row
        }
        val measurer = rememberTextMeasurer()
        val boldStyle = optionLabelStyle(isBold = true, isPanelSized)
        LabelBesideValue(
            gap = PlumDimens.rowTrailingGap,
            leastGap = PlumDimens.settingsValueGap,
            // A switch is as tall as a line and a half of text and sits on its row as it is.
            padding = if (verticalPadding > 0.dp || value is OptionValue.Switch) 0.dp else PlumDimens.settingsTwoLineRowPadding,
            boldLabelWidth = { measurer.measure(label, boldStyle, softWrap = false, maxLines = 1).size.width },
            modifier = Modifier.weight(1f),
        ) {
            labelPart()
            Box(valueModifier.then(valueShown).testTag(valueTag)) { OptionValueView(value, valueColor, focused, valueTag, knob) }
        }
    }
}

@Composable
private fun OptionValueView(
    value: OptionValue,
    color: () -> Color,
    focused: Boolean,
    valueTag: String,
    knob: KeyState,
    leadRoom: ValueLeadRoom? = null,
) {
    val colors = PlumTheme.colors
    // On the focused row the value takes the row's weight, as the board's focused trailing text does.
    val style = if (focused) PlumTheme.type.meta.copy(fontWeight = FontWeight(FocusSlab.YEAR_WEIGHT)) else PlumTheme.type.meta
    // The marks are dimmed text; a choice's chevrons keep their room while hidden so the value never moves with focus.
    val dim: () -> Color = { colors.muted }
    val hidden: () -> Color = { Color.Transparent }
    when (value) {
        is OptionValue.Text ->
            if (value.text.isNotEmpty()) BasicText(value.text, style = style, color = color, maxLines = 2)
        is OptionValue.Choice ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.valueLead(leadRoom), verticalAlignment = Alignment.CenterVertically) {
                    ValueChevron(ChevronSide.Left, style, if (focused) dim else hidden, tag = SettingsTags.chevronLeft(valueTag))
                    Spacer(Modifier.width(PlumDimens.settingsValueGap))
                }
                BasicText(value.text, style = style, color = color, maxLines = 2, modifier = Modifier.weight(1f, fill = false).testTag(SettingsTags.valueText(valueTag)))
                Spacer(Modifier.width(PlumDimens.settingsValueGap))
                ValueChevron(ChevronSide.Right, style, if (focused || value.opensPanel) dim else hidden, tag = SettingsTags.chevronRight(valueTag))
            }
        is OptionValue.Opens ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isSignedIn) {
                    ValueCheck(style, { colors.accent }, tag = SettingsTags.check(valueTag))
                    Spacer(Modifier.width(PlumDimens.settingsValueGap))
                }
                if (value.text.isNotEmpty()) {
                    BasicText(value.text, style = style, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).testTag(SettingsTags.valueText(valueTag)))
                    Spacer(Modifier.width(PlumDimens.settingsValueGap))
                }
                ValueChevron(ChevronSide.Right, style, dim, tag = SettingsTags.chevronRight(valueTag))
            }
        is OptionValue.Switch ->
            SetupSwitch(
                isOn = value.isOn,
                stateText = stringResource(if (value.isOn) R.string.settings_switch_on else R.string.settings_switch_off),
                isFocused = focused,
                key = knob,
            )
        is OptionValue.Swatch ->
            SwatchValue(value, style, color, valueTag)
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
            OptionValue.Opens(languageName(AppLanguages.byTag(settings.appearance.language)?.tag))
        SettingsOption.Colors -> OptionValue.Choice(stringResource(choiceName(option, option.choiceIndex(settings, textSizeInUse()))), opensPanel = true)
        SettingsOption.Font, SettingsOption.TextSize, SettingsOption.TextWeight, SettingsOption.LeftEdge, SettingsOption.GridSize, SettingsOption.SortGames, SettingsOption.SystemOrder,
        SettingsOption.SecondScreenInGame, SettingsOption.BoxArtRegion, SettingsOption.Keyboard, SettingsOption.SwitchSystemWith, SettingsOption.Sounds,
        ->
            OptionValue.Choice(stringResource(choiceName(option, option.choiceIndex(settings, textSizeInUse()))))
        SettingsOption.ConfirmBottom, SettingsOption.Animations, SettingsOption.Vibration, SettingsOption.UsageStatistics, SettingsOption.CheckUpdatesDaily,
        SettingsOption.PushRightToPlay, SettingsOption.ShowButtonHints, SettingsOption.ShowApps, SettingsOption.ClockAndBattery, SettingsOption.SystemInfo,
        -> OptionValue.Switch(option.switchValue(settings))
        SettingsOption.SecondScreen -> OptionValue.Switch(isSecondScreenOn(settings.secondScreen, content.secondScreen))
        SettingsOption.RomFolders -> {
            val count = content.folders.size
            OptionValue.Opens(pluralStringResource(R.plurals.settings_folders_count, count, rememberCountFormat().format(count)))
        }
        SettingsOption.Systems -> OptionValue.Opens(systemsShownText(content, settings))
        SettingsOption.Rescan -> OptionValue.Text(if (content.isScanning) stringResource(R.string.settings_rescan_running) else "")
        SettingsOption.SystemEmulator -> OptionValue.Opens(emulatorValue(content.systems.getOrNull(index)))
        SettingsOption.FilesNotAdded -> OptionValue.Opens(filesNotAddedValue(content))
        // Shown as the board's switch; A asks Android, which is the only one that can change it.
        SettingsOption.UseAsHome -> OptionValue.Switch(content.isHomeApp)
        SettingsOption.Version -> OptionValue.Text(versionName)
        SettingsOption.Updates -> updatesRowValue(content.update)
        SettingsOption.BackupAndRestore -> backupAndRestoreValue(content)
        SettingsOption.Backup -> opensUnlessBusy(backupRowValue(content.backup), content.backup is BackupState.Running)
        SettingsOption.Restore -> opensUnlessBusy(restoreRowValue(content.restore), content.restore.isRunning())
        SettingsOption.UndoRestore -> opensUnlessBusy(undoRowValue(content), content.restore.let { it is RestoreState.Replacing && it.isUndo })
        SettingsOption.Reset, SettingsOption.ResetAll, SettingsOption.ResetLibrary, SettingsOption.ClearRecent, SettingsOption.Licenses, SettingsOption.HiddenGames,
        SettingsOption.UnidentifiedGames, SettingsOption.ReportProblem,
        ->
            OptionValue.Opens("")
        // A page to read beside the row: A opens nothing, so no "»".
        SettingsOption.Privacy, SettingsOption.ExportLogs -> OptionValue.Text("")
        SettingsOption.BoxArtSource -> OptionValue.Opens(stringResource(boxArtSourceName(boxArtSourceIn(settings, content.account))))
        // Signed in: how box art is going in a word or two ("340 of 2,054", "Paused"), else the name; signed out: "Sign in »".
        SettingsOption.ScraperAccount ->
            when (val account = content.account) {
                // Signed in: a short word when box art has something to say ("340 of 2,054", "Paused"), else who is signed in.
                is AccountStatus.SignedIn ->
                    boxArtShortText(boxArtLineOf(account, content.boxArt))?.let { OptionValue.Opens(it) } ?: signedInValue(account.username)
                else -> OptionValue.Opens(stringResource(R.string.settings_account_value_sign_in))
            }
        SettingsOption.RetroAchievementsAccount ->
            when (val account = content.raAccount) {
                is RaAccountStatus.SignedIn ->
                    if (account.isKeyRefused) OptionValue.Opens(stringResource(R.string.settings_ra_value_refused)) else signedInValue(account.userName)
                RaAccountStatus.SignedOut -> OptionValue.Opens(stringResource(R.string.settings_account_value_sign_in))
            }
        // The line is the row's label (it may need two lines); nothing on the right.
        SettingsOption.ScraperStatus -> OptionValue.Text("")
        // Statistics draws its own page (StatisticsPage); its stops have no row.
        SettingsOption.Statistic -> OptionValue.Text("")
        // The Statistics page draws this row itself.
        SettingsOption.ExactPlayTime -> OptionValue.Switch(option.switchValue(settings) && content.hasUsageAccess)
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
        SettingsOption.HiddenGames -> stringResource(R.string.settings_hidden_games, rememberCountFormat().format(content.hiddenGames.size))
        SettingsOption.UnidentifiedGames -> stringResource(R.string.settings_unidentified_games, rememberCountFormat().format(content.unidentifiedGames.size))
        SettingsOption.ScraperStatus -> boxArtLineOf(content.account, content.boxArt)?.let { boxArtLineText(it) }.orEmpty()
        else -> stringResource(option.label)
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
    const val TOP_BAR = "settings_top_bar"
    const val SECTIONS = "settings_sections"
    const val SECTIONS_DIVIDER = "settings_sections_divider"
    const val OPTIONS = "settings_options"
    const val PANEL = "settings_panel"
    const val PANEL_LIST = "settings_panel_list"
    const val FONT_CARD = "settings_font_card"

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
    const val CONFIRM_PANEL = "settings_confirm_panel"

    /** Back up…'s panel, which scrolls when the text is large. */
    const val BACKUP_PANEL = "settings_backup_panel"
    const val UPDATE_PANEL = "settings_update_panel"
    const val UPDATE_NOTES = "settings_update_notes"
    const val UPDATE_HEADING = "settings_update_heading"
    const val UPDATE_NOTE = "settings_update_note"
    const val UPDATE_DOT = "settings_update_dot"
    const val UPDATE_PROGRESS = "settings_update_progress"
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

    /** The parts of a value, named from the value's own tag: its words, the chevrons of a choice or opening row, a signed-in check. */
    fun valueText(valueTag: String) = "settings_part_text_$valueTag"

    fun chevronLeft(valueTag: String) = "settings_part_chevron_left_$valueTag"

    fun chevronRight(valueTag: String) = "settings_part_chevron_right_$valueTag"

    fun check(valueTag: String) = "settings_part_check_$valueTag"

    /** A row of Emulators: one per system, by its index among them. */
    fun system(index: Int) = "settings_option_system_$index"

    /** An unknown folder's page, opened from Files not added. */
    const val UNKNOWN_FOLDER_PANEL = "settings_unknown_folder_panel"

    fun systemValue(index: Int) = "settings_value_system_$index"

    fun panelItem(index: Int) = "settings_panel_item_$index"

    /** Appearance › Colors' panel, each tile's picture and each tile's name. */
    const val COLOR_SETS_PANEL = "settings_color_sets_panel"

    /** The look preview card at the top of the right column ([SettingsLookPreview]). */
    const val LOOK_PREVIEW = "settings_look_preview"
    const val COLOR_TILE = "settings_color_tile"
    const val COLOR_TILE_NAME = "settings_color_tile_name"

    /** A most-played game's cover on Statistics' page, by the game's place. */
    fun statisticCover(index: Int) = "settings_statistic_cover_$index"
}

/** A row that opens a panel shows its chevron, but not while a word such as "Downloading…" tells what runs. */
internal fun opensUnlessBusy(text: String, isBusy: Boolean): OptionValue = if (isBusy) OptionValue.Text(text) else OptionValue.Opens(text)

internal fun RestoreState.isRunning(): Boolean = this is RestoreState.Reading || this is RestoreState.Replacing
