package com.plumemu.home.search

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.GameRow
import com.plumemu.coreui.components.InlineKeyboard
import com.plumemu.coreui.components.GameRowLook
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.components.WifiStatus
import com.plumemu.coreui.components.BatteryStatus
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.components.rememberGlidingPill
import com.plumemu.coreui.theme.rememberListGlide
import com.plumemu.home.KeepFocusedVisible
import com.plumemu.home.rememberCountFormat
import com.plumemu.home.variantSubtitle
import com.plumemu.keyboard.CaretLine
import com.plumemu.keyboard.KeyboardChoice

/**
 * Search's top bar: "Search", the query, the number of results once there is a query, then clock
 * and battery as on the main screen's bar. With Plumemu's keyboard the query is a plain line with a
 * static caret ([SearchQueryLine]); with Android's it is a text field that takes the keyboard while
 * the cursor is on it and gives it back when the cursor goes to the results.
 */
@Composable
internal fun SearchTopBar(
    search: SearchState,
    clock: () -> String?,
    battery: () -> String?,
    wifi: () -> Boolean = { false },
    charging: () -> Boolean = { false },
) {
    val colors = PlumTheme.colors
    val style = PlumTheme.type.topBar
    Row(
        modifier = Modifier.fillMaxWidth().height(PlumTheme.layout.topBarHeight).padding(horizontal = PlumDimens.stripMarginSide).testTag(SearchTags.TOP_BAR),
        horizontalArrangement = Arrangement.spacedBy(PlumDimens.topBarItemGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The typed text starts on the results' text line (strip margin + peek + row padding, the list's own
        // tokens): the label gives it that room, and only a label wider than that (a large text size) pushes it on.
        val queryStart = PlumTheme.layout.peek + PlumDimens.columnGap + PlumDimens.gameRowPadding
        val labelRoom = (queryStart - PlumDimens.stripMarginSide - PlumDimens.topBarItemGap).coerceAtLeast(0.dp)
        BasicText(stringResource(R.string.home_search_place), style = style.copy(color = colors.text2), maxLines = 1, modifier = Modifier.widthIn(min = labelRoom))
        if (search.usesOwnKeyboard) {
            SearchQueryLine(search, style, Modifier.weight(1f))
        } else {
            AndroidSearchField(search, style, Modifier.weight(1f))
        }
        if (search.query.isNotBlank() && search.isAnswered) {
            val numbers = rememberCountFormat()
            val count = search.results.size
            BasicText(pluralStringResource(R.plurals.home_games_count, count, numbers.format(count)), style = style.copy(color = colors.muted), maxLines = 1)
        }
        StatusText(clock)
        WifiStatus(wifi, style = PlumTheme.type.topBar.copy(color = PlumTheme.colors.muted), description = stringResource(R.string.wifi_connected))
        BatteryStatus(battery, charging, PlumTheme.type.topBar.copy(color = PlumTheme.colors.muted), stringResource(R.string.battery_charging))
    }
}

/**
 * The query typed with Plumemu's keyboard ([CaretLine]): the text, or the muted "Search games" when
 * empty, and the static caret while the cursor is on the field. A tap puts the caret at the tapped
 * character and goes back to typing.
 */
@Composable
private fun SearchQueryLine(
    search: SearchState,
    style: TextStyle,
    modifier: Modifier,
) {
    CaretLine(
        text = search.query,
        caret = { search.caret },
        showsCaret = { search.inField },
        placeholder = stringResource(R.string.home_search_hint),
        style = style.copy(color = PlumTheme.colors.text),
        onTapAt = search::focusQueryAt,
        modifier = modifier.testTag(SearchTags.FIELD),
        textTag = SearchTags.QUERY,
        placeholderTag = SearchTags.PROMPT,
    )
}

/**
 * The old path, Android's keyboard (Settings › Controls › Keyboard set to Android,
 * [KeyboardChoice.Android]): the field takes the keyboard while the cursor is on it and gives it back when the cursor
 * goes to the results.
 */
@Composable
private fun AndroidSearchField(
    search: SearchState,
    style: TextStyle,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    // Each ask (entering Search, A or a tap on the field) shows the keyboard again: the system's Back may have closed it.
    val owner = remember { KeyboardOwner() }
    val up = search.keyboardUp
    LaunchedEffect(up, search.keyboard.asks) {
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
    // Closing search takes the keyboard away too, and the bars it brought back.
    DisposableEffect(view) { onDispose { hideSystemBars(view) } }
    val prompt = stringResource(R.string.home_search_prompt)
    InlineKeyboard(stringResource(R.string.home_search_hint)) {
        BasicTextField(
            value = search.query,
            onValueChange = search::onQueryChange,
            singleLine = true,
            textStyle = style.copy(color = colors.text),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search.toResults() }),
            modifier =
                modifier
                    // A touch on the field means typing again; seen before the field handles it.
                    .pointerInput(search) { awaitEachGesture { awaitFirstDown(pass = PointerEventPass.Initial).also { search.focusField() } } }
                    // On the results the field cannot take focus: Android hands a window's focus to
                    // its first focusable view again, which would bring the keyboard back.
                    .focusProperties { canFocus = up }
                    .focusRequester(focus)
                    .testTag(SearchTags.FIELD),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (search.query.isEmpty()) BasicText(prompt, style = style.copy(color = colors.muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    field()
                }
            },
        )
    }
}

/** Whether Search put the keyboard up (plain, not state: nothing draws from it). */
private class KeyboardOwner {
    var hasShown = false
}

/**
 * On Android 6–10 the keyboard brings the system bars back and they stay after it hides (seen on
 * the API 23 emulator); the main screen has none (MainActivity.hideSystemBars, the same call).
 */
internal fun hideSystemBars(view: View) {
    val window = view.context.findActivity()?.window ?: return
    WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

/** The clock, read in its own scope as on the main screen's bar. */
@Composable
private fun StatusText(text: () -> String?) {
    val value = text() ?: return
    BasicText(value, style = PlumTheme.type.topBar.copy(color = PlumTheme.colors.muted), maxLines = 1)
}

/**
 * The results in the games column (where the list stands with games focused, the list's width
 * without box art): game rows in the one focus language, the star on favorites. An empty query
 * shows one muted line saying what to type; a query with no match, one plain line.
 */
@Composable
internal fun SearchResults(
    search: SearchState,
    onAction: (Action) -> Unit,
    isPanelOpen: Boolean,
    modifier: Modifier = Modifier,
) {
    val layout = PlumTheme.layout
    val start = layout.peek + PlumDimens.columnGap
    val listModifier = Modifier.padding(start = start).width(layout.listWidthWithoutArt).fillMaxHeight()
    val results = search.results
    val resources = LocalContext.current.resources
    val line =
        when {
            search.query.isBlank() -> R.string.home_search_empty to SearchTags.EMPTY
            search.isAnswered && results.isEmpty() -> R.string.home_search_no_match to SearchTags.NO_MATCH
            else -> null
        }
    if (line != null) {
        Box(modifier.then(listModifier)) {
            Box(Modifier.height(PlumTheme.layout.gameRowHeight).padding(horizontal = PlumDimens.gameRowPadding), contentAlignment = Alignment.CenterStart) {
                BasicText(
                    stringResource(line.first),
                    style = PlumTheme.type.gameTitle.copy(color = PlumTheme.colors.muted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(line.second),
                )
            }
        }
        return
    }
    val listState = rememberLazyListState()
    val glide = rememberListGlide(stride = PlumTheme.layout.gameRowHeight + PlumDimens.listRowGap)
    // Read by each row's derived look, so opening the game panel recomposes only the focused row.
    val panelOpen = rememberUpdatedState(isPanelOpen)
    // The focus pill is drawn by the list, so it glides from row to row; read while drawing.
    val pill =
        rememberGlidingPill(listState, glide, rowHeight = layout.gameRowHeight, stride = layout.gameRowHeight + PlumDimens.listRowGap) {
            if (resultLook(search, search.row, panelOpen.value) == GameRowLook.Focused) search.row else -1
        }
    pill.Provide {
    LazyColumn(
        state = listState,
        modifier = modifier.then(listModifier).testTag(SearchTags.RESULTS).then(pill.modifier).then(glide.modifier),
        contentPadding = PaddingValues(vertical = glide.extra),
        // No spacing: each game row carries its own gap (its tap area).
    ) {
        itemsIndexed(results, key = { _, hit -> hit.row.id }) { index, hit ->
            val look by remember(search, index) { derivedStateOf { resultLook(search, index, panelOpen.value) } }
            val tap = remember(index, onAction) { Modifier.tapItem(Column.Games, index, onAction).testTag(SearchTags.result(index)) }
            // The tags were set per system list (withVariantTags), so two games of one title read as they do there.
            val subtitle = remember(resources, hit.row.variantTags, hit.row.variantRegion) { variantSubtitle(resources, hit.row.variantTags, hit.row.variantRegion) }
            GameRow(title = hit.row.title, trailing = hit.row.trailing(hasBoxArt = false, showsSystem = true), look = look, modifier = tap, isFavorite = hit.row.isFavorite, subtitle = subtitle)
        }
    }
    }
    KeepFocusedVisible(listState, glide) { if (search.inField) 0 else search.row }
}

private fun resultLook(
    search: SearchState,
    index: Int,
    isPanelOpen: Boolean,
): GameRowLook =
    when {
        search.inField || index != search.row -> GameRowLook.Normal
        isPanelOpen -> GameRowLook.MenuOpen
        else -> GameRowLook.Focused
    }

/** Test tags of search's parts. */
internal object SearchTags {
    const val TOP_BAR = "search_top_bar"
    const val FIELD = "search_field"
    const val RESULTS = "search_results"
    const val NO_MATCH = "search_no_match"

    /** "Type a game name to search" while the query is empty. */
    const val EMPTY = "search_empty"

    /** The typed query's text (Plumemu's keyboard). */
    const val QUERY = "search_query"

    /** "Search games" while the query is empty (Plumemu's keyboard). */
    const val PROMPT = "search_prompt"

    /** Plumemu's keyboard tray. */
    const val TRAY = "search_tray"

    fun result(index: Int) = "search_result_$index"
}
