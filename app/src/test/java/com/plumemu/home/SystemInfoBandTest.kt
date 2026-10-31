package com.plumemu.home

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.Action
import com.plumemu.coreui.components.SYSTEM_BADGE_TAG
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The band over a system's games list: the list's first item, there
 * whichever column is focused and scrolling away with the rows; never with Settings › Home screen › System info off, for Recent, Favorites or Apps, or for a system
 * without info.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SystemInfoBandTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private lateinit var state: StripState
    private lateinit var layout: PlumLayout
    private val sent = mutableListOf<Action>()

    /** What the screen shows now; changing it rebuilds the screen (a compose rule hosts one content per test). */
    private class Setup(
        val actions: List<Action>,
        val content: HomeContent,
        val showSystemInfo: Boolean,
        val settings: PlumThemeSettings,
    )

    private var current by mutableStateOf<Setup?>(null)
    private var started = false

    private fun show(
        actions: List<Action> = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)),
        content: HomeContent = FakeHomeContent.board().withSystemInfo(),
        showSystemInfo: Boolean = true,
        settings: PlumThemeSettings = PlumThemeSettings.Graphite,
    ) {
        current = Setup(actions, content, showSystemInfo, settings)
        if (!started) {
            started = true
            compose.setContent {
                current?.let { c ->
                    key(c) {
                        PlumTheme(c.settings) {
                            layout = PlumTheme.layout
                            HomeAt(c.content, c.actions, onAction = { sent += it; state.dispatch(it) }, onState = { state = it }, showSystemInfo = c.showSystemInfo)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun press(action: Action) {
        compose.runOnIdle { state.dispatch(action) }
        compose.waitForIdle()
    }

    private fun bandCount() = compose.onAllNodesWithTag(HomeTags.SYSTEM_BAND, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun factsText(): String? =
        compose.onAllNodesWithTag(HomeTags.SYSTEM_BAND_FACTS, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun firstRowTop() = compose.onNodeWithTag(HomeTags.game(0), useUnmergedTree = true).getUnclippedBoundsInRoot().top

    @Test
    fun theBandShowsTheFactsWhileTheSystemsColumnIsFocused() {
        show()
        assertEquals(1, bandCount())
        assertEquals("Nintendo · Home console · 1990–2003", factsText())
    }

    @Test
    fun theBandIsAlwaysThereSoEnterAndBackMoveNothing() {
        show()
        val before = firstRowTop()
        val band = bounds(HomeTags.SYSTEM_BAND).top
        press(Action.Enter)
        assertEquals("the band stays with the games focused", 1, bandCount())
        assertEquals("no row moved on Enter", before.value, firstRowTop().value, 0.1f)
        assertEquals("the band did not move on Enter", band.value, bounds(HomeTags.SYSTEM_BAND).top.value, 0.1f)
        press(Action.Back)
        assertEquals(1, bandCount())
        assertEquals("no row moved on Back", before.value, firstRowTop().value, 0.1f)
    }

    /** The shown page's list (the page node is the lazy list itself). */
    private fun listBounds(row: Int) =
        compose.onNode(hasTestTag(HomeTags.GAMES_PAGE) and hasAnyDescendant(hasTestTag(HomeTags.game(row))), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private val textSizes = listOf(TextSize.Medium, TextSize.Largest)

    private fun atEachSize(actions: List<Action>, check: (String) -> Unit) {
        for (size in textSizes) {
            show(actions = actions, settings = PlumThemeSettings.Graphite.copy(textSize = size))
            check("$size")
        }
    }

    /** The list left deep: the remembered row is whole inside the LIST's bounds, with the systems column focused and after Enter. */
    @Test
    fun aDeepRememberedRowIsFullyInsideTheListAtEachTextSize() {
        atEachSize(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 7), Action.Back)) { at ->
            fun check(where: String) {
                val list = listBounds(7)
                val row = bounds(HomeTags.game(7))
                assertTrue("$at $where: row ${row.top}..${row.bottom} inside list ${list.top}..${list.bottom}", row.top >= list.top && row.bottom <= list.bottom)
            }
            check("systems focused")
            press(Action.Enter)
            check("games focused")
            repeat(3) { press(Action.Back); check("back $it"); press(Action.Enter); check("enter $it") }
        }
    }

    @Test
    fun scrollingDownMovesTheBandOffTheTopTogetherWithTheRows() {
        atEachSize(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 0))) { at ->
            val gap = bounds(HomeTags.SYSTEM_BAND).top - bounds(HomeTags.game(1)).top
            repeat(9) {
                press(Action.Move(Direction.Down))
                if (bandCount() == 1) {
                    assertEquals("$at: band and rows move together", gap.value, (bounds(HomeTags.SYSTEM_BAND).top - bounds(HomeTags.game(1)).top).value, 0.1f)
                }
            }
            val list = listBounds(9)
            assertTrue("$at: the band is off the top", bandCount() == 0 || bounds(HomeTags.SYSTEM_BAND).bottom <= list.top)
        }
    }

    @Test
    fun atTheFirstRowTheBandIsFullyVisibleAtTheTopOfTheList() {
        atEachSize(listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 0))) { at ->
            val top = bounds(HomeTags.SYSTEM_BAND).top
            val first = firstRowTop()
            repeat(9) { press(Action.Move(Direction.Down)) }
            repeat(9) { press(Action.Move(Direction.Up)) }
            assertEquals("$at: focus is on the first row", 0, state.focus.row)
            assertEquals("$at: band back at the top", top.value, bounds(HomeTags.SYSTEM_BAND).top.value, 0.1f)
            assertEquals("$at: rows back where they were", first.value, firstRowTop().value, 0.1f)
            val list = listBounds(0)
            assertTrue("$at: band whole in the list", bounds(HomeTags.SYSTEM_BAND).top >= list.top && bounds(HomeTags.SYSTEM_BAND).bottom <= list.bottom)
        }
    }

    /** The remembered row at the bottom of the list: Enter scrolls it into view once, and then Back and Enter again move nothing (no drift per round trip). */
    @Test
    fun aRoundTripWithTheRememberedRowAtTheBottomDoesNotDrift() {
        show(actions = listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 7), Action.Back))
        press(Action.Enter)
        val entered = firstRowTop()
        press(Action.Back)
        val back = firstRowTop()
        repeat(3) {
            press(Action.Enter)
            assertEquals("Enter, round $it", entered.value, firstRowTop().value, 0.1f)
            press(Action.Back)
            assertEquals("Back, round $it", back.value, firstRowTop().value, 0.1f)
        }
    }

    /** A real tap on a row while the band shows: the focus goes to that game, the row stays under the finger, and a second tap there is that same game's. */
    @Test
    fun aTapOnARowKeepsItUnderTheFingerSoTheSecondTapIsTheSameGame() {
        show()
        val row = compose.onNodeWithTag(HomeTags.game(2), useUnmergedTree = true)
        val top = row.getUnclippedBoundsInRoot().top
        row.performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("the first tap focused game 2", 2, state.focus.row)
        assertEquals(Column.Games, state.focus.column)
        assertEquals("the row did not move", top.value, compose.onNodeWithTag(HomeTags.game(2), useUnmergedTree = true).getUnclippedBoundsInRoot().top.value, 0.1f)
        sent.clear()
        compose.onNodeWithTag(HomeTags.game(2), useUnmergedTree = true).performTouchInput { click() }
        compose.waitForIdle()
        assertEquals("the second tap landed on the same game", listOf<Action>(Action.TapItem(Column.Games, 2)), sent)
        assertEquals(2, state.focus.row)
    }

    @Test
    fun theSettingOffGivesTodaysLayout() {
        show(showSystemInfo = false)
        assertEquals(0, bandCount())
        val off = firstRowTop()
        show(content = FakeHomeContent.board())
        assertEquals("the same rows as a build without the band", firstRowTop(), off)
    }

    @Test
    fun aSystemWithNoFactsShowsTheBadgeAlone() {
        show(actions = listOf(Action.TapItem(Column.Systems, systemIndex(BOARD_ARCADE_ID))))
        assertEquals(1, bandCount())
        assertEquals(null, factsText())
    }

    @Test
    fun theFixedEntriesAndUnknownSystemsHaveNoBand() {
        show(actions = listOf(Action.TapItem(Column.Systems, 0)))
        assertEquals("Recent", 0, bandCount())
        show(actions = listOf(Action.TapItem(Column.Systems, 2)))
        assertEquals("Apps", 0, bandCount())
        show(actions = listOf(Action.TapItem(Column.Systems, systemIndex("psx"))))
        assertEquals("a system without info", 0, bandCount())
    }

    @Test
    fun aSystemStillMadeShowsPresent() {
        show(actions = listOf(Action.TapItem(Column.Systems, systemIndex(BOARD_SWITCH_ID))))
        assertEquals("Nintendo · Home console · 2017–present", factsText())
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theLineNeverWrapsAndStaysInsideTheRightMargin() {
        for (size in listOf(TextSize.Medium, TextSize.Largest)) {
            show(settings = PlumThemeSettings.Graphite.copy(textSize = size))
            val line = factsText()!!
            assertTrue("$size: $line is one of the fallbacks", line in SnesInfo.factsIn())
            val bounds = compose.onNodeWithTag(HomeTags.SYSTEM_BAND_FACTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val facts = compose.onNodeWithTag(HomeTags.SYSTEM_BAND_FACTS, useUnmergedTree = true).fetchSemanticsNode()
            val lines = facts.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { read ->
                val out = mutableListOf<TextLayoutResult>()
                if (read(out)) out.single().lineCount else null
            }
            assertEquals("$size: one line", 1, lines)
            val limit = layout.width - PlumDimens.stripMarginSide
            assertTrue("$size: right edge ${bounds.right} within $limit", bounds.right <= limit)
        }
    }

    @Test
    fun theFirstLineThatFitsIsChosenAndTheLastIsTheFallback() {
        val lines = SnesInfo.factsIn()
        assertEquals(lines[0], firstThatFits(lines) { true })
        assertEquals(lines[1], firstThatFits(lines) { it.length < lines[0].length })
        assertEquals(lines[2], firstThatFits(lines) { it.length < lines[1].length })
        assertEquals("nothing fits: the shortest, never wrapped", lines[2], firstThatFits(lines) { false })
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** The numbers the design's Layout gives, at the three sizes and both ends of the text scale (done in place of the web-page measuring tool: this is an Android screen). */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun layoutNumbersAt768() = checkLayout()

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun layoutNumbersAt576() = checkLayout()

    @Test
    @Config(qualifiers = "w837dp-h471dp-land-xhdpi")
    fun layoutNumbersAt837() = checkLayout()

    private fun checkLayout() {
        for (size in listOf(TextSize.Medium, TextSize.Largest)) {
            for (preset in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
                show(settings = preset.copy(textSize = size))
                val at = "${layout.width} $size"
                val badge = bounds(SYSTEM_BADGE_TAG)
                val facts = bounds(HomeTags.SYSTEM_BAND_FACTS)
                val rule = bounds(HomeTags.SYSTEM_BAND_RULE)
                val band = bounds(HomeTags.SYSTEM_BAND)
                val tolerance = 0.6f
                assertTrue("$at: badge at least 72 wide (${badge.width})", badge.width.value >= 72f - tolerance)
                assertTrue("$at: badge at least 48 high (${badge.height})", badge.height.value >= 48f - tolerance)
                assertEquals("$at: facts 14 right of the badge", 14f, (facts.left - badge.right).value, tolerance)
                assertEquals("$at: facts centred on the badge", (badge.top + badge.bottom).value / 2, (facts.top + facts.bottom).value / 2, tolerance)
                assertEquals("$at: badge at the band's left edge", band.left.value, badge.left.value, tolerance)
                assertEquals("$at: hairline 8 under the badge", 8f, (rule.top - badge.bottom).value, tolerance)
                assertEquals("$at: hairline is 1 high", 1f, rule.height.value, tolerance)
                assertEquals("$at: the hairline spans the list's width", band.width.value, rule.width.value, tolerance)
                val firstRow = bounds(HomeTags.game(0))
                assertEquals("$at: the rows start under the hairline", rule.bottom.value, firstRow.top.value, tolerance)
                assertTrue("$at: the rows start below the hairline", firstRow.top >= rule.bottom)
                assertEquals("$at: the band starts where the rows do", firstRow.left.value, band.left.value, tolerance)
            }
        }
    }

    private fun textOf(tag: String): String? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }

    private fun linesOf(tag: String): Int {
        val out = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.single().lineCount
    }

    @Test
    fun aSystemWithoutAboutTextHasNoAboutLine() {
        show()
        assertEquals(null, textOf(HomeTags.SYSTEM_BAND_ABOUT))
    }

    @Test
    fun arcadeShowsItsManyMakersFactsAndItsAbout() {
        show(content = FakeHomeContent.board().withArcadeAbout(), actions = listOf(Action.TapItem(Column.Systems, systemIndex(BOARD_ARCADE_ID))))
        assertEquals("Arcade games from many makers", factsText())
        assertTrue(textOf(HomeTags.SYSTEM_BAND_ABOUT)!!.startsWith("Atari"))
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theAboutTextSitsUnderTheFactsInThreeLinesAtMostAndTheBandGrowsToHoldIt() {
        for (size in listOf(TextSize.Medium, TextSize.Largest)) {
            for (preset in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
                show(content = FakeHomeContent.board().withSnesAbout(), settings = preset.copy(textSize = size))
                val at = "$size"
                val badge = bounds(SYSTEM_BADGE_TAG)
                val facts = bounds(HomeTags.SYSTEM_BAND_FACTS)
                if (size == TextSize.Largest && textOf(HomeTags.SYSTEM_BAND_ABOUT) == null) continue // too long for three lines here: left out whole
                val about = bounds(HomeTags.SYSTEM_BAND_ABOUT)
                val rule = bounds(HomeTags.SYSTEM_BAND_RULE)
                assertEquals("$at: the text", "The day before launch, Nintendo loaded 100 trucks with boxes of Super Famicom consoles. Mode 7 simulated 3D perspective.", textOf(HomeTags.SYSTEM_BAND_ABOUT))
                assertTrue("$at: at most three lines", linesOf(HomeTags.SYSTEM_BAND_ABOUT) <= 3)
                assertEquals("$at: 4 under the facts line", 4f, (about.top - facts.bottom).value, 0.6f)
                assertEquals("$at: same left edge as the facts", facts.left.value, about.left.value, 0.6f)
                assertEquals("$at: facts and about are centred on the badge", (badge.top + badge.bottom).value / 2, (facts.top + about.bottom).value / 2, 0.6f)
                assertTrue("$at: inside the right margin", about.right.value <= layout.width.value - PlumDimens.stripMarginSide.value + 0.6f)
                assertEquals("$at: hairline 8 under the tallest of badge and text", 8f, (rule.top - maxOf(badge.bottom, about.bottom)).value, 0.6f)
                assertEquals("$at: the rows start under the hairline", rule.bottom.value, bounds(HomeTags.game(0)).top.value, 0.6f)
            }
        }
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun noAboutTextIsEverCutWhateverTheSystemOrTheTextSize() {
        var shown = 0
        for (size in listOf(TextSize.Medium, TextSize.Largest)) {
            for (id in SystemAbouts.ids) {
                val content = FakeHomeContent.board().let { board -> board.withEntries(board.entries.map { if (it.id == "snes") it.copy(info = SnesInfo.copy(about = SystemAbouts.of(id))) else it }) }
                show(content = content, settings = PlumThemeSettings.Graphite.copy(textSize = size))
                val text = textOf(HomeTags.SYSTEM_BAND_ABOUT) ?: continue
                shown++
                val out = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag(HomeTags.SYSTEM_BAND_ABOUT, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
                assertTrue("$size $id: at most three lines", out.single().lineCount <= 3)
                assertTrue("$size $id: not cut", !out.single().hasVisualOverflow && !text.endsWith("…"))
            }
        }
        assertTrue("some about texts were shown", shown > 0)
    }

    private fun systemIndex(id: String) = FakeHomeContent.board().entries.indexOfFirst { it.id == id }
}
