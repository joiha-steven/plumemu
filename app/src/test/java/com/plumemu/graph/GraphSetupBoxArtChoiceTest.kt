package com.plumemu.graph

import com.plumemu.coredata.settings.BoxArtSourceSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Setup scans the folder before it asks about box art, so the scan can end before "Box art only" is
 * chosen. That end found no source (no account, nothing chosen) and asked for no run; the choice
 * itself must start one, or no box art is downloaded until some later scan ends.
 */
internal class GraphSetupBoxArtChoiceTest : GraphScrapesHarness() {
    @Test
    fun boxArtOnlyChosenAfterTheScanEndedStartsTheRun() = runBlocking<Unit> {
        val scrapes = graph()
        // The setup scan ended with nothing chosen: no account, so own covers are in force and no run is asked.
        scrapes.requestRun().join()
        delay(300)
        assertEquals("nothing to run before the choice", 0, runs().size)

        scrapes.chooseInSetup(BoxArtSourceSetting.LibretroOnly)

        assertEquals(BoxArtSourceSetting.LibretroOnly, chosenSource.value)
        awaitRun()
        assertEquals("one run, started by the choice", 1, runs().size)
    }

    @Test
    fun ownCoversChosenInSetupStartsNoRun() = runBlocking<Unit> {
        val scrapes = graph()
        scrapes.chooseInSetup(BoxArtSourceSetting.OwnCovers)
        delay(300)
        assertEquals(BoxArtSourceSetting.OwnCovers, chosenSource.value)
        assertEquals(0, runs().size)
    }
}
