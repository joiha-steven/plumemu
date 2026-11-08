package com.plumemu.coreui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Outline
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Settle
import kotlin.math.abs

/**
 * What a list that draws the focus pill itself ([rememberGlidingPill]) tells its rows: they stop drawing their own pill
 * (see [focusPill]) and the focused one hands over its press dip, so the pill dips with it. Null (the default) outside
 * such a list and with animations off: each row draws its pill, as it always did.
 */
internal val LocalGlidingPill = compositionLocalOf<GlidingPill?> { null }

/** True in a list whose halo is drawn outside it ([GlidingPillHost.drawHalo]), animations on or off: a focused row then draws no halo of its own. */
internal val LocalHaloDrawnOutside = compositionLocalOf { false }

/**
 * The focus pill of a home list, drawn once by the list behind its rows so it can glide from the row it leaves to the row it
 * lands on (the PS Vita's way; docs/design/rules.md › Focus and current): over [PlumMotion.GLIDE_MS] on
 * [PlumMotion.Decelerate], the list glide's own tokens. Nothing recomposes per frame: the pill is a draw block that reads the
 * list's layout, the glide's offset and a [Settle], all while drawing.
 *
 * The pill is drawn at the focused row's drawn place (its laid-out place plus the list glide's translation, so it never
 * runs ahead of or behind its row while the list glides) plus a lag that settles to 0. A move starts the lag where the
 * pill was drawn last frame, so a move during a move never snaps back; the lag is kept within one [stridePx] (plus the gap above the first system). A key held
 * (the next move comes within [PlumMotion.GLIDE_MS] of the last) settles linearly at the speed of the repeats, like the
 * list glide, so the pill stays with the moving focus and never strobes.
 */
@Stable
class GlidingPill internal constructor(
    private val settle: Settle,
    private val nowNanos: () -> Long,
) {
    /** Set by the focused row, while it composes: its press dip, so the pill is scaled with it. */
    internal var press: PressDip? = null

    /** Set by the focused row with a title and a line under it: where its accent bar stands ([StripeAnchor]). */
    internal var stripe: StripeAnchor? = null

    /**
     * Set by the focused row, while it is placed, in a list whose rows differ in height (the panels'): its own top inside its
     * lazy item and its height, in px. Null: the list's fixed row height at the top of the item.
     */
    internal var rowBox: RowBox? = null

    /** True when [rememberGlidingPill] was told the rows differ in height: the focused row then reports its [RowBox]. */
    internal var measuresRows = false

    /**
     * Set while the content of a scrolling column is placed ([GlidingPillHost.contentModifier]): the focused row's [RowBox] is then
     * measured from it, so it holds wherever the row sits in the column (and does not change when the column scrolls).
     */
    internal var content: LayoutCoordinates? = null

    private var lastIndex = NONE
    private var lastTop = Float.NaN
    private var lastMoveAt = Long.MIN_VALUE

    /** Told where the slab is drawn each frame (the games list and search: a start grows from it); null for other lists. */
    internal var origin: com.plumemu.coreui.theme.LaunchOrigin? = null

    /** The node the pill draws in, for [origin]. */
    internal var drawnIn: LayoutCoordinates? = null

    /** True when another node draws this pill's halo ([GlidingPillHost.drawHalo], outside the list's clip): [draw] then leaves it out. */
    internal var haloElsewhere = false

    /** Where the pill is now (top and height in the list's px, the lag included), or null for none; starts a lag on a move, so a second call in one frame gives the same answer. */
    private fun place(
        input: PillInput,
        forHalo: Boolean = false,
    ): Pair<Float, Float>? {
        // With animations off the rows draw the pill; only the halo, drawn outside the list, is placed here, with no lag.
        val on = input.isOn || forHalo
        val index = input.focusedIndex()
        val scroll = input.scroll
        val height: Float
        val rowTop: Float
        if (scroll != null) {
            // A column with a ScrollState: the focused row's box is in the column's content coordinates; the visible window starts at the scroll.
            val box = rowBox
            if (index < 0 || box == null || !on) {
                stopTracking()
                return null
            }
            height = box.height
            rowTop = box.topIn(content) - scroll.value + input.glide.offset
        } else {
            val info = input.listState!!.layoutInfo
            val item = if (index < 0) null else info.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null || !on) {
                stopTracking()
                return null
            }
            val box = if (measuresRows) rowBox else null
            height = box?.height ?: input.heightPx
            // Rows are laid out inside the list's own bounds shifted up by the extra room, then drawn translated by the glide.
            rowTop =
                if (box != null && content != null) {
                    // The box is measured from the lazy list itself ([GlidingPillHost.contentModifier]): a row at the root of its item, or in a wrapper, is the same.
                    box.topIn(content) - input.glide.extraPixels + input.glide.offset
                } else {
                    val fromBottom = if (box != null) box.top else if (input.fromBottom) item.size - height else 0f
                    item.offset + info.beforeContentPadding - input.glide.extraPixels + fromBottom + input.glide.offset
                }
        }
        if (!input.isOn) return rowTop to height
        if (index != lastIndex) {
            if (lastIndex != NONE && !lastTop.isNaN()) startLag(lastTop - rowTop, input.stridePx, input.stridePx + input.extraGapPx)
            lastIndex = index
        }
        val top = rowTop + settle.value
        lastTop = top
        return top to height
    }

    private var warmedFor: Any? = null

    /**
     * Builds, on the low-priority image thread ([BackgroundBuilds]), the cached images the pill's first draw at list width [width] px would build in the
     * frame that shows it (the slab's shadow and inset shadows are per-pixel work of tens of ms): the same draw calls into a 1 px canvas, once per width,
     * look and density. Rows of equal height only. The caller restarts this when the list or focus moves; it is cancelled between its two parts, and a
     * build that ran out of memory is dropped (the draw then builds its image itself). It is the one speculative build there is.
     */
    internal suspend fun warm(
        input: PillInput,
        width: Int,
        density: Density,
        layoutDirection: LayoutDirection,
    ) {
        if (width <= 0 || input.heightPx <= 0f || !input.isOn) return
        val key = listOf(width, input.colors, input.shape, input.heightPx, input.onCard, input.blob, density.density)
        if (key == warmedFor) return
        delay(WARM_DELAY_MS)
        val size = androidx.compose.ui.geometry.Size(width.toFloat(), input.heightPx)
        val colors = input.colors
        val blobColor = if (input.blob) colors.slabBlob else Color.Transparent
        fun drawIn(part: DrawScope.(Outline) -> Unit) {
            CanvasDrawScope().draw(density, layoutDirection, Canvas(ImageBitmap(1, 1)), size) { part(input.shape.createOutline(size, layoutDirection, this)) }
        }
        if (!onImageThread { drawIn { drawSlabOutside(it, colors.slab, colors.slabGlow, blobColor, if (input.onCard) colors.card.fill else colors.bg) } }) return
        if (!onImageThread { drawIn { drawSlabFill(it, colors.slab, input.onCard) } }) return
        warmedFor = key
    }

    internal fun draw(
        scope: DrawScope,
        input: PillInput,
    ) = with(scope) {
        val (top, height) = place(input) ?: run {
            if (input.isOn) origin?.clear()
            // Animations off: the rows draw the fill and edge; the halo (and the light blob) is still drawn here, outside the list's clip, so it is the one motion-on draws.
            if (!input.isOn && !haloElsewhere && input.scroll == null) drawHaloOnly(scope, input)
            return@with
        }
        origin?.note(drawnIn, androidx.compose.ui.geometry.Rect(0f, top, size.width, top + height))
        val colors = input.colors
        val p = press?.progress?.value ?: 0f
        val scale = if (p == 0f) 1f else 1f - (1f - PlumMotion.PRESS_SCALE) * p
        // Settings' lists (a scroll state, or a lazy list measured from itself) clip only above and below: a clip at the side edge that the pill's round cap
        // touches shades one pixel of the cap, which would differ from the row's own pill (the Settings shots).
        val side = if (input.scroll != null || content != null) SIDE_UNCLIPPED_PX else 0f
        val outline = input.shape.createOutline(androidx.compose.ui.geometry.Size(size.width, height), layoutDirection, this)
        // The halo has its own clip: it reaches past the list's sides, where the pill's clip (the list's width) would cut it; above and below it is cut at the list.
        // It stays at the resting size, so a press does not dip it and never repaints the rows it reaches over.
        val reach = PlumDimens.focusHaloReach.toPx()
        // The slab is drawn past its row above and below ([slabOverhang]): the clips leave that much room at the list's ends.
        val over = slabOverhang(height, this)
        if (!haloElsewhere) {
            // A light theme's blob reaches further than the halo, so its clip is wider (the list's own clip above and below still applies).
            val blobColor = if (input.blob) colors.slabBlob else Color.Transparent
            val blob = maxOf(if (blobColor.alpha > 0f) SlabDimens.blobReach.toPx() else 0f, endGlow(colors.slabGlow.alpha > 0f && input.glide.keepsGlowRoom))
            val sideReach = maxOf(haloReach(colors.slabGlow.alpha > 0f, reach), blob)
            clipRect(left = -sideReach - side, top = -over - blob, right = size.width + sideReach + side, bottom = size.height + over + blob) {
                drawOutsideFaded(outline, top, height, over, blob, -sideReach - side, size.width + sideReach + side, colors, blobColor, if (input.onCard) colors.card.fill else colors.bg)
            }
        }
        clipRect(left = -side, top = -over, right = size.width + side, bottom = size.height + over) {
            withTransform({
                translate(0f, top)
                if (scale != 1f) scale(scale, scale, Offset(size.width / 2f, height / 2f))
            }) {
                drawSlabFill(outline, colors.slab, input.onCard, PlumMotion.PRESS_FILL_MIX * p, colors.text)
                drawSlabFace(outline, colors, barShift = stripe?.shiftY ?: 0f, barInset = (input.shape as? InsetFocusPillShape)?.barInset ?: SlabDimens.barInset)
            }
        }
    }

    /** The halo alone, for a node outside the list's clip ([GlidingPillHost.drawHalo]): widened by the reach at the sides, cut at the list above and below. */
    internal fun drawHaloOnly(
        scope: DrawScope,
        input: PillInput,
    ) = with(scope) {
        val (top, height) = place(input, forHalo = true) ?: return@with
        val outline = input.shape.createOutline(androidx.compose.ui.geometry.Size(size.width, height), layoutDirection, this)
        val reach = PlumDimens.focusHaloReach.toPx()
        val over = slabOverhang(height, this)
        val blobColor = if (input.blob) input.colors.slabBlob else Color.Transparent
        val blob = maxOf(if (blobColor.alpha > 0f) SlabDimens.blobReach.toPx() else 0f, endGlow(input.colors.slabGlow.alpha > 0f && input.glide.keepsGlowRoom))
        val sideReach = maxOf(haloReach(input.colors.slabGlow.alpha > 0f, reach), blob)
        clipRect(left = -sideReach, top = -over - blob, right = size.width + sideReach, bottom = size.height + over + blob) {
            drawOutsideFaded(outline, top, height, over, blob, -sideReach, size.width + sideReach, input.colors, blobColor, if (input.onCard) input.colors.card.fill else input.colors.bg)
        }
    }

    private var tailLength = -1f
    private var tailKey = Rect.Zero
    private var tailLow: Rect = Rect.Zero
    private var tailHigh: Rect = Rect.Zero
    private var tailDown: Brush? = null
    private var tailUp: Brush? = null
    private val tailLayer = Paint()

    /**
     * The outside shadow, glow and blob of the pill at [top], clipped [over] + [blob] px past the list's window at each end ([left] to [right] wide): the last
     * [SlabDimens.tailFade] of that clip is drawn into an offscreen layer and masked to transparent, so the clip is no line, over any neighbour drawn earlier.
     * Only a pill whose shadow reaches a band pays for its layer (two small rects of the list's width); the brushes and bounds are kept: a frame builds none
     * unless the list's size changed.
     */
    private fun DrawScope.drawOutsideFaded(
        outline: androidx.compose.ui.graphics.Outline,
        top: Float,
        height: Float,
        over: Float,
        blob: Float,
        left: Float,
        right: Float,
        colors: com.plumemu.coreui.theme.PlumComposeColors,
        blobColor: Color,
        bg: Color,
    ) {
        val clipTop = -over - blob
        val clipBottom = size.height + over + blob
        val length = minOf(SlabDimens.tailFade.toPx(), blob)
        val atLow = top + height + over + blob > clipBottom - length
        val atHigh = top - over - blob < clipTop + length
        if (length <= 0f || (!atLow && !atHigh)) {
            withTransform({ translate(0f, top) }) { drawSlabOutside(outline, colors.slab, colors.slabGlow, blobColor, bg) }
            return
        }
        if (length != tailLength) {
            tailLength = length
            tailDown = Brush.verticalGradient(0f to OPAQUE, 1f to Color.Transparent, startY = 0f, endY = length)
            tailUp = Brush.verticalGradient(0f to Color.Transparent, 1f to OPAQUE, startY = 0f, endY = length)
        }
        val key = Rect(left, clipTop, right, clipBottom)
        if (key != tailKey) {
            tailKey = key
            tailHigh = Rect(left, clipTop, right, clipTop + length)
            tailLow = Rect(left, clipBottom - length, right, clipBottom)
        }
        clipRect(left, clipTop + length, right, clipBottom - length) {
            withTransform({ translate(0f, top) }) { drawSlabOutside(outline, colors.slab, colors.slabGlow, blobColor, bg) }
        }
        if (atHigh) fadedBand(tailHigh, tailUp!!, outline, top, colors, blobColor, bg)
        if (atLow) fadedBand(tailLow, tailDown!!, outline, top, colors, blobColor, bg)
    }

    /** One end band: the shadow drawn in a layer the size of [band], then multiplied by [mask] (opaque to transparent towards the clip edge). */
    private fun DrawScope.fadedBand(
        band: Rect,
        mask: Brush,
        outline: androidx.compose.ui.graphics.Outline,
        top: Float,
        colors: com.plumemu.coreui.theme.PlumComposeColors,
        blobColor: Color,
        bg: Color,
    ) {
        clipRect(band.left, band.top, band.right, band.bottom) {
            val canvas = drawContext.canvas
            canvas.saveLayer(band, tailLayer)
            withTransform({ translate(0f, top) }) { drawSlabOutside(outline, colors.slab, colors.slabGlow, blobColor, bg) }
            withTransform({ translate(0f, band.top) }) {
                // Past the band too: the layer's bounds round outward, and a pixel row the clip covers in part must be masked as well.
                drawRect(mask, topLeft = Offset(band.left, -MASK_RUN_PX), size = androidx.compose.ui.geometry.Size(band.width, band.height + 2 * MASK_RUN_PX), blendMode = BlendMode.DstIn)
            }
            canvas.restore()
        }
    }

    /** How far the glow is drawn past the slab above and below: its whole reach for a list that keeps room for it ([ListGlide.keepsGlowRoom]), else none: such a list ends at the overhang. */
    private fun DrawScope.endGlow(glows: Boolean) = if (glows) SlabDimens.glowReach.toPx() else 0f

    /** The glow's side clip: [SlabDimens.glowReach] where the slab glows, else [reach]. */
    private fun DrawScope.haloReach(
        glows: Boolean,
        reach: Float,
    ) = if (glows) maxOf(reach, SlabDimens.glowReach.toPx()) else reach

    private fun stopTracking() {
        lastIndex = NONE
        lastTop = Float.NaN
        if (settle.isSettling) settle.stop()
    }

    private fun startLag(
        lagPx: Float,
        stridePx: Float,
        capPx: Float,
    ) {
        val lag = lagPx.coerceIn(-capPx, capPx)
        val now = nowNanos()
        val since = if (lastMoveAt == Long.MIN_VALUE) Long.MAX_VALUE else now - lastMoveAt
        lastMoveAt = now
        val glideNanos = PlumMotion.GLIDE_MS * NANOS_PER_MS
        if (since !in 0 until glideNanos) {
            settle.start(lag, glideNanos, PlumMotion.Decelerate)
            return
        }
        val rows = if (stridePx > 0f) abs(lag) / stridePx else 1f
        val duration = (since * rows).toLong().coerceIn(PlumMotion.GLIDE_HELD_MIN_MS * NANOS_PER_MS, glideNanos)
        settle.start(lag, duration, LinearEasing)
    }

    private companion object {
        /** Past the frames of a start or a page change. */
        const val WARM_DELAY_MS = 400L
        const val NONE = -1
        const val NANOS_PER_MS = 1_000_000L
        const val SIDE_UNCLIPPED_PX = 64f
        const val MASK_RUN_PX = 2f

        /** Any colour at full alpha: a mask only reads its alpha. */
        val OPAQUE = Color.Transparent.copy(alpha = 1f)
    }
}

/** A focused row's place in its lazy item: [top] from the item's top and [height], in px. */
internal class RowBox(
    val top: Float,
    val height: Float,
    /** The row's node, kept so a list measured from itself ([GlidingPillHost.contentModifier]) reads the row's place live: a scroll moves a row nested in a wrapper without placing it again. */
    val coordinates: LayoutCoordinates? = null,
) {
    /** The row's top from [content] now; the placed [top] when the row or the list is not attached. */
    fun topIn(content: LayoutCoordinates?): Float =
        if (content != null && content.isAttached && coordinates != null && coordinates.isAttached) content.localPositionOf(coordinates, Offset.Zero).y else top
}

internal class PillInput(
    /** The lazy list the pill is drawn for; null for a column with a [scroll] state. */
    val listState: LazyListState?,
    val scroll: ScrollState?,
    val glide: ListGlide,
    val colors: com.plumemu.coreui.theme.PlumComposeColors,
    val shape: Shape,
    val heightPx: Float,
    val stridePx: Float,
    val extraGapPx: Float,
    val fromBottom: Boolean,
    val isOn: Boolean,
    /** The pill sits in a raised card ([LocalSlabOnCard]): its fill is the lighter one. */
    val onCard: Boolean = false,
    /** The games list: on a light theme its slab carries the board's blue blob ([SlabDimens.blobDrop]); other lists and cards do not. */
    val blob: Boolean = false,
    val focusedIndex: () -> Int,
)

/** A [GlidingPill] and the modifier that draws it. */
@Stable
class GlidingPillHost internal constructor(
    internal val pill: GlidingPill?,
    /** The pill that places the halo even with animations off, when [pill] is null; null for a list whose halo the rows draw. */
    internal val haloPill: GlidingPill? = null,
    /** Goes on the list before its [ListGlide.modifier]: draws the pill behind the rows, not translated by the glide's layer. */
    val modifier: Modifier,
    /**
     * Optional on a lazy list, needed on a scrolling column ([rememberGlidingPill] with a ScrollState): goes on the lazy list after its
     * `glide.modifier` (or on the column inside the scroll), so the focused row is measured from the list itself and sits right
     * wherever it is in its item. Without it a lazy list's rows must be the root of their item, as the game panels' are.
     */
    val contentModifier: Modifier = Modifier,
    internal val input: PillInput? = null,
) {
    /**
     * Draws this pill's halo from a node that sits outside the list's clip (see `haloOutside` of [rememberGlidingPill]): call it in a
     * `drawBehind` of a parent that has the list's size; it reads what the pill reads, so it moves with it and recomposes nothing.
     */
    fun drawHalo(scope: DrawScope) {
        if (haloPill != null && input != null) haloPill.drawHaloOnly(scope, input)
    }

    /** Provide it around the list so its rows stop drawing their own pill. */
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGlidingPill provides pill, LocalHaloDrawnOutside provides (haloPill != null), content = content)
    }
}

/**
 * The home lists' gliding focus pill (see [GlidingPill]): put [GlidingPillHost.modifier] on the lazy column before
 * `glide.modifier`, and wrap the column in [GlidingPillHost.Provide]. [focusedIndex] is the lazy item that wears the pill, or
 * -1 for none; it is read while drawing. The pill is [rowHeight] tall; with [fromBottom] it sits at the bottom of its item
 * (the item has a gap above its row, as the first system's) instead of the top. [stridePx] is the distance between two rows.
 * With animations off nothing is drawn here and the rows draw their pill, as before.
 */
@Composable
fun rememberGlidingPill(
    listState: LazyListState,
    glide: ListGlide,
    rowHeight: Dp,
    stride: Dp,
    /** Extra distance between two particular rows (the gap above the first system): the lag may reach it, so that step does not jump. */
    extraGap: Dp = 0.dp,
    shape: Shape = FocusPillShape,
    fromBottom: Boolean = false,
    /** The rows differ in height (a note or a badge under a label, a label on three lines): the focused row's own box is the pill's. */
    rowsVary: Boolean = false,
    /** The halo is drawn by [GlidingPillHost.drawHalo] from a parent outside the list's clip: the pill leaves it out. */
    haloOutside: Boolean = false,
    /** The slab draws the light themes' blue blob (the games list only). */
    blob: Boolean = false,
    /** Told where the slab is drawn: the games list and search pass the home's [com.plumemu.coreui.theme.LocalLaunchOrigin]. */
    origin: com.plumemu.coreui.theme.LaunchOrigin? = null,
    /** The list's width in px when known before it is laid out: its slab's images are then built in the background once the page is composed, not in the frame that first shows it. */
    warmWidthPx: Int = 0,
    /** Px the slab's right end stands from the list's, read while drawing ([drawWithEndMoved]): a list that changes width with the strip's stop. */
    endMovedPx: (() -> Float)? = null,
    focusedIndex: () -> Int,
): GlidingPillHost {
    val scope = rememberCoroutineScope()
    val clock = rememberUpdatedState(LocalGlideClock.current)
    val isOn = PlumTheme.motion.isOn
    val colors = PlumTheme.colors
    val density = LocalDensity.current
    val onCard = LocalSlabOnCard.current
    val layoutDirection = LocalLayoutDirection.current
    val latestIndex = rememberUpdatedState(focusedIndex)
    val pill = remember(scope) { GlidingPill(Settle(scope)) { clock.value() } }
    val input =
        remember(listState, glide, colors, shape, rowHeight, stride, extraGap, fromBottom, isOn, density, onCard, blob) {
            PillInput(listState, null, glide, colors, shape, with(density) { rowHeight.roundToPx() }.toFloat(), with(density) { stride.toPx() }, with(density) { extraGap.toPx() }, fromBottom, isOn, onCard, blob) { latestIndex.value() }
        }
    pill.measuresRows = rowsVary
    pill.haloElsewhere = haloOutside
    pill.origin = origin
    if (warmWidthPx > 0 && !rowsVary) {
        // Restarts on any movement of the list or the focus: nothing is built while a key is held or a page scrolls.
        LaunchedEffect(pill, input, warmWidthPx) {
            snapshotFlow { Triple(latestIndex.value(), input.listState?.firstVisibleItemIndex, input.listState?.firstVisibleItemScrollOffset) }
                .collectLatest { pill.warm(input, warmWidthPx, density, layoutDirection) }
        }
    }
    // A list of equal rows has its halo placed here even with animations off (no row box needed); one whose rows differ in height leaves it to the rows.
    val haloHere = haloOutside || !rowsVary
    return remember(pill, input, haloOutside, haloHere, endMovedPx) {
        GlidingPillHost(
            if (isOn) pill else null,
            if (haloHere) pill else null,
            Modifier.onPlaced { pill.drawnIn = it }.drawBehind { if (endMovedPx == null) pill.draw(this, input) else drawWithEndMoved(endMovedPx()) { pill.draw(this, input) } },
            Modifier.onPlaced { pill.content = it },
            input,
        )
    }
}

/**
 * The same pill for a column that scrolls with a [ScrollState] (Settings' columns, whose rows are all composed): put
 * [GlidingPillHost.modifier] on the column before `glide.modifier` (outside the scroll), [GlidingPillHost.contentModifier] on the
 * column inside the scroll, and wrap it in [GlidingPillHost.Provide]. Rows differ in height, so the focused row reports its own box.
 * The pill takes [shape] (a row that is drawn inset passes the same inset shape). [focusedIndex] only tells when the focus moves
 * (-1: none); the pill sits where the focused row reports itself.
 */
@Composable
fun rememberGlidingPill(
    scroll: ScrollState,
    glide: ListGlide,
    stride: Dp,
    /** The most one step may be longer than a [stride] (a heading between two rows, a tall row): the pill's lag may span it, so that step does not jump. */
    extraGap: Dp = 0.dp,
    shape: Shape = FocusPillShape,
    focusedIndex: () -> Int,
): GlidingPillHost {
    val scope = rememberCoroutineScope()
    val clock = rememberUpdatedState(LocalGlideClock.current)
    val isOn = PlumTheme.motion.isOn
    val colors = PlumTheme.colors
    val density = LocalDensity.current
    val onCard = LocalSlabOnCard.current
    val latestIndex = rememberUpdatedState(focusedIndex)
    val pill = remember(scope) { GlidingPill(Settle(scope)) { clock.value() } }
    val input =
        remember(scroll, glide, colors, shape, stride, extraGap, isOn, density, onCard) {
            PillInput(null, scroll, glide, colors, shape, 0f, with(density) { stride.toPx() }, with(density) { extraGap.toPx() }, false, isOn, onCard) { latestIndex.value() }
        }
    pill.measuresRows = true
    return remember(pill, input) {
        GlidingPillHost(
            pill = if (isOn) pill else null,
            modifier = Modifier.drawBehind { pill.draw(this, input) },
            contentModifier = Modifier.onPlaced { pill.content = it },
        )
    }
}
