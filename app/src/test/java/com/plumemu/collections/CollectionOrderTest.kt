package com.plumemu.collections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionOrderTest {
    private val start = CollectionOrder(listOf(1, 2, 3, 4))

    @Test
    fun carryARowDownAndDrop() {
        val dropped = start.pickUp(1).moveDown().moveDown().drop()
        assertEquals(listOf(2L, 3L, 1L, 4L), dropped.newOrder)
        assertFalse(dropped.state.isHolding)
        assertEquals(listOf(2L, 3L, 1L, 4L), dropped.state.order)
    }

    @Test
    fun carryARowUp() {
        assertEquals(listOf(1L, 4L, 2L, 3L), start.pickUp(4).moveUp().moveUp().drop().newOrder)
    }

    @Test
    fun droppingInTheSamePlaceChangesNothing() {
        assertNull(start.pickUp(2).drop().newOrder)
        assertNull("there and back", start.pickUp(2).moveDown().moveUp().drop().newOrder)
    }

    @Test
    fun theEdgesStop() {
        assertEquals(listOf(1L, 2L, 3L, 4L), start.pickUp(1).moveUp().order)
        assertEquals(listOf(1L, 2L, 3L, 4L), start.pickUp(4).moveDown().order)
    }

    @Test
    fun cancelPutsTheOrderBack() {
        val cancelled = start.pickUp(1).moveDown().moveDown().cancel()
        assertEquals(listOf(1L, 2L, 3L, 4L), cancelled.order)
        assertFalse(cancelled.isHolding)
    }

    @Test
    fun movingWhileIdleOrPickingAnUnknownRowDoesNothing() {
        assertEquals(start, start.moveDown())
        assertEquals(start, start.pickUp(99))
        assertNull(start.drop().newOrder)
        assertEquals(start, start.cancel())
    }

    @Test
    fun aSecondPickUpWhileHoldingIsIgnored() {
        assertEquals(2L, start.pickUp(2).pickUp(3).held)
    }

    @Test
    fun oneCollectionAndNoneAreSafe() {
        assertNull(CollectionOrder(listOf(7)).pickUp(7).moveUp().moveDown().drop().newOrder)
        assertNull(CollectionOrder(emptyList()).pickUp(1).drop().newOrder)
    }

    @Test
    fun aCollectionDeletedWhileCarryingKeepsTheCarriedRowOrLetsItGo() {
        val carrying = start.pickUp(1).moveDown() // 2,1,3,4
        val other = carrying.refreshed(listOf(1, 3, 4)) // 2 deleted
        assertTrue(other.isHolding)
        assertEquals("1 stays where it was carried to", listOf(3L, 1L, 4L), other.order)
        assertEquals(listOf(3L, 1L, 4L), other.drop().newOrder)
        val gone = carrying.refreshed(listOf(2, 3, 4)) // the carried one deleted
        assertFalse(gone.isHolding)
        assertEquals(listOf(2L, 3L, 4L), gone.order)
    }
}
