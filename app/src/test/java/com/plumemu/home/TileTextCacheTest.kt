package com.plumemu.home

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The title fit and the line probe of a titled tile are computed once per (title, styles, width, density, font scale) and
 * style, however many tiles or grids ask: L2/R2 composes a fresh grid, and its tiles measured text on the press frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class TileTextCacheTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val style = TextStyle(fontSize = 13.sp)
    private val steps = listOf(style, TextStyle(fontSize = 11.sp))

    private fun measurer(): TextMeasurer {
        lateinit var m: TextMeasurer
        compose.setContent { m = rememberTextMeasurer(cacheSize = 2) }
        compose.waitForIdle()
        return m
    }

    @Test
    fun repeatedIdenticalTilesFitOnceEvenAcrossGrids() {
        val cache = TileTextCache()
        val measurer = measurer()
        val density = Density(2f, 1f)
        val first = TileTitleFit(measurer, cache)
        val answer = first.fit("Super Mario World", steps, 200, density)
        repeat(25) { assertEquals(answer, first.fit("Super Mario World", steps, 200, density)) }
        // A new grid builds a new fitter: it must find the answer, not measure again.
        assertEquals(answer, TileTitleFit(measurer, cache).fit("Super Mario World", steps, 200, density))
        assertEquals(1, cache.fitComputations)
    }

    @Test
    fun whatDecidesTheAnswerIsPartOfTheKey() {
        val cache = TileTextCache()
        val fit = TileTitleFit(measurer(), cache)
        fit.fit("Zelda", steps, 200, Density(2f, 1f))
        fit.fit("Zelda", steps, 201, Density(2f, 1f))
        fit.fit("Zelda", steps, 200, Density(3f, 1f))
        fit.fit("Zelda", steps, 200, Density(2f, 1.3f))
        fit.fit("Metroid", steps, 200, Density(2f, 1f))
        fit.fit("Zelda", listOf(style), 200, Density(2f, 1f))
        assertEquals(6, cache.fitComputations)
    }

    @Test
    fun lineProbeRunsOncePerStyleAndDensity() {
        val cache = TileTextCache()
        val fit = TileTitleFit(measurer(), cache)
        val density = Density(2f, 1f)
        val height = fit.lineHeightPx(style, density)
        repeat(25) { assertEquals(height, fit.lineHeightPx(style, density)) }
        assertEquals(1, cache.lineComputations)
        fit.lineHeightPx(steps[1], density)
        assertEquals(2, cache.lineComputations)
    }

    @Test
    fun cacheIsBounded() {
        val cache = TileTextCache(capacity = 8)
        val fit = TileTitleFit(measurer(), cache)
        repeat(50) { fit.fit("Game $it", steps, 200, Density(2f, 1f)) }
        assertTrue(cache.size <= 8)
    }
}
