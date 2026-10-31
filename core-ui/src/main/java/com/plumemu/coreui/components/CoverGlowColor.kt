package com.plumemu.coreui.components

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.plumemu.coreui.theme.PlumDimens

/**
 * The colour a focused cover glows in (docs/design/rules.md › Grid › Cover rim and glow): the average colour of its
 * picture, found once when the picture is loaded, then lifted so that a dark cover still gives a visible glow.
 */
object CoverGlowColor {
    /** Samples per side of the downsampled picture: 16 x 16 is plenty for an average and costs microseconds. */
    private const val SAMPLE = 16

    /** Pixels less opaque than this (of 255) do not count: an icon's transparent corners are not its colour. */
    private const val MIN_ALPHA = 16

    /**
     * The average colour of [pixels] (ARGB, as `Bitmap.getPixels` gives them), each weighted by its opacity, or null when none
     * is opaque enough to count. Not lifted: see [lifted].
     */
    fun average(pixels: IntArray): Color? {
        var weight = 0L
        var r = 0L
        var g = 0L
        var b = 0L
        for (p in pixels) {
            val a = p ushr 24
            if (a < MIN_ALPHA) continue
            weight += a
            r += a * ((p shr 16) and 0xFF)
            g += a * ((p shr 8) and 0xFF)
            b += a * (p and 0xFF)
        }
        if (weight == 0L) return null
        return Color(r.toFloat() / (weight * 255f), g.toFloat() / (weight * 255f), b.toFloat() / (weight * 255f))
    }

    /** [color] scaled up, its hue kept, until its brightest channel is [min]; black becomes the grey of that brightness. */
    fun lifted(
        color: Color,
        min: Float = PlumDimens.COVER_GLOW_MIN_VALUE,
    ): Color {
        val top = maxOf(color.red, color.green, color.blue)
        if (top >= min) return color
        if (top < 0.004f) return Color(min, min, min)
        val k = min / top
        return Color(color.red * k, color.green * k, color.blue * k)
    }

    /**
     * [SAMPLE] x [SAMPLE] ARGB pixels of [source]. A HARDWARE bitmap (what Coil returns by default) cannot be read: from API 28 it
     * is scaled (the result is HARDWARE too) and then copied to ARGB_8888; before 28 it cannot even be scaled, so it is copied
     * to ARGB_8888 first. [isHardware] and [sdk] are parameters so tests can take either branch.
     */
    internal fun samplePixels(
        source: Bitmap,
        isHardware: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && source.config == Bitmap.Config.HARDWARE,
        sdk: Int = Build.VERSION.SDK_INT,
    ): IntArray {
        val readable =
            when {
                !isHardware -> Bitmap.createScaledBitmap(source, SAMPLE, SAMPLE, true)
                sdk >= Build.VERSION_CODES.P -> Bitmap.createScaledBitmap(source, SAMPLE, SAMPLE, true).copy(Bitmap.Config.ARGB_8888, false)
                else -> Bitmap.createScaledBitmap(source.copy(Bitmap.Config.ARGB_8888, false), SAMPLE, SAMPLE, true)
            }
        val pixels = IntArray(SAMPLE * SAMPLE)
        readable.getPixels(pixels, 0, SAMPLE, 0, 0, SAMPLE, SAMPLE)
        return pixels
    }

    /**
     * The lifted average colour of [image], read from a [SAMPLE] x [SAMPLE] copy of it; null for an image with no opaque pixel,
     * and null (logged) when the pixels cannot be read, so the cover glows in its tile's colour instead of crashing.
     * Does real work: call it off the main thread.
     */
    fun of(
        image: ImageBitmap,
        read: (Bitmap) -> IntArray = { samplePixels(it) },
    ): Color? =
        try {
            val source = image.asAndroidBitmap()
            if (source.width <= 0 || source.height <= 0) null else average(read(source))?.let { lifted(it) }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Cannot read a cover's pixels for its glow colour; the glow uses the tile colour", e)
            null
        }

    internal const val TAG = "CoverGlowColor"
}
