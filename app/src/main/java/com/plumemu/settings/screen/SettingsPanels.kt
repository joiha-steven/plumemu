package com.plumemu.settings.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.theme.FlatPartDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumTypography
import com.plumemu.coreui.theme.SettingsPreviewDimens
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.settings.toRgb
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
        SettingsOption.TextSize -> ExplainPanel(model, option.label, R.string.settings_text_size_body)
        SettingsOption.LeftEdge -> ExplainPanel(model, option.label, R.string.settings_left_edge_body)
        SettingsOption.GridSize -> ExplainPanel(model, option.label, R.string.settings_grid_size_body)
        SettingsOption.ConfirmBottom -> ExplainPanel(model, option.label, R.string.settings_confirm_bottom_body)
        SettingsOption.Animations -> ExplainPanel(model, option.label, R.string.settings_animations_body)
        SettingsOption.Sounds -> ExplainPanel(model, option.label, R.string.settings_sounds_body)
        SettingsOption.UsageStatistics -> ExplainPanel(model, option.label, R.string.settings_usage_statistics_body)
        SettingsOption.Privacy -> ExplainPanel(model, option.label, R.string.settings_privacy_body)
        SettingsOption.ResetAll, SettingsOption.ResetLibrary, SettingsOption.ClearRecent -> ConfirmPanel(option, model, touch, inPanel)
        SettingsOption.Reset -> ResetPanel(model, touch)
        SettingsOption.BackupAndRestore -> BackupRestorePanel(model, touch)
        SettingsOption.Backup -> BackupPanel(model, touch)
        SettingsOption.Restore -> RestorePanel(model, touch)
        SettingsOption.UndoRestore -> UndoRestorePanel(model, touch)
        SettingsOption.Version -> AboutPanel(versionName, model)
        SettingsOption.Updates -> UpdatePanel(model, touch, settings, installedLine = versionName)
        // A switch inside Updates' panel, which says what it does.
        SettingsOption.CheckUpdatesDaily -> Unit
        SettingsOption.Licenses -> LicensesPanel(model, touch)
        SettingsOption.RomFolders -> FoldersPanel(model, touch)
        SettingsOption.Rescan -> RescanPanel(model)
        SettingsOption.UnreadableFolders -> UnreadableFoldersPanel(model, touch)
        SettingsOption.SortGames -> ExplainPanel(model, option.label, R.string.settings_sort_games_body)
        SettingsOption.Keyboard -> ExplainPanel(model, option.label, R.string.settings_keyboard_body)
        SettingsOption.SwitchSystemWith -> ExplainPanel(model, option.label, R.string.settings_switch_system_body)
        SettingsOption.Language -> LanguagePanel(model, touch, settings)
        SettingsOption.Vibration -> ExplainPanel(model, option.label, R.string.settings_vibration_body)
        SettingsOption.SystemOrder -> ExplainPanel(model, option.label, R.string.settings_system_order_body)
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
        SettingsOption.FilesNotAdded -> if (model.focus.page == PanelPage.UnknownFolder) UnknownFolderPanel(model, touch) else FilesNotAddedPanel(model, touch)
        SettingsOption.PushRightToPlay -> ExplainPanel(model, option.label, R.string.settings_push_right_body)
        SettingsOption.ShowButtonHints -> ExplainPanel(model, option.label, R.string.settings_show_hints_body)
        SettingsOption.UseAsHome -> ExplainPanel(model, option.label, R.string.settings_use_as_home_body) { PanelLine(model, option, null) }
        SettingsOption.ExportLogs -> ExplainPanel(model, option.label, R.string.settings_export_logs_body) { PanelLine(model, option, null) }
        SettingsOption.ShowApps -> ExplainPanel(model, option.label, R.string.settings_show_apps_body)
        // The switch also governs the battery line of the second screen's in-game clock card.
        SettingsOption.ClockAndBattery ->
            ExplainPanel(model, option.label, if (model.content.secondScreen != null) R.string.settings_clock_battery_body_two_screens else R.string.settings_clock_battery_body)
        SettingsOption.SystemInfo -> ExplainPanel(model, option.label, R.string.settings_system_info_body)
        SettingsOption.SecondScreen -> ExplainPanel(model, option.label, R.string.settings_second_screen_body)
        SettingsOption.SecondScreenInGame -> ExplainPanel(model, option.label, R.string.settings_second_screen_in_game_body)
        SettingsOption.ReportProblem -> ReportProblemPanel(model, touch, settings)
        SettingsOption.BoxArtSource -> BoxArtSourcePanel(settings, model, touch)
        SettingsOption.ScraperAccount -> ScraperAccountPanel(settings, model, touch)
        SettingsOption.ScraperStatus -> ScraperStatusPanel(model)
        SettingsOption.RetroAchievementsAccount -> RaAccountPanel(model, touch)
        SettingsOption.BoxArtRegion -> ExplainPanel(model, option.label, R.string.settings_box_art_region_body)
        // Statistics is a whole page with no side panel (StatisticsPage).
        SettingsOption.Statistic, SettingsOption.ExactPlayTime -> Unit
    }
}

/**
 * Text in a side panel (title, body, status lines) starts on the rows' text line, as in the game
 * panel: rows, fields and buttons span the panel and their text sits [PlumDimens.gameRowPadding] in
 * (`settingsPanelRowPadding` is the same), so free text starts there too (see [onPanelTextLine]).
 */
internal fun Modifier.onRowTextLine(): Modifier =
    composed { padding(start = LocalPanelTextStart.current, end = PlumDimens.gameRowPadding) }

/**
 * Where a panel's text starts: [PlumDimens.gameRowPadding] in, on the row-text line; 0 in the right column of three columns, where
 * the text starts on the card's left edge as on the board ([PanelHost] provides it; a page that covers the column keeps the line).
 */
internal val LocalPanelTextStart = compositionLocalOf { PlumDimens.gameRowPadding }

/** Running text of a panel (title, body, facts, status): starts at [LocalPanelTextStart] but ends [PlumDimens.panelTextEnd] from the right edge, where no slab needs clearing. */
internal fun Modifier.onPanelTextLine(): Modifier = composed { padding(start = LocalPanelTextStart.current, end = PlumDimens.panelTextEnd) }

/** [style] at the board's size for the right column of three columns; unchanged elsewhere. */
@Composable
private fun TextStyle.onBoard(scale: Float): TextStyle =
    if (!PlumTheme.layout.isSettingsThreeColumns) {
        this
    } else {
        copy(fontSize = fontSize * scale, lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight)
    }

/**
 * Room for the focus rim of a panel's buttons above and below them ([FlatPartDimens.focusRimRoom]: the rim, its glow and its shadow, never cut by the scroll clip). Not at the sides: the buttons
 * start on the panel's edge like its rows, and a ring may reach past it (no parent clips there; a
 * vertical scroll clips only along its axis).
 */
internal fun Modifier.ringRoomAboveAndBelow(): Modifier = padding(vertical = FlatPartDimens.focusRimRoom)

/**
 * The panel's title: `panelTitle` (16 / 600), on the row-text line. It may wrap to a second line
 * (the game panel's titles do): at Largest in Plum Serif "Re-download box art" no longer fits one.
 */
@Composable
internal fun PanelTitle(text: String) {
    val colors = PlumTheme.colors
    BasicText(text.withoutDots(), style = panelTitleStyle(), color = { colors.text }, maxLines = if (PlumTheme.layout.isSettingsThreeColumns) TITLE_LINES_THREE else TITLE_LINES, overflow = TextOverflow.Ellipsis, modifier = Modifier.panelBlock().onPanelTextLine())
}

@Composable
private fun panelTitleStyle() = PlumTheme.type.panelTitle.onBoard(SettingsPreviewDimens.BOARD_TITLE_SCALE)

/** A row's "…" says it opens something: the title and the crumb of what it opened have no dots. */
internal fun String.withoutDots(): String = trimEnd('…', '.')

/** Facts joined by " · ": on one line where they fit, else one fact a line with no dot, so a wrapped line never starts with it. */
@Composable
internal fun FactsLine(
    text: String,
    style: TextStyle,
    color: () -> Color,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = constraints.maxWidth
        val fits = remember(text, style, width) { measurer.measure(text, style, softWrap = false, maxLines = 1).size.width <= width }
        val shown = if (fits) text else text.oneFactALine()
        BasicText(shown, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = textModifier)
    }
}

/**
 * A title that is one long word (a brand name): always on one line, in the option's size when the title's own does not fit the column, so it never
 * breaks mid-word. Each panel holds one title: the size is measured, nothing is composed to try it.
 */
@Composable
internal fun WordPanelTitle(text: String) {
    val colors = PlumTheme.colors
    val full = panelTitleStyle()
    val step = PlumTheme.type.settingsOptionFocused
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.panelBlock().onPanelTextLine().fillMaxWidth()) {
        val fits = remember(text, full, constraints.maxWidth) { measurer.measure(text, full, softWrap = false, maxLines = 1).size.width <= constraints.maxWidth }
        BasicText(text, style = if (fits) full else step, color = { colors.text }, maxLines = 1, softWrap = false)
    }
}

/** A title that takes [full] where that fits [TITLE_LINES] lines and [short] where it does not: the words that matter whole, never an ellipsis over them. */
@Composable
internal fun PanelTitleOr(
    full: String,
    short: String,
) {
    val colors = PlumTheme.colors
    val style = panelTitleStyle()
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.panelBlock().onPanelTextLine().fillMaxWidth()) {
        val width = constraints.maxWidth
        val text = remember(full, short, style, width) { if (measurer.measure(full, style, constraints = Constraints(maxWidth = width)).lineCount <= TITLE_LINES) full else short }
        BasicText(text, style = style, color = { colors.text })
    }
}

private const val TITLE_LINES = 2

/** The 205 dp right column of three columns holds a long title over three lines. */
private const val TITLE_LINES_THREE = 3

/** How much of a panel's description the tile above it leaves room for: none, or this many pixels of height. Null: all of it. */
internal sealed interface BodyRoom {
    data object Dropped : BodyRoom

    data class Height(
        val px: Int,
    ) : BodyRoom
}

internal val LocalBodyRoom = compositionLocalOf<BodyRoom?> { null }

/**
 * The panel's text: `panelBody` (the details description's 13 on a 1.5 line) in `text2`, on the row-text line. Under the look
 * preview it keeps only the lines that fit whole ([LocalBodyRoom]); none when not even one does.
 */
@Composable
internal fun PanelBody(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.panelBody.onBoard(SettingsPreviewDimens.BOARD_BODY_SCALE)
    val lineHeight = with(LocalDensity.current) { (if (style.lineHeight.isSpecified) style.lineHeight else style.fontSize * BODY_LINE_FALLBACK).toPx() }
    val lines =
        when (val room = LocalBodyRoom.current) {
            null -> Int.MAX_VALUE
            BodyRoom.Dropped -> 0
            is BodyRoom.Height -> (room.px / lineHeight).toInt()
        }
    if (lines <= 0) return
    // An ellipsis only where the preview cut the lines; elsewhere the text is whole or clipped by its scroll, as before.
    val overflow = if (lines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
    BasicText(text, style = style, color = { colors.text2 }, maxLines = lines, overflow = overflow, modifier = Modifier.panelBlock().onPanelTextLine().then(modifier))
}

private const val BODY_LINE_FALLBACK = 1.5f

/** Title and description: the board's 8 px apart in three columns, the list gap elsewhere. */
@Composable
internal fun PanelHeading(
    title: String,
    body: String,
) {
    val gap = if (PlumTheme.layout.isSettingsThreeColumns) SettingsPreviewDimens.boardTextGap else PlumDimens.settingsPanelListGap
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        PanelTitle(title)
        PanelBody(body)
    }
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
    BasicText(text, style = PlumTheme.type.panelBody.onBoard(SettingsPreviewDimens.BOARD_BODY_SCALE), color = { colors.muted }, modifier = Modifier.onPanelTextLine().then(modifier))
}

private fun String.oneFactALine(): String = replace(" · ", "\n")

/** [PanelFact] for facts joined by " · " ([FactsLine]). */
@Composable
internal fun PanelFacts(text: String) {
    val colors = PlumTheme.colors
    FactsLine(text, PlumTheme.type.panelBody.onBoard(SettingsPreviewDimens.BOARD_BODY_SCALE), color = { colors.muted }, modifier = Modifier.onPanelTextLine())
}

@Composable
internal fun ExplainPanel(
    model: SettingsModel,
    title: Int,
    body: Int,
    after: @Composable () -> Unit = {},
) {
    // Each text starts at its top; beside the look preview the panel wraps its height, as the look preview does.
    key(title, body) {
        val scroll = rememberPressedScroll(title, model.licenseScroll, model)
        ReportTextScrolls(scroll, model)
        PanelScrollColumn(PlumDimens.settingsPanelListGap, scroll = scroll, tag = SettingsPanelTags.EXPLAIN, fillsHeight = false, endRoom = 0.dp, keyMoves = model.licenseScroll) {
            PanelHeading(stringResource(title), stringResource(body))
            after()
        }
    }
}

/** Tells the model whether [scroll], a panel of text, runs past its column: its row then offers "More" ([SettingsPanelModel.panelTextScrolls]). A panel that leaves takes the offer with it. */
@Composable
private fun ReportTextScrolls(
    scroll: ScrollState,
    model: SettingsModel,
) {
    LaunchedEffect(scroll, model) { snapshotFlow { scroll.canScrollForward || scroll.canScrollBackward }.collect { model.panelTextScrolls = it } }
    DisposableEffect(scroll, model) { onDispose { model.panelTextScrolls = false } }
}

/** "Current", "Default": accent fill, `onAccent` text, 10 bold (design/rules.md › Colour). */
@Composable
internal fun Badge(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    BasicText(
        text,
        style = PlumTheme.type.badge.copy(color = colors.onAccent),
        maxLines = 1,
        modifier =
            modifier
                .background(colors.accent, RoundedCornerShape(PlumDimens.cornerCoverPortrait))
                .padding(horizontal = PlumDimens.keyCapPadding),
    )
}

/** Reset all settings', Reset library's and Clear Recent…'s confirm: what it does, then Cancel (focused first) and Reset (Clear). */
@Composable
private fun ConfirmPanel(
    option: SettingsOption,
    model: SettingsModel,
    touch: SettingsTouch,
    inPanel: Boolean,
) {
    val isAll = option == SettingsOption.ResetAll
    // Scrolls when the text is large (Largest in Plum Serif, or with the result line), keeping the focused button in view.
    val gap = PlumDimens.settingsPanelGap
    BoxWithConstraints {
        val window = constraints.maxHeight
        PanelScrollColumn(gap, tag = SettingsTags.CONFIRM_PANEL) {
            TitleGivesWay(window, gap, title = { PanelTitle(stringResource(option.label)) }) {
                ConfirmWords(option, isAll, model, touch, inPanel)
            }
        }
    }
}

/** A confirm's text and its two buttons. */
@Composable
private fun ConfirmWords(
    option: SettingsOption,
    isAll: Boolean,
    model: SettingsModel,
    touch: SettingsTouch,
    inPanel: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
        val isRecent = option == SettingsOption.ClearRecent
        PanelBody(stringResource(if (isAll) R.string.settings_reset_all_body else if (isRecent) R.string.settings_clear_recent_body else R.string.settings_reset_library_body))
        // Clear Recent says how it ended with the row's notice (a count), like every other plain line.
        if (isRecent) PanelLine(model, option, null)
        ButtonsRow(Modifier.ringRoomAboveAndBelow()) {
            val focused = if (inPanel) model.focus.panelIndex else -1
            SetupButton(
                text = stringResource(R.string.settings_cancel),
                isPrimary = false,
                isFocused = focused == SettingsModel.CONFIRM_CANCEL,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_CANCEL, ring = FlatPartDimens.focusRimRoom)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_CANCEL) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_CANCEL)),
            )
            SetupButton(
                text = stringResource(if (isRecent) R.string.settings_clear_recent_confirm else R.string.settings_reset),
                isPrimary = true,
                isFocused = focused == SettingsModel.CONFIRM_RESET,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_RESET, ring = FlatPartDimens.focusRimRoom)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_RESET) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_RESET)),
            )
        }
    }
}

/** Buttons side by side, the second under the first and as wide as it when both do not fit a line (Largest); one under the other in the 205 dp right column of three columns. */
@Composable
private fun ButtonsRow(
    modifier: Modifier,
    buttons: @Composable () -> Unit,
) {
    val stacks = PlumTheme.layout.isSettingsThreeColumns
    val gap = PlumDimens.setupButtonGap
    Layout(buttons, modifier) { each, constraints ->
        val gapPx = gap.roundToPx()
        val widths = each.map { it.maxIntrinsicWidth(constraints.maxHeight).coerceAtMost(constraints.maxWidth) }
        if (!stacks && widths.sum() + gapPx * (each.size - 1) <= constraints.maxWidth) {
            val placed = each.mapIndexed { i, it -> it.measure(Constraints.fixedWidth(widths[i])) }
            layout(constraints.maxWidth, placed.maxOf { it.height }) {
                var x = 0
                placed.forEach {
                    it.place(x, 0)
                    x += it.width + gapPx
                }
            }
        } else {
            val widest = widths.max()
            val placed = each.map { it.measure(Constraints.fixedWidth(widest)) }
            layout(constraints.maxWidth, placed.sumOf { it.height } + gapPx * (each.size - 1)) {
                var y = 0
                placed.forEach {
                    it.place(0, y)
                    y += it.height + gapPx
                }
            }
        }
    }
}

/** Version: the app's name, its version (versionName) and one line about it. */
@Composable
private fun AboutPanel(
    versionName: String,
    model: SettingsModel,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val platformData = model.content.platformData
    // At the largest text its lines run past the panel: then Right on Version enters it, Up and Down scroll them.
    val scroll = rememberPressedScroll(SettingsOption.Version, model.licenseScroll, model)
    ReportTextScrolls(scroll, model)
    PanelScrollColumn(PlumDimens.titleLineGap, scroll = scroll, tag = SettingsPanelTags.ABOUT, keyMoves = model.licenseScroll) {
        BasicText(stringResource(R.string.app_name), style = type.settingsAboutTitle.copy(color = colors.text), maxLines = 1, modifier = Modifier.onPanelTextLine())
        BasicText(stringResource(R.string.settings_version_line, versionName), style = type.meta.copy(color = colors.meta), maxLines = 1, modifier = Modifier.onPanelTextLine())
        platformData?.let { data ->
            val line = stringResource(R.string.settings_platform_data_line, stringResource(R.string.settings_platform_data), platformDataRowValue(data))
            BasicText(line, style = type.meta.copy(color = colors.meta), modifier = Modifier.onPanelTextLine().testTag(SettingsPanelTags.PLATFORM_DATA))
        }
        PanelBody(stringResource(R.string.settings_about_body), Modifier.padding(top = PlumDimens.titleLineGap))
        if (platformData != null) PanelBody(stringResource(R.string.settings_platform_data_body), Modifier.padding(top = PlumDimens.titleLineGap))
    }
}

internal object SettingsPanelTags {
    /** Version's panel, which scrolls where its lines run past the column. */
    const val ABOUT = "settings_about"

    /** An explanation's text. */
    const val EXPLAIN = "settings_explain"

    /** Version's panel: the platform data's version, under Plumemu's. */
    const val PLATFORM_DATA = "settings_platform_data"

    /** ROM folders › a folder's page and its "Skip a sub-folder…" page. */
    const val FOLDER_PAGE = "settings_folder_page"
    const val SKIP_SUBFOLDER_PAGE = "settings_skip_subfolder_page"
    const val UNREADABLE_FOLDERS = "settings_unreadable_folders"
    const val FILES_NOT_ADDED = "settings_files_not_added"

    /** Back up…'s warning that the file holds the ScreenScraper password. */
    const val BACKUP_PASSWORD = "settings_backup_password"
    const val DONE = "settings_done"

    /** Accounts › RetroAchievements: the two help lines and the line on what is sent. */
    const val RA_HELP = "settings_ra_help"
    const val RA_PRIVACY = "settings_ra_privacy"

    const val STATUS = "settings_status"

    /** The last check's answer under a step left standing in the Updates panel. */
    const val UPDATE_LAST_CHECK = "settings_update_last_check"
    const val UPDATE_NOTICE = "settings_update_notice"

    /** The account facts block under the status line. */
    const val FACTS = "settings_account_facts"
    const val BOX_ART_SIZE = "settings_box_art_size"
    const val BOX_ART_GIVEN_UP = "settings_box_art_given_up"
    const val SCAN_PULSE = "settings_scan_pulse"

    /** The [index]th line of Rescan library's result (the first is [STATUS]). */
    fun rescanExtra(index: Int) = "settings_rescan_line_$index"
}
