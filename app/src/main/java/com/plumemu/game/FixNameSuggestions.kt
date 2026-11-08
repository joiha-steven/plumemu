package com.plumemu.game

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.rememberListGlide
import com.plumemu.home.BarRunClips
import com.plumemu.home.KeepFocusedVisible

/**
 * Fix name…'s results list: the closest titles (Settings' Unidentified games, the name not edited
 * yet) under a line that says what was found, then the other results under theirs; just the
 * results otherwise. Rows are the shared [PanelRowView]; the lines are [PlainLine]. The cursor
 * moves over [FixNameState.choices], suggestions first.
 */
@Composable
internal fun FixNameResults(
    state: FixNameState,
    onAction: (Action) -> Unit,
) {
    val entries = fixNameEntries(state)
    val choices = state.choices
    val listState = rememberLazyListState()
    val glide = rememberListGlide(stride = PlumTheme.layout.rowHeight + PlumDimens.panelRowGap, shadowPastEnds = true)
    val focusedEntry = if (state.inField) -1 else entries.indexOfFirst { it is FixNameEntry.Choice && it.index == state.row }
    val pill = rememberPanelPill(listState, glide, focusedEntry)
    val latestFocus = rememberUpdatedState(focusedEntry)
    val clips = remember(listState, glide) { BarRunClips(listState, glide, topRoom = 0f, ramps = true) { latestFocus.value } }
    pill.Provide {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().testTag(FixNameTags.RESULTS).then(pill.modifier).then(glide.withFadeMask(clips, isOn = true, hidesCutRows = true)),
            contentPadding = PaddingValues(vertical = glide.extra),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.panelRowGap),
        ) {
            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    is FixNameEntry.Header -> SuggestionLine(stringResource(entry.text), entry.tag)
                    is FixNameEntry.Choice -> {
                        val choice = choices[entry.index]
                        val look = if (!state.inField && entry.index == state.row) OptionRowLook.Focused else OptionRowLook.Normal
                        val row = PanelRow(resultKey(entry.index), UiText.Plain(choice.name), PanelCommand.PickTitle(choice), note = releaseNote(choice))
                        PanelRowView(row, look, entry.index, onAction)
                    }
                }
            }
        }
    }
    KeepFocusedVisible(listState, glide) { if (state.inField) 0 else focusedEntry.coerceAtLeast(0) }
}

/** What the results list is made of: the choices by their place in [FixNameState.choices], and the lines that say what the suggestions found. */
private sealed interface FixNameEntry {
    val key: String

    data class Header(val text: Int, val tag: String) : FixNameEntry {
        override val key get() = "header_$tag"
    }

    data class Choice(val index: Int) : FixNameEntry {
        override val key get() = "choice_$index"
    }
}

private fun fixNameEntries(state: FixNameState): List<FixNameEntry> {
    val shown = state.shownSuggestions
    val total = state.choices.size
    // While the tray is up the lines give way: the rows under the field matter there, and a short screen has room for few.
    val lines = !state.isTrayUp
    return buildList {
        // Nothing at all under the field (tray up, no suggestion, no result): still one short line, never an empty page.
        if (state.showsSuggestions && (lines || total == 0)) {
            val found = state.suggestions
            when {
                found == null -> add(FixNameEntry.Header(R.string.game_fix_name_suggesting, FixNameTags.SUGGESTING))
                found.isEmpty() -> add(FixNameEntry.Header(R.string.game_fix_name_no_suggestion, FixNameTags.NO_SUGGESTION))
                else -> add(FixNameEntry.Header(R.string.game_fix_name_suggestions, FixNameTags.SUGGESTIONS))
            }
        }
        shown.indices.forEach { add(FixNameEntry.Choice(it)) }
        // "No close title found" over bare results would contradict itself: the results are headed too.
        val noneClose = state.showsSuggestions && state.suggestions?.isEmpty() == true
        if (lines && (shown.isNotEmpty() || noneClose) && total > shown.size) add(FixNameEntry.Header(R.string.game_fix_name_others, FixNameTags.OTHERS))
        for (i in shown.size until total) add(FixNameEntry.Choice(i))
    }
}

/** [PlainLine]'s look, but the line may grow past one row's height when the text is large (the largest sizes wrap it to two lines). */
@Composable
private fun SuggestionLine(
    text: String,
    tag: String,
) {
    Box(Modifier.heightIn(min = PlumTheme.layout.rowHeight).padding(horizontal = PlumDimens.panelTitleStart), contentAlignment = Alignment.CenterStart) {
        BasicText(text, style = PlumTheme.type.settingsOption.copy(color = PlumTheme.colors.muted), modifier = Modifier.testTag(tag))
    }
}
