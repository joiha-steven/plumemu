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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
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
import com.plumemu.coreui.components.EdgeFade
import com.plumemu.coreui.components.GameRowLook
import com.plumemu.coreui.components.InsetFocusPillShape
import com.plumemu.coreui.components.SystemRow
import com.plumemu.coreui.components.SystemRowLook
import com.plumemu.coreui.components.clipSidesLeavingRoom
import com.plumemu.coreui.components.rememberGlidingPill
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
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
    // Apps shows details in its list only (the icon column); a wide grid takes the whole width; a narrow grid keeps its stop.
    // Without box art a list has the text-only column on wide screens only (listHasDetails).
    val showsDetails by remember(state, content, gridIds, layout) {
        derivedStateOf {
            val entry = content.entries.getOrNull(state.focus.systemIndex)
            val hasColumn =
                if (content.showsGrid(entry, gridIds)) layout.widthClass == WidthClass.Narrow else listHasDetails(layout, content.hasBoxArt)
            entry?.hasDetails == true && hasColumn && !(entry.kind == EntryKind.Apps && content.showsGrid(entry, gridIds))
        }
    }
    // A wide screen's column never runs past the right margin: it takes the room left at this stop (homeDetailsFit).
    val detailsWidth by remember(state, layout, content.hasBoxArt, showsGrid) { derivedStateOf { homeDetailsFit(layout, content.hasBoxArt, showsGrid, state.focus.column) } }
    // Without box art an empty library keeps the no-details width (its message centred); its Apps list
    // shrinks like any list with a column, so the column shows (SPEC: Apps list). With box art it is unchanged.
    // Every entry has details now, so "no details" never holds: only the Apps list keeps its column.
    val onApps by remember(state, content) { derivedStateOf { content.entries.getOrNull(state.focus.systemIndex)?.kind == EntryKind.Apps } }
    val keepsFullWidth = content.isLibraryEmpty && !content.hasBoxArt && !(onApps && showsDetails)
    // 640 dp and up, narrow, no details beside the list: with the systems open its preview must end inside the margin
    // (the right column is the year, region or system code); it takes its full width once the games have the focus.
    val systemsOpen by remember(state) { derivedStateOf { state.focus.column == Column.Systems } }
    val previewFits = layout.isNarrowFitted && !showsGrid && !showsDetails && !keepsFullWidth && systemsOpen
    val gamesWidth = if (previewFits) layout.narrowListWidth else if (keepsFullWidth) layout.listWidthWithoutArt else homeGamesWidth(layout, content.hasBoxArt, showsGrid)
    // A grid's focus ring and a list's focus slab (drawn past its first and last row) may reach into the bar gaps; a first or last row's glow reaches
    // [SlabDimens.listEndRoom] past the row, further than the gap, so the clip leaves that much above (else the glow is cut flat under the title).
    // Below stays the gap: a row cut by the list's end must not show in the hint keys' strip. A grid's ring keeps the gap too (its shots are not touched here).
    Box(modifier = modifier.then(Modifier.clipSidesLeavingRoom(above = if (showsGrid) PlumDimens.barGap else maxOf(PlumDimens.barGap, SlabDimens.listEndRoom))).stripDrag(state).testTag(HomeTags.STRIP)) {
        Row(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = state.translationX },
            horizontalArrangement = Arrangement.spacedBy(PlumDimens.columnGap),
        ) {
            // Starts at the screen edge: the 24 dp margin is inside the column, so the selector can run under the edge.
            SystemsColumn(content, state, onAction, Modifier.width(PlumDimens.stripMarginSide + PlumTheme.layout.systemsColumnWidth).fillMaxHeight(), isPanelOpen)
            if (showsGrid) {
                GamesGrid(content, state, onAction, Modifier.width(gamesWidth).fillMaxHeight(), isPanelOpen)
                PrefetchNeighbourCovers(content, state, gridIds)
            } else {
                // The list's width in px as it is laid out (the bleed of boardListStart included), for the pill images built ahead.
                val density = LocalDensity.current
                val listWidthPx = with(density) { gamesWidth.roundToPx() + if (keepsFullWidth) 0 else PlumDimens.gamesListBleed.roundToPx() }
                CompositionLocalProvider(LocalGamesListWidthPx provides listWidthPx) {
                    GamesList(content, state, gridIds, positions, onAction, Modifier.width(gamesWidth).fillMaxHeight().then(if (keepsFullWidth) Modifier else Modifier.boardListStart()), isPanelOpen, listShowsPlace(layout, PlumTheme.type.font), showSystemInfo)
                }
            }
            // Beside the list on wide screens; on narrow ones the third stop, after list or grid.
            if (showsDetails && (layout.widthClass == WidthClass.Wide || detailsWidth > 0.dp)) {
                DetailsColumn(content, state, onAction, Modifier.width(detailsWidth).fillMaxHeight(), detailsWidth)
            }
        }
        if (!hideFade) EdgeFade(Modifier.align(Alignment.CenterEnd).testTag(HomeTags.EDGE_FADE))
    }
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
    isPanelOpen: Boolean,
) {
    val listState = rememberLazyListState()
    // The counts peek out left of a narrow game card: not drawn while it is open (read while drawing, so no row recomposes).
    val panelOpen = rememberUpdatedState(isPanelOpen)
    val isNarrow = PlumTheme.layout.widthClass == WidthClass.Narrow
    val countHidden = remember(panelOpen, isNarrow) { { isNarrow && panelOpen.value } }
    val glide = rememberListGlide(stride = PlumTheme.layout.systemRowHeight, glowRoom = true)
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
    val pillShape = remember { InsetFocusPillShape(LeftColumnDimens.systemPillStart, LeftColumnDimens.systemBarInset) }
    val pill =
        rememberGlidingPill(listState, glide, rowHeight = PlumTheme.layout.systemRowHeight, stride = PlumTheme.layout.systemRowHeight, extraGap = PlumDimens.systemGroupGap, shape = pillShape, fromBottom = true) {
            if (state.focus.column == Column.Systems && !notices.emptyFocus.inButtons) state.focus.systemIndex else -1
        }
    // The column fades out at an edge it continues past (design/rules.md › Lists that scroll).
    val groupGapPx = with(LocalDensity.current) { PlumDimens.systemGroupGap.toPx() }
    val fades = remember(listState, glide, state, content.firstSystemIndex, groupGapPx) {
        ListEdgeFades(listState, glide, wholeRowsOnly = true, gapAbove = { if (it == content.firstSystemIndex) groupGapPx else 0f }) { state.focus.systemIndex }
    }
    // The column takes the strip's whole height; a row cut by its top or bottom edge is hidden, only whole rows show.
    val systemsHeightPx = with(LocalDensity.current) { PlumTheme.layout.stripHeight.roundToPx() }
    Box(modifier) {
        pill.Provide {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().listHeightAtMost(systemsHeightPx).testTag(HomeTags.SYSTEMS).then(pill.modifier).then(glide.withFadeMask(fades, PlumTheme.edgeFadeOn, hidesCutRows = true)),
                contentPadding = PaddingValues(vertical = glide.extra),
            ) {
                itemsIndexed(content.entries, key = { _, entry -> entry.id }) { index, entry ->
                    if (index == content.firstSystemIndex) Spacer(Modifier.height(PlumDimens.systemGroupGap))
                    // Read the focus in its own derived state: a game-row move changes the focus object
                    // but not this row's look, so the row is not recomposed.
                    val look by remember(state, index) { derivedStateOf { systemLook(state.focus, index, notices.emptyFocus.inButtons) } }
                    val tap = remember(index, onAction) { Modifier.tapItem(Column.Systems, index, onAction).testTag(HomeTags.system(index)) }
                    // Counts and System codes stay in the peek as the names fade; the rail covers the count's place, so it fades with the names.
                    // Codes stand in for the counts only in the peek: the open column keeps its counts.
                    val edgeText = if (content.leftEdge == LeftEdgeSetting.Codes && !systemsOpen) entryCode(entry) else numbers.formatSystemCount(entry.shownCount)
                    SystemRow(name = entryName(entry), count = edgeText, look = look, nameAlpha = nameAlpha, modifier = tap, bleedStart = LeftColumnDimens.systemLabelStart, hasDot = entry.id in newGameSystems, fadeCount = countsGiveWay, countHidden = countHidden)
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
        val key = stringResource(LocalButtonLetters.current.favorite)
        EmptyEntryLine(stringResource(R.string.home_favorites_empty, key), HomeTags.EMPTY_FAVORITES, modifier)
        return
    }
    if (entry.rows.isEmpty() && entry.kind == EntryKind.Recent) {
        EmptyEntryLine(stringResource(R.string.home_recent_empty), HomeTags.EMPTY_RECENT, modifier)
        return
    }
    // L1/R1 with the games focused: the next system's games fade in instead of popping. Moves in
    // the system column swap the preview at once (a held key would strobe it).
    val fade = rememberContentFade(entry.id) { fadesSystemSwap(state) }
    GamesPages(content, entryIndex, gridIds, state, onAction, panelOpen, positions, showsPlace, showSystemInfo, modifier.testTag(HomeTags.GAMES).fadingIn(fade))
}

/**
 * Favorites or Recent with no game yet (the area would be blank): one muted line where the list would start,
 * like Search's "Type a game name to search". Favorites names the key that adds one (Y; [com.plumemu.settings.ButtonLetters.favorite]);
 * Recent says where played games appear. It may wrap to two lines in a long translation; it is never cut.
 */
@Composable
private fun EmptyEntryLine(
    text: String,
    tag: String,
    modifier: Modifier,
) {
    val layout = PlumTheme.layout
    // As the empty library's message: the area from the list's start to the right margin, with the systems focused.
    val onScreen = layout.width - PlumDimens.stripMarginSide * 2 - layout.systemsColumnWidth - PlumDimens.columnGap
    Box(modifier) {
        Box(Modifier.width(onScreen).heightIn(min = layout.gameRowHeight).padding(horizontal = PlumDimens.gameRowPadding), contentAlignment = Alignment.CenterStart) {
            BasicText(
                text,
                style = PlumTheme.type.gameTitle.copy(color = PlumTheme.colors.muted),
                modifier = Modifier.testTag(tag),
            )
        }
    }
}

internal fun systemLook(
    focus: StripFocus,
    index: Int,
    // A button of the empty state has the focus: the column row is current, not focused.
    buttonHasFocus: Boolean = false,
): SystemRowLook =
    when {
        index != focus.systemIndex -> SystemRowLook.Normal
        focus.column == Column.Systems && !buttonHasFocus -> SystemRowLook.Focused
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
