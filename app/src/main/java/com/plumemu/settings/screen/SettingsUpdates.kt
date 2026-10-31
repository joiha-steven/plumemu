package com.plumemu.settings.screen

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.settings.versionLine
import com.plumemu.R
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.setup.SetupButton
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import com.plumemu.update.UpdateFailure
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * Settings › About's update rows: "Update
 * available" with its panel (notes, size, Update and the steps after it), "Check for updates" with
 * the last check, and the daily switch (its explanation is an ExplainPanel). The work is the graph's
 * (`Updates`); this file only shows it.
 */

/** What Settings asks of the updates. */
internal interface SettingsUpdateActions {
    /** Check for updates: now, whatever the switch and the last check say. */
    fun checkNow()

    /** Update (or, on mobile data, the yes to downloading there). */
    fun startUpdate(allowMetered: Boolean)

    /** Stops a download; drops a question or a wait for the permission. */
    fun cancel()

    /** Android's Install unknown apps screen for Plumemu; false when there is none. */
    fun openInstallPermission(): Boolean

    object None : SettingsUpdateActions {
        override fun checkNow() = Unit

        override fun startUpdate(allowMetered: Boolean) = Unit

        override fun cancel() = Unit

        override fun openInstallPermission(): Boolean = false
    }
}

/**
 * "Update available" comes (a check found one) and goes (installed) above the other About rows: the
 * cursor stays on the row it was on, so A after Check for updates checks again rather than opening
 * the update. If its own row went away, its panel closes. Null when nothing moves.
 */
internal fun aboutFocusKept(
    focus: SettingsFocus,
    before: SettingsContent,
    after: SettingsContent,
): SettingsFocus? {
    val about = SettingsSection.About
    if (focus.section != about || focus.column == SettingsColumn.Sections) return null
    val rowsBefore = about.rowsIn(before)
    val rowsAfter = about.rowsIn(after)
    if (rowsBefore == rowsAfter) return null
    val at = focus.rememberedOption[about] ?: return null
    val option = rowsBefore.getOrNull(at) ?: return null
    val now = rowsAfter.indexOf(option)
    if (now < 0) return focus.copy(column = SettingsColumn.Options, page = null, panelIndex = 0, rememberedOption = focus.rememberedOption + (about to at.coerceAtMost(rowsAfter.lastIndex)))
    return if (now == at) null else focus.copy(rememberedOption = focus.rememberedOption + (about to now))
}

/** The update panel's buttons, top to bottom. */
internal enum class UpdatePanelItem { Update, DownloadAnyway, AllowInstalls, Cancel }

/**
 * The buttons for where the update is: none while a check runs (the line says "Checking for
 * updates…"; Update would find the release changing under it) and none while the file is checked
 * or handed to Android (nothing to stop then). Once Android's screen was shown, Update again:
 * Android 6 sends no answer when its "Install blocked" dialog is cancelled, and the person must never
 * be stuck on "Installing…".
 */
internal fun updatePanelItems(view: UpdateView): List<UpdatePanelItem> =
    if (view.isChecking && !view.isUpdateRunning) {
        emptyList()
    } else {
        stepItems(view.step)
    }

private fun stepItems(step: UpdateStep): List<UpdatePanelItem> =
    when (step) {
        UpdateStep.Idle, UpdateStep.Cancelled, UpdateStep.InstallAborted, is UpdateStep.Failed -> listOf(UpdatePanelItem.Update)
        is UpdateStep.Installing -> if (step.askedPerson) listOf(UpdatePanelItem.Update) else emptyList()
        UpdateStep.AskMetered -> listOf(UpdatePanelItem.DownloadAnyway, UpdatePanelItem.Cancel)
        UpdateStep.NeedsPermission -> listOf(UpdatePanelItem.AllowInstalls, UpdatePanelItem.Cancel)
        UpdateStep.NoPermissionScreen -> listOf(UpdatePanelItem.Cancel)
        is UpdateStep.Downloading -> listOf(UpdatePanelItem.Cancel)
        UpdateStep.Verifying, UpdateStep.ReadyToInstall -> emptyList()
    }

/** Update available's value: the version, or a word while an update runs. */
@Composable
internal fun updateRowValue(view: UpdateView): String {
    val name = view.found?.let { versionLine(it.versionName, it.versionCode) }.orEmpty()
    return when (view.step) {
        is UpdateStep.Downloading, UpdateStep.Verifying -> stringResource(R.string.settings_update_value_downloading)
        is UpdateStep.Installing -> if (view.step.askedPerson) stringResource(R.string.settings_value_opens, name) else stringResource(R.string.settings_update_value_installing)
        UpdateStep.ReadyToInstall -> stringResource(R.string.settings_update_value_installing)
        else -> stringResource(R.string.settings_value_opens, name)
    }
}

/** Check for updates' value: "Checking…", or the last result and when ("Up to date · 10:15 AM"), or nothing before the first check. */
@Composable
internal fun checkRowValue(view: UpdateView): String {
    if (view.isUpdateRunning) return stringResource(R.string.settings_check_updates_value_busy)
    if (view.isChecking) return stringResource(R.string.settings_check_updates_checking)
    val last = view.lastCheck ?: return ""
    val word =
        when (last.outcome) {
            CheckOutcome.UpToDate -> R.string.settings_check_updates_value_up_to_date
            CheckOutcome.Found -> R.string.settings_check_updates_value_found
            CheckOutcome.NeedsNewerAndroid -> R.string.settings_check_updates_value_needs_android
            is CheckOutcome.Failed -> R.string.settings_check_updates_value_failed
        }
    return stringResource(R.string.settings_check_updates_value, stringResource(word), checkTime(last.at))
}

/** A time today ("10:15 AM"), else a date ("Oct 22, 2026"), in the device's formats. */
@Composable
private fun checkTime(at: Long): String = DateUtils.formatSameDayTime(at, System.currentTimeMillis(), DateFormat.MEDIUM, DateFormat.SHORT).toString()

/**
 * The manifest's release day ("YYYY-MM-DD", perhaps with a time after it) written as [checkTime]
 * writes a date ("Oct 23, 2026"); the text as given when it does not start with a date.
 */
internal fun releaseDateText(released: String): String {
    val day =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(released.take(ISO_DAY_LENGTH), ParsePosition(0))
            ?: return released
    return DateFormat.getDateInstance(DateFormat.MEDIUM).format(day)
}

private const val ISO_DAY_LENGTH = 10

/** The check panel's sentence for the last check, "Checking…", or why no check runs during an update. */
@Composable
internal fun checkLineText(view: UpdateView): String? {
    if (view.isUpdateRunning) return stringResource(R.string.settings_check_updates_busy)
    if (view.isChecking) return stringResource(R.string.settings_check_updates_checking)
    val last = view.lastCheck ?: return null
    val sentence =
        when (val outcome = last.outcome) {
            CheckOutcome.UpToDate -> stringResource(R.string.settings_check_updates_up_to_date)
            CheckOutcome.Found -> stringResource(R.string.settings_check_updates_found, view.found?.let { versionLine(it.versionName, it.versionCode) }.orEmpty())
            CheckOutcome.NeedsNewerAndroid -> stringResource(R.string.settings_check_updates_needs_android)
            is CheckOutcome.Failed ->
                stringResource(
                    when (outcome.failure) {
                        CheckFailure.Offline -> R.string.settings_check_updates_offline
                        CheckFailure.Timeout -> R.string.settings_check_updates_timeout
                        CheckFailure.Http -> R.string.settings_check_updates_http
                        CheckFailure.BadManifest -> R.string.settings_check_updates_bad_manifest
                        CheckFailure.Unexpected -> R.string.settings_check_updates_unexpected
                    },
                )
        }
    return stringResource(R.string.settings_check_updates_line, checkTime(last.at), sentence)
}

/** The update panel's status line for where the update is; null when there is nothing to say. */
@Composable
internal fun updateStepText(view: UpdateView): String? {
    val context = LocalContext.current
    if (view.isChecking && !view.isUpdateRunning) return stringResource(R.string.settings_update_checking)
    return when (val step = view.step) {
        UpdateStep.Idle -> null
        UpdateStep.AskMetered -> stringResource(R.string.settings_update_metered, Formatter.formatShortFileSize(context, view.found?.size ?: 0))
        is UpdateStep.Downloading ->
            stringResource(R.string.settings_update_downloading, Formatter.formatShortFileSize(context, step.done), Formatter.formatShortFileSize(context, step.total))
        UpdateStep.Verifying -> stringResource(R.string.settings_update_verifying)
        UpdateStep.NeedsPermission -> stringResource(R.string.settings_update_needs_permission)
        UpdateStep.NoPermissionScreen -> stringResource(R.string.settings_update_no_permission_screen)
        UpdateStep.ReadyToInstall -> stringResource(R.string.settings_update_ready)
        is UpdateStep.Installing -> stringResource(if (step.askedPerson) R.string.settings_update_confirm else R.string.settings_update_installing)
        UpdateStep.Cancelled -> stringResource(R.string.settings_update_cancelled)
        UpdateStep.InstallAborted -> stringResource(R.string.settings_update_aborted)
        is UpdateStep.Failed -> stringResource(failureText(step.failure))
    }
}

private fun failureText(failure: UpdateFailure): Int =
    when (failure) {
        UpdateFailure.Offline -> R.string.settings_update_failed_offline
        UpdateFailure.Timeout -> R.string.settings_update_failed_timeout
        UpdateFailure.Http -> R.string.settings_update_failed_http
        UpdateFailure.Storage -> R.string.settings_update_failed_storage
        UpdateFailure.SizeMismatch -> R.string.settings_update_failed_size
        UpdateFailure.HashMismatch -> R.string.settings_update_failed_hash
        UpdateFailure.NotAnApk -> R.string.settings_update_failed_not_apk
        UpdateFailure.WrongPackage -> R.string.settings_update_failed_package
        UpdateFailure.WrongVersion -> R.string.settings_update_failed_version
        UpdateFailure.WrongSigner -> R.string.settings_update_failed_signer
        UpdateFailure.InstallFailed -> R.string.settings_update_failed_install
    }

/** Check for updates' panel: what it asks and of whom, then the last check. */
@Composable
internal fun CheckUpdatesPanel(model: SettingsModel) {
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        PanelTitle(stringResource(R.string.settings_check_updates))
        PanelBody(stringResource(R.string.settings_check_updates_body))
        PanelLine(model, SettingsOption.CheckForUpdates, checkLineText(model.content.update))
    }
}

/**
 * Update available's panel: the version, its date and size, the notes, the status line, then the
 * buttons (stacked, as wide as the widest, like Back up…'s). It scrolls when the text is large,
 * keeping the focused button in view.
 */
@Composable
internal fun UpdatePanel(
    model: SettingsModel,
    touch: SettingsTouch,
) {
    val view = model.content.update
    val found = view.found ?: return
    val context = LocalContext.current
    val inPanel = model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.Update
    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag(SettingsTags.UPDATE_PANEL),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelGap),
    ) {
        PanelTitle(stringResource(R.string.settings_update_title, versionLine(found.versionName, found.versionCode)))
        PanelFact(stringResource(R.string.settings_update_facts, releaseDateText(found.released), Formatter.formatShortFileSize(context, found.size)))
        // No bullets: each note is a paragraph of its own, the panel's gap apart.
        for (note in found.notes) PanelBody(note)
        updateStepText(view)?.let { line ->
            BasicText(line, style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text), modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.STATUS))
        }
        val items = updatePanelItems(view)
        if (items.isNotEmpty()) {
            Column(
                modifier = Modifier.ringRoomAboveAndBelow().width(IntrinsicSize.Max),
                verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
            ) {
                items.forEachIndexed { index, item ->
                    val isFocused = inPanel && model.focus.panelIndex == index
                    SetupButton(
                        text = stringResource(updateItemText(item)),
                        isPrimary = item != UpdatePanelItem.Cancel,
                        isFocused = isFocused,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth().bringIntoViewWhen(isFocused, ring = PlumDimens.focusRingOutset).tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
                    )
                }
            }
        }
    }
}

private fun updateItemText(item: UpdatePanelItem): Int =
    when (item) {
        UpdatePanelItem.Update -> R.string.settings_update_button
        UpdatePanelItem.DownloadAnyway -> R.string.settings_update_download_anyway
        UpdatePanelItem.AllowInstalls -> R.string.settings_update_allow
        UpdatePanelItem.Cancel -> R.string.settings_cancel
    }
