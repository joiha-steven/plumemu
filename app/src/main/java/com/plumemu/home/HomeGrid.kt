package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
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
import com.plumemu.coreui.theme.rememberContentFade
import com.plumemu.coreui.theme.rememberListGlide

/**
 * The grid view of the focused system, Recent or Favorites (docs/design/rules.md › Grid; only with
 * box art, Apps' grid of icons is [AppsGrid]): covers packed in rows ([packRows]), each box as wide
 * as its own shape (its saved picture's, else its system's, [coverAspect]) lets the row, which spans the grid area from where the
 * list starts to the right margin. The Size step picks the column count; rows that do not fit are cut at the strip's edge, as on the board.
 *
 * The focused cover's ring is drawn over the grid, at the cover's place in the lazy layout,
 * rather than by the cover: the ring reaches 5.5 dp past the cover, which the grid's own clip
 * would cut at the top and bottom rows, and a move then only redraws (no cover recomposes).
 * The strip lets the ring into the bar gaps.
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
    // The game panel has the focus: the cover drops its ring (one focus mark on screen).
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
    val fade = rememberContentFade(entry.id) { fadesSystemSwap(state) }
    // A new grid size (or text size) or width re-packs but keeps the grid, its scroll state and the cells that stay in their row:
    // keyed on the system alone, the boxes change size by layout and the cells are not rebuilt (every rebuilt cell asks for its
    // picture in the same frame). The same game stays focused (the focus is its index) and [AlignOnRepack] shows its row whole.
    key(entry.id) {
        // Where each row stands in px, from the packing: rows differ in height, so no scroll rule counts rows of one height.
        val spans = remember(packed, rowGap, density) { derivedStateOf { RowSpans.of(packed.value, rowGap, density) } }
        val stripPx = with(density) { layout.gridStripHeight.roundToPx() }
        // Read once without observing: the grid must not recompose on every move.
        val opening = Snapshot.withoutReadObservation { state.focus.rowOf(entry.id) }
        val openingRow = Snapshot.withoutReadObservation { packed.value.rowOf(opening) }
        // Where the grid opens is read once, not observed: a picture landing re-packs the rows and must not recompose the grid.
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = Snapshot.withoutReadObservation { spans.value.firstShowing(openingRow, stripPx) })
        val viewport = rememberGridViewportPx(gridState, layout.gridStripHeight)
        AlignOnRepack(gridState, spans, stripPx, columns, targetHeight, coversWidth) { packed.value.rowOf(state.focus.rowOf(entry.id)) }
        val glide = rememberListGlide(stride = targetHeight * ROW_SCALE_ROOM + rowGap, slabRoom = false)
        // The collar's focus (groove lit) is the lift slot's 0..1 growth, read in the cell's draw block; the halo is drawn
        // here, over the grid, so the grid's clip does not cut it at the top and bottom rows.
        val motionOn = rememberUpdatedState(PlumTheme.motion.isOn)
        val openingFocus = Snapshot.withoutReadObservation { if (state.focus.column == Column.Games && !panelOpen.value) opening else NO_COVER }
        val lift = rememberCoverLift(openingFocus) { motionOn.value }
        // A finger's press on a cover: one dip for the grid, drawn by the cell (the focused one too, with the confirm button's).
        val touch = rememberTouchPressScale()
        // Pressed (rules.md > Shapes and motion > Pressed): the focused cover dips about its centre while the confirm button is held.
        val pressScale = rememberConfirmPressScale {
            val focus = state.focus
            if (focus.column == Column.Games && !panelOpen.value) focus.rowOf(entry.id) else null
        }
        val halo = focusHalo(entry, gridState, glide, lift, panelOpen, packed, touch, pressScale, state, motionOn)
        val measurer = rememberTextMeasurer(cacheSize = 64)
        // Remembered: a new fitter would recompose every tile that reads it.
        val fitter = LocalTileTitleFit.current ?: remember(measurer) { TileTitleFit(measurer) }
        // The focus rules move by this table, asked at each move (never a frame behind). Set while composing, so a new pack
        // (size, text, width) takes over with no frame in between; it goes when this grid does, unless a newer one has taken over.
        val source = remember(state, entry, packed) { { packed.value.toRows(entry.rows.map { it.id }) } }
        state.setPackedRowsSource(entry.id, source)
        DisposableEffect(state, source) { onDispose { state.clearPackedRowsSource(entry.id, source) } }
        CompositionLocalProvider(LocalTileTitleFit provides fitter) {
        // Under the ring: the focused cover is drawn over the mask (it never stands in a fade), the rows cut at an edge fade.
        val fades = remember(gridState, glide, state, entry.id, packed) { GridEdgeFades(gridState, glide) { packed.value.rowOf(state.focus.rowOf(entry.id)) } }
        GridRows(modifier, entry, gridState, packed, spans, viewport, glide, rowGap, density, titleStyle, state, onAction, touch, lift, fade, halo, fades)
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
    lift: CoverLift,
    fade: ContentFade,
    halo: Modifier,
    fades: GridEdgeFades,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        state = gridState,
        flingBehavior = rememberRowSnapFling(gridState),
        modifier = modifier.padding(top = PlumDimens.gridTopGap - PlumDimens.barGap).fadingIn(fade).then(halo).listEdgeFadeMask(fades, PlumTheme.edgeFadeOn).testTag(HomeTags.GRID).then(glide.modifier),
        contentPadding = PaddingValues(top = glide.extra, bottom = glide.extra + rememberGridLeftover(spans, viewport)),
        verticalArrangement = Arrangement.spacedBy(rowGap),
    ) {
        val rows = packed.value.rows
        items(rows.size, key = { entry.rows[rows[it].start].id }) { at ->
            val row = rows[at]
            PackedRowBoxes(entry, row, density, titleStyle, state, onAction, touch, lift)
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
    density: Density,
    titleStyle: TextStyle,
    state: StripState,
    onAction: (Action) -> Unit,
    touch: TouchPressScale,
    lift: CoverLift,
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
                    GridCoverAt(entry, index, DpSize(box.width.dp, row.height.dp), titleStyle, state, onAction, touch, lift)
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
    titleStyle: TextStyle,
    state: StripState,
    onAction: (Action) -> Unit,
    touch: TouchPressScale,
    lift: CoverLift,
) {
    val row = entry.rows[index]
    val art = LocalHomeArt.current
    LocalGridCellProbe.current?.let { probe -> remember { probe.onCreated(index).let { index } } }
    val tap = remember(index, onAction) { Modifier.tapItem(Column.Games, index, onAction).testTag(HomeTags.cover(index)) }
    // Read in the layer, not in composition: a move re-runs two covers' layers, nothing recomposes.
    val focused =
        remember(state, entry.id, index, touch) {
            touch.modifier(index).drawWithContent {
                // The focused cover dips with the confirm button as well; a finger's dip is the grid's one.
                val slot = lift.slotOf(index)
                // A lifted cover is drawn larger over the grid from its recording (the dip is drawn there).
                if (slot != null) return@drawWithContent drawCoverForLift(slot, index, entry.id)
                val dip = touch.scaleOf(index)
                if (dip < 1f) withTransform({ scale(dip, dip, center) }) { this@drawWithContent.drawContent() } else drawContent()
            }
        }
    // A game taken out of Favorites stays in the held grid until the person leaves it: dimmed, where the list drops the star.
    val dimmed = entry.kind == EntryKind.Favorites && !row.isFavorite
    GridCover(row, art.slot(row.scrapeKey), entry.shapeOf(row), entry.posterOf(row), cell, titleStyle, tap, focused, lift, index, fitsShape = entry.kind.mixesSystems, dimmed = dimmed)
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

/**
 * The focus halo of the focused cover's collar (and of the one just left, fading), drawn over the grid so its clip does not cut
 * it at the top and bottom rows; the collar's groove is lit by the cell itself. Reads the focus, the animations and the scroll
 * while drawing: a move or a scroll redraws the halo, nothing recomposes.
 */
@Composable
private fun focusHalo(
    entry: HomeEntry,
    gridState: LazyGridState,
    glide: ListGlide,
    lift: CoverLift,
    panelOpen: State<Boolean>,
    packed: State<PackedGrid>,
    touch: TouchPressScale,
    pressScale: () -> Float,
    state: StripState,
    motionOn: State<Boolean>,
): Modifier {
    val colors = PlumTheme.colors
    val art = LocalHomeArt.current
    FollowFocusLift(state, entry.id, panelOpen, lift, null, motionOn, null)
    return Modifier.drawWithContent {
        drawContent()
        fun drawOne(slot: LiftSlot) {
            val index = slot.index
            val grid = packed.value
            val box = grid.boxOf(index) ?: return
            val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == grid.rowOf(index) } ?: return
            // Follows its cover while the rows glide, cut where the covers are cut.
            val shift = glide.offset
            val width = box.width.dp.toPx()
            val cellRect = Rect(Offset(item.offset.x + box.x.dp.toPx(), item.offset.y + shift), Size(width, item.size.height.toFloat()))
            val inner = coverInner(DpSize(box.width.dp, grid.rows[grid.rowOf(index)].height.dp))
            val row = entry.rows.getOrNull(index)
            val rowShape = row?.let(entry::shapeOf) ?: entry.coverShape
            val picture = (art.slot(row?.scrapeKey) as? ArtSlot.Found)?.art?.gridPicture(inner, this)
            val aspect = picture?.aspect ?: if (entry.kind.mixesSystems) rowShape.widthOverHeight else null
            val collar = collarRect(aspect, cellRect)
            val pressed = if (slot.isFocused) minOf(pressScale(), touch.scaleOf(index)) else 1f
            val draw: DrawScope.() -> Unit = { drawLiftedCover(slot, entry.id, collar, cellRect, pressed, colors) }
            if (shift == 0f) draw() else clipRect(top = 0f, bottom = size.height, left = -size.width, right = size.width * 2) { draw() }
        }
        // The one fading out first, so the focused one is on top.
        for (slot in lift.slots) if (slot.index != NO_COVER && !slot.isFocused) drawOne(slot)
        for (slot in lift.slots) if (slot.index != NO_COVER && slot.isFocused) drawOne(slot)
    }
}

/**
 * One cover in its collar ([CollarSize.Cover]): the saved box front ([gridPicture]) at its own shape, the title in the placeholder
 * when there is none, the collar hugging the drawn box and bottom-centred in the [cell] so a row's covers stand on one line.
 * [shape] is the game's system's. With [fitsShape] (Recent and Favorites, whose cells are [MixedGridShape]) the placeholder is that
 * shape fitted in the cell like a picture, so a game without box art keeps its system's shape too. [dimmed]: a Favorites game taken
 * out and still shown ([PlumDimens.UNFAVORITED_COVER_ALPHA]). The groove's light is [lift]'s slot growth for [index], read while drawing.
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
    lift: CoverLift,
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
        Box(Modifier.collar(CollarSize.Cover, corner, halo = false) { lift.slotOf(index)?.grow?.value ?: 0f }) {
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
