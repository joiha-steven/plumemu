package com.plumemu.home.search

import com.plumemu.home.GameRowModel
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.usage.assertNoFreeText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Search tells the screen how it was used when it closes (`search_used`): lengths and counts, never the words. */
class SearchUsedTest {
    private val used = mutableListOf<Triple<Int, Int, Boolean>>()
    private val hits = List(12) { SearchHit(GameRowModel("g$it", "Game $it", "USA"), "snes") }

    private fun search() = SearchState(KeyboardChoice.Android).apply { onSearchUsed = { length, results, opened -> used += Triple(length, results, opened) } }

    @Test
    fun aSearchClosedSaysItsLengthResultsAndWhetherOneWasOpened() {
        val search = search()
        search.open()
        search.onQueryChange("zelda")
        search.showResults("zelda", hits)
        search.markResultOpened()
        search.close()
        assertEquals(listOf(Triple(5, 12, true)), used)
        val event = TelemetryEvent.SearchUsed(used[0].first, used[0].second, used[0].third)
        assertEquals(mapOf("query_length_bucket" to "3–5", "results_bucket" to "10+", "opened_result" to "yes"), event.wireFields())
        assertNoFreeText(listOf(event))
    }

    @Test
    fun nothingTypedIsNoSearchAndASecondCloseSaysNothing() {
        val search = search()
        search.open()
        search.close()
        search.close()
        search.open()
        search.onQueryChange("ab")
        search.close()
        search.close()
        assertEquals("a new search starts with nothing opened", listOf(Triple(2, 0, false)), used)
    }
}
