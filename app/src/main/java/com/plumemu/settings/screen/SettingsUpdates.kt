package com.plumemu.settings.screen

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreui.components.ListEdgeFade
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.platforms.PlatformDataVersion
import com.plumemu.settings.versionLine
import com.plumemu.setup.SetupButton
import com.plumemu.setup.SetupProgressBar
import com.plumemu.setup.bringIntoViewWhen
import com.plumemu.update.CheckFailure
import com.plumemu.update.CheckOutcome
import com.plumemu.update.UpdateFailure
import com.plumemu.update.UpdateManifest
import com.plumemu.update.UpdateStep
import com.plumemu.update.UpdateView
import com.plumemu.update.ui.ReleaseNotesTags
import com.plumemu.update.ui.ScrollingReleaseNotes
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * Settings › About › Updates: the row (the update found, or the last check) and its panel: the status
 * line; while an update is found its notes, size, Update and the steps after it; Check now; and the
 * daily switch with what it sends. The work is the graph's (`Updates`); this file only shows it.
 */

/** What Settings asks of the updates. */
internal interface SettingsUpdateActions {
    /** Check now: now, whatever the switch and the last check say. */
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

/** What the Updates panel can focus, top to bottom: the update's own buttons, Check now, the daily switch. */
internal enum class UpdatePanelItem { Update, DownloadAnyway, AllowInstalls, Cancel, CheckNow, CheckDaily }

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

/** The update's buttons for the panel: none with nothing found. */
internal fun updateStepItems(view: UpdateView): List<UpdatePanelItem> = if (view.found == null) emptyList() else updatePanelItems(view)

/** Everything the panel can focus: Check now stays through an update, whose status then says why it does nothing. */
internal fun updatesPanelItems(view: UpdateView): List<UpdatePanelItem> = updateStepItems(view) + UpdatePanelItem.CheckNow + UpdatePanelItem.CheckDaily

/** The row's value: the update found, else the last check; "»" unless an update runs. */
@Composable
internal fun updatesRowValue(view: UpdateView): OptionValue =
    if (view.found != null) opensUnlessBusy(updateRowValue(view), view.isUpdateRunning) else OptionValue.Opens(checkRowValue(view))

/** The value while an update is found: "0.1.3 available" (never the bare number, which reads as the installed one), or a word while an update runs. */
@Composable
internal fun updateRowValue(view: UpdateView): String {
    val name = stringResource(R.string.settings_update_value_available, view.found?.versionName.orEmpty())
    return when (view.step) {
        is UpdateStep.Downloading, UpdateStep.Verifying -> stringResource(R.string.settings_update_value_downloading)
        is UpdateStep.Installing -> if (view.step.askedPerson) name else stringResource(R.string.settings_update_value_installing)
        UpdateStep.ReadyToInstall -> stringResource(R.string.settings_update_value_installing)
        else -> name
    }
}

/** The value with no update found: "Checking…", or the last result and when ("Up to date · 10:15 AM"), or nothing before the first check. */
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

/** "Version 6 · Nov 1, 2026": the platform data's number and its date in the device's format. */
@Composable
internal fun platformDataRowValue(version: PlatformDataVersion): String = stringResource(R.string.settings_platform_data_value, version.dataVersion, releaseDateText(version.date))

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

/** The panel's sentence for the last check, "Checking…", or why no check runs during an update. */
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

/**
 * The panel, one scrolling column. While an update is found: "Plumemu installed → offered", the date and size, the notes (grouped
 * under New, Improved, Fixed and Known issues when the manifest has them, else bullets), the status line (with a bar while
 * downloading) and the update's buttons (stacked, as wide as the widest, like Back up…'s) fill the first screen, so the buttons
 * stay in view while only the notes scroll, by touch or by Up and Down past the first and last button
 * ([SettingsPanelModel.updateNotes]); the last check and Check now follow below it. With nothing found: what Check now asks and of whom, the last
 * check and the button. Under either, the daily switch and what it sends: the IP address it shows is the person's choice to
 * make, so it stands in the panel.
 */
@Composable
internal fun UpdatePanel(
    model: SettingsModel,
    touch: SettingsTouch,
    settings: PlumSettings,
    installedLine: String,
) {
    val view = model.content.update
    val found = view.found
    val items = updatesPanelItems(view)
    val inPanel = model.focus.column == SettingsColumn.Panel && model.focus.page == PanelPage.Update
    val focused = if (inPanel) items.getOrNull(model.focus.panelIndex) else null
    val scroll = rememberScrollState()
    val gap = PlumDimens.settingsPanelGap
    BoxWithConstraints(Modifier.fillMaxHeight().testTag(SettingsTags.UPDATE_PANEL)) {
        // The bottom fade says there is more; it never lies over the update's buttons.
        val firstScreen = maxHeight - PlumDimens.listFadeBand
        var frameHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
        // Back on the update, the whole of it shows again; a frame taller than the panel keeps each button in view by itself.
        val frameFits = found != null && frameHeight <= constraints.maxHeight
        val onUpdate = focused != null && focused != UpdatePanelItem.CheckNow && focused != UpdatePanelItem.CheckDaily
        LaunchedEffect(onUpdate, frameFits) { if (onUpdate && frameFits) scroll.scrollTo(0) }
        val buttons =
            @Composable { shown: (UpdatePanelItem) -> Boolean ->
                UpdateButtons(view, items, shown, focused, keepsInView = !frameFits, model, touch)
            }
        Column(Modifier.verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(gap)) {
            if (found != null) {
                NotesFrame(
                    height = firstScreen,
                    modifier = Modifier.onSizeChanged { frameHeight = it.height },
                    top = {
                        val (from, to) = titleVersions(installedLine, found)
                        PanelTitleOr(stringResource(R.string.settings_update_title, from, to), stringResource(R.string.settings_update_title_short, from, to))
                        PanelFacts(stringResource(R.string.settings_update_facts, releaseDateText(found.released), Formatter.formatShortFileSize(LocalContext.current, found.size)))
                    },
                    notes = { UpdateNotes(found, model, Modifier) },
                    bottom = {
                        UpdateStatus(view, isOnCheckNow = focused == UpdatePanelItem.CheckNow)
                        if (updateStepItems(view).isNotEmpty()) buttons { it != UpdatePanelItem.CheckNow }
                    },
                )
                LastCheckUnderUpdate(view)
                buttons { it == UpdatePanelItem.CheckNow }
            } else {
                PanelTitle(stringResource(R.string.settings_updates))
                PanelBody(stringResource(R.string.settings_check_updates_body))
                UpdateStatus(view, isOnCheckNow = false)
                buttons { true }
            }
            DailySwitch(settings, isFocused = focused == UpdatePanelItem.CheckDaily, items.indexOf(UpdatePanelItem.CheckDaily), touch)
        }
        if (scroll.canScrollBackward) ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter))
        if (scroll.canScrollForward) ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).testTag(SettingsTags.UPDATE_PANEL + FADE_BOTTOM_SUFFIX))
    }
}

/**
 * What the update needs, in [height]: [top], then [notes] in what is left, then [bottom], all of which stay whole. At a size where
 * nothing is left the notes give way and the buttons stay; short notes end the frame sooner.
 */
@Composable
private fun NotesFrame(
    height: Dp,
    modifier: Modifier,
    top: @Composable () -> Unit,
    notes: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    val gap = PlumDimens.settingsPanelGap
    Layout(
        {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) { top() }
            notes()
            Column(verticalArrangement = Arrangement.spacedBy(gap)) { bottom() }
        },
        modifier,
    ) { (topPart, notesPart, bottomPart), constraints ->
        val free = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        val above = topPart.measure(free)
        val below = bottomPart.measure(free)
        val gapPx = gap.roundToPx()
        val room = (height.roundToPx() - above.height - below.height - 2 * gapPx).coerceAtLeast(0)
        val text = notesPart.measure(constraints.copy(minHeight = 0, maxHeight = room))
        layout(constraints.maxWidth, above.height + text.height + below.height + 2 * gapPx) {
            above.place(0, 0)
            text.place(0, above.height + gapPx)
            below.place(0, above.height + text.height + 2 * gapPx)
        }
    }
}

/** The status line: where the update is (with a bar while downloading), else the last check; on Check now during a download, why it checks nothing. */
@Composable
private fun UpdateStatus(
    view: UpdateView,
    isOnCheckNow: Boolean,
) {
    val step = view.step
    val hasUpdate = view.found != null
    // With no button of its own the step keeps the line: the cursor is on Check now only because nothing else is left.
    val saysWhyNoCheck = view.isUpdateRunning && isOnCheckNow && updateStepItems(view).isNotEmpty()
    val stepLine = if (!hasUpdate || saysWhyNoCheck) null else updateStepText(view)
    // A finished check's answer stands under the update when one is found: it must not take the notes' room.
    val line = stepLine ?: (if (hasUpdate && isCheckAnswer(view)) null else checkLineText(view)) ?: return
    Column(verticalArrangement = Arrangement.spacedBy(PlumDimens.titleLineGap)) {
        if (hasUpdate && step is UpdateStep.Downloading) {
            SetupProgressBar(if (step.total > 0) step.done.toFloat() / step.total else 0f, Modifier.onRowTextLine().testTag(SettingsTags.UPDATE_PROGRESS))
        }
        BasicText(line, style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text), modifier = Modifier.onRowTextLine().testTag(SettingsPanelTags.STATUS))
    }
}

/** The last check holds a finished answer: no check runs and no update runs. */
private fun isCheckAnswer(view: UpdateView): Boolean = !view.isChecking && !view.isUpdateRunning

/**
 * With an update found: the last check's answer, between the update and Check now, the button it answers. Under a step left standing it is the [SettingsPanelTags.UPDATE_LAST_CHECK]
 * line; alone it is the status line. "Update found" would repeat the title: then only when it ran, or Check now would answer with nothing.
 */
@Composable
private fun LastCheckUnderUpdate(view: UpdateView) {
    if (!isCheckAnswer(view)) return
    val last = view.lastCheck
    val line = (if (last?.outcome == CheckOutcome.Found) stringResource(R.string.settings_check_updates_last, checkTime(last.at)) else checkLineText(view)) ?: return
    val tag = if (updateStepText(view) != null) SettingsPanelTags.UPDATE_LAST_CHECK else SettingsPanelTags.STATUS
    BasicText(line, style = PlumTheme.type.panelBody.copy(color = PlumTheme.colors.text), modifier = Modifier.onRowTextLine().testTag(tag))
}

/** Those of the panel's buttons that are [shown]; each keeps its index in [items]. */
@Composable
private fun UpdateButtons(
    view: UpdateView,
    items: List<UpdatePanelItem>,
    shown: (UpdatePanelItem) -> Boolean,
    focused: UpdatePanelItem?,
    keepsInView: Boolean,
    model: SettingsModel,
    touch: SettingsTouch,
) {
    Column(
        modifier = Modifier.ringRoomAboveAndBelow().width(IntrinsicSize.Max),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
    ) {
        items.forEachIndexed { index, item ->
            if (item == UpdatePanelItem.CheckDaily || !shown(item)) return@forEachIndexed
            val isFocused = item == focused && model.focus.panelIndex == index
            SetupButton(
                text = stringResource(updateItemText(item)),
                isPrimary = item != UpdatePanelItem.Cancel && (item != UpdatePanelItem.CheckNow || view.found == null),
                isFocused = isFocused,
                maxLines = 2,
                modifier =
                    Modifier.fillMaxWidth()
                        .bringIntoViewWhen(isFocused && (keepsInView || item == UpdatePanelItem.CheckNow), ring = PlumDimens.focusRingOutset, below = PlumDimens.listFadeBand)
                        .tapTarget { touch.onPanelItem(index) }
                        .testTag(SettingsTags.panelItem(index)),
            )
        }
    }
}

/** Check for updates daily, as the row it is elsewhere, and what it sends; focused, both are brought into view. */
@Composable
private fun DailySwitch(
    settings: PlumSettings,
    isFocused: Boolean,
    index: Int,
    touch: SettingsTouch,
) {
    // The narrow column beside the options cannot hold the long label and the switch on one line.
    val stacked = PlumTheme.layout.widthClass == WidthClass.Wide
    Column(Modifier.bringIntoViewWhen(isFocused), verticalArrangement = Arrangement.spacedBy(PlumDimens.settingsPanelListGap)) {
        OptionRow(
            option = SettingsOption.CheckUpdatesDaily,
            value = OptionValue.Switch(SettingsOption.CheckUpdatesDaily.switchValue(settings)),
            look = if (isFocused) OptionLook.Focused else OptionLook.Normal,
            modifier = Modifier.tapTarget { touch.onPanelItem(index) }.testTag(SettingsTags.panelItem(index)),
            verticalPadding = if (stacked) PlumDimens.settingsTwoLineRowPadding else 0.dp,
            valueBelow = stacked,
        )
        PanelBody(stringResource(R.string.settings_check_updates_daily_body))
    }
}

/**
 * The title's two versions: names only ("0.1.0 → 0.1.1"); when both names read the same (a test build
 * of the same name) the build numbers too ("0.1.1 (412) → 0.1.1 (950)"). [installedLine] is Version's "name (build)".
 */
internal fun titleVersions(
    installedLine: String,
    found: UpdateManifest,
): Pair<String, String> {
    val installedName = installedLine.substringBeforeLast(" (")
    return if (installedName == found.versionName) installedLine to versionLine(found.versionName, found.versionCode) else installedName to found.versionName
}

/** The release notes: grouped under small headings, or (an old manifest) bullets alone; the shared [ScrollingReleaseNotes]. */
@Composable
private fun UpdateNotes(
    found: UpdateManifest,
    model: SettingsModel,
    modifier: Modifier,
) {
    ScrollingReleaseNotes(found, model.updateNotes, UPDATE_NOTES_TAGS, modifier, Modifier.onRowTextLine())
}

private val UPDATE_NOTES_TAGS =
    ReleaseNotesTags(SettingsTags.UPDATE_NOTES, SettingsTags.UPDATE_HEADING, SettingsTags.UPDATE_NOTE, SettingsTags.UPDATE_DOT)

private fun updateItemText(item: UpdatePanelItem): Int =
    when (item) {
        UpdatePanelItem.Update -> R.string.settings_update_button
        UpdatePanelItem.DownloadAnyway -> R.string.settings_update_download_anyway
        UpdatePanelItem.AllowInstalls -> R.string.settings_update_allow
        UpdatePanelItem.Cancel -> R.string.settings_cancel
        UpdatePanelItem.CheckNow -> R.string.settings_updates_check_now
        UpdatePanelItem.CheckDaily -> R.string.settings_check_updates_daily
    }
