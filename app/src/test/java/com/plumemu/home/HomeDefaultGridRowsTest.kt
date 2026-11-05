package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
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
 * At Medium (the default) and Small the rows that fit the grid's height are whole and the next one is cut or absent, whatever the box
 * shape: landscape, portrait (0.71) and square boxes at the Pocket 5's 768, the Nova's 576 and the Pocket 6's 832.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeDefaultGridRowsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertWholeRows(label: String, system: String, aspect: Float, size: GridSize) {
        var layout: PlumLayout? = null
        compose.setContent {
            PlumTheme {
                com.plumemu.coreui.theme.ProvideGridSize(size) {
                    layout = PlumTheme.layout
                    HomeWithArt(rememberSource { realCoverSource(aspectOf = { _, _ -> aspect }) }, gridActions(boardIndexOf(system), 0), gridIds = setOf(system))
                }
            }
        }
        compose.waitForIdle()
        val sized = checkNotNull(layout)
        assertEquals("$label: size", size, sized.gridSize)
        val grid = bounds(HomeTags.GRID)
        // The first game of each row: the boxes of a row share its top, so the rows are the distinct tops.
        val tops = (0 until 120).filter { compose.onAllNodesWithTagCount(HomeTags.cover(it)) > 0 }.map { bounds(HomeTags.cover(it)) }.map { Math.round(it.top.value * 2) / 2f }.distinct().sorted()
        // The requirement changed: a size picks the column count and the row height follows the box's width and its shape, so how many
        // rows fit is whatever the height allows (it was three at Medium and four at Small from the grid's height).
        val first = bounds(HomeTags.cover(0))
        val rowHeight = (first.bottom - first.top).value
        val rows = tops.count { it + rowHeight <= grid.bottom.value + 0.5f }
        assertTrue("$label: at least one row fits whole", rows >= 1)
        assertTrue("$label: more rows than fit are composed to look at: $tops", tops.size > rows)
        assertEquals("$label: the whole rows are the first ones", tops.take(rows), tops.filter { it + rowHeight <= grid.bottom.value + 0.5f })
        assertTrue("$label: the next row does not fit whole (top ${tops[rows]}, grid bottom ${grid.bottom})", tops[rows] + rowHeight > grid.bottom.value + 0.5f)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun mediumLandscapeAt768() = assertWholeRows("Medium landscape 768", "snes", 1.4f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun mediumPortraitAt768() = assertWholeRows("Medium portrait 768", "megadrive", 0.71f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun mediumSquareAt768() = assertWholeRows("Medium square 768", "psx", 1f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun mediumLandscapeAt576() = assertWholeRows("Medium landscape 576", "snes", 1.4f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun mediumPortraitAt576() = assertWholeRows("Medium portrait 576", "megadrive", 0.71f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun mediumSquareAt576() = assertWholeRows("Medium square 576", "psx", 1f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun mediumLandscapeAt832() = assertWholeRows("Medium landscape 832", "snes", 1.4f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun mediumPortraitAt832() = assertWholeRows("Medium portrait 832", "megadrive", 0.71f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun mediumSquareAt832() = assertWholeRows("Medium square 832", "psx", 1f, GridSize.Medium)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun smallLandscapeAt768() = assertWholeRows("Small landscape 768", "snes", 1.4f, GridSize.Small)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun smallPortraitAt768() = assertWholeRows("Small portrait 768", "megadrive", 0.71f, GridSize.Small)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun smallSquareAt768() = assertWholeRows("Small square 768", "psx", 1f, GridSize.Small)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun smallLandscapeAt576() = assertWholeRows("Small landscape 576", "snes", 1.4f, GridSize.Small)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun smallPortraitAt576() = assertWholeRows("Small portrait 576", "megadrive", 0.71f, GridSize.Small)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun smallSquareAt576() = assertWholeRows("Small square 576", "psx", 1f, GridSize.Small)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun smallLandscapeAt832() = assertWholeRows("Small landscape 832", "snes", 1.4f, GridSize.Small)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun smallPortraitAt832() = assertWholeRows("Small portrait 832", "megadrive", 0.71f, GridSize.Small)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun smallSquareAt832() = assertWholeRows("Small square 832", "psx", 1f, GridSize.Small)
}
