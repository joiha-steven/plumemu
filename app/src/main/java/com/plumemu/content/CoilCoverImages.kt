package com.plumemu.content

import android.app.ActivityManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import coil.size.Scale
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import java.io.File

/**
 * The saved box art decoded by Coil (allowed list): local files only, at
 * the size they are drawn (never larger than the file), kept in a memory cache sized for the
 * device ([imageLoader]). A file that cannot be read is logged by exception class and leaves the
 * placeholder.
 */
internal class CoilCoverImages(
    private val context: Context,
    makeLoader: () -> ImageLoader,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) : CoverImages {
    // Built when the first cover is drawn, not at start (it asks the system for its memory).
    private val loader by lazy(makeLoader)

    override fun cached(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? = loader.memoryCache?.get(MemoryCache.Key(cacheKey(picture, widthPx, heightPx)))?.bitmap?.asImageBitmap()

    override suspend fun load(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? {
        val request =
            ImageRequest.Builder(context)
                .data(File(picture.path))
                .size(widthPx, heightPx)
                .scale(Scale.FIT)
                // Never decoded larger than the file: the saved size is already the drawn one or more.
                .precision(Precision.INEXACT)
                .memoryCacheKey(cacheKey(picture, widthPx, heightPx))
                .build()
        return when (val result = loader.execute(request)) {
            is SuccessResult -> (result.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
            is ErrorResult -> {
                // The reason by class (and its cause's), with the sizes; never the path (a game's name).
                val error = result.throwable
                val cause = error.cause?.let { " caused by ${it.javaClass.simpleName}" }.orEmpty()
                log("Box art picture could not be decoded (${error.javaClass.simpleName}$cause; stored ${picture.width}x${picture.height}, asked ${widthPx}x$heightPx)")
                null
            }
        }
    }

    companion object {
        private const val TAG = "PlumCovers"

        /** Share of the app's memory class the decoded covers may keep (under 100 MB in all). */
        private const val CACHE_SHARE = 0.15
        private const val CACHE_SHARE_LOW_RAM = 0.10

        /** `isLowRamDevice`, or under 3 GB of memory in all (the scraper's `MediaSaver` saves by the same rule). */
        fun isLowRamDevice(context: Context): Boolean {
            val activityManager = context.getSystemService(ActivityManager::class.java) ?: return true
            if (activityManager.isLowRamDevice) return true
            val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
            return memory.totalMem < LOW_RAM_TOTAL_BYTES
        }

        private const val LOW_RAM_TOTAL_BYTES = 3L * 1024 * 1024 * 1024

        /**
         * One entry per file, version and drawn size: the engine writes a re-downloaded picture
         * under the same path, so the stored size and the row's time are part of the key (an old
         * decode is never shown for a new file, nor stretched to another shape).
         */
        internal fun cacheKey(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): String = "${picture.path}#${picture.width}x${picture.height}@${picture.version}:${widthPx}x$heightPx"

        /**
         * The process's loader: a memory cache of [CACHE_SHARE] of the app's memory class
         * ([CACHE_SHARE_LOW_RAM] and `RGB_565` on a low-RAM device), no disk cache (the
         * files are on the disk already), no network observer and no crossfade (the screen fades a
         * landing cover itself). [allowHardware] false for tests that draw in software.
         */
        fun imageLoader(
            context: Context,
            isLowRam: Boolean,
            allowHardware: Boolean = true,
        ): ImageLoader =
            ImageLoader.Builder(context)
                .memoryCache { MemoryCache.Builder(context).maxSizePercent(if (isLowRam) CACHE_SHARE_LOW_RAM else CACHE_SHARE).build() }
                .diskCache(null)
                .networkObserverEnabled(false)
                .respectCacheHeaders(false)
                .crossfade(false)
                .allowRgb565(isLowRam)
                .allowHardware(allowHardware)
                .build()
    }
}
