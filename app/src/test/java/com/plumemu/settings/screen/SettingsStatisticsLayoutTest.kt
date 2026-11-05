package com.plumemu.settings.screen

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.test.core.app.ApplicationProvider
import com.plumemu.R
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SettingsThreeDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SettingsColumns
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Statistics' page by bounds, at the two reference widths: the tiles (four in a row wide, two by two narrow, equal
 * widths and gaps), the page's 24 dp margins, the lists (side by side wide, stacked narrow), and the empty, failed and preview forms.
 */
abstract class SettingsStatisticsLayoutBase(
    private val widthDp: Int,
    private val isWide: Boolean,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val margin = PlumDimens.stripMarginSide.value
    private val tileGap = PlumDimens.settingsPanelGap.value

    /** Three columns (wide, up to Large text): the page has the options' and right column's room and lays out as on a narrow screen. */
    private val threeColumns = isWide && SettingsColumns.fits(PlumThemeSettings.Graphite.textSize ?: TextSize.Default, PlumThemeSettings.Graphite.font)
    private val fourAcross = isWide && !threeColumns
    private val pageLeft = if (threeColumns) (SettingsThreeDimens.sectionsWidth + SettingsThreeDimens.optionsGap).value else margin

    private fun show(
        focus: SettingsFocus = StatisticsShots.focusAt(0),
        content: SettingsContent = StatisticsShots.contentOf(StatisticsShots.played),
    ) {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { StatisticsShots.WithCovers { SettingsAt(focus, content = content) } } }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun missing(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).assertDoesNotExist()

    private fun assertNear(
        what: String,
        expected: Float,
        actual: Float,
    ) = assertEquals(what, expected, actual, 0.5f)

    private val allTiles = listOf(StatisticTile.Total, StatisticTile.Week, StatisticTile.Played, StatisticTile.BoxArt)

    @Test
    fun tilesFillTheRowsWithEqualWidthsAndGaps() {
        show()
        val page = bounds(StatisticsTags.PAGE)
        val tiles = allTiles.map { bounds(StatisticsTags.tile(it)) }
        val widths = tiles.map { it.right.value - it.left.value }
        widths.forEach { assertNear("tile widths $widths", widths[0], it) }
        if (fourAcross) {
            tiles.forEach { assertNear("one row", tiles[0].top.value, it.top.value) }
            assertNear("first tile at the page's left", page.left.value, tiles.first().left.value)
            assertNear("last tile at the page's right", page.right.value, tiles.last().right.value)
            tiles.zipWithNext { a, b -> assertNear("gap between tiles", tileGap, b.left.value - a.right.value) }
        } else {
            assertNear("row one", tiles[0].top.value, tiles[1].top.value)
            assertNear("row two", tiles[2].top.value, tiles[3].top.value)
            assertNear("row gap", tileGap, tiles[2].top.value - maxOf(tiles[0].bottom.value, tiles[1].bottom.value))
            assertNear("column one", tiles[0].left.value, tiles[2].left.value)
            assertNear("column two", tiles[1].left.value, tiles[3].left.value)
            assertNear("gap between tiles", tileGap, tiles[1].left.value - tiles[0].right.value)
            assertNear("tiles reach the page's right", page.right.value, tiles[1].right.value)
            assertNear("tiles start at the page's left", page.left.value, tiles[0].left.value)
        }
        // A row's tiles are as tall as each other.
        for (row in tiles.chunked(if (fourAcross) 4 else 2)) {
            val heights = row.map { it.bottom.value - it.top.value }
            heights.forEach { assertNear("row heights $heights", heights[0], it) }
        }
    }

    @Test
    fun thePageKeepsTheMarginsAndNothingTouchesTheScreenEdge() {
        show()
        val page = bounds(StatisticsTags.PAGE)
        assertNear("page left (the margin, or the options column in three columns)", pageLeft, page.left.value)
        assertNear("right margin", widthDp - margin, page.right.value)
        val tags = listOf(StatisticsTags.TILES, StatisticsTags.MOST_PLAYED, StatisticsTags.SYSTEMS, StatisticsTags.FOOTER, StatisticsTags.game(0), StatisticsTags.system(0), StatisticsTags.gameTitle(0))
        for (tag in tags) {
            val box = bounds(tag)
            assertTrue("$tag starts at ${box.left}, left of the page $pageLeft", box.left.value >= pageLeft - 0.5f)
            assertTrue("$tag ends at ${box.right}, right of ${widthDp - margin}", box.right.value <= widthDp - margin + 0.5f)
        }
    }

    @Test
    fun theListsSitSideBySideWideAndStackedNarrow() {
        show()
        val most = bounds(StatisticsTags.MOST_PLAYED)
        val systems = bounds(StatisticsTags.SYSTEMS)
        val page = bounds(StatisticsTags.PAGE)
        if (fourAcross) {
            assertNear("same top", most.top.value, systems.top.value)
            assertNear("column gap", PlumDimens.columnGap.value, systems.left.value - most.right.value)
            assertNear("equal widths", most.right.value - most.left.value, systems.right.value - systems.left.value)
            assertNear("systems reach the right margin", page.right.value, systems.right.value)
        } else {
            assertNear("same left", most.left.value, systems.left.value)
            assertNear("same right", most.right.value, systems.right.value)
            assertNear("gap between the lists", tileGap, systems.top.value - most.bottom.value)
        }
        // Both lists start below the tiles.
        assertNear("lists start a gap under the tiles", tileGap, most.top.value - bounds(StatisticsTags.TILES).bottom.value)
        assertEquals("a game row per listed game", 5, (0 until 5).count { bounds(StatisticsTags.game(it)).bottom.value > 0f })
    }

    @Test
    fun nothingPlayedIsTheInviteTheBoxArtTileAndTheFooter() {
        show(content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed))
        compose.onNode(hasText(context.getString(R.string.settings_statistics_play_invite)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.INVITE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.tile(StatisticTile.BoxArt), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.FOOTER, useUnmergedTree = true).assertIsDisplayed()
        missing(StatisticsTags.MOST_PLAYED)
        missing(StatisticsTags.SYSTEMS)
        missing(StatisticsTags.tile(StatisticTile.Total))
        // The box art tile is one tile wide, as in the full page; line, tile and footer stand together in the page's middle.
        val tile = bounds(StatisticsTags.tile(StatisticTile.BoxArt))
        val page = bounds(StatisticsTags.PAGE)
        val invite = bounds(StatisticsTags.INVITE)
        val footer = bounds(StatisticsTags.FOOTER)
        assertTrue("one tile, not the page's width", tile.right.value < page.right.value - 1f)
        val middle = (page.left.value + page.right.value) / 2
        assertNear("tile centred across", middle, (tile.left.value + tile.right.value) / 2)
        assertNear("line centred across", middle, (invite.left.value + invite.right.value) / 2)
        assertNear("group centred down", invite.top.value - page.top.value, page.bottom.value - footer.bottom.value)
        assertNear("line to tile", tileGap, tile.top.value - invite.bottom.value)
        val exact = bounds(StatisticsTags.EXACT_PLAY_TIME)
        assertNear("tile to the Exact play time switch", tileGap, exact.top.value - tile.bottom.value)
        assertNear("its explanation to footer", tileGap, footer.top.value - exact.bottom.value)
    }

    @Test
    fun withTheSectionsFocusedAndNothingPlayedTheBlockIsCentredInThePageOnTheTilesGrid() {
        show(focus = SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Statistics), content = StatisticsShots.contentOf(StatisticsShots.nothingPlayed))
        compose.onNode(hasText(context.getString(R.string.settings_statistics_play_invite)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.settings_statistics_no_plays_title)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.tile(StatisticTile.BoxArt), useUnmergedTree = true).assertIsDisplayed()
        missing(StatisticsTags.FOOTER)
        val page = bounds(StatisticsTags.PAGE)
        val tile = bounds(StatisticsTags.tile(StatisticTile.BoxArt))
        val invite = bounds(StatisticsTags.INVITE)
        // The card is one cell of the two-to-a-row grid the played preview uses (its first tile's width).
        val cell = (page.right.value - page.left.value - tileGap) / 2
        assertNear("card one grid cell wide", cell, tile.right.value - tile.left.value)
        // The block (title to card) is centred across and down the page; a gap at least clear above it.
        // Narrow screens clip the page at the right margin: the block centres in what shows.
        val shown = if (threeColumns) page.right.value - page.left.value else minOf(page.right.value - page.left.value, widthDp - 2 * margin - PlumDimens.settingsSectionsWidth.value - PlumDimens.columnGap.value)
        val middle = page.left.value + shown / 2
        assertNear("card centred across", middle, (tile.left.value + tile.right.value) / 2)
        assertNear("line centred across", middle, (invite.left.value + invite.right.value) / 2)
        val blockTop = invite.top.value
        val blockBottom = tile.bottom.value
        assertNear("block centre is the page centre", (page.top.value + page.bottom.value) / 2, (blockTop + blockBottom) / 2)
        assertTrue("top gap ${blockTop - page.top.value} is under the standard gap $tileGap", blockTop - page.top.value >= tileGap - 0.5f)
        // The board's pair: the title bold over a muted body, centred, the body a gap under the title.
        val title = bounds(StatisticsTags.INVITE_TITLE)
        val body = bounds(StatisticsTags.INVITE_BODY)
        assertNear("title centred", middle, (title.left.value + title.right.value) / 2)
        assertNear("body centred", middle, (body.left.value + body.right.value) / 2)
        assertNear("title to body", com.plumemu.coreui.theme.EmptyStateDimens.gap.value, body.top.value - title.bottom.value)
        assertTrue("title is a larger line than the body", title.bottom.value - title.top.value > body.bottom.value - body.top.value - 0.5f)
        assertNear("line to card", tileGap, tile.top.value - invite.bottom.value)
        assertTrue("the card is whole inside the page", tile.bottom.value <= page.bottom.value + 0.5f)
    }

    @Test
    fun anEmptyLibraryIsOneLineAndTheFooter() {
        show(content = StatisticsShots.contentOf(PlayStatistics()))
        compose.onNode(hasText(context.getString(R.string.settings_statistics_no_games_title)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.FOOTER, useUnmergedTree = true).assertIsDisplayed()
        missing(StatisticsTags.tile(StatisticTile.BoxArt))
        missing(StatisticsTags.MOST_PLAYED)
    }

    @Test
    fun aFailedReadIsTheLineItsDetailAndTheFooter() {
        show(content = SettingsContent(statistics = StatisticsState.Failed))
        compose.onNode(hasText(context.getString(R.string.settings_statistics_unreadable)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasText(context.getString(R.string.settings_statistics_unreadable_detail)), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(StatisticsTags.FOOTER, useUnmergedTree = true).assertIsDisplayed()
        missing(StatisticsTags.TILES)
        val line = bounds(StatisticsTags.UNREADABLE)
        assertTrue("the line is inside the margin", line.left.value >= margin)
    }

    @Test
    fun withTheSectionsFocusedOnlyTheTilesShowTwoToARow() {
        show(focus = SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Statistics))
        compose.onNodeWithTag(StatisticsTags.TILES, useUnmergedTree = true).assertIsDisplayed()
        missing(StatisticsTags.MOST_PLAYED)
        missing(StatisticsTags.SYSTEMS)
        missing(StatisticsTags.FOOTER)
        val tiles = allTiles.map { bounds(StatisticsTags.tile(it)) }
        assertNear("two to a row", tiles[0].top.value, tiles[1].top.value)
        assertNear("second row", tiles[2].top.value, tiles[3].top.value)
        assertTrue("the third tile is under the first", tiles[2].top.value > tiles[0].bottom.value)
        assertNear("equal widths", tiles[0].right.value - tiles[0].left.value, tiles[1].right.value - tiles[1].left.value)
        assertNear("gap between tiles", tileGap, tiles[1].left.value - tiles[0].right.value)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsStatisticsLayout768Test : SettingsStatisticsLayoutBase(768, isWide = true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsStatisticsLayout576Test : SettingsStatisticsLayoutBase(576, isWide = false)

/** The Pocket 6's 1920x1080 at 2x. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w960dp-h540dp-land-xhdpi")
class SettingsStatisticsLayout1920Test : SettingsStatisticsLayoutBase(960, isWide = true)
