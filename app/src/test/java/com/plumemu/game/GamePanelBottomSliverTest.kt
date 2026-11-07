package com.plumemu.game

import org.junit.Assert.assertEquals
import org.junit.Test

/** A row cut by the end of the game menu shows its text by half or more, or not at all, as a row cut at the top does. */
class GamePanelBottomSliverTest {
    private val line = listOf(10f to 30f)

    @Test
    fun aTextBarelyShowingGoesAndARowLeftWithNoTextGoesWhole() {
        assertEquals(12f, bottomSliver(height = 100f, top = 88f, lines = line), 0f)
    }

    @Test
    fun aRowWhoseTextIsPastTheEndGoesWhole() {
        assertEquals(6f, bottomSliver(height = 100f, top = 94f, lines = line), 0f)
    }

    @Test
    fun aTextShowingHalfOrMoreStays() {
        assertEquals(0f, bottomSliver(height = 100f, top = 80f, lines = line), 0f)
    }

    @Test
    fun theLabelOfATwoLineRowStaysWhenOnlyItsNoteIsASliver() {
        val two = listOf(10f to 30f, 34f to 50f)
        assertEquals(4f, bottomSliver(height = 78f, top = 40f, lines = two), 0f)
    }
}
