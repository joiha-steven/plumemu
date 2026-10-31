package com.plumemu.scraper.media

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.plumemu.scraper.MediaKind
import java.io.File
import java.io.IOException

/**
 * Turns a downloaded picture into one WebP file per target size.
 *
 * Decodes the bounds first, then the pixels with the largest power-of-two `inSampleSize` that
 * keeps them at or above the biggest target, in `RGB_565` on a low-RAM device, then
 * scales to fit each size (never up). Each file is written to a temp file and renamed over the
 * old one, so a failure leaves the earlier copy as it was. Bitmaps are recycled when done.
 *
 * @property encode writes a bitmap to a file; tests wrap it to see the bitmap the encoder got.
 */
internal class MediaSaver(
    private val mediaDir: File,
    private val lowRam: Boolean,
    private val log: (String) -> Unit,
    private val encode: (Bitmap, File) -> Boolean = ::encodeWebp,
) {
    private data class Fit(val size: MediaSize, val width: Int, val height: Int)

    /** What [save] did. */
    sealed interface Saved {
        /** One file per size. */
        data class Files(val media: List<SavedMedia>) : Saved

        /** The picture (or the game id) cannot be used; another picture may work. Logged. */
        data object NotAnImage : Saved

        /** A file could not be written (disk full, permission): the run must stop. Logged. */
        data object StorageFailed : Saved
    }

    /**
     * The files an earlier run saved for every one of [sizes], or null when any is missing. The
     * pixel size is in the file name, so a size whose pixels changed is saved again.
     */
    fun existing(gameId: String, kind: MediaKind, sizes: List<MediaSize>): List<SavedMedia>? {
        if (!SAFE_ID.matches(gameId) || sizes.isEmpty()) return null
        return sizes.map { size ->
            val file = fileFor(gameId, kind, size)
            if (!file.isFile) return null
            val bounds = bounds(file) ?: return null
            SavedMedia(kind, size.name, file.absolutePath, bounds.first, bounds.second)
        }
    }

    /**
     * Saves [source] at every one of [sizes] for game [gameId], all sizes or none: every size is
     * written to a temp file first, and only when all are written are they renamed over the
     * earlier copies. A write that fails deletes the temps and leaves every earlier copy as it
     * was (a re-download never ends with a new grid picture next to an old large one). A copy
     * saved for an older pixel size of the same name is removed once the new ones are in place.
     */
    fun save(source: File, gameId: String, kind: MediaKind, sizes: List<MediaSize>): Saved {
        if (!SAFE_ID.matches(gameId)) {
            log("media: game id is not a safe folder name, not saved")
            return Saved.NotAnImage
        }
        val (width, height) = bounds(source)
            ?: return Saved.NotAnImage.also { log("media: game $gameId picture is not an image") }
        val fits = sizes.map { fit(width, height, it) }
        val sample = fits.minOfOrNull { sampleSize(width, height, it.width, it.height) } ?: return Saved.Files(emptyList())
        val decoded = decode(source, sample)
            ?: return Saved.NotAnImage.also { log("media: game $gameId picture could not be decoded") }
        try {
            removeStaleTemps(File(mediaDir, gameId))
            val written = ArrayList<Written>(fits.size)
            for (fit in fits) {
                val one = write(decoded, fit, gameId, kind)
                if (one == null) {
                    written.forEach { it.temp.delete() }
                    return Saved.StorageFailed
                }
                written += one
            }
            return place(written, kind)
        } finally {
            decoded.recycle()
        }
    }

    /** One size encoded into [temp], to be renamed to [target]. */
    private class Written(val temp: File, val target: File, val size: MediaSize, val media: SavedMedia)

    /**
     * Renames every temp over its target. A rename in the same folder of a file just written
     * fails only if the folder broke meanwhile; then the temps left are deleted and the save is a
     * storage failure (the sizes renamed before it are already new).
     */
    private fun place(written: List<Written>, kind: MediaKind): Saved {
        written.forEachIndexed { index, one ->
            if (!one.temp.renameTo(one.target)) {
                log("media: could not save ${one.target.name} (rename)")
                written.drop(index).forEach { it.temp.delete() }
                return Saved.StorageFailed
            }
        }
        written.forEach { removeOlderSizes(it.target, kind, it.size) }
        return Saved.Files(written.map { it.media })
    }

    /** A process killed while encoding leaves its temp file; this folder is about to be written. */
    private fun removeStaleTemps(folder: File) {
        folder.listFiles { file -> file.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
    }

    /** Encodes one size into a temp file next to its target; null (logged, temp deleted) on failure. */
    private fun write(decoded: Bitmap, fit: Fit, gameId: String, kind: MediaKind): Written? {
        val target = fileFor(gameId, kind, fit.size)
        val temp = File(target.parentFile, target.name + TEMP_SUFFIX)
        val scaled = if (decoded.width == fit.width && decoded.height == fit.height) {
            decoded
        } else {
            Bitmap.createScaledBitmap(decoded, fit.width, fit.height, true)
        }
        try {
            target.parentFile?.mkdirs()
            val encoded = try {
                encode(scaled, temp)
            } catch (e: IOException) {
                log("media: could not write ${target.name} (${e.javaClass.simpleName})")
                false
            }
            if (!encoded) {
                log("media: could not save ${target.name} (encoded: false)")
                temp.delete()
                return null
            }
            return Written(temp, target, fit.size, SavedMedia(kind, fit.size.name, target.absolutePath, scaled.width, scaled.height))
        } finally {
            if (scaled !== decoded) scaled.recycle()
        }
    }

    /** Files of the same kind and size name saved for other pixels, now replaced by [kept]. */
    private fun removeOlderSizes(kept: File, kind: MediaKind, size: MediaSize) {
        val prefix = "${kind.fileName()}-${size.name}-"
        kept.parentFile?.listFiles { file -> file.name.startsWith(prefix) && file.name.endsWith(WEBP) && file != kept }
            ?.forEach { it.delete() }
    }

    private fun fileFor(gameId: String, kind: MediaKind, size: MediaSize): File =
        File(File(mediaDir, gameId), "${kind.fileName()}-${size.name}-${size.maxWidth}x${size.maxHeight}$WEBP")

    private fun decode(source: File, sample: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (lowRam) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(source.absolutePath, options)
    }

    internal companion object {
        /** ScreenScraper game ids are numbers; anything else must not become a path. */
        private val SAFE_ID = Regex("[A-Za-z0-9_]+")
        private const val TEMP_SUFFIX = ".tmp"
        private const val WEBP = ".webp"
        private const val WEBP_QUALITY = 85

        fun MediaKind.fileName(): String = when (this) {
            MediaKind.BoxFront -> "box-front"
            MediaKind.Support -> "support"
            // References only today: the run never downloads these (screenshots come in step 5).
            MediaKind.Screenshot -> "screenshot"
            MediaKind.TitleScreen -> "title-screen"
            MediaKind.Manual -> "manual"
        }

        /** Width and height from the header only, or null when it is not an image. */
        fun bounds(file: File): Pair<Int, Int>? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            return if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
        }

        /** The size of a [width] x [height] picture scaled to fit inside [size], never enlarged. */
        private fun fit(width: Int, height: Int, size: MediaSize): Fit {
            val scale = minOf(size.maxWidth.toDouble() / width, size.maxHeight.toDouble() / height, 1.0)
            val fitWidth = Math.round(width * scale).toInt().coerceIn(1, width)
            val fitHeight = Math.round(height * scale).toInt().coerceIn(1, height)
            return Fit(size, fitWidth, fitHeight)
        }

        /** The largest power of two that keeps the decoded picture at or above [fitWidth] x [fitHeight]. */
        fun sampleSize(width: Int, height: Int, fitWidth: Int, fitHeight: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= fitWidth && height / (sample * 2) >= fitHeight) sample *= 2
            return sample
        }

        fun encodeWebp(bitmap: Bitmap, target: File): Boolean =
            target.outputStream().use { bitmap.compress(webpFormat(), WEBP_QUALITY, it) }

        private fun webpFormat(): Bitmap.CompressFormat =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                // WEBP_LOSSY is API 30; below it, WEBP at a quality under 100 is the same lossy WebP.
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }

        private const val LOW_RAM_TOTAL_BYTES: Long = 3L * 1024 * 1024 * 1024

        /** `isLowRamDevice`, or under 3 GB of memory in total. */
        fun isLowRamDevice(context: Context): Boolean {
            val activityManager = context.getSystemService(ActivityManager::class.java) ?: return true
            if (activityManager.isLowRamDevice) return true
            val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
            return memory.totalMem < LOW_RAM_TOTAL_BYTES
        }
    }
}
