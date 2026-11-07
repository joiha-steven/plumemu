package com.plumemu.settings.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A panel's option list fades at its own edges (design/rules.md › Lists that scroll): rows here are solid white on black, so every run of ink
 * down the list is a whole row, or a row cut by an edge that fades into it over the edge's last 12 dp (never a hard slice at the edge), and the
 * focused row stays whole. Never blank, by gamepad (the focus moves) and by touch (a drag).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class SettingsPanelWholeRowsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val focus = mutableIntStateOf(-1)

    private fun show(
        rows: Int,
        rowHeight: Int,
        windowHeight: Int,
        initial: Int = -1,
        motion: Boolean = true,
    ) {
        focus.intValue = initial
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(animationsOn = motion)) {
                Box(Modifier.width(300.dp).height(windowHeight.dp).background(Color.Black)) {
                    PanelLazyList(focus.intValue, Modifier) {
                        items(List(rows) { it }) { Box(Modifier.fillMaxWidth().height(rowHeight.dp).background(Color.White).testTag(rowTag(it))) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The white runs down the list's middle column, as pixel rows (start until end) of the list's capture; [height] is the capture's. */
    private fun runsIn(image: android.graphics.Bitmap): List<IntRange> {
        val runs = ArrayList<IntRange>()
        var start = -1
        for (y in 0 until image.height) {
            // Half white: the focused row's faint glow above it is not a row.
            val white = android.graphics.Color.red(image.getPixel(image.width / 2, y)) > 128
            if (white) {
                if (start < 0) start = y
            } else if (start >= 0) {
                if (y - start > RIM_PX) runs += start until y
                start = -1
            }
        }
        if (start >= 0 && image.height - start > RIM_PX) runs += start until image.height
        return runs
    }

    private fun capture() = compose.onNodeWithTag(SettingsTags.PANEL_LIST).captureToImage().asAndroidBitmap()

    private fun whiteRuns(): List<Int> = runsIn(capture()).map { it.last - it.first + 1 }

    private fun px(dp: Int) = (dp * RuntimeEnvironment.getApplication().resources.displayMetrics.density).toInt()

    /**
     * Every run is a whole row, or a row cut by an edge: that one lies in the edge's 12 dp band and falls away before the edge itself (the capture's first
     * or last pixel row is the page, no hard slice). Returns the number of whole rows.
     */
    private fun assertWholeOrFading(rowHeight: Int): Int {
        val image = capture()
        val runs = runsIn(image)
        assertTrue("some row is shown: ${runs.map { it.last - it.first + 1 }}", runs.isNotEmpty())
        val band = px(12)
        var whole = 0
        for (run in runs) {
            val length = run.last - run.first + 1
            // The focus slab adds a few px of glow to a whole row.
            if (length in px(rowHeight - 8)..px(rowHeight) + px(6)) {
                whole++
                continue
            }
            val atTop = run.first <= band
            val atBottom = run.last >= image.height - 1 - band
            assertTrue("a run of $length px (${run.first}..${run.last} of ${image.height}) is neither a whole row of ${px(rowHeight)} nor a row fading into an edge: ${runs.map { it.last - it.first + 1 }}", length < px(rowHeight - 8) && (atTop || atBottom))
            // No hard slice: the pixel row at the edge is (nearly) the page.
            // The darkest of the three pixel rows at the edge: a sliced row is bright in all three (a one-pixel rim of the slab is not the row).
            val shade = (0..2).minOf { android.graphics.Color.red(image.getPixel(image.width / 2, if (atBottom) image.height - 1 - it else it)) }
            assertTrue("the cut row still has ink ($shade of 255) at the list's edge (run ${run.first}..${run.last} of ${image.height}, runs $runs, focus ${focus.intValue}): it is sliced, not faded", shade <= 40)
        }
        return whole
    }

    @Test
    fun aRowTallerThanTheWindowIsNotBlankedWhenNothingIsFocused() {
        show(rows = 3, rowHeight = 300, windowHeight = 200)
        assertTrue("the tall row shows", whiteRuns().isNotEmpty())
    }

    @Test
    fun theBottomEdgeFadesTheRowItCuts() {
        // 250 dp window, 100 dp rows 8 apart: two whole rows and a third cut by the bottom edge, which fades into it.
        show(rows = 6, rowHeight = 100, windowHeight = 250)
        assertEquals("two whole rows", 2, assertWholeOrFading(100))
        assertEquals("and the cut one", 3, whiteRuns().size)
    }

    private fun rowTag(row: Int) = "whole_row_$row"

    /** The focused row lies whole inside the list's window, also where it reaches an edge. */
    private fun assertFocusedRowWhole(row: Int) {
        val list = compose.onNodeWithTag(SettingsTags.PANEL_LIST).getUnclippedBoundsInRoot()
        val bounds = compose.onNodeWithTag(rowTag(row)).getUnclippedBoundsInRoot()
        assertTrue("row $row $bounds is whole in $list", bounds.top >= list.top && bounds.bottom <= list.bottom)
    }

    @Test
    fun theFocusedRowStaysWholeWhileTheFocusMovesDown() {
        show(rows = 6, rowHeight = 100, windowHeight = 250, initial = 0)
        for (i in 1..5) {
            focus.intValue = i
            compose.waitForIdle()
            // The pill starts its glide on the first draw, and here nothing draws before a capture: let it land, or the slab under the rows shows through the top fade.
            capture()
            compose.mainClock.advanceTimeBy(PILL_LANDS_MS)
            compose.waitForIdle()
            assertWholeOrFading(100)
            assertFocusedRowWhole(i)
        }
        val last = whiteRuns().last()
        assertTrue("the last row is whole and at the bottom: $last", last >= px(100 - 8))
    }

    @Test
    fun aTouchDragShowsWholeRowsOrFadingOnes() {
        show(rows = 8, rowHeight = 100, windowHeight = 250)
        compose.onNodeWithTag(SettingsTags.PANEL_LIST).performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.2f, durationMillis = 400) }
        compose.waitForIdle()
        assertWholeOrFading(100)
    }

    @Test
    fun theFocusedRowStaysWholeWhileTheFocusMovesUp() {
        show(rows = 6, rowHeight = 100, windowHeight = 250, initial = 5)
        for (i in 4 downTo 0) {
            focus.intValue = i
            compose.waitForIdle()
            assertFocusedRowWhole(i)
        }
    }

    private fun dragFar() {
        repeat(FAR_DRAGS) {
            compose.onNodeWithTag(SettingsTags.PANEL_LIST).performTouchInput { swipeUp(startY = bottom * 0.9f, endY = bottom * 0.1f, durationMillis = 100) }
            compose.waitForIdle()
        }
    }

    @Test
    fun aKeyAfterAFarDragBringsBackTheFocusAbove() {
        show(rows = 60, rowHeight = 50, windowHeight = 250, initial = 2)
        dragFar()
        assertTrue("the focused row left the screen", compose.onAllNodesWithTag(rowTag(2)).fetchSemanticsNodes().isEmpty())
        focus.intValue = 3
        compose.waitForIdle()
        assertFocusedRowWhole(3)
    }

    @Test
    fun aKeyAfterAFarDragBringsBackTheFocusBelow() {
        show(rows = 60, rowHeight = 50, windowHeight = 250, initial = 40)
        repeat(2) { dragFar() }
        val down = compose.onAllNodesWithTag(rowTag(40)).fetchSemanticsNodes().isEmpty()
        assertTrue("the focused row left the screen", down)
        focus.intValue = 39
        compose.waitForIdle()
        assertFocusedRowWhole(39)
    }

    @Test
    fun aKeyAfterAFarDragBringsBackTheFocusWithAnimationsOff() {
        show(rows = 60, rowHeight = 50, windowHeight = 250, initial = 2, motion = false)
        dragFar()
        focus.intValue = 1
        compose.waitForIdle()
        assertFocusedRowWhole(1)
    }

    @Test
    fun theFirstDownMovesTheListOnceAfterAFarDrag() = aKeyMovesTheListOnce(from = 2)

    @Test
    fun theFirstUpMovesTheListOnceAfterAFarDrag() = aKeyMovesTheListOnce(from = 4)

    private fun aKeyMovesTheListOnce(from: Int) {
        show(rows = 60, rowHeight = 50, windowHeight = 250, initial = from)
        dragFar()
        // Frame by frame: the row reaches its place and stays, with no second motion after it.
        compose.mainClock.autoAdvance = false
        focus.intValue = 3
        val tops = ArrayList<Float>()
        repeat(GLIDE_FRAMES) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            tops += compose.onAllNodesWithTag(rowTag(3)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.top ?: Float.NaN
        }
        compose.mainClock.autoAdvance = true
        val seen = tops.filterNot { it.isNaN() }
        assertTrue("the row showed: $tops", seen.isNotEmpty())
        val turns = seen.zipWithNext { a, b -> b - a }.filter { kotlin.math.abs(it) > 0.5f }.zipWithNext { a, b -> a * b < 0 }.count { it }
        assertEquals("the row never turns back: $tops", 0, turns)
    }

    private companion object {
        const val FAR_DRAGS = 8
        const val GLIDE_FRAMES = 30
    }
}

/** The focus slab's bright rim, a line above its row, is not a row. */
private const val RIM_PX = 2

/** Longer than the pill's glide. */
private const val PILL_LANDS_MS = 2000L
