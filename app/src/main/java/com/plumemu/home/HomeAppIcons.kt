package com.plumemu.home

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Apps grid's icons by row id (`AppsGrid.kt`). The library's is `GraphAppIcons`, over the
 * launcher's `AppIcons` (Android's launcher icons, an LRU cache by bytes); `MainScreen` provides it as
 * [LocalHomeAppIcons]. A tile asks [cached] while it composes (a tile scrolled back in draws its icon in
 * its first frame) and [load]s only what is not in memory.
 */
internal interface HomeAppIcons {
    /**
     * Goes up when the icons held may be stale (a package was added, changed or removed): the grid keys its
     * tiles' icons by it, so the tiles on screen ask again (a new icon, or one an installing app lacked).
     */
    val generation: StateFlow<Int>

    /** Where the icons' glow colours are worked out (off the main thread); tests answer an inline dispatcher. */
    val glowDispatcher: CoroutineDispatcher get() = Dispatchers.Default

    /** [rowId]'s icon at [sizePx] if it is already in memory; never loads, so it may be asked on the main thread. */
    fun cached(
        rowId: String,
        sizePx: Int,
    ): ImageBitmap?

    /** [rowId]'s icon at [sizePx] (square), loaded off the main thread; null when the app has no usable icon (the tile's stand-in). */
    suspend fun load(
        rowId: String,
        sizePx: Int,
    ): ImageBitmap?

    /** No icons (benchmark builds, previews): every tile shows its stand-in. */
    object None : HomeAppIcons {
        override val generation: StateFlow<Int> = MutableStateFlow(0)

        override fun cached(
            rowId: String,
            sizePx: Int,
        ): ImageBitmap? = null

        override suspend fun load(
            rowId: String,
            sizePx: Int,
        ): ImageBitmap? = null
    }
}

/** The Apps grid's icons ([HomeAppIcons.None] outside the main screen). */
internal val LocalHomeAppIcons = staticCompositionLocalOf<HomeAppIcons> { HomeAppIcons.None }
