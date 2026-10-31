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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.tapItem
import com.plumemu.coreui.components.CoverShadow
import com.plumemu.coreui.components.TouchPressScale
import com.plumemu.coreui.components.FocusTravel
import com.plumemu.coreui.components.drawGridFocusRing
import com.plumemu.coreui.components.rememberFocusTravel
import com.plumemu.coreui.components.rememberConfirmPressScale
import com.plumemu.coreui.components.CoverGlowColor
import com.plumemu.coreui.components.rememberCoverShadow
import com.plumemu.coreui.components.rememberTouchPressScale
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.ListGlide
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
 * changed). One fixed size: the icon is PlumDimens.appsGridIcon whatever Settings › Grid size says, and
 * L1 / R1 do nothing in this grid (GridSizeKeys takes them; the system is switched with L2 / R2). No box art is needed: the icons come
 * from Android (HomeAppIcons). Focus, scroll and the ring are a game grid's (HomeGrid.kt).
 */

/**
 * Columns of the Apps grid on [layout]: as many tiles of [PlumDimens.appsGridTileMinWidth] (wider with the
 * text size, so names keep their words) as fit across the grid's span with the gap the cover grid's column
 * table was cut with ([PlumDimens.gridColumnsGapPortrait], as `PlumLayout.gridColumns` counts): the drawn gap
 * is wider, so a tile is a little narrower than the minimum but every column count is the one before the
 * gaps were widened. Width and text size only, never the grid size: 7 at 768 dp, 5 at 576, 8 at 837 at
 * Medium text; 5 / 3 / 5 at Largest.
 */
internal fun appsGridColumns(layout: PlumLayout): Int {
    val tile = PlumDimens.appsGridTileMinWidth * layout.textScale
    val gap = PlumDimens.gridColumnsGapPortrait
    return floor((layout.gridCoversWidth + gap) / (tile + gap)).toInt().coerceAtLeast(1)
}

/**
 * A row of the Apps grid: the tile's padding, the icon, the gap and two lines of the name in [nameLineHeight],
 * rounded up to a whole dp so every row is the same height.
 */
internal fun appsGridRowHeight(nameLineHeight: Dp): Dp =
    Dp(ceil((PlumDimens.appsGridTilePadding * 2 + PlumDimens.appsGridIcon + PlumDimens.appsGridNameGap + nameLineHeight * 2).value))

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
 * icons and names stand on the background; the focused tile glows in its icon's colour, grows [PlumDimens.FOCUSED_ICON_SCALE], rises and gets one light sweep. Tiles
 * start where the list starts and span the grid's width less its padding, as a game grid's covers do. The focused
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
    val fit = floor((layout.gridStripHeight + gap) / (rowHeight + gap)).toInt().coerceAtLeast(1)
    val iconPx = with(density) { PlumDimens.appsGridIcon.roundToPx() }
    val icons = LocalHomeAppIcons.current
    // Raised after a package change: the tiles on screen ask for their icons again (read here, not per tile).
    val generation by icons.generation.collectAsState()
    // L1/R1 with the games focused: the next entry fades in, as in every grid.
    val fade = rememberContentFade(entry.id) { fadesSystemSwap(state) }
    // A text size with other columns opens the grid again at the focused app's row.
    key(entry.id, columns) {
        // Read once without observing: the grid must not recompose on every move.
        val opening = Snapshot.withoutReadObservation { state.focus.rowOf(entry.id) }
        val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = firstIndexShowing(opening / columns, fit) * columns)
        val glide = rememberListGlide(stride = rowHeight + gap)
        val touch = rememberTouchPressScale()
        val confirmScale = rememberConfirmPressScale { if (state.focus.column == Column.Games && !panelOpen.value) state.focus.rowOf(entry.id) else null }
        // The tile focused as the grid opens is lifted in the first frame (no grow, no sweep), as in the cover grid.
        val motionOn = rememberUpdatedState(PlumTheme.motion.isOn)
        val openingFocus = Snapshot.withoutReadObservation { if (state.focus.column == Column.Games && !panelOpen.value) opening else NO_COVER }
        val lift = rememberCoverLift(openingFocus) { motionOn.value }
        val shadow = rememberCoverShadow()
        // The glow's colour per app, from its icon (AppTile writes it as the icon lands).
        val glowColors = remember(entry.id, icons) { CoverGlowColors(icons.glowDispatcher) }
        val travel = rememberFocusTravel()
        val ring = appsFocusRing(state, entry, gridState, glide, lift, panelOpen, touch, confirmScale, shadow, glowColors, motionOn, travel)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            flingBehavior = rememberRowSnapFling(gridState),
            modifier = modifier.padding(top = PlumDimens.gridTopGap - PlumDimens.barGap).fadingIn(fade).then(ring).testTag(HomeTags.GRID).then(glide.modifier),
            contentPadding = PaddingValues(top = glide.extra, bottom = glide.extra + rememberGridLeftover(gridState, layout.gridStripHeight, rowHeight, gap, fit), end = PlumDimens.gridPadding * 2),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
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
                                // A finger on it dips this tile alone, about its centre.
                                val dip = touch.scaleOf(index)
                                withTransform({ scale(dip, dip, center) }) { this@drawWithContent.drawContent() }
                                return@drawWithContent
                            }
                            // Whole in place too, under its larger copy (as a cover: see GamesGrid); the ring pass hides it
                            // with a plate of the background colour.
                            drawContent()
                            slot.layer.record { this@drawWithContent.drawContent() }
                            slot.recordedEntryId = entry.id
                            slot.recordedIndex = index
                        }
                    }
                AppTile(row, icons, generation, iconPx, rowHeight, nameStyle, glowColors, { lift.slotOf(index)?.isFocused == true }, tap.then(look))
            }
        }
        KeepFocusedCellVisible(gridState, columns, fit, glide) { state.focus.rowOf(entry.id) }
    }
}

/**
 * The focused tile's lift, read while drawing (the focus, the scroll, the glide, the animations): the tile (recorded
 * into its [LiftSlot]) drawn larger about its centre over the grid's clip with its deeper shadow, the one light sweep
 * and the ring, and the one it left shrinking back. The same pass as the cover grid's `focusRing`, the figures from
 * the same tokens; a move only redraws.
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
    shadow: CoverShadow,
    glowColors: CoverGlowColors,
    motionOn: State<Boolean>,
    travel: FocusTravel,
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
            val cell = Rect(Offset(item.offset.x.toFloat(), item.offset.y + shift), Size(item.size.width.toFloat(), item.size.height.toFloat()))
            val scale = 1f + (PlumDimens.FOCUSED_ICON_SCALE - 1f) * slot.grow.value
            val lift = PlumDimens.focusedLift.toPx() * slot.grow.value
            val tile = Rect(Offset.Zero, cell.size * scale).let { it.translate(cell.center - it.center - Offset(0f, lift)) }
            val corner = PlumDimens.cornerRow * scale
            val drawCover = slot.recordedIndex == index && slot.recordedEntryId == entry.id
            // The ring and the lifted tile dip with the confirm button or a finger (the cell's own copy stays: a plate hides it).
            val pressed = if (slot.isFocused) minOf(confirmScale(), touch.scaleOf(index)) else 1f
            // The ring travels from the tile it left (rules.md > Focus and current); the tile itself grows in place.
            val ringShift =
                if (slot.isFocused) {
                    ringDrawn = true
                    travel.offset(index, Offset(cell.left, cell.top - shift), size.width, cell.height * RING_LAG_ROWS) - Offset(0f, shift)
                } else {
                    Offset.Zero
                }
            val drawFocused: DrawScope.() -> Unit = {
                if (pressed < 1f && drawCover) drawRoundRect(colors.bg, cell.topLeft, cell.size, CornerRadius(PlumDimens.cornerRow.toPx()))
                withTransform({ scale(pressed, pressed, pivot = tile.center) }) {
                    if (drawCover) {
                        // The plate (background colour) hides the in-place copy; the glow is drawn over it, so inside the
                        // ring it is the same tint as around it and no frame shows. No shadow: only the glow, in the icon's
                        // average colour (the tile's own `sel` while there is none: loading, no icon).
                        drawRoundRect(colors.bg, topLeft = tile.topLeft, size = tile.size, cornerRadius = CornerRadius(corner.toPx()))
                        with(shadow) {
                            drawGlow(tile, corner.toPx(), entry.rows.getOrNull(index)?.let { glowColors[it.id] } ?: CoverGlowColor.lifted(colors.sel), slot.grow.value)
                        }
                        withTransform({
                            translate(cell.left, cell.top - lift)
                            scale(scale, scale, pivot = cell.size.center)
                        }) { drawLayer(slot.layer) }
                    }
                    if (slot.isFocused) {
                        if (sweep.progress.isRunning) drawSweep(tile, corner.toPx(), sweep.progress.value)
                        drawGridFocusRing(tile.topLeft + ringShift, tile.size, corner, colors.accent, colors.bg)
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

/** What a tile shows for its icon: nothing while it loads, the picture, or the stand-in when there is none. */
private sealed interface TileIcon {
    data object Loading : TileIcon

    data class Picture(val image: ImageBitmap) : TileIcon

    data object Missing : TileIcon
}

/**
 * One app: its icon (square, centred) and its name centred under it, at most two lines and "…" after.
 * The icon is taken from memory while composing when it is there, else loaded once per tile (keyed by the
 * row, the size and the icons' generation, never by the focus).
 */
@Composable
private fun AppTile(
    row: GameRowModel,
    icons: HomeAppIcons,
    generation: Int,
    iconPx: Int,
    height: Dp,
    nameStyle: TextStyle,
    glowColors: CoverGlowColors,
    isFocused: () -> Boolean,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val first = remember(row.id, iconPx, icons, generation) { icons.cached(row.id, iconPx)?.let { TileIcon.Picture(it) } ?: TileIcon.Loading }
    val icon by produceState(first, row.id, iconPx, icons, generation) {
        // Restarted when the row, the size, the source or the generation (a package change) changes: what
        // memory holds is shown at once; otherwise the old icon stays until the new answer comes.
        if (first is TileIcon.Picture || value is TileIcon.Loading) value = first
        if (first is TileIcon.Loading) value = icons.load(row.id, iconPx)?.let { TileIcon.Picture(it) } ?: TileIcon.Missing
    }
    // The glow's colour is the icon's average, found once per icon that lands.
    (icon as? TileIcon.Picture)?.let { shown -> LaunchedEffect(row.id, shown) { glowColors.learn(row.id, shown.image, replace = true) } }
    Column(
        modifier = modifier.height(height).padding(vertical = PlumDimens.appsGridTilePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconModifier = Modifier.size(PlumDimens.appsGridIcon)
        when (val shown = icon) {
            is TileIcon.Picture -> Image(shown.image, contentDescription = null, modifier = iconModifier.testTag(HomeTags.APP_ICON))
            TileIcon.Missing -> AppIconStandIn(row.title, iconModifier)
            TileIcon.Loading -> Spacer(iconModifier)
        }
        Spacer(Modifier.height(PlumDimens.appsGridNameGap))
        // Two names in one place: the resting one and the focused one (strong colour, weight 500). Which one draws
        // is read while drawing (the lift slot's focus), so a move redraws and recomposes nothing; the other draws nothing.
        Box(Modifier.fillMaxWidth()) {
            AppName(row.title, nameStyle.copy(textAlign = TextAlign.Center), { colors.text }, { !isFocused() }, Modifier.testTag(HomeTags.APP_NAME))
            AppName(row.title, nameStyle.copy(textAlign = TextAlign.Center, fontWeight = FontWeight((nameStyle.fontWeight?.weight ?: FontWeight.Normal.weight) + FOCUSED_NAME_WEIGHT_STEP)), { colors.strong }, isFocused, Modifier.clearAndSetSemantics {})
        }
    }
}

/** An app's name, at most two lines and "…" after, drawn only while [shown] says so (read while drawing). */
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
        maxLines = 2,
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
private fun AppIconStandIn(
    title: String,
    modifier: Modifier,
) {
    val colors = PlumTheme.colors
    val shape = CircleShape
    Box(modifier.background(colors.sel, shape).testTag(HomeTags.APP_ICON_STAND_IN), contentAlignment = Alignment.Center) {
        BasicText(text = firstLetterOf(title), style = PlumTheme.type.messageTitle.copy(color = colors.text2), maxLines = 1)
    }
}

/** The first character of [title] in upper case, whole (a letter outside the BMP is two chars); empty for an empty name. */
internal fun firstLetterOf(title: String): String {
    val name = title.trim()
    return if (name.isEmpty()) "" else String(Character.toChars(name.codePointAt(0))).uppercase()
}

/** The focused app's name is this much heavier than the others': 400 to 500 at the default text weight. */
private const val FOCUSED_NAME_WEIGHT_STEP = 100
