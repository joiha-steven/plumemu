package com.plumemu.graph

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Work that stops for a game goes on at the return, and a return between the two looks loses nothing. */
internal class HeldForPlayTest {
    private class MemoryStore : HeldWorkStore {
        val marked = LinkedHashSet<HeldWork>()
        var stores = true

        override fun mark(work: HeldWork): Boolean {
            if (stores) marked += work
            return stores
        }

        override fun take(): Set<HeldWork> = marked.toSet().also { marked.clear() }
    }

    private val store = MemoryStore()

    @Test
    fun noGameNoHoldNoMark() = runTest {
        val held = HeldForPlay(store, { false })
        assertFalse(held.holds(HeldWork.ContentPass))
        assertEquals(emptySet<HeldWork>(), held.takeHeld())
    }

    @Test
    fun aGameHoldsAndTheReturnTakesEachOnce() = runTest {
        val held = HeldForPlay(store, { true })
        assertTrue(held.holds(HeldWork.ContentPass))
        assertTrue(held.holds(HeldWork.BoxArt))
        assertTrue(held.holds(HeldWork.BoxArt))
        assertEquals(setOf(HeldWork.ContentPass, HeldWork.BoxArt), held.takeHeld())
        assertEquals("taken once, no duplicate run", emptySet<HeldWork>(), held.takeHeld())
    }

    @Test
    fun aReturnBetweenTheTwoLooksLetsTheWorkGoOn() = runTest {
        var looks = 0
        // Playing at the first look, back by the second: the work is not stopped (nothing would ask for it again).
        val held = HeldForPlay(store, { looks++ == 0 })
        assertFalse(held.holds(HeldWork.ContentPass))
        assertEquals("the mark left asks once more, harmless", setOf(HeldWork.ContentPass), held.takeHeld())
    }

    @Test
    fun aMarkThatCannotBeStoredDoesNotHold() = runTest {
        store.stores = false
        val logs = mutableListOf<String>()
        val held = HeldForPlay(store, { true }, { logs += it })
        assertFalse("nothing would ask for the work again: it goes on", held.holds(HeldWork.BoxArt))
        assertTrue(logs.single().contains("could not be stored"))
    }

    @Test
    fun theReturnTakesTheMarksAndAsksForEachWorkOnce() = runTest {
        val held = HeldForPlay(store, { true })
        held.holds(HeldWork.ContentPass)
        held.holds(HeldWork.BoxArt)
        var passes = 0
        var boxArt = 0
        resumeHeld(held, { passes++ }, { boxArt++ }) {}
        assertEquals(1, passes)
        assertEquals(1, boxArt)
        assertTrue("marks taken", store.marked.isEmpty())
        resumeHeld(held, { passes++ }, { boxArt++ }) {}
        assertEquals("a second return asks for nothing", 1 to 1, passes to boxArt)
    }

    @Test
    fun onlyTheMarkedWorkIsAskedFor() = runTest {
        val held = HeldForPlay(store, { true })
        held.holds(HeldWork.BoxArt)
        var passes = 0
        var boxArt = 0
        resumeHeld(held, { passes++ }, { boxArt++ }) {}
        assertEquals(0 to 1, passes to boxArt)
    }

    @Test
    fun aPlayNoteThatCouldNotBeClearedHoldsNothingOncePlumemuIsInFront() = runTest {
        var inFront = false
        // The note stays because PendingSessionStore.clear() failed (PlayHandOffTest shows the stuck note).
        val held = HeldForPlay(store, playingAway({ inFront }) { true })
        assertTrue("away in the game: held", held.holds(HeldWork.ContentPass))
        inFront = true
        assertFalse("back in Plumemu: the stuck note no longer holds", held.holds(HeldWork.ContentPass))
        assertFalse(held.holds(HeldWork.BoxArt))
    }
}
