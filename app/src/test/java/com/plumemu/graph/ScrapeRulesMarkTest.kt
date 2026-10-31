package com.plumemu.graph

import com.plumemu.library.scrape.SCRAPE_RULES_VERSION
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ScrapeRulesMarkTest {
    private val calls = mutableListOf<String>()

    @Test
    fun resultsFoundByOlderRulesAreAskedAgainThenTheVersionRecorded() = runTest {
        val marked = markForScrapeRules(0, { stored -> calls += "mark from $stored"; 805 }) { calls += "record $it" }
        assertEquals(true, marked)
        assertEquals(listOf("mark from 0", "record $SCRAPE_RULES_VERSION"), calls)
    }

    @Test
    fun resultsFoundByCurrentRulesAreLeftAlone() = runTest {
        val marked = markForScrapeRules(SCRAPE_RULES_VERSION, { calls += "mark"; 0 }) { calls += "record" }
        assertEquals(false, marked)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun settingsNotReadYetMarkNothing() = runTest {
        assertEquals(false, markForScrapeRules(null, { calls += "mark"; 0 }) { calls += "record" })
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aMarkThatFailsRecordsNothingSoTheNextScanTriesAgain() = runTest {
        val failure = runCatching { markForScrapeRules(0, { throw IOException("disk") }) { calls += "record" } }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IOException)
        assertEquals(emptyList<String>(), calls)
        assertEquals("the next scan tries again", true, markForScrapeRules(0, { calls += "mark"; 1 }) { calls += "record" })
    }
}
