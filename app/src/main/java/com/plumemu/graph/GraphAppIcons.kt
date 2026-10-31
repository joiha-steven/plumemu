package com.plumemu.graph

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.plumemu.content.LibraryHomeContent
import com.plumemu.home.HomeAppIcons
import com.plumemu.launcher.apps.AppIcon
import com.plumemu.launcher.apps.AppIcons
import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.flow.StateFlow

/**
 * [HomeAppIcons] on the app's graph: a row is looked up in the app list the graph holds (the one the
 * Apps entry was drawn from, as `GraphAppActions` does) and its icon asked of the launcher's [AppIcons]
 * (an I/O thread, the LRU cache). A row the list no longer has gets no icon (the tile's stand-in).
 */
internal class GraphAppIcons(
    private val listed: () -> List<LaunchableApp>,
    private val icons: AppIcons,
) : HomeAppIcons {
    constructor(graph: AppGraph) : this(listed = { graph.listedApps.value }, icons = graph.appIcons)

    /** The launcher's: raised by every `invalidate` (`AppGraph.packageChanges`). */
    override val generation: StateFlow<Int> get() = icons.generation

    /** The last list read and its apps by row id, built once per list (a screen of tiles asks for each). */
    @Volatile
    private var index: Pair<List<LaunchableApp>, Map<String, LaunchableApp>>? = null

    private fun appOf(rowId: String): LaunchableApp? {
        val list = listed()
        val known = index?.takeIf { it.first === list } ?: (list to list.associateBy(LibraryHomeContent::appRowId)).also { index = it }
        return known.second[rowId]
    }

    override fun cached(
        rowId: String,
        sizePx: Int,
    ): ImageBitmap? = appOf(rowId)?.let { icons.cached(it, sizePx) }?.bitmap?.asImageBitmap()

    override suspend fun load(
        rowId: String,
        sizePx: Int,
    ): ImageBitmap? {
        val app = appOf(rowId) ?: return null
        return (icons.load(app, sizePx) as? AppIcon.Picture)?.bitmap?.asImageBitmap()
    }
}
