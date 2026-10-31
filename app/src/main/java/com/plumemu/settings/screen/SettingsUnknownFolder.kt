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
import com.plumemu.home.rememberCountFormat

/*
 * Settings › Library › an unknown folder: the side
 * panel that answers which system a folder belongs to. Item 0 is "Ignore this folder", then every system, A to Z, as the
 * rest of Plumemu names them; the current answer wears the "Current" badge, so an answer can be changed. A applies
 * (the model's `onUnknownFolderEnter`), B cancels.
 */

/** Item 0's key; the systems are keyed by id, so the lazy list keeps its place when the list changes. */
private const val IGNORE_KEY = "ignore"

/** The panel for the folder under the cursor (wide: previewed beside the row) or opened by A or a tap. */
@Composable
internal fun UnknownFolderPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val focus = model.focus
    val inPanel = focus.column == SettingsColumn.Panel
    val row = SettingsListPages.unknownFolderAt(focus, model.content) ?: return
    val choices = model.content.folderSystemChoices
    val focused = if (inPanel) focus.panelIndex else -1
    val current = SettingsListPages.answerIndex(row, model.content)
    val isUnanswered = row.answer == FolderAnswer.Unanswered
    val isSaving = row.key in model.content.answeringFolders || row.key in model.work.answering
    Column(Modifier.fillMaxHeight().testTag(SettingsTags.UNKNOWN_FOLDER_PANEL), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(row.name)
        PanelBody(
            if (isUnanswered && row.files <= 0) {
                stringResource(R.string.settings_unknown_folder_body_none)
            } else if (isUnanswered) {
                pluralStringResource(R.plurals.settings_unknown_folder_body, row.files, rememberCountFormat().format(row.files))
            } else {
                stringResource(R.string.settings_unknown_folder_body_answered)
            },
        )
        PanelLine(model, SettingsOption.UnknownFolder, if (isSaving) stringResource(R.string.settings_unknown_folder_saving_line) else null)
        val ignore = stringResource(R.string.settings_unknown_folder_ignore)
        val entries = listOf<FolderSystemChoice?>(null) + choices
        PanelLazyList(focused, Modifier.weight(1f, fill = false)) {
            itemsIndexed(entries, key = { _, entry -> entry?.id ?: IGNORE_KEY }, contentType = { _, _ -> "system" }) { index, entry ->
                PanelItem(
                    title = entry?.name ?: ignore,
                    isFocused = index == focused,
                    isQuiet = !inPanel && index == current && !isUnanswered,
                    badge = if (index == current && !isUnanswered) stringResource(R.string.settings_badge_current) else null,
                    modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}
