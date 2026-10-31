package com.plumemu.settings.screen

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coredata.backup.BackupKind
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.graph.AccountInFile
import com.plumemu.graph.RestoreProblem
import com.plumemu.graph.RestoreState
import com.plumemu.graph.RestoreSummary
import com.plumemu.graph.WayBackSet
import com.plumemu.home.currentLocale
import com.plumemu.home.rememberCountFormat
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import java.text.DateFormat
import java.util.Date

/*
 * Settings › Advanced › Restore… and Undo last restore: Choose file… opens Android's open picker, the file is read and checked, a confirm
 * panel says what it holds and what it replaces, then the graph replaces (GraphRestore). Undo last
 * restore is listed while the way-back set exists. This file only shows the graph's state.
 */

/** What Settings asks of the restore: the picker, Cancel, Restore, Undo, Forget the copy, and forgetting the last result. */
internal interface SettingsRestoreActions {
    /** Android's open picker; false when the device has none. */
    fun pick(): Boolean

    fun cancel()

    fun confirm()

    fun undo()

    fun forgetCopy()

    /** The focus left the row: how the last one ended is not shown again. */
    fun forgetResult()

    object None : SettingsRestoreActions {
        override fun pick(): Boolean = false

        override fun cancel() = Unit

        override fun confirm() = Unit

        override fun undo() = Unit

        override fun forgetCopy() = Unit

        override fun forgetResult() = Unit
    }
}

/** Restore…'s buttons, top to bottom. */
internal enum class RestorePanelItem { ChooseFile, Cancel, Restore }

/**
 * Choose file… when nothing runs; Cancel while the file is read; Restore and Cancel at the confirm
 * (the cursor lands on Cancel: `SettingsPanelModel.focusKeptIn`); nothing while replacing.
 */
internal fun restorePanelItems(state: RestoreState): List<RestorePanelItem> =
    when (state) {
        is RestoreState.Reading -> listOf(RestorePanelItem.Cancel)
        is RestoreState.Confirm -> listOf(RestorePanelItem.Restore, RestorePanelItem.Cancel)
        is RestoreState.Replacing -> emptyList()
        else -> listOf(RestorePanelItem.ChooseFile)
    }

/** Undo restore's buttons: Undo the restore… and Forget the copy…, each asking again with Confirm and Cancel. */
internal enum class UndoPanelItem { Undo, Forget, Confirm, Cancel }

/** The first step's two buttons, or [ask]'s confirm (the cursor goes to Cancel); none while a restore or an Undo replaces. */
internal fun undoPanelItems(
    content: SettingsContent,
    ask: UndoPanelItem?,
): List<UndoPanelItem> =
    when {
        content.wayBack == null || content.restore is RestoreState.Replacing -> emptyList()
        ask == null -> listOf(UndoPanelItem.Undo, UndoPanelItem.Forget)
        else -> listOf(UndoPanelItem.Confirm, UndoPanelItem.Cancel)
    }

/** The Restore… row's value: what runs, or how the last one ended until the person has seen it; else "›". */
@Composable
internal fun restoreRowValue(state: RestoreState): String {
    val word =
        when (state) {
            RestoreState.Idle -> return stringResource(R.string.settings_value_chevron)
            is RestoreState.Reading -> return stringResource(R.string.settings_restore_reading)
            is RestoreState.Replacing -> return stringResource(if (state.isUndo) R.string.settings_undo_running else R.string.settings_restore_running)
            is RestoreState.Confirm -> R.string.settings_restore_value_ready
            is RestoreState.Refused -> R.string.settings_restore_value_refused
            is RestoreState.Done -> if (state.isUndo) R.string.settings_restore_value_undone else R.string.settings_restore_value_done
            is RestoreState.Failed -> R.string.settings_restore_value_failed
            is RestoreState.Interrupted -> R.string.settings_restore_value_interrupted
        }
    return stringResource(R.string.settings_value_opens, stringResource(word))
}

/** The Undo row's value: the space the way-back set keeps ("Copy · 412 MB ›"), or "Undoing…". */
@Composable
internal fun undoRowValue(content: SettingsContent): String {
    val state = content.restore
    if (state is RestoreState.Replacing && state.isUndo) return stringResource(R.string.settings_undo_running)
    val bytes = content.wayBack?.bytes ?: return stringResource(R.string.settings_value_chevron)
    return stringResource(R.string.settings_value_opens, stringResource(R.string.settings_undo_value_kept, Formatter.formatShortFileSize(LocalContext.current, bytes)))
}

/** A date as the person's language writes it ("23 Oct 2026", "Oct 23, 2026"). */
@Composable
private fun dateText(millis: Long): String {
    val locale = currentLocale()
    return remember(millis, locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(millis)) }
}

/** The panel's line for [state]; null when idle. */
@Composable
internal fun restoreLineText(
    state: RestoreState,
    wayBack: WayBackSet? = null,
): String? {
    val format = rememberCountFormat()
    return when (state) {
        RestoreState.Idle -> null
        is RestoreState.Reading -> {
            val progress = state.progress?.takeIf { it.filesTotal > 0 && it.filesDone > 0 }
            if (progress == null) {
                stringResource(R.string.settings_restore_reading)
            } else {
                pluralStringResource(R.plurals.settings_restore_progress, progress.filesTotal, format.format(progress.filesDone), format.format(progress.filesTotal))
            }
        }
        is RestoreState.Confirm -> confirmText(state.summary, wayBack)
        is RestoreState.Replacing ->
            when {
                !state.withLibrary -> stringResource(if (state.isUndo) R.string.settings_undo_running else R.string.settings_restore_running)
                state.isUndo -> stringResource(R.string.settings_undo_running_restart)
                else -> stringResource(R.string.settings_restore_running_restart)
            }
        is RestoreState.Refused -> stringResource(problemText(state.problem))
        is RestoreState.Done ->
            when {
                state.isUndo -> stringResource(R.string.settings_undo_done)
                state.kind == BackupKind.Everything -> stringResource(R.string.settings_restore_done_everything, dateText(state.fromMillis))
                else -> stringResource(R.string.settings_restore_done_settings, dateText(state.fromMillis))
            }
        is RestoreState.Failed ->
            when {
                !state.isPutBack -> stringResource(R.string.settings_restore_failed_not_put_back)
                state.isUndo -> stringResource(R.string.settings_undo_failed)
                else -> stringResource(R.string.settings_restore_failed)
            }
        is RestoreState.Interrupted -> stringResource(if (state.isFinished) R.string.settings_restore_interrupted_finished else R.string.settings_restore_interrupted_put_back)
    }
}

/** What the file holds, then what it replaces. */
@Composable
private fun confirmText(
    summary: RestoreSummary,
    wayBack: WayBackSet?,
): String {
    val format = rememberCountFormat()
    val date = dateText(summary.fromMillis)
    val withAccount = summary.account == AccountInFile.SignsIn
    val holds =
        if (summary.kind == BackupKind.Everything) {
            val games = pluralStringResource(R.plurals.settings_restore_games, summary.games, format.format(summary.games))
            val pictures = pluralStringResource(R.plurals.settings_restore_pictures, summary.pictures, format.format(summary.pictures))
            stringResource(if (withAccount) R.string.settings_restore_holds_everything_account else R.string.settings_restore_holds_everything, date, games, pictures)
        } else {
            stringResource(if (withAccount) R.string.settings_restore_holds_settings_account else R.string.settings_restore_holds_settings, date)
        }
    val replaces =
        when {
            summary.kind == BackupKind.Everything -> R.string.settings_restore_replaces_everything
            summary.account == AccountInFile.None -> R.string.settings_restore_replaces_settings
            else -> R.string.settings_restore_replaces_settings_account
        }
    val parts = mutableListOf(holds, stringResource(replaces))
    if (summary.account == AccountInFile.SignInAgain) parts += stringResource(R.string.settings_restore_sign_in_again)
    // The one kept set is replaced by this restore's (a Settings file over an Everything set is refused instead).
    if (wayBack != null) parts += stringResource(R.string.settings_restore_replaces_copy, dateText(wayBack.restoredAtMillis))
    return parts.joinToString(" ")
}

private fun problemText(problem: RestoreProblem): Int =
    when (problem) {
        RestoreProblem.NotABackup -> R.string.settings_restore_not_a_backup
        RestoreProblem.NewerFormat, RestoreProblem.NewerDatabase -> R.string.settings_restore_newer
        RestoreProblem.Torn -> R.string.settings_restore_torn
        RestoreProblem.BadDatabase -> R.string.settings_restore_bad_database
        RestoreProblem.BadSettings -> R.string.settings_restore_bad_settings
        RestoreProblem.BadAccount -> R.string.settings_restore_bad_account
        RestoreProblem.NoSpace -> R.string.settings_restore_no_space
        RestoreProblem.Unreadable -> R.string.settings_restore_unreadable
        RestoreProblem.BackupRunning -> R.string.settings_restore_backup_running
        RestoreProblem.BoxArtBusy -> R.string.settings_restore_box_art_busy
        RestoreProblem.ScanRunning -> R.string.settings_restore_scan_running
        RestoreProblem.EarlierRestoreKept -> R.string.settings_restore_earlier_kept
        RestoreProblem.LibraryBusy -> R.string.settings_restore_library_busy
    }

/** What Restore… does, the line, and the buttons, stacked like Back up…'s; it scrolls when the text is large. */
@Composable
internal fun RestorePanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val state = model.content.restore
    val inPanel = model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.Restore
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag(SettingsTags.RESTORE_PANEL),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap),
    ) {
        PanelTitle(stringResource(R.string.settings_restore_title))
        // The confirm says it all itself; otherwise what Restore does comes first.
        if (state !is RestoreState.Confirm) PanelBody(stringResource(R.string.settings_restore_body))
        PanelLine(model, SettingsOption.Restore, restoreLineText(state, model.content.wayBack))
        // Box art the file held damaged: one line under the result.
        if (state is RestoreState.Done && state.damagedMediaFiles > 0) {
            PanelBody(pluralStringResource(R.plurals.settings_restore_damaged_pictures, state.damagedMediaFiles, rememberCountFormat().format(state.damagedMediaFiles)))
        }
        PanelButtons(restorePanelItems(state).map { restoreItemText(it) to (it == RestorePanelItem.Restore) }, inPanel, model, touch)
    }
}

/** What Undo puts back and what Forget frees; the panel is the confirm. */
@Composable
internal fun UndoRestorePanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val content = model.content
    val state = content.restore
    val set = content.wayBack
    val inPanel = model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.UndoRestore
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag(SettingsTags.UNDO_PANEL),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap),
    ) {
        PanelTitle(stringResource(R.string.settings_undo_restore))
        val ask = model.focus.undoAsk.takeIf { model.focus.page == PanelPage.UndoRestore }
        if (set != null) {
            val date = dateText(set.restoredAtMillis)
            val size = Formatter.formatShortFileSize(LocalContext.current, set.bytes)
            when (ask) {
                UndoPanelItem.Undo -> PanelBody(stringResource(if (set.kind == BackupKind.Everything) R.string.settings_undo_ask_everything else R.string.settings_undo_ask_settings, date))
                UndoPanelItem.Forget -> PanelBody(stringResource(R.string.settings_undo_ask_forget, size))
                else -> {
                    PanelBody(stringResource(if (set.kind == BackupKind.Everything) R.string.settings_undo_body_everything else R.string.settings_undo_body_settings, date))
                    PanelBody(stringResource(R.string.settings_undo_forget_body, size))
                }
            }
        }
        val line =
            when {
                state is RestoreState.Replacing && state.isUndo -> restoreLineText(state)
                state is RestoreState.Failed && state.isUndo -> restoreLineText(state)
                state is RestoreState.Refused -> restoreLineText(state)
                else -> null
            }
        PanelLine(model, SettingsOption.UndoRestore, line)
        PanelButtons(undoPanelItems(content, ask).map { undoItemText(it, ask) to (it == UndoPanelItem.Confirm) }, inPanel, model, touch)
    }
}

/** Buttons one above the other, as wide as the widest (Back up…'s layout: two lines fit the 248 dp panel at the largest sizes). */
@Composable
private fun PanelButtons(
    items: List<Pair<Int, Boolean>>,
    inPanel: Boolean,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    if (items.isEmpty()) return
    Column(
        modifier = Modifier.ringRoomAboveAndBelow().width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
    ) {
        items.forEachIndexed { index, (text, isPrimary) ->
            val isFocused = inPanel && model.focus.panelIndex == index
            SetupButton(
                text = stringResource(text),
                isPrimary = isPrimary,
                isFocused = isFocused,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset).tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
            )
        }
    }
}

private fun restoreItemText(item: RestorePanelItem): Int =
    when (item) {
        RestorePanelItem.ChooseFile -> R.string.settings_restore_choose
        RestorePanelItem.Cancel -> R.string.settings_cancel
        RestorePanelItem.Restore -> R.string.settings_restore_confirm
    }

private fun undoItemText(
    item: UndoPanelItem,
    ask: UndoPanelItem?,
): Int =
    when (item) {
        UndoPanelItem.Undo -> R.string.settings_undo_confirm
        UndoPanelItem.Forget -> R.string.settings_undo_forget
        UndoPanelItem.Confirm -> if (ask == UndoPanelItem.Forget) R.string.settings_undo_delete else R.string.settings_undo_do
        UndoPanelItem.Cancel -> R.string.settings_cancel
    }
