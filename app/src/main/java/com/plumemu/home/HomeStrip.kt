package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.plumemu.R
import com.plumemu.coredata.settings.LeftEdgeSetting
import com.plumemu.coreinput.Action
import com.plumemu.settings.LocalButtonLetters
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripFocus
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.stripDrag
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.CentredMessage
import com.plumemu.coreui.components.EdgeFade
import com.plumemu.coreui.components.GameRowLook
import com.plumemu.coreui.components.InsetFocusPillShape
import com.plumemu.coreui.components.SystemRow
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.coreui.components.rememberGlidingPill
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.rememberContentFade
import com.plumemu.coreui.theme.rememberListGlide

/**
 * The strip: system column and games list on one row wider than the screen, moved
 * only by `graphicsLayer { translationX }`, under the 32 dp fade at the right edge.
 * Strip coordinates start at the screen edge with the 24 dp margin inside the system column (core-input's `StripGeometry`).
 */
@Composable
internal fun HomeStrip(
    content: HomeContent,
    state: StripState,
    gridIds: Set<String>,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
    isPanelOpen: Boolean = false,
    positions: GamesPagePositions,
    detailsOnSecondScreen: Boolean = false,
    showSystemInfo: Boolean = true,
) {
    val layout = PlumTheme.layout
    // Only what the strip's shape depends on, so a row move does not recompose it.
    val showsGrid by remember(state, content, gridIds) {
        derivedStateOf { content.showsGrid(content.entries.getOrNull(state.focus.systemIndex), gridIds) }
    }
    // No fade where nothing continues: a grid with its games focused ends at the margin, and the
    // narrow details stop ends at it too (Nova-3-details draws none).
    val hideFade by remember(state, content, gridIds) {
        derivedStateOf {
            val column = state.focus.column
            column == Column.Details || (content.showsGrid(content.entries.getOrNull(state.focus.systemIndex), gridIds) && column != Column.Systems)
        }
    }
    // Apps never has details (apps have no art); a wide grid takes the whole width; a list has
    // none while the second screen's window shows them (layout C), a narrow grid keeps its stop.
    // Without box art a list has the text-only column on wide screens only (listHasDetails).
    val showsDetails by remember(state, content, gridIds, layout, detailsOnSecondScreen) {
        derivedStateOf {
            val entry = content.entries.getOrNull(state.focus.systemIndex)
            val hasColumn =
                if (content.showsGrid(entry, gridIds)) layout.widthClass == WidthClass.Narrow else listHasDetails(layout, content.hasBoxArt, detailsOnSecondScreen)
            entry?.hasDetails == true && hasColumn
        }
    }
    // Without box art an empty library keeps the no-details width (its message centred and its Apps
    // list as before the text-only column); with box art it is unchanged.
    val keepsFullWidth = content.isLibraryEmpty && !content.hasBoxArt
    // 640 dp and up, narrow, no details beside the list: with the systems open its preview must end inside the margin
    // (the right column is the year, region or system code); it takes its full width once the games have the focus.
    val systemsOpen by remember(state) { derivedStateOf { state.focus.column == Column.Systems } }
    val previewFits = layout.isNarrowFitted && !showsGrid && !showsDetails && !keepsFullWidth && systemsOpen
    val gamesWidth = if (previewFits) layout.narrowListWidth else if (keepsFullWidth) layout.listWidthWithoutArt else homeGamesWidth(layout, content.hasBoxArt, showsGrid, detailsOnSecondScreen)
    // A grid's focus ring may reach into the bar gaps (with box art, or Apps' grid without it); a list
    // without box art draws nothing outside the strip and the plain clip keeps that screen as it was.
    val clip = if (content.hasBoxArt || showsGrid) Modifier.clipSidesAndBarGaps() else Modifier.clipToBounds()
    Box(modifier = modifier.then(clip).stripDrag(state).testTag(HomeTags.STRIP)) {
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = state.translationX },
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.columnGap),
        ) {
            // Starts at the screen edge: the 24 dp margin is inside the column, so the selector can run under the edge.
            SystemsColumn(content, state, onAction, Modifier.width(PlumDimens.stripMarginSide + PlumTheme.layout.systemsColumnWidth).fillMaxHeight())
            if (showsGrid) {
                GamesGrid(content, state, onAction, Modifier.width(gamesWidth).fillMaxHeight(), isPanelOpen)
            } else {
                GamesList(content, state, gridIds, positions, onAction, Modifier.width(gamesWidth).fillMaxHeight(), isPanelOpen, listShowsPlace(layout, detailsOnSecondScreen), showSystemInfo)
            }
            // Beside the list on wide screens; on narrow ones the third stop, after list or grid.
            if (showsDetails) {
                DetailsColumn(content, state, onAction, Modifier.width(homeDetailsWidth(layout)).fillMaxHeight())
            }
        }
        if (!hideFade) EdgeFade(Modifier.align(Alignment.CenterEnd).testTag(HomeTags.EDGE_FADE))
    }
}

/**
 * Clips the strip at its sides (where it slides off screen) but lets drawing reach into the
 * 10 dp gaps above and below, where a focused cover's ring and glow may extend.
 */
private fun Modifier.clipSidesAndBarGaps(): Modifier =
    drawWithContent {
        val gap = PlumDimens.barGap.toPx()
        clipRect(top = -gap, bottom = size.height + gap) { this@drawWithContent.drawContent() }
    }

/**
 * Recent, Favorites, Apps, a 4 dp gap, then each system; every entry with its count. In the peek the letter rail
 * ([LetterRail]) takes the counts' place, only while the focused list has letters ([HomeEntry.railLetters]).
 */
@Composable
private fun SystemsColumn(
    content: HomeContent,
    state: StripState,
    onAction: (Action) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val glide = rememberListGlide(stride = PlumTheme.layout.systemRowHeight)
    val numbers = rememberCountFormat()
    // The names fade with the strip's real position as it slides to the peek (read while drawing).
    val nameAlpha = remember(state) { { state.systemNamesAlpha } }
    // A system that got new games shows a dot until its games get the focus (memory only: LibraryNotices).
    val notices = LocalHomeNotices.current
    val dots = notices.newGameSystems.collectAsState()
    val newGameSystems by dots
    val currentContent by rememberUpdatedState(content)
    LaunchedEffect(state) {
        // Also when a notice arrives for the system already open: its games are in front, so no dot.
        snapshotFlow { Triple(state.focus.column, state.focus.systemIndex, dots.value) }.collect { (column, index, _) ->
            if (column == Column.Systems) return@collect
            currentContent.entries.getOrNull(index)?.takeIf { it.kind == EntryKind.System && it.id in dots.value }?.let { notices.onSystemOpened(it.id) }
        }
    }
    val systemsOpen by remember(state) { derivedStateOf { state.focus.column == Column.Systems } }
    // Letters only where the focused list has letters to show: Recent and lists not in name order keep the counts
    // (the default) in the peek instead of a blank edge; the stored setting stays Letters.
    val countsGiveWay by remember(state, content) {
        derivedStateOf { content.leftEdge == LeftEdgeSetting.Letters && content.entries.getOrNull(state.focus.systemIndex)?.railLetters?.isNotEmpty() == true }
    }
    // The focus pill is drawn by the list, so it glides from row to row; inset as the row's own was.
    val pillInset = (PlumDimens.stripMarginSide - PlumDimens.gameRowPadding).coerceAtLeast(0.dp)
    val pillShape = remember(pillInset) { InsetFocusPillShape(pillInset) }
    val pill =
        rememberGlidingPill(listState, glide, rowHeight = PlumTheme.layout.systemRowHeight, stride = PlumTheme.layout.systemRowHeight, extraGap = PlumDimens.systemGroupGap, shape = pillShape, fromBottom = true) {
            if (state.focus.column == Column.Systems) state.focus.systemIndex else -1
        }
    Box(modifier) {
        pill.Provide {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(HomeTags.SYSTEMS).then(pill.modifier).then(glide.modifier),
                contentPadding = PaddingValues(vertical = glide.extra),
            ) {
                itemsIndexed(content.entries, key = { _, entry -> entry.id }) { index, entry ->
                    if (index == content.firstSystemIndex) Spacer(Modifier.height(PlumDimens.systemGroupGap))
                    // Read the focus in its own derived state: a game-row move changes the focus object
                    // but not this row's look, so the row is not recomposed.
                    val look by remember(state, index) { derivedStateOf { systemLook(state.focus, index) } }
                    val tap = remember(index, onAction) { Modifier.tapItem(Column.Systems, index, onAction).testTag(HomeTags.system(index)) }
                    // Counts and System codes stay in the peek as the names fade; the rail covers the count's place, so it fades with the names.
                    // Codes stand in for the counts only in the peek: the open column keeps its counts.
                    val edgeText = if (content.leftEdge == LeftEdgeSetting.Codes && !systemsOpen) entryCode(entry) else numbers.formatSystemCount(entry.shownCount)
                    SystemRow(name = entryName(entry), count = edgeText, look = look, nameAlpha = nameAlpha, modifier = tap, bleedStart = PlumDimens.stripMarginSide, hasDot = entry.id in newGameSystems, fadeCount = countsGiveWay)
                }
            }
        }
        // The peek is the column's last stretch: the rail sits there, centred in it.
        if (content.leftEdge == LeftEdgeSetting.Letters) LetterRail(content, state, Modifier.align(Alignment.CenterEnd).width(PlumTheme.layout.peek).fillMaxHeight())
    }
    KeepFocusedVisible(listState, glide) { state.focus.systemIndex }
}

/**
 * The focused entry's rows: a muted preview with systems focused, the list with games focused.
 * One page per entry ([GamesPages]); the pages beside the shown one are built ahead.
 */
@Composable
private fun GamesList(
    content: HomeContent,
    state: StripState,
    gridIds: Set<String>,
    positions: GamesPagePositions,
    onAction: (Action) -> Unit,
    modifier: Modifier,
    isPanelOpen: Boolean,
    showsPlace: Boolean,
    showSystemInfo: Boolean,
) {
    val entryIndex by remember(state) { derivedStateOf { state.focus.systemIndex } }
    // Read by each row's derived look, so opening the game panel recomposes only the focused row.
    val panelOpen = rememberUpdatedState(isPanelOpen)
    val entry = content.entries.getOrNull(entryIndex) ?: return
    if (entry.rows.isEmpty() && content.isLibraryEmpty) {
        EmptyLibraryMessage(modifier, onAction)
        return
    }
    if (entry.rows.isEmpty() && entry.kind == EntryKind.Favorites) {
        EmptyFavoritesLine(modifier)
        return
    }
    // L1/R1 with the games focused: the next system's games fade in instead of popping. Moves in
    // the system column swap the preview at once (a held key would strobe it).
    val fade = rememberContentFade(entry.id) { fadesSystemSwap(state) }
    GamesPages(content, entryIndex, gridIds, state, onAction, panelOpen, positions, showsPlace, showSystemInfo, modifier.testTag(HomeTags.GAMES).fadingIn(fade))
}

/**
 * Favorites with no game yet (the area would be blank): one muted line where the list would start,
 * like Search's "Type a game name to search", naming the key that adds one (Y; [com.plumemu.settings.ButtonLetters.favorite]).
 * It may wrap to two lines in a long translation; it is never cut.
 */
@Composable
private fun EmptyFavoritesLine(modifier: Modifier) {
    val layout = PlumTheme.layout
    val key = stringResource(LocalButtonLetters.current.favorite)
    // As the empty library's message: the area from the list's start to the right margin, with the systems focused.
    val onScreen = layout.width - PlumDimens.stripMarginSide * 2 - layout.systemsColumnWidth - PlumDimens.columnGap
    Box(modifier) {
        Box(Modifier.width(onScreen).heightIn(min = layout.gameRowHeight).padding(horizontal = PlumDimens.gameRowPadding), contentAlignment = Alignment.CenterStart) {
            BasicText(
                stringResource(R.string.home_favorites_empty, key),
                style = PlumTheme.type.gameTitle.copy(color = PlumTheme.colors.muted),
                modifier = Modifier.testTag(HomeTags.EMPTY_FAVORITES),
            )
        }
    }
}

/**
 * No games at all: the system column with Recent, Favorites and Apps stays,
 * and the games area says why it is empty. Centred in the part of the area that is on screen
 * with systems focused (from the list's start to the right margin), not in the whole list width.
 */
@Composable
private fun EmptyLibraryMessage(
    modifier: Modifier,
    onAction: (Action) -> Unit,
) {
    val onScreen =
        PlumTheme.layout.width - PlumDimens.stripMarginSide * 2 - PlumTheme.layout.systemsColumnWidth - PlumDimens.columnGap
    val notices = LocalHomeNotices.current
    val why by notices.emptyLibrary.collectAsState()
    val folderMessage by notices.folderMessage.collectAsState()
    val add = stringResource(R.string.home_empty_body, stringResource(LocalButtonLetters.current.confirm))
    val problem = folderMessage?.let { stringResource(it) }
    val (title, body) =
        when (why) {
            EmptyLibrary.NoFolder -> stringResource(R.string.home_empty_title) to add
            EmptyLibrary.Scanning -> stringResource(R.string.setup_scan_title) to stringResource(R.string.setup_scan_running)
            EmptyLibrary.NoGames -> stringResource(R.string.home_nogames_title) to stringResource(R.string.home_nogames_body, add)
        }
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        CentredMessage(
            title = title,
            // "A Add folder": the key's name follows Settings › Controls; a tap does what A does.
            body = if (problem == null) body else "$problem\n$body",
            // Kept clear of the right-edge fade on both sides, so the centre stays put and a long
            // line wraps instead of fading out (the Nova).
            modifier =
                Modifier
                    .width(onScreen)
                    .clickable(interactionSource = null, indication = null) { onAction(Action.Enter) }
                    .padding(horizontal = PlumDimens.edgeFade)
                    .testTag(HomeTags.EMPTY_MESSAGE),
        )
    }
}

internal fun systemLook(
    focus: StripFocus,
    index: Int,
): SystemRowLook =
    when {
        index != focus.systemIndex -> SystemRowLook.Normal
        focus.column == Column.Systems -> SystemRowLook.Focused
        else -> SystemRowLook.Current
    }

internal fun gameLook(
    focus: StripFocus,
    entryId: String,
    index: Int,
    isPanelOpen: Boolean = false,
): GameRowLook {
    val remembered = focus.rowOf(entryId) == index
    return when {
        focus.column == Column.Systems -> if (remembered) GameRowLook.PreviewRemembered else GameRowLook.Preview
        // The narrow details stop: the list has slid out to its years; the current one is accent.
        focus.column == Column.Details -> if (remembered) GameRowLook.Current else GameRowLook.Preview
        // The game panel has the focus: its row stays marked, without a second fill.
        remembered -> if (isPanelOpen) GameRowLook.MenuOpen else GameRowLook.Focused
        else -> GameRowLook.Normal
    }
}

/** Whether a new system's games fade in: only when they have the focus (L1/R1), read without observing. */
internal fun fadesSystemSwap(state: StripState): Boolean = Snapshot.withoutReadObservation { state.focus.column } != Column.Systems

/** The first row to show so [row] is on screen when a list opens: the top, or [row] as the last of [fit]. */
internal fun firstIndexShowing(
    row: Int,
    fit: Int,
): Int = if (row < fit) 0 else row - fit + 1
