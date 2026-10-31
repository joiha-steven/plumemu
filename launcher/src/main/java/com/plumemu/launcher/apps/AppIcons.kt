package com.plumemu.launcher.apps

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.plumemu.launcher.AndroidLauncherLog
import com.plumemu.launcher.LauncherLog
import com.plumemu.launcher.kind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** What [AppIcons.load] answers: a picture, or "none" so the screen draws its calm placeholder. */
sealed interface AppIcon {
    /** The icon drawn at the asked size (square). Shared with the cache: never draw into it. */
    class Picture(val bitmap: Bitmap) : AppIcon

    /** The app has no usable icon (uninstalled since the list was read, or loading failed; logged once). */
    data object None : AppIcon
}

/**
 * The launcher icon of an Apps entry app, for the grid's fixed tiles. Never blocks the caller:
 * [load] suspends while the work runs on an I/O thread.
 */
interface AppIcons {
    /** [app]'s icon as a [sizePx] by [sizePx] bitmap; adaptive icons are drawn as Android shows them. */
    suspend fun load(app: LaunchableApp, sizePx: Int): AppIcon

    /**
     * [app]'s icon at [sizePx] when it is already in the cache, else null; never loads, so a grid tile
     * scrolled back into view can draw it in its first frame on the main thread.
     */
    fun cached(app: LaunchableApp, sizePx: Int): AppIcon.Picture? = null

    /**
     * Goes up by one at every [invalidate]: a grid keys its tiles' icons by it, so the icons on screen are
     * asked again after a package change (a new icon, or an app that had none while it was installing).
     */
    val generation: StateFlow<Int>

    /** Forgets what is held for [packageName], or for every app when null (a package was added, replaced, changed or removed). */
    fun invalidate(packageName: String? = null)

    /** Answers [AppIcon.None] to everything (benchmark builds, previews). */
    data object Off : AppIcons {
        override val generation: StateFlow<Int> = MutableStateFlow(0).asStateFlow()

        override suspend fun load(app: LaunchableApp, sizePx: Int): AppIcon = AppIcon.None

        override fun invalidate(packageName: String?) = Unit
    }

    companion object {
        /** The production loader with a cache sized for this device ([cacheBytesFor]). */
        fun create(context: Context): AppIcons {
            val app = context.applicationContext
            val memoryClassMb = app.getSystemService(ActivityManager::class.java)?.memoryClass ?: 0
            return PackageManagerAppIcons(
                iconOf = { component -> app.packageManager.getActivityIcon(component) },
                isDefault = { component, drawable -> isSystemDefault(app.packageManager, component, drawable) },
                maxBytes = cacheBytesFor(memoryClassMb),
                log = AndroidLauncherLog,
                io = Dispatchers.IO,
            )
        }

        /**
         * The cache cap: 1/16 of the app's memory class, at most 8 MB, at least 1 MB. That is 8 MB on
         * the Thor (memory class 256) and 4 MB on a 64 MB-class device. The Apps grid's 56 dp icon is
         * about 128 px on the Thor (64 KB each), so the cap holds about 125 icons there: more than a
         * screen of the grid (8 columns by 4 rows) and well under the cover cache (15 %).
         */
        fun cacheBytesFor(memoryClassMb: Int): Int =
            (memoryClassMb.coerceAtLeast(0) * MB / 16).coerceIn(MB, 8 * MB)

        /**
         * Whether [iconResource] (the activity's icon, else the application's; 0 for none) is no icon of the app's
         * own: nothing declared, or Android's `sym_def_app_icon` (the robot on a grid).
         */
        fun isDefaultIconResource(iconResource: Int): Boolean = iconResource == 0 || iconResource == android.R.drawable.sym_def_app_icon

        /**
         * The signs of an app that ships no icon, cheapest first: no icon resource or the platform's default one
         * (by resource, no drawing), then the drawable being the very one `defaultActivityIcon` hands out (same
         * constant state; no bitmap is compared).
         */
        internal fun isSystemDefault(
            packageManager: PackageManager,
            component: ComponentName,
            drawable: Drawable,
        ): Boolean {
            val info = packageManager.getActivityInfo(component, 0)
            if (isDefaultIconResource(info.iconResource)) return true
            val state = drawable.constantState ?: return false
            return state == packageManager.defaultActivityIcon.constantState
        }

        private const val MB = 1024 * 1024
    }
}

/** [AppIcons] on `PackageManager.getActivityIcon`, with an LRU cache bounded by bytes. */
internal class PackageManagerAppIcons(
    private val iconOf: (ComponentName) -> Drawable,
    maxBytes: Int,
    private val log: LauncherLog,
    private val io: CoroutineDispatcher,
    /** True when the drawable [iconOf] gave is the system's stand-in for an app with no icon of its own. */
    private val isDefault: (ComponentName, Drawable) -> Boolean = { _, _ -> false },
) : AppIcons {
    private data class Key(val packageName: String, val activityName: String, val sizePx: Int)

    private val cache = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    /** Packages whose failure is already in the log; cleared with the package's cache so a new failure is told. */
    private val logged: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    /**
     * Icons that could not be had: answered [AppIcon.None] without asking PackageManager again (a tile
     * composed again would ask on every pass) until the package's next [invalidate].
     */
    private val failedKeys: MutableSet<Key> = java.util.Collections.synchronizedSet(HashSet())

    private val changes = MutableStateFlow(0)
    override val generation: StateFlow<Int> = changes.asStateFlow()

    override fun cached(app: LaunchableApp, sizePx: Int): AppIcon.Picture? =
        cache.get(Key(app.packageName, app.activityName, sizePx))?.let { AppIcon.Picture(it) }

    override suspend fun load(app: LaunchableApp, sizePx: Int): AppIcon {
        require(sizePx > 0) { "icon size must be positive: $sizePx" }
        val key = Key(app.packageName, app.activityName, sizePx)
        cache.get(key)?.let { return AppIcon.Picture(it) }
        if (key in failedKeys) return AppIcon.None
        return withContext(io) {
            cache.get(key)?.let { return@withContext AppIcon.Picture(it) }
            if (key in failedKeys) return@withContext AppIcon.None
            try {
                val component = ComponentName(app.packageName, app.activityName)
                val drawable = iconOf(component)
                if (isDefault(component, drawable)) {
                    // Remembered like a failure, so a tile composed again does not ask again; the screen draws its own tile.
                    failedKeys += key
                    return@withContext AppIcon.None
                }
                val bitmap = draw(drawable, sizePx)
                cache.put(key, bitmap)
                AppIcon.Picture(bitmap)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PackageManager.NameNotFoundException) {
                failedKeys += key
                failed(app, e)
            } catch (e: RuntimeException) {
                failedKeys += key
                failed(app, e)
            }
        }
    }

    override fun invalidate(packageName: String?) {
        if (packageName == null) {
            cache.evictAll()
            logged.clear()
            failedKeys.clear()
        } else {
            cache.snapshot().keys.filter { it.packageName == packageName }.forEach(cache::remove)
            logged.remove(packageName)
            synchronized(failedKeys) { failedKeys.removeAll { it.packageName == packageName } }
        }
        changes.update { it + 1 }
    }

    private fun draw(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        // An AdaptiveIconDrawable paints its background and foreground under the system mask itself.
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun failed(app: LaunchableApp, e: Exception): AppIcon {
        if (logged.add(app.packageName)) log.warn("apps: no icon for ${app.packageName} (${e.kind()})")
        return AppIcon.None
    }
}
