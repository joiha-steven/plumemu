package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.MediumText
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
 * The 4:3 screens (Retroid Pocket Nova, 640 dp wide: 640x480 full, 640x432 with Android's bar): with the systems open the games list ends inside the right margin with its year whole, with the
 * games focused the details peek in whole, cut by the screen's edge, at the details stop everything
 * sits inside the margins. Nothing with text may reach past the screen's right edge, art or not.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class HomeFourThreeBoundsBase(
    private val screenWidth: Dp,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val rightMargin = screenWidth - 24.dp

    private fun show(
        content: HomeContent,
        actions: List<Action>,
    ) {
        compose.setContent { PlumTheme(MediumText.Default) { HomeAt(content, actions) } }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun count(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Every text node that reaches past [limit], as "text right", read from the whole semantics tree. */
    private fun textsPast(limit: Dp): List<String> {
        val density = compose.density.density
        val found = mutableListOf<String>()

        fun walk(node: SemanticsNode) {
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            if (text != null && (node.positionInRoot.x + node.size.width) / density > limit.value + TOLERANCE) found += "'$text' right=${(node.positionInRoot.x + node.size.width) / density}"
            node.children.forEach { walk(it) }
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return found
    }

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, TOLERANCE)

    /** The text starts at the column's left edge, the cover slot ends at its right edge, the gap lies between them (peek and stop alike). */
    private fun assertTextLeftAndCoverRight() {
        val column = bounds(HomeTags.DETAILS)
        val cover = bounds(HomeTags.DETAILS_COVER)
        assertDp("the title starts at the column's left edge", column.left, bounds(HomeTags.DETAILS_TITLE).left)
        assertDp("the cover is the stop's width", PlumDimens.detailsStopCoverWidth, cover.right - cover.left)
        assertDp("the cover ends at the column's right edge", column.right, cover.right)
        assertTrue("the title ends a gap before the cover: title ${bounds(HomeTags.DETAILS_TITLE)} cover $cover", bounds(HomeTags.DETAILS_TITLE).right <= cover.left - PlumDimens.detailsStopCoverGap + TOLERANCE.dp)
    }

    @Test
    fun withArtAndTheSystemsOpenTheListEndsAtTheRightMargin() {
        show(FakeHomeContent.withArtwork(), BoardSystemsFocused)
        val row = bounds(HomeTags.game(0))
        assertDp("list starts after the systems column and the gap", 248.dp, row.left)
        assertDp("list ends at the right margin", rightMargin, row.right)
        assertEquals("no text past the screen", emptyList<String>(), textsPast(screenWidth))
        assertEquals("no text past the margin (the year is whole)", emptyList<String>(), textsPast(rightMargin))
    }

    @Test
    fun withArtAndTheGamesFocusedTheDetailsPeekInWholeAndCutByTheScreen() {
        show(FakeHomeContent.withArtwork(), BoardGamesFocused)
        assertTrue("the title starts on screen (${bounds(HomeTags.DETAILS_TITLE).left})", bounds(HomeTags.DETAILS_TITLE).left < screenWidth)
        assertEquals("the title is there", 1, count(HomeTags.DETAILS_TITLE))
        // The peek is the details stop's layout shifted, cut by the screen's edge: text on the left, the cover on the right.
        assertTextLeftAndCoverRight()
        assertDp("the column is the details stop's width", screenWidth - 104.dp, bounds(HomeTags.DETAILS).let { it.right - it.left })
    }

    @Test
    fun withArtTheDetailsStopSitsInsideTheMargins() {
        show(FakeHomeContent.withArtwork(), BoardGamesFocused + Action.Move(Direction.Right))
        assertDp("details 24 after the list's peek", 80.dp, bounds(HomeTags.DETAILS).left)
        assertDp("details end at the right margin", rightMargin, bounds(HomeTags.DETAILS).right)
        assertTextLeftAndCoverRight()
        assertEquals("no text past the margin", emptyList<String>(), textsPast(rightMargin))
    }

    @Test
    fun withoutArtTheSystemsOpenPreviewEndsAtTheRightMargin() {
        show(FakeHomeContent.board(), BoardSystemsFocused)
        val row = bounds(HomeTags.game(0))
        assertDp("preview starts after the systems column and the gap", 248.dp, row.left)
        assertDp("preview ends at the right margin", rightMargin, row.right)
        assertEquals("no text past the margin", emptyList<String>(), textsPast(rightMargin))
    }

    @Test
    fun recentWithoutArtShowsItsSystemCodeWholeWithTheSystemsOpen() {
        show(FakeHomeContent.mixedSystems(withBoxArt = false), emptyList())
        assertDp("preview ends at the right margin", rightMargin, bounds(HomeTags.game(0)).right)
        assertEquals("the system code is whole", emptyList<String>(), textsPast(rightMargin))
    }

    @Test
    fun recentWithArtShowsItsSystemCodeWholeWithTheSystemsOpen() {
        show(FakeHomeContent.mixedSystems(withBoxArt = true), emptyList())
        assertDp("preview ends at the right margin", rightMargin, bounds(HomeTags.game(0)).right)
        assertEquals("the system code is whole", emptyList<String>(), textsPast(rightMargin))
    }

    @Test
    fun withoutArtTheGamesListStaysInsideTheMargin() {
        show(FakeHomeContent.board(), BoardGamesFocused)
        assertDp("list starts after the peek", 72.dp, bounds(HomeTags.game(0)).left)
        assertDp("the list takes width − 96 once the games are focused", screenWidth - 96.dp, bounds(HomeTags.game(0)).let { it.right - it.left })
        assertTrue("list ends inside the right margin", bounds(HomeTags.game(0)).right <= rightMargin)
        assertEquals("no text past the margin", emptyList<String>(), textsPast(rightMargin))
    }

    private companion object {
        const val TOLERANCE = 0.6f
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeFourThree480BoundsTest : HomeFourThreeBoundsBase(640.dp)

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h432dp-land-xhdpi")
class HomeFourThree432BoundsTest : HomeFourThreeBoundsBase(640.dp)
