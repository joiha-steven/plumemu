package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The grid's info line as the Covers board draws it: title 16, then a 9.33 dp gap (no dot), then the meta at 11.33. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class InfoLineGapTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun titleThenGapThenMetaAtTheBoardsSizes() {
        var density = 1f
        compose.setContent {
            PlumTheme(TableText.Graphite) {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                Box(Modifier.width(700.dp)) { TitledHintBar("Contra III", "1992 · Konami", 0.dp) {} }
            }
        }
        compose.waitForIdle()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText("Contra III 1992 · Konami")).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
        val result = layout.single()
        val titleEnd = "Contra III".length
        val gapDp = result.getBoundingBox(titleEnd).width / density
        assertEquals("the gap between the title and the meta, dp", 9.33f, gapDp, 0.4f)
        assertEquals("the title's size", 16f, result.layoutInput.style.fontSize.value, 0.01f)
        val metaSize = result.layoutInput.text.spanStyles.first { it.item.fontSize.isSp }.item.fontSize.value
        assertEquals("the meta's size", 11.33f, metaSize, 0.01f)
    }

    /**
     * The 768 x 432 home grid at Medium: five hints leave 191 dp for "Super Mario World  1990 · Nintendo", which does not fit at 16
     * or at 13 (the board's 229 dp, 13's 188), so the hint that gives way to the meta goes either way; the title then stays at 16
     * rather than stepping down for nothing.
     */
    @Test
    fun theTitleStaysAtTheBoardsSizeWhenStepDownWouldNotKeepAHint() {
        var titleSize = 0f
        val widths = listOf(57, 61, 77, 131, 111)
        compose.setContent {
            PlumTheme(TableText.Graphite) {
                Box(Modifier.width(696.dp)) {
                    TitledHintBar("Super Mario World", "1990 · Nintendo", 0.dp, dropBeforeMeta = listOf(3)) {
                        widths.forEachIndexed { i, w -> Box(Modifier.size(w.dp, 10.dp).testTag("hint$i")) }
                    }
                }
            }
        }
        compose.waitForIdle()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNode(hasText("Super Mario World", substring = true)).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layout)
        titleSize = layout.single().layoutInput.style.fontSize.value
        val kept = widths.indices.count { compose.onNodeWithTag("hint$it").fetchSemanticsNode().layoutInfo.isPlaced }
        assertEquals("the title stays at the board's 16", 16f, titleSize, 0.01f)
        assertEquals("only the hint that gives way to the meta is left out", 4, kept)
    }
}
