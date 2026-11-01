package com.plumemu.content

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.plumemu.game.BadgeImages
import java.io.File

/**
 * RetroAchievements' badge pictures for Info, downloaded by Coil at the size they are drawn and kept
 * on the disk ([DISK_BYTES]) and in a small memory cache. The loader is made when the first badge is
 * asked for, which only a signed-in person's Info does: signed out, nothing here is built. A badge
 * that cannot be fetched or decoded is logged by exception class (never the address) and leaves the
 * caller's placeholder.
 */
internal class CoilBadgeImages(
    private val context: Context,
    private val cacheFolder: File,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
    makeLoader: ((Context, File) -> ImageLoader)? = null,
) : BadgeImages {
    private val loader by lazy { (makeLoader ?: ::imageLoader)(context, cacheFolder) }

    override fun cached(
        url: String,
        sizePx: Int,
    ): ImageBitmap? = loader.memoryCache?.get(MemoryCache.Key(memoryKey(url, sizePx)))?.bitmap?.asImageBitmap()

    override suspend fun load(
        url: String,
        sizePx: Int,
    ): ImageBitmap? {
        val request =
            ImageRequest.Builder(context)
                .data(url)
                .size(sizePx, sizePx)
                .scale(Scale.FIT)
                .precision(Precision.INEXACT)
                .memoryCacheKey(memoryKey(url, sizePx))
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()
        return when (val result = loader.execute(request)) {
            is SuccessResult -> (result.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
            is ErrorResult -> {
                val error = result.throwable
                val cause = error.cause?.let { " caused by ${it.javaClass.simpleName}" }.orEmpty()
                log("An achievement badge could not be loaded (${error.javaClass.simpleName}$cause)")
                null
            }
        }
    }

    companion object {
        private const val TAG = "PlumBadges"

        /** The badges kept on the disk: each is a few KB, so this holds hundreds of games' sets. */
        const val DISK_BYTES: Long = 8L * 1024 * 1024

        /** Share of the app's memory class the decoded badges may keep. */
        private const val MEMORY_SHARE = 0.02

        internal fun memoryKey(
            url: String,
            sizePx: Int,
        ): String = "$url@$sizePx"

        fun imageLoader(
            context: Context,
            cacheFolder: File,
        ): ImageLoader =
            ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder(context).maxSizePercent(MEMORY_SHARE).build() }
                .diskCache { DiskCache.Builder().directory(cacheFolder).maxSizeBytes(DISK_BYTES).build() }
                .networkObserverEnabled(false)
                // RetroAchievements' badge files never change under a name; the disk cache keeps them whatever the headers say.
                .respectCacheHeaders(false)
                .crossfade(false)
                .build()
    }
}
