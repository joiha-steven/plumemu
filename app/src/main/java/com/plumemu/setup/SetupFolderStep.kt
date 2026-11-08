package com.plumemu.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.rememberCountFormat
import kotlin.math.roundToInt

/**
 * The folder step (the board's RP5-O0 before a folder is chosen, RP5-O1 and Nova-7 after): three
 * plain lines and Choose folder; then each chosen folder as a quiet filled card with what the scan
 * found in it, "+ Add another folder" and Continue.
 */
@Composable
internal fun ColumnScope.FolderStepContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    if (state.folders.isEmpty()) ChooseFolderContent(state, onAction) else ChosenFoldersContent(state, onAction)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ChooseFolderContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    listOf(R.string.setup_folder_point_opens, R.string.setup_folder_point_use, R.string.setup_folder_point_private).forEach { point ->
        SetupPoint(stringResource(point))
    }
    // Side by side when the column is wide enough, one under the other when not (then the column scrolls).
    FlowRow(
        modifier = Modifier.padding(top = PlumDimens.setupChooseButtonSpacer),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonSpacer),
    ) {
        SetupButton(
            text = stringResource(R.string.setup_choose_folder),
            isPrimary = true,
            isFocused = state.focus == SetupControl.ChooseFolder,
            modifier = Modifier.setupControl(state, SetupControl.ChooseFolder, onAction),
        )
        // Quiet: the usual way on is a folder; this one leaves setup with none (the empty home asks for it again).
        SetupButton(
            text = stringResource(R.string.setup_set_up_later),
            isPrimary = false,
            isFocused = state.focus == SetupControl.SetUpLater,
            modifier = Modifier.setupControl(state, SetupControl.SetUpLater, onAction),
        )
    }
    StepNote(state.message, R.string.setup_folder_sd_hint)
}

@Composable
private fun ColumnScope.ChosenFoldersContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    state.folders.forEachIndexed { index, folder ->
        // A or a tap on a card removes the folder; past the eighth card Settings › Library does it.
        val control = SetupControl.folderCard(index)
        val isFocused = control != null && state.focus == control
        SetupChoice(
            label = folderLabel(folder),
            sub = if (isFocused) stringResource(R.string.setup_folder_remove) else folderSummary(folder, state.isScanning),
            look = if (isFocused) ChoiceLook.Focused else ChoiceLook.Chosen,
            modifier = Modifier.testTag(SetupTags.FOLDER_CARD).then(if (control != null) Modifier.setupControl(state, control, onAction) else Modifier),
        )
    }
    SetupChoice(
        label = stringResource(R.string.setup_add_folder),
        sub = stringResource(R.string.setup_add_folder_sub),
        look = if (state.focus == SetupControl.AddFolder) ChoiceLook.Focused else ChoiceLook.Plain,
        modifier = Modifier.setupControl(state, SetupControl.AddFolder, onAction),
    )
    // The content gap plus the spacer above it, so the collar keeps its distance from the card.
    SetupButton(
        text = stringResource(R.string.setup_continue),
        isPrimary = true,
        isFocused = state.focus == SetupControl.Continue,
        modifier = Modifier.padding(top = PlumDimens.setupButtonSpacer).setupControl(state, SetupControl.Continue, onAction),
    )
    StepNote(state.message, R.string.setup_folder_note)
}

/** A bullet on the middle of the text's first line (a wrapped line leaves it there), the text centred in a minimum height. */
@Composable
private fun SetupPoint(text: String) {
    val colors = PlumTheme.colors
    val minHeight = PlumTheme.layout.setupPointHeight
    var firstLineCentre by remember { mutableFloatStateOf(0f) }
    Layout(
        content = {
            Box(Modifier.size(PlumDimens.focusDot).background(colors.muted, CircleShape).testTag(SetupTags.POINT_DOT))
            BasicText(
                text = text,
                style = PlumTheme.type.setupPoint.copy(color = colors.text2),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { firstLineCentre = (it.getLineTop(0) + it.getLineBottom(0)) / 2f },
                modifier = Modifier.testTag(SetupTags.POINT_TEXT),
            )
        },
    ) { measurables, constraints ->
        val gap = PlumDimens.setupPointGap.roundToPx()
        val dot = measurables[0].measure(Constraints())
        val label = measurables[1].measure(Constraints(maxWidth = (constraints.maxWidth - dot.width - gap).coerceAtLeast(0)))
        val height = maxOf(minHeight.roundToPx(), label.height)
        layout(dot.width + gap + label.width, height) {
            val labelTop = (height - label.height) / 2
            label.placeRelative(dot.width + gap, labelTop)
            dot.placeRelative(0, labelTop + (firstLineCentre - dot.height / 2f).roundToInt())
        }
    }
}

/** The small line under the step; a message takes its place, in `text` so it reads as news. */
@Composable
internal fun StepNote(
    message: SetupMessage?,
    note: Int,
) {
    val colors = PlumTheme.colors
    val text = if (message != null) stringResource(messageText(message)) else stringResource(note)
    BasicText(
        text = text,
        style = PlumTheme.type.setupNote.copy(color = if (message != null) colors.text else colors.muted),
        modifier =
            Modifier
                .padding(top = PlumDimens.setupNoteTopPadding)
                .testTag(if (message != null) SetupTags.MESSAGE else SetupTags.NOTE),
    )
}

private fun messageText(message: SetupMessage): Int =
    when (message) {
        SetupMessage.FolderNotKept -> R.string.setup_message_not_kept
        SetupMessage.FolderCouldNotOpen -> R.string.setup_message_could_not_open
        SetupMessage.FolderAlreadyAdded -> R.string.setup_message_already_added
        SetupMessage.NoFolderPicker -> R.string.setup_message_no_picker
        SetupMessage.SaveFailed -> R.string.setup_message_save_failed
        SetupMessage.ScanFailed -> R.string.setup_message_scan_failed
        SetupMessage.FolderCouldNotRemove -> R.string.setup_message_could_not_remove
    }

/** "Internal storage › ROMs", "SD card › Games", or the stored name for another app's storage. */
@Composable
internal fun folderLabel(folder: SetupFolder): String {
    val separator = stringResource(R.string.setup_folder_path_separator)
    return when (val place = folderPlaceOf(folder.treeUri)) {
        is FolderPlace.Internal -> (listOf(stringResource(R.string.setup_folder_internal)) + place.path).joinToString(separator)
        is FolderPlace.Removable -> (listOf(stringResource(R.string.setup_folder_removable)) + place.path).joinToString(separator)
        FolderPlace.Other -> folder.displayName.ifBlank { stringResource(R.string.setup_folder_unnamed) }
    }
}

/**
 * What the scan found so far: counting (only while a scan runs), not read yet, "1,031 games ·
 * 14 systems", none, or unreadable.
 */
@Composable
private fun folderSummary(
    folder: SetupFolder,
    isScanning: Boolean,
): String =
    when {
        folder.isRemoving -> stringResource(R.string.setup_folder_removing)
        folder.isUnavailable -> stringResource(R.string.setup_folder_unavailable)
        folder.gameCount == 0 && !folder.isCounted && isScanning -> stringResource(R.string.setup_folder_counting)
        folder.gameCount == 0 && !folder.isCounted -> stringResource(R.string.setup_folder_not_counted)
        folder.gameCount == 0 -> stringResource(R.string.setup_folder_no_games)
        else -> {
            val format = rememberCountFormat()
            stringResource(
                R.string.setup_folder_counts,
                pluralStringResource(R.plurals.home_games_count, folder.gameCount, format.format(folder.gameCount)),
                pluralStringResource(R.plurals.home_systems_count, folder.systemCount, format.format(folder.systemCount)),
            )
        }
    }
