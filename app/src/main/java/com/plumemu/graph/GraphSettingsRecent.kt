package com.plumemu.graph

import android.util.Log
import com.plumemu.settings.screen.SettingsRecentActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings › Library › Clear Recent…. Runs in the process scope: leaving Settings does not stop a clear already asked for. */
internal fun AppGraph.settingsRecentActions(): SettingsRecentActions = GraphSettingsRecent(GraphRecent(database), appScope)

internal class GraphSettingsRecent(
    private val recent: GraphRecent,
    private val scope: CoroutineScope,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) : SettingsRecentActions {
    override fun clearRecent(onDone: (Int?) -> Unit) {
        scope.launch {
            val cleared =
                try {
                    recent.clearRecent()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Class only: a database message may hold a path.
                    log("clear recent: could not be written (${e.javaClass.simpleName})")
                    null
                }
            withContext(main) { onDone(cleared) }
        }
    }

    private companion object {
        const val TAG = "PlumRecent"
    }
}
