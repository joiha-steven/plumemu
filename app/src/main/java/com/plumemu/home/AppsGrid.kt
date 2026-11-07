package com.plumemu.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import androidx.compose.ui.graphics.graphicsLayer
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.TouchPressScale
import com.plumemu.coreui.components.FocusTravel
import com.plumemu.coreui.components.drawCoverFocusMark
import com.plumemu.coreui.components.rememberFocusTravel
import com.plumemu.coreui.components.rememberConfirmPressScale
import com.plumemu.coreui.components.rememberTouchPressScale
import com.plumemu.coreui.components.listEdgeFadeMask
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.AppsGridDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.rememberContentFade
import com.plumemu.coreui.theme.rememberListGlide
import kotlin.math.ceil
import kotlin.math.floor

/*
 * Apps as a grid of icons (Apps has a grid too, but its size cannot be
 * changed). One fixed size: the icon is AppsGridDimens.appsGridTileIcon whatever size the grids use, and
 * L1 / R1 do nothing in this grid (GridSizeKeys takes them; the system is switched with L2 / R2). No box art is needed: the icons come
 * from Android (HomeAppIcons). Focus, scroll and the ring are a game grid's (HomeGrid.kt).
 */

/**
 * Columns of the Apps grid on [layout]: as many tiles of [AppsGridDimens.appsGridTileMinWidth] (wider with the
 * text size, so names keep their words) as fit across the grid's span with the gap the cover grid's column
 * table was cut with ([PlumDimens.gridColumnsGapPortrait], as `PlumLayout.gridColumns` counts): the drawn gap
 * is wider, so a tile is a little narrower than the minimum but every column count is the one before the
 * gaps were widened. Width and text size only, never the grid size: 7 at 768 dp, 5 at 576, 8 at 837 at
 * Medium text; 5 / 3 / 5 at Largest.
 */
internal fun appsGridColumns(layout: PlumLayout): Int {
    val tile = AppsGridDimens.appsGridTileMinWidth * layout.textScale
    val gap = PlumDimens.gridColumnsGapPortrait
    return floor((layout.gridCoversWidth + gap) / (tile + gap)).toInt().coerceAtLeast(1)
}

/**
 * A row of the Apps grid: the tile's padding, the icon, the gap and one line of the name in [nameLineHeight],
 * rounded up to a whole dp so every row is the same height.
 */
internal fun appsGridRowHeight(nameLineHeight: Dp): Dp =
    Dp(ceil((AppsGridDimens.appsGridTilePadding * 2 + AppsGridDimens.appsGridTileIcon + AppsGridDimens.appsGridNameGap + nameLineHeight).value))

/** The name under an app's icon: the meta size (13 at Medium), following the text size and font. */
@Composable
internal fun appsGridNameStyle(): TextStyle = PlumTheme.type.meta

/**
 * One line of [style] as drawn: Compose rounds each line's height up to a whole pixel (14.16 dp at Small on
 * xhdpi draws 29 px lines), so two lines are two of these, not twice the style's line height.
 */
internal fun appsGridNameLine(
    style: TextStyle,
    density: Density,
): Dp = with(density) { ceil(style.lineHeight.toPx()).toDp() }

/**
 * The Apps entry as a grid, with the cover grid's gaps and lift but no card (apps are not put inside a frame):
 * icons and names stand on the background; the focused icon gets the cover's ring and one light sweep; nothing grows, so names keep their line. Tiles
 * start where the list starts and span the grid's whole width, as a game grid's covers do. The focused
 * tile is drawn by the grid over its clip, with its ring ([appsFocusRing], the cover grid's [CoverLift] and
 * [FocusSweep]), so a move recomposes no tile and reloads no icon.
 */
@Composable
internal fun AppsGrid(
    entry: HomeEntry,
    state: StripState,
    onAction: (Action) -> Unit,
    panelOpen: State<Boolean>,
    modifier: Modifier,
) {
    val layout = PlumTheme.layout
    val colors = PlumTheme.colors
    val density = LocalDensity.current
    val columns = appsGridColumns(layout)
    val gap = layout.gridGap(CoverClass.Square)
    val nameStyle = appsGridNameStyle()
    val rowHeight = appsGridRowHeight(appsGridNameLine(nameStyle, density))
    val rowGap = AppsGridDimens.appsGridRowGap
    val rowCount = (entry.rows.size + columns - 1) / columns
    // Every row one height: where each stands in px, for the scroll rules (the cover grid's rows may differ).
    val spans = remember(rowCount, rowHeight, rowGap, density) { derivedStateOf { RowSpans.uniform(rowCount, with(density) { rowHeight.roundToPx() }, with(density) { rowGap.roundToPx() }) } }
    val rowPx = with(density) { rowHeight.roundToPx() }
    val gapPx = with(density) { rowGap.roundToPx() }
    // Whole rows only: the window is as high as the rows that fit whole in the strip (AppsGridWindow.kt).
    // Whole rows clear of the fade on the hint bar.
    val stripPx = wholeRowsPx(with(density) { (layout.appsStripHeight + LocalBarRoom.current.taller + LocalBarRoom.current.bottom - PlumDimens.listFadeBand).roundToPx() }, rowPx, gapPx)
    val iconPx = with(density) { AppsGridDimens.appsGridTileIcon.roundToPx() }
    val icons = LocalHomeAppIcons.current
    // Raised after a package change: the tiles on screen ask for their icons again (read here, not per tile).
    val generation by icons.generation.collectAsState()
    // L1/R1 with the games focused: the next entry fades in, as in every grid.
    val fade = rememberContentFade(entry.id) { fadesSystemSwap(state) }
    // A text size with other columns opens the grid again at the focused app's row.
    key(entry.id, columns) {
        // Read once without observing: the grid must not recompose on every move.
        val opening = Snapshot.withoutReadObservation { state.focus.rowOf(entry.id) }
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = spans.value.firstShowing(opening / columns, stripPx) * columns)
        // The room under the window, down to the hint bar: the tiles run through it and under the bar.
        val belowWindow = (LocalBarRoom.current.height - PlumDimens.appsGridTopGap - with(density) { stripPx.toDp() }).coerceAtLeast(0.dp)
        // The window is stripPx by definition: measured back from the padding it could round a px short of its last row, and the grid would scroll onto a row that stands whole.
        val viewport = rememberUpdatedState(stripPx)
        val glide = rememberListGlide(stride = rowHeight + rowGap, slabRoom = false)
        val touch = rememberTouchPressScale()
        val spread = LocalAppsSpread.current
        val confirmScale = rememberConfirmPressScale { if (state.focus.column == Column.Games && !panelOpen.value) state.focus.rowOf(entry.id) else null }
        // The tile focused as the grid opens is lifted in the first frame (no grow, no sweep), as in the cover grid.
        val motionOn = rememberUpdatedState(PlumTheme.motion.isOn)
        val openingFocus = Snapshot.withoutReadObservation { if (state.focus.column == Column.Games && !panelOpen.value) opening else NO_COVER }
        val lift = rememberCoverLift(openingFocus) { motionOn.value }
        val travel = rememberFocusTravel()
        val ring = appsFocusRing(state, entry, gridState, glide, lift, panelOpen, touch, confirmScale, motionOn, travel, spread, columns)
        // Under the ring: the focused cover is drawn over the mask, a row showing too little of itself at a bar is left out.
        val fades = remember(gridState, glide, state, entry.id) { GridEdgeFades(gridState, glide, with(density) { PlumDimens.appsGridTopGap.roundToPx().toFloat() }) { state.focus.rowOf(entry.id) } }
        FollowBarRunner(fades)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            flingBehavior = rememberRowSnapFling(gridState),
            modifier = modifier.fadingIn(fade).then(ring).listEdgeFadeMask(fades, true).testTag(HomeTags.GRID).then(glide.modifier),
            contentPadding = PaddingValues(top = glide.extra + PlumDimens.appsGridTopGap, bottom = glide.extra + belowWindow + rememberGridLeftover(spans, viewport)),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(rowGap),
        ) {
            itemsIndexed(entry.rows, key = { _, row -> row.id }) { index, row ->
                val tap = remember(index, onAction) { Modifier.tapItem(Column.Games, index, onAction).testTag(HomeTags.cover(index)) }
                // Read in the layer, not in composition: a finger's dip and (while the tile holds a lift slot, focused or
                // shrinking back) its drawing into the slot's layer for the grid's ring pass. No plate, rim or shadow:
                // the icon and the name stand straight on the background.
                val look =
                    remember(state, entry.id, index, touch) {
                        touch.modifier(index).drawWithContent {
                            val slot = lift.slotOf(index)
                            if (slot == null) {
                                drawTileDipped(touch.scaleOf(index))
                                return@drawWithContent
                            }
                            // Whole in place, name included; the ring pass only redraws the icon while it dips.
                            drawContent()
                            slot.layer.record { this@drawWithContent.drawContent() }
                            slot.recordedEntryId = entry.id
                            slot.recordedIndex = index
                        }
                    }
                // Beside the systems the preview's columns are drawn spread to the right margin: the cell's layer carries its touch target with it.
                val spreadBy = remember(spread, index, columns) { if (spread == null) Modifier else Modifier.graphicsLayer { translationX = spread.shiftOf(index % columns) } }
                AppTile(row, icons, generation, iconPx, rowHeight, nameStyle, { lift.slotOf(index)?.isFocused == true }, spreadBy.then(tap).then(look))
            }
        }
        KeepFocusedCellVisible(gridState, columns, spans, viewport, glide) { state.focus.rowOf(entry.id) }
    }
}

/**
 * The focus on a tile, read while drawing (the focus, the scroll, the glide, the animations): the one light sweep on
 * the focused icon and its ring, which travels from the tile left. The icon and its ring dip on a press, redrawn from
 * the tile's [LiftSlot]; the name never moves. A move only redraws.
 */
@Composable
private fun appsFocusRing(
    state: StripState,
    entry: HomeEntry,
    gridState: LazyGridState,
    glide: ListGlide,
    lift: CoverLift,
    panelOpen: State<Boolean>,
    touch: TouchPressScale,
    confirmScale: () -> Float,
    motionOn: State<Boolean>,
    travel: FocusTravel,
    spread: AppsSpread?,
    columns: Int,
): Modifier {
    val colors = PlumTheme.colors
    val sweep = remember { FocusSweep() }
    FollowFocusLift(state, entry.id, panelOpen, lift, sweep, motionOn, travel)
    return Modifier.drawWithContent {
        drawContent()
        var ringDrawn = false
        fun drawLifted(slot: LiftSlot) {
            val index = slot.index
            val item = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
            // While the rows glide the lift is cut where the tiles are cut; at rest it may reach into the bar gaps.
            val shift = glide.offset
            // Where the cell is drawn: the Apps preview spreads its columns, and the ring and the lifted tile stand on the cell.
            val spreadX = spread?.shiftOf(index % columns) ?: 0f
            val cell = Rect(Offset(item.offset.x + spreadX, item.offset.y + PlumDimens.appsGridTopGap.toPx() + shift), Size(item.size.width.toFloat(), item.size.height.toFloat()))
            // The focus stands on the icon, as on a cover, but neither grows: every name stays on its row's line,
            // and the ring has the room between the icon and its name.
            val iconSide = AppsGridDimens.appsGridTileIcon.toPx()
            val iconCentre = Offset(cell.center.x, cell.top + AppsGridDimens.appsGridTilePadding.toPx() + iconSide / 2)
            val icon = Rect(iconCentre, iconSide / 2)
            val corner = iconSide * AppsGridDimens.APPS_ICON_CORNER_SHARE
            val drawCover = slot.recordedIndex == index && slot.recordedEntryId == entry.id
            // The ring and the icon dip with the confirm button or a finger; the name stays where it is.
            val pressed = if (slot.isFocused) minOf(confirmScale(), touch.scaleOf(index)) else 1f
            // The ring travels from the tile it left (rules.md > Focus and current).
            val ringShift =
                if (slot.isFocused) {
                    ringDrawn = true
                    travel.offset(index, Offset(cell.left, cell.top - shift), size.width, cell.height * RING_LAG_ROWS) - Offset(0f, shift)
                } else {
                    Offset.Zero
                }
            val drawFocused: DrawScope.() -> Unit = {
                if (pressed < 1f && drawCover) drawRect(colors.bg, icon.topLeft, icon.size)
                withTransform({ scale(pressed, pressed, pivot = iconCentre) }) {
                    if (drawCover) {
                        if (pressed < 1f) {
                            clipRect(icon.left, icon.top, icon.right, icon.bottom) {
                                translate(cell.left, cell.top) { drawLayer(slot.layer) }
                            }
                        }
                    }
                    if (slot.isFocused) {
                        if (sweep.progress.isRunning) drawSweep(icon, corner, sweep.progress.value)
                        drawCoverFocusMark(icon.topLeft + ringShift, icon.size, corner, colors.accent, colors.bg, 1f)
                    }
                }
            }
            if (shift == 0f) {
                drawFocused()
            } else {
                clipRect(top = 0f, bottom = size.height, left = -size.width, right = size.width * 2) { drawFocused() }
            }
        }
        for (slot in lift.slots) if (slot.index != NO_COVER && !slot.isFocused) drawLifted(slot)
        for (slot in lift.slots) if (slot.index != NO_COVER && slot.isFocused) drawLifted(slot)
        if (!ringDrawn) travel.reset()
    }
}

/** A resting tile under a finger: the icon dips alone, so the name stays on its row's line. */
private fun ContentDrawScope.drawTileDipped(dip: Float) {
    if (dip >= 1f) {
        drawContent()
        return
    }
    val iconSide = AppsGridDimens.appsGridTileIcon.toPx()
    val iconBottom = AppsGridDimens.appsGridTilePadding.toPx() + iconSide
    clipRect(bottom = iconBottom) {
        withTransform({ scale(dip, dip, Offset(center.x, iconBottom - iconSide / 2)) }) { this@drawTileDipped.drawContent() }
    }
    clipRect(top = iconBottom) { this@drawTileDipped.drawContent() }
}

/** What a tile shows for its icon: nothing while it loads, the picture, or the stand-in when there is none. */
internal sealed interface TileIcon {
    data object Loading : TileIcon

    data class Picture(val image: ImageBitmap) : TileIcon

    data object Missing : TileIcon
}

/**
 * What [key]'s icon is now: taken from memory while composing when it is there, else loaded once (keyed by the key, the size
 * and the icons' [generation], never by the focus). Restarted when any changes: what memory holds is shown at once;
 * otherwise the old icon stays until the new answer comes. A failed or empty answer is [TileIcon.Missing] (the stand-in).
 */
@Composable
internal fun rememberTileIcon(
    key: String,
    iconPx: Int,
    icons: HomeAppIcons,
    generation: Int,
): TileIcon {
    val first = remember(key, iconPx, icons, generation) { icons.cached(key, iconPx)?.let { TileIcon.Picture(it) } ?: TileIcon.Loading }
    val icon by produceState(first, key, iconPx, icons, generation) {
        if (first is TileIcon.Picture || value is TileIcon.Loading) value = first
        if (first is TileIcon.Loading) value = icons.loadOrNull(key, iconPx)?.let { TileIcon.Picture(it) } ?: TileIcon.Missing
    }
    return icon
}

/**
 * One app: its icon (square, centred) and its name centred under it, on one line and "…" after.
 * The icon is taken from memory while composing when it is there, else loaded once per tile (keyed by the
 * row, the size and the icons' generation, never by the focus).
 */
@Composable
internal fun AppTile(
    row: GameRowModel,
    icons: HomeAppIcons,
    generation: Int,
    iconPx: Int,
    height: Dp,
    nameStyle: TextStyle,
    isFocused: () -> Boolean,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val icon = rememberTileIcon(row.iconKey, iconPx, icons, generation)
    Column(
        modifier = modifier.height(height).padding(vertical = AppsGridDimens.appsGridTilePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconModifier = Modifier.size(AppsGridDimens.appsGridTileIcon)
        when (val shown = icon) {
            is TileIcon.Picture -> Image(shown.image, contentDescription = null, modifier = iconModifier.testTag(HomeTags.APP_ICON))
            TileIcon.Missing -> AppIconStandIn(row.title, iconModifier)
            TileIcon.Loading -> Spacer(iconModifier)
        }
        Spacer(Modifier.height(AppsGridDimens.appsGridNameGap))
        // Two names in one place: the resting one and the focused one (strong colour, weight 500). Which one draws
        // is read while drawing (the lift slot's focus), so a move redraws and recomposes nothing; the other draws nothing.
        Box(Modifier.fillMaxWidth()) {
            AppName(row.title, nameStyle.copy(textAlign = TextAlign.Center), { colors.text }, { !isFocused() }, Modifier.testTag(HomeTags.APP_NAME))
            AppName(row.title, nameStyle.copy(textAlign = TextAlign.Center, fontWeight = FontWeight((nameStyle.fontWeight?.weight ?: FontWeight.Normal.weight) + FOCUSED_NAME_WEIGHT_STEP)), { colors.strong }, isFocused, Modifier.clearAndSetSemantics {})
        }
    }
}

/** An app's name, on one line and "…" after (the hint bar names the focused app in full), drawn only while [shown] says so (read while drawing). */
@Composable
private fun AppName(
    title: String,
    style: TextStyle,
    color: () -> Color,
    shown: () -> Boolean,
    modifier: Modifier,
) {
    BasicText(
        text = title,
        style = style,
        color = { color() },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().drawWithContent { if (shown()) drawContent() },
    )
}

/**
 * An app without a usable icon: Android's default robot (the app ships none; the launcher's `AppIcons` answers
 * None for it), an app uninstalled since the list was read, or one Android could not draw. A round tile in the
 * tiles' `sel` with the name's first letter in the chosen font (Plum Sans by default), so the tile keeps its size and is
 * still told apart. One letter, not two: two would read as initials of a multi-word name and crowd the circle.
 */
@Composable
internal fun AppIconStandIn(
    title: String,
    modifier: Modifier,
    /** The letter's size in dp (a large disc); null keeps the message title's. */
    letterSize: Float? = null,
) {
    val colors = PlumTheme.colors
    val shape = CircleShape
    Box(modifier.background(colors.sel, shape).testTag(HomeTags.APP_ICON_STAND_IN), contentAlignment = Alignment.Center) {
        val base = PlumTheme.type.messageTitle.copy(color = colors.text2)
        BasicText(text = firstLetterOf(title), style = if (letterSize == null) base else base.copy(fontSize = letterSize.sp, lineHeight = letterSize.sp), maxLines = 1)
    }
}

/** The first character of [title] in upper case, whole (a letter outside the BMP is two chars); empty for an empty name. */
internal fun firstLetterOf(title: String): String {
    val name = title.trim()
    return if (name.isEmpty()) "" else String(Character.toChars(name.codePointAt(0))).uppercase()
}

/** The focused app's name is this much heavier than the others': 400 to 500 at the default text weight. */
private const val FOCUSED_NAME_WEIGHT_STEP = 100

/** A row of the Apps grid cut by an edge stays (faded) only while this share of it shows; less is left out whole, as no sliver of an icon or a name may stand there. */
