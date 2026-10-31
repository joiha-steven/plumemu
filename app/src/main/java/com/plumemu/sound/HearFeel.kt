package com.plumemu.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/** Plays what [feels] report while this is composed; nothing is heard once it leaves (or in tests with no sounds). */
@Composable
fun HearFeel(vararg feels: Feel) {
    val sounds = LocalUiSounds.current
    DisposableEffect(sounds, *feels) {
        feels.forEach { it.sink = sounds::playFor }
        onDispose { feels.forEach { it.sink = {} } }
    }
}
