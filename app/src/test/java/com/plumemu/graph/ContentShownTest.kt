package com.plumemu.graph

import org.junit.Assert.assertEquals
import org.junit.Test

/** Fully drawn once per Activity; the startup work the first time the library itself shows. */
class ContentShownTest {
    @Test
    fun aFailureThenTheLibraryStartsTheWorkOnceTheLibraryShows() {
        val shown = ContentShown(runsStartupWork = true)
        assertEquals("the message is the first screen: fully drawn, no work", ShownActions(true, false), shown.onShown(isLibrary = false))
        assertEquals("Try again worked: the work starts now", ShownActions(false, true), shown.onShown(isLibrary = true))
        assertEquals("and only once", ShownActions(false, false), shown.onShown(isLibrary = true))
    }

    @Test
    fun theLibraryAtOnceDoesBoth() {
        val shown = ContentShown(runsStartupWork = true)
        assertEquals(ShownActions(true, true), shown.onShown(isLibrary = true))
        assertEquals(true, shown.onShown(isLibrary = false).isEmpty)
    }

    @Test
    fun benchmarkBuildsOnlyReportFullyDrawn() {
        val shown = ContentShown(runsStartupWork = false)
        assertEquals(ShownActions(true, false), shown.onShown(isLibrary = true))
    }
}
