package com.plumemu.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coredata.db.SkippedSubfolderDao
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.setup.folderLabel

/*
 * Settings › ROM folders › a folder (Skipped sub-folders): A on
 * a folder opens its page: "Skip a sub-folder…", the sub-folders it skips (A reads one again), then
 * "Remove this folder…" (the confirm as before). "Skip a sub-folder…" lists the folder's
 * first-level sub-folders read from disk (SettingsWork.listSubfolders); A skips one and returns.
 * Each change is stored and then scanned by the graph (GraphSettingsLibrary.skipSubfolder).
 */

/** One item of a folder's page, in order: [SkipOne], each [Skipped] path, [Remove]. */
internal sealed interface FolderPageItem {
    data object SkipOne : FolderPageItem

    data class Skipped(val path: String) : FolderPageItem

    data object Remove : FolderPageItem
}

/** The items of [folderId]'s page; the skipped paths as [SettingsContent.skippedSubfolders] orders them. */
internal fun folderPageItems(
    content: SettingsContent,
    folderId: Long?,
): List<FolderPageItem> =
    listOf(FolderPageItem.SkipOne) + content.skippedSubfolders[folderId].orEmpty().map(FolderPageItem::Skipped) + FolderPageItem.Remove

/** What "Skip a sub-folder…" shows for one folder: being read, the sub-folders, or that it could not be read. */
internal sealed interface SubfolderState {
    data object Loading : SubfolderState

    data class Listed(val names: List<String>) : SubfolderState

    data object Unreadable : SubfolderState
}

/** The listing of [folderId]'s sub-folders that "Skip a sub-folder…" asked for ([SettingsWork.subfolders]). */
internal data class SubfolderPick(
    val folderId: Long,
    val state: SubfolderState,
)

/** The sub-folders "Skip a sub-folder…" offers: those read from disk that the folder does not skip yet (ignoring case). */
internal fun pickableSubfolders(
    pick: SubfolderPick?,
    content: SettingsContent,
): List<String> {
    val listed = (pick?.state as? SubfolderState.Listed)?.names ?: return emptyList()
    val skipped = content.skippedSubfolders[pick.folderId].orEmpty().mapTo(HashSet(), SkippedSubfolderDao::matchKey)
    return listed.filter { SkippedSubfolderDao.matchKey(it) !in skipped }
}

/** A folder's page: its name, what skipping does, then the items of [folderPageItems]. */
@Composable
internal fun FolderPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val content = model.content
    val folder = content.folder(focus.folderId) ?: return
    val items = folderPageItems(content, folder.id)
    val focused = focus.panelIndex.coerceAtMost(items.lastIndex)
    val skippedDetail = stringResource(R.string.settings_folder_skipped)
    Column(Modifier.fillMaxHeight().testTag(SettingsPanelTags.FOLDER_PAGE), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(folderLabel(folder))
        PanelBody(stringResource(R.string.settings_folder_body))
        PanelLine(model, SettingsOption.RomFolders, null)
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(items, key = { _, item -> folderItemKey(item) }, contentType = { _, item -> item.javaClass.simpleName }) { index, item ->
                val isAction = item !is FolderPageItem.Skipped
                PanelItem(
                    title =
                        when (item) {
                            FolderPageItem.SkipOne -> stringResource(R.string.settings_folder_skip_one)
                            is FolderPageItem.Skipped -> item.path
                            FolderPageItem.Remove -> stringResource(R.string.settings_folder_remove)
                        },
                    detail = if (isAction) null else skippedDetail,
                    titleStyle =
                        if (isAction) {
                            PlumTheme.type.settingsPanelAction.copy(color = PlumTheme.colors.accent)
                        } else {
                            PlumTheme.type.meta.copy(color = PlumTheme.colors.text)
                        },
                    isFocused = index == focused,
                    isOutlined = !isAction,
                    padded = !isAction,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}

private fun folderItemKey(item: FolderPageItem): String =
    when (item) {
        FolderPageItem.SkipOne -> "skip"
        is FolderPageItem.Skipped -> "skipped:${item.path}"
        FolderPageItem.Remove -> "remove"
    }

/** "Skip a sub-folder…": the folder's sub-folders not skipped yet; a line instead while reading, when unreadable or when there are none. */
@Composable
internal fun SkipSubfolderPage(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val content = model.content
    val folder = content.folder(focus.folderId) ?: return
    val pick = model.work.subfolders?.takeIf { it.folderId == folder.id }
    val names = pickableSubfolders(pick, content)
    val focused = focus.panelIndex.coerceAtMost(names.lastIndex)
    Column(Modifier.fillMaxHeight().testTag(SettingsPanelTags.SKIP_SUBFOLDER_PAGE), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_skip_subfolder_title))
        PanelBody(stringResource(R.string.settings_skip_subfolder_body))
        val line =
            when (pick?.state) {
                null, SubfolderState.Loading -> stringResource(R.string.settings_skip_subfolder_loading)
                SubfolderState.Unreadable -> stringResource(R.string.settings_skip_subfolder_unreadable)
                is SubfolderState.Listed -> if (names.isEmpty()) stringResource(R.string.settings_skip_subfolder_none) else null
            }
        PanelLine(model, SettingsOption.RomFolders, line)
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(names, key = { _, name -> name }, contentType = { _, _ -> "subfolder" }) { index, name ->
                PanelItem(
                    title = name,
                    isFocused = index == focused,
                    isOutlined = true,
                    padded = true,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}
