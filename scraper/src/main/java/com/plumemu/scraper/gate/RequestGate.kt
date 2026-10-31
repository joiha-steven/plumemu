package com.plumemu.scraper.gate

import com.plumemu.scraper.PauseReason
import com.plumemu.scraper.UnavailableReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps the scraper inside the account's limits.
 *
 * - At most [threadLimit] requests in flight: 1 until an answer says otherwise, then the
 *   account's `maxthreads`, never more; 1 again for the rest of this session after a 429.
 * - Each slot waits [spacingMillis] (at least 1.2 s) between the starts of its requests; wider
 *   when the account's per-minute limit needs it.
 * - While a pause is set (daily quota, server closed) or the credentials were refused, no request
 *   starts: [run] checks for a [Stop] when called and again right before the request, after the
 *   wait for a slot and the spacing, so calls queued behind the one that got the stop send nothing.
 *   The caller must set the stop ([pauseUntil], [refuse]) inside the block, before the slot is
 *   released, or a woken waiter could slip through.
 *
 * State is guarded by a plain lock, not a coroutine Mutex: releasing a slot happens in `finally`
 * and must not suspend, or a cancelled request would keep its slot forever.
 */
internal class RequestGate(
    private val clock: () -> Long,
    private val minSpacingMillis: Long = MIN_SPACING_MILLIS,
) {
    /** Why no request may start now. */
    sealed interface Stop

    data class Pause(val untilMillis: Long, val reason: PauseReason) : Stop

    data class Refusal(val reason: UnavailableReason) : Stop

    private class Slot {
        var busy = false
        var lastStartMillis: Long? = null
    }

    private val lock = Any()
    private val slots = ArrayList<Slot>()
    private val waiters = ArrayList<CompletableDeferred<Unit>>()
    private var accountThreads = 1
    private var throttled = false
    private var maxPerMinute: Int? = null
    private var pause: Pause? = null
    private var refusedWith: Refusal? = null

    val threadLimit: Int get() = synchronized(lock) { if (throttled) 1 else accountThreads }

    private val limitNow = MutableStateFlow(1)

    /** [threadLimit] as it changes: the run starts this many games together. */
    val threadLimits: StateFlow<Int> = limitNow.asStateFlow()

    val spacingMillis: Long
        get() = synchronized(lock) {
            val perMinute = maxPerMinute ?: return@synchronized minSpacingMillis
            // Every slot shares the per-minute budget: threads x (60 s / budget) per slot.
            val needed = (MINUTE_MILLIS * threadLimitLocked() + perMinute - 1) / perMinute
            maxOf(minSpacingMillis, needed)
        }

    /**
     * Runs [block] in a free slot, after that slot's spacing; or, when a [Stop] is in force at the
     * start or once the slot and spacing are waited out, returns [stopped] without running it.
     */
    suspend fun <T> run(stopped: (Stop) -> T, block: suspend () -> T): T = runWaited(stopped) { block() }

    /** Like [run]; [block] is told how many milliseconds the call waited for a slot and the spacing. */
    suspend fun <T> runWaited(stopped: (Stop) -> T, block: suspend (waitedMillis: Long) -> T): T {
        val enteredAt = clock()
        stop()?.let { return stopped(it) }
        val slot = acquire()
        try {
            val wait = slot.lastStartMillis?.let { it + spacingMillis - clock() } ?: 0L
            if (wait > 0) delay(wait)
            stop()?.let { return stopped(it) }
            val startedAt = clock()
            slot.lastStartMillis = startedAt
            return block((startedAt - enteredAt).coerceAtLeast(0L))
        } finally {
            release(slot)
        }
    }

    /** Reads the account limits a successful answer carried. */
    fun updateFromAccount(maxThreads: Int?, maxRequestsPerMinute: Int?) {
        synchronized(lock) {
            if (maxThreads != null && maxThreads >= 1) accountThreads = maxThreads
            if (maxRequestsPerMinute != null && maxRequestsPerMinute >= 1) maxPerMinute = maxRequestsPerMinute
            limitNow.value = threadLimitLocked()
        }
        wakeWaiters()
    }

    /** ScreenScraper said too many threads or requests: one at a time from now on. */
    fun onThreadLimit() {
        synchronized(lock) {
            throttled = true
            limitNow.value = 1
        }
    }

    fun pauseUntil(untilMillis: Long, reason: PauseReason) {
        synchronized(lock) {
            val current = pause
            if (current == null || current.untilMillis < untilMillis) pause = Pause(untilMillis, reason)
        }
    }

    /** ScreenScraper refused this app or account: no request for the rest of this session. */
    fun refuse(reason: UnavailableReason) {
        synchronized(lock) { if (refusedWith == null) refusedWith = Refusal(reason) }
    }

    /** The pause in force now, if any. */
    fun pause(): Pause? = synchronized(lock) { pause?.takeIf { clock() < it.untilMillis } }

    fun refusal(): Refusal? = synchronized(lock) { refusedWith }

    /** A refusal, else the pause in force now, if any. */
    fun stop(): Stop? = refusal() ?: pause()

    private suspend fun acquire(): Slot {
        while (true) {
            val waiter = CompletableDeferred<Unit>()
            synchronized(lock) {
                freeSlotLocked()?.let { slot ->
                    slot.busy = true
                    return slot
                }
                waiters += waiter
            }
            waiter.await()
        }
    }

    /**
     * The free slot under the limit that has waited longest since its last start, or null while
     * as many requests as the limit are running. Busy slots past the limit count too: after the
     * limit drops (a 429, a smaller `maxthreads`) the requests already running still hold theirs.
     */
    private fun freeSlotLocked(): Slot? {
        val limit = threadLimitLocked()
        if (slots.count { it.busy } >= limit) return null
        while (slots.size < limit) slots += Slot()
        return slots.take(limit).filter { !it.busy }.minByOrNull { it.lastStartMillis ?: Long.MIN_VALUE }
    }

    private fun threadLimitLocked(): Int = if (throttled) 1 else accountThreads

    private fun release(slot: Slot) {
        synchronized(lock) { slot.busy = false }
        wakeWaiters()
    }

    private fun wakeWaiters() {
        val woken = synchronized(lock) { waiters.toList().also { waiters.clear() } }
        woken.forEach { it.complete(Unit) }
    }

    companion object {
        const val MIN_SPACING_MILLIS: Long = 1_200
        private const val MINUTE_MILLIS: Long = 60_000
    }
}
