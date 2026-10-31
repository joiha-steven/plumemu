package com.plumemu.settings.screen

import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorRole
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.Readability
import com.plumemu.coreui.theme.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "+" page's curated swatches: every tile reads on its page, no two look alike, and the
 * grid is three rows of eight. A plain JVM test: the palette and the readability test are pure.
 */
class PaletteCurationTest {
    private val backgrounds: Map<String, Rgb> =
        mapOf(
            "Black" to Rgb(0x000000),
            "Graphite" to BackgroundPreset.Graphite.color,
            "Paper" to BackgroundPreset.Paper.color,
            "Banana" to ColorSet.Banana.backgroundColor,
        )

    private fun pageSwatches(
        kind: ColorKind,
        background: Rgb,
    ): List<Rgb> =
        ColorPages.palette(
            when (kind) {
                ColorKind.Background -> PanelPage.BackgroundCustom
                ColorKind.Text -> PanelPage.TextCustom
                else -> PanelPage.AccentCustom
            },
            background,
        )

    private fun assertDistinct(
        label: String,
        tiles: List<Rgb>,
    ) {
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                val d = PaletteCuration.distance(tiles[i], tiles[j])
                assertTrue("$label: ${tiles[i].toHex()} and ${tiles[j].toHex()} are ${"%.1f".format(d)} apart", d >= PaletteCuration.MIN_DISTANCE)
            }
        }
    }

    @Test
    fun theBackgroundPageHasAtMostThreeRowsOfEightAndEveryTileTakesText() {
        val tiles = pageSwatches(ColorKind.Background, Rgb(0))
        println("background tiles: ${tiles.size}")
        assertTrue("at most 3 rows of 8, got ${tiles.size}", tiles.size <= PaletteCuration.COLUMNS * PaletteCuration.ROWS)
        assertTrue("at least 2 rows, got ${tiles.size}", tiles.size >= 2 * PaletteCuration.COLUMNS)
        for (rgb in tiles) assertTrue(rgb.toHex(), Readability.backgroundAllowsText(rgb))
        assertDistinct("Background", tiles)
    }

    @Test
    fun theTextAndAccentPagesPassTheirReadabilityTestOnEveryBackgroundAndNoTwoTilesLookAlike() {
        for ((name, bg) in backgrounds) {
            val text = pageSwatches(ColorKind.Text, bg)
            val accent = pageSwatches(ColorKind.Accent, bg)
            println("$name: text ${text.size}, accent ${accent.size}")
            assertTrue("$name text fits 3 rows of 8", text.size <= PaletteCuration.COLUMNS * PaletteCuration.ROWS)
            assertTrue("$name accent fits 3 rows of 8", accent.size <= PaletteCuration.COLUMNS * PaletteCuration.ROWS)
            assertTrue("$name accent has tiles", accent.size >= PaletteCuration.COLUMNS)
            for (rgb in text) assertTrue("$name text ${rgb.toHex()}", Readability.passes(rgb, bg, ColorRole.Text))
            for (rgb in accent) assertTrue("$name accent ${rgb.toHex()}", Readability.passes(rgb, bg, ColorRole.Accent))
            assertDistinct("$name text", text)
            assertDistinct("$name accent", accent)
        }
    }

    @Test
    fun aPaletteNothingReadsOnStaysEmpty() {
        assertEquals(emptyList<Rgb>(), PaletteCuration.pick(emptyList()))
    }
}

/** The Background "+" page tiles, as the page lists them (curated); tests that walk the grid index into this. */
internal val backgroundGrid: List<Rgb> = ColorPages.palette(PanelPage.BackgroundCustom, Rgb(0))
