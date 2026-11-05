package com.plumemu.graph

import com.plumemu.coredata.db.ArtAspectPass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The stored shapes' pass stays out of the way: nothing without box art, and a failure never reaches the home content and is tried again. */
class KeepArtAspectsTest {
    @Test
    fun withoutBoxArtTheDatabaseIsNotTouched() = runTest {
        var opened = 0
        val emitted = keepArtAspects(flowOf(false), artAspectPasses { opened++; error("not opened") }, { error("no log: $it") }, {}).toList()
        assertEquals(emptyList<Nothing>(), emitted)
        assertEquals(0, opened)
    }

    /** Read on a device: numbers only, one line a pass, the rows written first. */
    @Test
    fun eachPassLogsItsCost() = runTest {
        val lines = mutableListOf<String>()
        keepArtAspects(flowOf(true), flowOf(ArtAspectPass(1200, 340, 95), ArtAspectPass(0, 12, 0)), { error("no failure: $it") }, lines::add).toList()
        assertEquals(listOf("Box art shapes: 1200 rows written, read 340 ms, write 95 ms", "Box art shapes: 0 rows written, read 12 ms, write 0 ms"), lines)
    }

    @Test
    fun aFailedPassIsLoggedByClassAndTriedAgainThirtySecondsLater() = runTest {
        val logs = mutableListOf<String>()
        var attempts = 0
        val passes =
            flow {
                if (++attempts == 1) throw IllegalStateException("/data/secret/path")
                emit(ArtAspectPass(1, 0, 0))
            }
        val emitted = keepArtAspects(flowOf(true), passes, logs::add, {}).toList()
        assertEquals(emptyList<Nothing>(), emitted)
        assertEquals(listOf("Box art shapes could not be stored (IllegalStateException)"), logs)
        assertEquals(2, attempts)
        assertEquals(30_000L, currentTime)
    }

    @Test
    fun aDatabaseThatCannotOpenIsTriedAgainWhileCollected() = runTest {
        val logs = mutableListOf<String>()
        val collecting = launch { keepArtAspects(flowOf(true), artAspectPasses { throw IllegalStateException("/data/secret/path") }, logs::add, {}).toList() }
        advanceTimeBy(95_000)
        collecting.cancel()
        assertEquals("at once, then every 30 s", 4, logs.size)
    }

    @Test
    fun aCancelledPassIsNeitherLoggedNorRetried() = runTest {
        var attempts = 0
        val passes = flow<ArtAspectPass> { attempts++; throw CancellationException("stopped") }
        keepArtAspects(flowOf(true), passes, { error("no log: $it") }, {}).toList()
        assertEquals(1, attempts)
    }
}
