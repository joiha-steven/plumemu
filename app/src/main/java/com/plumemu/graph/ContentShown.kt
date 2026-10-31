package com.plumemu.graph

/** What to do after a frame that shows something: report fully drawn, start the startup work. */
internal data class ShownActions(val reportFullyDrawn: Boolean, val startStartupWork: Boolean) {
    val isEmpty: Boolean get() = !reportFullyDrawn && !startStartupWork
}

/**
 * One Activity's bookkeeping, kept apart on purpose: the first screen drawn (library or the
 * "could not open" message) is reported as fully drawn once, and the startup work starts the first
 * time the library itself shows, even when that comes after a Try again.
 */
internal class ContentShown(private val runsStartupWork: Boolean) {
    private var fullyDrawnReported = false
    private var startupRequested = false

    @Synchronized
    fun onShown(isLibrary: Boolean): ShownActions {
        val report = !fullyDrawnReported
        val start = runsStartupWork && isLibrary && !startupRequested
        fullyDrawnReported = true
        if (start) startupRequested = true
        return ShownActions(report, start)
    }
}
