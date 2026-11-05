package com.plumemu.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.plumemu.content.CoilCoverImages
import com.plumemu.content.LastSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Benchmark builds only (launch extra `FakeHomeContent.EXTRA_COVERS`): every game has a box-art
 * result and its picture is drawn into a bitmap on a background thread at the asked size, kept
 * in a bounded memory cache as the real decoder keeps its own, so the grid benchmarks meet landing
 * covers, the placeholder under them and cache hits as a signed-in library does.
 */
internal class BenchmarkCoverArt : HomeArtSource {
    override fun observe(keys: List<String>): Flow<Map<String, GameArt>> =
        // Mapped off the main thread, as the library's source does.
        flow { emit(keys.associateWith { key -> GameArt(grid = pictureOf(key), large = null, details = ArtworkFakes.detailsFor(key, abs(key.hashCode()))) }) }.flowOn(Dispatchers.Default)

    override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

    override val images: CoverImages = SyntheticCoverImages()

    private fun pictureOf(key: String): ArtPicture {
        val h = abs(key.hashCode())
        // Upright boxes of a few shapes, as a system's own differ.
        return ArtPicture("bench:$key", STORED_WIDTH, STORED_HEIGHTS[h % STORED_HEIGHTS.size])
    }

    companion object {
        private const val STORED_WIDTH = 450
        private val STORED_HEIGHTS = IntArray(13) { 380 + it * 15 }

        /** Off by the benchmark's `sync` switch. */
        @Volatile var keepsOtherSizes = true
    }
}

/** Draws a picture per key instead of decoding a file; bounded to [CACHE_BYTES]. */
internal class SyntheticCoverImages : CoverImages {
    private val sizes = LastSizes(SIZES_KEPT)

    private val cache =
        object : LruCache<String, ImageBitmap>(cacheBytes()) {
            override fun sizeOf(
                key: String,
                value: ImageBitmap,
            ): Int = value.width * value.height * BYTES_PER_PIXEL
        }

    override fun cached(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? = cache.get(CoilCoverImages.cacheKey(picture, widthPx, heightPx))

    override fun cachedAnySize(picture: ArtPicture): ImageBitmap? =
        if (BenchmarkCoverArt.keepsOtherSizes) sizes.last(picture.path)?.let { (w, h) -> cached(picture, w, h) } else null

    override suspend fun load(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? {
        val key = CoilCoverImages.cacheKey(picture, widthPx, heightPx)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) { draw(picture, widthPx, heightPx).also { cache.put(key, it) } }.also { sizes.record(picture.path, widthPx, heightPx) }
    }

    private fun draw(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtMost(picture.width), heightPx.coerceAtMost(picture.height), Bitmap.Config.ARGB_8888)
        val hue = (abs(picture.path.hashCode()) % HUES).toFloat() * (360f / HUES)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, bitmap.height.toFloat(), Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.9f)), Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.4f)), Shader.TileMode.CLAMP)
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        paint.shader = null
        paint.color = Color.HSVToColor(DOT_ALPHA, floatArrayOf(hue, 0.15f, 1f))
        repeat(DOTS) { canvas.drawCircle(bitmap.width * (it % 5 + 1) / 6f, bitmap.height * (it / 5 + 1) / 5f, bitmap.width / 14f, paint) }
        // As the real decoder does for a software bitmap: the render thread uploads it before the frame that shows it.
        bitmap.prepareToDraw()
        return bitmap.asImageBitmap()
    }

    private companion object {
        const val SIZES_KEPT = 512
        const val CACHE_BYTES = 10 * 1024 * 1024
        const val HEAP_SHARE = 8

        /** At most 10 MB, and an eighth of the app heap (a 48 MB heap held no more with the covers on screen). */
        fun cacheBytes(): Int = minOf(CACHE_BYTES.toLong(), Runtime.getRuntime().maxMemory() / HEAP_SHARE).toInt()
        const val BYTES_PER_PIXEL = 4
        const val HUES = 24
        const val DOTS = 15
        const val DOT_ALPHA = 70
    }
}
