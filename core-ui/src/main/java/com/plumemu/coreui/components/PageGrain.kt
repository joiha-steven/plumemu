package com.plumemu.coreui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.PorterDuff
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import com.plumemu.coreui.theme.LocalPageGrain
import com.plumemu.coreui.theme.MaskOpaque
import com.plumemu.coreui.theme.PageGrainLook
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.roundToInt
import kotlin.random.Random

/** How much noise the page carries: the page colour plus a fine, monochrome, uniform offset on every pixel. */
enum class PageGrain(
    /** Peak offset per channel on a dark page ([amplitudeFor] scales it for a light one). */
    val amplitude: Int,
) {
    Off(0),
    Light(PageGrainLook.LIGHT_AMPLITUDE),
    Strong(PageGrainLook.STRONG_AMPLITUDE),
    ;

    /** The peak offset that shows as much on a page of colour [page]: light pages get more. */
    fun amplitudeFor(page: Color): Int = if (page.luminance() > PageGrainLook.LIGHT_PAGE_LUMINANCE) (amplitude * PageGrainLook.LIGHT_PAGE_SCALE).roundToInt() else amplitude
}

/** Relative luminance of an sRGB colour (the grain's only use: dark page or light page). */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** The tile's pixels: [page] plus one uniform offset in [-amplitude, amplitude] on R, G and B alike, alpha 255, the same for the same input. */
fun pageGrainPixels(
    page: Int,
    amplitude: Int,
    side: Int = PageGrainLook.TILE_SIDE,
): IntArray {
    val random = Random(PageGrainLook.SEED)
    val r0 = (page shr 16) and 0xFF
    val g0 = (page shr 8) and 0xFF
    val b0 = page and 0xFF
    return IntArray(side * side) {
        val n = if (amplitude == 0) 0 else random.nextInt(-amplitude, amplitude + 1)
        (0xFF shl 24) or ((r0 + n).coerceIn(0, 255) shl 16) or ((g0 + n).coerceIn(0, 255) shl 8) or (b0 + n).coerceIn(0, 255)
    }
}

/** The one tile in use: built on first draw, replaced when the page colour or the strength changes. Main thread only. */
internal object PageGrainTile {
    private var key: Pair<Int, Int>? = null
    private var bitmap: Bitmap? = null
    private var brush: Brush? = null
    private val fades = object : LinkedHashMap<FadeKey, Brush>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<FadeKey, Brush>?): Boolean = size > MAX_FADES
    }

    /** How many tiles have been built (a test reads it: a frame must not build one). */
    var built = 0
        private set

    /** How many fade shaders have been built (a frame at rest must not build one). */
    var fadesBuilt = 0
        private set

    private data class FadeKey(val tile: Pair<Int, Int>, val height: Int, val solidAtBottom: Boolean)

    private fun tileFor(
        page: Color,
        amplitude: Int,
    ): Bitmap {
        val wanted = page.toArgb() to amplitude
        bitmap?.takeIf { key == wanted }?.let { return it }
        val side = PageGrainLook.TILE_SIDE
        val made = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        made.setPixels(pageGrainPixels(wanted.first, amplitude), 0, side, 0, 0, side, side)
        built++
        key = wanted
        bitmap = made
        brush = null
        tileShader = null
        fades.clear()
        return made
    }

    fun brushFor(
        page: Color,
        amplitude: Int,
    ): Brush {
        val tile = tileFor(page, amplitude)
        return brush ?: ShaderBrush(ImageShader(tile.asImageBitmap(), TileMode.Repeated, TileMode.Repeated)).also { brush = it }
    }

    /**
     * The tile seen through an alpha ramp over [height] px (whole px), in one shader brush whose origin is the band's own top-left: clear to
     * opaque downwards when [solidAtBottom], else upwards. The grain is noise, so it need not line up with the page behind. Kept per tile,
     * height and direction.
     */
    fun fadeBrushFor(
        page: Color,
        amplitude: Int,
        height: Int,
        solidAtBottom: Boolean,
    ): Brush {
        val tile = tileFor(page, amplitude)
        val wanted = FadeKey(key!!, height, solidAtBottom)
        fades[wanted]?.let { return it }
        val grain = tileShader ?: BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also { tileShader = it }
        val clear = android.graphics.Color.TRANSPARENT
        val opaque = MaskOpaque.toArgb()
        val ramp = LinearGradient(0f, 0f, 0f, height.toFloat(), if (solidAtBottom) intArrayOf(clear, opaque) else intArrayOf(opaque, clear), null, Shader.TileMode.CLAMP)
        fadesBuilt++
        return ShaderBrush(ComposeShader(grain, ramp, PorterDuff.Mode.DST_IN)).also { fades[wanted] = it }
    }

    /** The tile's one bitmap shader, shared by every fade of it. */
    private var tileShader: BitmapShader? = null

    // A list's fade changes height with the scroll (its reach), a px at a time: every height of both directions is kept once built, so a
    // scroll builds nothing after its first pass. Each entry is a small gradient and a compose shader over the shared tile shader.
    private const val MAX_FADES = 512
}

/**
 * Paints the page colour fading in over the rectangle at [topLeft] of [size]: clear to [page] downwards when [solidAtBottom], else upwards
 * (the gradient of a list's edge fade). With [grain] off that is the plain gradient. With grain on it is the page's grain tile seen through
 * the same alpha ramp, in one draw call with no layer, so the fade is grain too, not a smooth band on a grained page. The tile starts at the
 * band's own top-left (grain is noise: it need not line up with the page). One shader is cached per tile, whole-px band height and direction.
 */
fun DrawScope.drawPageFade(
    page: Color,
    grain: PageGrain,
    topLeft: Offset,
    size: Size,
    solidAtBottom: Boolean,
) {
    if (grain == PageGrain.Off) {
        val clear = page.copy(alpha = 0f)
        drawRect(
            Brush.verticalGradient(if (solidAtBottom) listOf(clear, page) else listOf(page, clear), startY = topLeft.y, endY = topLeft.y + size.height),
            topLeft = topLeft,
            size = size,
        )
        return
    }
    // The shader is cut to whole px: the rect is drawn at that height, a fraction of a px of a fade is not seen.
    val height = size.height.roundToInt()
    if (height <= 0) return
    val brush = PageGrainTile.fadeBrushFor(page, grain.amplitudeFor(page), height, solidAtBottom)
    translate(topLeft.x, topLeft.y) { drawRect(brush, size = Size(size.width, height.toFloat())) }
}

/**
 * Paints the page: [PlumTheme] colours' `bg` (read while drawing, so a moved colour redraws without recomposing); with [grain] on, the repeating
 * grain tile of that colour instead, with one rect and no per-frame work.
 */
@Composable
fun Modifier.pageBackground(
    grain: PageGrain = LocalPageGrain.current,
    /** Under 1: a scrim over the screen behind, that much of the page. */
    alpha: Float = 1f,
): Modifier {
    val colors = PlumTheme.colors
    return drawBehind { drawPage(colors.bg, grain, alpha) }
}

/** Fills this scope with the page: [page] flat with [grain] off, else the grain tile of that colour. [alpha] under 1 lets the screen behind show. */
fun DrawScope.drawPage(
    page: Color,
    grain: PageGrain,
    alpha: Float = 1f,
) {
    if (grain == PageGrain.Off) {
        drawRect(page, alpha = alpha)
    } else {
        drawRect(PageGrainTile.brushFor(page, grain.amplitudeFor(page)), alpha = alpha)
    }
}
