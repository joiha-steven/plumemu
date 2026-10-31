package com.plumemu.identify.name

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class TitleSearchTest {
    private val names = listOf(
        "Zork Quest", "Zork Quest II", "The Moonlit Harbor", "Ice & Fire Brigade", "Caf\u00e9 Racer Deluxe",
        "Starfield Tactics: Second Front", "Dravon Knight Red", "Dravon Knight Rex", "Legend of Dravon, The - Harbor of Ice",
        "Galfen Island", "Zorkian Tales",
    )
    private val search = TitleSearch(names)

    private fun found(query: String): List<String> = search.find(query).map { names[it.index] }

    @Test
    fun theSameKeyComesFirst() {
        assertEquals("Zork Quest II", found("zork quest 2").first())
        assertEquals("The Moonlit Harbor", found("moonlit harbor").first())
        assertEquals("Ice & Fire Brigade", found("Ice and Fire Brigade").first())
        assertEquals("Caf\u00e9 Racer Deluxe", found("cafe racer deluxe").first())
    }

    @Test
    fun aHalfTypedLastWordFindsTheTitlesItStarts() {
        assertEquals(listOf("Zork Quest", "Zork Quest II", "Zorkian Tales"), found("zork").take(3).sorted())
        assertEquals(listOf("Dravon Knight Red", "Dravon Knight Rex"), found("dravon knight re"))
    }

    @Test
    fun wordsInAnyOrderFindTheTitle() {
        assertEquals("Legend of Dravon, The - Harbor of Ice", found("harbor dravon").first())
    }

    @Test
    fun aTypoLaterInAWordIsForgiven() {
        assertEquals("Galfen Island", found("galfin island").first())
        assertEquals("Starfield Tactics: Second Front", found("starfeld tactics second front").first())
    }

    @Test
    fun startingWithTheQueryRanksAboveWordsElsewhere() {
        val hits = found("harbor")
        assertTrue(hits.containsAll(listOf("The Moonlit Harbor", "Legend of Dravon, The - Harbor of Ice")))
        assertEquals(2, hits.size)
    }

    @Test
    fun nothingCloseGivesNoTitles() {
        assertEquals(emptyList<String>(), found("qwxv plomb"))
        assertEquals(emptyList<String>(), found(""))
        assertEquals(emptyList<String>(), found("  - : "))
    }

    @Test
    fun theLimitKeepsTheBestOnes() {
        val many = TitleSearch((1..500).map { "Zork Quest Episode $it" } + "Zork Quest")
        val hits = many.find("zork quest", limit = 10)
        assertEquals(10, hits.size)
        assertEquals(500, hits.first().index)
        assertEquals(1.0, hits.first().score, 0.0)
    }

    @Test
    fun aCheckpointThatThrowsStopsTheScanPartWay() {
        val big = TitleSearch((1..10_000).map { "Zork Title $it" })
        var checks = 0
        val stopped = runCatching { big.find("zork") { if (++checks == 2) throw java.util.concurrent.CancellationException("newer key") } }
        assertTrue(stopped.exceptionOrNull() is java.util.concurrent.CancellationException)
        assertEquals("stopped at the second checkpoint, not after 10,000 titles", 2, checks)
        var all = 0
        big.find("zork") { all++ }
        assertEquals("one checkpoint per ${TitleSearch.CHECK_EVERY} titles", (10_000 + TitleSearch.CHECK_EVERY - 1) / TitleSearch.CHECK_EVERY, all)
    }

    /**
     * Speed as ratios measured on the same machine, so a slow CI runner or a Mac busy with other
     * builds does not fail it: rounds interleave the three timings (a busy moment hits all of
     * them) and only the fastest of each counts. A query must cost less than keying the whole
     * library 7 times (about 2.2 on an Apple M-series), and must
     * grow linearly with the library (40,000 titles against 10,000: 4 when linear, 16 when
     * quadratic; the bound is 8). The absolute laptop budget is [fortyThousandTitlesWithinTheLaptopBudget].
     */
    @Test
    fun aQueryCostsLessThanKeyingTheLibraryAndGrowsLinearly() {
        val bigNames = zorkTitles(40_000)
        val small = TitleSearch(zorkTitles(10_000))
        var big = TitleSearch(bigNames)
        repeat(5) { small.find(SLOW_QUERY); big.find(SLOW_QUERY) }
        var buildMs = Double.MAX_VALUE
        var bigMs = Double.MAX_VALUE
        var smallMs = Double.MAX_VALUE
        repeat(9) {
            buildMs = minOf(buildMs, millis { big = TitleSearch(bigNames) })
            bigMs = minOf(bigMs, millis { big.find(SLOW_QUERY) })
            smallMs = minOf(smallMs, millis { small.find(SLOW_QUERY) })
        }
        assertTrue("query $bigMs ms, keying every title $buildMs ms", bigMs < 7 * buildMs)
        assertTrue("query over 40,000 titles $bigMs ms, over 10,000 $smallMs ms", bigMs < 8 * smallMs)
    }

    /** The laptop budget, opt-in with `-Pidentify.measure=true` like the identify measurement. */
    @Test
    fun fortyThousandTitlesWithinTheLaptopBudget() {
        assumeTrue("set -Pidentify.measure=true to run", System.getProperty("identify.measure") == "true")
        val big = TitleSearch(zorkTitles(40_000))
        repeat(5) { big.find(SLOW_QUERY) }
        var fastest = Double.MAX_VALUE
        repeat(9) { fastest = minOf(fastest, millis { big.find(SLOW_QUERY) }) }
        assertTrue("took $fastest ms per query", fastest < 200)
    }

    private fun zorkTitles(count: Int) = (1..count).map { "Zork Title Number $it Deluxe" }

    private inline fun millis(work: () -> Unit): Double {
        val started = System.nanoTime()
        work()
        return (System.nanoTime() - started) / 1_000_000.0
    }

    private companion object {
        /** Every title shares a stem with it, so every title is scored: the slowest query shape. */
        const val SLOW_QUERY = "zork title number 3999"
    }
}
