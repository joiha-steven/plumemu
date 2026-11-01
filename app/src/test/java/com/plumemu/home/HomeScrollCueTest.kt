package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.EdgeFadesKey
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The home screen's lists and grids show they scroll (design/rules.md › Lists that scroll): walking the focus down and
 * back up, the games list and the systems column fade at an edge exactly while they can scroll that way, a grid fades
 * only where a row is cut at that edge (never at the top while it stands on rows), and no fade ever covers the focused
 * row or cover. At 768x432 and 576x432, at several text sizes and grid sizes; each case that scrolls must exist, or
 * the test proves nothing.
 */
abstract class HomeScrollCueBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var textSize by mutableStateOf(TextSize.Medium)
    private var gridSize by mutableStateOf(GridSize.Default)
    private var edgeFadeOn by mutableStateOf(true)
    private val problems = mutableListOf<String>()

    private fun show(content: @Composable () -> Unit) =
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize, gridSize = gridSize, edgeFadeOn = edgeFadeOn)) { content() } }

    private fun send(action: Action) {
        compose.runOnIdle { checkNotNull(state).dispatch(action) }
        compose.waitForIdle()
    }

    /** The node drawn with a fade mask that [pick] chooses among all of them. */
    private fun faded(pick: (SemanticsNode) -> Boolean): SemanticsNode =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(EdgeFadesKey), useUnmergedTree = true).fetchSemanticsNodes().single(pick)

    private fun tagOf(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.TestTag)

    private fun rectPx(tag: String): Rect? {
        if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return null
        val b = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val d = compose.density.density
        return Rect(b.left.value * d, b.top.value * d, b.right.value * d, b.bottom.value * d)
    }

    /** The fades now on [node], as bands in root px: top and bottom (empty when there is none). */
    private fun bands(node: SemanticsNode): Pair<Rect, Rect> {
        val fades = node.config[EdgeFadesKey]
        val fadePx = PlumDimens.listEdgeFade.value * compose.density.density
        val box = node.boundsInRoot
        val top = fades.top(fadePx, box.height)
        val bottom = fades.bottom(fadePx, box.height)
        return Rect(box.left, box.top, box.right, box.top + top) to Rect(box.left, box.bottom - bottom, box.right, box.bottom)
    }

    private fun canScroll(
        node: SemanticsNode,
        down: Boolean,
    ): Boolean {
        val range = node.config[SemanticsProperties.VerticalScrollAxisRange]
        return if (down) range.value() < range.maxValue() else range.value() > 0f
    }

    private fun Rect.hasArea() = height > 0.5f

    private fun Rect.overlaps(other: Rect) = other.hasArea() && top < other.bottom - 0.5f && bottom > other.top + 0.5f

    /** A list: fades while it can scroll that way, and the focused item ([focusedTag]) is never under one. Returns whether it could scroll down. */
    private fun checkList(
        label: String,
        node: SemanticsNode,
        focusedTag: String,
    ): Boolean {
        val (top, bottom) = bands(node)
        val down = canScroll(node, down = true)
        val up = canScroll(node, down = false)
        if (bottom.hasArea() != down) problems += "$label: can scroll down = $down but bottom fade is ${bottom.height} px"
        if (top.hasArea() != up) problems += "$label: can scroll up = $up but top fade is ${top.height} px"
        val focused = rectPx(focusedTag)
        if (focused == null) {
            problems += "$label: focused $focusedTag not on screen"
        } else if (focused.overlaps(top) || focused.overlaps(bottom)) {
            problems += "$label: focused $focusedTag $focused under a fade (top $top, bottom $bottom)"
        }
        return down
    }

    /** A grid: fades only where a cover is cut at that edge, never over the focused cover. Returns whether a fade showed. */
    private fun checkGrid(
        label: String,
        focused: Int,
        count: Int,
    ): Boolean {
        val node = faded { tagOf(it) == HomeTags.GRID }
        val (top, bottom) = bands(node)
        val box = node.boundsInRoot
        val cells = (0 until count).mapNotNull { rectPx(HomeTags.cover(it)) }
        val cutTop = cells.any { it.top < box.top - 1f && it.bottom > box.top + 1f }
        val cutBottom = cells.any { it.top < box.bottom - 1f && it.bottom > box.bottom + 1f }
        if (top.hasArea() != cutTop) problems += "$label: a cover cut at the top = $cutTop but top fade is ${top.height} px"
        if (bottom.hasArea() != cutBottom) problems += "$label: a cover cut at the bottom = $cutBottom but bottom fade is ${bottom.height} px"
        if (bottom.hasArea() && !canScroll(node, down = true)) problems += "$label: bottom fade at the grid's end"
        val cell = rectPx(HomeTags.cover(focused))
        if (cell == null || cell.overlaps(top) || cell.overlaps(bottom)) problems += "$label: focused cover $focused $cell under a fade (top $top, bottom $bottom)"
        return top.hasArea() || bottom.hasArea()
    }

    private fun assertNoProblems() = assertTrue(problems.joinToString("\n"), problems.isEmpty())

    protected fun gamesList() {
        val snesId = FakeHomeContent.board().entries[FakeHomeContent.BOARD_SNES_INDEX].id
        show { HomeAt(FakeHomeContent.board(), BoardGamesFocused, onState = { state = it }) }
        var scrolls = 0
        for (size in TEXT_SIZES) {
            textSize = size
            compose.waitForIdle()
            // As it opens too (the remembered row may be the last whole one, the row after it cut).
            checkList("games $size opened", faded { tagOf(it) != HomeTags.SYSTEMS && tagOf(it) != HomeTags.GRID }, HomeTags.game(checkNotNull(state).focus.rowOf(snesId)))
            val steps = List(STEPS) { Direction.Down } + List(STEPS * 2) { Direction.Up }
            for (direction in steps) {
                // Held: the list stops at its ends instead of wrapping.
                send(Action.Move(direction, isRepeat = true))
                val row = checkNotNull(state).focus.rowOf(snesId)
                val list = faded { tagOf(it) != HomeTags.SYSTEMS && tagOf(it) != HomeTags.GRID }
                if (checkList("games $size row $row", list, HomeTags.game(row))) scrolls++
            }
        }
        assertNoProblems()
        assertTrue("the games list scrolls", scrolls > 0)
    }

    protected fun systemsColumn() {
        val entries = FakeHomeContent.board().entries.size
        show { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, onState = { state = it }) }
        var scrolls = 0
        for (size in TEXT_SIZES) {
            textSize = size
            compose.waitForIdle()
            var index = checkNotNull(state).focus.systemIndex
            for (direction in List(entries) { Direction.Down } + List(entries) { Direction.Up }) {
                val before = checkNotNull(state).focus.systemIndex
                send(Action.Move(direction, isRepeat = true))
                index = checkNotNull(state).focus.systemIndex
                if (index == before) continue
                if (checkList("systems $size index $index", faded { tagOf(it) == HomeTags.SYSTEMS }, HomeTags.system(index))) scrolls++
            }
        }
        assertNoProblems()
        assertTrue("the systems column scrolls at some text size", scrolls > 0)
    }

    protected fun coverGrid() {
        val count = CoverArtLibrary.entries[FakeHomeContent.BOARD_SNES_INDEX].rows.size
        show { key(gridSize) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it }) } }
        var fading = 0
        for (size in GridSize.entries) for (text in TEXT_SIZES) {
            gridSize = size
            textSize = text
            compose.waitForIdle()
            send(Action.TapItem(com.plumemu.coreinput.Column.Games, 0))
            for (direction in List(STEPS) { Direction.Down } + List(STEPS) { Direction.Up }) {
                send(Action.Move(direction, isRepeat = true))
                val focused = checkNotNull(state).focus.rowOf(CoverArtLibrary.entries[FakeHomeContent.BOARD_SNES_INDEX].id)
                if (checkGrid("covers $size $text cover $focused", focused, count)) fading++
            }
        }
        assertNoProblems()
        assertTrue("a cover grid shows a cut row at some size", fading > 0)
    }

    protected fun appsGrid() {
        show { AppsGridBoard(focused = 0, onState = { state = it }) }
        val appsId = appsGridLibrary().entries.first { it.kind == EntryKind.Apps }.id
        var fading = 0
        for (text in TEXT_SIZES) {
            textSize = text
            compose.waitForIdle()
            send(Action.TapItem(com.plumemu.coreinput.Column.Games, 0))
            for (direction in List(STEPS) { Direction.Down } + List(STEPS) { Direction.Up }) {
                send(Action.Move(direction, isRepeat = true))
                val focused = checkNotNull(state).focus.rowOf(appsId)
                if (checkGrid("apps $text app $focused", focused, AppsGridApps.size)) fading++
            }
        }
        assertNoProblems()
        assertTrue("the Apps grid shows a cut row at some size", fading > 0)
    }

    /** Settings › Appearance › Fade list edges off: no list or grid draws a mask, at once; on again, they do. */
    protected fun offIsFlat() {
        show { HomeAt(FakeHomeContent.board(), BoardGamesFocused, onState = { state = it }) }
        fun masked() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(EdgeFadesKey), useUnmergedTree = true).fetchSemanticsNodes().size
        assertTrue("on: the systems column and the games list carry a mask", masked() >= 2)
        edgeFadeOn = false
        compose.waitForIdle()
        assertEquals("off: no mask anywhere", 0, masked())
        edgeFadeOn = true
        compose.waitForIdle()
        assertTrue("on again", masked() >= 2)
    }

    private companion object {
        val TEXT_SIZES = listOf(TextSize.Medium, TextSize.Large, TextSize.Largest)
        const val STEPS = 14
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeScrollCue768Test : HomeScrollCueBase() {
    @Test fun theGamesListFadesWhereItScrolls() = gamesList()

    @Test fun withTheSettingOffNothingFades() = offIsFlat()

    @Test fun theSystemsColumnFadesWhereItScrolls() = systemsColumn()

    @Test fun theCoverGridFadesOnlyACutRow() = coverGrid()

    @Test fun theAppsGridFadesOnlyACutRow() = appsGrid()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeScrollCue576Test : HomeScrollCueBase() {
    @Test fun theGamesListFadesWhereItScrolls() = gamesList()

    @Test fun withTheSettingOffNothingFades() = offIsFlat()

    @Test fun theSystemsColumnFadesWhereItScrolls() = systemsColumn()

    @Test fun theCoverGridFadesOnlyACutRow() = coverGrid()

    @Test fun theAppsGridFadesOnlyACutRow() = appsGrid()
}
