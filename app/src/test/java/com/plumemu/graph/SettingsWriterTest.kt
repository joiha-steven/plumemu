package com.plumemu.graph

import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.PlumSettings
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Settings' writes land in the order they were made; a failed one is logged and the next still lands. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsWriterTest {
    private var stored = PlumSettings()
    private val logged = CopyOnWriteArrayList<String>()

    @Test
    fun writesLandInOrderEvenWhenTheFirstIsSlow() = runTest {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val writer =
            SettingsWriter(backgroundScope, { transform ->
                if (first) {
                    first = false
                    gate.await()
                }
                stored = transform(stored)
            }, logged::add)
        writer.submit { it.copy(appearance = it.appearance.copy(font = FontSetting.Serif)) }
        writer.submit { it.copy(appearance = it.appearance.copy(font = FontSetting.Mono)) }
        runCurrent()
        assertEquals("the second waits for the first", FontSetting.Sans, stored.appearance.font)
        gate.complete(Unit)
        runCurrent()
        assertEquals("the last change wins", FontSetting.Mono, stored.appearance.font)
    }

    @Test
    fun aFailedWriteIsLoggedByClassAndTheNextStillLands() = runTest {
        var calls = 0
        val writer =
            SettingsWriter(backgroundScope, { transform ->
                calls++
                if (calls == 1) throw IOException("/data/user/0/com.plumemu/files/datastore/settings.preferences_pb")
                stored = transform(stored)
            }, logged::add)
        writer.submit { it.copy(appearance = it.appearance.copy(font = FontSetting.Serif)) }
        runCurrent()
        assertTrue("Settings is told", writer.failed.value)
        writer.submit { it.copy(appearance = it.appearance.copy(font = FontSetting.Mono)) }
        runCurrent()
        assertFalse("cleared once a write lands", writer.failed.value)
        assertEquals(listOf("Settings could not be saved (IOException)"), logged.toList())
        assertEquals(FontSetting.Mono, stored.appearance.font)
    }

    @Test
    fun aToldWriteSaysWhenItFailedAndOnlyThen() = runTest {
        var calls = 0
        val writer =
            SettingsWriter(backgroundScope, { transform ->
                calls++
                if (calls == 1) throw IOException("disk")
                stored = transform(stored)
            }, logged::add)
        val told = mutableListOf<Int>()
        writer.submitTold({ it.copy(appearance = it.appearance.copy(font = FontSetting.Serif)) }, { told += 1 })
        writer.submitTold({ it.copy(appearance = it.appearance.copy(font = FontSetting.Mono)) }, { told += 2 })
        runCurrent()
        assertEquals("only the write that failed is told", listOf(1), told)
        assertEquals(FontSetting.Mono, stored.appearance.font)
    }

    @Test
    fun resetAllSettingsThatClearsThePersonsBoxArtPauseAsksForARunOnceItLanded() = runTest {
        stored = PlumSettings(library = PlumSettings().library.copy(boxArtPaused = true))
        val gate = CompletableDeferred<Unit>()
        val writer =
            SettingsWriter(backgroundScope, { transform ->
                gate.await()
                stored = transform(stored)
            }, logged::add)
        var runs = 0
        writer.resetAll(onPauseCleared = { runs++ })
        runCurrent()
        assertEquals("not before the write lands: the run would still read the pause", 0, runs)
        gate.complete(Unit)
        runCurrent()
        assertFalse(stored.library.boxArtPaused)
        assertEquals(1, runs)
        writer.resetAll(onPauseCleared = { runs++ })
        runCurrent()
        assertEquals("not paused before: nothing more is asked", 1, runs)
    }

    @Test
    fun aResetThatFailsAsksForNoRun() = runTest {
        stored = PlumSettings(library = PlumSettings().library.copy(boxArtPaused = true))
        val writer = SettingsWriter(backgroundScope, { throw IOException("disk full") }, logged::add)
        var runs = 0
        writer.resetAll(onPauseCleared = { runs++ })
        runCurrent()
        assertEquals(0, runs)
        assertTrue(writer.failed.value)
    }
}
