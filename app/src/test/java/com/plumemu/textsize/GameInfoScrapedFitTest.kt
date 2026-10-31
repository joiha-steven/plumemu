package com.plumemu.textsize

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.GameFacts
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.ScrapedFacts
import com.plumemu.game.descriptionBlocks
import com.plumemu.game.sampleFacts
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A synopsis of the length ScreenScraper gives: four paragraphs, about 1,300 characters. */
internal val LONG_SYNOPSIS: String =
    (1..4).joinToString("\n") { n ->
        "Paragraph $n. The hunter lands on a planet that never sleeps, where every tunnel hides a new creature, a door that will not open " +
            "until the right weapon is found and a message left by someone who did not make it out. Explore, backtrack and grow stronger."
    }

/** The facts of a game ScreenScraper knows, every row filled. */
internal fun scrapedGameFacts(scraped: ScrapedFacts?): GameFacts =
    sampleFacts(title = "Super Metroid").copy(systemName = "Super Nintendo", regions = "USA", fileName = "Super Metroid.sfc", scraped = scraped)

internal val FULL_SCRAPED =
    ScrapedFacts(year = 1994, publisher = "Nintendo", developer = "Nintendo R&D1", genre = "Platform", players = "1", description = LONG_SYNOPSIS)

/**
 * Info with ScreenScraper's year, publisher, developer, genre, players and description: each row shows
 * when stored and is left out when not; the whole description can be read with Up and Down (the
 * facts' scroll steps one block a press) and with a finger, at every text size.
 */
abstract class GameInfoScrapedFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(facts: GameFacts): InfoReadThrough {
        val info = InfoReadThrough(OpenPanel(GameRef("1", facts.title), PanelScreen.Info(facts)))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { info.screen() } }
        compose.waitForIdle()
        return info
    }

    private fun present(tag: String) = compose.onAllNodesWithTagCount(tag) > 0

    @Test
    fun everyRowShowsWhenStored() {
        show(scrapedGameFacts(FULL_SCRAPED))
        for (text in listOf("Year", "1994", "Publisher", "Nintendo", "Developer", "Nintendo R&D1", "Genre", "Platform", "Players")) {
            assertTrue("$text is a row of Info", compose.onAllNodesWithTextCount(text) > 0)
        }
        assertTrue("the description's label", compose.onAllNodesWithTextCount("Description") > 0)
        assertTrue("and its first block", present(GamePanelTags.descriptionBlock(1)))
    }

    @Test
    fun rowsAreLeftOutWhenNotStored() {
        show(scrapedGameFacts(null))
        for (label in listOf("Year", "Publisher", "Developer", "Genre", "Players", "Description")) {
            assertEquals("$label is not a row without ScreenScraper's answer", 0, compose.onAllNodesWithTextCount(label))
        }
        assertTrue(!present(GamePanelTags.DESCRIPTION))
    }

    @Test
    fun onlyTheStoredRowsShow() {
        show(scrapedGameFacts(ScrapedFacts(year = 1994, genre = "Platform")))
        assertTrue(compose.onAllNodesWithTextCount("Year") > 0 && compose.onAllNodesWithTextCount("Genre") > 0)
        for (label in listOf("Publisher", "Developer", "Players", "Description")) assertEquals(label, 0, compose.onAllNodesWithTextCount(label))
    }

    @Test
    fun theWholeDescriptionIsReachableWithUpAndDownAtEverySize() {
        val facts = scrapedGameFacts(FULL_SCRAPED)
        var size by mutableStateOf(TextSize.Medium)
        var font by mutableStateOf(PlumFont.Sans)
        val info = InfoReadThrough(OpenPanel(GameRef("1", facts.title), PanelScreen.Info(facts)))
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { info.screen() } }
        val failures = mutableListOf<String>()
        val sizes = TextSize.entries.map { PlumFont.Sans to it } + listOf(PlumFont.Serif to TextSize.Largest, PlumFont.Mono to TextSize.Largest)
        for ((eachFont, each) in sizes) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
            val allow = FitAllowance(setOf(GamePanelTags.ROWS, GamePanelTags.FACTS_SCROLL), ellipsisUnder = GamePanelTags.FACTS, isLargest = each == TextSize.Largest)
            // The description's blocks are never ellipsized; the table's values may be at Largest only.
            textProblems(root, allow).mapTo(failures) { "${each.name} ${eachFont.name} on ${widthDp}x$heightDp: $it" }
            info.unreadRows(compose).mapTo(failures) { "${each.name} ${eachFont.name} on ${widthDp}x$heightDp: $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(30).joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aFingerScrollsToTheEndOfTheDescription() {
        show(scrapedGameFacts(FULL_SCRAPED))
        repeat(8) { compose.onNodeWithTag(GamePanelTags.FACTS_SCROLL, useUnmergedTree = true).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        // The last block is the one whose successor is missing.
        val last = generateSequence(1) { it + 1 }.takeWhile { present(GamePanelTags.descriptionBlock(it)) }.last()
        compose.onNodeWithTag(GamePanelTags.descriptionBlock(last), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theDescriptionIsNeverCutWithAnEllipsis() {
        show(scrapedGameFacts(FULL_SCRAPED))
        val shown = generateSequence(1) { it + 1 }.takeWhile { present(GamePanelTags.descriptionBlock(it)) }.count()
        assertTrue("a long synopsis takes several blocks, took $shown", shown > 2)
        assertEquals("no block ends in an ellipsis", 0, compose.onAllNodesWithTextCount("…"))
    }

    @Test
    fun blocksKeepEveryWordInOrder() {
        // Pure: three-line blocks of a paragraph's lines, a paragraph ending a block, blank paragraphs gone.
        val lines = { p: String -> p.split(' ').chunked(2).map { it.joinToString(" ") } }
        val blocks = descriptionBlocks("a b c d e f g h\n\n  \ni j", lines)
        assertEquals(listOf("a b\nc d\ne f", "g h", "i j"), blocks)
        assertEquals(emptyList<String>(), descriptionBlocks("  \n", lines))
    }
}

private fun ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

private fun ComposeContentTestRule.onAllNodesWithTextCount(text: String): Int =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().size

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GameInfoScrapedFit768Test : GameInfoScrapedFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class GameInfoScrapedFit576Test : GameInfoScrapedFitBase(576, 432)
