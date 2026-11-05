package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.plumemu.coreui.components.CoverGlowColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * The colour the focused cover (or Apps tile) glows in, by the key of what it shows (an [ArtPicture], an app's row id): the
 * average colour of its image ([CoverGlowColor]), found once when the image is first seen and kept for the last [KEPT] keys.
 * Read by the grid's ring pass while drawing and written on the main thread (compose or a loading effect). [version] is the
 * one piece of snapshot state: a colour that lands after its cover was focused redraws the ring pass once; nothing else writes it.
 */
internal class CoverGlowColors(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val colors =
        object : LinkedHashMap<Any, Color>(KEPT, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Color>?): Boolean = size > KEPT
        }

    private var version by mutableIntStateOf(0)

    /** The colour of [key]'s image, or null when none was found yet (the grid then glows in the tile's colour). Reading it in a draw block observes [version]. */
    operator fun get(key: Any): Color? {
        version
        return colors[key]
    }

    /**
     * Finds the colour of [image] unless [key] has one already ([replace]: take it again, an app's icon changed) and returns it.
     * The pixels are read on the [dispatcher] (Default; inline in tests); the cache is written back on the caller's thread (main).
     */
    suspend fun learn(
        key: Any,
        image: ImageBitmap,
        replace: Boolean = false,
    ): Color? {
        if (!replace && colors.containsKey(key)) return colors[key]
        val found = withContext(dispatcher) { CoverGlowColor.of(image) }
        if (found == null) colors.remove(key) else colors[key] = found
        version++
        return found
    }

    private val waiting = LinkedHashMap<Any, ImageBitmap>()
    private var isDraining = false

    /**
     * [learn] for a caller that does not need the answer, batched: the images asked for in the same frame wait for one another
     * and their pixels are read in one hop to the [dispatcher] and written back in one hop to the main thread, rather than two
     * hops each (every hop back takes the UI dispatcher's lock a worker thread then holds up the main thread on). The caller that
     * finds no batch running runs it, to its end even if that caller is cancelled meanwhile, so the others' images are not lost.
     * Main thread only.
     */
    suspend fun learnSoon(
        key: Any,
        image: ImageBitmap,
    ) {
        if (colors.containsKey(key)) return
        waiting[key] = image
        if (isDraining) return
        isDraining = true
        try {
            withContext(NonCancellable) {
                // The others asked in this frame are queued behind this one: let them join the batch.
                yield()
                while (waiting.isNotEmpty()) {
                    val batch = waiting.entries.map { it.key to it.value }
                    waiting.clear()
                    val found = withContext(dispatcher) { batch.map { (k, pixels) -> k to CoverGlowColor.of(pixels) } }
                    for ((k, colour) in found) if (colour == null) colors.remove(k) else colors[k] = colour
                    version++
                }
            }
        } finally {
            isDraining = false
        }
    }

    private companion object {
        const val KEPT = 256
        const val LOAD_FACTOR = 0.75f
    }
}
