package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The games list's pages beside the shown one are asked for ahead of time, by entry id: a page no
 * longer beside the shown one is cancelled, the page being shown is never cancelled (it uses what
 * was built for it), and the page just left is kept without being built again. [PagesAhead] is the
 * logic of [NeighbourPagesPrefetch]; Compose's handles cannot be made outside Compose.
 */
class NeighbourPagesPrefetchTest {
    private val scheduled = mutableListOf<Int>()
    private val cancelled = mutableListOf<Int>()
    private val pages = PagesAhead<String, Int> { cancelled += it }
    private val keys = ('a'..'s').map { it.toString() }

    private fun show(
        page: String,
        among: List<String> = keys,
        canBuild: (String) -> Boolean = { true },
    ) = pages.show(page, among, canBuild) { index -> index.also { scheduled += it } }

    @Test
    fun asksForThePageBeforeAndAfterTheShownOne() {
        show("f")
        assertEquals(listOf(4, 6), scheduled)
        assertEquals(setOf("e", "g"), pages.ahead)
    }

    @Test
    fun aMoveKeepsThePageLeftCancelsTheFarOneAndAsksForTheNext() {
        show("f")
        show("g")
        assertEquals("the page now two away is dropped; the shown one is not cancelled", listOf(4), cancelled)
        assertEquals("only the next page is built: the page left is kept as it is", listOf(4, 6, 7), scheduled)
        assertEquals(setOf("f", "h"), pages.ahead)
    }

    @Test
    fun theSameLayoutAgainAsksForNothingNew() {
        show("f")
        show("f")
        assertEquals(listOf(4, 6), scheduled)
        assertEquals(emptyList<Int>(), cancelled)
    }

    @Test
    fun aJumpOfSeveralSystemsDropsBothOldNeighboursAndKeepsNothing() {
        show("f")
        show("k")
        assertEquals(listOf(4, 6), cancelled.sorted())
        assertEquals(listOf(4, 6, 9, 11), scheduled)
        assertEquals(setOf("j", "l"), pages.ahead)
    }

    @Test
    fun theEndsHaveOneNeighbour() {
        show("a")
        assertEquals(setOf("b"), pages.ahead)
        show("s")
        assertEquals(setOf("r"), pages.ahead)
    }

    /** A rescan or Show Android apps adds an entry beside the shown one: the indices move, the ids do not. */
    @Test
    fun anEntryInsertedBesideTheShownOneIsAskedForAndTheReadyPageIsKept() {
        show("b", listOf("a", "b", "c"))
        show("b", listOf("a", "x", "b", "c"))
        assertEquals("the old neighbour a is dropped", listOf(0), cancelled)
        assertEquals("x is asked for at its new index; c is not asked for again", listOf(0, 2, 1), scheduled)
        assertEquals(setOf("x", "c"), pages.ahead)
    }

    @Test
    fun anEntryRemovedBesideTheShownOneIsCancelled() {
        show("b", listOf("a", "b", "c", "d"))
        show("b", listOf("b", "c", "d"))
        assertEquals(listOf(0), cancelled)
        assertEquals(setOf("c"), pages.ahead)
    }

    @Test
    fun gridEntriesGetNoListPage() {
        show("f", canBuild = { it != "g" })
        assertEquals(listOf(4), scheduled)
        assertEquals(setOf("e"), pages.ahead)
    }

    @Test
    fun theShownEntryMissingAsksForNothing() {
        show("zz")
        assertEquals(emptyList<Int>(), scheduled)
    }
}
