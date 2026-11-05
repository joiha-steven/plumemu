package com.plumemu.home

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.InputRouter
import com.plumemu.game.FakeGameActions
import com.plumemu.game.gameMenuEntries
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.game.EmulatorToStart
import com.plumemu.game.GamePanelTags
import com.plumemu.game.GameRef
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.sampleFacts
import com.plumemu.graph.AchievementCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where the focused slab sits in the game menu card, in dp, against the board's (SPEC section 22): the card's 22 / 14 px padding and its
 * 392 px row column leave the 412 px slab 3.33 dp from the card's left edge and 15.33 dp from its right; under the last row the padding is
 * 15.33 dp, and with a row after the slab that row's 40 dp comes on top. Measured with the cursor on the last entry and on the one before it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class GameMenuSlabGapsTest {
    private companion object {
        /** Rows: Play, Play with, Favorite, Sort by, Name, Box art, Achievements, Info, Hide game; the cursor starts on the first. */
        const val LAST = 8
    }

    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val games = FakeGameActions()
    private var size by mutableStateOf(TextSize.Small)
    private var shown = false
    private lateinit var router: InputRouter

    private val facts = sampleFacts(canFixBoxArt = true, achievementCount = AchievementCount(12, 40)).copy(emulator = EmulatorToStart("RetroArch (Snes9x)", isInstalled = true))

    private fun send(vararg actions: Action) =
        actions.forEach { action ->
            compose.runOnIdle { router.send(action) }
            compose.waitForIdle()
        }

    /** Opens the menu of the first Super Nintendo game and moves the cursor down [downs] rows, as a person does (the list keeps its context rows after a move). */
    private fun gaps(downs: Int, text: TextSize, look: PlumThemeSettings = PlumThemeSettings.Graphite): Triple<Float, Float, Float> {
        games.facts = { facts }
        size = text
        if (!shown) {
            shown = true
            compose.setContent {
                val scope = rememberCoroutineScope()
                router = remember { InputRouter(scope) }
                PlumTheme(look.copy(textSize = size)) { HomeScreen(FakeHomeContent.board(), router, HomeCallbacks(games = games)) }
            }
            compose.waitForIdle()
            send(*List(FakeHomeContent.BOARD_SNES_INDEX) { Action.Move(Direction.Down) }.toTypedArray(), Action.Enter, Action.Menu)
            send(*List(downs) { Action.Move(Direction.Down) }.toTypedArray())
        }
        return measureGaps(downs)
    }

    private fun measureGaps(downs: Int): Triple<Float, Float, Float> {
        compose.waitForIdle()
        val d = compose.density.density
        val column = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag(GamePanelTags.CARD, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cardLeft = card.left.value
        val cardRight = card.right.value
        val cardBottom = card.bottom.value
        // The focused row: the entries in order, Sort by… among them (a menu opened from a system's list); the cursor starts on Play.
        val keys = gameMenuEntries(facts.copy(canSort = true)).map { it.key }
        val tag = GamePanelTags.row(keys[downs])
        val row = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val overhang = compose.slabOverhangPx(tag) / d
        return Triple(row.left.value - cardLeft, cardRight - row.right.value, cardBottom - (row.bottom.value + overhang))
    }

    // At 832 the card nearly holds all nine rows: the list's "no sliver of the first row under the title" room may sit under the last one, so only the board's minimum counts there.
    private fun check(what: String, text: TextSize = TextSize.Small, exactBottom: Boolean = true) {
        val (left, right, bottom) = gaps(LAST, text)
        val wrong = listOfNotNull(
            "left $left (2.67)".takeIf { kotlin.math.abs(left - 2.67f) > 1f },
            "right $right (2.67, the same as the left)".takeIf { kotlin.math.abs(right - 2.67f) > 1f },
            "bottom $bottom (15.33, the card's padding under the last row)".takeIf { if (exactBottom) kotlin.math.abs(bottom - 15.33f) > 1f else bottom < 15.33f - 1f },
        )
        assertTrue("$what: slab to the card's edges: $wrong", wrong.isEmpty())
    }

    private fun checkSecond(what: String, text: TextSize = TextSize.Small) {
        val (left, right, bottom) = gaps(LAST - 1, text)
        val wrong = listOfNotNull(
            "left $left (2.67)".takeIf { kotlin.math.abs(left - 2.67f) > 1f },
            "right $right (2.67, the same as the left)".takeIf { kotlin.math.abs(right - 2.67f) > 1f },
            "bottom $bottom (at least 55.33: the next row and the padding)".takeIf { bottom < 55.33f - 1f },
        )
        assertTrue("$what: slab to the card's edges: $wrong", wrong.isEmpty())
    }

    /** The menu opened straight on a row (a remembered row, a tap, a size change): the same room below the slab as after a step. */
    private fun openedOn(row: Int, text: TextSize, look: PlumThemeSettings = PlumThemeSettings.Graphite) {
        val game = GameRef("snes-6", "Super Metroid")
        val panel = OpenPanel(game, PanelScreen.Menu(facts.copy(canSort = true)), focus = row)
        compose.setContent { PlumTheme(look.copy(textSize = text)) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        val (left, right, bottom) = measureGaps(row)
        val isLast = row == LAST
        val wrong = listOfNotNull(
            "left $left (2.67)".takeIf { kotlin.math.abs(left - 2.67f) > 1f },
            "right $right (2.67, the same as the left)".takeIf { kotlin.math.abs(right - 2.67f) > 1f },
            "bottom $bottom (${if (isLast) "15.33, the padding" else "at least 55.33, a row and the padding"})".takeIf { if (isLast) bottom < 14.33f else bottom < 54.33f },
        )
        assertTrue("opened on row $row: slab to the card's edges: $wrong", wrong.isEmpty())
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun openedOnSecondToLastAt768() = openedOn(LAST - 1, TextSize.Small)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun openedOnSecondToLastAt768Paper() = openedOn(LAST - 1, TextSize.Small, PlumThemeSettings.Paper)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun openedOnSecondToLastAt576() = openedOn(LAST - 1, TextSize.Small)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun openedOnSecondToLastAt576Largest() = openedOn(LAST - 1, TextSize.Largest)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun openedOnLastAt768() = openedOn(LAST, TextSize.Small)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun lastAt576() = check("576 last")

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun secondToLastAt576() = checkSecond("576 second")

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun lastAt768() = check("768 last")

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun secondToLastAt768() = checkSecond("768 second")

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun lastAt832() = check("832 last", exactBottom = false)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun secondToLastAt832() = checkSecond("832 second")

    /**
     * A row the card's bottom edge cuts fades out (design/rules.md, Lists that scroll): where a label of the cut row is in view, its ink at the last lines above the card's edge line is at most half of a fully shown label's.
     * Returns whether a label was in view (the cut is at a different place at each text size and cursor row: the cursor steps down up to three more rows to find one).
     */
    private fun assertCutRowFades(what: String, look: PlumThemeSettings, text: TextSize): Boolean {
        gaps(1, text, look)
        for (step in 0..3) {
            if (step > 0) send(Action.Move(Direction.Down))
            if (measureCutRow(what)) return true
        }
        return false
    }

    private fun measureCutRow(what: String): Boolean {
        assertTrue("$what: the menu scrolls, so its bottom fade is there", compose.onAllNodesWithTag(GamePanelTags.FADE_BOTTOM, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val keys = gameMenuEntries(facts.copy(canSort = true)).map { it.key }
        val cardBottom = compose.onNodeWithTag(GamePanelTags.PANEL, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom.toInt()
        val nodes = keys.flatMap { compose.onAllNodesWithTag(GamePanelTags.row(it), useUnmergedTree = true).fetchSemanticsNodes() }
        // Cut by the clip, or reaching past the card's edge into the list's room (SlabDimens.listEndRoom, cleared by the fade mask).
        val cut = nodes.firstOrNull { (it.size.height > it.boundsInRoot.height + 1f || it.boundsInRoot.bottom > cardBottom + 1f) && it.boundsInRoot.height > 0f && it.boundsInRoot.top < cardBottom }
        if (cut == null) return false // the list ends on a row boundary at this size: nothing is cut
        val box = cut.boundsInRoot
        val d = compose.density.density
        fun lum(x: Int, y: Int) = shot[x, y].let { it.red + it.green + it.blue }
        // Ink of one line of a row: the strongest step from the card's colour 8 dp left of the text over the label's width.
        fun ink(row: androidx.compose.ui.geometry.Rect, y: Int): Float {
            val bg = lum((row.left + 8f * d).toInt(), y)
            return ((row.left + 24f * d).toInt()..(row.left + 100f * d).toInt()).maxOf { x -> kotlin.math.abs(lum(x, y) - bg) }
        }
        // A fully shown label's ink: the strongest line over the rows wholly inside the card.
        val whole = nodes.map { it.boundsInRoot }.filter { it.height > 0f && it.bottom < cardBottom - 6f * d && it.top > 0f }
        val reference = whole.maxOfOrNull { row -> (row.top.toInt() until row.bottom.toInt()).maxOf { ink(row, it) } } ?: 0f
        // Not the card's own edge line: the last 3 dp are left out.
        val end = cardBottom - (3f * d).toInt()
        val strongest = (box.top.toInt() until end).maxOfOrNull { ink(box, it) } ?: 0f
        if (strongest < 0.5f) return false
        val last = ink(box, end - 1)
        assertTrue("$what: the cut row's ink at the card's edge is $last, a whole label's is $reference: it should fade to at most half", last <= reference * 0.5f)
        return true
    }

    // At 768 the cut can fall between labels at every size; the ink must be seen at 576 only.
    private fun assertFades(what: String, look: PlumThemeSettings, inkRequired: Boolean = true) {
        val seen = listOf(TextSize.Small, TextSize.Large, TextSize.Largest).map { size -> assertCutRowFades("$what ${size.name}", look, size) }
        if (inkRequired) assertTrue("$what: a label of the cut row was in view at one of the sizes, so the fade was measured on ink", seen.any { it })
    }

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun cutRowFadesAt576Graphite() = assertFades("576 graphite", PlumThemeSettings.Graphite)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun cutRowFadesAt576Paper() = assertFades("576 paper", PlumThemeSettings.Paper)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun cutRowFadesAt768Graphite() = assertFades("768 graphite", PlumThemeSettings.Graphite, inkRequired = false)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun cutRowFadesAt768Paper() = assertFades("768 paper", PlumThemeSettings.Paper, inkRequired = false)
}
