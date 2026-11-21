package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Background palette is a whole 8 x 6 grid of backgrounds text can read on. */
class CustomPaletteTest {
    private val swatches = CustomPalette.backgroundSwatches

    private fun at(
        row: Int,
        column: Int,
    ) = swatches[row * CustomPalette.hueCount + column]

    @Test
    fun hasEightHuesBySixRowsAndEveryCellReads() {
        assertEquals(8, CustomPalette.hueCount)
        assertEquals(6, CustomPalette.ROWS)
        assertEquals(48, swatches.size)
        val bad = swatches.filterNot(Readability::backgroundAllowsText).map { it.toHex() }
        assertTrue("cells no text reads on: ${bad.map { h -> h + "->" + Readability.nearestUsableBackground(CustomPalette.backgroundSwatches.first { it.toHex() == h }).toHex() }}", bad.isEmpty())
        assertEquals("every cell differs", 48, swatches.toSet().size)
    }

    @Test
    fun lightRowsCarryDarkTextAndDeepRowsLightText() {
        for (column in 0 until 8) {
            for (row in 0 until 3) assertTrue("row $row column $column ${at(row, column).toHex()} takes dark text ${Readability.textFailures(TextMode.Dark.color, at(row, column))}", Readability.passes(TextMode.Dark.color, at(row, column), ColorRole.Text))
            for (row in 3 until 6) assertTrue("row $row column $column ${at(row, column).toHex()} takes light text ${Readability.textFailures(TextMode.Light.color, at(row, column))}", Readability.passes(TextMode.Light.color, at(row, column), ColorRole.Text))
        }
    }

    @Test
    fun theFirstColumnIsNeutralWithAnAlmostBlackAndAnAlmostWhite() {
        for (row in 0 until 6) assertTrue("row $row is neutral", at(row, 0).let { maxOf(it.red, it.green, it.blue) - minOf(it.red, it.green, it.blue) } <= 0x10)
        assertTrue("near black: ${at(5, 0).toHex()}", at(5, 0).red <= 0x12)
        assertTrue("near white: ${at(2, 0).toHex()}", at(2, 0).red >= 0xF0)
    }

    @Test
    fun eachColumnKeepsItsHueAndTheRowsStep() {
        for (column in 1 until 8) {
            val hues = (0 until 6).map { ColorMath.toHsl(at(it, column)).first * 360 }
            val spread = hues.maxOf { h -> hues.maxOf { o -> minOf(kotlin.math.abs(h - o), 360 - kotlin.math.abs(h - o)) } }
            assertTrue("column $column keeps its hue: $hues", spread <= 30)
        }
        for (column in 0 until 8) {
            val lightness = (0 until 6).map { ColorMath.toHsl(at(it, column)).third }
            assertEquals("column $column: the light rows get paler", lightness.take(3).sorted(), lightness.take(3))
            assertEquals("column $column: the deep rows get darker", lightness.drop(3).sortedDescending(), lightness.drop(3))
            assertTrue("column $column: the deep half is below the light half", lightness[3] < lightness[0])
        }
    }

    @Test
    fun theVividRowsCarryColourNotGreyishTints() {
        for (column in 1 until 8) assertTrue("row 1 column $column saturated", ColorMath.toHsl(at(0, column)).second >= 0.5)
        for (column in 1 until 8) assertTrue("row 4 column $column saturated", ColorMath.toHsl(at(3, column)).second >= 0.5)
    }

    @Test
    fun theNamedAnchorsAreInTheGrid() {
        assertEquals("#E8BE46", at(0, 3).toHex()) // lemon
        assertEquals("#EA934C", at(0, 2).toHex()) // tangerine
        assertEquals("#BDB3DB", at(1, 7).toHex()) // lavender
        assertEquals("#143FA4", at(3, 6).toHex()) // cobalt
        assertEquals("#6A1F1F", at(4, 1).toHex()) // wine
    }
}
