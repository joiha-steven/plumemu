package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SecondScreenDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.FakeCoverImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.assertNoTextCut
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.PlayStatistics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The statistics card measured: inside its margins and on the bottom margin at every text
 * size and in every font at Largest; no text cut but a game's title (with "…", in its lines); the
 * times and counts whole; lines going in the documented order when the height runs out, never
 * overflowing; covers and the box-art line only with box art set up.
 */
abstract class SecondScreenStatisticsCardFitBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(MediumText.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = FakeCoverImages()) } }
    }

    private fun show(
        statistics: PlayStatistics,
        settings: PlumThemeSettings = MediumText.Default,
    ) {
        this.settings = settings
        this.content = SecondScreenContent.Statistics(statistics)
        compose.waitForIdle()
    }

    private val dimens get() = if (size == SecondSize.Roomy) SecondScreenDimens.Roomy else SecondScreenDimens.Compact

    private fun px(dp: Dp) = dp.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density

    private fun all(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)

    private fun placed(tag: String) = all(tag).filter { it.layoutInfo.isPlaced }

    private fun layoutOf(node: SemanticsNode): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        return layouts.single()
    }

    private fun textOf(node: SemanticsNode) = node.config[SemanticsProperties.Text].joinToString("")

    /** The skippable lines [statistics] has at all, lowest priority first. */
    private fun droppableOf(statistics: PlayStatistics): List<String> =
        buildList {
            if (statistics.hasBoxArt && statistics.libraryGames > 0) add(SecondScreenTags.STATISTICS_BOX_ART)
            if (statistics.libraryGames > 0) add(SecondScreenTags.STATISTICS_PLAYED)
            if (statistics.hasPlayed) {
                if (statistics.topSystem != null) add(SecondScreenTags.STATISTICS_SYSTEM)
                for (rank in statistics.topGames.size downTo 1) add(SecondScreenTags.statisticsGame(rank))
            }
        }

    /** What [statistics] shows now: every part inside the margins, the block on the bottom margin, nothing cut but titles, the skipped lines returned. */
    private fun assertFits(
        label: String,
        statistics: PlayStatistics,
    ): List<String> {
        val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
        val margin = px(dimens.margin)
        val block = compose.onNodeWithTag(SecondScreenTags.STATISTICS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("$label: the block on the bottom margin", card.bottom - margin, block.bottom, 0.5f)
        assertEquals("$label: the block on the left margin", card.left + margin, block.left, 0.5f)
        assertTrue("$label: the block's top ${block.top} inside the top margin", block.top >= card.top + margin - 0.5f)
        // Every placed text of the block: inside the margins, and cut only where it is a game's title.
        var texts = 0
        fun visit(node: SemanticsNode) {
            if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config) {
                texts++
                val b = node.boundsInRoot
                val what = "$label: '${textOf(node)}'"
                assertTrue("$what left ${b.left}", b.left >= card.left + margin - 0.5f)
                assertTrue("$what right ${b.right}", b.right <= card.right - margin + 0.5f)
                assertTrue("$what top ${b.top}", b.top >= card.top + margin - 0.5f)
                assertTrue("$what bottom ${b.bottom}", b.bottom <= card.bottom - margin + 0.5f)
                val layout = layoutOf(node)
                val isTitle = SemanticsProperties.TestTag in node.config && node.config[SemanticsProperties.TestTag] == SecondScreenTags.STATISTICS_GAME_TITLE
                if (isTitle) {
                    assertTrue("$what: ${layout.lineCount} title lines", layout.lineCount <= dimens.statisticsTitleMaxLines)
                } else {
                    assertFalse("$what is cut with an ellipsis", layout.isLineEllipsized(layout.lineCount - 1))
                    // Its lines all show: the node is as tall as its lines.
                    assertTrue("$what: lines end at ${layout.size.height} px in a ${b.height} px box", layout.size.height <= b.height + 1f)
                }
            }
            node.children.forEach(::visit)
        }
        visit(compose.onNodeWithTag(SecondScreenTags.STATISTICS, useUnmergedTree = true).fetchSemanticsNode())
        assertTrue("$label: texts seen", texts > 0)
        // Times and counts are whole and on one line.
        for (figure in placed(SecondScreenTags.STATISTICS_FIGURE) + placed(SecondScreenTags.STATISTICS_TOTAL)) assertNoTextCut(figure, "$label figure")
        // The top line and the total (or the calm line) always stay.
        assertEquals("$label: the top line", 1, placed(SecondScreenTags.TOP).size)
        val head = if (statistics.hasPlayed) SecondScreenTags.STATISTICS_TOTAL else SecondScreenTags.STATISTICS_NOTHING_PLAYED
        assertEquals("$label: $head", 1, placed(head).size)
        // A line that does not fit is skipped, the lower ones are still tried (`keptParts`, tested on its own):
        // here only what the card shows is checked, as the list of lines it skipped.
        return droppableOf(statistics).filter { placed(it).isEmpty() }
    }

    @Test
    fun everyCaseFitsAtEveryTextSize() {
        val report = StringBuilder()
        for (textSize in TextSize.entries) {
            for ((name, each) in SecondScreenStatisticsSamples.cases) {
                show(each, PlumThemeSettings.Default.copy(textSize = textSize))
                val dropped = assertFits("$size $textSize $name", each)
                report.append("$size $textSize $name: skipped $dropped\n")
            }
        }
        println(report)
    }

    @Test
    fun aSystemLineThatDoesNotFitIsSkippedAndGamesPlayedStillShowsBelowIt() {
        // A system name longer than the samples' since the type ramp: at 16 (was 17) their three-line
        // name fits on Roomy at Larger, so it no longer exercised the skip.
        val longNames = SecondScreenStatisticsSamples.longNames
        val longerSystem = longNames.copy(topSystem = longNames.topSystem?.copy(name = longNames.topSystem?.name + " with the Famicom Disk System"))
        show(longerSystem, PlumThemeSettings.Default.copy(textSize = TextSize.Larger))
        assertFits("$size Larger long names", longerSystem)
        assertTrue("the long system line did not fit", placed(SecondScreenTags.STATISTICS_SYSTEM).isEmpty())
        assertEquals("Games played shows", 1, placed(SecondScreenTags.STATISTICS_PLAYED).size)
        assertEquals("so does the box-art line", 1, placed(SecondScreenTags.STATISTICS_BOX_ART).size)
    }

    @Test
    fun theLargestTextFitsInEveryFont() {
        for (font in PlumFont.entries) {
            for ((name, each) in SecondScreenStatisticsSamples.cases) {
                show(each, PlumThemeSettings.Default.copy(textSize = TextSize.Largest, font = font))
                val dropped = assertFits("$size $font $name", each)
                println("$size Largest $font $name: skipped $dropped")
            }
        }
    }

    @Test
    fun atMediumEveryLineShows() {
        // The long names (a system name of three lines) may lose lines even at Medium; the fit test above covers them.
        for ((name, each) in SecondScreenStatisticsSamples.cases.filter { it.first != "long names" }) {
            for (withSessions in listOf(false, true)) {
                val figures = each.copy(hasSessions = withSessions)
                show(figures)
                assertEquals("$size $name (sessions $withSessions): nothing dropped at Medium", emptyList<String>(), assertFits("$size Medium $name", figures))
            }
        }
    }

    @Test
    fun theGapsAreTheCardsOwnAndEven() {
        show(SecondScreenStatisticsSamples.withCovers)
        fun box(tag: String) = placed(tag).single().boundsInRoot
        val order =
            listOf(
                SecondScreenTags.TOP to 0.dp,
                SecondScreenTags.STATISTICS_TOTAL to SecondScreenDimens.topToTitleGap,
                SecondScreenTags.STATISTICS_TOTAL_LABEL to SecondScreenDimens.statisticsLabelGap,
                SecondScreenTags.statisticsGame(1) to dimens.statisticsGroupGap,
                SecondScreenTags.statisticsGame(2) to dimens.statisticsRowGap,
                SecondScreenTags.statisticsGame(3) to dimens.statisticsRowGap,
                SecondScreenTags.STATISTICS_SYSTEM to dimens.statisticsGroupGap,
                SecondScreenTags.STATISTICS_PLAYED to dimens.statisticsRowGap,
                SecondScreenTags.STATISTICS_BOX_ART to dimens.statisticsRowGap,
            )
        for (at in 1 until order.size) {
            val (above, _) = order[at - 1]
            val (tag, gap) = order[at]
            assertEquals("$size: $gap between $above and $tag", px(gap), box(tag).top - box(above).bottom, 1f)
        }
        // Covers and figures line up: every cover on the left margin, every figure's right edge on the right margin.
        val card = compose.onNodeWithTag(SecondScreenTags.CARD).fetchSemanticsNode().boundsInRoot
        for (cover in placed(SecondScreenTags.STATISTICS_COVER)) assertEquals("$size: cover on the left margin", card.left + px(dimens.margin), cover.boundsInRoot.left, 0.5f)
        for (figure in placed(SecondScreenTags.STATISTICS_FIGURE)) assertEquals("$size: figure on the right margin", card.right - px(dimens.margin), figure.boundsInRoot.right, 0.5f)
    }

    @Test
    fun coversAndTheBoxArtLineOnlyWithBoxArtSetUp() {
        show(SecondScreenStatisticsSamples.withCovers)
        assertEquals("a cover slot per game, the third the stand-in", 3, placed(SecondScreenTags.STATISTICS_COVER).size)
        assertEquals(1, placed(SecondScreenTags.STATISTICS_BOX_ART).size)
        show(SecondScreenStatisticsSamples.signedOut)
        assertTrue("signed out: no cover slot", all(SecondScreenTags.STATISTICS_COVER).isEmpty())
        assertTrue("signed out: no box-art line", all(SecondScreenTags.STATISTICS_BOX_ART).isEmpty())
        assertEquals("the games are still there", 3, (1..3).count { placed(SecondScreenTags.statisticsGame(it)).isNotEmpty() })
    }

    private fun totalLabels() = placed(SecondScreenTags.STATISTICS_TOTAL_LABEL).map { textOf(it) }

    @Test
    fun theWeekRidesOnTheTotalsLabelLineAtMediumAndIsDroppedNotCutAtLargest() {
        show(SecondScreenStatisticsSamples.withCovers)
        assertEquals("$size Medium", listOf("Total play time · 2 h 10 min this week"), totalLabels())
        show(SecondScreenStatisticsSamples.withCovers.copy(thisWeekSeconds = 0))
        assertEquals(listOf("Total play time · none this week"), totalLabels())
        show(SecondScreenStatisticsSamples.withCovers.copy(thisWeekSeconds = 30))
        assertEquals(listOf("Total play time · less than a minute this week"), totalLabels())
        show(SecondScreenStatisticsSamples.withCovers.copy(hasSessions = false))
        assertEquals("no session, no week", listOf("Total play time"), totalLabels())
        val fellBack = mutableListOf<String>()
        for (textSize in TextSize.entries) {
            for (font in PlumFont.entries) {
                show(SecondScreenStatisticsSamples.withCovers.copy(thisWeekSeconds = 123 * 3_600L + 45 * 60), PlumThemeSettings.Default.copy(textSize = textSize, font = font))
                val label = placed(SecondScreenTags.STATISTICS_TOTAL_LABEL).single()
                assertTrue("$size $textSize $font: ${textOf(label)}", textOf(label) in listOf("Total play time", "Total play time · 123 h 45 min this week"))
                assertEquals("one line", 1, layoutOf(label).lineCount)
                assertNoTextCut(label, "$size $textSize $font label")
                if (textOf(label) == "Total play time") fellBack += "$textSize $font"
            }
        }
        assertTrue("$size: at least one text size and font must fall back to the plain label (so the drop branch runs), none did", fellBack.isNotEmpty())
        println("$size: the week part is dropped at $fellBack")
    }

    @Test
    fun theWordsAreSettingsOwnAndVersionIsNotOnTheCard() {
        show(SecondScreenStatisticsSamples.withCovers)
        val texts = all(SecondScreenTags.STATISTICS).flatMap { node -> buildList { collectTexts(node, this) } }
        for (expected in listOf("Statistics", "23 h 10 min", "Total play time", "Super Metroid", "12 h 5 min", "55 min", "Most-played system", "19 h 45 min", "Games played", "38 of 412", "Games with box art", "301 of 412")) {
            assertTrue("$size: '$expected' in $texts", texts.any { expected in it })
        }
        assertTrue("$size: no version: $texts", texts.none { it.contains(com.plumemu.settings.screen.TEST_VERSION) })
        show(SecondScreenStatisticsSamples.nothingPlayed)
        val calm = all(SecondScreenTags.STATISTICS).flatMap { node -> buildList { collectTexts(node, this) } }
        assertTrue("$size: $calm", calm.any { "Nothing played yet" in it } && calm.none { "Total play time" in it })
        assertTrue("$size: the library lines stay: $calm", calm.any { "38 of 412" in it || "0 of 412" in it } && calm.any { "301 of 412" in it })
        assertTrue("no game rows", all(SecondScreenTags.statisticsGame(1)).isEmpty())
    }

    private fun collectTexts(
        node: SemanticsNode,
        into: MutableList<String>,
    ) {
        if (SemanticsProperties.Text in node.config) into += textOf(node)
        node.children.forEach { collectTexts(it, into) }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenStatisticsCardFitRoomyTest : SecondScreenStatisticsCardFitBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenStatisticsCardFitCompactTest : SecondScreenStatisticsCardFitBase(SecondSize.Compact)
