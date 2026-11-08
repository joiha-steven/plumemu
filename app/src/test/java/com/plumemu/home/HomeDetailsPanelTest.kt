package com.plumemu.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wide details column's facts block at the Thor's size with little data and with long words, by measured bounds: no text over another,
 * labels a gutter apart and inside the column, the genre on one line, no empty cell at the start of a row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeDetailsPanelTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(
        genre: String,
        rating: Int?,
        facts: PlayFacts,
    ) {
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = genre, players = "1", description = "", rating = rating)
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) { Panel(details, facts) }
        }
        compose.waitForIdle()
    }

    @Composable
    private fun Panel(
        details: GameDetails,
        facts: PlayFacts,
    ) {
        // A bounded height, as the details column gives the box: the real path, where cells can be left out whole.
        Column(Modifier.width(PlumDimens.detailsColumnWidth).height(PANEL_HEIGHT).testTag(PANEL)) { DetailsBlock(details, ShownPlay.Fixed(facts)) }
    }

    private fun texts(node: SemanticsNode): List<SemanticsNode> =
        (if (SemanticsProperties.Text in node.config && node.layoutInfo.isPlaced) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun text(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("")

    private fun lines(node: SemanticsNode): Int {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.firstOrNull()?.lineCount ?: 1
    }

    private fun check(expectedLabels: List<String>) {
        val block = compose.onNodeWithTag(PANEL, useUnmergedTree = true).fetchSemanticsNode()
        val column = block.boundsInRoot
        val all = texts(block)
        val shown = all.map(::text)
        for (label in expectedLabels) assertTrue("$label is shown: $shown", label in shown)
        for ((i, a) in all.withIndex()) {
            val x = a.boundsInRoot
            assertTrue("'${text(a)}' is inside the column $column: $x", x.left >= column.left - 0.5f && x.right <= column.right + 0.5f)
            for (b in all.drop(i + 1)) {
                val y = b.boundsInRoot
                val overlaps = x.left < y.right - 0.5f && y.left < x.right - 0.5f && x.top < y.bottom - 0.5f && y.top < x.bottom - 0.5f
                assertTrue("'${text(a)}' $x overlaps '${text(b)}' $y", !overlaps)
                // Two texts on one line (a label beside a label, a value beside a value) keep a gutter: no run-on caps.
                val sameLine = x.top < y.bottom && y.top < x.bottom
                val gap = if (x.left < y.left) y.left - x.right else x.left - y.right
                assertTrue("'${text(a)}' and '${text(b)}' are ${gap}px apart on one line", !sameLine || gap >= GUTTER_PX)
            }
        }
        // No empty cell: every row starts at the column's left edge.
        val rows = all.groupBy { Math.round(it.boundsInRoot.top / ROW_TOLERANCE_PX) }
        for ((_, row) in rows) {
            assertTrue("a row starts at the column's left edge: ${row.map(::text)} at ${row.minOf { it.boundsInRoot.left }} of ${column.left}", row.minOf { it.boundsInRoot.left } <= column.left + 0.5f)
        }
        for (each in all.filter { text(it).startsWith("Role") }) assertTrue("'${text(each)}' stays on one line", lines(each) == 1)
    }

    private val density get() = compose.density.density
    private val GUTTER_PX get() = (PlumDimens.factsColumnGap.value - 1f) * density
    private val ROW_TOLERANCE_PX get() = 6f * density

    @Test
    fun aGameNeverPlayedWithNoRatingOrGenre() {
        show("", null, PlayFacts(null, 0, "Asked on Play"))
        check(listOf("Played", "Last played", "Emulator"))
    }

    @Test
    fun aLongGenreBesideTheOtherCells() {
        show("Role Playing Game", 17, PlayFacts(null, 0, "Asked on Play"))
        check(listOf("Genre", "Rating", "Played", "Last played", "Emulator"))
    }

    @Test
    fun everyCellFilled() {
        show("Action, Adventure", 17, PlayFacts(2, 252, "RetroArch: Snes9x"))
        check(listOf("Genre", "Rating", "Played", "Last played", "Emulator"))
    }

    private companion object {
        const val PANEL = "details_panel"
        val PANEL_HEIGHT = 180.dp
    }
}
