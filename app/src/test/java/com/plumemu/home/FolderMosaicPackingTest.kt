package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The folder card's packing: one justified row of the most covers that keep their pictures tall enough; else one left-aligned cover. */
class FolderMosaicPackingTest {
    private val gap = 8f
    private val chromeX = 4f
    private val chromeY = 6f

    private fun pack(aspects: List<Float>, width: Float = 300f, height: Float = 1000f) = mosaicRow(aspects, width, height, gap, chromeX, chromeY)

    // Tall 3DS, square GB, wide N64, narrow Switch.
    private val mixed = listOf(0.9f, 1f, 1.4f, 0.6f)

    @Test
    fun theMostCoversThatKeepTheirPicturesTallEnoughFormOneRowSpanningTheWidth() {
        val row = pack(mixed, width = 300f)!!
        assertEquals(4, row.boxes.size)
        for ((i, b) in row.boxes.withIndex()) {
            assertEquals((row.height - chromeY) * mixed[i] + chromeX, b.width, 0.01f)
            if (i > 0) assertEquals(gap, b.x - (row.boxes[i - 1].x + row.boxes[i - 1].width), 0.01f)
        }
        assertEquals(300f, row.boxes.last().x + row.boxes.last().width, 0.01f)
    }

    @Test
    fun aNarrowerColumnDropsCoversUntilThePicturesAreTallEnough() {
        val four = pack(mixed, width = 300f)!!
        val fewer = pack(mixed, width = 230f)!!
        assertTrue(fewer.boxes.size < four.boxes.size)
        assertTrue("pictures stay at least 60 dp tall", fewer.height - chromeY >= 60f)
        assertEquals(230f, fewer.boxes.last().x + fewer.boxes.last().width, 0.01f)
    }

    @Test
    fun threeCoversStandWhenTheirPicturesReachSixtyAndFourWouldNot() {
        // Four of the mixed shapes at 236 dp wide give a picture under 64; three give over 60.
        val row = pack(mixed, width = 236f)!!
        assertEquals(3, row.boxes.size)
        assertTrue(row.height - chromeY >= 60f)
    }

    @Test
    fun aRowTallerThanTheRoomDropsCoversToo() {
        val row = pack(mixed, width = 300f, height = 80f)
        assertTrue(row == null || row.height <= 80f)
    }

    @Test
    fun whenNotEvenTwoFitOneCoverStandsLeftAtItsOwnShapeCappedToTheHeight() {
        val row = pack(mixed, width = 120f, height = 60f)!!
        assertEquals(1, row.boxes.size)
        assertEquals(0f, row.boxes[0].x, 0f)
        assertEquals(60f, row.height, 0.01f)
        assertEquals((60f - chromeY) * 0.9f + chromeX, row.boxes[0].width, 0.01f)
    }

    @Test
    fun aSingleCoverWithRoomFillsTheWidth() {
        assertEquals(300f, pack(listOf(1.4f))!!.boxes.single().width, 0.01f)
    }

    @Test
    fun noCoversOrNoWidthIsNoRow() {
        assertNull(pack(emptyList()))
        assertNull(pack(mixed, width = 0f))
        assertNotNull(pack(mixed))
    }
}
