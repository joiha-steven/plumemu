package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
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
 * The grid bar's title takes the board's size (16), or steps down so that no hint key is dropped: swept over bar widths in every font
 * at Medium and Largest, a hint never goes while the title is at 16, and a narrower bar steps the title down with every hint kept.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HintTitleStepTest {
    @get:Rule
    val compose = createComposeRule()

    private val barWidth = mutableStateOf(700)
    private val fontState = mutableStateOf(PlumFont.Sans)
    private val sizeState = mutableStateOf(TextSize.Default)
    private var boardSize = 0f
    private var steppedSize = 0f
    private val title = "Mega Man X Legacy Collection"

    private class Look(val size: Float, val hintsKept: Int)

    private fun show(text: String = title, beforeMeta: List<Int> = emptyList()) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = sizeState.value)) {
                boardSize = PlumTheme.type.infoLineTitle.fontSize.value
                steppedSize = PlumTheme.type.gridFocusTitle.fontSize.value
                Box(Modifier.width(barWidth.value.dp)) {
                    TitledHintBar(text, "", 0.dp, dropOrder = listOf(3, 2, 1), dropBeforeMeta = beforeMeta) {
                        repeat(HINTS) { Box(Modifier.size(HINT_WIDTH.dp, 10.dp).testTag("hint$it")) }
                    }
                }
            }
        }
    }

    private fun look(): Look {
        compose.waitForIdle()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(title)).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
        val kept = (0 until HINTS).count { compose.onNodeWithTag("hint$it").fetchSemanticsNode().layoutInfo.isPlaced }
        return Look(layout.single().layoutInput.style.fontSize.value, kept)
    }

    @Test
    fun theTitleIsAtTheBoardsSizeOrStepsDownKeepingEveryHint() {
        show()
        for (font in PlumFont.entries) for (size in listOf(TextSize.Default, TextSize.Largest)) {
            val what = "$font $size"
            fontState.value = font
            sizeState.value = size
            val looks = (240..700 step 4).map { w -> barWidth.value = w; w to look() }
            for ((w, l) in looks) {
                if (l.size == boardSize) assertEquals("$what at $w: the title at the board's size keeps every hint", HINTS, l.hintsKept)
            }
            assertTrue("$what: a wide bar shows the board's size", looks.last().second.size == boardSize)
            assertTrue("$what: a narrower bar steps down with every hint kept", looks.any { it.second.size == steppedSize && it.second.hintsKept == HINTS })
        }
    }

    private var shownOnce = false

    /** Kept hints, whether the title is cut, how many characters show, and its font size, with [text] on a bar [width] dp wide. */
    private class Long(val kept: Int, val cut: Boolean, val visible: Int, val size: Float)

    private fun longLook(width: Int, beforeMeta: List<Int> = emptyList(), text: String = LONG_TITLE): Long {
        if (!shownOnce) show(text, beforeMeta).also { shownOnce = true }
        barWidth.value = width
        compose.waitForIdle()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText(text)).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
        val kept = (0 until HINTS).count { compose.onNodeWithTag("hint$it").fetchSemanticsNode().layoutInfo.isPlaced }
        val r = layout.single()
        // EllipsisText lays out the cut string itself, ending in the mark, so a cut shows as a shorter text.
        val shown = r.layoutInput.text.text
        return Long(kept, shown != text, shown.length - 1, r.layoutInput.style.fontSize.value)
    }

    @Test
    fun aLongTitleIsCutBeforeAnyHintLeaves() {
        // 4 hints of 60 + gaps leave about 250 of 528: the title is cut, every hint stays.
        val l = longLook(528)
        assertEquals("every hint stays", HINTS, l.kept)
        assertTrue("the long title is cut with an ellipsis", l.cut)
    }

    @Test
    fun hintsLeaveOnlyOnceTheTitleIsAtItsMinimum() {
        // Room for the minimum title and 2 of the 3 droppable hints: the first one goes, not all.
        val l = longLook(96 + 2 * 69 + 3 * 69 / 3 + 40)
        assertTrue("the title is still cut", l.cut)
        assertEquals("only the first in the drop order leaves", HINTS - 1, l.kept)
        assertTrue("the minimum shows about 10 characters (${l.visible})", l.visible >= 10)
    }

    @Test
    fun theKeyNamedBeforeMetaLeavesBeforeTheTitleIsCut() {
        // 528 holds all four hints with the title cut; a key named in dropBeforeMeta (L2 R2) goes first, so the title is cut with it gone.
        val l = longLook(528, beforeMeta = listOf(3))
        assertEquals("the named key leaves, the other three stay", HINTS - 1, l.kept)
        assertTrue("the title is cut", l.cut)
    }

    @Test
    fun aMediumTitleShowsWholeAtThirteenRatherThanCutAtSixteen() {
        // Hints 3 stay once L2 R2 (index 3) is gone; the title is too wide whole at 16 but fits whole at 13.
        val looks = (260..560 step 4).map { w -> w to longLook(w, beforeMeta = listOf(3), text = MEDIUM_TITLE) }
        assertTrue("some width shows the whole title at 13 with the same keys", looks.any { (_, l) -> l.kept == HINTS - 1 && l.size == steppedSize && !l.cut })
        assertTrue("no width with 3 keys cuts the title at 16 while 13 would show it whole", looks.none { (_, l) -> l.kept == HINTS - 1 && l.size == boardSize && l.cut })
    }

    private companion object {
        const val MEDIUM_TITLE = "Mega Man X Legacy Collection"
        const val LONG_TITLE = "The Legend of Extremely Long Titles: A Very Long Subtitle That Goes On And On"
        const val HINTS = 4
        const val HINT_WIDTH = 60
    }
}
