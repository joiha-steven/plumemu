package com.plumemu.home

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The band's facts line ("Arcade games from many makers" has no shorter form) is on one line at the Nova's width at every text size: it fits, or its
 * end is an ellipsis inside the room the band leaves; it is never wrapped or cut mid-letter by the clip.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SystemFactsLineFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theArcadeFactsLineFitsOrEndsInAnEllipsisAtEveryTextSize() {
        var size by mutableStateOf(TextSize.Small)
        val content = FakeHomeContent.board().withArcadeAbout()
        val index = content.entries.indexOfFirst { it.id == BOARD_ARCADE_ID }
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = PlumFont.Serif)) {
                HomeAt(content, listOf(Action.TapItem(Column.Systems, index), Action.Enter))
            }
        }
        val problems = mutableListOf<String>()
        for (each in TextSize.entries) {
            size = each
            compose.waitForIdle()
            val node = compose.onNodeWithTag(HomeTags.SYSTEM_BAND_FACTS, useUnmergedTree = true).fetchSemanticsNode()
            val bounds = node.boundsInRoot
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            // A line that does not fit whole must end in an ellipsis: ink in the node's last two pixel columns is a letter cut by the clip.
            val edge = HashSet<Int>()
            for (x in (bounds.right.toInt() - 2) until bounds.right.toInt()) for (y in bounds.top.toInt() until bounds.bottom.toInt()) edge += pixels[x, y].hashCode()
            val lines = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(lines)
            val fitsWhole = lines.first().multiParagraph.intrinsics.maxIntrinsicWidth <= bounds.width + 1f
            // An ellipsised line was laid out to fit: its "…" may end within a pixel of the edge, which is not a cut.
            if (!fitsWhole && !lines.first().isLineEllipsized(0) && edge.size > 1) problems += "${each.name}: ink touches the right edge of the line (cut by the clip)"
            if (lines.first().lineCount != 1) problems += "${each.name}: ${lines.first().lineCount} lines"
        }
        assertTrue("${problems.size} problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
