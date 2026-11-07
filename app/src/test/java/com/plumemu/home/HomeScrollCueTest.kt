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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.BAR_FADE_BOTTOM
import com.plumemu.coreui.components.BAR_FADE_TOP
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
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
 * The home screen's lists and grids show they scroll with the screen's one fade (design/rules.md › Lists that scroll): two bands across the whole width, tight on the
 * bars, none with the setting off. Walking the focus down and back up, the top one draws nothing while the columns stand at their start, and never over the focused
 * row or cover (nor the glow past it); the bottom one never covers the focused row either. A grid leaves out only a row showing under a third of itself. At 768x432
 * and 576x432, at several text sizes and grid sizes; each case that scrolls must exist, or the test proves nothing.
 */
abstract class HomeScrollCueBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var textSize by mutableStateOf(TextSize.Small)
    private var gridSize by mutableStateOf(GridSize.Default)
    private var edgeFadeOn by mutableStateOf(true)
    private val problems = mutableListOf<String>()
    private var topDrew = 0

    /** Motion off: nothing glows or glides between two captures, so the setting is the only change the probe sees. */
    private fun show(content: @Composable () -> Unit) =
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize, gridSize = gridSize, edgeFadeOn = edgeFadeOn, animationsOn = false)) { content() } }

    /** Lets every glide finish: what is read next is the rest, not a frame of the move. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun send(action: Action) {
        compose.runOnIdle { checkNotNull(state).dispatch(action) }
        settle()
    }

    /** Every node that scrolls vertically, the one that [pick] chooses among all of them. */
    private fun scroller(pick: (SemanticsNode) -> Boolean): SemanticsNode =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true).fetchSemanticsNodes().single(pick)

    private fun tagOf(node: SemanticsNode) = node.config.getOrNull(SemanticsProperties.TestTag)

    private fun rectPx(tag: String): Rect? {
        if (compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return null
        val b = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val d = compose.density.density
        return Rect(b.left.value * d, b.top.value * d, b.right.value * d, b.bottom.value * d)
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

    /** The screen's two bands: one each, tight on the bars, as wide as the screen, [PlumDimens.listFadeBand] tall. */
    private fun assertOneFadeAcrossTheWidth(label: String) {
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        for (tag in listOf(BAR_FADE_TOP, BAR_FADE_BOTTOM)) {
            val nodes = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            if (nodes.size != 1) {
                problems += "$label: ${nodes.size} nodes tagged $tag, not one"
                continue
            }
            val b = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            if (kotlin.math.abs((b.right - b.left).value - (root.right - root.left).value) > 1f) problems += "$label: $tag is ${(b.right - b.left).value} dp wide, the screen ${(root.right - root.left).value}"
            if (kotlin.math.abs((b.bottom - b.top).value - PlumDimens.listFadeBand.value) > 0.1f) problems += "$label: $tag is ${(b.bottom - b.top).value} dp tall, not ${PlumDimens.listFadeBand.value}"
        }
        val topBar = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hintBar = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val top = compose.onNodeWithTag(BAR_FADE_TOP, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bottom = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).getUnclippedBoundsInRoot()
        if (kotlin.math.abs(top.top.value - topBar.bottom.value) > 0.6f) problems += "$label: the top band starts at ${top.top.value} dp, the top bar ends at ${topBar.bottom.value}"
        if (kotlin.math.abs(bottom.bottom.value - hintBar.top.value) > 0.6f) problems += "$label: the bottom band ends at ${bottom.bottom.value} dp, the hint bar starts at ${hintBar.top.value}"
    }

    /** Whether the top band changes a pixel now: the root with the setting on against the same settled frame with it off (the setting acts on the bands alone), each taken the same time after its toggle. */
    private fun topFadeDraws(skip: ClosedFloatingPointRange<Float> = 1f..0f): Boolean {
        val d = compose.density.density
        val band = compose.onNodeWithTag(BAR_FADE_TOP, useUnmergedTree = true).getUnclippedBoundsInRoot()
        fun settled(): androidx.compose.ui.graphics.PixelMap {
            settle()
            return compose.onRoot().captureToImage().toPixelMap()
        }
        // Each capture a fixed time after a toggle, so a glow that breathes is at the same phase in both.
        edgeFadeOn = false
        val off = settled()
        edgeFadeOn = true
        val on = settled()
        for (y in (band.top.value * d).toInt() until (band.bottom.value * d).toInt()) for (x in 0 until on.width) {
            if (x.toFloat() in skip) continue
            val p = on[x, y]
            val q = off[x, y]
            if (kotlin.math.abs(p.red - q.red) > 0.01f || kotlin.math.abs(p.green - q.green) > 0.01f || kotlin.math.abs(p.blue - q.blue) > 0.01f) return true
        }
        return false
    }

    /**
     * The screen's fade at one step: the top band draws only while a column [scrolledUp], and not over the focused item ([focused], with the glow a slab reaches past
     * it); the bottom band never covers it. Returns whether the top band drew.
     */
    private fun checkFades(
        label: String,
        scrolledUp: Boolean,
        focused: Rect?,
        ringReachDp: Float = 0f,
    ): Boolean {
        val d = compose.density.density
        // A grid's focused cover wears a ring and a halo that glow past it and are drawn over the mask: the probe leaves its columns out.
        val drew = topFadeDraws(if (focused != null && ringReachDp > 0f) (focused.left - ringReachDp * d - RING_AA_PX)..(focused.right + ringReachDp * d + RING_AA_PX) else 1f..0f)
        if (drew) topDrew++
        if (drew && !scrolledUp) problems += "$label: the top band draws although the columns stand at their start"
        val topBand = compose.onNodeWithTag(BAR_FADE_TOP, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bottomBand = compose.onNodeWithTag(BAR_FADE_BOTTOM, useUnmergedTree = true).getUnclippedBoundsInRoot()
        if (focused == null) {
            problems += "$label: the focused item is not on screen"
        } else {
            val reach = (PlumDimens.listFadeBand + SlabDimens.listEndRoom).value * d
            if (drew && focused.top < topBand.top.value * d + reach - 0.5f) problems += "$label: the top band draws with the focused item at ${focused.top - topBand.top.value * d} px under its top, inside ${reach} px"
            if (focused.bottom > bottomBand.top.value * d + 0.5f) problems += "$label: the focused item $focused is under the bottom band (from ${bottomBand.top.value * d})"
        }
        return drew
    }

    private fun scrolledUp() = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange), useUnmergedTree = true).fetchSemanticsNodes().any { canScroll(it, down = false) }

    /** A list: the screen's fade at this step. Returns whether it could scroll down. */
    private fun checkList(
        label: String,
        node: SemanticsNode,
        focusedTag: String,
    ): Boolean {
        settle()
        checkFades(label, scrolledUp(), rectPx(focusedTag))
        return canScroll(node, down = true)
    }

    /**
     * A grid: the screen's fade at this step, and the rows cut at a bar. Returns whether a cover shows cut at a bar (a row showing under [minShown] of itself is left out
     * by the grid's mask and does not count).
     */
    private fun checkGrid(
        label: String,
        focused: Int,
        count: Int,
        minShown: Float = 1f / 3f,
    ): Boolean {
        settle()
        val node = scroller { tagOf(it) == HomeTags.GRID }
        val box = node.boundsInRoot
        val cells = (0 until count).mapNotNull { rectPx(HomeTags.cover(it)) }
        checkFades(label, canScroll(node, down = false), rectPx(HomeTags.cover(focused)), ringReachDp = PlumDimens.focusRingOutset.value + PlumDimens.focusRingKeyline.value + PlumDimens.focusHaloReach.value + RING_LIFT_DP)
        return cells.any { (it.top < box.top - 1f && it.bottom > box.top + 1f && (it.bottom - box.top) >= minShown * it.height) || (it.top < box.bottom - 1f && it.bottom > box.bottom + 1f && (box.bottom - it.top) >= minShown * it.height) }
    }

    private fun assertNoProblems() = assertTrue(problems.joinToString("\n"), problems.isEmpty())

    protected fun gamesList() {
        val snesId = FakeHomeContent.board().entries[FakeHomeContent.BOARD_SNES_INDEX].id
        show { HomeAt(FakeHomeContent.board(), BoardGamesFocused, onState = { state = it }) }
        var scrolls = 0
        assertOneFadeAcrossTheWidth("games")
        for (size in TEXT_SIZES) {
            textSize = size
            compose.waitForIdle()
            // As it opens too (the remembered row may be the last whole one, the row after it cut).
            settle()
            checkList("games $size opened", scroller { tagOf(it) != HomeTags.SYSTEMS && tagOf(it) != HomeTags.GRID }, HomeTags.game(checkNotNull(state).focus.rowOf(snesId)))
            val steps = List(STEPS) { Direction.Down } + List(STEPS * 2) { Direction.Up }
            for (direction in steps) {
                // Held: the list stops at its ends instead of wrapping.
                send(Action.Move(direction, isRepeat = true))
                val row = checkNotNull(state).focus.rowOf(snesId)
                val list = scroller { tagOf(it) != HomeTags.SYSTEMS && tagOf(it) != HomeTags.GRID }
                if (checkList("games $size row $row", list, HomeTags.game(row))) scrolls++
            }
        }
        assertNoProblems()
        assertTrue("the games list scrolls", scrolls > 0)
        assertTrue("the top band draws at some step of a scrolled list", topDrew > 0)
    }

    protected fun systemsColumn() {
        val entries = FakeHomeContent.board().entries.size
        show { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, onState = { state = it }) }
        var scrolls = 0
        assertOneFadeAcrossTheWidth("systems")
        for (size in TEXT_SIZES) {
            textSize = size
            compose.waitForIdle()
            var index = checkNotNull(state).focus.systemIndex
            for (direction in List(entries) { Direction.Down } + List(entries) { Direction.Up }) {
                val before = checkNotNull(state).focus.systemIndex
                send(Action.Move(direction, isRepeat = true))
                index = checkNotNull(state).focus.systemIndex
                if (index == before) continue
                if (checkList("systems $size index $index", scroller { tagOf(it) == HomeTags.SYSTEMS }, HomeTags.system(index))) scrolls++
            }
        }
        assertNoProblems()
        assertTrue("the systems column scrolls at some text size", scrolls > 0)
    }

    protected fun coverGrid() {
        val count = CoverArtLibrary.entries[FakeHomeContent.BOARD_SNES_INDEX].rows.size
        show { key(gridSize) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it }) } }
        var cut = 0
        assertOneFadeAcrossTheWidth("covers")
        for (size in GridSize.entries) for (text in TEXT_SIZES) {
            gridSize = size
            textSize = text
            compose.waitForIdle()
            send(Action.TapItem(com.plumemu.coreinput.Column.Games, 0))
            for (direction in List(STEPS) { Direction.Down } + List(STEPS) { Direction.Up }) {
                send(Action.Move(direction, isRepeat = true))
                val focused = checkNotNull(state).focus.rowOf(CoverArtLibrary.entries[FakeHomeContent.BOARD_SNES_INDEX].id)
                if (checkGrid("covers $size $text cover $focused", focused, count)) cut++
            }
        }
        assertNoProblems()
        assertTrue("a cover grid shows a cut row at some size", cut > 0)
    }

    protected fun appsGrid() {
        show { AppsGridBoard(focused = 0, onState = { state = it }) }
        val appsId = appsGridLibrary().entries.first { it.kind == EntryKind.Apps }.id
        var cut = 0
        assertOneFadeAcrossTheWidth("apps")
        for (text in TEXT_SIZES) {
            textSize = text
            compose.waitForIdle()
            send(Action.TapItem(com.plumemu.coreinput.Column.Games, 0))
            for (direction in List(STEPS) { Direction.Down } + List(STEPS) { Direction.Up }) {
                send(Action.Move(direction, isRepeat = true))
                val focused = checkNotNull(state).focus.rowOf(appsId)
                if (checkGrid("apps $text app $focused", focused, AppsGridApps.size)) cut++
            }
        }
        assertNoProblems()
        // Rows run under the bars: some step shows a row cut there.
        assertTrue("the Apps grid shows a cut row at some step", cut > 0)
    }

    /** The theme with the fade off (the app never does this now): no list or grid draws a mask, at once; on again, they do. */
    protected fun offIsFlat() {
        show { HomeAt(FakeHomeContent.board(), BoardGamesFocused, onState = { state = it }) }
        fun bands() = listOf(BAR_FADE_TOP, BAR_FADE_BOTTOM).sumOf { compose.onAllNodesWithTag(it, useUnmergedTree = true).fetchSemanticsNodes().size }
        assertEquals("on: the top and the bottom band", 2, bands())
        edgeFadeOn = false
        compose.waitForIdle()
        assertEquals("off: no band anywhere", 0, bands())
        edgeFadeOn = true
        compose.waitForIdle()
        assertEquals("on again", 2, bands())
    }

    private companion object {
        val TEXT_SIZES = listOf(TextSize.Small, TextSize.Large, TextSize.Largest)
        const val STEPS = 14
        const val SETTLE_MS = 1_000L

        /** The ring's soft edge: a pixel past its reach that a capture can round differently. */
        const val RING_AA_PX = 4f

        /** The lifted cover grows past its cell by about this, each side. */
        const val RING_LIFT_DP = 8f
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
