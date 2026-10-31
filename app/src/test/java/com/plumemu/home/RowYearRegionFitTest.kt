package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
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
 * A single system's list with box art shows "1995 · USA". A 21-character title
 * ("Donkey Kong Country 2") must stay whole beside it at the Largest text size, in the 400 dp list
 * beside the details and in the full-width list used while the second screen's window is up; the
 * right-hand text is never cut. At the narrow details stop the text stays the year alone.
 */
abstract class RowYearRegionFitBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val title = "Donkey Kong Country 2"
    private var region = "USA"
    private val game get() = GameRowModel("dkc2", title, region, details = GameDetails(1995, "Rare", "Platform", "1-2", ""))
    private val content get() = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", listOf(game))), hasBoxArt = true)
    private val toList = listOf(Action.TapItem(Column.Systems, FIRST_SYSTEM_INDEX), Action.Enter)

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun show(
        actions: List<Action>,
        windowUp: Boolean,
        size: TextSize = TextSize.Largest,
    ) = compose.setContent {
        PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
            HomeAt(content, toList + actions, detailsOnSecondScreen = { windowUp })
        }
    }

    /** The title is whole (its natural width fits what it was given) and "1995 · USA" is whole and clear of it. */
    protected fun titleStaysWhole(
        label: String,
        windowUp: Boolean,
        text: String,
        size: TextSize = TextSize.Largest,
        wholeTitle: Boolean = true,
        regionCode: String = "USA",
    ) {
        region = regionCode
        show(emptyList(), windowUp, size)
        compose.waitForIdle()
        val inRow = hasAnyAncestor(hasTestTag(HomeTags.game(0)))
        val trailing = compose.onNode(hasText(text) and inRow, useUnmergedTree = true).fetchSemanticsNode()
        val name = compose.onNode(hasText(title) and inRow, useUnmergedTree = true).fetchSemanticsNode()
        val tl = layoutOf(trailing)
        val nl = layoutOf(name)
        val spare = name.boundsInRoot.width - nl.multiParagraph.intrinsics.maxIntrinsicWidth
        println("FIT $label windowUp=$windowUp [$text]: title needs ${nl.multiParagraph.intrinsics.maxIntrinsicWidth} px of ${name.boundsInRoot.width} (spare $spare px); gap to the text ${trailing.boundsInRoot.left - name.boundsInRoot.right} px; trailing needs ${tl.multiParagraph.intrinsics.maxIntrinsicWidth} of ${trailing.boundsInRoot.width} px")
        assertTrue("$label: the title is cut ('…')", !wholeTitle || spare >= 0f)
        assertTrue("$label: '$text' is cut", tl.multiParagraph.intrinsics.maxIntrinsicWidth <= trailing.boundsInRoot.width + 1f)
        assertTrue("$label: title runs into the text", name.boundsInRoot.right <= trailing.boundsInRoot.left)
    }

    /** Narrow, no window: beside the details (games stop) or at the details stop, the list writes the year alone, never year · region. */
    protected fun aNarrowListKeepsTheYearAlone(
        label: String,
        stop: List<Action>,
    ) {
        show(stop, windowUp = false)
        compose.waitForIdle()
        assertEquals("$label: no year · region", 0, compose.onAllNodesWithTextCount("1995 · USA"))
        assertTrue("$label: the year alone", compose.onAllNodesWithTextCount("1995") > 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class RowYearRegionFit576Test : RowYearRegionFitBase() {
    @Test fun besideTheDetails() = titleStaysWhole("576x432 Largest", windowUp = false, text = "1995")

    @Test fun inTheFullWidthListWhileTheSecondScreenIsUp() = titleStaysWhole("576x432 Largest", windowUp = true, text = "1995 · USA")

    @Test fun aNarrowListBesideTheDetailsKeepsTheYearAlone() = aNarrowListKeepsTheYearAlone("576x432 games stop", emptyList())

    @Test fun aNarrowListAtTheDetailsStopKeepsTheYearAlone() = aNarrowListKeepsTheYearAlone("576x432 details stop", listOf(Action.Move(Direction.Right)))
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class RowYearRegionFit768Test : RowYearRegionFitBase() {
    @Test fun besideTheDetailsAtMedium() = titleStaysWhole("768x432 Medium", windowUp = false, text = "1995 · USA", size = TextSize.Medium)

    @Test fun besideTheDetailsAtHuge() = titleStaysWhole("768x432 Huge", windowUp = false, text = "1995 · USA", size = TextSize.Huge)

    @Test fun besideTheDetailsAtHugeWithAWideRegion() = titleStaysWhole("768x432 Huge WLD", windowUp = false, text = "1995 · WLD", size = TextSize.Huge, regionCode = "WLD")

    @Test fun besideTheDetailsAtLargerWithAWideRegion() = titleStaysWhole("768x432 Larger WLD", windowUp = false, text = "1995 · WLD", size = TextSize.Larger, regionCode = "WLD")

    // At Largest the systems column grows, the 400 dp list shrinks and year · region would cut the title: the year alone.
    @Test fun besideTheDetailsAtLargestKeepsTheYear() = titleStaysWhole("768x432 Largest", windowUp = false, text = "1995")
}
