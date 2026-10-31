package com.plumemu.setup

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.ScrollState
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
 * Offsets are scroll values in pixels, measured from [anchor] (the content's first item).
 */
internal class LineSnaps {
    private var anchor: LayoutCoordinates? = null
    private val textTops = FloatArray(PARTS)
    private val lineTops = Array(PARTS) { FloatArray(0) }
    private var offsets = IntArray(0)

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

    fun layoutOf(part: Int, layout: TextLayoutResult) {
        // Every line's top, and the last line's bottom: a text that ends at the top of the visible area is whole gone, not cut.
        lineTops[part] = FloatArray(layout.lineCount + 1) { if (it < layout.lineCount) layout.getLineTop(it) else layout.getLineBottom(layout.lineCount - 1) }
        rebuild()
    }

    private fun rebuild() {
        val all = mutableListOf(0)
        for (part in 0 until PARTS) for (top in lineTops[part]) all += (textTops[part] + top).toInt()
        val sorted = all.filter { it >= 0 }.distinct().sorted().toIntArray()
        if (!sorted.contentEquals(offsets)) {
            offsets = sorted
            version++
        }
    }

    /** The first rest at or past [value] (px); [value] itself when it is past the last line (content, not text, is at the top then). */
    fun restAtOrPast(value: Int): Int = offsets.firstOrNull { it >= value } ?: value

    private companion object {
        const val PARTS = 2
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
