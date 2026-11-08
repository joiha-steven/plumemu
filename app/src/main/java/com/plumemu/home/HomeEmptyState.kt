package com.plumemu.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.ButtonKind
import com.plumemu.coreui.components.CentredMessage
import com.plumemu.coreui.components.PlumButton
import com.plumemu.coreui.theme.EmptyStateDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/** A button of the home empty state: what A does while it is focused. */
internal enum class EmptyButton { AddFolder, FilesNotAdded, ChooseFolder }

/** The buttons the empty library offers, first is the primary one. Reading folders offers none. */
internal fun buttonsFor(why: EmptyLibrary): List<EmptyButton> =
    when (why) {
        EmptyLibrary.NoFolder -> listOf(EmptyButton.AddFolder)
        EmptyLibrary.NoGames -> listOf(EmptyButton.FilesNotAdded, EmptyButton.ChooseFolder)
        // Nothing to report: the page would say "Every file is in the library"; what helps is another folder.
        EmptyLibrary.NoFiles -> listOf(EmptyButton.ChooseFolder)
        EmptyLibrary.Scanning -> emptyList()
    }

/**
 * Where the focus is on the empty state: in the buttons ([inButtons], Right from the column goes in, Left from the first
 * one comes back) or on the column row, where only the A shortcut reaches the primary button. [index] is read against the
 * buttons shown now with [indexIn]. Only one thing looks focused: the column row or a button.
 */
@Stable
internal class EmptyFocus {
    var index by mutableIntStateOf(0)
    var inButtons by mutableStateOf(false)

    fun indexIn(count: Int): Int = index.coerceIn(0, (count - 1).coerceAtLeast(0))

    /** Moves by [step] inside [count] buttons; false at an end (nothing moved). */
    fun move(
        step: Int,
        count: Int,
    ): Boolean {
        val from = indexIn(count)
        val to = (from + step).coerceIn(0, (count - 1).coerceAtLeast(0))
        index = to
        return to != from
    }
}

/** The button A presses now: the focused one, or the primary one (the shortcut) while the column row has the focus; null while no button is shown. */
internal fun HomeNotices.focusedEmptyButton(): EmptyButton? {
    val buttons = buttonsFor(emptyLibrary.value)
    return buttons.getOrNull(if (emptyFocus.inButtons) emptyFocus.indexIn(buttons.size) else 0)
}

/** The focused button as the hint bar needs it; collects the reason only while the empty message is up ([isUp]). */
@Composable
internal fun rememberEmptyButton(isUp: Boolean): EmptyButton? {
    if (!isUp) return null
    val notices = LocalHomeNotices.current
    val why by notices.emptyLibrary.collectAsState()
    val buttons = buttonsFor(why)
    return buttons.getOrNull(if (notices.emptyFocus.inButtons) notices.emptyFocus.indexIn(buttons.size) else 0)
}

/**
 * No games at all: the games area says why, with its buttons (board: Parts › Empty state): a mark, a title, two lines of body,
 * a primary and a secondary button side by side (stacked when they do not fit). Centred in the part of the area that is on screen
 * with systems focused. A tap on a button focuses and presses it; Right from the column enters the buttons, Left and Right move between them, A presses the focused one (or the primary one from the column) ([FollowRouterActions]).
 */
@Composable
internal fun EmptyLibraryMessage(
    modifier: Modifier,
    onAction: (Action) -> Unit,
) {
    val onScreen =
        PlumTheme.layout.width - PlumDimens.stripMarginSide * 2 - PlumTheme.layout.systemsColumnWidth - PlumDimens.columnGap
    val notices = LocalHomeNotices.current
    val why by notices.emptyLibrary.collectAsState()
    val folderMessage by notices.folderMessage.collectAsState()
    // Leaving the empty state (games arrived, Settings opened) puts the focus back on the column row.
    DisposableEffect(notices) { onDispose { notices.emptyFocus.inButtons = false } }
    val problem = folderMessage?.let { stringResource(it) }
    val buttons = buttonsFor(why)
    if (buttons.isEmpty()) {
        // Reading folders: the plain message, nothing to press (a tap does nothing, as A does nothing).
        CentredBox(modifier, onScreen) {
            CentredMessage(
                title = stringResource(R.string.setup_scan_title),
                body = listOfNotNull(problem, stringResource(R.string.setup_scan_running)).joinToString("\n"),
                modifier =
                    Modifier
                        .width(onScreen)
                        .padding(horizontal = PlumDimens.edgeFade)
                        .testTag(HomeTags.EMPTY_MESSAGE),
            )
        }
        return
    }
    val title = stringResource(if (why == EmptyLibrary.NoFolder) R.string.home_empty_title else R.string.home_nogames_title)
    val body =
        stringResource(
            when (why) {
                EmptyLibrary.NoFolder -> R.string.home_empty_body_folder
                EmptyLibrary.NoFiles -> R.string.home_nogames_body_no_files
                else -> R.string.home_nogames_body_files
            },
        )
    val focused = notices.emptyFocus.indexIn(buttons.size)
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    CentredBox(modifier, onScreen) {
        EmptyStack(
            modifier =
                Modifier
                    .then(if (why == EmptyLibrary.NoFolder) Modifier.clickable(interactionSource = null, indication = null) { onAction(Action.Enter) } else Modifier)
                    .testTag(HomeTags.EMPTY_MESSAGE),
            // The board's empty state title is 600 (the ramp's message title is 500).
            title = { BasicText(title, style = type.messageTitle.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center), color = { colors.text }) },
            // A refused folder ask says why above the body, as before.
            body = { BasicText(lines(problem, body), style = type.meta.copy(textAlign = TextAlign.Center), color = { colors.body }) },
            buttons = {
                ButtonsRow(EmptyStateDimens.gap, EmptyStateDimens.buttonBleed) {
                    buttons.forEachIndexed { i, button ->
                        PlumButton(
                            label = stringResource(button.label()),
                            kind = if (i == 0) ButtonKind.Primary else ButtonKind.Secondary,
                            focused = notices.emptyFocus.inButtons && i == focused,
                            // One line when the label fits, else it wraps; never an ellipsis.
                            maxLines = 4,
                            modifier =
                                Modifier
                                    .testTag(HomeTags.emptyButton(button))
                                    .clickable(interactionSource = null, indication = null) {
                                        notices.emptyFocus.index = i
                                        notices.emptyFocus.inButtons = true
                                        onAction(Action.Enter)
                                    },
                        )
                    }
                }
            },
        )
    }
}

/**
 * An empty-state message outside the home list (the library error): the same title, body and one focused primary button
 * as the home empty states, centred in [modifier]'s area. A tap on the button or A calls [onPress] ([onPress] is the caller's to wire).
 */
@Composable
internal fun EmptyStateWithButton(
    title: String,
    body: String,
    buttonLabel: String,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    val type = PlumTheme.type
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        EmptyStack(
            modifier = Modifier,
            title = { BasicText(title, style = type.messageTitle.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center), color = { colors.text }) },
            body = { BasicText(body, style = type.meta.copy(textAlign = TextAlign.Center), color = { colors.body }) },
            buttons = {
                ButtonsRow(EmptyStateDimens.gap, EmptyStateDimens.buttonBleed) {
                    PlumButton(
                        label = buttonLabel,
                        kind = ButtonKind.Primary,
                        focused = true,
                        maxLines = 4,
                        modifier = buttonModifier.clickable(interactionSource = null, indication = null) { onPress() },
                    )
                }
            },
        )
    }
}

/** Title, body and buttons one under the other, centred, [EmptyStateDimens.gap] apart; the body gives way when the height is short. */
@Composable
private fun EmptyStack(
    modifier: Modifier,
    title: @Composable () -> Unit,
    body: @Composable () -> Unit,
    buttons: @Composable () -> Unit,
) {
    Layout(content = { title(); body(); buttons() }, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val textRoom = width - PlumDimens.edgeFade.roundToPx() * 2
        val loose = Constraints(maxWidth = textRoom.coerceAtLeast(0))
        val bodyLoose = Constraints(maxWidth = minOf(textRoom, EmptyStateDimens.bodyWidth.roundToPx()).coerceAtLeast(0))
        val titleP = measurables[0].measure(loose)
        val bodyP = measurables[1].measure(bodyLoose)
        // The text keeps 32 dp clear on each side; a button that needs more than the area reaches into its margins, evenly on both sides (a label never breaks or ends in an ellipsis).
        val buttonsP = measurables[2].measure(Constraints(maxWidth = textRoom.coerceAtLeast(0) + EmptyStateDimens.buttonBleed.roundToPx() * 2))
        val g = EmptyStateDimens.gap.roundToPx()
        fun heightOf(parts: List<Placeable>) = parts.sumOf { it.height } + g * (parts.size - 1)
        // The body gives way while the height is short (the largest text sizes); the title and the buttons stay.
        val shown =
            listOf(listOf(titleP, bodyP, buttonsP), listOf(titleP, buttonsP))
                .firstOrNull { heightOf(it) <= constraints.maxHeight } ?: listOf(titleP, buttonsP)
        val height = heightOf(shown)
        val w = shown.maxOf { it.width }
        layout(w, height) {
            var y = 0
            shown.forEach {
                it.place((w - it.width) / 2, y)
                y += it.height + g
            }
        }
    }
}

/** The non-null [parts], one per line. */
private fun lines(vararg parts: String?): String = parts.filterNotNull().joinToString("\n")

internal fun EmptyButton.label(): Int =
    when (this) {
        EmptyButton.AddFolder -> R.string.home_hint_add_folder
        EmptyButton.FilesNotAdded -> R.string.home_empty_files_not_added
        EmptyButton.ChooseFolder -> R.string.home_empty_choose_folder
    }

@Composable
private fun CentredBox(
    modifier: Modifier,
    width: Dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Box(Modifier.width(width), contentAlignment = Alignment.Center) { content() }
    }
}

/** The buttons side by side, centred; one under the other when the row is wider than the space (the text room; the room given is wider by the button bleed on each side, [sideBySideInset] takes it off). */
@Composable
private fun ButtonsRow(
    gap: Dp,
    sideBySideInset: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(Constraints(maxWidth = loose.maxWidth)) }
        val g = gap.roundToPx()
        val sideBySide = placeables.sumOf { it.width } + g * (placeables.size - 1) <= constraints.maxWidth - sideBySideInset.roundToPx() * 2
        val width = if (sideBySide) placeables.sumOf { it.width } + g * (placeables.size - 1) else placeables.maxOf { it.width }
        val height = if (sideBySide) placeables.maxOf { it.height } else placeables.sumOf { it.height } + g * (placeables.size - 1)
        layout(width, height) {
            var at = 0
            placeables.forEach {
                if (sideBySide) {
                    it.place(at, (height - it.height) / 2)
                    at += it.width + g
                } else {
                    it.place((width - it.width) / 2, at)
                    at += it.height + g
                }
            }
        }
    }
}
