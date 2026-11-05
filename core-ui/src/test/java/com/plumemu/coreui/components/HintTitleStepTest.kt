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

    private fun show() {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(font = fontState.value, textSize = sizeState.value)) {
                boardSize = PlumTheme.type.infoLineTitle.fontSize.value
                steppedSize = PlumTheme.type.gridFocusTitle.fontSize.value
                Box(Modifier.width(barWidth.value.dp)) {
                    TitledHintBar(title, "", 0.dp, dropOrder = listOf(3, 2, 1)) {
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

    private companion object {
        const val HINTS = 4
        const val HINT_WIDTH = 60
    }
}
