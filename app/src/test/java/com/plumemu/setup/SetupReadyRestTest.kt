package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Where the Ready step rests: the focused Start playing's button ends the bar gap
 * (10) above the hint bar, not 5.6, because the scroll had nothing to scroll (the content fitted by less than the gap);
 * and where the column does scroll (576 wide), it stops at a whole line, not half way through the paragraph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupReadyRestTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var screenHeight by mutableStateOf(432)
    private var state by mutableStateOf(SetupFakes.chooseFolder)
    private var build by mutableStateOf(0)

    /**
     * Shown at [screenHeight]; a new height builds the screen again (fresh scroll), so one test can sweep the heights.
     * The content's overflow depends on the font (Robolectric's differs from a device's), the sweep covers every overflow
     * from a little to a lot, among them the device's few dp that the scroll once could not take up.
     */
    private fun show(size: TextSize) =
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) {
                key(screenHeight, build) { Box(Modifier.height(screenHeight.dp)) { SetupAt(state) } }
            }
        }

    private fun forEachHeight(check: (Int) -> Unit) {
        for (height in HEIGHTS) {
            // The step before Ready is shown first: the scroll lives on across steps and its range (0 when the step fitted)
            // is what the Ready step starts from; that carried-over range is what left Start playing 5.6 above the bar.
            state = SetupFakes.chooseFolder
            screenHeight = height
            compose.waitForIdle()
            state = SetupFakes.ready
            compose.waitForIdle()
            check(height)
        }
    }

    private fun assertBarGapAboveTheHints(height: Int) {
        compose.waitForIdle()
        val start = compose.onNodeWithTag(SetupTags.control(SetupControl.StartPlaying), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hints = compose.onNodeWithTag(SetupTags.HINTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val gap = hints.top.value - start.bottom.value
        assertTrue("At $height dp high Start playing ends at ${start.bottom}, $gap above the hints at ${hints.top}; the bar gap is ${PlumDimens.barGap}", gap >= PlumDimens.barGap.value - TOLERANCE)
    }

    /** No line of the title or the description is cut by the top of the visible scroll area. */
    private fun assertNoLineCutAtTheTop(height: Int) {
        val visibleTop = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value + PlumDimens.focusRingOutset.value
        for (tag in listOf(SetupTags.TITLE, SetupTags.DESCRIPTION)) {
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true)
            val top = node.getUnclippedBoundsInRoot().top.value
            val results = mutableListOf<TextLayoutResult>()
            node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(results)
            val density = compose.density.density
            val layout = results.first()
            for (line in 0 until layout.lineCount) {
                val lineTop = top + layout.getLineTop(line) / density
                val lineBottom = top + layout.getLineBottom(line) / density
                assertTrue(
                    "At $height dp high $tag line $line ($lineTop to $lineBottom) is cut by the scroll's top at $visibleTop",
                    !(lineTop < visibleTop - TOLERANCE && lineBottom > visibleTop + TOLERANCE),
                )
            }
        }
    }

    private fun scrollRange(): Float =
        compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    /** The room the second screen step adds to rest on a line is not carried into the box art step (no snapping there). */
    private fun boxArtRangeAfterSecondScreenMatchesOpeningIt(size: TextSize) {
        show(size)
        for (height in 250..440 step 3) {
            state = SetupFakes.boxArtQuestion
            screenHeight = height
            build++
            compose.waitForIdle()
            val opened = scrollRange()
            state = SetupFakes.secondScreen
            build++
            compose.waitForIdle()
            state = SetupFakes.boxArtQuestion
            compose.waitForIdle()
            assertTrue("At $height dp high the box art step scrolls ${scrollRange()} after the second screen step, $opened opened directly", scrollRange() == opened)
        }
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLargestBoxArtAfterSecondScreenHasNoEmptyEnd() = boxArtRangeAfterSecondScreenMatchesOpeningIt(TextSize.Largest)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLargeBoxArtAfterSecondScreenHasNoEmptyEnd() = boxArtRangeAfterSecondScreenMatchesOpeningIt(TextSize.Large)

    @Test
    @Config(qualifiers = WIDE)
    fun wideLargeKeepsTheBarGap() {
        show(TextSize.Large)
        forEachHeight { assertBarGapAboveTheHints(it) }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideLargestKeepsTheBarGap() {
        show(TextSize.Largest)
        forEachHeight { assertBarGapAboveTheHints(it) }
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLargeKeepsTheBarGapAndCutsNoLine() {
        show(TextSize.Large)
        forEachHeight { assertBarGapAboveTheHints(it); assertNoLineCutAtTheTop(it) }
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLargestKeepsTheBarGapAndCutsNoLine() {
        show(TextSize.Largest)
        forEachHeight { assertBarGapAboveTheHints(it); assertNoLineCutAtTheTop(it) }
    }

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.6f
        val HEIGHTS = (330..440 step 3).toList()
    }
}
