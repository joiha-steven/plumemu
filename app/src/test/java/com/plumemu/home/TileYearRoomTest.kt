package com.plumemu.home

import androidx.compose.ui.Alignment
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A text tile decides before it draws whether its year fits under the title (the title comes first): the year is never drawn in
 * the first frame and taken away by the second, which would rewrap the title a frame late.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class TileYearRoomTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun yearsAfterFrames(
        title: String,
        size: DpSize,
        frames: Int,
    ): Int {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme {
                CoverArt(title, null, size, Alignment.TopStart, PlumDimens.cornerCoverLandscape, PlumDimens.coverPaddingDetails, subtitle = YEAR)
            }
        }
        repeat(frames) { compose.mainClock.advanceTimeByFrame() }
        return compose.onAllNodesWithText(YEAR, useUnmergedTree = true).fetchSemanticsNodes().size
    }

    @Test
    fun aTitleThatNeedsTheYearsLineNeverShowsTheYearEvenInTheFirstFrame() {
        assertEquals("year in the first frame", 0, yearsAfterFrames(LONG_TITLE, DpSize(TILE_WIDTH, TILE_HEIGHT), 1))
    }

    /** The question the tile asks before it draws: how many lines the title takes at the tile's text width. */
    @Test
    fun theTitleFitAnswersHowManyLinesATitleTakes() {
        var long = 0
        var short = 0
        compose.setContent {
            PlumTheme {
                val fitter = currentTileTitleFit()
                val style = PlumTheme.type.gridFocusTitle
                val density = androidx.compose.ui.platform.LocalDensity.current
                long = fitter.lineCount(LONG_TITLE, style, 240, density)
                short = fitter.lineCount("Mario", style, 240, density)
            }
        }
        compose.waitForIdle()
        assertEquals("a short title", 1, short)
        assertTrue("a long title takes several lines: $long", long >= 3)
    }

    /** A tile with a short title has its year in the very first frame. */
    @Test
    fun aShortTitleKeepsItsYearFromTheFirstFrame() {
        assertEquals("year in the first frame", 1, yearsAfterFrames("Mario", DpSize(TILE_WIDTH, TILE_HEIGHT), 1))
    }

    /** The line count is kept with the fit's key: asked again (a grid built again, a tile recomposed) it measures nothing. */
    @Test
    fun theLineCountIsKeptWithTheFitKey() {
        val cache = TileTextCache()
        var lines = 0
        compose.setContent {
            PlumTheme {
                val density = androidx.compose.ui.platform.LocalDensity.current
                val fitter = TileTitleFit(androidx.compose.ui.text.rememberTextMeasurer(), cache)
                val style = PlumTheme.type.gridFocusTitle
                repeat(3) { lines += fitter.lineCount(LONG_TITLE, style, 240, density) }
            }
        }
        compose.waitForIdle()
        assertTrue("several lines: $lines", lines >= 9)
        assertEquals("measured once for three asks", 1, cache.lineCountComputations)
    }

    private companion object {
        const val YEAR = "1994"
        const val LONG_TITLE = "Legend of the Mystical Ninja Starring Goemon and Friends in a Very Long Adventure"
        val TILE_WIDTH = 120.dp
        val TILE_HEIGHT = 80.dp
    }
}
