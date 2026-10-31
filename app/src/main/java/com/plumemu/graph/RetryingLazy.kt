package com.plumemu.graph

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Builds a value once, the first time it is asked for, and keeps it; a failed build (null) is not
 * kept, so the next call tries again (the person's Try again after a failure, nothing
 * retries on its own). Two callers at once share one build.
 */
internal class RetryingLazy<T : Any>(private val build: suspend () -> T?) {
    private val mutex = Mutex()

    @Volatile
    private var value: T? = null

    suspend fun get(): T? = value ?: mutex.withLock { value ?: build()?.also { value = it } }

    /** The value if it was built already, without building it. */
    fun peek(): T? = value
}
