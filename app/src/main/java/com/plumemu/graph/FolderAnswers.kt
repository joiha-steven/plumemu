package com.plumemu.graph

import com.plumemu.settings.screen.FolderKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Settings › Library's answers for unknown folders (and ROM folders' skipped sub-folders, [T] the skip), kept past the screen: each answer is stored and then
 * [scanAfter] asks for the scan that applies it, both in [scope] (the process's, not Settings'), so closing Settings
 * while the store waits for a running scan loses nothing. [saving] lists the folders whose answer is not stored yet
 * (Settings shows "Saving…"). Answers go one after another, so two quick answers for a folder end on the last.
 * Logs say how it ended without the folder's name or path.
 */
internal class FolderAnswers<T>(
    private val scope: CoroutineScope,
    private val main: CoroutineDispatcher,
    /** Stores the answer; waits for a running scan (`LibraryScanner.chooseFolderSystem`). */
    private val store: suspend (FolderKey, T) -> Unit,
    /** Asks for the scan that lists the folder again; called only after a stored answer. */
    private val scanAfter: () -> Unit,
    private val log: (String) -> Unit,
) {
    private val order = Mutex()
    private val mutableSaving = MutableStateFlow<Set<FolderKey>>(emptySet())

    /** Answers asked and not yet stored, per folder: a folder stays in [saving] until its last one is. */
    private val waiting = HashMap<FolderKey, Int>()

    private fun count(key: FolderKey, change: Int) =
        synchronized(waiting) {
            val now = (waiting[key] ?: 0) + change
            if (now <= 0) waiting.remove(key) else waiting[key] = now
            mutableSaving.value = waiting.keys.toSet()
        }

    val saving: StateFlow<Set<FolderKey>> = mutableSaving.asStateFlow()

    /** [onDone] gets, on [main], whether the answer was stored. */
    fun answer(
        key: FolderKey,
        value: T,
        onDone: (Boolean) -> Unit,
    ) {
        count(key, +1)
        scope.launch {
            val stored =
                try {
                    order.withLock { store(key, value) }
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("Settings: answer for folder ${key.folderId} not stored (${e.javaClass.simpleName})")
                    false
                } finally {
                    count(key, -1)
                }
            if (stored) scanAfter()
            withContext(main) { onDone(stored) }
        }
    }
}
