package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.plumemu.coreui.components.CoverGlowColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private companion object {
        const val KEPT = 256
        const val LOAD_FACTOR = 0.75f
    }
}
