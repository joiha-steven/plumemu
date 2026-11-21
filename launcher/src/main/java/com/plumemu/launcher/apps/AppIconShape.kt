package com.plumemu.launcher.apps

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * The one shape every app icon wears in Plumemu: a rounded square, whatever shape the app or the device's own mask gives.
 * An adaptive icon is drawn from its two layers under this mask; a legacy icon sits inset on a flat plate of this shape.
 */
object AppIconShape {
    /** The corner radius as a share of the icon's side (the focus rim of an icon uses the same). */
    const val CORNER_SHARE = 0.22f

    /** A legacy icon is drawn at this share of the plate's side. */
    internal const val LEGACY_INSET_SHARE = 0.70f

    /** An adaptive icon's layers are 108 dp and its visible window 72 dp: the layers are drawn this much larger than the icon. */
    private const val ADAPTIVE_LAYER_SCALE = 108f / 72f

    /** How far a transparent-edged icon's own average colour is pulled to black or white for its plate. */
    private const val PLATE_PULL = 0.85f

    /** [drawable] drawn as a [sizePx] square icon in the rounded-square mask (transparent outside it). */
    fun draw(drawable: Drawable, sizePx: Int): Bitmap {
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(content)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            drawAdaptive(drawable, canvas, sizePx)
        } else {
            drawLegacy(drawable, canvas, sizePx)
        }
        return mask(content, sizePx)
    }

    @android.annotation.TargetApi(Build.VERSION_CODES.O)
    private fun drawAdaptive(icon: AdaptiveIconDrawable, canvas: Canvas, sizePx: Int) {
        val layer = Math.round(sizePx * ADAPTIVE_LAYER_SCALE)
        val offset = (sizePx - layer) / 2
        for (part in listOfNotNull(icon.background, icon.foreground)) {
            part.setBounds(offset, offset, offset + layer, offset + layer)
            part.draw(canvas)
        }
    }

    private fun drawLegacy(drawable: Drawable, canvas: Canvas, sizePx: Int) {
        val inner = Math.round(sizePx * LEGACY_INSET_SHARE).coerceAtLeast(1)
        val glyph = Bitmap.createBitmap(inner, inner, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, inner, inner)
        drawable.draw(Canvas(glyph))
        canvas.drawColor(plateColor(glyph))
        val at = (sizePx - inner) / 2f
        canvas.drawBitmap(glyph, at, at, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /**
     * The plate behind a legacy icon, flat: the icon's dominant edge colour when its edge is opaque; otherwise
     * (transparent edges) the glyph's average colour pulled toward white for a dark glyph and toward black for a light one, so no theme enters the cache.
     */
    internal fun plateColor(glyph: Bitmap): Int {
        val w = glyph.width
        val h = glyph.height
        val pixels = IntArray(w * h)
        glyph.getPixels(pixels, 0, w, 0, 0, w, h)
        val buckets = HashMap<Int, IntArray>() // 4 bits a channel -> count, r, g, b sums
        var edge = 0
        var opaqueEdge = 0
        for (y in 0 until h) for (x in 0 until w) {
            val onEdge = x == 0 || y == 0 || x == w - 1 || y == h - 1
            if (!onEdge) continue
            edge++
            val p = pixels[y * w + x]
            if (Color.alpha(p) < OPAQUE) continue
            opaqueEdge++
            val key = (Color.red(p) shr 4 shl 8) or (Color.green(p) shr 4 shl 4) or (Color.blue(p) shr 4)
            val sums = buckets.getOrPut(key) { IntArray(4) }
            sums[0]++
            sums[1] += Color.red(p)
            sums[2] += Color.green(p)
            sums[3] += Color.blue(p)
        }
        if (opaqueEdge * 10 >= edge * 9) {
            val top = buckets.values.maxByOrNull { it[0] }!!
            return Color.rgb(top[1] / top[0], top[2] / top[0], top[3] / top[0])
        }
        var r = 0L
        var g = 0L
        var b = 0L
        var seen = 0
        for (p in pixels) {
            if (Color.alpha(p) < OPAQUE) continue
            r += Color.red(p)
            g += Color.green(p)
            b += Color.blue(p)
            seen++
        }
        if (seen == 0) return 0 // nothing drawn: no plate
        val mean = Color.rgb((r / seen).toInt(), (g / seen).toInt(), (b / seen).toInt())
        val light = (0.299f * Color.red(mean) + 0.587f * Color.green(mean) + 0.114f * Color.blue(mean)) / 255f
        return pull(mean, if (light > 0.5f) 0 else MAX_CHANNEL)
    }

    /** [from] with every channel moved [PLATE_PULL] of the way to [channel] (0 for black, 255 for white). */
    private fun pull(from: Int, channel: Int): Int =
        Color.rgb(
            Math.round(Color.red(from) + (channel - Color.red(from)) * PLATE_PULL),
            Math.round(Color.green(from) + (channel - Color.green(from)) * PLATE_PULL),
            Math.round(Color.blue(from) + (channel - Color.blue(from)) * PLATE_PULL),
        )

    /** [content] as the fill of an anti-aliased rounded square in a fresh transparent bitmap (a DST_IN draw would leave the outside as it was). */
    private fun mask(content: Bitmap, sizePx: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(content, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        val corner = sizePx * CORNER_SHARE
        val shaped = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(shaped).drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), corner, corner, paint)
        return shaped
    }

    private const val OPAQUE = 200
    private const val MAX_CHANNEL = 255
}
