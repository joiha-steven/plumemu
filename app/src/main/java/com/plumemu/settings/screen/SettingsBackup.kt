package com.plumemu.settings.screen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.account.AccountStatus
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.graph.BackupFailure
import com.plumemu.graph.BackupState
import com.plumemu.home.rememberCountFormat
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen

/*
 * Settings › Advanced › Back up…: the choice between
 * Settings and Everything, Android's save picker, then the progress with Cancel and a plain line
 * for how it ended. The work itself is the graph's (GraphBackup); this file only shows it.
 */

/** What Settings asks of the backup: the picker, Cancel, and forgetting the last result. */
internal interface SettingsBackupActions {
    /** Android's save picker for a [kind] backup; false when the device has none. */
    fun pick(kind: BackupKind): Boolean

    fun cancel()

    /** The focus left Back up…: its last result is not shown again. */
    fun forgetResult()

    object None : SettingsBackupActions {
        override fun pick(kind: BackupKind): Boolean = false

        override fun cancel() = Unit

        override fun forgetResult() = Unit
    }
}

/** The panel's buttons, top to bottom: the two choices, or Cancel while a backup runs. */
internal enum class BackupPanelItem { Settings, Everything, Cancel }

internal fun backupPanelItems(state: BackupState): List<BackupPanelItem> =
    if (state is BackupState.Running) listOf(BackupPanelItem.Cancel) else listOf(BackupPanelItem.Settings, BackupPanelItem.Everything)

/**
 * The row's value: "Backing up…" while one runs; how the last one ended ("Saved ›", "Failed ›",
 * "Cancelled ›", "Interrupted ›") until the person has seen its panel and left the row, so a result
 * that came while Settings was closed is not missed; else "›".
 */
@Composable
internal fun backupRowValue(state: BackupState): String {
    val word =
        when (state) {
            is BackupState.Running -> return stringResource(R.string.settings_backup_running)
            BackupState.Idle -> return stringResource(R.string.settings_value_chevron)
            is BackupState.Done -> R.string.settings_backup_value_saved
            is BackupState.Failed -> R.string.settings_backup_value_failed
            is BackupState.Cancelled -> R.string.settings_backup_value_cancelled
            is BackupState.Interrupted -> R.string.settings_backup_value_interrupted
        }
    return stringResource(R.string.settings_value_opens, stringResource(word))
}

/** The panel's line for [state]: the progress, or how the last backup ended; null when idle. */
@Composable
internal fun backupLineText(state: BackupState): String? {
    val format = rememberCountFormat()
    return when (state) {
        BackupState.Idle -> null
        is BackupState.Running -> {
            val progress = state.progress?.takeIf { it.filesTotal > 0 }
            if (state.isChecking) {
                stringResource(R.string.settings_backup_checking)
            } else if (progress == null) {
                stringResource(R.string.settings_backup_running)
            } else {
                pluralStringResource(R.plurals.settings_backup_progress, progress.filesTotal, format.format(progress.filesDone), format.format(progress.filesTotal))
            }
        }
        is BackupState.Done -> {
            val size = Formatter.formatShortFileSize(LocalContext.current, state.bytes)
            if (state.fileName != null) stringResource(R.string.settings_backup_done, state.fileName, size) else stringResource(R.string.settings_backup_done_unnamed, size)
        }
        is BackupState.Cancelled -> stringResource(if (state.deleted) R.string.settings_backup_cancelled else R.string.settings_backup_cancelled_kept)
        is BackupState.Failed ->
            stringResource(
                when (state.reason) {
                    BackupFailure.Damaged -> if (state.deleted) R.string.settings_backup_damaged else R.string.settings_backup_damaged_kept
                    BackupFailure.NotReadBack -> if (state.deleted) R.string.settings_backup_not_read_back else R.string.settings_backup_not_read_back_kept
                    BackupFailure.BoxArtBusy -> R.string.settings_backup_box_art_busy
                    BackupFailure.Other -> if (state.deleted) R.string.settings_backup_failed else R.string.settings_backup_failed_kept
                },
            )
        is BackupState.Interrupted ->
            when {
                state.deleted -> stringResource(R.string.settings_backup_interrupted)
                state.fileName != null -> stringResource(R.string.settings_backup_interrupted_kept, state.fileName)
                else -> stringResource(R.string.settings_backup_interrupted_kept_unnamed)
            }
    }
}

/**
 * What a backup holds, the password warning while an account is stored (the file carries it in
 * plain text), the line, and the buttons (Reset's confirm buttons, stacked like the ScreenScraper panel's). It
 * scrolls when the text is large, keeping the focused button in view.
 */
@Composable
internal fun BackupPanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val content = model.content
    val state = content.backup
    val inPanel = model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.Backup
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag(SettingsTags.BACKUP_PANEL),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap),
    ) {
        PanelTitle(stringResource(R.string.settings_backup_title))
        PanelBody(stringResource(R.string.settings_backup_body))
        if (content.account is AccountStatus.SignedIn) {
            BasicText(
                stringResource(R.string.settings_backup_password_note),
                style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text),
                modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.BACKUP_PASSWORD),
            )
        }
        PanelLine(model, SettingsOption.Backup, backupLineText(state))
        if (state is BackupState.Done && state.missingMedia > 0) {
            PanelBody(pluralStringResource(R.plurals.settings_backup_done_missing, state.missingMedia, rememberCountFormat().format(state.missingMedia)))
        }
        // One above the other, as wide as the widest: side by side, "Everything" did not fit the
        // 248 dp panel from the Large text size on (SettingsBackupFitTest).
        Column(
            modifier = Modifier.ringRoomAboveAndBelow().width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
        ) {
            backupPanelItems(state).forEachIndexed { index, item ->
                val isFocused = inPanel && model.focus.panelIndex == index
                SetupButton(
                    text = stringResource(backupItemText(item)),
                    isPrimary = item == BackupPanelItem.Everything,
                    isFocused = isFocused,
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset).tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                )
            }
        }
    }
}

private fun backupItemText(item: BackupPanelItem): Int =
    when (item) {
        BackupPanelItem.Settings -> R.string.settings_backup_settings
        BackupPanelItem.Everything -> R.string.settings_backup_everything
        BackupPanelItem.Cancel -> R.string.settings_cancel
    }
