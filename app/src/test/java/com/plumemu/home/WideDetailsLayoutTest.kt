package com.plumemu.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wide details column (`WideDetailsLayout`): the cover is 178 high unless the title block would not
 * then fit whole, and then it gives up exactly what the block needs, in the same pass; no text is ever
 * drawn over it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class WideDetailsLayoutTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().let { Rect(it.left.value, it.top.value, it.right.value, it.bottom.value) }

    /** The layout alone in [columnHeight], a title block of [titleHeight]: where the cover and the block land (dp). */
    private fun laidOut(
        columnHeight: Dp,
        titleHeight: Dp?,
    ): Pair<Rect, Rect?> {
        var title by mutableStateOf(titleHeight)
        compose.setContent {
            PlumTheme {
                Box(Modifier.height(columnHeight).width(PlumDimens.detailsColumnWidth)) {
                    WideDetailsLayout(
                        cover = { Box(Modifier.fillMaxSize().testTag(COVER)) },
                        titleBlock = { title?.let { Spacer(Modifier.width(PlumDimens.detailsColumnWidth).height(it).testTag(TITLE)) } },
                        rest = { Spacer(Modifier.width(PlumDimens.detailsColumnWidth).height(40.dp)) },
                    )
                }
            }
        }
        compose.waitForIdle()
        return bounds(COVER) to titleHeight?.let { bounds(TITLE) }
    }

    @Test
    fun theCoverIs178WhenTheTitleBlockFitsUnderIt() {
        val (cover, title) = laidOut(400.dp, 80.dp)
        assertEquals(PlumDimens.detailsCoverHeightWide.value, cover.height, 0.5f)
        assertEquals("the block a gap under the cover", cover.bottom + PlumDimens.detailsBlockGapWide.value, title!!.top, 0.5f)
    }

    @Test
    fun theCoverGivesUpExactlyWhatTheTitleBlockNeeds() {
        val gap = PlumDimens.detailsBlockGapWide.value
        val (cover, title) = laidOut(300.dp, 140.dp)
        assertEquals("300 − gap − 140", 300f - gap - 140f, cover.height, 0.5f)
        assertEquals("the block a gap under the cover", cover.bottom + gap, title!!.top, 0.5f)
        assertEquals("the block whole, ending at the column's bottom", 300f, title.bottom, 0.5f)
    }

    @Test
    fun withoutATitleBlockTheColumnIsTheCoverAlone() {
        val (cover, _) = laidOut(300.dp, null)
        assertEquals(PlumDimens.detailsCoverHeightWide.value, cover.height, 0.5f)
    }

    /** The real column at Medium: the cover 178 high, the title right under it (unchanged by the layout). */
    @Test
    fun atMediumTheCoverStays178() {
        compose.setContent { PlumTheme { CoverDetailsBoard(rememberSource { coverArtSource() }) } }
        compose.waitForIdle()
        val cover = bounds(HomeTags.DETAILS_COVER)
        assertEquals(PlumDimens.detailsCoverHeightWide.value, cover.height, 0.5f)
        assertEquals(cover.bottom + PlumDimens.detailsBlockGapWide.value, bounds(HomeTags.DETAILS_TITLE).top, 0.5f)
    }

    /**
     * Largest in Serif on 768 x 432, a title of two lines: the cover gives up what the title block needs,
     * the block (title and both fact lines) is whole inside the column, the picture shrinks into the cover's
     * box keeping its shape, and no text of the column is drawn over the cover.
     */
    @Test
    fun atLargestSerifTheCoverShrinksAndNoTextOverlapsIt() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = PlumFont.Serif)) {
                CoverDetailsBoard(rememberSource { coverArtSource() }, withSuperMetroidTitled(TWO_LINE_TITLE))
            }
        }
        compose.waitForIdle()
        val column = bounds(HomeTags.DETAILS)
        val cover = bounds(HomeTags.DETAILS_COVER)
        val title = bounds(HomeTags.DETAILS_TITLE)
        assertTrue("the cover gave up height: ${cover.height}", cover.height < PlumDimens.detailsCoverHeightWide.value - 0.5f)
        assertEquals("the title a gap under the cover", cover.bottom + PlumDimens.detailsBlockGapWide.value, title.top, 0.5f)
        val texts = textsUnder(compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode())
        val blockBottom = texts.filter { (text, box) -> box.top >= title.top - 0.5f && box.height > 0f && text.isNotEmpty() }.maxOf { it.second.bottom }
        assertTrue("the title and fact lines whole in the column (bottom $blockBottom of ${column.bottom})", blockBottom <= column.bottom + 0.5f)
        assertEquals("the cover gave up exactly what the block needs", column.bottom, cover.bottom + PlumDimens.detailsBlockGapWide.value + (blockBottom - title.top), 1f)
        val drawn = texts.filter { (_, box) -> box.height > 0f && box.width > 0f && box.overlaps(cover) }
        assertEquals("texts drawn over the cover", emptyList<Pair<String, Rect>>(), drawn)
        val picture = bounds(HomeTags.COVER_PICTURE)
        assertTrue("the picture inside the cover's box: $picture in $cover", picture.top >= cover.top - 0.5f && picture.bottom <= cover.bottom + 0.5f && picture.right <= cover.right + 0.5f)
    }

    /**
     * The reviewer's case: Largest in Serif, the focus moving from the systems to the games on the same game
     * (only the title block appears). In the very frame of the move the cover already gives way: the title
     * is never drawn under a 178 cover for a frame.
     */
    @Test
    fun theCoverGivesWayInTheFrameTheTitleAppears() {
        lateinit var state: StripState
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = PlumFont.Serif)) {
                HomeWithArt(rememberSource { coverArtSource() }, BoardSystemsFocused, withSuperMetroidTitled(TWO_LINE_TITLE), onState = { state = it })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(Direction.Right)) }
        // Frame by frame (the clock moves only here) until the frame the title is first drawn.
        val frames =
            (1..MAX_FRAMES).firstOrNull {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                compose.onAllNodes(hasTestTag(HomeTags.DETAILS_TITLE), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        assertTrue("the title appears within $MAX_FRAMES frames", frames != null)
        val cover = bounds(HomeTags.DETAILS_COVER)
        val title = bounds(HomeTags.DETAILS_TITLE)
        assertTrue("the cover gave up height: ${cover.height}", cover.height < PlumDimens.detailsCoverHeightWide.value - 0.5f)
        assertEquals("in the move's frame the title a gap under the cover", cover.bottom + PlumDimens.detailsBlockGapWide.value, title.top, 0.5f)
    }

    /** Every text under [node] with where it is drawn (dp, clipped by its parents). */
    private fun textsUnder(node: SemanticsNode): List<Pair<String, Rect>> {
        val density = compose.density.density
        val own =
            if (SemanticsProperties.Text in node.config) {
                val box = node.boundsInRoot
                listOf(node.config[SemanticsProperties.Text].joinToString() to Rect(box.left / density, box.top / density, box.right / density, box.bottom / density))
            } else {
                emptyList()
            }
        return own + node.children.flatMap { textsUnder(it) }
    }

    private companion object {
        const val COVER = "test_cover"
        const val TITLE = "test_title"

        /** Two lines at Largest in Serif in the 248 column. */
        const val TWO_LINE_TITLE = "Super Mario World 2: Yoshi's Island"

        const val MAX_FRAMES = 5
    }
}
