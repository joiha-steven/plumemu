package com.plumemu.settings.screen

import com.plumemu.sound.Outcome

/**
 * What the panel column does on Settings › ROM folders: a folder's page, Skip a sub-folder… and the
 * Remove confirm (`SettingsFolderPage.kt`). [SettingsPanelModel] adds the other panels on top.
 */
internal abstract class SettingsFolderModel(
    initialFocus: SettingsFocus,
    work: SettingsWork,
) : SettingsNavigation(initialFocus, work) {
    /** A folder opens its page (on Skip a sub-folder…); "+ Add folder", the last item, opens the picker. */
    protected fun onFolderEnter(index: Int) {
        val folder =
            content.folders.getOrNull(index) ?: run {
                work.openFolderPicker()
                feel.report(Outcome.Activated)
                return
            }
        if (!work.isRemoving(folder.id, content)) moveTo(focus.copy(page = PanelPage.Folder, panelIndex = 0, folderId = folder.id))
    }

    /**
     * A on a folder's page ([folderPageItems]), about the folder the page opened for (by id):
     * Skip a sub-folder… reads its sub-folders and lists them; a skipped one is read again (its
     * games come back with the scan that follows); Remove this folder… opens the confirm (Cancel first).
     */
    protected fun onFolderPageEnter(index: Int) {
        val folderId = focus.folderId
        if (folderId == null || content.folder(folderId) == null || work.isRemoving(folderId, content)) return backToFolders()
        when (val item = folderPageItems(content, folderId).getOrNull(index) ?: return) {
            FolderPageItem.SkipOne -> {
                work.listSubfolders(folderId)
                moveTo(focus.copy(page = PanelPage.SkipSubfolder, panelIndex = 0))
            }
            is FolderPageItem.Skipped ->
                if (!work.skipSubfolder(FolderKey(folderId, item.path), skip = false, content)) return feel.report(Outcome.Nothing)
            FolderPageItem.Remove -> moveTo(focus.copy(page = PanelPage.RemoveFolder, panelIndex = SettingsModel.CONFIRM_CANCEL))
        }
        feel.report(Outcome.Activated)
    }

    /** A on a sub-folder of Skip a sub-folder…: skips it (the scan that follows drops its games) and returns to the folder's page. */
    protected fun onSkipPickEnter(index: Int) {
        val folderId = focus.folderId ?: return backToFolders()
        val name = pickableSubfolders(work.subfolders, content).getOrNull(index) ?: return feel.report(Outcome.Nothing)
        if (!work.skipSubfolder(FolderKey(folderId, name), skip = true, content)) return feel.report(Outcome.Nothing)
        feel.report(Outcome.Activated)
        backToFolderPage(FolderPageItem.SkipOne)
    }

    /** Back on the folder's page, on [item] (its first item when the page no longer has it). */
    protected fun backToFolderPage(item: FolderPageItem) {
        work.forgetSubfolders()
        if (content.folder(focus.folderId) == null) return backToFolders()
        val at = folderPageItems(content, focus.folderId).indexOf(item).coerceAtLeast(0)
        moveTo(focus.copy(page = PanelPage.Folder, panelIndex = at))
    }

    protected fun onRemoveEnter(index: Int) {
        // By id: only the folder the confirm named is removed, whatever the list does meanwhile.
        val folderId = focus.folderId
        if (index == SettingsModel.CONFIRM_RESET && folderId != null && content.folder(folderId) != null) {
            work.removeFolder(folderId)
            backToFolders()
        } else {
            backToFolderPage(FolderPageItem.Remove)
        }
    }

    protected fun backToFolders() = moveTo(SettingsListPages.backToFolders(focus, content))

    /**
     * Where the cursor stays on a folder's page, its pick or its confirm when the content changes:
     * on the same item of the page, inside the pick; null when the folder left (back to the list).
     */
    protected fun folderIndexKept(
        focus: SettingsFocus,
        before: SettingsContent,
        after: SettingsContent,
    ): Int? {
        if (after.folder(focus.folderId) == null) return null
        val at = focus.panelIndex
        return when (focus.page) {
            PanelPage.Folder -> {
                val now = folderPageItems(after, focus.folderId)
                folderPageItems(before, focus.folderId).getOrNull(at)?.let { now.indexOf(it) }?.takeIf { it >= 0 } ?: at.coerceAtMost(now.lastIndex)
            }
            PanelPage.SkipSubfolder -> at.coerceAtMost(pickableSubfolders(work.subfolders, after).size - 1)
            else -> at
        }
    }
}
