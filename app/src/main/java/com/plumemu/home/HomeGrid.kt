package com.plumemu.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.plumemu.coreui.components.CoverShadow
import com.plumemu.coreui.components.CoverGlowColor
import com.plumemu.coreui.components.rememberCoverShadow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.drawGridFocusRing
import com.plumemu.coreui.components.TouchPressScale
import com.plumemu.coreui.components.rememberConfirmPressScale
import com.plumemu.coreui.components.rememberTouchPressScale
import com.plumemu.coreui.components.FocusTravel
import com.plumemu.coreui.components.rememberFocusTravel
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
 * box art, Apps' grid of icons is [AppsGrid]): covers in the system's shape, columns and row height from
 * `PlumLayout`. Recent and Favorites mix systems: their cells are [MixedGridShape]'s and each cover
 * keeps its own system's shape inside its cell ([HomeEntry.shapeOf]). Covers start
 * where the list starts and span the grid area minus the board's 4 dp padding; rows that do not
 * fit are cut at the strip's edge, as on the board.
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
    if (entry.kind == EntryKind.Apps) {
        AppsGrid(entry, state, onAction, panelOpen, modifier)
        return
    }
    val layout = PlumTheme.layout
    val colors = PlumTheme.colors
    val shape = entry.coverShape
    val columns = layout.gridColumns(shape)
    val gap = layout.gridGap(shape)
    val rowGap = layout.gridRowGap(shape)
    val rowHeight = layout.gridRowHeight(shape)
    val fit = layout.gridRowsThatFit(shape)
    val cell = DpSize(layout.gridCoverWidth(shape), rowHeight)
    val titleStyle = PlumTheme.type.coverTitle(layout.gridSize)
    val art = LocalHomeArt.current
    // L1/R1 with the games focused: the next system's games fade in instead of popping. Moves in
    // the system column swap the preview at once (a held key would strobe it).
    val fade = rememberContentFade(entry.id) { fadesSystemSwap(state) }
    // A new grid size (or text size) with other columns opens the grid again at the focused
    // game's row, like a system's grid opening: the same game stays focused (the focus is its
    // index), and its row is whole on screen whatever the old scroll was.
    key(entry.id, columns) {
        // Read once without observing: the grid must not recompose on every move.
        val opening = Snapshot.withoutReadObservation { state.focus.rowOf(entry.id) }
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = firstIndexShowing(opening / columns, fit) * columns)
        val glide = rememberListGlide(stride = rowHeight + rowGap)
        // The focused cover is drawn here, over the grid and outside its clip (which cuts every
        // other cover at the grid's bounds): the cover records itself into this layer instead of
        // drawing in place, and the ring's pass draws it FOCUSED_COVER_SCALE larger.
        val motionOn = rememberUpdatedState(PlumTheme.motion.isOn)
        // The cover focused as the grid opens (a system switch, a new grid size) is lifted in the first frame:
        // ring and full scale at once, no grow and no sweep; those play only when focus moves between covers.
        val openingFocus = Snapshot.withoutReadObservation { if (state.focus.column == Column.Games && !panelOpen.value) opening else NO_COVER }
        val lift = rememberCoverLift(openingFocus) { motionOn.value }
        val shadow = rememberCoverShadow()
        // A finger's press on a cover: one dip for the grid, drawn by the cell (alone) or by the ring pass (focused).
        val touch = rememberTouchPressScale()
        val travel = rememberFocusTravel()
        val ring = focusRing(state, entry, gridState, glide, lift, panelOpen, cell, touch, shadow, motionOn, travel)
        val fitter = LocalTileTitleFit.current ?: TileTitleFit(rememberTextMeasurer(cacheSize = 64))
        CompositionLocalProvider(LocalTileTitleFit provides fitter) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            flingBehavior = rememberRowSnapFling(gridState),
            modifier = modifier.padding(top = PlumDimens.gridTopGap - PlumDimens.barGap).fadingIn(fade).then(ring).testTag(HomeTags.GRID).then(glide.modifier),
            contentPadding = PaddingValues(top = glide.extra, bottom = glide.extra + rememberGridLeftover(gridState, layout.gridStripHeight, rowHeight, rowGap, fit), end = PlumDimens.gridPadding * 2),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            itemsIndexed(entry.rows, key = { _, row -> row.id }) { index, row ->
                val tap = remember(index, onAction) { Modifier.tapItem(Column.Games, index, onAction).testTag(HomeTags.cover(index)) }
                // Read in the layer, not in composition: a move re-runs two covers' layers, nothing recomposes.
                val focused =
                    remember(state, entry.id, index, touch) {
                        touch.modifier(index).drawWithContent {
                            // A cover holds a slot while it is focused and while it shrinks back (CoverLift).
                            val slot = lift.slotOf(index)
                            if (slot == null) {
                                // Not focused: a finger on it dips this cover alone, about its cell's centre.
                                val dip = touch.scaleOf(index)
                                if (dip < 1f) withTransform({ scale(dip, dip, center) }) { this@drawWithContent.drawContent() } else drawContent()
                                return@drawWithContent
                            }
                            // The grid draws it larger over its clip (the slot's layer). It is drawn in place
                            // too, whole: drawn only into the layer (or clipped to a pixel in place),
                            // its drawing (the cover's clip layer) left the grid's layer for another in one
                            // frame, and the device's renderer then drew it blank (seen on an AYN Thor: covers vanished under the cursor; FocusedCoverOnDeviceTest). The
                            // ring pass hides this copy under a background plate (focusRing).
                            drawContent()
                            slot.layer.record { this@drawWithContent.drawContent() }
                            slot.recordedEntryId = entry.id
                            slot.recordedIndex = index
                        }
                    }
                // A game taken out of Favorites stays in the held grid until the person leaves it: dimmed, where the list drops the star.
                val dimmed = entry.kind == EntryKind.Favorites && !row.isFavorite
                GridCover(row, art.slot(row.scrapeKey), entry.shapeOf(row), cell, titleStyle, tap, focused, shadow, fitsShape = entry.kind.mixesSystems, dimmed = dimmed)
            }
        }
        }
        KeepFocusedCellVisible(gridState, columns, fit, glide) { state.focus.rowOf(entry.id) }
        FollowArt(entry.rows) { gridState.firstVisibleItemIndex }
    }
}

/**
 * The ring of the focused cover, and the covers themselves drawn larger over the grid's clip (see
 * [GamesGrid] and [CoverLift]): the focused one growing to [PlumDimens.FOCUSED_COVER_SCALE] under
 * its ring, its deeper shadow and the one light sweep, and the one it left shrinking back. Reads the focus, the animations and
 * the scroll while drawing: a move or a scroll redraws the ring, nothing recomposes.
 */
@Composable
private fun focusRing(
    state: StripState,
    entry: HomeEntry,
    gridState: LazyGridState,
    glide: ListGlide,
    lift: CoverLift,
    panelOpen: State<Boolean>,
    cell: DpSize,
    touch: TouchPressScale,
    shadow: CoverShadow,
    motionOn: State<Boolean>,
    travel: FocusTravel,
): Modifier {
    val colors = PlumTheme.colors
    val art = LocalHomeArt.current
    // Pressed (rules.md > Shapes and motion > Pressed): the focused cover and its ring dip about their centre while
    // the confirm button is held. Read in the draw block only.
    val pressScale = rememberConfirmPressScale {
        val focus = state.focus
        if (focus.column == Column.Games && !panelOpen.value) focus.rowOf(entry.id) else null
    }
    val sweep = remember { FocusSweep() }
    FollowFocusLift(state, entry.id, panelOpen, lift, sweep, motionOn, travel)
    return Modifier.drawWithContent {
        drawContent()
        var ringDrawn = false
        // One drawn lifted cover: the focused one (ring, sweep) or one shrinking back.
        fun drawLifted(slot: LiftSlot) {
            val index = slot.index
            val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
            // The ring follows its cover while the rows glide, cut where the covers are cut
            // (at rest it may reach into the bar gaps, as on the board); it hugs the picture's
            // own shape, drawn larger like the focused cover.
            val shift = glide.offset
            val cellRect = Rect(Offset(item.offset.x.toFloat(), item.offset.y + shift), Size(item.size.width.toFloat(), item.size.height.toFloat()))
            val row = entry.rows.getOrNull(index)
            val rowShape = row?.let(entry::shapeOf) ?: entry.coverShape
            val picture = (art.slot(row?.scrapeKey) as? ArtSlot.Found)?.art?.gridPicture(cell, this)
            // A Recent or Favorites placeholder stands in its own system's shape (GridCover), not the whole cell.
            val aspect = picture?.aspect ?: if (entry.kind.mixesSystems) rowShape.widthOverHeight else null
            val resting = if (aspect != null) shelfRect(aspect, cellRect) else cellRect
            val grow = slot.grow.value
            val scale = 1f + (PlumDimens.FOCUSED_COVER_SCALE - 1f) * grow
            val lift = PlumDimens.focusedLift.toPx() * grow
            val cover = focusedCoverRect(cellRect, aspect, scale, lift)
            val ringCorner = coverCorner(rowShape) * scale
            val drawCover = slot.recordedIndex == index && slot.recordedEntryId == entry.id
            val glowColor = picture?.let { art.glowColors[it] } ?: CoverGlowColor.lifted(colors.sel)
            val pressed = if (slot.isFocused) minOf(pressScale(), touch.scaleOf(index)) else 1f
            // The ring travels from the cell it left (rules.md > Focus and current); the cover itself grows in place.
            val ringShift =
                if (slot.isFocused) {
                    ringDrawn = true
                    travel.offset(index, Offset(cellRect.left, cellRect.top - shift), size.width, cellRect.height * RING_LAG_ROWS) - Offset(0f, shift)
                } else {
                    Offset.Zero
                }
            val drawFocused: DrawScope.() -> Unit = {
                // The cell's own copy is wider than the dipped cover, so a plate of the background hides it first.
                if (pressed < 1f && drawCover) {
                    drawRoundRect(colors.bg, resting.topLeft, resting.size, CornerRadius(coverCorner(rowShape).toPx()))
                }
                withTransform({ scale(pressed, pressed, pivot = cover.center) }) {
                    // Deeper and further than the cell's own shadow, in step with the lift; drawn with the cover's
                    // copy only, else it would darken the cell's own, still unscaled cover.
                    if (drawCover) {
                        with(shadow) {
                            draw(cover, ringCorner.toPx(), isLifted = true, strength = grow)
                            // In the cover's own colour (its picture's average, the tile's `sel` for a placeholder), in step with the lift.
                            drawGlow(cover, ringCorner.toPx(), glowColor, grow)
                        }
                        drawFocusedCover(slot.layer, resting, cover, ringCorner, colors.bg, scale, lift)
                    }
                    if (slot.isFocused) {
                        if (sweep.progress.isRunning) drawSweep(cover, ringCorner.toPx(), sweep.progress.value)
                        drawGridFocusRing(cover.topLeft + ringShift, cover.size, ringCorner, colors.accent, colors.bg)
                    }
                }
            }
            if (shift == 0f) {
                drawFocused()
            } else {
                clipRect(top = 0f, bottom = size.height, left = -size.width, right = size.width * 2) { drawFocused() }
            }
        }
        // The one shrinking back first, so the focused cover is on top.
        for (slot in lift.slots) if (slot.index != NO_COVER && !slot.isFocused) drawLifted(slot)
        for (slot in lift.slots) if (slot.index != NO_COVER && slot.isFocused) drawLifted(slot)
        if (!ringDrawn) travel.reset()
    }
}

/**
 * One cover on the shelf: the saved box front ([gridPicture]) at its own shape, bottom-centred in
 * the [cell] so a row's covers stand on one line; the title in the placeholder when there is none.
 * [shape] is the game's system's. With [fitsShape] (Recent and Favorites, whose cells are
 * [MixedGridShape]) the placeholder is that shape fitted in the cell and bottom-centred like a
 * picture, so a game without box art keeps its system's shape too. [dimmed]: a Favorites game
 * taken out and still shown ([PlumDimens.UNFAVORITED_COVER_ALPHA]).
 */
@Composable
private fun GridCover(
    row: GameRowModel,
    slot: ArtSlot,
    shape: CoverShape,
    cell: DpSize,
    titleStyle: TextStyle,
    modifier: Modifier,
    focused: Modifier,
    shadow: CoverShadow,
    fitsShape: Boolean = false,
    dimmed: Boolean = false,
) {
    // Inside the focused modifier, so the focus layer's larger copy is dimmed too.
    val look = if (dimmed) focused.then(Modifier.graphicsLayer { alpha = PlumDimens.UNFAVORITED_COVER_ALPHA }.semantics { this[CoverDimmed] = true }) else focused
    val landscape = shape.coverClass == CoverClass.Landscape
    val density = LocalDensity.current
    val picture = (slot as? ArtSlot.Found)?.art?.gridPicture(cell, density)
    val ownShape = picture == null && fitsShape
    @Composable
    fun Cover(size: DpSize, coverModifier: Modifier) =
        CoverArt(
            title = row.title,
            picture = picture,
            titleStyle = titleStyle,
            size = size,
            alignment = Alignment.BottomCenter,
            corner = coverCorner(shape),
            titlePadding = if (landscape) PlumDimens.coverPaddingLandscape else PlumDimens.coverPaddingPortrait,
            modifier = coverModifier,
            pictureModifier = look,
            subtitle = detailsOf(row, slot)?.year?.toString(),
            hasEdge = true,
            shadow = shadow,
            shadowAlpha = if (dimmed) PlumDimens.UNFAVORITED_COVER_ALPHA else 1f,
        )
    if (ownShape) {
        Box(modifier.size(cell), contentAlignment = Alignment.BottomCenter) { Cover(fitInside(shape.widthOverHeight, cell), Modifier.testTag(HomeTags.COVER_OWN_SHAPE)) }
    } else {
        Cover(cell, modifier)
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

/**
 * Where the focused cover is drawn in a grid cell at [cell] (px): the picture of [aspect] fitted
 * and bottom-centred ([shelfRect]; the whole cell for a titled tile), grown by
 * [scale] (the full [PlumDimens.FOCUSED_COVER_SCALE] at rest, less while it grows or shrinks) about its
 * centre and raised [lift] px. The ring is drawn around it.
 */
internal fun focusedCoverRect(
    cell: Rect,
    aspect: Float?,
    scale: Float = PlumDimens.FOCUSED_COVER_SCALE,
    lift: Float = 0f,
): Rect {
    val cover = if (aspect != null) shelfRect(aspect, cell) else cell
    val dx = cover.width * (scale - 1f) / 2f
    val dy = cover.height * (scale - 1f) / 2f
    return Rect(cover.left - dx, cover.top - dy - lift, cover.right + dx, cover.bottom + dy - lift)
}

/** Landscape covers round 8; square and tall ones, the smaller cells, 6 like the board's portrait. */
private fun coverCorner(shape: CoverShape): Dp =
    if (shape.coverClass == CoverClass.Landscape) PlumDimens.cornerCoverLandscape else PlumDimens.cornerCoverPortrait

/**
 * The height left under [fit] whole rows in the grid, added as padding under the last row so the grid can scroll to
 * a position where its top is a row's top (without it the end of the list stops with a cut row's tail at the top,
 * which shows alone under the header). Measured in px from the laid-out grid (the viewport, a row and the gap
 * between), because dp rounded to px on a screen like the Thor's (2.29375) can be a px or three off, which left the
 * last row cut at the top by those px. Until the grid has a layout it is [gridLeftover]'s figure in dp.
 */
@Composable
internal fun rememberGridLeftover(
    gridState: LazyGridState,
    stripHeight: Dp,
    rowHeight: Dp,
    gap: Dp,
    fit: Int,
): Dp {
    val density = LocalDensity.current
    val measured by remember(gridState, fit) {
        derivedStateOf {
            val info = gridState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull() ?: return@derivedStateOf -1
            // The viewport has the glide's extra room at each end; the top padding is that room alone.
            val viewport = info.viewportSize.height - 2 * info.beforeContentPadding
            (viewport - (fit * item.size.height + (fit - 1) * info.mainAxisItemSpacing)).coerceAtLeast(0)
        }
    }
    return if (measured < 0) gridLeftover(stripHeight, rowHeight, gap, fit) else with(density) { measured.toDp() }
}

/** The height left under [fit] whole rows of [rowHeight] with [gap] between, in a grid [stripHeight] tall, from the dp figures alone. */
internal fun gridLeftover(
    stripHeight: Dp,
    rowHeight: Dp,
    gap: Dp,
    fit: Int,
): Dp = (stripHeight - rowHeight * fit - gap * (fit - 1)).coerceAtLeast(0.dp)

/**
 * Keeps the focused cover's row on screen (docs/design/rules.md › Focus and current: grids scroll
 * so its row shows), in the same frame as the focus moves, and glides like the list
 * (`KeepFocusedVisible`).
 */
@Composable
internal fun KeepFocusedCellVisible(
    gridState: LazyGridState,
    columns: Int,
    fit: Int,
    glide: ListGlide,
    focusedIndex: () -> Int,
) {
    val latest = rememberUpdatedState(focusedIndex)
    val index by remember(gridState) { derivedStateOf { latest.value() } }
    val keeper = remember(gridState, columns) { ScrollKeeper() }
    val target = index
    var kept: KeptScroll? = null
    val moved =
        Snapshot.withoutReadObservation {
            val items = gridState.layoutInfo.visibleItemsInfo
            keeper.update(target, hasLayout = items.isNotEmpty(), isLaidOut = items.any { it.index == target }) {
                gridState.keepVisible(target, columns, fit).also { kept = it }.moved
            }
        }
    // Also when nothing scrolled. The grid scrolls only when the row past the focus is not whole (the rule it
    // always had); when it does, the new row glides in from the grid's edge and is clipped by it.
    kept?.let { glide.jumped(moved.toFloat(), it.roomAbove, it.roomBelow) }
    if (keeper.isWaiting) gridState.layoutInfo
    KeepFocusedCellOnResize(gridState, columns, fit) { latest.value() }
}

/**
 * Room the grid's glide may draw rows past the grid's edge: unbounded, so the new row slides in from the edge and the grid's
 * clip cuts it while it glides ([ListGlide] caps the offset by the rows it composed). A held key still goes linear.
 */
private const val GRID_GLIDE_ROOM = Float.MAX_VALUE

/**
 * The grid got another height or another number of whole rows ([fit]; a larger text size or font makes the bars
 * taller and the rows too) while the focus stood still: the scroll is aligned to whole rows again with the
 * focused row shown, without a glide (the room under the last row changed with [fit], so an end-of-list
 * scroll can now stand between rows). Only this small scope reads the viewport's height.
 */
@Composable
private fun KeepFocusedCellOnResize(
    gridState: LazyGridState,
    columns: Int,
    fit: Int,
    focusedIndex: () -> Int,
) {
    // Read in a flow, not in composition: while the grid glides its layout info is rewritten every frame, and a
    // composition-time read recomposed this scope on a move (HomeRecompositionTest) though the height stood still.
    val fitNow = rememberUpdatedState(fit)
    val focusNow = rememberUpdatedState(focusedIndex)
    LaunchedEffect(gridState, columns) {
        var seen: Pair<Int, Int>? = null
        snapshotFlow { gridState.layoutInfo.viewportSize.height to fitNow.value }.collect { now ->
            val before = seen
            seen = now
            if (before != null && before.first != 0 && before != now) gridState.alignToRows(focusNow.value(), columns, now.second)
        }
    }
}

/** The scroll to whole rows with row [index]'s shown: the first shown row stays when it can, else moves least. */
private fun LazyGridState.alignToRows(
    index: Int,
    columns: Int,
    fit: Int,
) {
    val total = layoutInfo.totalItemsCount
    if (total == 0 || index !in 0 until total) return
    val rows = (total + columns - 1) / columns
    val row = index / columns
    val first = (firstVisibleItemIndex / columns).coerceIn(0, maxOf(rows - fit, 0))
    val aligned = if (row < first) row else if (row > first + fit - 1) row - fit + 1 else first
    requestScrollToItem(aligned * columns, 0)
}

/**
 * Touch flings end on a row: the grid snaps to the nearest row's start (foundation's snapping, as a pager's), so a
 * fling never leaves a row cut at the top. The room under the last row ([gridLeftover]) lets the end be one.
 */
@Composable
internal fun rememberRowSnapFling(gridState: LazyGridState) = rememberSnapFlingBehavior(remember(gridState) { SnapLayoutInfoProvider(gridState, SnapPosition.Start) })

/**
 * The grid's `keepVisible` (KeepFocusedVisible.kt), by rows of [columns] covers: the scroll that
 * shows the focused row whole, and the room left above and below it for a glide.
 */
private fun LazyGridState.keepVisible(
    index: Int,
    columns: Int,
    fit: Int,
): KeptScroll {
    val info = layoutInfo
    val items = info.visibleItemsInfo
    if (items.isEmpty() || index !in 0 until info.totalItemsCount) return KeptScroll(0, 0f, 0f)
    val windowEnd = info.viewportEndOffset - info.afterContentPadding
    val item = items.firstOrNull { it.index == index }
    val top: Int
    val bottom: Int
    if (item != null) {
        top = item.offset.y
        bottom = item.offset.y + item.size.height
    } else {
        val first = items.first()
        val last = items.last()
        val rows = last.index / columns - first.index / columns
        val stride = if (rows > 0) (last.offset.y - first.offset.y) / rows else first.size.height + info.mainAxisItemSpacing
        val anchor = if (index < first.index) first else last
        top = anchor.offset.y + (index / columns - anchor.index / columns) * stride
        bottom = top + anchor.size.height
    }
    val height = bottom - top
    val row = index / columns
    val firstRow = firstVisibleItemIndex / columns
    // By rows when the grid stands on a row (never in px: the room under the rows is rounded from dp, a px or
    // three off windowEnd), by px after a touch scroll left it between rows.
    val onRow = firstVisibleItemScrollOffset == 0
    val isBelow = if (onRow) row >= firstRow + fit else bottom > windowEnd
    val isAbove = if (onRow) row < firstRow else top < 0
    return when {
        isAbove -> {
            requestScrollToItem(row * columns, 0)
            KeptScroll(-top, GRID_GLIDE_ROOM, GRID_GLIDE_ROOM)
        }
        isBelow -> {
            // Scrolls by whole rows: the focused row lands as the last whole row and the rows above it start
            // at the grid's top (the first of the [fit] rows exactly there), so no row is cut at the top (a cut
            // row's tail, an app's second name line or a text tile's title, drew alone under the header). What does not fit is left under the last whole row, where a cut row
            // reads as "more".
            val stride = height + info.mainAxisItemSpacing
            val newFirst = (row - (fit - 1)).coerceAtLeast(0)
            requestScrollToItem(newFirst * columns, 0)
            val newTop = (row - newFirst) * stride
            KeptScroll(newTop - top, GRID_GLIDE_ROOM, GRID_GLIDE_ROOM)
        }
        else -> KeptScroll(0, GRID_GLIDE_ROOM, GRID_GLIDE_ROOM)
    }
}

