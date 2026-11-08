package com.plumemu.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.plumemu.coreui.components.drawFocusLiftUnder
import com.plumemu.coreui.components.drawFocusRim
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.TouchPressScale
import com.plumemu.coreui.theme.PlumMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** [FocusOutline.index] when no item of the grid is focused. */
internal const val NO_COVER = -1

/**
 * The focus of a cover grid or the Apps grid (design/rules.md > Focus and current; `FocusLift`): the focused item grows, its rim and the
 * light and shadow under it fade in with it, over [PlumMotion.FOCUS_GROW_MS]; the item the focus left shrinks and its rim fades out over
 * [PlumMotion.FOCUS_LEAVE_MS], so the two rims cross-fade and none travels. With animations off the switch is one frame. Each item has its own
 * amount ([liftOf], 0..1), so a held key leaves a trail of items settling, none cut short. At rest nothing animates, so nothing draws a frame.
 *
 * [index] and the amounts are snapshot state: a cell's draw block reads them, so a move redraws the cells and the grid's layer, and nothing recomposes.
 * [confirmDip] is the confirm button's dip of the focused item (1 at rest).
 */
internal class FocusOutline(
    opening: Int,
    private val scope: CoroutineScope,
    private val isMotionOn: () -> Boolean,
    private val confirmDip: () -> Float,
) {
    /** The focused item, or [NO_COVER]. */
    var index by mutableIntStateOf(opening)
        private set

    /** The items with a lift in hand: the focused one and those still settling back. Changed only outside drawing; [version] tells readers. */
    private val lifts = HashMap<Int, Animatable<Float, AnimationVector1D>>()
    private val jobs = HashMap<Int, Job>()
    private var version by mutableIntStateOf(0)

    init {
        if (opening != NO_COVER) lifts[opening] = Animatable(1f)
    }

    /** Whether any item is growing or shrinking: false at rest, when nothing draws a frame for it. */
    val isAnimating: Boolean get() = lifts.values.any { it.isRunning }

    /** How lifted item [item] is now, 0 (at rest) to 1 (focused, settled). Read in draw blocks only. */
    fun liftOf(item: Int): Float {
        version
        return lifts[item]?.value ?: 0f
    }

    /** Every item with a lift now and its amount, for the grid to draw the rim, light and shadow of each. Read in draw blocks only. */
    fun forEachLifted(block: (item: Int, amount: Float) -> Unit) {
        for (item in liftedItems()) block(item, liftOf(item))
    }

    /** The items with a lift in hand, the focused one first. */
    fun liftedItems(): List<Int> {
        version
        val focused = index
        return buildList {
            if (focused != NO_COVER && lifts.containsKey(focused)) add(focused)
            lifts.keys.forEach { if (it != focused) add(it) }
        }
    }

    /** The dip of item [item] now: the confirm button's for the focused one, a finger's for the one it is on; the smaller wins. Read in draw blocks only. */
    fun dipOf(
        item: Int,
        touch: TouchPressScale,
    ): Float = minOf(touch.scaleOf(item), if (item == index) confirmDip() else 1f)

    /** Focus moved to [target] ([NO_COVER]: the grid lost it, or a panel opened). Call it from the code that follows the focus, not while drawing. */
    fun moveTo(target: Int) {
        if (target == index) return
        val left = index
        index = target
        if (left != NO_COVER) drive(left, 0f, PlumMotion.FOCUS_LEAVE_MS)
        if (target != NO_COVER) drive(target, 1f, PlumMotion.FOCUS_GROW_MS)
    }

    private fun drive(
        item: Int,
        to: Float,
        ms: Int,
    ) {
        jobs.remove(item)?.cancel()
        val lift = lifts.getOrPut(item) { Animatable(0f) }
        version++
        // Undispatched: the first value is set in the frame of the move, so an item is never drawn at its end for one frame.
        val job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                if (isMotionOn()) lift.animateTo(to, tween(ms, easing = PlumMotion.Decelerate)) else lift.snapTo(to)
                if (to == 0f && lifts[item] === lift) {
                    lifts.remove(item)
                    version++
                }
            }
        if (job.isActive) jobs[item] = job
    }
}

@Composable
internal fun rememberFocusOutline(
    opening: Int = NO_COVER,
    confirmDip: () -> Float,
    isMotionOn: () -> Boolean,
): FocusOutline {
    val scope = rememberCoroutineScope()
    return remember(scope) { FocusOutline(opening, scope, isMotionOn, confirmDip) }
}

/**
 * Makes the focus follow a grid ([entryId]'s items): the item that has it (the games column, no panel open) is the one lifted. Shared by the
 * cover grid and the Apps grid.
 */
@Composable
internal fun FollowFocus(
    state: StripState,
    entryId: String,
    panelOpen: State<Boolean>,
    outline: FocusOutline,
) {
    LaunchedEffect(state, entryId) {
        snapshotFlow {
            val focus = state.focus
            if (focus.column == Column.Games && !panelOpen.value) focus.rowOf(entryId) else NO_COVER
        }.collect { outline.moveTo(it) }
    }
}

/** Where a lifted item stands, in the grid's pixels, for drawing its light and rim: its frame, the frame's corner, and the two points it scales about (a press's dip and the lift). */
internal class LiftPlacement(
    val frame: Rect,
    val corner: Float,
    val dipPivot: Offset,
    val pivot: Offset,
    /** Where what lies under the item stops (px, grid space): the Apps grid keeps its light off the names. */
    val underBottom: Float = Float.POSITIVE_INFINITY,
    /** Whether the depth shadow is drawn under the item (not for an Apps tile, whose name stands just below). */
    val shadow: Boolean = true,
)

/** Which edges of the grid an item stands on, so its lift grows inward ([FocusLift.pivot]). */
internal data class LiftEdges(val atLeft: Boolean, val atRight: Boolean, val atTop: Boolean)

/** The edges of the box [k] of a row of [count] boxes, the row [rowIndex] of its grid: the first and last of a row, the first row. */
internal fun liftEdges(
    k: Int,
    count: Int,
    rowIndex: Int,
): LiftEdges = LiftEdges(atLeft = k == 0, atRight = k == count - 1, atTop = rowIndex == 0)

/** The cell's draw: [content] drawn dipped by [dip] about [dipPivot] and lifted by [lift] (0..1) about [pivot]; as it is at rest. */
internal fun DrawScope.drawLifted(
    dip: Float,
    dipPivot: Offset,
    lift: Float,
    pivot: Offset,
    content: DrawScope.() -> Unit,
) {
    if (dip >= 1f && lift <= 0f) {
        content()
        return
    }
    val grown = FocusLift.scaleOf(lift)
    withTransform({
        scale(dip, dip, dipPivot)
        scale(grown, grown, pivot)
    }) { content() }
}

/**
 * The grid's own layer for the focus ([FocusLift]), drawn round the lazy grid so the grid's clip never cuts it at the margins and bars: under
 * the cells the light and shadow of each lifted item, over them its rim, each as the item is drawn (dipped, lifted, faded by its amount).
 * [placementOf] says where an item stands now (null: not on screen); [onFocused] is told the focused item's frame (for the launch animation).
 * Reads the focus, the animations and the scroll while drawing: a move or a scroll redraws, nothing recomposes.
 */
internal fun focusLiftLayer(
    outline: FocusOutline,
    touch: TouchPressScale,
    glide: ListGlide,
    colors: PlumComposeColors,
    placementOf: DrawScope.(Int) -> LiftPlacement?,
    onFocused: (Rect) -> Unit,
): Modifier =
    Modifier.drawWithContent {
        val items = outline.liftedItems()
        // While the rows glide, what is drawn is cut where the tiles are cut; at rest it may reach into the bar gaps and the margins.
        val cut = glide.offset != 0f
        val dark = colors.isDark
        val ink = colors.text
        fun DrawScope.each(
            clipsBelow: Boolean,
            draw: DrawScope.(LiftPlacement, Float) -> Unit,
        ) {
            for (item in items) {
                val at = placementOf(item) ?: continue
                val amount = outline.liftOf(item)
                val dip = outline.dipOf(item, touch)
                val grown = FocusLift.scaleOf(amount)
                val body: DrawScope.() -> Unit = {
                    withTransform({
                        scale(dip, dip, at.dipPivot)
                        scale(grown, grown, at.pivot)
                    }) { draw(at, amount) }
                }
                if (clipsBelow && at.underBottom.isFinite()) {
                    clipRect(left = -size.width, top = -size.height, right = size.width * 2, bottom = at.underBottom) { body() }
                } else {
                    body()
                }
            }
        }
        fun DrawScope.under() = each(true) { at, amount -> drawFocusLiftUnder(at.frame, at.corner, amount, dark, ink, at.shadow) }
        fun DrawScope.over() = each(false) { at, amount -> drawFocusRim(at.frame, at.corner, amount, dark, ink) }
        val focused = outline.index
        if (focused != NO_COVER) placementOf(focused)?.let { onFocused(it.frame) }
        if (cut) clipRect(top = 0f, bottom = size.height, left = -size.width, right = size.width * 2) { under() } else under()
        drawContent()
        if (cut) clipRect(top = 0f, bottom = size.height, left = -size.width, right = size.width * 2) { over() } else over()
    }
