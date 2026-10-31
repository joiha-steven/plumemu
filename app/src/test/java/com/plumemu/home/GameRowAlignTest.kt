package com.plumemu.home

import android.content.Context
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
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
 * The games list's row at Default and Largest text (rows drawn 36 × text scale but at least 38, titles 16 sp):
 * the title is centred in its row, the year sits on the title's baseline, nothing is cut, and the
 * focused row's fill is the whole row, `gameRowHeight` high.
 */
abstract class GameRowAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    protected fun theTitleIsCentredAndTheYearSharesItsBaseline(label: String) {
        var size by mutableStateOf(TextSize.Medium)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                // No spacing: each GameRow carries its own 2 dp gap, as in the real list.
                Column {
                    for ((index, look) in listOf(GameRowLook.Normal, GameRowLook.Focused, GameRowLook.MenuOpen, GameRowLook.Normal).withIndex()) {
                        // The last row is a favorite: the star must not move the baseline.
                        GameRow(title = "Title $index", trailing = "199$index", look = look, modifier = Modifier.testTag("row$index"), isFavorite = index == 3)
                    }
                }
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Medium, TextSize.Largest)) {
            size = each
            compose.waitForIdle()
            val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
            val onePx = 1f
            // The drawn row's height, from the decision, not from the code's formula.
            val expectedHeight = mapOf(TextSize.Medium to 38f, TextSize.Largest to 52.2f).getValue(each) * density
            for (index in 0..3) {
                val at = "$label ${each.name} row $index"
                val row = compose.onNode(hasTestTag(GAME_ROW_DRAWN_TAG) and hasAnyAncestor(hasTestTag("row$index")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                val title = compose.onNode(hasText("Title $index") and hasAnyAncestor(hasTestTag("row$index")), useUnmergedTree = true).fetchSemanticsNode()
                val year = compose.onNode(hasText("199$index") and hasAnyAncestor(hasTestTag("row$index")), useUnmergedTree = true).fetchSemanticsNode()
                val titleBox = title.boundsInRoot
                val yearBox = year.boundsInRoot
                if (abs(row.height - expectedHeight) > onePx) problems += "$at: row is ${row.height} px high, not $expectedHeight"
                if (abs((titleBox.top + titleBox.bottom) / 2 - (row.top + row.bottom) / 2) > onePx) problems += "$at: title centre ${(titleBox.top + titleBox.bottom) / 2} vs row centre ${(row.top + row.bottom) / 2}"
                val titleBaseline = titleBox.top + layoutOf(title).getLineBaseline(0)
                val yearBaseline = yearBox.top + layoutOf(year).getLineBaseline(0)
                if (abs(titleBaseline - yearBaseline) > onePx) problems += "$at: title baseline $titleBaseline, year baseline $yearBaseline"
                if (titleBox.top < row.top - onePx || titleBox.bottom > row.bottom + onePx) problems += "$at: title $titleBox is cut by the row $row"
                if (yearBox.top < row.top - onePx || yearBox.bottom > row.bottom + onePx) problems += "$at: year $yearBox is cut by the row $row"
                if (layoutOf(title).didOverflowHeight || layoutOf(year).didOverflowHeight) problems += "$at: a text overflows its height"
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    protected fun theFocusedFillCoversTheWholeRow(label: String) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) {
                GameRow(title = "Title", trailing = "1990", look = GameRowLook.Focused, modifier = Modifier.testTag("focused"))
            }
        }
        compose.waitForIdle()
        val density = ApplicationProvider.getApplicationContext<Context>().resources.displayMetrics.density
        val row = compose.onNode(hasTestTag(GAME_ROW_DRAWN_TAG) and hasAnyAncestor(hasTestTag("focused")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // The fill is a background on the drawn row's own modifier chain, so it is exactly that box (the tap area adds the gap below).
        assertEquals("$label: focused row height", 52.2f * density, row.height, 1f)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameRowAlign768Test : GameRowAlignBase() {
    @Test fun theTitleIsCentredAndTheYearSharesItsBaseline() = theTitleIsCentredAndTheYearSharesItsBaseline("768x432")

    @Test fun theFocusedFillCoversTheWholeRow() = theFocusedFillCoversTheWholeRow("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GameRowAlign576Test : GameRowAlignBase() {
    @Test fun theTitleIsCentredAndTheYearSharesItsBaseline() = theTitleIsCentredAndTheYearSharesItsBaseline("576x432")

    @Test fun theFocusedFillCoversTheWholeRow() = theFocusedFillCoversTheWholeRow("576x432")
}
