package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One cover to decode ahead: [picture] at [widthPx] x [heightPx], as the grid's cover will ask for it. */
internal data class CoverAsk(
    val picture: ArtPicture,
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * The first screen of covers of the systems either side of the shown one, decoded at the current grid size while the person looks at
 * this one, so a system switch finds them in memory (no titled tile, no fade, no decode landing in the frames after the press).
 * Waits [START_DELAY_MS] for the shown grid's own decodes, asks at most [COVERS_PER_SYSTEM] covers of each neighbour (so at most twice
 * that stay in the decoder's bounded cache), is cancelled by any change of the shown system or the size, and ends when done: nothing
 * runs at rest. Part of `HomeStrip`, beside the grid.
 */
@Composable
internal fun PrefetchNeighbourCovers(
    content: HomeContent,
    state: StripState,
    gridIds: Set<String>,
) {
    val layout = PlumTheme.layout
    val density = LocalDensity.current
    val art = LocalHomeArt.current
    if (!art.isLive) return
    val latestContent = rememberUpdatedState(content)
    val latestGridIds = rememberUpdatedState(gridIds)
    LaunchedEffect(art, layout, density) {
        prefetchWhileShown(
            shown = snapshotFlow { state.focus.systemIndex },
            content = { latestContent.value },
            isGrid = { latestContent.value.showsGrid(it, latestGridIds.value) },
            openingOf = { state.focus.rowOf(it.id) },
            art = art,
            layout = layout,
            density = density,
        )
    }
}

/**
 * For each system [shown] reports: wait [startDelayMs], then decode the first screen of the neighbouring grids ([neighbourEntries]).
 * A new system cancels the work in progress; when it has run out nothing is left running.
 */
internal suspend fun prefetchWhileShown(
    shown: Flow<Int>,
    content: () -> HomeContent,
    isGrid: (HomeEntry) -> Boolean,
    openingOf: (HomeEntry) -> Int,
    art: HomeArt,
    layout: PlumLayout,
    density: Density,
    startDelayMs: Long = START_DELAY_MS,
) {
    shown
        .distinctUntilChanged()
        .map { index -> neighbourEntries(content(), index, isGrid) }
        .collectLatest { neighbours ->
            delay(startDelayMs)
            for (entry in neighbours) {
                val opening = openingOf(entry)
                art.prefetchSlots(windowKeys(entry, opening))
                val asks = homeTrace("Plum:coverAsks") { coverAsks(entry, opening, { (art.slot(it.scrapeKey) as? ArtSlot.Found)?.art }, layout, density) }
                for (ask in asks) {
                    // The glow colours too: a cover found in memory on the switch would otherwise learn its colours in the frames after the press.
                    val bitmap = art.images.cached(ask.picture, ask.widthPx, ask.heightPx) ?: art.images.load(ask.picture, ask.widthPx, ask.heightPx)
                    if (bitmap != null) art.glowColors.learnSoon(ask.picture, bitmap)
                }
            }
        }
}

/** The grid entries to decode ahead: the nearest one after and the nearest one before entry [index] (within [REACH] places), when they are not the shown one. */
internal fun neighbourEntries(
    content: HomeContent,
    index: Int,
    isGrid: (HomeEntry) -> Boolean,
): List<HomeEntry> {
    val entries = content.entries
    if (entries.size < 2) return emptyList()
    fun nearest(step: Int): HomeEntry? =
        (1..REACH).firstNotNullOfOrNull { offset -> entries.getOrNull(Math.floorMod(index + step * offset, entries.size))?.takeIf { it.rows.isNotEmpty() && it.kind != EntryKind.Apps && !it.isAppGamesGrid && isGrid(it) } }
    return listOfNotNull(nearest(1), nearest(-1)).filter { it !== entries.getOrNull(index) }.distinct()
}

/** The scrape keys whose results the first screen of [entry]'s grid needs, opening at row [opening]: the rows before it count (they shape the packing). */
internal fun windowKeys(
    entry: HomeEntry,
    opening: Int,
): List<String> = entry.rows.take(opening.coerceAtLeast(0) + COVERS_PER_SYSTEM).mapNotNull { it.scrapeKey }

/**
 * The covers the first screen of [entry]'s grid draws, opening at game [opening]: packed as the grid packs them ([packRows], from the
 * saved pictures' shapes in [artOf]) and sized as a cover is decoded (its fitted box in px). At most [COVERS_PER_SYSTEM], none for a game
 * without a saved picture.
 */
internal fun coverAsks(
    entry: HomeEntry,
    opening: Int,
    artOf: (GameRowModel) -> GameArt?,
    layout: PlumLayout,
    density: Density,
): List<CoverAsk> {
    val shape = entry.coverShape
    val rows = entry.rows.take(opening.coerceAtLeast(0) + COVERS_PER_SYSTEM)
    val packed =
        packRows(
            rows.map { row -> artOf(row)?.let { (it.grid ?: it.large)?.aspect } ?: entry.shapeOf(row).widthOverHeight },
            layout.gridColumns(shape),
            layout.gridRowHeight(shape).value,
            layout.gridGap(shape).value,
            layout.gridCoversWidth.value,
            (CollarSize.Cover.sidePadding * 2).value,
            (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value,
        )
    val from = packed.rows.getOrNull(packed.rowOf(opening))?.start ?: 0
    val asks = ArrayList<CoverAsk>(COVERS_PER_SYSTEM)
    for (index in from until rows.size) {
        if (asks.size >= COVERS_PER_SYSTEM) break
        val box = packed.boxOf(index) ?: continue
        val inner = coverInner(DpSize(box.width.dp, packed.rows[packed.rowOf(index)].height.dp))
        val picture = artOf(rows[index])?.gridPicture(inner, density) ?: continue
        val fitted = fitInside(picture.aspect, inner)
        with(density) { asks += CoverAsk(picture, fitted.width.roundToPx().coerceAtLeast(1), fitted.height.roundToPx().coerceAtLeast(1)) }
    }
    return asks
}

/** Covers asked ahead for each neighbouring system: about two screens of a grid. */
internal const val COVERS_PER_SYSTEM = 24

/** The wait before the neighbours' covers are asked, so the shown grid's own decodes go first. */
internal const val START_DELAY_MS = 300L

private const val REACH = 3
