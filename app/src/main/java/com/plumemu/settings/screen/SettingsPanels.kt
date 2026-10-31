package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumTypography
import com.plumemu.settings.toRgb
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.graph.LibraryResetState
import com.plumemu.settings.toThemeSettings
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.setup.focusRing

/*
 * The side panel (wide: 248 beside the options; narrow: a further stop as wide as the options):
 * what the focused option does, and for colours, resets and licences the things to choose. Built
 * from the board's RP5-S1-settings panels; what the board does not draw uses the same parts.
 */

@Composable
internal fun SettingsPanel(
    option: SettingsOption,
    settings: PlumSettings,
    model: SettingsModel,
    touch: SettingsTouch,
    versionName: String,
) {
    val inPanel = model.focus.column == SettingsColumn.Panel
    when (option) {
        SettingsOption.Colors -> ColorSetsPanel(settings, model, touch)
        SettingsOption.Background, SettingsOption.TextColor, SettingsOption.Accent -> ColorPanel(option, settings, model, touch)
        SettingsOption.Font -> FontPanel(settings)
        SettingsOption.TextSize -> ExplainPanel(option.label, R.string.settings_text_size_body)
        SettingsOption.TextWeight -> ExplainPanel(option.label, R.string.settings_text_weight_body)
        SettingsOption.LeftEdge -> ExplainPanel(option.label, R.string.settings_left_edge_body)
        SettingsOption.GridSize -> ExplainPanel(option.label, R.string.settings_grid_size_body)
        SettingsOption.ConfirmBottom -> ExplainPanel(option.label, R.string.settings_confirm_bottom_body)
        SettingsOption.Animations -> ExplainPanel(option.label, R.string.settings_animations_body)
        SettingsOption.Sounds -> ExplainPanel(option.label, R.string.settings_sounds_body)
        SettingsOption.UsageStatistics -> ExplainPanel(option.label, R.string.settings_usage_statistics_body)
        SettingsOption.Privacy -> ExplainPanel(option.label, R.string.settings_privacy_body)
        SettingsOption.ResetAll, SettingsOption.ResetLibrary, SettingsOption.ClearRecent -> ConfirmPanel(option, model, touch, inPanel)
        SettingsOption.Backup -> BackupPanel(model, touch)
        SettingsOption.Restore -> RestorePanel(model, touch)
        SettingsOption.UndoRestore -> UndoRestorePanel(model, touch)
        SettingsOption.Version -> AboutPanel(versionName)
        SettingsOption.UpdateAvailable -> UpdatePanel(model, touch)
        SettingsOption.CheckForUpdates -> CheckUpdatesPanel(model)
        SettingsOption.CheckUpdatesDaily -> ExplainPanel(option.label, R.string.settings_check_updates_daily_body)
        SettingsOption.Licenses -> LicensesPanel(model, touch)
        SettingsOption.RomFolders -> FoldersPanel(model, touch)
        SettingsOption.Rescan -> RescanPanel(model)
        SettingsOption.ScanOnStartup -> ExplainPanel(option.label, R.string.settings_scan_on_startup_body)
        SettingsOption.SortGames -> ExplainPanel(option.label, R.string.settings_sort_games_body)
        SettingsOption.RecentSize -> ExplainPanel(option.label, R.string.settings_recent_size_body)
        SettingsOption.Keyboard -> ExplainPanel(option.label, R.string.settings_keyboard_body)
        SettingsOption.SwitchSystemWith -> ExplainPanel(option.label, R.string.settings_switch_system_body)
        SettingsOption.Language -> LanguagePanel(model, touch, settings)
        SettingsOption.Vibration -> ExplainPanel(option.label, R.string.settings_vibration_body)
        SettingsOption.SystemOrder -> ExplainPanel(option.label, R.string.settings_system_order_body)
        SettingsOption.Systems -> SystemsPanel(settings, model)
        SettingsOption.UnidentifiedGames -> UnidentifiedGamesPanel(model, touch, option)
        SettingsOption.HiddenGames ->
            SettingsGameListPanel(
                model,
                touch,
                option,
                stringResource(R.string.settings_hidden_games_title),
                stringResource(R.string.settings_hidden_games_body),
                model.content.hiddenGames,
            )
        SettingsOption.SystemEmulator -> EmulatorsPanel(model, touch)
        SettingsOption.UnknownFolder -> UnknownFolderPanel(model, touch)
        SettingsOption.DetectEmulators -> DetectPanel(model)
        SettingsOption.HoldToScrollFaster -> ExplainPanel(option.label, R.string.settings_hold_to_scroll_body)
        SettingsOption.PushRightToPlay -> ExplainPanel(option.label, R.string.settings_push_right_body)
        SettingsOption.ShowButtonHints -> ExplainPanel(option.label, R.string.settings_show_hints_body)
        SettingsOption.UseAsHome -> ExplainPanel(option.label, R.string.settings_use_as_home_body) { PanelLine(model, option, null) }
        SettingsOption.ExportLogs -> ExplainPanel(option.label, R.string.settings_export_logs_body) { PanelLine(model, option, null) }
        SettingsOption.ShowApps -> ExplainPanel(option.label, R.string.settings_show_apps_body)
        SettingsOption.ShowClock -> ExplainPanel(option.label, R.string.settings_clock_body)
        // The switch also governs the battery line of the second screen's in-game clock card.
        SettingsOption.ShowBattery ->
            ExplainPanel(option.label, if (model.content.secondScreen != null) R.string.settings_show_battery_body_two_screens else R.string.settings_show_battery_body)
        SettingsOption.SystemInfo -> ExplainPanel(option.label, R.string.settings_system_info_body)
        SettingsOption.SecondScreen -> ExplainPanel(option.label, R.string.settings_second_screen_body)
        SettingsOption.SecondScreenInGame -> ExplainPanel(option.label, R.string.settings_second_screen_in_game_body)
        SettingsOption.ReportProblem -> ReportProblemPanel(model, touch, settings)
        SettingsOption.ScraperAccount -> ScraperAccountPanel(model, touch)
        SettingsOption.ScraperStatus -> ScraperStatusPanel(model)
        SettingsOption.BoxArtRegion -> ExplainPanel(option.label, R.string.settings_box_art_region_body)
        SettingsOption.Statistic -> StatisticsPanel(model.focusedStatisticGame)
    }
}

/**
 * Text in a side panel (title, body, status lines) starts on the rows' text line, as in the game
 * panel: rows, fields and buttons span the panel and their text sits [PlumDimens.gameRowPadding] in
 * (`settingsPanelRowPadding` is the same), so free text starts there too.
 */
internal fun Modifier.onRowTextLine(): Modifier = padding(horizontal = PlumDimens.gameRowPadding)

/**
 * Room for the focus rings of a panel's buttons above and below them. Not at the sides: the buttons
 * start on the panel's edge like its rows, and a ring may reach past it (no parent clips there; a
 * vertical scroll clips only along its axis).
 */
internal fun Modifier.ringRoomAboveAndBelow(): Modifier = padding(vertical = PlumDimens.focusRingOutset)

/**
 * The panel's title: `panelTitle` (16 / 600), on the row-text line. It may wrap to a second line
 * (the game panel's titles do): at Largest in Plum Serif "Re-download box art" no longer fits one.
 */
@Composable
internal fun PanelTitle(text: String) {
    val colors = PlumTheme.colors
    BasicText(text, style = PlumTheme.type.panelTitle, color = { colors.text }, maxLines = TITLE_LINES, overflow = TextOverflow.Ellipsis, modifier = Modifier.onRowTextLine())
}

private const val TITLE_LINES = 2

/** The panel's text: `panelBody` (the details description's 13 on a 1.5 line) in `text2`, on the row-text line. */
@Composable
internal fun PanelBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    BasicText(text, style = PlumTheme.type.panelBody, color = { colors.text2 }, modifier = Modifier.onRowTextLine().then(modifier))
}

/**
 * A fact or status line in a panel (a release's date and size, the account's request counts): the
 * body's size but `muted`, so facts stay quieter than the prose around them.
 */
@Composable
internal fun PanelFact(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    BasicText(text, style = PlumTheme.type.panelBody, color = { colors.muted }, modifier = Modifier.onRowTextLine().then(modifier))
}

@Composable
internal fun ExplainPanel(
    title: Int,
    body: Int,
    after: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(title))
        PanelBody(stringResource(body))
        after()
    }
}

/** Font: a sample line in each Plum font, the current one filled with its badge. */
@Composable
private fun FontPanel(settings: PlumSettings) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val theme = settings.toThemeSettings()
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_font))
        PanelBody(stringResource(R.string.settings_font_body))
        for (font in FontSetting.entries) {
            val isCurrent = settings.appearance.font == font
            val sample = PlumTypography(PlumFont.entries[font.ordinal], type.textSize, theme.textMode)
            val shape = RoundedCornerShape(PlumDimens.cornerRow)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PlumTheme.layout.rowHeight)
                        .then(if (isCurrent) Modifier.background(colors.sel, shape) else Modifier.border(PlumDimens.settingsSwatchEdge, colors.sel, shape))
                        .padding(horizontal = PlumDimens.settingsPanelRowPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    stringResource(R.string.settings_sample_game_2),
                    style = sample.fontSample.copy(color = colors.text),
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.End) {
                    BasicText(stringResource(fontName(font)), style = type.settingsPanelMeta.copy(color = colors.text), maxLines = 1)
                    if (isCurrent) Badge(stringResource(R.string.settings_badge_current))
                }
            }
        }
    }
}

/** "Current", "Default": accent fill, `onAccent` text, 10 bold (design/rules.md › Colour). */
@Composable
internal fun Badge(text: String) {
    val colors = PlumTheme.colors
    BasicText(
        text,
        style = PlumTheme.type.badge.copy(color = colors.onAccent),
        maxLines = 1,
        modifier =
            Modifier
                .background(colors.accent, RoundedCornerShape(PlumDimens.cornerCoverPortrait))
                .padding(horizontal = PlumDimens.keyCapPadding),
    )
}

/** Reset all settings, Reset library and Clear Recent…: what it does, then Cancel (focused first) and Reset (Clear). */
@Composable
private fun ConfirmPanel(
    option: SettingsOption,
    model: SettingsModel,
    touch: SettingsTouch,
    inPanel: Boolean,
) {
    val isAll = option == SettingsOption.ResetAll
    // Scrolls when the text is large (Largest in Plum Serif, or with the result line), keeping the focused button in view.
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
        PanelTitle(stringResource(option.label))
        val isRecent = option == SettingsOption.ClearRecent
        PanelBody(stringResource(if (isAll) R.string.settings_reset_all_body else if (isRecent) R.string.settings_clear_recent_body else R.string.settings_reset_library_body))
        // Clear Recent says how it ended with the row's notice (a count), like every other plain line.
        if (isRecent) PanelLine(model, option, null)
        val status = LocalSettingsStatus.current
        val doneText =
            when {
                model.doneOption != option -> null
                isAll -> if (status.saveFailed) null else R.string.settings_reset_all_done
                isRecent -> null
                else ->
                    when (status.libraryReset) {
                        LibraryResetState.Idle, LibraryResetState.Forgetting -> R.string.settings_reset_library_forgetting
                        LibraryResetState.Forgotten -> R.string.settings_reset_library_done
                        LibraryResetState.Failed -> R.string.settings_reset_library_failed
                    }
            }
        if (doneText != null) {
            BasicText(
                stringResource(doneText),
                style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
                modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.DONE),
            )
        }
        Row(
            modifier = Modifier.ringRoomAboveAndBelow(),
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
        ) {
            val focused = if (inPanel) model.focus.panelIndex else -1
            SetupButton(
                text = stringResource(R.string.settings_cancel),
                isPrimary = false,
                isFocused = focused == SettingsModel.CONFIRM_CANCEL,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_CANCEL, ring = PlumDimens.focusRingOutset)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_CANCEL) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_CANCEL)),
            )
            SetupButton(
                text = stringResource(if (isRecent) R.string.settings_clear_recent_confirm else R.string.settings_reset),
                isPrimary = true,
                isFocused = focused == SettingsModel.CONFIRM_RESET,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_RESET, ring = PlumDimens.focusRingOutset)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_RESET) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_RESET)),
            )
        }
    }
}

/** Version: the app's name, its version (versionName) and one line about it. */
@Composable
private fun AboutPanel(versionName: String) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
        BasicText(stringResource(R.string.app_name), style = type.settingsAboutTitle.copy(color = colors.text), maxLines = 1, modifier = Modifier.onRowTextLine())
        BasicText(stringResource(R.string.settings_version_line, versionName), style = type.meta.copy(color = colors.meta), maxLines = 1, modifier = Modifier.onRowTextLine())
        PanelBody(stringResource(R.string.settings_about_body), Modifier.padding(top = PlumDimens.titleLineGap))
    }
}

internal object SettingsPanelTags {
    /** ROM folders › a folder's page and its "Skip a sub-folder…" page. */
    const val FOLDER_PAGE = "settings_folder_page"
    const val SKIP_SUBFOLDER_PAGE = "settings_skip_subfolder_page"

    /** Back up…'s warning that the file holds the ScreenScraper password. */
    const val BACKUP_PASSWORD = "settings_backup_password"
    const val DONE = "settings_done"

    /** Statistics' side panel for a most-played game: its cover, title, facts and the "A to play" line. */
    const val STATISTIC_COVER = "settings_statistic_panel_cover"
    const val STATISTIC_TITLE = "settings_statistic_panel_title"
    const val STATISTIC_FACTS = "settings_statistic_panel_facts"
    const val STATISTIC_PLAY_HINT = "settings_statistic_panel_play_hint"
    const val STATUS = "settings_status"

    /** The account facts block under the status line. */
    const val FACTS = "settings_account_facts"
    const val BOX_ART_SIZE = "settings_box_art_size"
    const val BOX_ART_GIVEN_UP = "settings_box_art_given_up"
    const val SCAN_PULSE = "settings_scan_pulse"

    /** The [index]th line of Rescan library's result (the first is [STATUS]). */
    fun rescanExtra(index: Int) = "settings_rescan_line_$index"
}
