package com.plumemu.coreui.components

import androidx.compose.ui.graphics.ImageBitmap
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Whether images that take long to build (the collar's) are built off the main thread when a stretched one of another size can stand in.
 * Off unless the app turns it on, so screenshot tests draw the exact image at once.
 */
object ImageBuilding {
    @Volatile var inBackground = false

    /**
     * The most memory the cached collar and shadow images may hold together: on Android 7 and older a bitmap lives in the app's heap
     * (48 MB on a small handheld), so the app sets it from the heap it has ([budgetFor]).
     */
    @Volatile var budgetBytes = MAX_BUDGET

    /** The budget for an app heap of [maxHeapBytes] (`Runtime.maxMemory()`): a sixth of it, at most [MAX_BUDGET]. */
    fun budgetFor(maxHeapBytes: Long): Long = minOf(MAX_BUDGET, maxHeapBytes / HEAP_SHARE)

    /** The bytes the collar, CSS shadow and soft shadow caches hold now (tests). */
    internal val heldBytes: Long get() = CollarImages.heldBytes + CssImages.heldBytes + SoftImages.heldBytes

    /** Empties the collar, CSS shadow and soft shadow caches (tests). */
    internal fun clearCaches() {
        CollarImages.clear()
        CssImages.clear()
        SoftImages.clear()
    }

    /** Called with the trace name of every image build, on the thread that builds it (tests). */
    @Volatile internal var watcher: ((String) -> Unit)? = null

    private const val MAX_BUDGET = 18L * 1024 * 1024
    private const val HEAP_SHARE = 6L
}

/** The bytes of [images], each image counted once. */
internal fun distinctBytes(images: List<ImageBitmap>): Long {
    val seen = Collections.newSetFromMap(IdentityHashMap<ImageBitmap, Boolean>())
    return images.filter { seen.add(it) }.sumOf { it.width.toLong() * it.height * BYTES_PER_PIXEL }
}

private const val BYTES_PER_PIXEL = 4
