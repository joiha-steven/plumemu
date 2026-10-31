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
        assertEquals("#E98D43", at(0, 2).toHex()) // tangerine
        assertEquals("#B4A9D6", at(1, 7).toHex()) // lavender
        assertEquals("#1541A9", at(3, 6).toHex()) // cobalt
        assertEquals("#6A1F1F", at(4, 1).toHex()) // wine
    }

    @Test
    fun theTextPaletteOffersOnlyPassingColoursOfTheSideThatReads() {
        val black = Rgb(0x000000)
        val paper = BackgroundPreset.Paper.color
        val banana = ColorSet.Banana.backgroundColor
        for ((name, bg) in listOf("Black" to black, "Graphite" to BackgroundPreset.Graphite.color, "Paper" to paper, "Banana" to banana)) {
            val cells = CustomPalette.textSwatchesOn(bg)
            assertTrue("$name offers cells", cells.size >= 8)
            assertTrue("$name fits the 8 x 6 grid", cells.size <= CustomPalette.hueCount * CustomPalette.ROWS)
            for (rgb in cells) assertTrue("$name ${rgb.toHex()}", Readability.passes(rgb, bg, ColorRole.Text))
        }
        assertTrue("lights on Black", CustomPalette.textSwatchesOn(black).all { ColorMath.toHsl(it).third >= 0.5 })
        assertTrue("darks on Paper", CustomPalette.textSwatchesOn(paper).all { ColorMath.toHsl(it).third <= 0.5 })
        assertTrue("nothing reads on a middle grey", CustomPalette.textSwatchesOn(Rgb(0x808080)).isEmpty())
    }

    @Test
    fun theAccentPaletteOffersOnlyColoursAtThreeToOne() {
        for (bg in listOf(Rgb(0x000000), BackgroundPreset.Paper.color, ColorSet.Banana.backgroundColor)) {
            val cells = CustomPalette.accentSwatchesOn(bg)
            assertTrue(cells.isNotEmpty())
            for (rgb in cells) assertTrue(rgb.toHex(), Readability.passes(rgb, bg, ColorRole.Accent))
        }
    }

    @Test
    fun theTextAndAccentPalettesAreFullGridsWithOneColumnPerHue() {
        for (bg in listOf(Rgb(0x000000), BackgroundPreset.Graphite.color, BackgroundPreset.Paper.color, ColorSet.Banana.backgroundColor)) {
            for (cells in listOf(CustomPalette.textSwatchesOn(bg), CustomPalette.accentSwatchesOn(bg))) {
                assertEquals("8 x 6, no gaps", CustomPalette.hueCount * CustomPalette.readableShadeCount, cells.size)
                // The first column is grey; every other column keeps its hue down the rows.
                for (row in 0 until CustomPalette.readableShadeCount) {
                    val grey = cells[row * CustomPalette.hueCount]
                    assertEquals("grey column, row $row", 0.0, ColorMath.toHsl(grey).second, 0.02)
                }
                for (column in 1 until CustomPalette.hueCount) {
                    val hues = (0 until CustomPalette.readableShadeCount).map { ColorMath.toHsl(cells[it * CustomPalette.hueCount + column]).first }
                    assertTrue("column $column keeps one hue: $hues", hues.max() - hues.min() < 0.05)
                }
            }
        }
    }

    @Test
    fun theTextPaletteReachesSaturatedColoursOnBlack() {
        // 7:1 instead of the sets' 11:1 lets amber, green and the like through.
        val cells = CustomPalette.textSwatchesOn(Rgb(0x000000))
        val bold = cells.filter { ColorMath.toHsl(it).let { (_, s, l) -> s > 0.7 && l < 0.7 } }
        assertTrue("saturated cells on Black: ${bold.size}", bold.size >= 8)
    }

    private val backgrounds =
        listOf(
            "Paper" to BackgroundPreset.Paper.color,
            "Ivory" to ColorSet.Ivory.backgroundColor,
            "Graphite" to BackgroundPreset.Graphite.color,
            "Black" to Rgb(0x000000),
            "Yellow" to at(0, 3),
            "Navy" to Rgb(0x14337A),
        )

    @Test
    fun textAndAccentPalettesAreWholeAndPassOnPaperIvoryGraphiteBlackYellowAndNavy() {
        for ((name, bg) in backgrounds) {
            val text = CustomPalette.textSwatchesOn(bg)
            val accent = CustomPalette.accentSwatchesOn(bg)
            assertEquals("$name text 8 x 6", 48, text.size)
            assertEquals("$name accent 8 x 6", 48, accent.size)
            for (cell in text) assertTrue("$name text ${cell.toHex()}", Readability.passes(cell, bg, ColorRole.Text))
            for (cell in accent) assertTrue("$name accent ${cell.toHex()}", Readability.passes(cell, bg, ColorRole.Accent))
        }
    }

    @Test
    fun theAccentPaletteIsVividInEveryHueColumnOnLightAndDarkBackgrounds() {
        for ((name, bg) in backgrounds) {
            val accent = CustomPalette.accentSwatchesOn(bg)
            for (column in 1 until 8) {
                val best = (0 until 6).maxOf { ColorMath.toHsl(accent[it * 8 + column]).second }
                assertTrue("$name column $column is vivid (saturation $best)", best >= 0.6)
            }
        }
    }
}
