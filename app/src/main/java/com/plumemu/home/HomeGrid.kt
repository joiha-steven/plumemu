package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.collar
import com.plumemu.coreui.components.coverBoxShadow
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.FocusLift
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.TouchPressScale
import com.plumemu.coreui.components.rememberConfirmPressScale
import com.plumemu.coreui.components.rememberTouchPressScale
import com.plumemu.coreui.components.listEdgeFadeMask
import com.plumemu.coreui.theme.ContentFade
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.LocalLaunchOrigin
import com.plumemu.coreui.theme.rememberSlidingSwap
import com.plumemu.coreui.theme.rememberListGlide

/**
 * The grid view of the focused system, Recent or Favorites (docs/design/rules.md › Grid; only with
 * box art, Apps' grid of icons is [AppsGrid]): covers packed in rows ([packRows]), each box as wide
 * as its own shape (its saved picture's, else its system's, [coverAspect]) lets the row, which spans the grid area from where the
 * list starts to the right margin. The Size step picks the column count; rows that do not fit are cut at the strip's edge, as on the board.
 *
 * The focused cover grows ([FocusLift]) about a pivot that keeps it inside the grid; its rim, light and shadow are drawn round the grid,
 * at the cover's place in the lazy layout, rather than by the cover: they reach past the cover, which the grid's own clip would cut at
 * the margins and the top and bottom rows, and a move then only redraws (no cover recomposes). The strip lets them into the bar gaps.
 */
@Composable
internal fun GamesGrid(
    content: HomeContent,
    state: StripState,
    onAction: (Action) -> Unit,
    modifier: Modifier,
    isPanelOpen: Boolean = false,
) {
    val entryIndex by remember(state) { derivedStateOf { state.focus.systemIndex } }
    // The game panel has the focus: the cover drops its focus (one focus mark on screen).
    val panelOpen = rememberUpdatedState(isPanelOpen)
    val entry = content.entries.getOrNull(entryIndex) ?: return
    // Apps' grid is of icons at one fixed size, without box art.
    if (entry.kind == EntryKind.Apps || entry.isAppGamesGrid) {
        AppsGrid(entry, state, onAction, panelOpen, modifier)
        return
    }
    val layout = PlumTheme.layout
    val shape = entry.coverShape
    val gap = layout.gridGap(shape)
    val rowGap = layout.gridRowGap(shape)
    // The Size step's column count and the height of a box of the system's shape at it: the target of a mixed grid's rows.
    val columns = layout.gridColumns(shape)
    val targetHeight = layout.gridRowHeight(shape)
    val coversWidth = layout.gridCoversWidth
    val density = LocalDensity.current
    val titleStyle = PlumTheme.type.coverTitle(layout.gridSize)
    val art = LocalHomeArt.current
    // The rows as packed: one height, each box as wide as its own shape. Read in lazy and draw scopes only, so a result
    // landing re-packs without recomposing the grid; the focus rules get the same table (setPackedRows below).
    val packed = rememberPackedGrid(entry, columns, targetHeight, gap, coversWidth, art)
    // L1/R1 with the games focused: the next system's games fade in instead of popping. Moves in
    // the system column swap the preview at once (a held key would strobe it).
    val fade = rememberSlidingSwap(entry.id, { state.focus.systemIndex }, wrapsAt = content.entries.size) { fadesSystemSwap(state) }
    // A new grid size (or text size) or width re-packs but keeps the grid, its scroll state and the cells that stay in their row:
    // keyed on the system alone, the boxes change size by layout and the cells are not rebuilt (every rebuilt cell asks for its
    // picture in the same frame). The same game stays focused (the focus is its index) and [AlignOnRepack] shows its row whole.
    key(entry.id) {
        // Where each row stands in px, from the packing: rows differ in height, so no scroll rule counts rows of one height.
        val spans = remember(packed, rowGap, density) { derivedStateOf { RowSpans.of(packed.value, rowGap, density) } }
        val stripHeight = layout.gridStripHeight + LocalBarRoom.current.taller
        val stripPx = with(density) { stripHeight.roundToPx() }
        // Read once without observing: the grid must not recompose on every move.
        val opening = Snapshot.withoutReadObservation { state.focus.rowOf(entry.id) }
        val openingRow = Snapshot.withoutReadObservation { packed.value.rowOf(opening) }
        // Where the grid opens is read once, not observed: a picture landing re-packs the rows and must not recompose the grid.
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = Snapshot.withoutReadObservation { spans.value.firstShowing(openingRow, stripPx) })
        val viewport = rememberGridViewportPx(gridState, stripHeight, PlumDimens.gridTopGap, LocalBarRoom.current.bottom + PlumDimens.gridBottomRoom)
        AlignOnRepack(gridState, spans, stripPx, columns, targetHeight, coversWidth) { packed.value.rowOf(state.focus.rowOf(entry.id)) }
        val glide = rememberListGlide(stride = targetHeight * ROW_SCALE_ROOM + rowGap, slabRoom = false)
        // The focus's rim, light and shadow are drawn here, round the grid, so the grid's clip does not cut them at the margins and bars.
        val motionOn = rememberUpdatedState(PlumTheme.motion.isOn)
        val openingFocus = Snapshot.withoutReadObservation { if (state.focus.column == Column.Games && !panelOpen.value) opening else NO_COVER }
        // A finger's press on a cover: one dip for the grid, drawn by the cell (the focused one too, with the confirm button's).
        val touch = rememberTouchPressScale()
        // Pressed (rules.md > Shapes and motion > Pressed): the focused cover dips about its centre while the confirm button is held.
        val pressScale = rememberConfirmPressScale {
            val focus = state.focus
            if (focus.column == Column.Games && !panelOpen.value) focus.rowOf(entry.id) else null
        }
        val outline = rememberFocusOutline(openingFocus, pressScale) { motionOn.value }
        val halo = focusLift(entry, gridState, glide, outline, panelOpen, packed, touch, state)
        val measurer = rememberTextMeasurer(cacheSize = 64)
        // Remembered: a new fitter would recompose every tile that reads it.
        val fitter = LocalTileTitleFit.current ?: remember(measurer) { TileTitleFit(measurer) }
        // The focus rules move by this table, asked at each move (never a frame behind). Set while composing, so a new pack
        // (size, text, width) takes over with no frame in between; it goes when this grid does, unless a newer one has taken over.
        val source = remember(state, entry, packed) { { packed.value.toRows(entry.rows.map { it.id }) } }
        state.setPackedRowsSource(entry.id, source)
        DisposableEffect(state, source) { onDispose { state.clearPackedRowsSource(entry.id, source) } }
        CompositionLocalProvider(LocalTileTitleFit provides fitter) {
        // Under the focus's rim: the focused cover is drawn over the mask, a row showing too little of itself at a bar is left out.
        val footerGapPx = with(density) { (PlumDimens.footerGap - LocalBarRoom.current.bottom).coerceAtLeast(0.dp).toPx() }
        val fades = remember(gridState, glide, state, entry.id, packed, footerGapPx) { GridEdgeFades(gridState, glide, with(density) { PlumDimens.gridTopGap.roundToPx().toFloat() }, liftShare = FocusLift.GROWTH_SHARE, ringPx = with(density) { FocusLift.reach.toPx() }, bottomGapPx = footerGapPx) { packed.value.rowOf(state.focus.rowOf(entry.id)) } }
        FollowBarRunner(fades)
        GridRows(modifier, entry, gridState, packed, spans, viewport, glide, rowGap, density, titleStyle, state, onAction, touch, outline, fade, halo, fades)
        }
        KeepFocusedCellVisible(gridState, 1, spans, viewport, glide) { packed.value.rowOf(state.focus.rowOf(entry.id)) }
        FollowArt(entry.rows) { packed.value.rows.getOrNull(gridState.firstVisibleItemIndex)?.start ?: 0 }
    }
}

/**
 * The lazy grid, one lazy item per packed row (it has one column): its covers stand at the packing's places. Its own scope reads the
 * leftover under the last row, which moves once the grid is measured (the strip's height stands in until then): that recomposes this
 * scope alone, not [GamesGrid] and its focus, glide and halo state.
 */
@Composable
private fun GridRows(
    modifier: Modifier,
    entry: HomeEntry,
    gridState: LazyGridState,
    packed: State<PackedGrid>,
    spans: State<RowSpans>,
    viewport: State<Int>,
    glide: ListGlide,
    rowGap: Dp,
    density: Density,
    titleStyle: TextStyle,
    state: StripState,
    onAction: (Action) -> Unit,
    touch: TouchPressScale,
    outline: FocusOutline,
    fade: ContentFade,
    halo: Modifier,
    fades: GridEdgeFades,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        state = gridState,
        flingBehavior = rememberRowSnapFling(gridState),
        modifier = modifier.fadingIn(fade).then(halo).listEdgeFadeMask(fades, true).testTag(HomeTags.GRID).then(glide.modifier),
        contentPadding = PaddingValues(top = glide.extra + PlumDimens.gridTopGap, bottom = glide.extra + LocalBarRoom.current.bottom + PlumDimens.gridBottomRoom + rememberGridLeftover(spans, viewport)),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        val rows = packed.value.rows
        items(rows.size, key = { entry.rows[rows[it].start].id }) { at ->
            val row = rows[at]
            PackedRowBoxes(entry, row, at, density, titleStyle, state, onAction, touch, outline)
        }
    }
}

/**
 * A Size, text-size or width change packs again under the same grid: the scroll was by rows of the old packing, so it is set again to
 * the first row that shows the focused game's row whole. Not on the first composition, and not when only a picture's shape landed.
 */
@Composable
private fun AlignOnRepack(
    gridState: LazyGridState,
    spans: State<RowSpans>,
    windowPx: Int,
    columns: Int,
    targetHeight: Dp,
    coversWidth: Dp,
    focusedRow: () -> Int,
) {
    val latest = rememberUpdatedState(focusedRow)
    val isFirst = remember { booleanArrayOf(true) }
    LaunchedEffect(columns, targetHeight, coversWidth, windowPx) {
        if (isFirst[0]) {
            isFirst[0] = false
            return@LaunchedEffect
        }
        gridState.scrollToItem(spans.value.firstShowing(latest.value(), windowPx), 0)
    }
}

/** One packed row: its boxes at the packing's places, each edge snapped to whole px. */
@Composable
private fun PackedRowBoxes(
    entry: HomeEntry,
    row: PackedRow,
    rowIndex: Int,
    density: Density,
    titleStyle: TextStyle,
    state: StripState,
    onAction: (Action) -> Unit,
    touch: TouchPressScale,
    outline: FocusOutline,
) {
    Box(Modifier.fillMaxWidth().height(row.height.dp)) {
        row.boxes.forEachIndexed { k, box ->
            val index = row.start + k
            key(index) {
                // The gap between two boxes is the board's rounded down or up, never a px short on both sides.
                val left = with(density) { box.x.dp.roundToPx() }
                val right = with(density) { (box.x + box.width).dp.roundToPx() }
                // The cover is told the box's own width and the snapped width is imposed in layout: a box moving by a fraction of a px
                // may snap a px wider or narrower, which must not compose the cover again.
                Box(Modifier.offset { IntOffset(left, 0) }.snappedWidth(right - left)) {
                    GridCoverAt(entry, index, DpSize(box.width.dp, row.height.dp), liftEdges(k, row.boxes.size, rowIndex), titleStyle, state, onAction, touch, outline)
                }
            }
        }
    }
}

/** Measures the content at exactly [widthPx] wide, whatever width it asked for. */
private fun Modifier.snappedWidth(widthPx: Int): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/** The cover grid's packing as derived state: re-packs when a picture changes a game's shape, a size does or the width does; art of a stored shape moves nothing, and a stored shape written during the visit waits for the next one ([HeldArtAspects]). */
@Composable
private fun rememberPackedGrid(
    entry: HomeEntry,
    columns: Int,
    rowHeight: Dp,
    gap: Dp,
    coversWidth: Dp,
    art: HomeArt,
): State<PackedGrid> {
    // Keyed on the system alone: a new list of the same system is the same visit.
    val held = remember(entry.id) { HeldArtAspects(entry.rows) }
    return remember(entry, columns, rowHeight, coversWidth, gap, art) {
        held.follow(entry.rows)
        derivedStateOf(structuralEqualityPolicy()) {
            packRows(
                entry.rows.map { coverAspect(entry, it, art, held) },
                columns,
                rowHeight.value,
                gap.value,
                coversWidth.value,
                (CollarSize.Cover.sidePadding * 2).value,
                (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value,
            )
        }
    }
}

/** The shape game [row] is packed at, [coverBoxAspect]'s order; the system's for a poster, an app card, a cover with no shape known yet. */
private fun coverAspect(
    entry: HomeEntry,
    row: GameRowModel,
    art: HomeArt,
    held: HeldArtAspects,
): Float {
    val slot = art.slot(row.scrapeKey)
    val saved = (slot as? ArtSlot.Found)?.art
    val system = entry.shapeOf(row).widthOverHeight
    return coverBoxAspect(slot, saved?.grid ?: saved?.large, held.of(row), system) ?: system
}

/** One game's cover in its box ([cell]): taps, press dip and focus come from the grid; reads nothing in composition. */
@Composable
private fun GridCoverAt(
    entry: HomeEntry,
    index: Int,
    cell: DpSize,
    edges: LiftEdges,
    titleStyle: TextStyle,
    state: StripState,
    onAction: (Action) -> Unit,
    touch: TouchPressScale,
    outline: FocusOutline,
) {
    val row = entry.rows[index]
    val art = LocalHomeArt.current
    LocalGridCellProbe.current?.let { probe -> remember { probe.onCreated(index).let { index } } }
    val tap = remember(index, onAction) { Modifier.tapItem(Column.Games, index, onAction).testTag(HomeTags.cover(index)) }
    // Read in the layer, not in composition: a move re-runs two covers' layers, nothing recomposes.
    val focused =
        remember(state, entry.id, index, touch, cell, edges, art) {
            touch.modifier(index).drawWithContent {
                // The focused cover dips with the confirm button as well (a finger's dip is the grid's one) and is lifted by the focus
                // about the pivot that keeps it inside the grid; its rim, light and shadow are the grid's layer ([focusLift]).
                val dip = outline.dipOf(index, touch)
                val lift = outline.liftOf(index)
                val pivot = if (lift > 0f) pivotOf(coverFrameOf(entry, index, art, Rect(Offset.Zero, size), cell), edges) else center
                drawLifted(dip, center, lift, pivot) { this@drawWithContent.drawContent() }
            }
        }
    // A game taken out of Favorites stays in the held grid until the person leaves it: dimmed, where the list drops the star.
    val dimmed = entry.kind == EntryKind.Favorites && !row.isFavorite
    GridCover(row, art.slot(row.scrapeKey), entry.shapeOf(row), entry.posterOf(row), cell, titleStyle, tap, focused, outline, index, fitsShape = entry.kind.mixesSystems, dimmed = dimmed)
}

/**
 * The collar of a grid cover: [inner] is the room for its box inside a cell of [cell], after the collar's lip and groove on each side.
 */
internal fun coverInner(cell: DpSize): DpSize =
    DpSize(cell.width - CollarSize.Cover.sidePadding * 2, cell.height - CollarSize.Cover.topPadding - CollarSize.Cover.bottomPadding)

/**
 * The outer edge of the collar of the cover in [cell] (px): the box of [aspect] (null: the cell's whole inner room) fitted inside
 * the collar's padding and bottom-centred, plus that padding, as [GridCover] lays it out.
 */
internal fun Density.collarRect(
    aspect: Float?,
    cell: Rect,
): Rect {
    val side = CollarSize.Cover.sidePadding.toPx()
    val top = CollarSize.Cover.topPadding.toPx()
    val bottom = CollarSize.Cover.bottomPadding.toPx()
    val inner = Rect(cell.left + side, cell.top + top, cell.right - side, cell.bottom - bottom)
    val box = if (aspect != null) shelfRect(aspect, inner) else inner
    return Rect(box.left - side, box.top - top, box.right + side, box.bottom + bottom)
}

/** The pivot a cover of [frame] with [edges] is lifted about ([FocusLift.pivot]). */
private fun pivotOf(
    frame: Rect,
    edges: LiftEdges,
): Offset = FocusLift.pivot(frame, edges.atLeft, edges.atRight, edges.atTop)

/** The collar frame of game [index] in [cell] (px; the box's size [cellDp]): its saved picture's shape, or its system's in a mixed grid, else the whole inner room. */
private fun Density.coverFrameOf(
    entry: HomeEntry,
    index: Int,
    art: HomeArt,
    cell: Rect,
    cellDp: DpSize,
): Rect {
    val row = entry.rows.getOrNull(index)
    val rowShape = row?.let(entry::shapeOf) ?: entry.coverShape
    val picture = (art.slot(row?.scrapeKey) as? ArtSlot.Found)?.art?.gridPicture(coverInner(cellDp), this)
    val aspect = picture?.aspect ?: if (entry.kind.mixesSystems) rowShape.widthOverHeight else null
    return collarRect(aspect, cell)
}

/**
 * The focus of the grid's covers: the rim, light and shadow of each lifted cover, drawn round the grid ([focusLiftLayer]) at the cover's
 * place in the lazy layout, the frame the cover itself draws in ([GridCoverAt]). Reads the focus, the animations and the scroll while
 * drawing: a move or a scroll redraws, nothing recomposes.
 */
@Composable
private fun focusLift(
    entry: HomeEntry,
    gridState: LazyGridState,
    glide: ListGlide,
    outline: FocusOutline,
    panelOpen: State<Boolean>,
    packed: State<PackedGrid>,
    touch: TouchPressScale,
    state: StripState,
): Modifier {
    val colors = PlumTheme.colors
    val art = LocalHomeArt.current
    FollowFocus(state, entry.id, panelOpen, outline)
    val origin = LocalLaunchOrigin.current
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val placement: DrawScope.(Int) -> LiftPlacement? = place@{ index ->
        val grid = packed.value
        val box = grid.boxOf(index) ?: return@place null
        val rowIndex = grid.rowOf(index)
        val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == rowIndex } ?: return@place null
        // Follows its cover while the rows glide, on the same whole px the cover is laid out on.
        val left = box.x.dp.roundToPx()
        val width = (box.x + box.width).dp.roundToPx() - left
        val cell = Rect(Offset(item.offset.x + left.toFloat(), item.offset.y + PlumDimens.gridTopGap.toPx() + glide.offset), Size(width.toFloat(), item.size.height.toFloat()))
        val frame = coverFrameOf(entry, index, art, cell, DpSize(box.width.dp, grid.rows[rowIndex].height.dp))
        val corner = CollarSize.Cover.outerCorner(this, CollarDimens.coverBoxCorner.toPx(), frame.size)
        val k = index - grid.rows[rowIndex].start
        LiftPlacement(frame, corner, cell.center, pivotOf(frame, liftEdges(k, grid.rows[rowIndex].boxes.size, rowIndex)))
    }
    return Modifier.onPlaced { placed[0] = it }.then(focusLiftLayer(outline, touch, glide, colors, placement) { origin?.note(placed[0], it) })
}

/**
 * One cover in its collar ([CollarSize.Cover]): the saved box front ([gridPicture]) at its own shape, the title in the placeholder
 * when there is none, the collar hugging the drawn box and bottom-centred in the [cell] so a row's covers stand on one line.
 * [shape] is the game's system's. With [fitsShape] (Recent and Favorites, whose cells are [MixedGridShape]) the placeholder is that
 * shape fitted in the cell like a picture, so a game without box art keeps its system's shape too. [dimmed]: a Favorites game taken
 * out and still shown ([PlumDimens.UNFAVORITED_COVER_ALPHA]). The cover draws itself in place, lifted by the focus; its rim, light and shadow are the grid's ([focusLift]).
 */
@Composable
private fun GridCover(
    row: GameRowModel,
    slot: ArtSlot,
    shape: CoverShape,
    posterSpec: PosterSpec,
    cell: DpSize,
    titleStyle: TextStyle,
    modifier: Modifier,
    focused: Modifier,
    outline: FocusOutline,
    index: Int,
    fitsShape: Boolean = false,
    dimmed: Boolean = false,
) {
    // On the cell, so the collar is dimmed and dips with the box.
    val look = if (dimmed) focused.then(Modifier.graphicsLayer { alpha = PlumDimens.UNFAVORITED_COVER_ALPHA }.semantics { this[CoverDimmed] = true }) else focused
    val landscape = shape.coverClass == CoverClass.Landscape
    val density = LocalDensity.current
    val inner = coverInner(cell)
    val picture = (slot as? ArtSlot.Found)?.art?.gridPicture(inner, density)
    val ownShape = picture == null && fitsShape
    val box = if (picture != null) fitInside(picture.aspect, inner) else if (ownShape) fitInside(shape.widthOverHeight, inner) else inner
    val corner = CollarDimens.coverBoxCorner
    // An app game in a grid with files is a card like its neighbours: its icon on a gradient of the icon's colour (AppGameCover.kt);
    // with no icon it is the poster, as a file game with no box art.
    val appIcon = if (row.appPackage == null) null else rememberAppGameIcon(row, if (ownShape) box else inner)
    val boxAlpha = if (dimmed) PlumDimens.UNFAVORITED_COVER_ALPHA else 1f
    val boxModifier = (if (ownShape) Modifier.testTag(HomeTags.COVER_OWN_SHAPE) else Modifier).coverBoxShadow(corner)
    Box(modifier.size(cell).then(look), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.collar(CollarSize.Cover, corner, halo = false) { 0f }) {
            if (appIcon != null && appIcon != TileIcon.Missing) {
                AppGameCover(posterSpec, row.iconKey, appIcon, box, corner, null, boxAlpha, boxModifier, Modifier)
            } else {
                CoverArt(
                    title = row.title,
                    picture = picture,
                    titleStyle = titleStyle,
                    size = box,
                    alignment = Alignment.BottomCenter,
                    corner = corner,
                    titlePadding = if (landscape) PlumDimens.coverPaddingLandscape else PlumDimens.coverPaddingPortrait,
                    modifier = boxModifier,
                    subtitle = detailsOf(row, slot)?.year?.toString(),
                    // Not while the result is still being read: the plain tile stands until it is known there is no picture.
                    poster = if (slot is ArtSlot.Unknown && appIcon == null) null else posterSpec,
                    tileSystem = posterSpec,
                )
            }
        }
    }
}

/** Set on a Favorites grid cover dimmed because its game was taken out and the list still holds it (read by tests). */
internal val CoverDimmed = SemanticsPropertyKey<Boolean>("CoverDimmed")

/**
 * The saved box front a grid cell of [cell] draws (docs/design/rules.md › Grid): the grid-size
 * file (560x400 at most) while its height covers the picture's drawn height in pixels, else the
 * large one (720 tall at most), so a tall cell (PSP at Large on the Thor: 238 dp, 546 px) is
 * never drawn from a smaller file. Either stands in for the other when only one is saved. The
 * height compared is the one `CoverPicture` decodes at (the focused cover's 2.5 % is left out).
 */
internal fun GameArt.gridPicture(
    cell: DpSize,
    density: Density,
): ArtPicture? {
    val small = grid ?: return large
    val big = large ?: return small
    val drawnHeight = with(density) { fitInside(small.aspect, cell).height.roundToPx() }
    return if (small.height >= drawnHeight) small else big
}

/** Test seam: told when a cover cell of the grid is created (null in the app); a Size change must not create the cells again. */
internal fun interface GridCellProbe {
    fun onCreated(index: Int)
}

internal val LocalGridCellProbe = androidx.compose.runtime.staticCompositionLocalOf<GridCellProbe?> { null }
