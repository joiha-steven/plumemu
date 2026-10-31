package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.FAVORITE_STAR
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The favorite star measured (docs/design/rules.md › Favorite mark: 8 after the title, a long
 * title cut before it, never the star) and the games hint bar with Y and X fitting at 576×432 in
 * all three fonts (never overlapping, never past the right margin).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeFavoriteBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** Each star on screen, top to bottom. */
    private fun stars(): List<DpRect> =
        compose.onAllNodesWithText(FAVORITE_STAR, useUnmergedTree = true).fetchSemanticsNodes().map { node ->
            val r = node.boundsInRoot
            val density = node.layoutInfo.density.density
            DpRect((r.left / density).dp, (r.top / density).dp, (r.right / density).dp, (r.bottom / density).dp)
        }.sortedBy { it.top }

    private fun textBounds(text: String): DpRect =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first().let { node ->
            val r = node.boundsInRoot
            val density = node.layoutInfo.density.density
            DpRect((r.left / density).dp, (r.top / density).dp, (r.right / density).dp, (r.bottom / density).dp)
        }

    private var font by mutableStateOf(PlumFont.Sans)

    private fun showGames() {
        compose.setContent {
            PlumTheme(MediumText.Graphite.copy(font = font)) {
                HomeAt(FakeHomeContent.withFavorites(longTitle = true), BoardGamesFocused)
            }
        }
        compose.waitForIdle()
    }

    private fun assertStarLayout(width: Float) {
        showGames()
        val list = bounds(HomeTags.GAMES)
        val longRow = bounds(HomeTags.game(0))
        val star = stars().first()
        val title = textBounds(FakeHomeContent.withFavorites(longTitle = true).entries[FakeHomeContent.BOARD_SNES_INDEX].rows[0].title)
        assertTrue("the star is drawn (${star.right - star.left})", star.right - star.left > 4.dp)
        assertEquals("the long title stops 8 before its star", (title.right + 8.dp).value, star.left.value, TOLERANCE)
        val region = textBounds("USA")
        assertTrue("the star stays 12 clear of the region (${star.right} vs ${region.left})", star.right <= region.left - 12.dp + TOLERANCE.dp)
        assertTrue("the star is inside the row", star.right <= longRow.right && star.left >= longRow.left)
        assertTrue("and inside the list at $width", star.right <= list.right)
        // A short favorite (EarthBound, row 2): the star sits 8 after the end of the title.
        val short = textBounds("EarthBound")
        val shortStar = stars().first { it.top >= short.top - 1.dp && it.bottom <= short.bottom + 1.dp }
        assertEquals("8 after a short title", (short.right + 8.dp).value, shortStar.left.value, TOLERANCE)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideStarAfterTheTitleAndALongTitleCutBeforeIt() = assertStarLayout(768f)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowStarAfterTheTitleAndALongTitleCutBeforeIt() = assertStarLayout(576f)

    /** Whether the hint for [action] is on screen: composed and placed (a hint that gave way is not). */
    private fun isShown(action: Action): Boolean =
        compose.onAllNodesWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNodes().any { it.layoutInfo.isPlaced }

    /** Every text in the hint bar is drawn whole (no width overflow). */
    private fun assertNoHintTextCut(what: String) {
        assertNoTextCut(compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).fetchSemanticsNode(), what)
    }

    private fun shownHints(order: List<Action>): List<DpRect> = order.filter(::isShown).map { bounds(HomeTags.hint(it)) }

    @Test
    @Config(qualifiers = NARROW)
    fun theGamesHintsFitAt576InEveryFont() {
        val order = listOf(Action.Play, Action.Back, Action.ToggleFavorite, Action.Search, Action.Menu, Action.JumpLetterNext)
        val report = StringBuilder()
        showGames()
        for (each in PlumFont.entries) {
            font = each
            compose.waitForIdle()
            for (required in listOf(Action.Play, Action.ToggleFavorite, Action.Search, Action.Menu)) {
                assertTrue("$each: ${required::class.simpleName} is shown", isShown(required))
            }
            val hints = shownHints(order)
            report.append("$each: ").append(hints.joinToString { "%.1f–%.1f".format(it.left.value, it.right.value) })
            report.append(if (isShown(Action.JumpLetterNext)) "" else "  (L2 R2 gave way)").append('\n')
            assertTrue("$each: starts at the margin", hints.first().left >= 24.dp - TOLERANCE.dp)
            hints.zipWithNext().forEach { (a, b) -> assertTrue("$each: no overlap ($a, $b)", b.left >= a.right) }
            assertTrue("$each: ends inside the right margin (${hints.last().right})", hints.last().right <= 552.dp + TOLERANCE.dp)
            assertNoHintTextCut("$each")
        }
        println("Hint bar at 576 (games focused on a favorite: Y Unfavorite):\n$report")
    }

    @Test
    @Config(qualifiers = WIDE)
    fun at768EveryGamesHintShows() {
        showGames()
        val order = listOf(Action.Play, Action.Back, Action.ToggleFavorite, Action.Search, Action.Menu, Action.JumpLetterNext)
        order.forEach { assertTrue("${it::class.simpleName} is shown", isShown(it)) }
        val hints = shownHints(order)
        hints.zipWithNext().forEach { (a, b) -> assertEquals("20 between hints", (a.right + 20.dp).value, b.left.value, TOLERANCE) }
        assertTrue(hints.last().right <= 744.dp + TOLERANCE.dp)
        assertNoHintTextCut("768")
        println("Hint bar at 768: " + hints.joinToString { "%.1f–%.1f".format(it.left.value, it.right.value) })
    }

    private fun assertSearchResultsInTheGamesColumn(width: Float) {
        val content = FakeHomeContent.withFavorites()
        val search =
            SearchState().apply {
                open()
                onQueryChange("super")
                showResults("super", SearchIndex.of(content).find("super"))
                toResults()
            }
        compose.setContent { PlumTheme(MediumText.Default) { HomeAt(content, BoardGamesFocused, search = search) } }
        compose.waitForIdle()
        val row = bounds(SearchTags.result(0))
        assertEquals("results start where the games list does (peek 48 + gap 24)", 72f, row.left.value, TOLERANCE)
        assertEquals("and end at the right margin", width - 24f, row.right.value, TOLERANCE)
        assertEquals("tap areas 40 high (38 drawn and the gap)", 40f, (row.bottom - row.top).value, TOLERANCE)
        val second = bounds(SearchTags.result(1))
        assertEquals("40 apart", 40f, (second.top - row.top).value, TOLERANCE)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSearchResults() = assertSearchResultsInTheGamesColumn(768f)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSearchResults() = assertSearchResultsInTheGamesColumn(576f)

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.5f
    }
}
