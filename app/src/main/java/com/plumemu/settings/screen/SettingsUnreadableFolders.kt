package com.plumemu.settings.screen

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coredata.db.SkippedSubfolderDao
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.rememberCountFormat

/*
 * Library › "Folder could not be read": listed only while the last scan of a picked folder failed to list some of its
 * sub-folders. That scan deleted nothing (games removed from the folder stay), so the person is told and offered Scan again
 * (Rescan library) and, for each folder, Skip this folder (the skipped sub-folders of ROM folders › a folder).
 */

/** A sub-folder the last scan of picked folder [folderId] could not list: [path] below it, [folderName] as picked. */
internal data class UnreadableFolder(
    val folderId: Long,
    val folderName: String,
    val path: String,
) {
    val key get() = FolderKey(folderId, path)
}

/** One item of the panel, in order: [ScanAgain], then one [Skip] per folder of [unreadableFolders]. */
internal sealed interface UnreadablePanelItem {
    data object ScanAgain : UnreadablePanelItem

    data class Skip(val folder: UnreadableFolder) : UnreadablePanelItem
}

/**
 * The sub-folders the last scans could not list, by picked folder then path; those the person skips and those of a folder
 * being removed are left out: the next scan no longer reads them. One whose skip is still being saved stays listed, so a
 * save that fails is said on this panel instead of a row that left and came back.
 */
internal fun unreadableFolders(content: SettingsContent): List<UnreadableFolder> =
    content.folders.filter { !it.isRemoving && it.id !in content.removingFolderIds }.flatMap { folder ->
        val skipped = content.skippedSubfolders[folder.id].orEmpty().map(SkippedSubfolderDao::matchKey)
        folder.unreadablePaths
            .filter { path ->
                val key = SkippedSubfolderDao.matchKey(path)
                skipped.none { key == it || key.startsWith("$it/") }
            }.map { UnreadableFolder(folder.id, folder.displayName, it) }
    }

internal fun unreadablePanelItems(content: SettingsContent): List<UnreadablePanelItem> =
    listOf(UnreadablePanelItem.ScanAgain) + unreadableFolders(content).map(UnreadablePanelItem::Skip)

/** A folder skipped (or read by a scan) leaves: the cursor stays on the same item, else inside the list. */
internal fun unreadableIndexAfter(
    at: Int,
    before: SettingsContent,
    after: SettingsContent,
): Int {
    val now = unreadablePanelItems(after)
    return unreadablePanelItems(before).getOrNull(at)?.let { now.indexOf(it) }?.takeIf { it >= 0 } ?: at.coerceAtMost(now.lastIndex)
}

/** "ROMs/psx": the picked folder's stored name, then the path below it. */
@Composable
internal fun unreadableFolderName(folder: UnreadableFolder): String =
    folder.folderName.ifBlank { stringResource(R.string.setup_folder_unnamed) } + "/" + folder.path

/** The row's value: the one folder's last name, or how many there are. */
@Composable
internal fun unreadableRowValue(content: SettingsContent): String {
    val folders = unreadableFolders(content)
    return folders.singleOrNull()?.path?.substringAfterLast('/')
        ?: pluralStringResource(R.plurals.settings_folders_count, folders.size, rememberCountFormat().format(folders.size))
}

/** The row's label, "Folder could not be read" or "Folders could not be read". */
@Composable
internal fun unreadableRowLabel(content: SettingsContent): String {
    // Two strings, not plurals: a plural with no number is wrong where "one" also means 0 (French).
    return stringResource(if (unreadableFolders(content).size > 1) R.string.settings_unreadable_title_many else R.string.settings_unreadable_row)
}

/** The panel: what happened in plain words, Scan again, then each folder with Skip this folder. */
@Composable
internal fun UnreadableFoldersPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val content = model.content
    val items = unreadablePanelItems(content)
    // As Game folders' panel: beside the list (the cursor on the row) nothing is focused and the folders are quiet.
    val inPanel = model.focus.column == SettingsColumn.Panel && model.focus.page != null
    val focused = if (inPanel) model.focus.panelIndex.coerceAtMost(items.lastIndex) else -1
    val skipLabel = stringResource(R.string.settings_unreadable_skip)
    PanelListColumn(Modifier.fillMaxHeight().testTag(SettingsPanelTags.UNREADABLE_FOLDERS)) {
        PanelTitle(unreadableRowLabel(content))
        PanelBody(stringResource(if (items.size > 2) R.string.settings_unreadable_body_many else R.string.settings_unreadable_body))
        PanelLine(model, SettingsOption.UnreadableFolders, null)
        PanelLazyList(focused, Modifier) {
            itemsIndexed(items, key = { _, item -> itemKey(item) }, contentType = { _, item -> item.javaClass.simpleName }) { index, item ->
                val isAction = item is UnreadablePanelItem.ScanAgain
                PanelItem(
                    title =
                        when (item) {
                            UnreadablePanelItem.ScanAgain -> stringResource(R.string.settings_unreadable_scan_again)
                            is UnreadablePanelItem.Skip -> unreadableFolderName(item.folder)
                        },
                    detail = if (isAction) null else skipLabel,
                    detailMaxLines = 2,
                    titleStyle =
                        if (isAction) {
                            PlumTheme.type.settingsPanelAction.copy(color = PlumTheme.colors.accent)
                        } else {
                            PlumTheme.type.meta.copy(color = PlumTheme.colors.text)
                        },
                    isFocused = index == focused,
                    isQuiet = !inPanel && !isAction,
                    isOutlined = inPanel && !isAction,
                    padded = !isAction,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}

private fun itemKey(item: UnreadablePanelItem): String =
    when (item) {
        UnreadablePanelItem.ScanAgain -> "scan"
        is UnreadablePanelItem.Skip -> "skip:${item.folder.folderId}:${item.folder.path}"
    }
