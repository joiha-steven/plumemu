package com.plumemu.home

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.plumemu.coreui.theme.BoxFieldLook

/**
 * Drawn box fronts that outlive one composition: Settings replacing the home screen, or a grid built again, would otherwise render
 * every visible cover's image again on the main thread. Bounded by bytes, least recently used out first.
 */
internal class BoxFrontImages(maxBytes: Int) {
    private val cache =
        object : LruCache<BoxFrontKey, ImageBitmap>(maxBytes.coerceAtLeast(1)) {
            override fun sizeOf(key: BoxFrontKey, value: ImageBitmap): Int = value.width * value.height * BYTES_PER_PIXEL
        }

    /** The image for [key], built by [build] on a miss; a null result is not stored. */
    fun getOrPut(key: BoxFrontKey, build: () -> ImageBitmap?): ImageBitmap? {
        cache.get(key)?.let { return it }
        val image = build() ?: return null
        cache.put(key, image)
        return image
    }

    fun clear() = cache.evictAll()

    /** The bytes held now. */
    fun bytes(): Int = cache.size()

    private companion object {
        const val BYTES_PER_PIXEL = 4
    }
}

/** Everything a drawn box front depends on; [systemShort] is empty for the field-only tile ground. */
internal data class BoxFrontKey(
    val fieldOnly: Boolean,
    val title: String,
    val system: String,
    val systemShort: String,
    val top: Int,
    val bottom: Int,
    val mark: Int,
    val titleInk: Int,
    val codeInk: Int,
    // The size as laid out, not rounded: the texts are placed by the fractional size.
    val width: Float,
    val height: Float,
    val density: Float,
    val fontScale: Float,
) {
    companion object {
        fun of(
            fieldOnly: Boolean,
            title: String,
            system: String,
            systemShort: String,
            look: BoxFieldLook,
            size: Size,
            density: Float,
            fontScale: Float,
        ) = BoxFrontKey(
            fieldOnly, title, system, systemShort,
            look.top.toArgb(), look.bottom.toArgb(), look.mark.toArgb(), look.title.toArgb(), look.code.toArgb(),
            size.width, size.height, density, fontScale,
        )
    }
}

/** Null (the default) means no cache: every composition renders its own image, as tests and screenshots expect. */
internal val LocalBoxFrontImages = staticCompositionLocalOf<BoxFrontImages?> { null }

/** The app's one cache. */
internal object SharedBoxFrontImages {
    /** At most this much: a screenful of covers is about 2 MB. */
    private const val MAX_MB = 8

    /** A sixteenth of the heap: bitmaps live in the Java heap on Android 6, where the low-end emulator has 48 MB. */
    private const val HEAP_SHARE = 16
    private const val BYTES_PER_MB = 1024 * 1024
    private const val BYTES_PER_KB = 1024
    private const val TAG = "BoxFrontImages"

    @Volatile private var instance: BoxFrontImages? = null

    /**
     * Android asks the process to give memory back ([ComponentCallbacks2.onTrimMemory]): from TRIM_MEMORY_RUNNING_CRITICAL up, which
     * includes UI_HIDDEN (a game or another app in front, the screen off), the images go. A cache of up to 8 MB kept while a game runs
     * makes Plumemu likelier to be killed, and coming back is then a cold start; drawing the covers again on return costs about one
     * frame. UI_HIDDEN, unlike BACKGROUND and the levels above, still reaches every Android version, a home app included.
     */
    fun trim(level: Int) {
        if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) return
        val images = instance ?: return
        // One line per trim, so a device log shows when the covers were given back (never while only Settings opens or closes).
        Log.i(TAG, "box fronts given back (${images.bytes() / BYTES_PER_KB} KB) at trim level $level")
        images.clear()
    }

    /**
     * Gives the images back each time an activity [isHome] answers for stops (a game or another app in front, the screen off). On a
     * dual-screen handheld the second screen's card keeps Plumemu visible while a game runs, so Android never sends UI_HIDDEN then.
     */
    fun releaseWhenStopped(app: Application, isHome: (Activity) -> Boolean) =
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStopped(activity: Activity) {
                    if (isHome(activity)) trim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
                }

                override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )

    fun of(context: Context): BoxFrontImages =
        instance ?: synchronized(this) {
            instance ?: run {
                val heapMb = (context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
                BoxFrontImages(minOf(MAX_MB, heapMb / HEAP_SHARE).coerceAtLeast(1) * BYTES_PER_MB).also { instance = it }
            }
        }
}
