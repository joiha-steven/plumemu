package com.plumemu.update.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Release notes scrolled by the D-pad: Up (-1) and Down (+1) presses, counted here for [ScrollingReleaseNotes] to apply (like a
 * licence text's `licenseScroll`). Held by the update panel's model and by the What's new card's state.
 */
class NotesScroll {
    var presses: Int by mutableIntStateOf(0)
        private set

    /** Whether the notes can scroll that way; the notes set it (they know their size), so a press that cannot stays silent. */
    var canScroll: (down: Boolean) -> Boolean = { true }

    /** Counts one press and says whether it can scroll. */
    fun press(down: Boolean): Boolean {
        if (down) presses++ else presses--
        return canScroll(down)
    }
}
