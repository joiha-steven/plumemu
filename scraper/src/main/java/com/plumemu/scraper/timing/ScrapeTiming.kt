package com.plumemu.scraper.timing

import android.util.Log
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Where the scraper writes its timing lines (the "why is a box-art run slow" measurement). One
 * line per HTTP request, one per game, one for the account limits.
 *
 * A line never holds a URL, a query string, a user name, a password or a file path: only
 * endpoint names, status codes, byte counts, milliseconds, system ids and file extensions.
 */
public fun interface ScrapeTimingLog {
    public fun line(text: String)

    public companion object {
        /** The tag to read with `adb logcat -s PlumScrapeTiming`. */
        public const val TAG: String = "PlumScrapeTiming"

        /** Info level, so it shows on a release-like build. */
        public val Android: ScrapeTimingLog = ScrapeTimingLog {
            try {
                Log.i(TAG, it)
            } catch (e: RuntimeException) {
                // Measuring must never break a scrape: a plain JVM test without Android's Log throws here.
            }
        }

        public val Off: ScrapeTimingLog = ScrapeTimingLog { }
    }
}

/** One HTTP request, as logged and as added to its game's summary. [code] 0 means no answer. */
internal class RequestRecord(
    /** `jeuInfos`, `jeuRecherche`, `ssuserInfos` or `media`. */
    val endpoint: String,
    /** The picture kind of a `media` request. */
    val kind: String? = null,
    val code: Int,
    val bytes: Long,
    val totalMs: Long,
    /** Until the response headers arrived (the server's think time plus the connection); null when none came. */
    val headersMs: Long?,
    /** Waited in the gate (a free slot, then the spacing) before the request started. */
    val waitMs: Long,
    /** The exception class of a request that got no answer. */
    val error: String? = null,
) {
    val isLookup: Boolean get() = endpoint == "jeuInfos" || endpoint == "jeuRecherche"

    fun line(): String = buildString {
        append("req endpoint=").append(endpoint)
        kind?.let { append(" kind=").append(it) }
        append(" code=").append(code)
        error?.let { append(" error=").append(it) }
        append(" bytes=").append(bytes)
        append(" total=").append(totalMs).append("ms")
        if (headersMs == null) {
            append(" headers=-")
        } else {
            append(" headers=").append(headersMs).append("ms")
            append(" body=").append((totalMs - headersMs).coerceAtLeast(0)).append("ms")
        }
        append(" gateWait=").append(waitMs).append("ms")
    }
}

/** Nanoseconds to whole milliseconds since [startedNanos]. */
internal fun msSince(startedNanos: Long): Long = (System.nanoTime() - startedNanos) / NANOS_PER_MILLI

private const val NANOS_PER_MILLI = 1_000_000L

/** What one game cost; installed in the coroutine context by the run, filled by the layers below. */
internal class GameTiming(
    private val systemId: String,
    fileName: String,
    private val fileBytes: Long,
    /** Which pass of the run: `main` (lookup and box front) or `disc` (the disc or cartridge). */
    private val pass: String = "main",
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<GameTiming>

    // Only a known ROM extension is logged: a name without one ("Dr. Mario") must not leak a piece of the title.
    private val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it in KNOWN_EXTENSIONS } ?: "?"
    private val lock = Any()
    private val requests = ArrayList<RequestRecord>()
    private val saveMs = HashMap<String, Long>()
    private val pictureOutcome = HashMap<String, String>()
    private var hashMs = -1L
    private var hashSource = "-"
    private var parseMs = 0L
    private var storeMs = 0L
    private var cacheMs = 0L
    private var cacheHits = 0
    private var backoffMs = 0L
    private var deliverMs = 0L
    private var outcomeText = "cancelled"

    fun hash(ms: Long, source: String) = synchronized(lock) {
        hashMs = ms
        hashSource = source
    }

    fun request(record: RequestRecord) = synchronized(lock) { requests += record; Unit }
    fun parse(ms: Long) = synchronized(lock) { parseMs += ms }
    fun store(ms: Long) = synchronized(lock) { storeMs += ms }
    fun cacheHit(ms: Long) = synchronized(lock) {
        cacheHits++
        cacheMs += ms
    }
    fun backoff(ms: Long) = synchronized(lock) { backoffMs += ms }
    fun deliver(ms: Long) = synchronized(lock) { deliverMs = ms }
    fun saved(kind: String, ms: Long) = synchronized(lock) { saveMs[kind] = (saveMs[kind] ?: 0L) + ms }
    fun picture(kind: String, outcome: String) = synchronized(lock) { pictureOutcome[kind] = outcome }
    fun outcome(text: String) = synchronized(lock) { outcomeText = text }

    /** The one summary line of the game; [totalMs] is the whole step, delivery included. */
    fun summary(totalMs: Long): String = synchronized(lock) {
        val lookups = requests.filter { it.isLookup }
        buildString {
            append("game sys=").append(systemId)
            append(" ext=").append(extension)
            append(" pass=").append(pass)
            append(" outcome=").append(outcomeText)
            append(" total=").append(totalMs).append("ms")
            append(" size=").append(fileBytes).append('B')
            append(" hash=").append(if (hashMs < 0) "-" else "${hashMs}ms")
            append(" hashSrc=").append(hashSource)
            append(" lookup=").append(lookups.sumOf { it.totalMs }).append("ms")
            append(" lookupBytes=").append(lookups.sumOf { it.bytes }).append('B')
            append(" lookupReqs=").append(lookups.size)
            append(" parse=").append(parseMs).append("ms")
            append(" store=").append(storeMs).append("ms")
            append(" cacheHits=").append(cacheHits).append('/').append(cacheMs).append("ms")
            picture(this, "box", "BoxFront")
            picture(this, "disc", "Support")
            append(" deliver=").append(deliverMs).append("ms")
            append(" gateWait=").append(requests.sumOf { it.waitMs }).append("ms")
            append(" backoff=").append(backoffMs).append("ms")
        }
    }

    private fun picture(out: StringBuilder, label: String, kind: String) {
        val media = requests.filter { it.endpoint == "media" && it.kind == kind }
        out.append(' ').append(label).append("Dl=").append(media.sumOf { it.totalMs }).append("ms")
        out.append(' ').append(label).append("Bytes=").append(media.sumOf { it.bytes }).append('B')
        out.append(' ').append(label).append("Reqs=").append(media.size)
        out.append(' ').append(label).append("Save=").append(saveMs[kind] ?: 0L).append("ms")
        out.append(' ').append(label).append('=').append(pictureOutcome[kind] ?: "-")
    }
}

/** Common ROM and disc-image extensions (facts); the platform catalog's own list also holds plain words. */
private val KNOWN_EXTENSIONS: Set<String> = (
    "nes fds unf unif sfc smc fig swc bs bsx gb gbc gba sgb nds dsi 3ds cia cci cxi 3dsx n64 z64 v64 ndd gcm iso ciso " +
        "gcz rvz wbfs wia wad dol nsp xci nro nso nca md smd gen bin 32x sms gg sg sc 68k pce sgx cue chd ccd mds mdf " +
        "img nrg cso pbp psx psf m3u a26 a52 a78 j64 jag lnx lyx ngp ngc ngpc ws wsc vb vboy col cv int rom ch8 d64 " +
        "t64 tap prg crt adf adz dms ipf st msa stx dsk atr xex cas tzx z80 sna zip 7z rar neo dat vec min mx1 mx2 tgc"
    ).split(' ').toSet()

/**
 * Per run: the account limits are logged once, and again only when they change; the run also
 * writes the latest [lastLimits] line again with each of its `page` lines.
 */
internal class RunTiming : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RunTiming>

    private var lastLimits: String? = null
    private var lastLine: String? = null

    /** The whole `limits` line of the latest answer that had them ([remember]), or null before the first. */
    @Synchronized
    fun lastLimits(): String? = lastLine

    @Synchronized
    fun remember(line: String) {
        lastLine = line
    }

    /** True the first time [limits] is seen and whenever it differs from the last one. */
    @Synchronized
    fun changed(limits: String): Boolean {
        if (limits == lastLimits) return false
        lastLimits = limits
        return true
    }
}

/**
 * This log, but a line that fails to write is dropped: measuring must never break a scrape or
 * hide its result (a host's logger may throw). Applied once where a log is taken in.
 */
internal fun ScrapeTimingLog.guarded(): ScrapeTimingLog = if (this is Guarded) this else Guarded(this)

private class Guarded(private val inner: ScrapeTimingLog) : ScrapeTimingLog {
    override fun line(text: String) {
        try {
            inner.line(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Nothing left to log it with; the line is lost.
        }
    }
}

/** Logs [record] and adds it to the game being timed, if any. Called from a suspend function. */
internal suspend fun ScrapeTimingLog.request(record: RequestRecord) {
    line(record.line())
    coroutineContext[GameTiming]?.request(record)
}
