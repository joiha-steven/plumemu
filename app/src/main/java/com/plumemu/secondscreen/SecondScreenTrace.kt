package com.plumemu.secondscreen

import android.os.Trace

/** Names the work of a second-screen card in a system trace (section "SS:<part>"), so a slow frame says which part cost it. */
internal inline fun <T> cardTrace(
    name: String,
    block: () -> T,
): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}

/** Counts the text layouts and compositions a card asks for, by part, while [counting] (a test turns it on); off, [note] does nothing. */
internal object CardLayoutCount {
    const val DESCRIPTION = "description"
    const val DESCRIPTION_LAYOUTS = "descriptionLayouts"
    const val TILE_LABEL = "tileLabel"

    @Volatile var counting = false
    val counts = HashMap<String, Int>()

    fun note(
        part: String,
        amount: Int = 1,
    ) {
        if (counting) synchronized(counts) { counts[part] = (counts[part] ?: 0) + amount }
    }
}
