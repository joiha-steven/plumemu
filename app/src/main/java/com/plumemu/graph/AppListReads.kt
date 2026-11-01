package com.plumemu.graph

import com.plumemu.launcher.apps.LaunchableApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reads of the Apps entry's list, one at a time: startup's packages job and a package change (an uninstall) can ask
 * together, and an older, slower read finishing last would put an uninstalled app back. Each read starts after the
 * one before it was stored, so the last one asked is the last one stored. A read that finds no launcher (null) keeps the list.
 */
internal class AppListReads(
    private val into: MutableStateFlow<List<LaunchableApp>>,
    private val onChanged: () -> Unit = {},
    private val source: suspend () -> List<LaunchableApp>?,
) {
    private val one = Mutex()

    suspend fun refresh() {
        read()
    }

    /**
     * Reads under the same lock and stores the answer; returns it (null: no launcher, list kept). [onChanged] runs when the
     * stored list differs from the one before, so what was drawn from the old list (icons found by package) asks again.
     */
    suspend fun read(): List<LaunchableApp>? =
        one.withLock {
            source()?.also {
                val changed = into.value != it
                into.value = it
                if (changed) onChanged()
            }
        }
}
