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
 * [HomeAppIcons] on the app's graph: a key (a row id, or an app game's package) is looked up in the app list the graph holds (the one the
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

    /**
     * The last list read and its apps by key, built once per list (a screen of tiles asks for each): the Apps entry's
     * row id (`package/activity`) and, for an app game's row, the package (the first launcher of it).
     */
    @Volatile
    private var index: Pair<List<LaunchableApp>, Map<String, LaunchableApp>>? = null

    private fun appOf(key: String): LaunchableApp? {
        val list = listed()
        val known = index?.takeIf { it.first === list } ?: (list to keysOf(list)).also { index = it }
        return known.second[key]
    }

    private fun keysOf(list: List<LaunchableApp>): Map<String, LaunchableApp> {
        val byPackage = HashMap<String, LaunchableApp>()
        for (app in list) if (app.packageName !in byPackage) byPackage[app.packageName] = app
        return byPackage + list.associateBy(LibraryHomeContent::appRowId)
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
