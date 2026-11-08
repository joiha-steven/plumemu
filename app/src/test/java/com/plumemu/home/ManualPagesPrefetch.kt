package com.plumemu.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.PrefetchRequest
import androidx.compose.foundation.lazy.layout.PrefetchRequestScope
import androidx.compose.foundation.lazy.layout.PrefetchScheduler

/**
 * A prefetch scheduler for the games pages that the test runs itself. Compose's own one spends a budget measured
 * against the wall clock, so under Robolectric whether a neighbour page is built ahead depends on how busy the machine
 * is. Install it through [gamesPagesStateMade] before the screen is set, then run its requests with [runIdle].
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ManualPagesPrefetch : PrefetchScheduler {
    val requests = ArrayDeque<PrefetchRequest>()

    /** Requests scheduled so far. */
    var scheduled = 0
        private set

    override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
        requests += prefetchRequest
        scheduled++
    }

    /** Gives the pages' lazy row this scheduler (a foundation internal, set by reflection: no public way). */
    fun install(list: LazyListState) {
        val prefetchState = LazyListState::class.java.getMethod("getPrefetchState\$foundation").invoke(list)
        prefetchState.javaClass.getMethod("setPrefetchScheduler\$foundation", PrefetchScheduler::class.java).invoke(prefetchState, this)
    }

    /** Idle time, on the UI thread: runs queued requests, each [slices] times at most with almost no time to spend, or to the end with null. */
    fun runIdle(slices: Int? = null) {
        var left = slices ?: Int.MAX_VALUE
        while (left > 0 && requests.isNotEmpty()) {
            val request = requests.first()
            val more = with(request) { Budget(if (slices == null) Long.MAX_VALUE / 4 else 1L).execute() }
            if (!more) requests.removeFirst()
            left--
        }
    }

    private class Budget(
        val nanos: Long,
    ) : PrefetchRequestScope {
        override fun availableTimeNanos(): Long = nanos
    }
}
