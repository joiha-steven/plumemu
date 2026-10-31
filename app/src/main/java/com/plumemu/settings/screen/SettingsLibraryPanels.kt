package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreui.components.ScanPulseLine
import com.plumemu.coreui.components.focusPill
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFonts
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.rememberCountFormat
import com.plumemu.settings.LocalButtonLetters
import com.plumemu.setup.SetupButton
import com.plumemu.setup.SetupFolder
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.setup.folderLabel

/*
 * Library › ROM folders (the folders, + Add folder, Remove's confirm), Rescan library and a
 * system's emulators, from the board's RP5-S1-settings panels: folder cards 10 / 12 in
 * `sel2`, emulator rows with the current one marked. While the panel has the focus the focused item
 * takes the row focus language and the others lose their fill (never two filled rows).
 */

@Composable
internal fun FoldersPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val inPanel = focus.column == SettingsColumn.Panel
    if (inPanel && focus.page == PanelPage.RemoveFolder) {
        RemoveFolderPage(model, touch)
        return
    }
    if (inPanel && focus.page == PanelPage.Folder) {
        FolderPage(model, touch)
        return
    }
    if (inPanel && focus.page == PanelPage.SkipSubfolder) {
        SkipSubfolderPage(model, touch)
        return
    }
    val content = model.content
    val focused = if (inPanel) focus.panelIndex else -1
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_rom_folders))
        PanelBody(stringResource(R.string.settings_rom_folders_body))
        PanelLine(model, SettingsOption.RomFolders, folderAddText(LocalSettingsStatus.current.folderAdd))
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(content.folders, key = { _, folder -> folder.id }, contentType = { _, _ -> "folder" }) { index, folder ->
                PanelItem(
                    title = folderLabel(folder),
                    detail = if (model.work.isRemoving(folder.id, content)) stringResource(R.string.settings_folder_removing) else folderSummary(folder, content.isScanning),
                    isFocused = index == focused,
                    isQuiet = !inPanel,
                    isOutlined = inPanel,
                    padded = true,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
            item(key = "add", contentType = "add") {
                val add = content.folders.size
                PanelItem(
                    title = stringResource(R.string.settings_add_folder),
                    titleStyle = PlumTheme.type.settingsPanelAction.copy(color = PlumTheme.colors.accent),
                    isFocused = add == focused,
                    modifier = Modifier.tapTarget { touch.onPanelItem(add) }.testTag(SettingsTags.panelItem(add)),
                )
            }
        }
    }
}

/**
 * A list of games in the panel, each with its system under the title (Library › Hidden games;
 * I3's Unidentified games is meant to use it too): [games] as items, A or a tap acts on one.
 */
@Composable
internal fun SettingsGameListPanel(
    model: SettingsModel,
    touch: SettingsTouch,
    option: SettingsOption,
    title: String,
    body: String,
    games: List<SettingsGame>,
) {
    val focus = model.focus
    val inPanel = focus.column == SettingsColumn.Panel && focus.page != null
    // A shown-again game leaves the list: the focus stays in place, on the last one at the end.
    val focused = if (inPanel) focus.panelIndex.coerceAtMost(games.lastIndex) else -1
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(title)
        PanelBody(body)
        PanelLine(model, option, null)
        // Lazy: a library has thousands of games, and a panel that only previews must not build them all.
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(games, key = { _, game -> game.id }, contentType = { _, _ -> "game" }) { index, game ->
                PanelItem(
                    title = game.title,
                    detail = if (game.isNotAGame) stringResource(R.string.settings_hidden_not_a_game, game.systemName) else game.systemName,
                    isFocused = index == focused,
                    isQuiet = !inPanel,
                    isOutlined = inPanel,
                    padded = true,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}

/**
 * Library › Unidentified games: the games named only from their file; A or a tap opens
 * Fix name… for one in the same panel, and a pick returns here. The page's actions come back
 * through the router like the hints' ([SettingsTouch.onHint]).
 */
@Composable
internal fun UnidentifiedGamesPanel(
    model: SettingsModel,
    touch: SettingsTouch,
    option: SettingsOption,
) {
    val fixing = model.nameFix.open
    if (fixing != null && model.focus.page == PanelPage.FixName) {
        SettingsFixNamePage(fixing, touch.onHint)
        return
    }
    SettingsGameListPanel(
        model,
        touch,
        option,
        stringResource(R.string.settings_unidentified_games_title),
        stringResource(R.string.settings_unidentified_games_body),
        model.content.unidentifiedGames,
    )
}

/** Remove's confirm: which folder, what goes, then Cancel (focused first) and Remove. */
@Composable
private fun RemoveFolderPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val folder = model.content.folder(model.focus.folderId)
    // Scrolls when the text is large (Largest in Plum Serif), keeping the focused button in view.
    Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap)) {
        PanelTitle(stringResource(R.string.settings_remove_folder_title))
        if (folder != null) {
            BasicText(folderLabel(folder), style = PlumTheme.type.meta.copy(color = PlumTheme.colors.text), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.onRowTextLine())
        }
        PanelBody(stringResource(R.string.settings_remove_folder_body))
        Row(Modifier.ringRoomAboveAndBelow(), horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap)) {
            val focused = model.focus.panelIndex
            SetupButton(
                text = stringResource(R.string.settings_cancel),
                isPrimary = false,
                isFocused = focused == SettingsModel.CONFIRM_CANCEL,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_CANCEL, ring = PlumDimens.focusRingOutset)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_CANCEL) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_CANCEL)),
            )
            SetupButton(
                text = stringResource(R.string.settings_remove),
                isPrimary = true,
                isFocused = focused == SettingsModel.CONFIRM_RESET,
                modifier =
                    Modifier.bringIntoViewWhen(focused == SettingsModel.CONFIRM_RESET, ring = PlumDimens.focusRingOutset)
                        .tapTarget { touch.onPanelItem(SettingsModel.CONFIRM_RESET) }.testTag(SettingsTags.panelItem(SettingsModel.CONFIRM_RESET)),
            )
        }
    }
}

/** Rescan library: what it does, then the scan's counts while it runs and its result when it ended (until the focus leaves). */
@Composable
internal fun RescanPanel(model: SettingsModel) {
    val lines = rescanLines(model.content)
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_rescan))
        PanelBody(stringResource(R.string.settings_rescan_body))
        PanelLine(model, SettingsOption.Rescan, lines.firstOrNull())
        // "Already reading your games." under the progress, until the scan ends or the focus leaves.
        model.work.notice?.takeIf { it.first == SettingsOption.Rescan }?.let { (_, notice) ->
            BasicText(stringResource(notice.text), style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text), modifier = Modifier.onRowTextLine().testTag(SettingsTags.NOTICE))
        }
        // A result can add one line per folder that could not be read.
        for ((index, line) in lines.withIndex().drop(1)) {
            BasicText(line, style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text), modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.rescanExtra(index)))
        }
    }
}

/** The emulators panel's first line; names the confirm button by its letter, so it follows the swap. */
@Composable
internal fun emulatorsBody(systemName: String): String =
    stringResource(R.string.settings_emulators_body, systemName, stringResource(LocalButtonLetters.current.confirm))

/** A system's emulators: "Asks on the first game" first, then each installed one (legal-risk ones with their warning line). */
@Composable
internal fun EmulatorsPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val inPanel = focus.column == SettingsColumn.Panel
    val system = (if (inPanel) model.content.system(focus.systemId) else null) ?: SettingsListPages.systemAt(focus, model.content) ?: return
    val focused = if (inPanel) focus.panelIndex else -1
    val current = system.currentIndex
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(system.name)
        PanelBody(emulatorsBody(system.name))
        PanelLine(model, SettingsOption.SystemEmulator, null)
        // Nothing chosen: the first game played asks; no emulator is started by itself.
        val automatic = stringResource(if (system.installed.isNotEmpty()) R.string.settings_emulator_ask else R.string.settings_emulator_automatic_none)
        val entries = listOf<EmulatorEntry?>(null) + system.choices
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(entries, key = { _, entry -> entry?.id ?: AUTOMATIC_KEY }, contentType = { _, _ -> "emulator" }) { index, entry ->
                PanelItem(
                    title = entry?.name ?: automatic,
                    detail =
                        when {
                            entry == null -> null
                            !entry.isInstalled -> stringResource(R.string.game_play_with_not_installed)
                            entry.isLegalRisk -> stringResource(R.string.game_legal_risk_note)
                            else -> null
                        },
                    isFocused = index == focused,
                    isQuiet = !inPanel && index == current,
                    badge = if (index == current) stringResource(R.string.settings_badge_current) else null,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
        model.content.emulatorAppCount?.let { count ->
            PanelFact(pluralStringResource(R.plurals.settings_emulators_found, count, rememberCountFormat().format(count)))
        }
    }
}

/** Detect new emulators: what it does, how many were found, and how the last run went. */
@Composable
internal fun DetectPanel(model: SettingsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_detect_emulators))
        PanelBody(stringResource(R.string.settings_detect_body))
        model.content.emulatorAppCount?.let { count ->
            BasicText(
                pluralStringResource(R.plurals.settings_emulators_found, count, rememberCountFormat().format(count)),
                style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text2),
                modifier = Modifier.onRowTextLine(),
            )
        }
        PanelLine(model, SettingsOption.DetectEmulators, null)
    }
}

/**
 * The panel's line about [option]: what it just tried ([SettingsWork.notice]) or, when nothing was
 * said, [status]. In `text`, so it reads as news.
 */
@Composable
internal fun PanelLine(
    model: SettingsModel,
    option: SettingsOption,
    status: String?,
) {
    // Rescan shows its busy notice as a line of its own under the progress (RescanPanel).
    val notice = model.work.notice?.takeIf { it.first == option && option != SettingsOption.Rescan }?.second
    val text = notice?.lineText(model.work.noticeCount) ?: status ?: return
    // While a scan runs the line leads with the breathing dot, also when it says "Already reading your games".
    // No tabular figures: the font's draw slashed zeros that look foreign. The line is left-aligned with nothing beside it, so growing counts move nothing.
    val style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text)
    val tag = if (notice != null) SettingsTags.NOTICE else SettingsPanelTags.STATUS
    if (option == SettingsOption.Rescan && model.content.isScanning) {
        ScanPulseLine(text, style, Modifier.onRowTextLine(), dotModifier = Modifier.testTag(SettingsPanelTags.SCAN_PULSE), textModifier = Modifier.testTag(tag))
    } else {
        BasicText(text, style = style, modifier = Modifier.onRowTextLine().testTag(tag))
    }
}

@Composable
private fun folderAddText(state: FolderAddState): String? =
    when (state) {
        FolderAddState.Idle -> null
        FolderAddState.Adding -> stringResource(R.string.settings_folder_adding)
        FolderAddState.Added -> stringResource(R.string.settings_folder_added)
        FolderAddState.AlreadyAdded -> stringResource(R.string.setup_message_already_added)
        FolderAddState.NotKept -> stringResource(R.string.setup_message_not_kept)
        FolderAddState.CouldNotOpen -> stringResource(R.string.setup_message_could_not_open)
    }

/** "1,031 games · 14 systems", counting, not read yet, none, or unreadable. */
@Composable
private fun folderSummary(
    folder: SetupFolder,
    isScanning: Boolean,
): String =
    when {
        folder.isUnavailable -> stringResource(R.string.settings_folder_unavailable)
        folder.gameCount == 0 && !folder.isCounted && isScanning -> stringResource(R.string.setup_folder_counting)
        folder.gameCount == 0 && !folder.isCounted -> stringResource(R.string.settings_folder_not_read)
        folder.gameCount == 0 -> stringResource(R.string.settings_folder_no_games)
        else -> {
            val format = rememberCountFormat()
            stringResource(
                R.string.setup_folder_counts,
                pluralStringResource(R.plurals.home_games_count, folder.gameCount, format.format(folder.gameCount)),
                pluralStringResource(R.plurals.home_systems_count, folder.systemCount, format.format(folder.systemCount)),
            )
        }
    }

/**
 * An item of a panel list, at least a list row high. Focused: accent pill with ring, no dot, `strong`
 * 13 / 500 (the pill: [focusPill]). Otherwise [isQuiet] gives the board's `sel2` card, [isOutlined] a 1 dp `sel` edge
 * (folder cards while the panel has the focus), neither a plain row. [padded]: the card's 10 above
 * and below for a title with a detail line. [isChecked]: a checkmark at the end, in the accent
 * (Choose systems…).
 */
@Composable
internal fun PanelItem(
    title: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    detail: String? = null,
    titleStyle: TextStyle = PlumTheme.type.meta.copy(color = PlumTheme.colors.text),
    isQuiet: Boolean = false,
    isOutlined: Boolean = false,
    padded: Boolean = false,
    badge: String? = null,
    isChecked: Boolean = false,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val shape = RoundedCornerShape(PlumDimens.cornerRow)
    val surface =
        when {
            isFocused -> Modifier
            isQuiet -> Modifier.background(colors.sel2, shape)
            isOutlined -> Modifier.border(PlumDimens.settingsSwatchEdge, colors.sel, shape)
            else -> Modifier
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = PlumTheme.layout.rowHeight)
                .focusPill(isFocused)
                .then(surface)
                .padding(horizontal = PlumDimens.settingsPanelRowPadding, vertical = if (padded) PlumDimens.settingsFolderCardPadding else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap)) {
            val style = if (isFocused) type.settingsPanelItemFocused.copy(color = colors.strong) else titleStyle
            BasicText(title, style = style, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (detail != null) BasicText(detail, style = type.smallFact.copy(color = if (isFocused) colors.meta else colors.muted))
        }
        if (badge != null) {
            Spacer(Modifier.width(PlumDimens.rowTrailingGap))
            Badge(badge)
        }
        if (isChecked) {
            Spacer(Modifier.width(PlumDimens.rowTrailingGap))
            // Plum Serif has no U+2713: the mark is always drawn in Plum Sans, which has it.
            BasicText(
                stringResource(R.string.settings_check_mark),
                style = type.settingsPanelItemFocused.copy(color = colors.accent, fontFamily = PlumFonts.Sans),
                modifier = Modifier.testTag(SettingsTags.CHECK_MARK),
            )
        }
    }
}

private const val AUTOMATIC_KEY = "automatic"
