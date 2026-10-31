package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.game.GamePanelTags
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt

/**
 * Info over the board, its rows (the eight facts, System to Play time, then each line of "Name from")
 * read with Up and Down as a person does: a fit check lets the rows be cut at the edges of their scroll
 * (`GamePanelTags.FACTS_SCROLL`), and [unreadRows] makes sure that is all it is. Every row must be shown
 * whole after some number of Down presses, and Up must bring the first back. Wherever it stops (as Info
 * opens and after every press), a row hidden below or above must be told by the first of them being
 * visibly cut at that edge: at least a third of a line in view and at least a quarter out of it. A row
 * left out, one no press brings whole, or rows hidden with nothing of them showing are problems: the
 * person could never read them, or would not know to press again.
 */
internal class InfoReadThrough(
    val panel: OpenPanel,
) {
    private val scroll = (panel.screen as PanelScreen.Info).scroll
    private val screenFacts = (panel.screen as PanelScreen.Info).facts

    /** The fact rows: the eight, and the Battery row of a game measured before. */
    private val infoFacts = INFO_FACTS + (if (screenFacts.batteryPace != null) 1 else 0) + scrapedRowCount(screenFacts.scraped)

    val screen: @Composable () -> Unit = { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }

    /** The problems of the screen as it is now; it is left at the facts' top. */
    fun unreadRows(compose: ComposeContentTestRule): List<String> {
        val problems = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var rowCount = infoFacts
        var rowTags = emptyList<String>()
        var firstIsWhole = false
        fun look(where: String) {
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val rows = infoRows(root)
            rowCount = rows.size
            rowTags = rows.map { it.first }
            val whole = rows.filter { (_, node) -> node.layoutInfo.isPlaced && notFullyShown(node, root) == null }.map { it.first }
            seen += whole
            firstIsWhole = rows.firstOrNull()?.first in whole
            findTagged(root, GamePanelTags.FACTS_SCROLL)?.let { viewport -> hiddenWithoutCue(viewport, rows.map { it.second }).mapTo(problems) { "$where: $it" } }
        }
        fun press(isDown: Boolean): Boolean = compose.runOnIdle { scroll.press(isDown) }
        look("as Info opens")
        for ((isDown, name) in listOf(true to "Down", false to "Up")) {
            var presses = 0
            while (press(isDown)) {
                presses++
                look("after $presses $name")
                // One press per row is always enough.
                if (presses > rowCount) {
                    problems += "$name still moves after $presses presses"
                    break
                }
            }
        }
        problems += allTags(rowTags).filterNot { it in seen }.map { "the row $it is never whole on screen, whatever Up and Down do" }
        if (!screenFacts.scraped?.description.isNullOrEmpty() && rowTags.none { it.startsWith("game_panel_description") }) problems += "the description shows but none of its blocks was found to read"
        if (!firstIsWhole) problems += "Up as far as it goes does not bring ${GamePanelTags.fact(0)} back"
        // "Name from" scrolls with the facts, exempt from the clip check like them: its lines must be read here.
        if (screenFacts.name != null && rowTags.none { it.startsWith("game_panel_name_from") }) problems += "\"Name from\" shows but none of its lines was found to read"
        return problems
    }

    /** Info's rows on screen, top to bottom: the facts, then "Name from"'s lines (0 its label). */
    private fun infoRows(root: SemanticsNode): List<Pair<String, SemanticsNode>> =
        (0 until infoFacts).mapNotNull { index -> findTagged(root, GamePanelTags.fact(index))?.let { GamePanelTags.fact(index) to it } } +
            generateSequence(0) { it + 1 }.map { GamePanelTags.nameFromLine(it) }.map { tag -> findTagged(root, tag)?.let { tag to it } }.takeWhile { it != null }.filterNotNull() +
            generateSequence(0) { it + 1 }.map { GamePanelTags.descriptionBlock(it) }.map { tag -> findTagged(root, tag)?.let { tag to it } }.takeWhile { it != null }.filterNotNull()

    /** Every row that must be read: the facts, then every "Name from" line and description block found. */
    private fun allTags(found: List<String>): List<String> = (0 until infoFacts).map(GamePanelTags::fact) + found.filterNot { it.startsWith("game_panel_fact_") }

    private fun scrapedRowCount(scraped: com.plumemu.game.ScrapedFacts?): Int =
        listOfNotNull(scraped?.year?.toString(), scraped?.publisher, scraped?.developer, scraped?.genre, scraped?.players).count { it.isNotEmpty() }

    /** Whether the first row hidden below the [viewport]'s bottom, and the last hidden above its top, are visibly cut there. */
    private fun hiddenWithoutCue(
        viewport: SemanticsNode,
        rows: List<SemanticsNode>,
    ): List<String> {
        val edges = viewport.boundsInRoot
        val spans = rows.map { it.positionInRoot.y.let { top -> top to top + it.size.height } }
        val line = spans.minOfOrNull { (top, bottom) -> bottom - top } ?: return emptyList()
        val (mustShow, mustHide) = line / 3f to line / 4f
        val problems = mutableListOf<String>()
        spans.filter { (_, bottom) -> bottom > edges.bottom + SLACK }.minByOrNull { it.first }?.let { (top, bottom) ->
            val shown = edges.bottom - top
            val hidden = bottom - edges.bottom
            if (shown + SLACK < mustShow || hidden + SLACK < mustHide) problems += "rows are hidden below and nothing says so: the next shows $shown px and hides $hidden px (at least $mustShow / $mustHide)"
        }
        spans.filter { (top, _) -> top < edges.top - SLACK }.maxByOrNull { it.first }?.let { (top, bottom) ->
            val shown = bottom - edges.top
            val hidden = edges.top - top
            if (shown + SLACK < mustShow || hidden + SLACK < mustHide) problems += "rows are hidden above and nothing says so: the next shows $shown px and hides $hidden px (at least $mustShow / $mustHide)"
        }
        return problems
    }
}

/** Info's fact rows: System, Region, File (or Arcade set), Size, Folder, Emulator, Last played, Play time; then Battery for a game measured before. */
private const val INFO_FACTS = 8

/** Rounding of positions (px). */
private const val SLACK = 0.5f
