package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An app game among file games is a cover card (its icon on a gradient of the icon's colour, the system band, no name) of
 * the same size as its neighbours; with no icon it is the poster with its title; a grid of app games only stays Apps tiles.
 * Both themes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppGameCoverTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        settings: PlumThemeSettings,
        content: @Composable () -> Unit,
    ) {
        compose.setContent { PlumTheme(settings) { content() } }
        compose.waitForIdle()
    }

    private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun inCell(
        tag: String,
        index: Int,
    ) = compose.onAllNodes(hasTestTag(tag) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).fetchSemanticsNodes().size

    /** Column widths differ by a pixel where the grid shares out a fractional width: one px (0.5 dp at xhdpi) is the same width. */
    private fun assertSame(message: String, expected: Dp, actual: Dp) = assertTrue("$message: $expected vs $actual", abs(expected.value - actual.value) <= 0.5f)

    private fun assertSame(expected: Dp, actual: Dp) = assertSame("width", expected, actual)

    private fun bounds(index: Int) = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).getBoundsInRoot()

    private fun mixedGridIsCards(settings: PlumThemeSettings) {
        show(settings) { AppGamesBoard(PORTS_ENTRY) }
        for (app in listOf(2, 3, 5)) {
            val file = bounds(0)
            val cell = bounds(app)
            assertSame("cell $app same width as a file game's", file.width, cell.width)
            assertEquals("cell $app same height", file.height, cell.height)
            val row = (0..11).filter { it !in listOf(2, 3, 5) }.mapNotNull { runCatching { bounds(it) }.getOrNull() }.firstOrNull { it.top == cell.top }
            // A card may stand alone on its row (five columns now): then only its height, checked above, is compared.
            row?.let { assertEquals("cell $app same bottom", it.bottom, cell.bottom) }
        }
        // Without a picture the file game's poster fills its cell; the app game's card is as large as that poster.
        val poster = compose.onNode(hasTestTag(HomeTags.POSTER_COVER) and hasAnyAncestor(hasTestTag(HomeTags.cover(0))), useUnmergedTree = true).getBoundsInRoot()
        for (app in listOf(2, 5)) {
            assertEquals("card of $app carries the icon tag", 1, inCell(HomeTags.APP_ICON, app))
            val card = compose.onNode(hasTestTag(HomeTags.APP_ICON) and hasAnyAncestor(hasTestTag(HomeTags.cover(app))), useUnmergedTree = true).getBoundsInRoot()
            assertSame(poster.width, card.width)
            assertEquals(poster.height, card.height)
            assertEquals("the name is not drawn on the card", 0, inCell(HomeTags.APP_NAME, app))
            assertEquals("no Apps tile stand-in", 0, inCell(HomeTags.APP_ICON_STAND_IN, app))
        }
    }

    @Test
    fun appGameCellInAMixedGridIsACoverCardOfItsNeighboursSizeGraphite() = mixedGridIsCards(PlumThemeSettings.Graphite)

    @Test
    fun appGameCellInAMixedGridIsACoverCardOfItsNeighboursSizePaper() = mixedGridIsCards(PlumThemeSettings.Paper)

    @Test
    fun appGameWithNoIconIsThePosterWithItsTitle() {
        show(PlumThemeSettings.Default) { AppGamesBoard(PORTS_ENTRY, icons = remember { FakeAppIcons(missing = setOf(gamePackage(21))) }) }
        assertEquals("the poster", 1, inCell(HomeTags.POSTER_COVER, 3))
        assertTrue("with the title", compose.onAllNodes(hasContentDescription("Ship of Harkinian"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        assertEquals("no icon card", 0, inCell(HomeTags.APP_ICON, 3))
    }

    @Test
    fun gridOfAppGamesOnlyStaysAppsTiles() {
        show(PlumThemeSettings.Default) { AppGamesBoard(ANDROID_GAMES_ENTRY) }
        assertTrue("Apps tiles with names", count(HomeTags.APP_NAME) > 0)
        assertEquals("no poster", 0, count(HomeTags.POSTER_COVER))
        assertEquals("no cover card", 0, count(HomeTags.APP_GAME_CARD))
    }

    @Test
    fun favoritesMixingBoxArtPostersAndAppGamesAlignsCardsWithNeighbours() {
        show(PlumThemeSettings.Default) { AppGameMixedBoard() }
        val reference = bounds(0)
        for (app in FavoriteAppGameIndexes) {
            val cell = bounds(app)
            // Packed rows: a card is as wide as its system's shape (Android games and Ports are square) at its row's height. The
            // requirement changed: rows are justified, each at its own height, so only the row's other cells (below) share it; across
            // rows the heights stay near the first row's (a justified row's scale is 0.8 to 1.25 of the target).
            assertTrue("cell $app is about square (${cell.width} x ${cell.height})", cell.width.value in cell.height.value * 0.85f - 1f..cell.height.value * 1.2f + 1f)
            assertTrue("cell $app is about the first row's height (${cell.height} against ${reference.height})", cell.height.value in reference.height.value * 0.8f..reference.height.value * 1.25f)
            assertTrue("cell $app has the top of a file game's cell", (0..11).any { it !in FavoriteAppGameIndexes && bounds(it).top == cell.top && bounds(it).bottom == cell.bottom })
        }
        assertEquals(1, inCell(HomeTags.APP_ICON, 2))
        assertEquals("the app without an icon is a poster", 1, inCell(HomeTags.POSTER_COVER, FAVORITE_APP_WITHOUT_ICON))
    }

    private fun neutralInkIsReadable(settings: PlumThemeSettings) {
        lateinit var look: com.plumemu.coreui.theme.PosterLook
        show(settings) { look = appGameNeutralLook(PlumTheme.colors) }
        for (end in listOf(look.top, look.bottom, look.band)) {
            val ratio = (maxOf(look.ink.luminance(), end.luminance()) + 0.05f) / (minOf(look.ink.luminance(), end.luminance()) + 0.05f)
            assertTrue("neutral ink on $end is $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun neutralCardInkIsReadableInGraphite() = neutralInkIsReadable(PlumThemeSettings.Graphite)

    @Test
    fun neutralCardInkIsReadableInPaper() = neutralInkIsReadable(PlumThemeSettings.Paper)

    @Test
    fun cardColoursKeepTheInkReadable() {
        val averages = listOf(Color.White, Color.Black, Color.Yellow, Color(0xFFFFE600), Color.Red, Color.Green, Color.Cyan, Color(0xFF808080), Color(0xFF3355AA), Color(0xFFFFB74D))
        for (average in averages) {
            val look = appGameLook(average)
            for (end in listOf(look.top, look.bottom, look.band)) {
                val ratio = 1.05f / (end.luminance() + 0.05f)
                assertTrue("$average: white ink on $end is $ratio", ratio >= 4.5f)
            }
        }
    }
}
