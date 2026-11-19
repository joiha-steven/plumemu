package com.plumemu.graph

import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.settings.withFolderView
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
        writer.submit { it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Small)) }
        writer.submit { it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Huge)) }
        runCurrent()
        assertEquals("the second waits for the first", null, stored.appearance.textSize)
        gate.complete(Unit)
        runCurrent()
        assertEquals("the last change wins", TextSizeSetting.Huge, stored.appearance.textSize)
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
        writer.submit { it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Small)) }
        runCurrent()
        assertTrue("Settings is told", writer.failed.value)
        writer.submit { it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Huge)) }
        runCurrent()
        assertFalse("cleared once a write lands", writer.failed.value)
        assertEquals(listOf("Settings could not be saved (IOException)"), logged.toList())
        assertEquals(TextSizeSetting.Huge, stored.appearance.textSize)
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
        writer.submitTold({ it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Small)) }, { told += 1 })
        writer.submitTold({ it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Huge)) }, { told += 2 })
        runCurrent()
        assertEquals("only the write that failed is told", listOf(1), told)
        assertEquals(TextSizeSetting.Huge, stored.appearance.textSize)
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

    @Test
    fun aFoldersViewPressIsStoredInTheListsAndAFailedWriteIsTold() = runTest {
        var calls = 0
        var told = 0
        val writer =
            SettingsWriter(backgroundScope, { transform ->
                calls++
                if (calls == 3) throw IOException("disk")
                stored = transform(stored)
            }, logged::add)
        // The wiring MainActivity gives HomeCallbacks.onFolderView.
        val onFolderView = { id: String, isGrid: Boolean -> writer.submitTold({ it.withFolderView(id, isGrid) }, { told++ }) { _, _ -> } }
        onFolderView("collection:7", false)
        runCurrent()
        assertEquals(setOf("collection:7"), stored.appearance.listCollections)
        onFolderView("collection:7", true)
        runCurrent()
        assertEquals(emptySet<String>(), stored.appearance.listCollections)
        onFolderView("most_played", false)
        runCurrent()
        assertEquals("the failed write is told, nothing stored", 1 to emptySet<String>(), told to stored.appearance.listCollections)
    }

    /** The compiled-code restart ends the process only while [SettingsWriter.isIdle]: a write waiting or running is never lost. */
    @Test
    fun isIdleOnlyWhenNoWriteWaitsOrRuns() = runTest {
        val gate = CompletableDeferred<Unit>()
        val writer = SettingsWriter(backgroundScope, { transform -> gate.await(); stored = transform(stored) }, logged::add)
        assertTrue("nothing submitted", writer.isIdle)
        writer.submit { it.copy(appearance = it.appearance.copy(textSize = TextSizeSetting.Small)) }
        assertFalse("waiting in the queue", writer.isIdle)
        runCurrent()
        assertFalse("running", writer.isIdle)
        gate.complete(Unit)
        runCurrent()
        assertTrue("landed", writer.isIdle)
    }

    @Test
    fun aFailedWriteLeavesTheWriterIdle() = runTest {
        val writer = SettingsWriter(backgroundScope, { throw IOException("disk") }, logged::add)
        writer.submit { it }
        writer.resetAll(onPauseCleared = {})
        assertFalse(writer.isIdle)
        runCurrent()
        assertTrue("both failed, none pending", writer.isIdle)
    }
}
