package com.plumemu.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import android.os.Handler
import android.os.Looper
import com.plumemu.coreui.components.WARM_DELAY_MS
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.rememberSlidingSwap

/**
 * The grids as pages, one per entry, in a row that never scrolls by hand; the page of the focused entry is the shown grid. As with the
 * games list ([GamesPages]) the pages beside the shown one are built ahead in the idle time between frames ([NeighbourPagesPrefetch]) and
 * kept while they are beside it (pinned), so a switch onto a grid places a grid that is built (its covers composed and measured) instead
 * of composing it inside the frame; a page that is not shown is measured at its size and neither placed, drawn nor tagged ([LocalGridPageShown]).
 * Entries not shown as a grid have an empty page. This row is also where the shown grid lives, so [modifier] gives the grid's size (and is
 * what the strip hides it with while a list is shown). The swap's fade is made here, on the row: it fades grid to grid and cuts in from a list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GridPages(
    content: HomeContent,
    gridIds: Set<String>,
    state: StripState,
    onAction: (Action) -> Unit,
    panelOpen: State<Boolean>,
    /** The grid's width: what each grid is measured at. */
    gridWidth: Dp,
    modifier: Modifier,
) {
    val layout = PlumTheme.layout
    // The Apps preview beside the systems spreads its columns to the right margin ([wholeColumnsWhile]), past the grid's own width: the row leaves room for it.
    val previewReach = (layout.width - PlumDimens.stripMarginSide * 2 - layout.systemsColumnWidth - PlumDimens.columnGap - gridWidth).coerceAtLeast(0.dp)
    val roomRight = GRID_PAGE_ROOM + previewReach
    val shownIndex by remember(state) { derivedStateOf { state.focus.systemIndex } }
    val prefetch = remember { NeighbourPagesPrefetch().also { it.waitsForRest = true } }
    val keys = remember(content) { content.entries.map { it.id } }
    val gridKeys = remember(content, gridIds) { content.entries.filter { content.showsGrid(it, gridIds) }.mapTo(HashSet()) { it.id } }
    SideEffect {
        prefetch.pageKeys = keys
        prefetch.listKeys = gridKeys
    }
    val pages = rememberLazyListState(initialFirstVisibleItemIndex = shownIndex, prefetchStrategy = prefetch)
    remember(pages) { pages.also { gamesPagesStateMade?.invoke(it) } }
    // Read without observing: a layout of the pages must not recompose this.
    Snapshot.withoutReadObservation {
        if (pages.firstVisibleItemIndex != shownIndex || pages.firstVisibleItemScrollOffset != 0) pages.requestScrollToItem(shownIndex)
    }
    // Built only while the focus rests (a held key, a scroll or a switch in progress build nothing); a rest asks for the pages not yet built.
    // The wait is a delayed post on the main looper, not a coroutine timer: a timer pending in Compose's clock moves the frame a test captures at.
    // The focus, a grid scrolled by a finger or a fling (no focus change), the strip moving: none of them may be going on.
    val scrolls = remember { GridScrolls() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val restEnded = remember(prefetch) { Runnable { prefetch.rest(true) } }
    val restKey = remember(state, scrolls) { { Triple(state.focus, scrolls.active, state.isMoving) } }
    SideEffect { prefetch.restKey = restKey }
    LaunchedEffect(prefetch, handler, restKey) {
        snapshotFlow(restKey).collect { (_, scrolling, moving) ->
            prefetch.rest(false)
            handler.removeCallbacks(restEnded)
            if (scrolling == 0 && !moving) handler.postDelayed(restEnded, WARM_DELAY_MS)
        }
    }
    DisposableEffect(handler) { onDispose { handler.removeCallbacks(restEnded) } }
    // L1/R1 with the games focused: the next system's grid fades in instead of popping; a grid after a list cuts in (this row stays composed while a list is shown).
    val entry by remember(state, content) { derivedStateOf { content.shownEntry(state.focus) } }
    val gridWasShown = remember { BooleanArray(1) { content.showsGrid(entry, gridIds) } }
    val fade = rememberSlidingSwap(entry?.id, { state.focus.systemIndex }, wrapsAt = content.entries.size) { fadesSystemSwap(state) && gridWasShown[0] && content.showsGrid(entry, gridIds) }
    gridWasShown[0] = content.showsGrid(entry, gridIds)
    CompositionLocalProvider(LocalGridScrolls provides scrolls) {
        LazyRow(
            state = pages,
            modifier = modifier.fadingIn(fade).withRoomAround(GRID_PAGE_ROOM, roomRight),
            userScrollEnabled = false,
        ) {
            itemsIndexed(content.entries, key = { _, entry -> entry.id }, contentType = { _, _ -> GRID_PAGE }) { index, entry ->
                GridPage(index, entry, content, gridIds, prefetch, gridKeys, state, pages, onAction, panelOpen, gridWidth)
            }
        }
    }
}

/** One page: the entry's grid (placed only while shown), or an empty page of the same size for an entry that is a list. */
@Composable
private fun LazyItemScope.GridPage(
    index: Int,
    slot: HomeEntry,
    content: HomeContent,
    gridIds: Set<String>,
    prefetch: NeighbourPagesPrefetch,
    gridKeys: Set<String>,
    state: StripState,
    pages: LazyListState,
    onAction: (Action) -> Unit,
    panelOpen: State<Boolean>,
    gridWidth: Dp,
) {
    // The Collections slot draws the open folder's grid when that folder is a grid; other slots draw their own entry's.
    val folder by remember(state, content, slot, gridIds) { derivedStateOf { content.openFolderIn(slot, state.focus)?.takeIf { content.showsGrid(it, gridIds) } } }
    val entry = if (slot.isFolderList) folder else slot.takeIf { it.id in gridKeys }
    if (entry == null) {
        Spacer(Modifier.fillParentMaxSize())
        return
    }
    val rowAt = remember(pages) { { pages.firstVisibleItemIndex } }
    val pin = rememberPagePin(index, slot.id, prefetch, state, rowAt)
    val shown = pin.shown
    val page = remember(shown, pin) { Modifier.fillParentMaxSize().placedOnlyWhen(shown, pin.keepWhenMeasured).rowsInSemanticsOnlyWhen(shown, HomeTags.GRID_PAGE) }
    Box(page) {
        CompositionLocalProvider(LocalGridPageShown provides shown) {
            GamesGrid(entry, state, onAction, Modifier.padding(start = GRID_PAGE_ROOM).width(gridWidth).fillMaxHeight(), panelOpen)
        }
    }
}

/**
 * The lazy row clips its pages at its sides, and a grid's focus (rim, light, shadow) is drawn past the grid's edges: the row is
 * [GRID_PAGE_ROOM] wider on each side than the room it takes, each grid is inset by the same, and what the row draws is unchanged.
 */
private fun Modifier.withRoomAround(
    left: Dp,
    right: Dp,
): Modifier =
    layout { measurable, constraints ->
        val before = left.roundToPx()
        val width = constraints.maxWidth + before + right.roundToPx()
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, placeable.height) { placeable.place(-before, 0) }
    }

/** How many grids are being scrolled now (a drag or a fling): the grids beside the shown one are not built meanwhile. */
internal class GridScrolls {
    var active by mutableIntStateOf(0)
}

internal val LocalGridScrolls = staticCompositionLocalOf<GridScrolls?> { null }

/** Tells [GridPages] while [gridState] scrolls. */
@Composable
internal fun ReportGridScroll(gridState: LazyGridState) {
    val scrolls = LocalGridScrolls.current ?: return
    LaunchedEffect(scrolls, gridState) {
        var counted = false
        try {
            snapshotFlow { gridState.isScrollInProgress }.collect { now ->
                if (now != counted) scrolls.active += if (now) 1 else -1
                counted = now
            }
        } finally {
            if (counted) scrolls.active -= 1
        }
    }
}

/**
 * A grid kept built beside the shown one goes back to where a new grid opens ([opening], the first row index that shows the focus) when it
 * stops being shown, so showing it again finds its focus on screen however a finger scrolled it. Nothing when it already stands there.
 */
@Composable
internal fun ResetScrollWhenHidden(
    gridState: LazyGridState,
    opening: () -> Int,
) {
    val shown = LocalGridPageShown.current
    val latest = rememberUpdatedState(opening)
    LaunchedEffect(gridState, shown) {
        var was = shown()
        snapshotFlow { shown() }.collect { now ->
            if (was && !now) {
                val at = latest.value()
                if (gridState.firstVisibleItemIndex != at || gridState.firstVisibleItemScrollOffset != 0) gridState.scrollToItem(at)
            }
            was = now
        }
    }
}

/** More than the focus reaches past a cover (`FocusLift.reach`, the lifted cover's growth). */
private val GRID_PAGE_ROOM = GRID_PAGE_ROOM_DP.dp

private const val GRID_PAGE = "grid"

/** [GRID_PAGE_ROOM] in dp (read by `GridPageRoomTest`). */
internal const val GRID_PAGE_ROOM_DP = 48
