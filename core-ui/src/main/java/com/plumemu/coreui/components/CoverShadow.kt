package com.plumemu.coreui.components

import android.graphics.BlurMaskFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The soft shadow under a grid cover (docs/design/rules.md › Grid › Cover shadow): black, blurred,
 * pushed down, drawn behind the cover. A blur is too dear to run per frame (and a mask filter
 * does not draw on a hardware canvas before Android 9), so each look is blurred once, in software,
 * into a small ALPHA_8 bitmap of a rounded rectangle just big enough for its corners, and drawn
 * stretched as nine slices at any cover size: nothing is computed while the grid is idle.
 */
@Stable
class CoverShadow internal constructor(
    private val density: Density,
    private val resting: Look,
    private val lifted: Look,
    private val glow: Look = Look(PlumDimens.coverGlowBlur, PlumDimens.coverGlowOffsetY, PlumDimens.COVER_GLOW_ALPHA_DARK, fullStrength = true),
    /** The rim's colour at its opacity ([Color.Transparent]: this look has no rim, a light background's). */
    val rim: Color = Color.Transparent,
) {
    /** One look: blur radius, downward offset and opacity of the black. */
    internal class Look(
        val blur: Dp,
        val offsetY: Dp,
        val alpha: Float,
        /**
         * The slice's blurred shape is made at least as wide as the blur needs to reach its full strength in the middle (a
         * shape of only two corners' width, blurred widely, never gets there). 
         */
        val fullStrength: Boolean = false,
    ) {
        fun blurPx(density: Density): Int = with(density) { blur.toPx() }.roundToInt().coerceAtLeast(1)

        fun offsetPx(density: Density): Float = with(density) { offsetY.toPx() }
    }

    // The slices already blurred, found by a linear scan of primitive keys (corner and blur px in one Long): a grid
    // has two or three looks, so a lookup allocates nothing, and neither does the draw (the four edges and the source
    // offsets of the nine slices go through the arrays below, which draw reuses; the draw thread is one).
    private var sliceKeys = LongArray(4)
    private var slices = arrayOfNulls<ImageBitmap>(4)
    private var sliceCount = 0
    private val xs = IntArray(4)
    private val ys = IntArray(4)
    private val sx = IntArray(4)
    private val sy = IntArray(4)

    private fun sliceOf(
        c: Int,
        blur: Int,
        corner: Float,
    ): ImageBitmap {
        val key = (c.toLong() shl 32) or blur.toLong()
        for (i in 0 until sliceCount) if (sliceKeys[i] == key) return checkNotNull(slices[i])
        if (sliceCount == sliceKeys.size) {
            sliceKeys = sliceKeys.copyOf(sliceCount * 2)
            slices = slices.copyOf(sliceCount * 2)
        }
        val slice = blurred(c, blur, corner)
        sliceKeys[sliceCount] = key
        slices[sliceCount] = slice
        sliceCount++
        return slice
    }

    /** The shadow of the cover at [cover] (px) with [corner] (px): the resting one, or the lifted one of the focused cover, at [strength] (0..1) of its opacity. */
    fun DrawScope.draw(
        cover: Rect,
        corner: Float,
        isLifted: Boolean = false,
        strength: Float = 1f,
    ) = draw(cover.left, cover.top, cover.right, cover.bottom, corner, isLifted, strength)

    /** The same for a cover given by its edges, so that a caller draws without making a [Rect]. */
    fun DrawScope.draw(
        coverLeft: Float,
        coverTop: Float,
        coverRight: Float,
        coverBottom: Float,
        corner: Float,
        isLifted: Boolean = false,
        strength: Float = 1f,
    ) {
        val look = if (isLifted) lifted else resting
        val alpha = look.alpha * strength
        if (alpha <= 0f) return
        val slice = place(look, coverLeft, coverTop, coverRight, coverBottom, corner)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                val dw = xs[col + 1] - xs[col]
                val dh = ys[row + 1] - ys[row]
                if (dw <= 0 || dh <= 0) continue
                drawImage(
                    image = slice,
                    srcOffset = IntOffset(sx[col], sy[row]),
                    srcSize = IntSize(sx[col + 1] - sx[col], sy[row + 1] - sy[row]),
                    dstOffset = IntOffset(xs[col], ys[row]),
                    dstSize = IntSize(dw, dh),
                    alpha = alpha,
                    filterQuality = FilterQuality.Low,
                )
            }
        }
    }

    /** The glow's paint, reused by every draw (the draw thread is one): an ALPHA_8 slice is drawn in the paint's colour. */
    private val glowPaint = Paint().apply { filterQuality = FilterQuality.Low }

    /**
     * The glow of the focused cover at [cover] (px) with [corner] (px): its blurred shape in [color], pushed down a little, at
     * [strength] (0..1) of its opacity (the cover's growth, so it fades in and out with it). Draw it after the lifted shadow
     * and before the cover.
     */
    fun DrawScope.drawGlow(
        cover: Rect,
        corner: Float,
        color: Color,
        strength: Float,
    ) {
        val alpha = glow.alpha * strength
        if (alpha <= 0f) return
        val slice = place(glow, cover.left, cover.top, cover.right, cover.bottom, corner)
        glowPaint.color = color.copy(alpha = alpha)
        drawIntoCanvas { canvas ->
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    val dw = xs[col + 1] - xs[col]
                    val dh = ys[row + 1] - ys[row]
                    if (dw <= 0 || dh <= 0) continue
                    canvas.drawImageRect(
                        image = slice,
                        srcOffset = IntOffset(sx[col], sy[row]),
                        srcSize = IntSize(sx[col + 1] - sx[col], sy[row + 1] - sy[row]),
                        dstOffset = IntOffset(xs[col], ys[row]),
                        dstSize = IntSize(dw, dh),
                        paint = glowPaint,
                    )
                }
            }
        }
    }

    /** Finds [look]'s slice and sets the nine slices' edges ([xs], [ys]) and source offsets ([sx], [sy]) for a cover given by its edges. */
    private fun place(
        look: Look,
        coverLeft: Float,
        coverTop: Float,
        coverRight: Float,
        coverBottom: Float,
        corner: Float,
    ): ImageBitmap {
        val blur = look.blurPx(density)
        val offset = look.offsetPx(density)
        val c = (if (look.fullStrength) maxOf(ceil(corner).toInt(), (blur * FULL_STRENGTH_SHAPE).toInt()) else ceil(corner).toInt()) + blur
        val slice = sliceOf(c, blur, corner)
        val left = coverLeft.roundToInt() - blur
        val top = (coverTop + offset).roundToInt() - blur
        val right = coverRight.roundToInt() + blur
        val bottom = (coverBottom + offset).roundToInt() + blur
        // A cover smaller than its two corners would overlap its slices: shrink them to fit.
        val cx = minOf(c, (right - left) / 2)
        val cy = minOf(c, (bottom - top) / 2)
        xs[0] = left; xs[1] = left + cx; xs[2] = right - cx; xs[3] = right
        ys[0] = top; ys[1] = top + cy; ys[2] = bottom - cy; ys[3] = bottom
        sx[0] = 0; sx[1] = c; sx[2] = slice.width - c; sx[3] = slice.width
        sy[0] = 0; sy[1] = c; sy[2] = slice.height - c; sy[3] = slice.height
        return slice
    }

    /** A rounded rectangle of [corner] blurred by [blur], with [c] px of corner and blur at each side and a 2 px middle to stretch. */
    private fun blurred(
        c: Int,
        blur: Int,
        corner: Float,
    ): ImageBitmap {
        val side = c * 2 + 2
        val image = ImageBitmap(side, side, ImageBitmapConfig.Alpha8)
        val paint = Paint().apply { asFrameworkPaint().maskFilter = BlurMaskFilter(blur.toFloat(), BlurMaskFilter.Blur.NORMAL) }
        val inset = blur.toFloat()
        Canvas(image).nativeCanvas.drawRoundRect(inset, inset, side - inset, side - inset, corner, corner, paint.asFrameworkPaint())
        check(image.asAndroidBitmap().width == side)
        return image
    }
}

/** Share of the blur radius that a full-strength slice's shape reaches in from the cover's edge (twice the blur's sigma, about 0.58 of the radius). */
private const val FULL_STRENGTH_SHAPE = 1.25f

/** The grid's cover shadows for the colours in view: heavier on a dark background, light on a light one. */
@Composable
fun rememberCoverShadow(): CoverShadow {
    val density = LocalDensity.current
    val isLight = PlumTheme.colors.bg.luminance() > PlumDimens.COVER_SHADOW_LIGHT_BACKGROUND
    return remember(density, isLight) {
        CoverShadow(
            density,
            CoverShadow.Look(PlumDimens.coverShadowBlur, PlumDimens.coverShadowOffsetY, if (isLight) PlumDimens.COVER_SHADOW_ALPHA_LIGHT else PlumDimens.COVER_SHADOW_ALPHA_DARK),
            CoverShadow.Look(PlumDimens.coverShadowLiftedBlur, PlumDimens.coverShadowLiftedOffsetY, if (isLight) PlumDimens.COVER_SHADOW_LIFTED_ALPHA_LIGHT else PlumDimens.COVER_SHADOW_LIFTED_ALPHA_DARK),
            CoverShadow.Look(PlumDimens.coverGlowBlur, PlumDimens.coverGlowOffsetY, if (isLight) PlumDimens.COVER_GLOW_ALPHA_LIGHT else PlumDimens.COVER_GLOW_ALPHA_DARK, fullStrength = true),
            if (isLight) Color.Transparent else PlumDimens.coverRimColor.copy(alpha = PlumDimens.COVER_RIM_ALPHA_DARK),
        )
    }
}

/**
 * Draws [shadow] behind the box this modifier sits on (which is the cover's whole size), at the
 * box's corner of [corner]. Put it outside the cover's clip and outside the focus modifier, so the
 * focused cover's copy does not carry it. [alpha] dims it with a dimmed cover.
 */
fun Modifier.coverShadow(
    shadow: CoverShadow?,
    corner: Dp,
    alpha: Float = 1f,
): Modifier =
    if (shadow == null) {
        this
    } else {
        drawBehind { with(shadow) { draw(0f, 0f, size.width, size.height, corner.toPx(), strength = alpha) } }
    }

/**
 * The rim light: a [PlumDimens.coverEdge]-wide line of [color] just inside the edge of the box being drawn, following [corner]
 * (px), so nothing moves. [alpha] fades it with its picture. [width] is read from the density by default: a caller drawing inside
 * a `GraphicsLayer.record` block passes it (from outside), as reading the density there loops back into the node's own scope. Drawn over the cover, so the focused copy carries it.
 */
fun DrawScope.drawCoverRim(
    color: Color,
    corner: Float,
    alpha: Float = 1f,
    width: Float = PlumDimens.coverEdge.toPx(),
) {
    val inset = width / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - width, size.height - width),
        cornerRadius = CornerRadius((corner - inset).coerceAtLeast(0f)),
        alpha = alpha,
        style = rimStroke(width),
    )
}

// One Stroke for every rim drawn at this width (the width only changes with the density), so drawing allocates none.
private var cachedRimStroke = Stroke(0f)

private fun rimStroke(width: Float): Stroke {
    if (cachedRimStroke.width != width) cachedRimStroke = Stroke(width)
    return cachedRimStroke
}
