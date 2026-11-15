package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The cover grid's bar: the focused title (with its meta) and the hint labels share one first baseline. */
abstract class GridHintBarBaselineBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun firstBaseline(node: SemanticsNode): Float {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return node.boundsInRoot.top + layouts.first().getLineBaseline(0)
    }

    protected fun titleSharesTheHintLabelsBaseline(label: String) {
        var size by mutableStateOf(TextSize.Small)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) { CoverGridBoard(rememberSource { coverArtSource() }) } }
        for (each in listOf(TextSize.Small, TextSize.Large)) {
            size = each
            compose.waitForIdle()
            val title = compose.onNodeWithTag(HomeTags.GRID_FOCUS_TITLE, useUnmergedTree = true).fetchSemanticsNode()
            val back = compose.onNode(hasTestTag(HomeTags.hint(Action.Back)), useUnmergedTree = true)
            val labelNode = compose.onNode(hasAnyAncestor(hasTestTag(HomeTags.hint(Action.Back))) and hasText("Back"), useUnmergedTree = true).fetchSemanticsNode()
            back.assertExists()
            assertEquals("$label $each: title baseline against the Back label's", firstBaseline(labelNode), firstBaseline(title), 0.5f)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridHintBarBaseline768Test : GridHintBarBaselineBase() {
    @Test fun titleSharesTheHintLabelsBaseline() = titleSharesTheHintLabelsBaseline("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GridHintBarBaseline576Test : GridHintBarBaselineBase() {
    @Test fun titleSharesTheHintLabelsBaseline() = titleSharesTheHintLabelsBaseline("576x432")
}
