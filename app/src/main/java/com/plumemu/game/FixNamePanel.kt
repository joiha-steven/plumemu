package com.plumemu.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.FieldRow
import com.plumemu.coreui.components.FieldRowFrame
import com.plumemu.coreui.components.OptionRowLook
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.rememberListGlide
import com.plumemu.home.KeepFocusedVisible
import com.plumemu.home.search.hideSystemBars
import com.plumemu.keyboard.CaretLine
import com.plumemu.keyboard.KeyboardChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/**
 * Fix name… in the game panel: the field (Plumemu's or Android's keyboard) pre-filled with
 * the shown name, then the system's titles ranked for it, each with its regions and year on the
 * line under it (the note line Play with uses). A query nothing matches shows one plain line.
 * The keyboard's search key, A and D-pad Down go to the results; the actions arrive through the
 * controller ([FixNameState.onAction]). Settings' Unidentified games draws the same body.
 */
@Composable
internal fun FixNameBody(
    state: FixNameState,
    onAction: (Action) -> Unit,
) {
    FollowTitleResults(state)
    TypingField(
        value = state.query,
        onValueChange = state::onQueryChange,
        typing = state,
        placeholder = stringResource(R.string.game_fix_name_prompt),
        hint = stringResource(R.string.game_name_hint),
        options = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
        onAction = onAction,
        tag = FixNameTags.FIELD,
    )
    Spacer(Modifier.height(PlumDimens.listRowGap))
    if (state.isLoading) {
        PlainLine(stringResource(R.string.game_fix_name_loading), FixNameTags.LOADING)
        return
    }
    if (state.query.isNotBlank() && state.isAnswered && state.results.isEmpty()) {
        PlainLine(stringResource(R.string.game_fix_name_no_match), FixNameTags.NO_MATCH)
        return
    }
    val listState = rememberLazyListState()
    val glide = rememberListGlide(stride = PlumTheme.layout.rowHeight + PlumDimens.listRowGap)
    val pill = rememberPanelPill(listState, glide, if (state.inField) -1 else state.row)
    pill.Provide {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().testTag(FixNameTags.RESULTS).then(pill.modifier).then(glide.modifier),
        contentPadding = PaddingValues(vertical = glide.extra),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
    ) {
        itemsIndexed(state.results, key = { index, _ -> index }) { index, choice ->
            val look = if (!state.inField && index == state.row) OptionRowLook.Focused else OptionRowLook.Normal
            val row = PanelRow(resultKey(index), UiText.Plain(choice.name), PanelCommand.PickTitle(choice), note = releaseNote(choice))
            PanelRowView(row, look, index, onAction)
        }
    }
    }
    KeepFocusedVisible(listState, glide) { if (state.inField) 0 else state.row }
}

/**
 * Use my own name: the field pre-filled with the shown name, a plain line when an empty name was
 * refused, then Save (row 0 of the panel column).
 */
@Composable
internal fun OwnNameBody(
    state: OwnNameState,
    onAction: (Action) -> Unit,
) {
    TypingField(
        value = state.text,
        onValueChange = state::onTextChange,
        typing = state,
        placeholder = stringResource(R.string.game_own_name_prompt),
        hint = stringResource(R.string.game_name_hint),
        options = KeyboardOptions(capitalization = KeyboardCapitalization.Words, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        onAction = onAction,
        tag = FixNameTags.OWN_FIELD,
    )
    // Scrolls when Plumemu's keyboard leaves it less room than its lines (TypingPanelLayout).
    Column(Modifier.verticalScroll(rememberScrollState()).testTag(FixNameTags.OWN_ROWS)) {
        if (state.isRefused) {
            Spacer(Modifier.height(PlumDimens.titleLineGap))
            BasicText(
                stringResource(R.string.game_own_name_empty),
                style = PlumTheme.type.smallFact.copy(color = PlumTheme.colors.text2),
                maxLines = REFUSED_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = PlumDimens.gameRowPadding).testTag(FixNameTags.REFUSED),
            )
        }
        Spacer(Modifier.height(PlumDimens.listRowGap))
        val save = PanelRow(SAVE_KEY, UiText.Res(R.string.game_own_name_save), PanelCommand.SaveOwnName(state.text))
        PanelRowView(save, if (state.inField) OptionRowLook.Normal else OptionRowLook.Focused, OwnNameState.SAVE_ROW, onAction)
    }
}

/** "USA, Europe · 1994", or whichever of the two the list has; null when it has neither. */
internal fun releaseNote(choice: TitleChoice): UiText? {
    val year = choice.year?.toString()
    return when {
        choice.regions.isNotEmpty() && year != null -> UiText.Res(R.string.game_fix_name_release, listOf(choice.regions, year))
        choice.regions.isNotEmpty() -> UiText.Plain(choice.regions)
        year != null -> UiText.Plain(year)
        else -> null
    }
}

/**
 * The field row; the keyboard follows [typing]'s [TypingState.keyboardUp]: up when A or a tap
 * asked for it (again on each ask, [KeyboardAsk.asks]: the system's Back may have closed it);
 * hidden, with the system bars hidden again, when the cursor leaves the field. Opening the panel
 * never brings it up. The field takes the input focus only while the keyboard is up, or Android
 * would hand it the window's focus and open the keyboard by itself. The keyboard is told the
 * field's name and not to go full screen ([FieldRow]'s `keyboardHint`); its action key goes to
 * [TypingState.onKeyboardAction] and the action that returns is routed through [onAction].
 * Also the field of Fix box art ([FixBoxArtBody]).
 */
@Composable
internal fun TypingField(
    value: String,
    onValueChange: (String) -> Unit,
    typing: TypingState,
    placeholder: String,
    hint: String,
    options: KeyboardOptions,
    onAction: (Action) -> Unit,
    tag: String,
    /** A name before the text, as the sign-in form's fields have ("Name"); none in Fix name, whose field is the one row of its panel. */
    label: String? = null,
) {
    val labelWidth = label?.let { labelWidthOf(it) } ?: 0.dp
    if (typing.keyboardKind == KeyboardChoice.Plumemu) {
        OwnKeyboardField(value, typing, placeholder, hint, tag, label, labelWidth)
        return
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val owner = remember { KeyboardOwner() }
    val up = typing.keyboardUp
    LaunchedEffect(up, typing.keyboard.asks) {
        if (up) {
            focus.requestFocus()
            keyboard?.show()
            owner.hasShown = true
        } else if (owner.hasShown) {
            keyboard?.hide()
            focusManager.clearFocus()
            hideSystemBars(view)
            owner.hasShown = false
        }
    }
    DisposableEffect(view) {
        onDispose {
            if (owner.hasShown) {
                keyboard?.hide()
                hideSystemBars(view)
            }
        }
    }
    FieldRow(
        value = value,
        onValueChange = onValueChange,
        look = if (typing.inField) OptionRowLook.Focused else OptionRowLook.Normal,
        placeholder = placeholder,
        label = label,
        labelWidth = labelWidth,
        keyboardHint = hint,
        // A touch on the field means typing again; seen before the field handles it.
        modifier = Modifier.pointerInput(typing) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial).also { typing.focusField() } } }.testTag(tag),
        fieldModifier = Modifier.focusProperties { canFocus = up }.focusRequester(focus),
        keyboardOptions = options,
        keyboardActions = KeyboardActions(onSearch = { typing.onKeyboardAction()?.let(onAction) }, onDone = { typing.onKeyboardAction()?.let(onAction) }),
    )
}

/**
 * The field typed with Plumemu's keyboard: the [FieldRow] look with a drawn line and a static caret
 * ([CaretLine]), no text field, so Android's keyboard can never come up. The tray itself is drawn by
 * the screen ([PanelKeyboardTray]). A tap on the line puts the caret at the tapped character and
 * brings the tray up; the row is named by [hint] for accessibility, as Android's keyboard was told.
 */
@Composable
private fun OwnKeyboardField(
    value: String,
    typing: TypingState,
    placeholder: String,
    hint: String,
    tag: String,
    label: String?,
    labelWidth: Dp,
) {
    val onTapAt = remember(typing) { { offset: Int -> typing.focusFieldAt(offset) } }
    FieldRowFrame(
        look = if (typing.inField) OptionRowLook.Focused else OptionRowLook.Normal,
        modifier = Modifier.semantics { contentDescription = hint }.testTag(tag),
        answersButton = !typing.isTrayUp,
    ) { style ->
        if (label != null) FieldLabel(label, labelWidth)
        CaretLine(
            text = value,
            caret = { typing.fieldKeys.caret },
            showsCaret = { typing.inField },
            placeholder = placeholder,
            style = style,
            onTapAt = onTapAt,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            textTag = FixNameTags.typedText(tag),
            placeholderTag = FixNameTags.placeholder(tag),
            // Not typing: the start of the name shows ("Captain Toad: Treasure Tracker", not "Treasure Tracker").
            followsCaret = { typing.isTrayUp },
        )
    }
}

/** The label's width: the room of one name, as the sign-in form measures its names. */
@Composable
private fun labelWidthOf(label: String): Dp {
    val measurer = rememberTextMeasurer()
    val style = PlumTheme.type.settingsOption
    val density = LocalDensity.current
    return with(density) { measurer.measure(label, style, maxLines = 1).size.width.toDp() }
}

/** [FieldRow]'s label column for the field typed with Plumemu's keyboard. */
@Composable
private fun RowScope.FieldLabel(
    label: String,
    labelWidth: Dp,
) {
    val colors = PlumTheme.colors
    BasicText(label, style = PlumTheme.type.settingsOption.copy(color = colors.muted), maxLines = 1, modifier = Modifier.width(labelWidth))
    Spacer(Modifier.width(PlumDimens.rowTrailingGap))
}

/** Whether this field put the keyboard up (plain, not state: nothing draws from it). */
private class KeyboardOwner {
    var hasShown = false
}

@Composable
private fun PlainLine(
    text: String,
    tag: String,
) {
    Box(Modifier.height(PlumTheme.layout.rowHeight).padding(horizontal = PlumDimens.gameRowPadding), contentAlignment = Alignment.CenterStart) {
        BasicText(
            text,
            style = PlumTheme.type.settingsOption.copy(color = PlumTheme.colors.muted),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(tag),
        )
    }
}

/**
 * Keeps [state]'s results answering its query, off the main thread; a newer query cancels the
 * search of the older one. Nothing runs once the panel is gone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun FollowTitleResults(state: FixNameState) {
    LaunchedEffect(state) {
        snapshotFlow { state.finder to state.query }
            .mapLatest { (finder, query) ->
                // A newer key press cancels this search part-way through the list, not after it.
                query to finder?.let { withContext(Dispatchers.Default) { it.find(query) { ensureActive() } } }
            }
            .collect { (query, hits) -> if (hits != null) state.showResults(query, hits) }
    }
}

/** Test tags of Fix name… and Use my own name. */
internal object FixNameTags {
    const val FIELD = "fix_name_field"
    const val OWN_FIELD = "own_name_field"
    const val RESULTS = "fix_name_results"
    const val NO_MATCH = "fix_name_no_match"
    const val LOADING = "fix_name_loading"
    const val REFUSED = "own_name_refused"

    /** Use my own name's refusal and Save, under the field. */
    const val OWN_ROWS = "own_name_rows"

    /** The tray of Plumemu's keyboard over a panel's field. */
    const val TRAY = "panel_keyboard_tray"

    /** The typed text of the field tagged [field] (Plumemu's keyboard). */
    fun typedText(field: String) = "${field}_text"

    fun placeholder(field: String) = "${field}_placeholder"
}

internal fun resultKey(index: Int) = "title_$index"

internal const val SAVE_KEY = "save"

/** The refusal's lines: whole at the largest text size in the 248 panel ("Type a name first. An empty name cannot be saved." takes three). */
private const val REFUSED_LINES = 4

/** One column, so the bodies stack under the panel's title like its rows. */
@Composable
internal fun TypingPanelBody(
    screen: PanelScreen,
    onAction: (Action) -> Unit,
) {
    Column {
        when (screen) {
            is PanelScreen.FixName -> FixNameBody(screen.typing, onAction)
            is PanelScreen.OwnName -> OwnNameBody(screen.typing, onAction)
            is PanelScreen.FixBoxArt -> FixBoxArtBody(screen.typing, onAction)
            else -> Unit
        }
    }
}
