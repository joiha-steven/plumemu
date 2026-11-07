package com.plumemu.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.plumemu.coredata.db.AddedSession
import com.plumemu.coredata.settings.SortGamesSetting
import com.plumemu.coreui.testing.ReferenceShots
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.Beetle
import com.plumemu.game.CORE_NO_KEY
import com.plumemu.game.CORE_YES_KEY
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.GameMenuEntryId
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.coreQuestionMessage
import com.plumemu.game.sampleFacts
import com.plumemu.graph.CoreQuestion
import com.plumemu.launcher.choice.CoreToConfirm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil

/**
 * The game panel's raised card (the menu and the core question) at the three reference sizes, Medium and Largest text: nothing cut, the
 * question's buttons inside the card and side by side with Yes at the right, the card above the hint bar, the menu's real values shown.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GameCardBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val game = GameRef("snes-6", "Super Metroid")
    private val added = AddedSession(sessionId = 9, gameId = 0, endedAt = 62_000, seconds = 62, previousLastPlayedAt = null)
    private val question = CoreQuestion(1, "snes-6", "Super Metroid", Beetle.id, "RetroArch: Snes9x", CoreToConfirm("com.retroarch", "snes9x_libretro_android.so"), added)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun show(
        text: TextSize,
        panel: OpenPanel,
    ) = compose.setContent {
        PlumTheme(PlumThemeSettings.Graphite.copy(textSize = text)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
    }

    private fun hasText(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun assertCardFits(what: String) {
        val panel = bounds(GamePanelTags.PANEL)
        assertTrue("$what: the card ends above the hint bar", panel.bottom <= bounds(HomeTags.HINT_BAR).top)
        val node = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("$what: the card has text", assertTextWhole(node, what) > 0)
    }

    /**
     * Every placed text of [root] has the room all its wrapped lines need (a text that wraps is not cut; a one that ran out of lines
     * or width is) and stays inside the card sideways. [assertNoTextCut] takes a text that wraps for a cut one. Returns how many it checked.
     */
    private fun assertTextWhole(root: SemanticsNode, what: String): Int {
        var seen = 0
        val card = root.boundsInRoot
        fun visit(node: SemanticsNode) {
            val box = node.boundsInRoot
            // A row the list's edge cuts is a list that scrolls (its edge fades), not a cut text: it is whole when it scrolls into view.
            val isCutByList = node.size.height > box.height + 1f || node.size.width > box.width + 1f
            if (node.layoutInfo.isPlaced && SemanticsProperties.Text in node.config && box.width > 0f && box.height > 0f && !isCutByList) {
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val layout = layouts.first()
                val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
                val lines = ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth / box.width).toInt().coerceAtLeast(1)
                val text = node.config[SemanticsProperties.Text].joinToString()
                assertTrue("$what: '$text' needs $lines lines of $lineHeight px, has ${box.height}", box.height >= lines * lineHeight - 1f)
                assertTrue("$what: '$text' leaves the card sideways", box.left >= card.left - 1f && box.right <= card.right + 1f)
                seen++
            }
            node.children.forEach(::visit)
        }
        visit(root)
        return seen
    }

    private fun assertQuestion(what: String, text: TextSize) {
        show(text, OpenPanel(game, PanelScreen.Message(coreQuestionMessage(question))))
        assertCardFits(what)
        val panel = bounds(GamePanelTags.PANEL)
        val yes = bounds(GamePanelTags.row(CORE_YES_KEY))
        val no = bounds(GamePanelTags.row(CORE_NO_KEY))
        assertTrue("$what: No is left of Yes", no.right <= yes.left)
        assertEquals("$what: same line", yes.top.value, no.top.value, 0.5f)
        assertTrue("$what: buttons inside the card", no.left >= panel.left && yes.right <= panel.right && yes.bottom <= panel.bottom)
        val collarEnd = CollarSize.Control.padding.calculateEndPadding(LayoutDirection.Ltr)
        assertEquals("$what: Yes stays on the right, the rows' text inset less its collar", (PlumDimens.panelTitleStart - collarEnd).value, (panel.right - yes.right).value, 0.5f)
        assertTrue("$what: a button is a touch target", yes.bottom - yes.top >= 40.dp && no.bottom - no.top >= 40.dp)
        if (text == TextSize.Small) assertEquals("$what: the card starts under the top bar, the bar gap and its top padding", bounds(HomeTags.TOP_BAR).bottom.value + PlumDimens.barGap.value + CardDimens.padTop.value, panel.top.value, 0.5f)
        assertEquals("$what: the card hugs its content: the board's bottom padding under the buttons", yes.bottom.value + CardDimens.padBottom.value, panel.bottom.value, 1f)
        if (text == TextSize.Small) assertTrue("$what: the card is not a full-height column", panel.bottom.value < 388f - 40f)
        assertTrue("$what: the question is the card's title", hasText("Did Super Metroid start?"))
    }

    private fun assertMenu(what: String, text: TextSize) {
        val facts = sampleFacts(canFixBoxArt = true, canSort = true).copy(emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true), sort = SortGamesSetting.LastPlayed)
        show(text, OpenPanel(game, PanelScreen.Menu(facts)))
        assertCardFits(what)
        assertTrue("$what: Play with… shows the emulator", hasText("RetroArch (Snes9x)"))
        assertTrue("$what: Sort by… shows the sort in use", hasText("Last played"))
        if (text == TextSize.Small) assertEveryLabelIsOneLine(what, GameMenuEntryId.entries.map { it.name }.filter { bounds(GamePanelTags.PANEL).let { _ -> hasRow(it) } })
    }

    private fun hasRow(key: String) = compose.onAllNodesWithTag(GamePanelTags.row(key), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** A label never wraps while it fits on one line: the first text of each row (its label) is one line high, whatever its value takes. */
    private fun assertEveryLabelIsOneLine(what: String, keys: List<String>) {
        assertTrue("$what: rows to check", keys.size >= 5)
        for (key in keys) {
            val row = compose.onNodeWithTag(GamePanelTags.row(key), useUnmergedTree = true).fetchSemanticsNode()
            val label = row.children.first { SemanticsProperties.Text in it.config }
            val layouts = mutableListOf<TextLayoutResult>()
            label.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val oneLine = layouts.first().getLineBottom(0) - layouts.first().getLineTop(0)
            assertTrue("$what: '${label.config[SemanticsProperties.Text].joinToString()}' wraps (${label.boundsInRoot.height} > $oneLine px)", label.boundsInRoot.height <= oneLine + 1f)
        }
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun questionAt768() = assertQuestion("768", TextSize.Small)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun questionAt576() = assertQuestion("576", TextSize.Small)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun questionAt832() = assertQuestion("832", TextSize.Small)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun questionAt768Large() = assertQuestion("768 large", TextSize.Large)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun questionAt576Large() = assertQuestion("576 large", TextSize.Large)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun questionAt832Large() = assertQuestion("832 large", TextSize.Large)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun questionAt768Largest() = assertQuestion("768 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun questionAt576Largest() = assertQuestion("576 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun questionAt832Largest() = assertQuestion("832 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun menuAt768() = assertMenu("768", TextSize.Small)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun menuAt576() = assertMenu("576", TextSize.Small)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun menuAt832() = assertMenu("832", TextSize.Small)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun menuAt768Largest() = assertMenu("768 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun menuAt576Largest() = assertMenu("576 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun menuAt832Largest() = assertMenu("832 largest", TextSize.Largest)

    /** Walks the focus down every menu row: the focused row is whole (not cut by the list) and inside the card, card padding included. */
    private fun assertFocusedRowAlwaysInsideCard(what: String, text: TextSize) {
        val facts = sampleFacts(canFixBoxArt = true, canSort = true).copy(emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true), sort = SortGamesSetting.Name)
        val open = mutableStateOf(OpenPanel(game, PanelScreen.Menu(facts)))
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = text)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = open.value) }
        }
        val rows = open.value.content.focusableRows
        assertTrue("$what: rows to walk", rows.size >= 5)
        for (index in rows.indices) {
            open.value = OpenPanel(game, PanelScreen.Menu(facts), focus = index)
            compose.waitForIdle()
            val card = bounds(GamePanelTags.PANEL)
            val row = compose.onNodeWithTag(GamePanelTags.row(rows[index].key), useUnmergedTree = true)
            val whole = row.getUnclippedBoundsInRoot()
            val seen = row.fetchSemanticsNode().boundsInRoot
            assertTrue("$what: row $index '${rows[index].key}' is cut by the list (${seen.height} of ${whole.bottom.value - whole.top.value})", seen.height >= (whole.bottom.value - whole.top.value) * compose.density.density - 1f)
            assertTrue("$what: row $index '${rows[index].key}' ${whole.bottom} ends below the card ${card.bottom} less its padding", whole.bottom.value <= card.bottom.value - CardDimens.padBottom.value + 0.5f)
            assertTrue("$what: row $index '${rows[index].key}' starts above the card", whole.top >= card.top)
        }
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun everyFocusedRowIsInsideTheCardAt768Largest() = assertFocusedRowAlwaysInsideCard("768 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun everyFocusedRowIsInsideTheCardAt576Largest() = assertFocusedRowAlwaysInsideCard("576 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun everyFocusedRowIsInsideTheCardAt832Largest() = assertFocusedRowAlwaysInsideCard("832 largest", TextSize.Largest)

    private fun menuFacts() = sampleFacts(canFixBoxArt = true, canSort = true).copy(emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true), sort = SortGamesSetting.Name)

    private fun allTexts(root: SemanticsNode): List<SemanticsNode> =
        (if (SemanticsProperties.Text in root.config && root.layoutInfo.isPlaced) listOf(root) else emptyList()) + root.children.flatMap(::allTexts)

    /** A menu scrolled to its end draws no row under the card: below its bottom edge no pixel is lighter than the background (a shadow only darkens). */
    private fun assertNoRowBelowTheCard(what: String, text: TextSize, fromEnd: Int) {
        val rows = OpenPanel(game, PanelScreen.Menu(menuFacts())).content.focusableRows
        show(text, OpenPanel(game, PanelScreen.Menu(menuFacts()), focus = rows.size - fromEnd))
        compose.waitForIdle()
        val d = compose.density.density
        val card = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val top = (card.bottom + 3f * d).toInt()
        val hintTop = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val bottom = minOf((card.bottom + 30f * d).toInt(), (hintTop - 4f * d).toInt())
        val bg = shot[card.left.toInt() + 2, bottom - 1]
        var odd = 0
        for (y in top until bottom) {
            for (x in (card.left.toInt() + 2) until (card.right.toInt() - 2)) {
                val c = shot[x, y]
                if (c.red > bg.red + 2f / 255f || c.green > bg.green + 2f / 255f || c.blue > bg.blue + 2f / 255f) odd++
            }
        }
        assertEquals("$what: pixels under the card lighter than the background", 0, odd)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun noRowBelowTheCardOnTheSecondToLastAt768() = assertNoRowBelowTheCard("768 second to last", TextSize.Small, 2)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun noRowBelowTheCardOnTheSecondToLastAt576() = assertNoRowBelowTheCard("576 second to last", TextSize.Small, 2)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun noRowBelowTheCardOnTheLastAt768() = assertNoRowBelowTheCard("768 last", TextSize.Small, 1)

    /** At the last row, the list starts below the title and one-line rows keep one pitch (a cut row's hiding is GamePanelWholeRowsTest's). */
    private fun assertListTopAndPitch(what: String, text: TextSize) {
        val facts = menuFacts()
        val rows = OpenPanel(game, PanelScreen.Menu(facts)).content.focusableRows
        show(text, OpenPanel(game, PanelScreen.Menu(facts), focus = rows.size - 1))
        compose.waitForIdle()
        val d = compose.density.density
        val listTop = compose.onNodeWithTag(GamePanelTags.ROWS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        val title = compose.onNodeWithTag(GamePanelTags.TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("$what: the list starts below the title", listTop >= title.bottom - 1f)
        val rowNodes = rows.map { compose.onAllNodesWithTag(GamePanelTags.row(it.key), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() }
        // The sliver check that stood here read layout bounds. A cut row is now hidden whole at draw time, so the pixels decide: GamePanelWholeRowsTest (every focus, all sizes).
        val whole = rowNodes.filterNotNull().filter { it.size.height <= it.boundsInRoot.height + 1f }
        val lines = whole.filter { allTexts(it).size == 1 }
        for ((a, b) in lines.zipWithNext()) {
            if (rowNodes.indexOf(b) - rowNodes.indexOf(a) != 1) continue
            assertEquals("$what: one-line rows are the same height", a.size.height.toFloat(), b.size.height.toFloat(), 1f)
            assertEquals("$what: one-line rows keep one pitch", a.size.height + PlumDimens.panelRowGap.value * d, b.boundsInRoot.top - a.boundsInRoot.top, 1f)
        }
        assertTrue("$what: one-line rows to compare", lines.size >= 2)
    }

    /** The board's menu card: title 24 dp and row text 27 dp from the card's edge (the card is the panel and its 2.67 dp inset), rows 40 dp apart. */
    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun menuInsetsAndPitchAreTheBoardsAt768() {
        val facts = menuFacts()
        show(TextSize.Small, OpenPanel(game, PanelScreen.Menu(facts)))
        compose.waitForIdle()
        val d = compose.density.density
        val card = bounds(GamePanelTags.CARD).left.value
        fun node(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        val title = node(GamePanelTags.TITLE).boundsInRoot.left / d - card
        assertEquals("the title is 24 dp from the card's edge", 24f - 0.67f, title, 1f)
        val slab = node(GamePanelTags.ROWS).boundsInRoot.left / d - card
        assertEquals("the rows (and their slab) start 2.7 dp from the card's edge", 2.67f, slab, 0.5f)
        val rows = OpenPanel(game, PanelScreen.Menu(facts)).content.focusableRows.map { node(GamePanelTags.row(it.key)) }
        val (a, b) = rows.zipWithNext().first { (x, y) -> allTexts(x).size == 1 && allTexts(y).size == 1 }
        val pitch = (b.boundsInRoot.top - a.boundsInRoot.top) / d
        assertEquals("plain rows are 40 dp apart", 40f, pitch, 1f)
        val text = allTexts(a).first().boundsInRoot.left / d - card
        assertEquals("row text is the title's start from the card's edge", 23.33f, text, 1f)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun listTopAndPitchAt768Largest() = assertListTopAndPitch("768 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun listTopAndPitchAt576Largest() = assertListTopAndPitch("576 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun listTopAndPitchAt832Largest() = assertListTopAndPitch("832 largest", TextSize.Largest)

    /**
     * Just outside the card's rounded bottom corners, while the menu scrolls (rows continue past its bottom), no pixel is lighter than the
     * page beside the card at the same height: the fade in the card's fill stays inside the card's arc.
     */
    private fun assertNothingPastTheBottomCorners(what: String, text: TextSize) {
        show(text, OpenPanel(game, PanelScreen.Menu(menuFacts())))
        compose.waitForIdle()
        assertTrue("$what: the menu scrolls, so rows continue past its bottom (the chevron's tag is there)", compose.onAllNodesWithTag(GamePanelTags.FADE_BOTTOM, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val d = compose.density.density
        val card = compose.onNodeWithTag(GamePanelTags.CARD, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val r = CardDimens.corner.value * d
        val left = card.left
        val right = card.right
        val bottom = card.bottom
        val margin = 1.5f * d
        var odd = 0
        var worst = ""
        for ((cx, side) in listOf((left + r) to -1, (right - r) to 1)) {
            val cy = bottom - r
            val besideX = (if (side < 0) left - 2f * d else right + 2f * d).toInt()
            for (y in cy.toInt() until bottom.toInt()) {
                val page = shot[besideX, y]
                for (x in (cx - r).toInt().coerceAtLeast(0) until (cx + r).toInt()) {
                    if ((x - cx) * side < 0) continue
                    val dx = x + 0.5f - cx
                    val dy = y + 0.5f - cy
                    if (kotlin.math.sqrt(dx * dx + dy * dy) < r + margin) continue
                    val c = shot[x, y]
                    if (c.red > page.red + 3f / 255f || c.green > page.green + 3f / 255f || c.blue > page.blue + 3f / 255f) {
                        odd++
                        if (worst.isEmpty()) worst = "at ($x, $y): $c, page $page"
                    }
                }
            }
        }
        assertEquals("$what: pixels past the card's bottom corners lighter than the page ($worst)", 0, odd)
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun nothingPastTheBottomCornersAt576Largest() = assertNothingPastTheBottomCorners("576 largest", TextSize.Largest)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun nothingPastTheBottomCornersAt768() = assertNothingPastTheBottomCorners("768", TextSize.Small)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun nothingPastTheBottomCornersAt832Largest() = assertNothingPastTheBottomCorners("832 largest", TextSize.Largest)
}
