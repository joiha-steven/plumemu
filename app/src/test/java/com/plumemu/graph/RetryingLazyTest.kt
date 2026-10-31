package com.plumemu.graph

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A failed build (the platform data could not be read) is tried again; a good one is kept. */
class RetryingLazyTest {
    @Test
    fun aFailedLoadIsTriedAgainAndASuccessfulOneIsKept() = runTest {
        var builds = 0
        val lazy = RetryingLazy { builds++; if (builds == 1) null else "catalog" }

        assertNull("the first read failed", lazy.get())
        assertEquals("Try again reads it anew", "catalog", lazy.get())
        assertEquals("kept from then on", "catalog", lazy.get())
        assertEquals(2, builds)
    }

    @Test
    fun twoCallersAtOnceShareOneBuild() = runTest {
        var builds = 0
        val lazy = RetryingLazy { builds++; delay(10); "catalog" }
        val results = List(2) { async { lazy.get() } }.awaitAll()
        assertEquals(listOf("catalog", "catalog"), results)
        assertEquals(1, builds)
    }
}
