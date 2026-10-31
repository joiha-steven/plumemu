package com.plumemu.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.ScanPulseLine
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.graph.ScanPhase
import com.plumemu.home.rememberCountFormat

/**
 * The scan and the library ready (the board's RP5-O4 and Nova-9): games and systems found, the
 * four biggest systems (wide screens only), then, once the scan has ended, emulators picked, the
 * usage statistics switch and Start playing; after a sign-in on the box art step, a line that box
 * art is downloading. While the scan runs: a bar and its line instead, the line led by a breathing dot (`ScanPulse`).
 */
@Composable
internal fun ColumnScope.LibraryStepContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    if (state.hasScanFailed) {
        Box(Modifier.padding(top = PlumDimens.setupButtonSpacer)) {
            SetupButton(
                text = stringResource(R.string.setup_try_again),
                isPrimary = true,
                isFocused = state.focus == SetupControl.TryAgain,
                modifier = Modifier.setupControl(state, SetupControl.TryAgain, onAction),
            )
        }
        return
    }
    // Set up later: no folder, so no counts to read.
    if (state.folders.isNotEmpty()) LibraryCounts(state)
    if (state.step == SetupStep.Scan) {
        val counted = state.folders.count { it.isCounted }
        // No total is known before the walk ends; folders done is the only honest fraction.
        val walkFraction = if (state.folders.isEmpty()) 0f else maxOf(counted.toFloat() / state.folders.size, MIN_PROGRESS)
        // Naming starts only after every walk ended, so the bar restarts for it: it shows the phase it is in.
        val progress = state.scanProgress
        val isNaming = progress.phase == ScanPhase.Naming
        val fraction = if (isNaming) maxOf(progress.namedFraction ?: 0f, MIN_PROGRESS) else walkFraction
        val format = rememberCountFormat()
        SetupProgressBar(fraction, Modifier.testTag(SetupTags.PROGRESS))
        ScanPulseLine(
            text =
                when {
                    !isNaming -> stringResource(R.string.setup_scan_running)
                    progress.gamesToName > 0 ->
                        stringResource(R.string.setup_scan_naming, format.format(progress.gamesNamed), format.format(progress.gamesToName))
                    else -> stringResource(R.string.setup_scan_naming_start)
                },
            style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.muted),
            dotModifier = Modifier.testTag(SetupTags.SCAN_PULSE),
            textModifier = Modifier.testTag(SetupTags.SCAN_LINE),
        )
    }
    // Not on the Ready step: its rows (emulators, statistics, one hand) and Start playing need the room at 768 x 432.
    if (PlumTheme.layout.widthClass == WidthClass.Wide && state.systems.isNotEmpty() && state.step != SetupStep.Ready) BiggestSystems(state.systems)
    if (state.step != SetupStep.Ready) return
    if (state.systemCount > 0) EmulatorsRow(state.emulators)
    // Only after a sign-in on the box art step: the run starts in the background.
    if (state.isBoxArtComing) {
        SetupInfoRow(
            title = stringResource(R.string.setup_ready_box_art),
            body = null,
            isFocused = false,
            // One content gap from the rows around it, like the statistics row.
            modifier = Modifier.testTag(SetupTags.BOX_ART_COMING),
        )
    }
    if (state.asksAboutStatistics) {
        SetupInfoRow(
            title = stringResource(R.string.setup_stats_title),
            body = stringResource(R.string.setup_stats_body),
            isFocused = state.focus == SetupControl.UsageStatistics,
            isFocusable = true,
            modifier = Modifier.setupControl(state, SetupControl.UsageStatistics, onAction),
        ) {
            SetupSwitch(state.usageStatistics, stringResource(if (state.usageStatistics) R.string.setup_stats_on else R.string.setup_stats_off))
        }
    }
    SetupInfoRow(
        title = stringResource(R.string.setup_one_hand_title),
        body = stringResource(R.string.setup_one_hand_body),
        isFocused = state.focus == SetupControl.PushRightToPlay,
        isFocusable = true,
        modifier = Modifier.setupControl(state, SetupControl.PushRightToPlay, onAction),
    ) {
        SetupSwitch(state.pushRightToPlay, stringResource(if (state.pushRightToPlay) R.string.setup_stats_on else R.string.setup_stats_off))
    }
    Box(Modifier.padding(top = PlumDimens.setupButtonSpacer)) {
        SetupButton(
            text = stringResource(R.string.setup_start_playing),
            isPrimary = true,
            isFocused = state.focus == SetupControl.StartPlaying,
            modifier = Modifier.setupControl(state, SetupControl.StartPlaying, onAction),
        )
    }
    if (state.message != null) StepNote(state.message, R.string.setup_folder_note)
}

/** The folders or systems could not be read: Try again reads them anew; Set up later leaves setup (quiet, as on the folder step). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LoadFailedContent(
    state: SetupUiState,
    onAction: (Action) -> Unit,
) {
    FlowRow(
        modifier = Modifier.padding(top = PlumDimens.setupButtonSpacer),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonGap),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.setupButtonSpacer),
    ) {
        SetupButton(
            text = stringResource(R.string.setup_try_again),
            isPrimary = true,
            isFocused = state.focus == SetupControl.TryAgain,
            modifier = Modifier.setupControl(state, SetupControl.TryAgain, onAction),
        )
        SetupButton(
            text = stringResource(R.string.setup_set_up_later),
            isPrimary = false,
            isFocused = state.focus == SetupControl.SetUpLater,
            modifier = Modifier.setupControl(state, SetupControl.SetUpLater, onAction),
        )
    }
    if (state.message != null) StepNote(state.message, R.string.setup_folder_note)
}

/** "1,284 games" (16 / 600) and "18 systems" (12, `muted`) centred on one line, at the two ends. */
@Composable
private fun LibraryCounts(state: SetupUiState) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    val format = rememberCountFormat()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        BasicText(
            text = pluralStringResource(R.plurals.home_games_count, state.gameCount, format.format(state.gameCount)),
            style = type.setupCount.copy(color = colors.text),
            modifier = Modifier.testTag(SetupTags.GAME_COUNT),
        )
        BasicText(
            text = pluralStringResource(R.plurals.home_systems_count, state.systemCount, format.format(state.systemCount)),
            style = type.smallFact.copy(color = colors.muted),
            modifier = Modifier.testTag(SetupTags.SYSTEM_COUNT),
        )
    }
}

/** The four biggest systems in two columns, 20 apart; rows 24 high. */
@Composable
private fun BiggestSystems(systems: List<SystemCount>) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.meta
    val format = rememberCountFormat()
    // Two columns; one when the text is large and a name with its count no longer fits half the width.
    val columns = if (PlumTheme.layout.textScale > FOUND_TWO_COLUMNS_MAX_SCALE) 1 else FOUND_COLUMNS
    Column(
        Modifier.padding(top = PlumDimens.setupFoundTopPadding).testTag(SetupTags.FOUND),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.setupFoundRowGap),
    ) {
        systems.take(BIGGEST_SYSTEMS).chunked(columns).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(PlumDimens.setupFoundColumnGap)) {
                for (i in 0 until columns) {
                    val system = pair.getOrNull(i)
                    Row(
                        Modifier.weight(1f).height(PlumTheme.layout.setupFoundRowHeight),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (system != null) {
                            BasicText(
                                text = system.name,
                                style = style.copy(color = colors.text2),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            BasicText(text = format.format(system.gameCount), style = style.copy(color = colors.muted), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** "Emulators found for 16 of 18 systems", and the systems still without one. */
@Composable
private fun EmulatorsRow(coverage: EmulatorCoverage?) {
    val format = rememberCountFormat()
    val (title, body) =
        when {
            coverage == null -> stringResource(R.string.setup_emulators_checking) to null
            coverage.missing.isEmpty() ->
                pluralStringResource(R.plurals.setup_emulators_all, coverage.systems, format.format(coverage.systems)) to
                    stringResource(R.string.setup_emulators_all_sub)
            coverage.systemsWithEmulator == 0 ->
                stringResource(R.string.setup_emulators_none) to stringResource(R.string.setup_emulators_none_sub, joinNames(coverage.missing))
            else ->
                stringResource(R.string.setup_emulators_some, format.format(coverage.systemsWithEmulator), format.format(coverage.systems)) to
                    stringResource(R.string.setup_emulators_missing, joinNames(coverage.missing))
        }
    SetupInfoRow(title = title, body = body, isFocused = false, modifier = Modifier.padding(top = PlumDimens.setupRowTopMargin).testTag(SetupTags.EMULATORS))
}

/** "A", "A and B", "A, B and C", "A, B, C and 4 others". */
@Composable
private fun joinNames(names: List<String>): String {
    val shown = names.take(NAMED_SYSTEMS)
    val others = names.size - shown.size
    val format = rememberCountFormat()
    val othersText = pluralStringResource(R.plurals.setup_list_others, others.coerceAtLeast(1), format.format(others))
    val items = if (others > 0) shown + othersText else shown
    if (items.size == 1) return items[0]
    val head = items.dropLast(1).reduce { acc, name -> stringResource(R.string.setup_list_more, acc, name) }
    return stringResource(R.string.setup_list_pair, head, items.last())
}

private const val MIN_PROGRESS = 0.08f
private const val BIGGEST_SYSTEMS = 4
private const val FOUND_COLUMNS = 2
private const val FOUND_TWO_COLUMNS_MAX_SCALE = 1.3f
private const val NAMED_SYSTEMS = 3
