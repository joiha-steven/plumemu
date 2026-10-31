package com.plumemu

import com.plumemu.graph.AppGraph
import com.plumemu.settings.ViewedSystem
import com.plumemu.settings.systemColors

private val sharedLock = Any()
private var shared: Pair<AppGraph, ViewedSystem>? = null

/**
 * Accent color › By system: the system last viewed on the home screen and the colour of each system.
 * One instance per graph: a new one on every root recomposition would reset the accent to the fallback for a frame
 * and read the colours again (`SettingsTheme` keys its state on it). The colours come from the platform data, read on
 * first use off the main thread; unreadable data gives none (the fallback accent shows).
 */
internal fun AppGraph.viewedSystemAccent(): ViewedSystem =
    synchronized(sharedLock) {
        shared?.takeIf { it.first === this }?.second
            ?: ViewedSystem(viewedSystem) { catalog()?.systemColors() }.also { shared = this to it }
    }
