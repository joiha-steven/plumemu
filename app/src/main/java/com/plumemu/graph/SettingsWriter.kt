package com.plumemu.graph

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.screen.resetKeepingFlags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings' writes, one after another in the order they were made. Walking the colour palette
 * writes on every press; launched separately, two writes could reach the store out of order and
 * leave the earlier colour. A failed write is logged by class (never the value) and the
 * next one still runs. Lives in the process's scope, so a write made just before leaving still lands.
 */
internal class SettingsWriter(
    scope: CoroutineScope,
    private val write: suspend ((PlumSettings) -> PlumSettings) -> Unit,
    private val log: (String) -> Unit,
) {
    private val queue = Channel<Write>(Channel.UNLIMITED)

    private class Write(val transform: (PlumSettings) -> PlumSettings, val onWritten: () -> Unit, val onFailed: () -> Unit)
    private val mutableFailed = MutableStateFlow(false)

    /** The last write failed (Settings says so; the screen keeps showing what is stored). False again after one lands. */
    val failed: StateFlow<Boolean> = mutableFailed.asStateFlow()

    init {
        scope.launch {
            for (each in queue) {
                try {
                    write(each.transform)
                    mutableFailed.value = false
                    each.onWritten()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Settings could not be saved (${e.javaClass.simpleName})")
                    mutableFailed.value = true
                    each.onFailed()
                }
            }
        }
    }

    fun submit(transform: (PlumSettings) -> PlumSettings) = enqueue(transform) {}

    /**
     * As [submit]; once the write has landed, [onLanded] gets the stored settings before it and after
     * it (on the writer's scope), so what changed can be counted (`setting_changed`). Not called when it failed.
     */
    fun submitWatched(
        transform: (PlumSettings) -> PlumSettings,
        onLanded: (before: PlumSettings, after: PlumSettings) -> Unit,
    ) = submitTold(transform, onFailed = {}, onLanded = onLanded)

    /** As [submit]; [onFailed] runs when this write could not be saved (on the writer's scope, not the main thread). */
    fun submitTold(
        transform: (PlumSettings) -> PlumSettings,
        onFailed: () -> Unit,
        onLanded: (before: PlumSettings, after: PlumSettings) -> Unit = { _, _ -> },
    ) {
        var written: Pair<PlumSettings, PlumSettings>? = null
        enqueue({ stored -> transform(stored).also { written = stored to it } }, onFailed = onFailed) {
            written?.let { (before, after) -> onLanded(before, after) }
        }
    }

    /** Queues [transform]; [onWritten] runs once it has landed (not when it failed). */
    private fun enqueue(
        transform: (PlumSettings) -> PlumSettings,
        onFailed: () -> Unit = {},
        onWritten: () -> Unit,
    ) {
        queue.trySend(Write(transform, onWritten, onFailed))
    }

    /**
     * Reset all settings ([resetKeepingFlags]). It also clears the person's box-art pause
     * (`library.box_art_paused`); when it did, [onPauseCleared] runs once the reset has landed, so
     * the run it asks for reads the store without the pause (otherwise Settings says
     * box art goes on while nothing is asked).
     */
    fun resetAll(onPauseCleared: () -> Unit) {
        var clearedPause = false
        enqueue(
            transform = { stored ->
                clearedPause = stored.library.boxArtPaused
                stored.resetKeepingFlags()
            },
            onWritten = { if (clearedPause) onPauseCleared() },
        )
    }
}
