package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** [arrangeCells] as a pure function: keeping every text whole comes before keeping the rows on one column line. */
class ArrangeCellsTest {
    private val needs =
        mapOf(
            DetailsCell.GENRE to .62f,
            DetailsCell.RATING to .35f,
            DetailsCell.PLAYED to .28f,
            DetailsCell.LAST to .41f,
            DetailsCell.EMULATOR to .40f,
            DetailsCell.BATTERY to .46f,
        )
    private val floors = mapOf(DetailsCell.GENRE to .3f, DetailsCell.EMULATOR to .3f)

    @Test
    fun wholeTextComesBeforeOneColumnLine() {
        val shown = arrangeCells(needs, batteryShortNeed = .36f, maxRows = 3, floors = floors)
        assertEquals(
            listOf(
                listOf(DetailsCell.GENRE, DetailsCell.RATING),
                listOf(DetailsCell.PLAYED, DetailsCell.LAST),
                listOf(DetailsCell.EMULATOR, DetailsCell.BATTERY),
            ),
            shown.rows,
        )
        assertFalse("the battery keeps its full text", shown.batteryShort)
    }

    @Test
    fun oneColumnLineChoosesBetweenWholeArrangements() {
        val whole = needs + (DetailsCell.GENRE to .3f) + (DetailsCell.RATING to .3f) + (DetailsCell.EMULATOR to .7f) + (DetailsCell.BATTERY to .28f)
        val shown = arrangeCells(whole, batteryShortNeed = null, maxRows = 3, floors = floors)
        val pairs = shown.rows.filter { it.size == 2 }.map { shown.shares.getValue(it[0]) }
        assertEquals("rows of two start their second cell on one line: ${shown.rows}", pairs.max(), pairs.min(), .001f)
    }
}
