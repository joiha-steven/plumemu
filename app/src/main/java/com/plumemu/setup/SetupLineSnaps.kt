package com.plumemu.setup

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextLayoutResult

/**
 * Where a scrolling step may come to rest so that no line of its title block is cut by the top of the visible area
 * (at 576 x 432 the Ready step once rested with only "Settings." of the paragraph showing).
 * The narrow layout scrolls the title with the content, so the scroll to a focused button can end half way through
 * a paragraph. A rest is one of: not scrolled, or a line of the title or the description at the top.
 * A card of the content ([restCard]) is a rest too: the scroll never ends with a card cut by the top.
 * Offsets are scroll values in pixels, measured from [anchor] (the content's first item).
 */
internal class LineSnaps {
    private var anchor: LayoutCoordinates? = null
    private val textTops = FloatArray(PARTS)
    private val lineTops = Array(PARTS) { FloatArray(0) }
    private var offsets = IntArray(0)
    private var textEnd = Int.MAX_VALUE
    private val cardTops = HashMap<String, Float>()

    /** Changes when the offsets do, so a reader of it is asked again. */
    var version by mutableIntStateOf(0)
        private set

    /** Marks the content's first item: the offsets count from its top. */
    fun Modifier.anchoring(): Modifier = onGloballyPositioned { anchor = it }

    /** A text of the block ([part] 0 = title, 1 = description): [layout] is its lines, the node reports where it is. */
    fun Modifier.textPart(part: Int): Modifier =
        onGloballyPositioned { coordinates ->
            val from = anchor?.takeIf { it.isAttached } ?: return@onGloballyPositioned
            textTops[part] = from.localPositionOf(coordinates, Offset.Zero).y
            rebuild()
        }

    /** A card of the content: the top of the visible area may rest on its top. */
    fun Modifier.card(key: String): Modifier =
        onGloballyPositioned { coordinates ->
            val from = anchor?.takeIf { it.isAttached } ?: return@onGloballyPositioned
            cardTops[key] = from.localPositionOf(coordinates, Offset.Zero).y
            rebuild()
        }

    fun dropCard(key: String) {
        if (cardTops.remove(key) != null) rebuild()
    }

    fun layoutOf(part: Int, layout: TextLayoutResult) {
        // Every line's top, and the last line's bottom: a text that ends at the top of the visible area is whole gone, not cut.
        lineTops[part] = FloatArray(layout.lineCount + 1) { if (it < layout.lineCount) layout.getLineTop(it) else layout.getLineBottom(layout.lineCount - 1) }
        rebuild()
    }

    private fun rebuild() {
        val all = mutableListOf(0)
        for (part in 0 until PARTS) for (top in lineTops[part]) all += (textTops[part] + top).toInt()
        // Where the block's last line ends: the scroll's end can fall a little past it (see [isRest]).
        textEnd = all.max()
        for (top in cardTops.values) all += top.toInt()
        val sorted = all.filter { it >= 0 }.distinct().sorted().toIntArray()
        if (!sorted.contentEquals(offsets)) {
            offsets = sorted
            version++
        }
    }

    /** The first rest at or past [value] (px); [value] itself when it is past the last line (content, not text, is at the top then). */
    fun restAtOrPast(value: Int): Int = offsets.firstOrNull { it >= value } ?: value

    /** True at a rest (to a pixel), or at most [reach] past the block's end (the scroll's end can fall just past it, where no line is left to reach). */
    fun isRest(value: Int, reach: Int): Boolean = offsets.any { value - it in 0..ROUNDING } || value - textEnd in 0..reach

    private companion object {
        const val PARTS = 2

        /** A scroll lands on a whole pixel at or just past a rest that was cut to one. */
        const val ROUNDING = 1
    }
}

/**
 * The scroll to a focused item, instant (see [InstantReveal]), and ending on one of [snaps]' rests when it scrolls down:
 * a little further than the item needs, never so far that the item leaves the top.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class LineSnapReveal(
    private val scroll: ScrollState,
    private val snaps: LineSnaps,
) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = snap()

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val needed = super.calculateScrollDistance(offset, size, containerSize)
        if (needed <= 0f) return needed
        val now = scroll.value
        val rest = snaps.restAtOrPast((now + needed).toInt())
        // Never past the scroll's end (a request that cannot be met keeps the scroll asking), and the item stays below the top.
        val distance = minOf((rest - now).toFloat(), maxOf((scroll.maxValue - now).toFloat(), needed))
        return if (offset - distance < 0f) needed else distance
    }
}

/** [inner] seen through the window without the shadow room [room] added on top and below it. */
@OptIn(ExperimentalFoundationApi::class)
internal class WithoutRoom(
    private val inner: BringIntoViewSpec,
    private val room: () -> Float,
) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> get() = inner.scrollAnimationSpec

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val by = room()
        return inner.calculateScrollDistance(offset - by, size, containerSize - by * 2)
    }
}

/** The steps' scroll hands its [LineSnaps] down, so a card of any step can mark itself a rest; null where nothing rests on lines. */
internal val LocalLineSnaps = compositionLocalOf<LineSnaps?> { null }

/** Marks this item a card the scroll rests on whole (see [LineSnaps.card]); [key] tells the cards of a step apart. */
@Composable
internal fun Modifier.restCard(key: String): Modifier {
    val snaps = LocalLineSnaps.current ?: return this
    DisposableEffect(snaps, key) { onDispose { snaps.dropCard(key) } }
    return with(snaps) { card(key) }
}
