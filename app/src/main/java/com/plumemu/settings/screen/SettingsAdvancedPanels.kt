package com.plumemu.settings.screen

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.graph.BackupState
import com.plumemu.graph.LibraryResetState

/*
 * Settings › Advanced's two rows that stand for several: Back up and restore lists Back up…,
 * Restore… and Undo restore; Reset… lists the two resets. Each item opens the page it always had.
 */

/** Back up and restore's items, top to bottom. */
internal enum class BackupRestoreItem { Backup, Restore, Undo }

/** Undo restore only while the way-back set exists. */
internal fun backupRestoreItems(content: SettingsContent): List<BackupRestoreItem> =
    listOfNotNull(BackupRestoreItem.Backup, BackupRestoreItem.Restore, BackupRestoreItem.Undo.takeIf { content.wayBack != null })

private val BackupRestoreItem.option: SettingsOption
    get() =
        when (this) {
            BackupRestoreItem.Backup -> SettingsOption.Backup
            BackupRestoreItem.Restore -> SettingsOption.Restore
            BackupRestoreItem.Undo -> SettingsOption.UndoRestore
        }

/**
 * The row's value, the most urgent first: what runs, then a result not seen yet (a restore's first: it changed
 * the library), then the space the way-back set keeps. A result stays until its page was opened and the row left,
 * so one that came while Settings was closed is not missed.
 */
@Composable
internal fun backupAndRestoreValue(content: SettingsContent): OptionValue {
    val isRestoring = content.restore.isRunning()
    val isBackingUp = content.backup is BackupState.Running
    val word =
        when {
            isRestoring -> restoreRowValue(content.restore)
            isBackingUp -> backupRowValue(content.backup)
            else -> restoreRowValue(content.restore).ifEmpty { backupRowValue(content.backup) }.ifEmpty { undoRowValue(content) }
        }
    return opensUnlessBusy(word, isRestoring || isBackingUp)
}

@Composable
private fun itemWord(
    item: BackupRestoreItem,
    content: SettingsContent,
): String =
    when (item) {
        BackupRestoreItem.Backup -> backupRowValue(content.backup)
        BackupRestoreItem.Restore -> restoreRowValue(content.restore)
        BackupRestoreItem.Undo -> undoRowValue(content)
    }

/** The list, each item with the word its own row used to show. */
@Composable
internal fun BackupRestorePanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val content = model.content
    val inPanel = model.focus.column == SettingsColumn.Panel
    val focused = if (inPanel) model.focus.panelIndex else -1
    PanelListColumn(Modifier.fillMaxHeight()) {
        PanelTitle(stringResource(R.string.settings_backup_and_restore))
        PanelLazyList(focused, Modifier) {
            itemsIndexed(backupRestoreItems(content), key = { _, item -> item }) { index, item ->
                PanelItem(
                    title = stringResource(item.option.label),
                    detail = itemWord(item, content).ifEmpty { null },
                    isFocused = index == focused,
                    isOutlined = inPanel,
                    padded = true,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}

/** Reset…'s items, top to bottom. */
internal enum class ResetPanelItem(
    val option: SettingsOption,
    @StringRes val body: Int,
) {
    All(SettingsOption.ResetAll, R.string.settings_reset_all_body),
    Library(SettingsOption.ResetLibrary, R.string.settings_reset_library_body),
}

/** How the reset that ran went, until the focus leaves the row; null when none ran. */
@StringRes
@Composable
private fun resetDoneText(model: SettingsModel): Int? {
    val status = LocalSettingsStatus.current
    return when (model.doneOption) {
        SettingsOption.ResetAll -> if (status.saveFailed) null else R.string.settings_reset_all_done
        SettingsOption.ResetLibrary ->
            when (status.libraryReset) {
                LibraryResetState.Idle, LibraryResetState.Forgetting -> R.string.settings_reset_library_forgetting
                LibraryResetState.Forgotten -> R.string.settings_reset_library_done
                LibraryResetState.Failed -> R.string.settings_reset_library_failed
            }
        else -> null
    }
}

/** The two resets, each with what it does, and how the last one went. */
@Composable
internal fun ResetPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val inPanel = model.focus.column == SettingsColumn.Panel
    val focused = if (inPanel) model.focus.panelIndex else -1
    PanelListColumn(Modifier.fillMaxHeight()) {
        PanelTitle(stringResource(R.string.settings_reset))
        resetDoneText(model)?.let { done ->
            BasicText(
                stringResource(done),
                style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
                modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.DONE),
            )
        }
        PanelLazyList(focused, Modifier) {
            itemsIndexed(ResetPanelItem.entries, key = { _, item -> item }) { index, item ->
                PanelItem(
                    title = stringResource(item.option.label),
                    // Walked, only the focused one says what it does: two long cards never fit, and the one left would stand as a blank band under the title.
                    detail = stringResource(item.body).takeIf { !inPanel || index == focused },
                    isFocused = index == focused,
                    isOutlined = inPanel,
                    padded = true,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}
