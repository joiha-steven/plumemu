package com.plumemu.coreui.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The one low-priority thread that builds cached images (collar, soft shade, CSS shadow layers) for a size the screen has not drawn
 * yet, while the draw uses the last image of the same look stretched ([ImageBuilding.inBackground]). At most [MAX_QUEUED] builds wait;
 * a draw that cannot queue builds its image itself.
 */
internal object BackgroundBuilds {
    @Volatile private var thread: Thread? = null
    private val worker: ExecutorService by lazy {
        Executors.newSingleThreadExecutor { task -> Thread(task, "plum-image-builds").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.also { thread = it } }
    }

    /** Whether this is the build thread: an image built here (the collar's shades) is built exact, never queued behind a stand-in. */
    val onBuildThread: Boolean get() = Thread.currentThread() === thread
    private val queued = AtomicInteger()

    /**
     * Bumped when a background image lands; a draw that used a stand-in reads it, so it draws again. Written on the main thread only: a
     * state write from the low-priority build thread makes it queue work on Compose's UI dispatcher, under the dispatcher's lock, and
     * a build thread stopped there holds the main thread up (seen as 9 ms of lock contention in a trace of a system switch).
     */
    private val landed = mutableIntStateOf(0)
    private val bumpLanded = Runnable { landed.intValue++ }

    /** Reads [landed]: call from a draw that shows a stand-in. */
    fun watchLanding() {
        landed.intValue
    }

    /** Queues [task], which returns the image it built (hinted to the GPU before its first draw); false when too many wait. */
    fun submit(task: () -> ImageBitmap?): Boolean {
        if (queued.incrementAndGet() > MAX_QUEUED) {
            queued.decrementAndGet()
            return false
        }
        worker.execute {
            try {
                // Asks the render thread to upload the texture now, so the first draw does not (software bitmaps, API 23 included).
                task()?.asAndroidBitmap()?.prepareToDraw()
                // One pending post for the builds that land before the main thread gets to it.
                Handler(Looper.getMainLooper()).run {
                    removeCallbacks(bumpLanded)
                    post(bumpLanded)
                }
            } catch (e: OutOfMemoryError) {
                // A small heap (Android 6 handhelds): the stand-in stays, a later draw tries again (nothing redraws for a dropped build, so no loop); never a crash of the app.
                Log.w(TAG, "An image build ran out of memory and was dropped")
            } finally {
                queued.decrementAndGet()
            }
        }
        return true
    }

    /** How many builds wait (tests). */
    val waiting: Int get() = queued.get()

    private const val MAX_QUEUED = 96
    private const val TAG = "PlumImages"
}

/** Whether an image built for [builtW] x [builtH] may stand in, stretched, for a [w] x [h] one: not when a side differs by more than 2.5 times. */
internal fun standsIn(
    builtW: Int,
    builtH: Int,
    w: Int,
    h: Int,
): Boolean = builtW > 0 && builtH > 0 && w * 10 <= builtW * 25 && builtW * 10 <= w * 25 && h * 10 <= builtH * 25 && builtH * 10 <= h * 25
