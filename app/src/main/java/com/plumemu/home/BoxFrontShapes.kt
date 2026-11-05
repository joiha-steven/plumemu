package com.plumemu.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.plumemu.coreui.theme.POSTER_INK_WHITE
import com.plumemu.coreui.theme.POSTER_PALETTE
import com.plumemu.coreui.theme.PosterDice
import com.plumemu.coreui.theme.PosterLook
import com.plumemu.coreui.theme.boxHighlight
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

/** One circle or rounded square of a drawn box front: [rect] is its bounds in the cover's px (it may reach past the edges). */
internal class BoxShape(
    val circle: Boolean,
    val rect: Rect,
    private val brush: Brush,
    private val corner: Float,
) {
    fun draw(scope: DrawScope) =
        with(scope) {
            if (circle) drawCircle(brush, radius = rect.width / 2f, center = rect.center) else drawRoundRect(brush, rect.topLeft, rect.size, CornerRadius(corner))
        }
}

/** Where a shape's centre may sit, as shares of the field's width and height: corners and edges (so shapes bleed off them like the board's sun), then the inside. */
private val ANCHORS =
    listOf(0.92f to 0.02f, 1.0f to 0.5f, 0.88f to 0.96f, 0.6f to 0.08f, 0.0f to 0.3f, 0.5f to 0.55f, 0.78f to 0.35f, 0.15f to 0.75f, 0.0f to 0.95f, 1.0f to 0.0f)

/** A circle's diameter and a square's side, as shares of the field's shorter side. */
private val CIRCLE_SIZES = listOf(0.45f, 0.62f, 0.9f)
private val SQUARE_SIZES = listOf(0.14f, 0.2f, 0.28f)
private val SHRINKS = listOf(1f, 0.8f, 0.62f)
/** How much of a shape must show inside the field: a circle may bleed off like the board's sun, a square only slightly (a sliver of one looks like a flaw). */
private const val CIRCLE_VISIBLE = 0.45f
private const val SQUARE_VISIBLE = 0.85f
private const val SQUARE_CORNER = 0.11f

/**
 * One to three shapes for a drawn box front, in colours of the palette's other brightness (a deep field gets light shapes and
 * the reverse), the way the board's yellow sun and red square sit on its blue field. Each takes its size and a starting place from
 * [dice] and the first place from there that keeps out of [keepOut] (the title and the small line, so a shape never covers text)
 * and does not sit on another shape; a shape that fits nowhere is made smaller, then left out. Shapes are drawn in order.
 */
internal fun planShapes(
    field: Rect,
    keepOut: List<Rect>,
    look: PosterLook,
    dice: PosterDice,
): List<BoxShape> {
    val deep = look.ink == POSTER_INK_WHITE
    val colours = POSTER_PALETTE.indices.filter { (POSTER_PALETTE[it].ink == POSTER_INK_WHITE) != deep }
    val count = 1 + dice.next(3)
    val base = min(field.width, field.height)
    val placed = ArrayList<BoxShape>(count)
    var previous = -1
    repeat(count) { index ->
        val circle = index == 0 || dice.next(2) == 0
        val full = (if (circle) CIRCLE_SIZES else SQUARE_SIZES)[dice.next(3)] * base
        var entry = colours[dice.next(colours.size)]
        if (entry == previous) entry = colours[(colours.indexOf(entry) + 1) % colours.size]
        val start = dice.next(ANCHORS.size)
        val found =
            SHRINKS.firstNotNullOfOrNull { shrink ->
                val side = full * shrink
                (0 until ANCHORS.size).firstNotNullOfOrNull { step ->
                    val (fx, fy) = ANCHORS[(start + step) % ANCHORS.size]
                    val rect = Rect(Offset(field.left + field.width * fx - side / 2f, field.top + field.height * fy - side / 2f), Size(side, side))
                    rect.takeIf { isFree(it, circle, field, keepOut, placed) }
                }
            }
        if (found != null) {
            previous = entry
            placed += BoxShape(circle, found, if (circle) sunBrush(found, POSTER_PALETTE[entry]) else bevelBrush(found, POSTER_PALETTE[entry]), if (circle) 0f else found.width * SQUARE_CORNER)
        }
    }
    return placed
}

private fun isFree(
    rect: Rect,
    circle: Boolean,
    field: Rect,
    keepOut: List<Rect>,
    placed: List<BoxShape>,
): Boolean {
    val seen = rect.intersect(field)
    if (seen.width <= 0f || seen.height <= 0f || seen.width * seen.height < rect.width * rect.height * (if (circle) CIRCLE_VISIBLE else SQUARE_VISIBLE)) return false
    val radius = rect.width / 2f
    for (text in keepOut) {
        val dx = rect.center.x - rect.center.x.coerceIn(text.left, text.right)
        val dy = rect.center.y - rect.center.y.coerceIn(text.top, text.bottom)
        if (if (circle) hypot(dx, dy) < radius else rect.overlaps(text)) return false
    }
    return placed.none { hypot(it.rect.center.x - rect.center.x, it.rect.center.y - rect.center.y) < (it.rect.width / 2f + radius) * 0.6f }
}

/** The board's sun: `radial-gradient(circle at 34% 30%, light 0%, mid 42%, dark 100%)` over the circle's own box, from one palette entry. */
private fun sunBrush(
    rect: Rect,
    entry: PosterLook,
): Brush {
    val d = rect.width
    return Brush.radialGradient(
        0f to boxHighlight(entry.top),
        0.42f to entry.top,
        1f to entry.band,
        center = Offset(rect.left + d * 0.34f, rect.top + d * 0.30f),
        radius = hypot(d * 0.66f, d * 0.70f),
    )
}

/** The board's square: `linear-gradient(145deg, light 0%, mid 50%, dark 100%)`, the CSS gradient line through the box's centre at 145 degrees. */
private fun bevelBrush(
    rect: Rect,
    entry: PosterLook,
): Brush {
    val angle = Math.toRadians(145.0)
    val dx = sin(angle).toFloat()
    val dy = -cos(angle).toFloat()
    val half = rect.width * (kotlin.math.abs(dx) + kotlin.math.abs(dy)) / 2f
    val c = rect.center
    return Brush.linearGradient(
        0f to entry.top,
        0.5f to entry.bottom,
        1f to entry.band,
        start = Offset(c.x - dx * half, c.y - dy * half),
        end = Offset(c.x + dx * half, c.y + dy * half),
    )
}
