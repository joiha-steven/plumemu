package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.rememberCountFormat
import com.plumemu.setup.folderLabel

/**
 * Library › Files not added: each group's heading, then its files (name, and the folder it is in
 * under it, both cut with "…" when long), then "and 340 more files"; an unknown folder is one line,
 * and A on it opens its page ([UnknownFolderPanel]). Every line is a stop, so the gamepad scrolls the list; A on a file
 * does nothing. With nothing left out: "Every file is in the library", or "No files to show" while there are no games.
 */
@Composable
internal fun FilesNotAddedPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val content = model.content
    val items = content.notAdded
    val inPanel = focus.column == SettingsColumn.Panel && focus.page == PanelPage.FilesNotAdded
    // A heading never wears the focus: a page changed under the cursor shows none until it moves.
    val focused = if (inPanel) focus.panelIndex.coerceAtMost(items.lastIndex).takeIf { items.getOrNull(it) !is NotAddedItem.Heading } ?: -1 else -1
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val format = rememberCountFormat()
    val chooseSystem = stringResource(R.string.settings_unknown_folder_choose)
    val systemNames = content.folderSystemChoices.associate { it.id to it.name }
    val folderNames = content.folders.associate { it.id to folderLabel(it) }
    PanelListColumn(Modifier.fillMaxHeight().testTag(SettingsPanelTags.FILES_NOT_ADDED)) {
        PanelTitle(stringResource(R.string.settings_files_not_added_title))
        // An unknown folder with no file counted yet still waits for an answer: the row says so, this must not say all is in.
        val allIn = notAddedCount(items) == 0 && items.none { it is NotAddedItem.Folder && it.answer == FolderAnswer.Unanswered }
        PanelBody(
            stringResource(
                when {
                    // With no games at all, "every file is in the library" would be an empty claim.
                    items.isEmpty() && content.gameCount == 0 -> R.string.settings_files_not_added_nothing
                    allIn -> R.string.settings_files_not_added_none
                    else -> R.string.settings_files_not_added_body
                },
            ),
        )
        PanelLine(model, SettingsOption.FilesNotAdded, null)
        PanelLazyList(focused, Modifier, restsAtTopOn = items.indexOfFirst { it !is NotAddedItem.Heading }.takeIf { it > 0 } ?: -1) {
            itemsIndexed(items, key = { _, item -> item.key }, contentType = { _, item -> item.javaClass.simpleName }) { index, item ->
                val modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index))
                when (item) {
                    is NotAddedItem.Heading ->
                        PanelItem(
                            title = headingText(item, systemNames),
                            isFocused = index == focused,
                            titleStyle = type.settingsPanelAction.copy(color = colors.text),
                            modifier = modifier,
                        )
                    is NotAddedItem.File ->
                        PanelItem(
                            title = item.name,
                            detail = item.directoryPath.ifEmpty { folderNames[item.folderId].orEmpty() },
                            detailMaxLines = PATH_LINES,
                            isFocused = index == focused,
                            padded = true,
                            modifier = modifier,
                        )
                    is NotAddedItem.More ->
                        PanelItem(
                            title = pluralStringResource(R.plurals.settings_files_not_added_more, item.count, format.format(item.count)),
                            isFocused = index == focused,
                            titleStyle = type.meta.copy(color = colors.muted),
                            modifier = modifier,
                        )
                    is NotAddedItem.Folder ->
                        PanelItem(
                            title = item.folder.relativePath,
                            detail =
                                if (item.isWaitingWithFiles(content)) {
                                    stringResource(
                                        R.string.settings_files_not_added_folder,
                                        pluralStringResource(R.plurals.settings_unknown_folder_files, item.files, format.format(item.files)),
                                        chooseSystem,
                                    )
                                } else {
                                    unknownFolderValue(content, content.unknownFolders.firstOrNull { it.key == item.folder })
                                },
                            detailMaxLines = PATH_LINES,
                            isFocused = index == focused,
                            isOutlined = inPanel,
                            padded = true,
                            titleStyle = type.settingsPanelAction.copy(color = if (item.answer == FolderAnswer.Unanswered) colors.accent else colors.text),
                            modifier = modifier,
                        )
                }
            }
        }
    }
}

private fun NotAddedItem.Folder.isWaitingWithFiles(content: SettingsContent): Boolean =
    answer == FolderAnswer.Unanswered && files > 0 && folder !in content.answeringFolders

/** Files not added's row: how many folders wait for an answer, else how many files were left out. */
@Composable
internal fun filesNotAddedValue(content: SettingsContent): String {
    val format = rememberCountFormat()
    val waiting = content.unknownFolders.count { it.answer == FolderAnswer.Unanswered }
    if (waiting > 0) return pluralStringResource(R.plurals.settings_files_not_added_waiting, waiting, format.format(waiting))
    return notAddedCount(content.notAdded).let { if (it == 0) "" else format.format(it) }
}

/** A long folder path takes at most two lines under the file's name. */
private const val PATH_LINES = 2

@Composable
private fun headingText(
    heading: NotAddedItem.Heading,
    systemNames: Map<String, String>,
): String =
    when (heading.group) {
        NotAddedGroup.NoSystem -> stringResource(R.string.settings_files_not_added_no_system)
        NotAddedGroup.AnsweredFolders -> stringResource(R.string.settings_files_not_added_answered)
        NotAddedGroup.Unmatched ->
            heading.systemId?.let { id -> stringResource(R.string.settings_files_not_added_unmatched_system, systemNames[id] ?: id) }
                ?: stringResource(R.string.settings_files_not_added_unmatched)
        NotAddedGroup.Bios -> stringResource(R.string.settings_files_not_added_bios)
        NotAddedGroup.Saves -> stringResource(R.string.settings_files_not_added_saves)
        NotAddedGroup.Junk -> stringResource(R.string.settings_files_not_added_junk)
    }
