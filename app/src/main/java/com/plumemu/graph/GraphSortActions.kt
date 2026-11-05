package com.plumemu.graph

import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.game.SortActions
import com.plumemu.game.SortChoices
import kotlinx.coroutines.flow.first

/**
 * [SortActions] on the app's graph: the sort in use is read from the settings store and written
 * back to it (the one setting Settings › Library › Sort games also writes, so the lists follow
 * the same way); Year and Rating are offered only while some listed game has one
 * ([com.plumemu.coredata.db.GameDao.observeHasYear], [com.plumemu.coredata.db.GameDao.observeHasRating]).
 */
internal class GraphSortActions(
    private val current: suspend () -> SortGamesSetting,
    private val hasYear: suspend () -> Boolean,
    private val hasRating: suspend () -> Boolean,
    private val store: suspend (SortGamesSetting) -> Unit,
) : SortActions {
    constructor(graph: AppGraph) : this(
        current = { graph.settingsStore.settings.first().library.sortGames },
        hasYear = { graph.database.gameDao().observeHasYear().first() },
        hasRating = { graph.database.gameDao().observeHasRating().first() },
        store = { sort -> graph.settingsStore.update { it.copy(library = it.library.copy(sortGames = sort)) } },
    )

    override suspend fun choices() = SortChoices(current(), hasYear(), hasRating())

    override suspend fun set(sort: SortGamesSetting) = store(sort)
}
