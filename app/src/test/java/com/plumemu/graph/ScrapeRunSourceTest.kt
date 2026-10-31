package com.plumemu.graph

import com.plumemu.scraper.RomSource
import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.work.ScrapePass
import com.plumemu.scraper.work.ScrapeSource
import java.io.InputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One run's source: the library's main pass as it is, its disc pass with every
 * game the disc pass already tried in this run left out, and the pace fed by box fronts only.
 */
class ScrapeRunSourceTest {
    private class Rom(override val key: String) : RomSource {
        override val fileName: String = "$key.sfc"
        override val sizeBytes: Long = 1
        override val lastModifiedMillis: Long = 0

        override fun open(): InputStream = error("a run with hints never opens the file")
    }

    /**
     * Like the library: a main-pass delivery leaves [pending] and, matched with its box front only,
     * joins the disc pass; a disc-pass delivery whose disc failed again stays there (the library
     * offers it again until its third failure, across runs).
     */
    private class FakeLibrary(main: List<String>, discs: List<String> = emptyList()) : ScrapeSource {
        val main = main.toMutableList()
        val discs = discs.toMutableList()
        val askedSupport = mutableListOf<Int>()
        val delivered = mutableListOf<Pair<String, ScrapePass>>()

        override suspend fun pending(limit: Int): List<ScrapeRequest> = main.take(limit).map { request(it) }

        override suspend fun pendingSupport(limit: Int): List<ScrapeRequest> {
            askedSupport += limit
            return discs.take(limit).map { request(it) }
        }

        override suspend fun deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass) {
            val key = request.rom.key
            delivered += key to pass
            if (pass == ScrapePass.Main) {
                main -= key
                discs += key
            }
        }
    }

    private companion object {
        fun request(key: String) = ScrapeRequest(Rom(key), "snes", key, listOf("us"), listOf("en"))
    }

    private suspend fun ScrapeSource.deliverAll(requests: List<ScrapeRequest>, pass: ScrapePass) =
        requests.forEach { deliver(it, ScrapeResult.NoMatch, emptyList(), pass) }

    @Test
    fun theMainPassIsTheLibrarysAndItsGamesGetTheirDiscInTheSameRun() = runBlocking {
        val library = FakeLibrary(listOf("m1", "m2"))
        var paced = 0
        val source = ScrapeRunSource(library) { paced++ }

        val main = source.pending(5)
        assertEquals(listOf("m1", "m2"), main.map { it.rom.key })
        source.deliverAll(main, ScrapePass.Main)
        assertEquals(emptyList<String>(), source.pending(5).map { it.rom.key })
        assertEquals("delivered a moment ago, offered to the disc pass", listOf("m1", "m2"), source.pendingSupport(5).map { it.rom.key })
        source.deliverAll(source.pendingSupport(5), ScrapePass.Support)

        assertEquals(listOf("m1" to ScrapePass.Main, "m2" to ScrapePass.Main, "m1" to ScrapePass.Support, "m2" to ScrapePass.Support), library.delivered)
        assertEquals("the pace counts box fronts only", 2, paced)
    }

    @Test
    fun aGameTheDiscPassTriedIsNotOfferedAgainInTheRun() = runBlocking {
        val library = FakeLibrary(emptyList(), discs = listOf("s1", "s2"))
        val source = ScrapeRunSource(library)

        source.deliverAll(source.pendingSupport(1), ScrapePass.Support)

        // The library still offers s1 (its disc failed again); the run does not.
        assertEquals(listOf("s1", "s2"), library.pendingSupport(5).map { it.rom.key })
        assertEquals(listOf("s2"), source.pendingSupport(5).map { it.rom.key })
        source.deliverAll(source.pendingSupport(5), ScrapePass.Support)
        assertEquals(emptyList<String>(), source.pendingSupport(5).map { it.rom.key })
        assertEquals("a new run asks them again", listOf("s1", "s2"), ScrapeRunSource(library).pendingSupport(5).map { it.rom.key })
    }

    @Test
    fun aDiscPageIsOneQueryBigEnoughForTheGamesTriedInThisRun() = runBlocking {
        val library = FakeLibrary(emptyList(), discs = listOf("d1", "d2", "d3", "s1", "s2"))
        val source = ScrapeRunSource(library)
        source.deliverAll(source.pendingSupport(3), ScrapePass.Support)
        library.askedSupport.clear()

        assertEquals(listOf("s1", "s2"), source.pendingSupport(2).map { it.rom.key })
        assertEquals("one query, the rows tried earlier are not read again and again", listOf(5), library.askedSupport)
    }

    @Test
    fun aCopyOfAFileWhoseDiscWasTriedIsNotOfferedByTheDiscPass() = runBlocking {
        // Two copies of one file (two folders) share their row key.
        val rowKey = mapOf("copyA" to "crc:1", "copyB" to "crc:1", "s1" to "crc:2")
        val library = FakeLibrary(emptyList(), discs = listOf("copyA", "copyB", "s1"))
        val source = ScrapeRunSource(library, keyOf = { rowKey[it.rom.key] })

        source.deliverAll(source.pendingSupport(1), ScrapePass.Support)

        assertEquals(listOf("s1"), source.pendingSupport(5).map { it.rom.key })
    }

    @Test
    fun noPageIsAskedForNothing() = runBlocking {
        val library = FakeLibrary(listOf("m1"), discs = listOf("s1"))
        val source = ScrapeRunSource(library)
        assertEquals(emptyList<ScrapeRequest>(), source.pending(0))
        assertEquals(emptyList<ScrapeRequest>(), source.pendingSupport(0))
        assertTrue(library.askedSupport.isEmpty())
    }
}
