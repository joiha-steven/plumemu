package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.SecondScreenText
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.GlassDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Thor's bottom card (538x468 dp, 369 dpi): even gaps between Recent's boxes, Favorites' full rows
 * reaching the right margin, the game tiles' last lines on one baseline with "this wk" inside the text edge, and no chart for a
 * game never played.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class SecondScreenTightCardsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        compose.setContent { PlumTheme(SecondScreenText.Default) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(content) } } }
    }

    private fun show(each: SecondScreenContent) {
        compose.runOnIdle { content = SecondScreenContent.Calm }
        compose.waitForIdle()
        compose.runOnIdle { content = each }
        compose.waitForIdle()
    }

    private fun dp(px: Float) = px / RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun px(dp: Dp) = dp.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun boundsOf(tag: String): List<Rect> = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun recentBoxesAreEvenlySpacedWhateverTheirCaptionsSay() {
        show(realRecent())
        val covers = boundsOf(EntryTags.COVER)
        assertTrue("covers: ${covers.size}", covers.size >= 4)
        val gaps = covers.zipWithNext { a, b -> dp(b.left - a.right) }
        for (gap in gaps) assertEquals("gaps $gaps", gaps.first(), gap, 0.5f)
        // The tagged box sits inside its collar: the gap between boxes is the row's gap plus a collar side either way.
        assertEquals("gaps $gaps", (EntryCardDimens.recentCoverGap + CollarSize.Cover.sidePadding * 2).value, gaps.first(), 0.5f)
    }

    private fun layoutsIn(node: SemanticsNode): List<TextLayoutResult> {
        val own = mutableListOf<TextLayoutResult>()
        if (SemanticsActions.GetTextLayoutResult in node.config) node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(own)
        return own + node.children.flatMap(::layoutsIn)
    }

    private fun ellipsized(tag: String): List<Boolean> =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().filter { it.boundsInRoot.width > 0f }.sortedBy { it.boundsInRoot.left }.map { node ->
            val layouts = layoutsIn(node)
            assertTrue("$tag: no text layout under the node", layouts.isNotEmpty())
            layouts.any { r -> (0 until r.lineCount).any { r.isLineEllipsized(it) } }
        }

    @Test
    fun recentCaptionsStayAGapApartAndAreCutOnlyWhereTheyWouldMeet() {
        show(realRecent())
        val whens = boundsOf(EntryTags.WHEN)
        for ((a, b) in whens.zipWithNext()) assertTrue("captions closer than the gap: $a $b", b.left - a.right >= px(EntryCardDimens.recentCoverGap) - 0.5f)
    }

    @Test
    fun theLastCaptionShownMayUseTheRoomToTheCardsEdge() {
        // "3 hours ago" is wider than a PSP box; nothing follows it, so it is whole.
        show(realRecent().let { it.copy(figures = it.figures!!.copy(games = it.figures!!.games.take(2))) })
        assertEquals(listOf(false, false), ellipsized(EntryTags.WHEN))
    }

    @Test
    fun theSystemCardsCaptionsAreWhole() {
        show(BOARD_SYSTEM)
        val cut = ellipsized(EntryTags.WHEN)
        assertTrue("captions placed: ${cut.size}", cut.size >= 4)
        assertEquals(List(cut.size) { false }, cut)
    }

    /** What the greedy packing shows (the same covers at the usual height): the least a justified card may show. */
    private fun greedyShown(card: FavoritesEntry): Int {
        val density = Density(RuntimeEnvironment.getApplication().resources.displayMetrics.density)
        val width = bounds(EntryTags.STATUS).width.toInt()
        val widths = card.games.map { with(density) { entryCoverWidth(it, card.systems, EntryCardDimens.favoritesCoverHeight, DEFAULT_COVER_ASPECT).roundToPx() } }
        return packRows(widths, with(density) { EntryCardDimens.favoritesCoverGap.roundToPx() }, width, EntryCardDimens.FAVORITES_ROWS).sumOf { it.size }
    }

    @Test
    fun favoritesJustifyFromTheTopAndNeverShowFewerCoversThanTheGreedyPacking() {
        for (card in listOf(BOARD_FAVORITES, FAVORITES_MANY)) {
            show(card)
            val margin = bounds(EntryTags.STATUS).right
            val rows = (0 until EntryCardDimens.FAVORITES_ROWS).filter { count(EntryTags.favoritesRow(it)) == 1 }
            assertTrue("${card.gameCount}: shown ${count(EntryTags.COVER)} < greedy ${greedyShown(card)}", count(EntryTags.COVER) >= greedyShown(card))
            // Rows are justified from the top while the height allows: the first one always.
            assertEquals("${card.gameCount}: first row", margin, bounds(EntryTags.favoritesRow(rows.first())).right, px(Dp(0.5f)))
            val reaching = rows.map { kotlin.math.abs(bounds(EntryTags.favoritesRow(it)).right - margin) <= px(Dp(0.5f)) }
            assertEquals("${card.gameCount}: reaching rows are the top ones: $reaching", reaching.sortedDescending(), reaching)
            assertTrue("fits the card", bounds(EntryTags.STATUS).bottom <= compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom)
        }
    }

    @Test
    fun theTilesLastLinesShareOneBaseline() {
        show(BOARD_GAME)
        assertEquals(bounds(GameCardTags.LATEST).bottom, bounds(GameCardTags.ABOUT).bottom, 0.5f)
        assertEquals(bounds(GameCardTags.ACHIEVEMENTS).bottom, bounds(GameCardTags.PLAY).bottom, 0.5f)
    }

    @Test
    fun thisWeekEndsInsideTheTileTextEdge() {
        show(BOARD_GAME)
        val edge = bounds(GameCardTags.PLAY).right - px(GlassDimens.edge) - px(SecondScreenGameCardDimens.tilePadX)
        val label = bounds(GameCardTags.THIS_WEEK)
        assertTrue("this wk ${label.right} past the edge $edge", label.right <= edge + 0.5f)
        assertTrue("this wk is not right-aligned to the bars' end: ${label.right} / $edge", label.right >= edge - px(Dp(1f)))
    }
}
