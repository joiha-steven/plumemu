package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.components.GAME_ROW_DRAWN_TAG
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
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
import kotlin.math.abs

/**
 * With a games list resting at its top (no system band in view), each system's count in the left peek
 * sits on the baseline of the game row's title beside it, at every text size, across the Recent/Favorites/Apps group.
 * The test finds the column's text by [SystemRowTags.COUNT], not by what it says: whatever the column shows (a count
 * or a system code) must keep that tag, so this equality still holds.
 */
abstract class CountColumnBaselineBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun layoutOf(node: SemanticsNode): TextLayoutResult? {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        return layouts.firstOrNull()
    }

    private fun firstBaseline(node: SemanticsNode) = node.boundsInRoot.top + layoutOf(node)!!.getLineBaseline(0)

    private fun texts(node: SemanticsNode): List<SemanticsNode> = (if (layoutOf(node) != null) listOf(node) else emptyList()) + node.children.flatMap { texts(it) }

    protected fun countsShareTheTitlesBaseline(label: String) {
        val problems = mutableListOf<String>()
        var compared = 0
        var current by mutableStateOf(TextSize.Small)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = current)) {
                    HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 0)), showSystemInfo = false)
            }
        }
        for (size in TextSize.entries) {
            current = size
            compose.waitForIdle()
            val counts = compose.onAllNodes(hasTestTag(SystemRowTags.COUNT), useUnmergedTree = true).fetchSemanticsNodes()
            val rows = compose.onAllNodes(hasTestTag(GAME_ROW_DRAWN_TAG) and hasAnyAncestor(hasTestTag(HomeTags.GAMES_PAGE)), useUnmergedTree = true).fetchSemanticsNodes()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            for (count in counts.filter { it.boundsInRoot.top > root.top && it.boundsInRoot.bottom < root.bottom }) {
                val centre = (count.boundsInRoot.top + count.boundsInRoot.bottom) / 2
                val row = rows.firstOrNull { centre >= it.boundsInRoot.top && centre <= it.boundsInRoot.bottom } ?: continue
                val title = texts(row).minByOrNull { it.boundsInRoot.left } ?: continue
                // A row is only a partner when it is drawn where its bounds say (not parked under a bar).
                if (abs((title.boundsInRoot.top + title.boundsInRoot.bottom) / 2 - centre) > row.boundsInRoot.height) continue
                val countBaseline = firstBaseline(count)
                val titleBaseline = firstBaseline(title)
                compared++
                if (abs(countBaseline - titleBaseline) > 0.5f) problems += "$label ${size.name}: count at ${count.boundsInRoot.top} baseline $countBaseline, title baseline $titleBaseline"
            }
        }
        assertTrue("some counts were compared: $compared", compared >= TextSize.entries.size * 4)
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class CountColumnBaseline768Test : CountColumnBaselineBase() {
    @Test fun countsShareTheTitlesBaseline() = countsShareTheTitlesBaseline("768x432")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class CountColumnBaseline837Test : CountColumnBaselineBase() {
    @Test fun countsShareTheTitlesBaseline() = countsShareTheTitlesBaseline("837x471")
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class CountColumnBaseline576Test : CountColumnBaselineBase() {
    @Test fun countsShareTheTitlesBaseline() = countsShareTheTitlesBaseline("576x432")
}
