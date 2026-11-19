package com.plumemu.graph

import android.util.Log
import com.plumemu.settings.screen.SettingsCollectionActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Collections reads and writes over the app database. A new value per call: it holds nothing but the database, which
 * the graph opens once. `MainScreen` passes it as `HomeCallbacks.collections`.
 */
internal val AppGraph.collections: GraphCollections get() = GraphCollections(database)

/**
 * Settings › Library › Collections' writes. They run in the process scope, so leaving Settings does not stop one, and answer
 * on [main]. A failure is logged by class (no names) and answered as not stored.
 */
internal class GraphSettingsCollections(
    private val collections: GraphCollections,
    private val scope: CoroutineScope,
    private val main: CoroutineDispatcher,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) : SettingsCollectionActions {
    override fun rename(id: Long, name: String, onDone: (Boolean) -> Unit) = answer("rename", onDone) { collections.rename(id, name) != null }

    override fun delete(id: Long, onDone: (Boolean) -> Unit) = answer("delete", onDone) { collections.delete(id).let { true } }

    override fun reorder(ids: List<Long>, onDone: (Boolean) -> Unit) = answer("reorder", onDone) { collections.reorder(ids).let { true } }

    private fun answer(what: String, onDone: (Boolean) -> Unit, block: suspend () -> Boolean) {
        scope.launch {
            val result = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Settings: collection $what failed (${e.javaClass.simpleName})")
                false
            }
            withContext(main) { onDone(result) }
        }
    }

    private companion object {
        const val TAG = "PlumSettings"
    }
}
