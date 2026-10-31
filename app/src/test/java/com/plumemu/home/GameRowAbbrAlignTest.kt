package com.plumemu.home

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import com.plumemu.coreinput.Action
import com.plumemu.platforms.PlatformLoadResult
import com.plumemu.platforms.PlatformLoader
import java.io.File
import com.plumemu.coreinput.Column as CoreColumn
import com.plumemu.coreui.components.GAME_ROW_DRAWN_TAG
import com.plumemu.coreui.components.GameRow
import com.plumemu.coreui.components.GameRowLook
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
import kotlin.math.abs

/**
 * A row of Recent, Favorites or Search shows the system's short name where a system list shows the
 * year: same slot, same baseline as the title, the same right edge as the year, and with a very
 * long title the ellipsis comes first (the short name is never cut), also at Largest text.
 */
abstract class GameRowAbbrAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun textIn(
        row: String,
        text: String,
    ): SemanticsNode = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(row)), useUnmergedTree = true).fetchSemanticsNode()

    /** The widest of every shipped abbreviation, measured with the real font at the text size now in force. */
    @androidx.compose.runtime.Composable
    private fun widestAbbreviation(): String {
        val measurer = rememberTextMeasurer()
        val style = PlumTheme.type.smallFact
        return shippedAbbreviations().maxBy { measurer.measure(it, style).size.width }
    }

    private fun shippedAbbreviations(): List<String> {
        val text = File("../platforms/src/main/assets/platforms.json").readText()
        return (PlatformLoader.load(text, "shipped") as PlatformLoadResult.Loaded).catalog.systems.map { it.abbr }
    }

    /** The real path: Recent through the home screen, the widest short name beside a very long title, at Largest. */
    protected fun theWidestAbbreviationFitsInRecentOnTheHomeScreen(label: String) {
        var widest = ""
        val longTitle = "The Legend of Zelda: A Link to the Past and Four Swords, Special Anniversary Collector's Edition " +
            "with the Complete Soundtrack, Strategy Guide and Behind the Scenes Documentary Disc"
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                widest = widestAbbreviation()
                val row = GameRowModel("r0", longTitle, "USA", systemAbbr = widest)
                val content = HomeContent.of(listOf(row), emptyList(), emptyList(), emptyList())
                HomeAt(content, listOf(Action.TapItem(CoreColumn.Systems, 0), Action.Enter))
            }
        }
        compose.waitForIdle()
        val tag = compose.onNode(hasText(widest), useUnmergedTree = true).fetchSemanticsNode()
        // The row's title; on a wide screen the text-only details column names the game too.
        val title = compose.onNode(hasText(longTitle) and hasAnyAncestor(hasTestTag(HomeTags.game(0))), useUnmergedTree = true).fetchSemanticsNode()
        val box = tag.boundsInRoot
        val list = compose.onNode(hasTestTag(HomeTags.game(0)), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val layout = layoutOf(tag)
        val problems = mutableListOf<String>()
        // The text clips (maxLines 1, no ellipsis) and is laid out in the whole width left, so `didOverflowWidth`
        // is always true: cut means it needs more width than it was given.
        val needs = layout.multiParagraph.intrinsics.maxIntrinsicWidth
        if (needs > layout.size.width || layout.didOverflowHeight || layout.lineCount != 1) problems += "$label: '$widest' is cut ($needs px wide, ${layout.size.width} given)"
        if (box.right > list.right || box.left < list.left) problems += "$label: '$widest' $box is outside the row $list"
        if (title.boundsInRoot.right > box.left) problems += "$label: title runs into '$widest'"
        if (title.boundsInRoot.width < list.width / 3) problems += "$label: the title got only ${title.boundsInRoot.width} px of ${list.width}"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun theAbbreviationSharesTheBaselineAndTheYearsRightEdge(label: String) {
        var widest = ""
        val longTitle = "The Legend of Zelda: A Link to the Past and Four Swords, Special Anniversary Collector's Edition " +
                "with the Complete Soundtrack, Strategy Guide and Behind the Scenes Documentary Disc"
        var size by mutableStateOf(TextSize.Medium)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                widest = widestAbbreviation()
                Column {
                    GameRow(title = "Year row", trailing = "1990", look = GameRowLook.Normal, modifier = Modifier.testTag("year"))
                    GameRow(title = "Short row", trailing = "SNES", look = GameRowLook.Normal, modifier = Modifier.testTag("short"))
                    GameRow(title = longTitle, trailing = widest, look = GameRowLook.Focused, modifier = Modifier.testTag("long"), isFavorite = true)
                }
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Medium, TextSize.Largest)) {
            size = each
            compose.waitForIdle()
            val at = "$label ${each.name}"
            val yearBox = textIn("year", "1990").boundsInRoot
            val shortAbbr = textIn("short", "SNES")
            val shortTitle = textIn("short", "Short row")
            if (abs(shortAbbr.boundsInRoot.right - yearBox.right) > 1f) problems += "$at: SNES right edge ${shortAbbr.boundsInRoot.right}, year's ${yearBox.right}"
            val shortBaseline = shortAbbr.boundsInRoot.top + layoutOf(shortAbbr).getLineBaseline(0)
            val titleBaseline = shortTitle.boundsInRoot.top + layoutOf(shortTitle).getLineBaseline(0)
            if (abs(shortBaseline - titleBaseline) > 1f) problems += "$at: abbreviation baseline $shortBaseline, title baseline $titleBaseline"

            val row = compose.onNode(hasTestTag(GAME_ROW_DRAWN_TAG) and hasAnyAncestor(hasTestTag("long")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val abbr = textIn("long", widest)
            val title = textIn("long", longTitle)
            val abbrBox = abbr.boundsInRoot
            val titleBox = title.boundsInRoot
            if (abs(abbrBox.right - yearBox.right) > 1f) problems += "$at: long row's abbreviation right edge ${abbrBox.right}, year's ${yearBox.right}"
            if (abbrBox.right > row.right || abbrBox.left < row.left) problems += "$at: abbreviation $abbrBox is outside the row $row"
            if (layoutOf(abbr).isLineEllipsized(0) || layoutOf(abbr).didOverflowHeight || layoutOf(abbr).lineCount != 1) problems += "$at: abbreviation is cut"
            if (!layoutOf(title).isLineEllipsized(0)) problems += "$at: the long title was expected to be ellipsized"
            if (titleBox.right > abbrBox.left) problems += "$at: title $titleBox runs into the abbreviation $abbrBox"
            if (titleBox.width < row.width / 3) problems += "$at: the title got only ${titleBox.width} of ${row.width} px"
            val longBaseline = abbrBox.top + layoutOf(abbr).getLineBaseline(0)
            val longTitleBaseline = titleBox.top + layoutOf(title).getLineBaseline(0)
            if (abs(longBaseline - longTitleBaseline) > 1f) problems += "$at: long row baselines $longBaseline vs $longTitleBaseline"
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameRowAbbrAlign768Test : GameRowAbbrAlignBase() {
    @Test fun theAbbreviationSharesTheBaselineAndTheYearsRightEdge() = theAbbreviationSharesTheBaselineAndTheYearsRightEdge("768x432")

    @Test fun theWidestAbbreviationFitsInRecentOnTheHomeScreen() = theWidestAbbreviationFitsInRecentOnTheHomeScreen("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GameRowAbbrAlign576Test : GameRowAbbrAlignBase() {
    @Test fun theAbbreviationSharesTheBaselineAndTheYearsRightEdge() = theAbbreviationSharesTheBaselineAndTheYearsRightEdge("576x432")

    @Test fun theWidestAbbreviationFitsInRecentOnTheHomeScreen() = theWidestAbbreviationFitsInRecentOnTheHomeScreen("576x432")
}
