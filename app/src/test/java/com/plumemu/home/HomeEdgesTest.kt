package com.plumemu.home

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.components.SystemRowTags
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.LeftColumnDimens
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home screen's edges at the Thor's size: the slab, the last row and the cover preview. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeEdgesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(actions: List<Action>) {
        compose.setContent { PlumTheme(PlumThemeSettings.Paper) { HomeWithArt(rememberSource { coverArtSource() }, actions) } }
        compose.waitForIdle()
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun theSystemsSlabReachesIntoTheMarginButNotTheScreenEdge() {
        show(listOf(Action.TapItem(Column.Systems, PSP_INDEX)))
        val pill = compose.onAllNodesWithTag(SystemRowTags.PILL, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        assertTrue("slab left ${pill.left} is the pill start ${LeftColumnDimens.systemPillStart}, no nearer than 16 to the screen edge", pill.left.value >= 16f - 0.5f && pill.left.value <= LeftColumnDimens.systemPillStart.value + 0.5f)
    }

    @Test
    fun theLastSystemsFocusedRowIsWhollyInsideTheStrip() {
        show(listOf(Action.TapItem(Column.Systems, 20)))
        val pill = compose.onAllNodesWithTag(SystemRowTags.PILL, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val strip = bounds(HomeTags.STRIP)
        val hints = bounds(HomeTags.HINT_BAR)
        assertTrue("slab bottom ${pill.bottom} within the strip ${strip.bottom}", pill.bottom.value <= strip.bottom.value + 0.5f)
        assertTrue("slab bottom ${pill.bottom} above the hint bar ${hints.top}", pill.bottom.value <= hints.top.value)
    }

    @Test
    fun theSystemsListFillsTheStripAndTheHintBarKeepsItsHeight() {
        var barHeight = 0f
        compose.setContent {
            PlumTheme(PlumThemeSettings.Paper) {
                barHeight = PlumTheme.layout.hintBarHeight.value
                HomeWithArt(rememberSource { coverArtSource() }, listOf(Action.TapItem(Column.Systems, PSP_INDEX)))
            }
        }
        compose.waitForIdle()
        val strip = bounds(HomeTags.STRIP)
        val systems = bounds(HomeTags.SYSTEMS)
        val hints = bounds(HomeTags.HINT_BAR)
        assertEquals("no room left empty under the systems list", strip.bottom.value, systems.bottom.value, 0.5f)
        assertEquals("the hint bar is one line of keys", barHeight, hints.bottom.value - hints.top.value, 0.5f)
        assertEquals("the footer gap, nothing more, between the list and the hint bar", PlumDimens.footerGap.value, hints.top.value - strip.bottom.value, 0.5f)
    }

    @Test
    fun theCoverPreviewStaysInsideTheRightMargin() {
        show(listOf(Action.TapItem(Column.Systems, PSP_INDEX)))
        val width = compose.onRoot().getUnclippedBoundsInRoot().right.value
        // The cover is left out when its whole column does not fit (HomeSystemsCoverBoundsTest); if it is there it ends inside the margin.
        if (compose.onAllNodesWithTag(HomeTags.DETAILS_COVER, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return
        val details = bounds(HomeTags.DETAILS_COVER)
        assertTrue("preview right ${details.right} within ${width - PlumDimens.stripMarginSide.value}", details.right.value <= width - PlumDimens.stripMarginSide.value + 0.5f)
    }

    @Test
    fun theGamesListKeepsItsFullWidthWithTheSystemsFocused() {
        var expected = 0f
        compose.setContent {
            PlumTheme(PlumThemeSettings.Paper) {
                expected = PlumTheme.layout.gamesListWidth.value
                HomeWithArt(rememberSource { coverArtSource() }, listOf(Action.TapItem(Column.Systems, PSP_INDEX)))
            }
        }
        compose.waitForIdle()
        val width = bounds(HomeTags.GAMES).let { it.right.value - it.left.value }
        assertTrue("the list ($width) does not shrink below its width ($expected) to make room for the cover", width >= expected - 0.5f)
    }

    @Test
    fun theCoverPreviewStandsCentredInItsColumn() {
        show(gridActions(PSP_INDEX, 3))
        val column = bounds(HomeTags.DETAILS)
        val cover = bounds(HomeTags.DETAILS_COVER)
        assertEquals("centred", (column.left.value + column.right.value) / 2, (cover.left.value + cover.right.value) / 2, 0.5f)
    }

    /**
     * With the last row focused (reached by holding Down) the slab's glow below it fades out smoothly: the list's window does not cut it flat.
     * A cut shows as a step between two neighbouring pixel rows below the slab; a soft fade changes by a few levels at most.
     */
    private fun assertGlowBelowTheFocusedLastRowIsNotCut(
        tag: String,
        actions: List<Action>,
    ) {
        var density = Density(1f)
        compose.setContent {
            density = LocalDensity.current
            PlumTheme(PlumThemeSettings.Paper) { HomeWithArt(rememberSource { coverArtSource() }, actions) }
        }
        compose.waitForIdle()
        val row = compose.onAllNodesWithTag(tag, useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // Beside the slab, where its glow runs along the row: that is the strip a window cut shows on.
        val x = with(density) { (row.right + SIDE_GLOW_PROBE).toPx() }.toInt()
        val from = with(density) { (row.bottom + SlabDimens.maxOverhang).toPx() }.toInt() - 12
        // Down to the glow's reach, or to the gap above the hint keys when that is nearer (what the strip's clip leaves).
        val to = minOf(from + with(density) { SlabDimens.glowReach.toPx() }.toInt(), with(density) { (bounds(HomeTags.STRIP).bottom + footerGapToKeys(PlumThemeSettings.Paper.textSize ?: TextSize.Large)).toPx() }.toInt() - 2)
        var worst = 0f
        var at = 0
        for (y in from until to) {
            val a = pixels[x, y]
            val b = pixels[x, y + 1]
            val step = maxOf(abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue))
            if (step > worst) {
                worst = step
                at = y - from
            }
        }
        assertTrue("the glow steps by ${(worst * 255).toInt()}/255 at ${at}px below the slab: cut flat", worst <= MAX_GLOW_STEP)
    }

    @Test
    fun theGlowUnderTheLastFocusedSystemIsNotCutFlat() =
        assertGlowBelowTheFocusedLastRowIsNotCut(SystemRowTags.PILL, listOf(Action.TapItem(Column.Systems, 12)) + List(5) { Action.Move(Direction.Down) })

    private companion object {
        // The cut this guards against steps 12 levels; the glow's tail at the strip's own clip, under the hint keys' gap, steps 4.
        const val MAX_GLOW_STEP = 6f / 255f
        val SIDE_GLOW_PROBE = 4.dp
    }
}
