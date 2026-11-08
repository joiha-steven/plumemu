package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * While Remove from Recent's Undo is offered (the top bar's "Removed from Recent · Undo" line and X's "Undo" hint),
 * at 576x432, 640x480 and 768x432, in English, French, Japanese and Korean, at Small and Largest text, in a games list
 * and in the Apps grid (where X is the hint that gives way first without Undo): the line stays between the side margin
 * and the clock, its two parts do not overlap, the hint bar keeps X and ends inside the screen. Prints the numbers
 * (`UNDO-FIT`); a part ellipsised is printed too: the top bar's other notices ellipsise the same way (`PlaceText`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class RecentUndoFitTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun str(id: Int) = RuntimeEnvironment.getApplication().getString(id)

    private fun inTopBar(text: String) = hasText(text) and hasAnyAncestor(hasTestTag(HomeTags.TOP_BAR))

    private fun boundsOf(text: String): DpRect = compose.onNode(inTopBar(text), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun isCut(text: String): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNode(inTopBar(text), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.any { r -> (0 until r.lineCount).any { r.isLineEllipsized(it) } }
    }

    private fun hintsShown(): List<String> =
        listOf("A" to Action.Play, "B" to Action.Back, "X" to Action.Search, "START" to Action.Menu, "L2R2" to Action.JumpLetterNext, "SELECT" to Action.ToggleView, "Y" to Action.ToggleFavorite, "R3" to Action.Sort)
            .mapNotNull { (name, action) ->
                compose.onAllNodesWithTag(HomeTags.hint(action), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull { it.layoutInfo.isPlaced }?.let { name to it.boundsInRoot }
            }.sortedBy { it.second.left }.also { shown ->
                val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
                assertTrue("the hints end inside the screen: ${shown.map { it.first to it.second.right }} of ${root.right}", shown.all { it.second.right <= root.right + 0.5f })
            }.map { it.first }

    private fun at(
        width: Int,
        height: Int,
        language: String,
    ) {
        RuntimeEnvironment.setQualifiers("$language-w${width}dp-h${height}dp-land-xhdpi".trimStart('-'))
        val lead = str(R.string.home_notice_removed_from_recent)
        val tail = str(R.string.home_hint_undo)
        var size by mutableStateOf(TextSize.Small)
        var apps by mutableStateOf(false)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size)) {
                CompositionLocalProvider(LocalRecentUndoOffered provides { true }) {
                    if (apps) AppsGridBoard(focused = 8) else HomeAt(FakeHomeContent.board(), BoardGamesFocused)
                }
            }
        }
        val problems = mutableListOf<String>()
        for (each in listOf(TextSize.Small, TextSize.Largest)) {
            for (isApps in listOf(false, true)) {
                size = each
                apps = isApps
                compose.waitForIdle()
                val where = "$language $width x $height $each ${if (isApps) "apps" else "list"}"
                val root = compose.onRoot().getUnclippedBoundsInRoot()
                val clock = boundsOf(BOARD_CLOCK)
                val l = boundsOf(lead)
                val t = boundsOf(tail)
                val hints = hintsShown()
                println("UNDO-FIT $where: line ${l.left.value}..${l.right.value} + ${t.left.value}..${t.right.value}, clock from ${clock.left.value}, margin ${PlumDimens.stripMarginSide.value}, cut ${isCut(lead)}/${isCut(tail)}, hints $hints")
                if (l.left.value < PlumDimens.stripMarginSide.value - 0.5f) problems += "$where: the line starts ${l.left.value}, inside the margin"
                if (l.right.value > t.left.value + 0.5f) problems += "$where: the notice overlaps Undo"
                if (t.right.value > clock.left.value + 0.5f) problems += "$where: the line reaches the clock (${t.right.value} > ${clock.left.value})"
                if (t.right.value > root.right.value) problems += "$where: the line leaves the screen"
                if (isCut(tail)) problems += "$where: Undo is cut"
                if (isCut(lead)) problems += "$where: the notice is cut"
                if ("X" !in hints) problems += "$where: X Undo is not drawn ($hints)"
                if ("A" !in hints) problems += "$where: A is not drawn"
            }
        }
        assertTrue(problems.size.toString() + " problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun english576() = at(576, 432, "")

    @Test fun english640() = at(640, 480, "")

    @Test fun english768() = at(768, 432, "")

    @Test fun french576() = at(576, 432, "fr")

    @Test fun french640() = at(640, 480, "fr")

    @Test fun french768() = at(768, 432, "fr")

    @Test fun japanese576() = at(576, 432, "ja")

    @Test fun japanese640() = at(640, 480, "ja")

    @Test fun japanese768() = at(768, 432, "ja")

    @Test fun korean576() = at(576, 432, "ko")

    @Test fun korean640() = at(640, 480, "ko")

    @Test fun korean768() = at(768, 432, "ko")
}
