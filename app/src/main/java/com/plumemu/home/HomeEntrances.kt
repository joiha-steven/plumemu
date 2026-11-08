package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.plumemu.launcher.LaunchAnimation

/**
 * Counts the home's returns to the front from a game or app it started: each one settles the home in from nearer, as
 * when Settings closes. A return the person did not cause through the home (the screen waking, another app leaving)
 * shows it at once. 0 until the first such return.
 */
@Composable
internal fun rememberHomeEntrances(): Int {
    var entrances by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        var startsSeen = LaunchAnimation.starts
        val watcher =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START && LaunchAnimation.starts != startsSeen) {
                    startsSeen = LaunchAnimation.starts
                    entrances++
                }
            }
        lifecycle.addObserver(watcher)
        onDispose { lifecycle.removeObserver(watcher) }
    }
    return entrances
}
