package com.plumemu.graph

import java.util.concurrent.TimeUnit
import org.junit.rules.Timeout

/**
 * A graph test that waits on a thread or latch must fail with a stack, not eat the CI job
 * (a hang once cost two 60-minute runs). Generous: the slowest graph test takes seconds.
 */
internal fun graphTestTimeout(): Timeout = Timeout.builder().withTimeout(120, TimeUnit.SECONDS).withLookingForStuckThread(true).build()

/** How long a graph test waits for something it started before failing with a stack. */
internal const val WAIT_MS = 30_000L

internal suspend fun kotlinx.coroutines.Job.joinWithin() = kotlinx.coroutines.withTimeout(WAIT_MS) { join() }

internal suspend fun <T> kotlinx.coroutines.Deferred<T>.awaitWithin(): T = kotlinx.coroutines.withTimeout(WAIT_MS) { await() }
