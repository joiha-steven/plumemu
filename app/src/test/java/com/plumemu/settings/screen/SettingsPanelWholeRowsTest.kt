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
import androidx.compose.ui.test.captureToImage
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
 * A panel's option list shows whole options only (design/rules.md › Lists that scroll): rows here are solid white on black,
 * so every run of ink down the list must be a whole row (it may fade a little at an edge). Never blank, by gamepad (the focus moves) and by touch (a drag).
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
    ) {
        focus.intValue = initial
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                Box(Modifier.width(300.dp).height(windowHeight.dp).background(Color.Black)) {
                    PanelLazyList(focus.intValue, Modifier) {
                        items(List(rows) { it }) { Box(Modifier.fillMaxWidth().height(rowHeight.dp).background(Color.White)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** The lengths (px) of the white runs down the list's middle column. */
    private fun whiteRuns(): List<Int> {
        val image = compose.onNodeWithTag(SettingsTags.PANEL_LIST).captureToImage().asAndroidBitmap()
        val runs = ArrayList<Int>()
        var run = 0
        for (y in 0 until image.height) {
            val white = android.graphics.Color.red(image.getPixel(image.width / 2, y)) > 12
            if (white) run++ else if (run > 0) { runs += run; run = 0 }
        }
        if (run > 0) runs += run
        return runs
    }

    private fun px(dp: Int) = (dp * RuntimeEnvironment.getApplication().resources.displayMetrics.density).toInt()

    private fun assertWhole(rowHeight: Int) {
        val runs = whiteRuns()
        assertTrue("some row is shown: $runs", runs.isNotEmpty())
        // A row near an edge fades there (the ramp and its clear share: up to 8 dp short); the focus slab adds a few px of glow; a cut row would be far shorter.
        runs.forEach { assertTrue("a run of $it px is not a whole row of ${px(rowHeight)}: $runs", it in px(rowHeight - 8)..px(rowHeight) + px(6)) }
    }

    @Test
    fun aRowTallerThanTheWindowIsNotBlankedWhenNothingIsFocused() {
        show(rows = 3, rowHeight = 300, windowHeight = 200)
        assertTrue("the tall row shows", whiteRuns().isNotEmpty())
    }

    @Test
    fun theBottomEdgeHidesTheRowItCuts() {
        // 250 dp window, 100 dp rows 8 apart: two whole rows and a third cut by the bottom edge.
        show(rows = 6, rowHeight = 100, windowHeight = 250)
        assertWhole(100)
        assertEquals("two whole rows", 2, whiteRuns().size)
    }

    @Test
    fun theFocusedRowStaysWholeWhileTheFocusMovesDown() {
        show(rows = 6, rowHeight = 100, windowHeight = 250, initial = 0)
        for (i in 1..5) {
            focus.intValue = i
            compose.waitForIdle()
            assertWhole(100)
        }
        val last = whiteRuns().last()
        assertTrue("the last row is whole and at the bottom: $last", last >= px(100 - 8))
    }

    @Test
    fun aTouchDragShowsWholeRowsOnly() {
        show(rows = 8, rowHeight = 100, windowHeight = 250)
        compose.onNodeWithTag(SettingsTags.PANEL_LIST).performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.2f, durationMillis = 400) }
        compose.waitForIdle()
        assertWhole(100)
    }
}
