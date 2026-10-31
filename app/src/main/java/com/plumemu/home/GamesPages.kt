package com.plumemu.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListPrefetchScope
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.components.GameRowLook
import com.plumemu.coreui.components.rememberGlidingPill
import com.plumemu.coreui.theme.rememberListGlide
import kotlin.math.abs

/**
 * The games list as pages, one per entry, in a row that never scrolls by hand; the page of entry
 * [shownIndex] is shown, jumped to while composing the frame the focus changes in (as
 * [keepVisible] does), so it is in that frame's layout.
 *
 * Building a page (its list and the dozen rows on screen) inside that frame made it miss the
 * Thor's 8.3 ms at 120 Hz (build 129). So the pages beside the shown one are built ahead, in the
 * idle time between frames ([NeighbourPagesPrefetch]), and kept while they are beside it (pinned):
 * a one-step change places a page that is ready and keeps the page it left. Entries shown as a
 * grid get no list page. Each entry's scroll position is held in [positions], which the screen
 * keeps beside the strip's focus ([rememberGamesPagePositions] in HomeScreen), so a list comes
 * back where it was left even after these pages left the composition (see [GamesPagePositions]).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GamesPages(
    content: HomeContent,
    shownIndex: Int,
    gridIds: Set<String>,
    state: StripState,
    onAction: (Action) -> Unit,
    panelOpen: State<Boolean>,
    positions: GamesPagePositions,
    showsPlace: Boolean,
    showSystemInfo: Boolean,
    modifier: Modifier,
) {
    val prefetch = remember { NeighbourPagesPrefetch() }
    val keys = remember(content) { content.entries.map { it.id } }
    val listKeys = remember(content, gridIds) { content.entries.filterNot { content.showsGrid(it, gridIds) }.mapTo(HashSet()) { it.id } }
    // Read by the prefetcher after this frame's layout, which uses the same entries.
    SideEffect {
        prefetch.pageKeys = keys
        prefetch.listKeys = listKeys
    }
    val pages = rememberLazyListState(initialFirstVisibleItemIndex = shownIndex, prefetchStrategy = prefetch)
    remember(pages) { pages.also { gamesPagesStateMade?.invoke(it) } }
    // Read without observing: a layout of the pages must not recompose this.
    Snapshot.withoutReadObservation {
        if (pages.firstVisibleItemIndex != shownIndex || pages.firstVisibleItemScrollOffset != 0) pages.requestScrollToItem(shownIndex)
    }
    val probe = remember(pages, prefetch) { GamesPagesProbe(pages, { prefetch.ahead }, { prefetch.pending }) }
    LazyRow(
        state = pages,
        modifier = modifier.semantics { this[GamesPagesInfo] = probe },
        userScrollEnabled = false,
    ) {
        itemsIndexed(content.entries, key = { _, entry -> entry.id }, contentType = { _, _ -> GAMES_PAGE }) { index, entry ->
            GamesPage(index, entry, prefetch, content.hasBoxArt, showsPlace, showSystemInfo, state, positions, onAction, panelOpen)
        }
    }
}

/**
 * One page: pinned while it is the shown entry's, or beside it and built ([NeighbourPagesPrefetch.isReady]),
 * so the lazy row keeps it built; placed, and its rows in the semantics tree, only while shown.
 * Pinning a page that is not built yet makes the lazy row compose it in the frame of the next
 * layout, so a neighbour is pinned only after the prefetcher finished it (or it was shown).
 */
@Composable
private fun LazyItemScope.GamesPage(
    index: Int,
    entry: HomeEntry,
    prefetch: NeighbourPagesPrefetch,
    hasBoxArt: Boolean,
    showsPlace: Boolean,
    showSystemInfo: Boolean,
    state: StripState,
    positions: GamesPagePositions,
    onAction: (Action) -> Unit,
    panelOpen: State<Boolean>,
) {
    val key = entry.id
    val distance = { abs(state.focus.systemIndex - index) }
    val isNear by remember(state, index) { derivedStateOf { distance() <= 1 } }
    val isPinned by remember(state, index, prefetch, key) { derivedStateOf { distance().let { it == 0 || (it == 1 && prefetch.isReady(key)) } } }
    val container = LocalPinnableContainer.current
    val hold = remember(container) { PageHold(container) }
    DisposableEffect(hold, isPinned) {
        if (isPinned) hold.pin()
        onDispose {}
    }
    DisposableEffect(hold, isNear) { onDispose { if (!isNear) hold.release() } }
    DisposableEffect(hold) { onDispose { hold.release() } }
    // A page the layout itself built and measured (a width change shows two at once) is kept at once:
    // it is built, and waiting for the next composition would drop it in this layout pass.
    val keepWhenMeasured = remember(hold, state, index) { { if (Snapshot.withoutReadObservation { distance() <= 1 }) hold.pin() } }
    // Read while placing, not composing: showing a kept page only places it.
    val isShown = remember(state, index) { derivedStateOf { state.focus.systemIndex == index } }
    val shown = remember(isShown) { { isShown.value } }
    val page = remember(shown, keepWhenMeasured) { Modifier.fillParentMaxSize().placedOnlyWhen(shown, keepWhenMeasured).rowsInSemanticsOnlyWhen(shown) }
    EntryGames(entry, hasBoxArt, showsPlace, showSystemInfo, shown, state, positions, onAction, panelOpen, page)
}

/**
 * Measures the content but places it only while [shown]: a page kept beside the shown one stays
 * built and measured, and is neither drawn nor touchable. (Not being placed does not take it out
 * of the semantics tree: [rowsInSemanticsOnlyWhen] does.)
 */
private fun Modifier.placedOnlyWhen(
    shown: () -> Boolean,
    onMeasured: () -> Unit,
): Modifier =
    layout { measurable, constraints ->
        // Measured when the layout measures the page, and also when the prefetcher's premeasure does: this can run for a page
        // that is not shown or pinned yet, so [onMeasured] only pins one that is near.
        onMeasured()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { if (shown()) placeable.place(0, 0) }
    }

/** Keeps this lazy item built, though it is not on screen, from [pin] to [release]. */
private class PageHold(
    private val container: PinnableContainer?,
) {
    private var handle: PinnableContainer.PinnedHandle? = null

    fun pin() {
        if (handle == null) handle = container?.pin()
    }

    fun release() {
        handle?.release()
        handle = null
    }
}

/**
 * One entry's games list: opens where it was left ([positions]), or with its remembered row in
 * view the first time, and keeps the focus in view. Its rows follow the focus even while the page
 * is built ahead and not shown: when it is shown, nothing in it has to change (freezing them cost
 * more, every row recomposed). Only the remembered row can be composed with a look other than
 * Normal ([RowForms]); the others' looks differ in their title colour alone, which is drawn, so
 * Enter and Back recompose one row per page ([GamesListItem]).
 */
@Composable
private fun EntryGames(
    entry: HomeEntry,
    hasBoxArt: Boolean,
    showsPlace: Boolean,
    showSystemInfo: Boolean,
    isShown: () -> Boolean,
    state: StripState,
    positions: GamesPagePositions,
    onAction: (Action) -> Unit,
    panelOpen: State<Boolean>,
    modifier: Modifier,
) {
    val fit = PlumTheme.layout.listRowsThatFit
    // The band is the list's first item (it scrolls with the list): there for
    // a system the platform data knows while Settings › System info is on, whichever column is focused, so
    // nothing appears or goes on Enter or Back and no row moves under a finger. Only the lazy list's item
    // index is offset by [bandItems]; row indexes outside this list stay as they are.
    val info = entry.info.takeIf { showSystemInfo && entry.kind == EntryKind.System }
    val bandItems = if (info != null) 1 else 0
    val listState =
        remember(positions, entry.id) {
            // Read once without observing: the list must not recompose on every row move.
            positions.open(entry.id) {
                val row = Snapshot.withoutReadObservation { state.focus.rowOf(entry.id) }
                // Near the top the list opens at the top (the band shows); deeper, the band is scrolled out.
                if (row < fit) 0 else firstIndexShowing(row, fit) + bandItems
            }
        }
    DisposableEffect(positions, entry.id, listState) {
        positions.attach(entry.id, listState)
        onDispose { positions.detach(entry.id, listState) }
    }
    val glide = rememberListGlide(stride = PlumTheme.layout.gameRowHeight + PlumDimens.listRowGap)
    val forms = remember { RowForms() }
    FollowRowForms(forms, state, entry.id, panelOpen)
    val layout = PlumTheme.layout
    val visibleWidth = layout.width - PlumDimens.stripMarginSide * 2 - layout.systemsColumnWidth - PlumDimens.columnGap
    // The focus pill is drawn by the list, so it glides from row to row; read while drawing.
    val pill =
        rememberGlidingPill(listState, glide, rowHeight = layout.gameRowHeight, stride = layout.gameRowHeight + PlumDimens.listRowGap) {
            val row = state.focus.rowOf(entry.id)
            if (gameLook(state.focus, entry.id, row, panelOpen.value) == GameRowLook.Focused) row + bandItems else -1
        }
    pill.Provide {
        LazyColumn(
            state = listState,
            modifier = modifier.then(pill.modifier).then(glide.modifier),
            contentPadding = PaddingValues(vertical = glide.extra),
            // No spacing: each game row carries its own gap (its tap area).
        ) {
            if (info != null) item(key = SYSTEM_BAND_KEY, contentType = SYSTEM_BAND_KEY) { SystemInfoBand(info, visibleWidth) }
            itemsIndexed(entry.rows, key = { _, row -> row.id }) { index, row ->
                GamesListItem(index, row, entry.id, hasBoxArt, entry.kind.mixesSystems, showsPlace, isShown, forms, state, onAction, panelOpen)
            }
        }
    }
    // The first row is brought in with the band above it, so the list shows its top.
    KeepFocusedVisible(listState, glide) { state.focus.rowOf(entry.id).let { if (it == 0) 0 else it + bandItems } }
    FollowArt(entry.rows) { listState.firstVisibleItemIndex - bandItems }
}

/** Where a games list stands: its first visible row and how far that row is scrolled. */
internal data class ListPosition(
    val index: Int,
    val offset: Int,
)

/**
 * Each entry's games list position, by entry id: the live list's while its page is built, the
 * last one seen after the page is dropped. One per main screen ([rememberGamesPagePositions] in
 * HomeScreen, beside the strip's focus), so they outlive the games pages: search, a system shown
 * as a grid, an empty library or Settings take the pages out of the composition and the lists
 * come back where they were. Saved with the screen ([Saver]): a game launch, a rotation or the
 * process being killed in the background keep them; a fresh start of the app does not.
 */
internal class GamesPagePositions(
    saved: Map<String, ListPosition> = emptyMap(),
) {
    private val saved = HashMap(saved)
    private val live = HashMap<String, LazyListState>()

    /** The list state for [entryId]: where it was left, or at [opening] the first time. */
    fun open(
        entryId: String,
        opening: () -> Int,
    ): LazyListState {
        val at = saved[entryId]
        return LazyListState(at?.index ?: opening(), at?.offset ?: 0)
    }

    fun attach(
        entryId: String,
        list: LazyListState,
    ) {
        live[entryId] = list
    }

    fun detach(
        entryId: String,
        list: LazyListState,
    ) {
        if (live[entryId] === list) live.remove(entryId)
        saved[entryId] = list.position()
    }

    /** Every entry's position now: the live lists' current ones over the saved ones. */
    fun all(): Map<String, ListPosition> = saved + live.mapValues { (_, list) -> list.position() }

    private fun LazyListState.position() = ListPosition(firstVisibleItemIndex, firstVisibleItemScrollOffset)

    companion object {
        val Saver =
            listSaver<GamesPagePositions, Any>(
                save = { positions -> positions.all().flatMap { (id, at) -> listOf(id, at.index, at.offset) } },
                restore = { flat -> GamesPagePositions(flat.chunked(3).associate { (id, index, offset) -> id as String to ListPosition(index as Int, offset as Int) }) },
            )
    }
}

/** The main screen's [GamesPagePositions], saved with it. */
@Composable
internal fun rememberGamesPagePositions(): GamesPagePositions = rememberSaveable(saver = GamesPagePositions.Saver) { GamesPagePositions() }

/**
 * Asks Compose's prefetcher to build the list pages beside the shown one in the idle time between
 * frames, and cancels any other it asked for. Pages are tracked by entry id ([pageKeys], in page
 * order; only [listKeys] get a list page), so entries added, removed or hidden (a rescan, Show
 * Android apps) never leave a stale page or cancel a ready one.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class NeighbourPagesPrefetch : LazyListPrefetchStrategy {
    var pageKeys: List<String> = emptyList()
    var listKeys: Set<String> = emptySet()
    private val pages = PagesAhead<String, LazyLayoutPrefetchState.PrefetchHandle> { it.cancel() }
    private val asked = mutableStateSetOf<String>()

    /** Pages asked for whose request is not finished (tests read it through [GamesPagesInfo]). */
    val pending: Set<String> get() = asked.toSet()

    /**
     * False for a page the prefetcher is still building: pinning it would make the lazy row build
     * the rest in the next layout, inside a frame. A page the layout built itself (a fast hold,
     * a width change) is not asked for, so it counts as built. Read by a page's pin decision, so
     * it pins when the request finishes.
     */
    fun isReady(key: String): Boolean = key !in asked

    /** The pages built or being built beside the shown one (tests read it through [GamesPagesInfo]). */
    val ahead: Set<String> get() = pages.ahead

    override fun LazyListPrefetchScope.onScroll(
        delta: Float,
        layoutInfo: LazyListLayoutInfo,
    ) = Unit

    override fun LazyListPrefetchScope.onVisibleItemsUpdated(layoutInfo: LazyListLayoutInfo) {
        // Pinned pages are laid out beside the shown one; the shown one is at the start.
        val shown = layoutInfo.visibleItemsInfo.minByOrNull { abs(it.offset - layoutInfo.viewportStartOffset) }?.key as? String ?: return
        val keys = pageKeys
        // Read without observing: the pages' layout must not run again for a change of this set.
        Snapshot.withoutReadObservation {
            pages.show(shown, keys, canBuild = { it in listKeys }) { index ->
                val key = keys[index]
                asked.add(key)
                // Done in idle time; a request cancelled before then never reports.
                schedulePrefetch(index) { asked.remove(key) }
            }
            asked.filter { it !in pages.ahead }.forEach { asked.remove(it) }
        }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) = Unit
}

/**
 * Which pages are built ahead, apart from Compose so it can be tested: [K] is a page's key, [H] a
 * build request's handle, given back to [cancel] when its page is no longer beside the shown one.
 * The page that was shown is kept without a request when it becomes a neighbour (it is built).
 */
internal class PagesAhead<K, H : Any>(
    private val cancel: (H) -> Unit,
) {
    /** Pages beside the shown one: the request that builds it, or null for a page already built (shown before). */
    private val pages = LinkedHashMap<K, H?>()
    private var shown: K? = null

    val ahead: Set<K> get() = pages.keys.toSet()

    /** Page [shownKey] is on screen among [keys]: ask with [schedule] (by index) for its neighbours not asked for yet, cancel the rest. */
    fun show(
        shownKey: K,
        keys: List<K>,
        canBuild: (K) -> Boolean = { true },
        schedule: (index: Int) -> H,
    ) {
        val at = keys.indexOf(shownKey)
        val wanted = if (at < 0) emptyMap() else listOf(at - 1, at + 1).filter { it in keys.indices && canBuild(keys[it]) }.associateBy { keys[it] }
        val left = shown
        shown = shownKey
        // The shown page used what was built for it: forget its request without cancelling.
        pages.remove(shownKey)
        pages.keys.filter { it !in wanted }.forEach { key -> pages.remove(key)?.let(cancel) }
        wanted.forEach { (key, index) ->
            when {
                key in pages -> Unit
                key == left -> pages[key] = null
                else -> pages[key] = schedule(index)
            }
        }
    }
}

private const val GAMES_PAGE = "games"
private const val SYSTEM_BAND_KEY = "system-band"

/**
 * Test seam: called once with the pages' list state right after it is made, before its lazy row is
 * composed, so a test can give it a prefetch scheduler it runs itself (Robolectric gives Compose's
 * own none idle time). Null in the app.
 */
internal var gamesPagesStateMade: ((LazyListState) -> Unit)? = null

/** What tests read from the games pages: the pages' list state (one for the screen's life), the pages kept beside the shown one and those of them still being built. */
internal class GamesPagesProbe(
    val pages: LazyListState,
    val ahead: () -> Set<String>,
    val pending: () -> Set<String>,
)

/**
 * The games pages' [GamesPagesProbe], for tests. Robolectric gives Compose's prefetcher no idle
 * time, so no page is built ahead there; tests check what is asked for and kept, and
 * SystemSwitchBenchmark measures the frames on a device.
 */
internal val GamesPagesInfo = SemanticsPropertyKey<GamesPagesProbe>("GamesPagesInfo")
