package com.plumemu.graph

import com.plumemu.scraper.ScrapeRequest
import com.plumemu.scraper.ScrapeResult
import com.plumemu.scraper.media.SavedMedia
import com.plumemu.scraper.work.ScrapePass
import com.plumemu.scraper.work.ScrapeSource
import java.util.Collections

/**
 * What one background run walks (two passes): the library's
 * main pass ([ScrapeSource.pending]: lookup and box front) and its disc pass
 * ([ScrapeSource.pendingSupport]: the disc or cartridge of matched games, the ones the main pass
 * delivered in this run included). The run itself orders them: the disc pass only once the main
 * pass is empty, giving way to any game the main pass gets meanwhile (scraper `ScrapeRun`).
 *
 * The disc pass offers each game once per run: the library offers a game whose disc failed again
 * until its third failure (`MAX_SUPPORT_ATTEMPTS`), across runs. So the rom key and row key
 * ([keyOf]: two copies of one file share it) of every disc-pass delivery are remembered for the
 * run and filtered out of [ScrapeSource.pendingSupport]; a filtered page is asked again a little
 * bigger, so the disc pass does not end early. A page is asked at `limit` plus the games tried
 * so far, so it is one query, not several that read the same rows again.
 *
 * [onDelivered] (the pace of [ScrapePace]) hears the main pass only: the pace is covers per
 * minute, and Settings' time left counts the games still without a lookup.
 */
internal class ScrapeRunSource(
    private val library: ScrapeSource,
    private val keyOf: (ScrapeRequest) -> String? = { null },
    private val onDelivered: () -> Unit = {},
) : ScrapeSource {
    /** Rom keys and row keys of every game the disc pass delivered in this run. */
    private val discTried: MutableSet<String> = Collections.synchronizedSet(HashSet())

    override suspend fun pending(limit: Int): List<ScrapeRequest> = if (limit <= 0) emptyList() else library.pending(limit)

    /** Up to [limit] disc-pass requests none of whose keys the disc pass tried in this run. */
    override suspend fun pendingSupport(limit: Int): List<ScrapeRequest> {
        if (limit <= 0) return emptyList()
        // Ask for the games tried so far on top at once: the tried ones sit ahead in the library's
        // order, so a page asked at `limit` is eaten by them and asked again bigger, each round
        // re-reading the same rows (a run got slower with every game it tried).
        var ask = limit + discTried.size
        while (true) {
            val got = library.pendingSupport(ask)
            val kept = got.filter { request -> keysOf(request).none { it in discTried } }
            // Fewer than asked: nothing more behind. Each round asks for as many more as were
            // filtered, so it ends once the filtered games (a handful) are passed.
            if (kept.size >= limit || got.size < ask) return kept.take(limit)
            ask += got.size - kept.size
        }
    }

    private fun keysOf(request: ScrapeRequest): List<String> = listOfNotNull(request.rom.key, keyOf(request))

    override suspend fun failedAsking(request: ScrapeRequest) = library.failedAsking(request)

    override suspend fun deliver(request: ScrapeRequest, result: ScrapeResult, media: List<SavedMedia>, pass: ScrapePass) {
        library.deliver(request, result, media, pass)
        when (pass) {
            ScrapePass.Main -> onDelivered()
            ScrapePass.Support -> discTried.addAll(keysOf(request))
        }
    }
}
