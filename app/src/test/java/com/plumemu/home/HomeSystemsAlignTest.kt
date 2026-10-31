package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
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
 * The systems column lines up with the page: every system label starts on the
 * left line of the top bar's text and the first hint key cap (the page margin), focused or not;
 * the focus dot sits in the margin left of the label, inside the selector; the selector starts at
 * x = 0 (square left corners, rounded right ones). At the four reference sizes and all six text sizes.
 */
abstract class HomeSystemsAlignBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun unclipped(matcher: SemanticsMatcher): DpRect = compose.onNode(matcher, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun inside(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag(HomeTags.system(FakeHomeContent.BOARD_SNES_INDEX)))

    protected fun check(label: String) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { HomeAt(FakeHomeContent.board(), BoardSystemsFocused) } }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val at = "$label ${each.name}"
            val header = unclipped(hasText("systems", substring = true) and hasAnyAncestor(hasTestTag(HomeTags.TOP_BAR))).left.value
            val hint = compose.onNodeWithTag(HomeTags.hint(Action.Enter), useUnmergedTree = true).getUnclippedBoundsInRoot().left.value
            val labels = compose.onAllNodesWithTag(SystemRowTags.LABEL, useUnmergedTree = true)
            val lefts = labels.fetchSemanticsNodes().indices.map { labels[it].getUnclippedBoundsInRoot().left.value }
            val focusedLabel = unclipped(inside(SystemRowTags.LABEL)).left.value
            val pill = unclipped(inside(SystemRowTags.PILL))
            val rowBox = unclipped(hasTestTag(HomeTags.system(FakeHomeContent.BOARD_SNES_INDEX)))
            val labelLeft = focusedLabel
            fun expect(what: String, ok: Boolean, numbers: String) {
                if (!ok) problems += "$at: $what ($numbers)"
            }
            fun near(a: Float, b: Float) = kotlin.math.abs(a - b) <= TOLERANCE
            expect("header = hint key cap", near(header, hint), "header $header, hint $hint")
            expect("at least 5 labels visible", lefts.size >= 5, "${lefts.size}")
            expect("every label = header", lefts.all { near(it, header) }, "header $header, labels ${lefts.distinct()}")
            expect("focused label = header", near(labelLeft, header), "focused $labelLeft, header $header")
            val inset = (PlumDimens.stripMarginSide - PlumDimens.gameRowPadding).value
            val labelBox = unclipped(inside(SystemRowTags.LABEL))
            expect("pill inset from the screen edge", near(pill.left.value, inset), "pill left ${pill.left.value}, inset $inset")
            expect("pill spans the row box", near(pill.top.value, rowBox.top.value) && near(pill.bottom.value, rowBox.bottom.value) && near(pill.right.value, rowBox.right.value), "pill $pill, row $rowBox")
            expect("label is a game row's padding from the pill's left", near(labelLeft - pill.left.value, PlumDimens.gameRowPadding.value), "${labelLeft - pill.left.value}")
            expect("label centred on the pill (baseline as unfocused)", near((labelBox.top.value + labelBox.bottom.value) / 2, (pill.top.value + pill.bottom.value) / 2), "label ${labelBox.top.value}..${labelBox.bottom.value}, pill ${pill.top.value}..${pill.bottom.value}")
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * The strip slides to the games (the column into the peek) and back, at Medium and Largest. Every frame, from
     * unclipped bounds: the row is margin + column width wide and starts where the strip is; while the row is
     * focused its selector starts at the row's left edge, the label is the margin from it, and nothing passes the
     * column's right edge. Fails if no frame was moving or no selector was found on the way back.
     */
    protected fun checkSlides(label: String) {
        val problems = mutableListOf<String>()
        lateinit var state: StripState
        var columnWidth = 0f
        var size by mutableStateOf(TextSize.Medium)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                columnWidth = (PlumDimens.stripMarginSide + PlumTheme.layout.systemsColumnWidth).value
                HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)), isMotionOn = true, onState = { state = it })
            }
        }
        for (textSize in listOf(TextSize.Medium, TextSize.Largest)) {
            size = textSize
            val row = hasTestTag(HomeTags.system(FakeHomeContent.BOARD_SNES_INDEX))
            var sawMoving = 0
            var sawPill = 0
            fun frame(at: String, expectPill: Boolean) {
                val bounds = unclipped(row)
                val name = unclipped(inside(SystemRowTags.LABEL))
                if (kotlin.math.abs((bounds.right - bounds.left).value - columnWidth) > TOLERANCE) problems += "$at: row is ${(bounds.right - bounds.left).value} wide, not $columnWidth"
                if (bounds.left.value > TOLERANCE) problems += "$at: row starts at ${bounds.left.value}, right of the screen edge"
                if (bounds.left.value < -TOLERANCE) sawMoving++
                if (kotlin.math.abs((name.left - bounds.left).value - PlumDimens.stripMarginSide.value) > TOLERANCE) problems += "$at: label is ${(name.left - bounds.left).value} from the row's left edge"
                val pills = compose.onAllNodesWithTag(SystemRowTags.PILL, useUnmergedTree = true).fetchSemanticsNodes()
                if (expectPill && pills.isEmpty()) problems += "$at: no selector on the focused row"
                if (pills.isNotEmpty()) {
                    sawPill++
                    val pill = unclipped(inside(SystemRowTags.PILL))
                    val inset = (PlumDimens.stripMarginSide - PlumDimens.gameRowPadding).value
                    if (kotlin.math.abs(pill.left.value - bounds.left.value - inset) > TOLERANCE) problems += "$at: selector left ${pill.left.value}, row left ${bounds.left.value}, inset $inset"
                    if (pill.right.value > bounds.right.value + TOLERANCE) problems += "$at: selector right ${pill.right.value} past the column's right edge ${bounds.right.value}"
                }
            }
            // The clock is stepped by hand: with it running, the slide would finish before it could be sampled.
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.runOnUiThread { state.dispatch(Action.Enter) }
            repeat(14) {
                compose.mainClock.advanceTimeBy(16)
                frame("$label $textSize to the peek, frame $it", expectPill = false)
            }
            compose.mainClock.advanceTimeBy(SLIDE_AND_MORE_MS)
            if (sawMoving == 0) problems += "$label $textSize: the strip never moved on the way to the peek"
            sawMoving = 0
            compose.runOnUiThread { state.dispatch(Action.Back) }
            repeat(14) {
                compose.mainClock.advanceTimeBy(16)
                frame("$label $textSize back, frame $it", expectPill = true)
            }
            compose.mainClock.advanceTimeBy(SLIDE_AND_MORE_MS)
            if (sawMoving == 0) problems += "$label $textSize: the strip never moved on the way back"
            if (sawPill == 0) problems += "$label $textSize: no selector found while sliding back"
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.take(30).joinToString("\n"), problems.isEmpty())
    }

    companion object {
        const val TOLERANCE = 0.5f
        const val SLIDE_AND_MORE_MS = 600L
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSystemsAlign768Test : HomeSystemsAlignBase() {
    @Test fun labelsHeaderAndHintsShareOneLeftLine() = check("768x432")

    @Test fun systemsColumnHoldsWhileTheStripSlides() = checkSlides("768x432")

}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class HomeSystemsAlign837Test : HomeSystemsAlignBase() {
    @Test fun labelsHeaderAndHintsShareOneLeftLine() = check("837x471")

    @Test fun systemsColumnHoldsWhileTheStripSlides() = checkSlides("837x471")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class HomeSystemsAlign667Test : HomeSystemsAlignBase() {
    @Test fun labelsHeaderAndHintsShareOneLeftLine() = check("667x375")

    @Test fun systemsColumnHoldsWhileTheStripSlides() = checkSlides("667x375")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeSystemsAlign576Test : HomeSystemsAlignBase() {
    @Test fun labelsHeaderAndHintsShareOneLeftLine() = check("576x432")

    @Test fun systemsColumnHoldsWhileTheStripSlides() = checkSlides("576x432")
}
