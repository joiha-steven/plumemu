package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The focus of a cover in the cover grid (docs/design/rules.md > Grid; `FocusLift`), driven by the compose test clock and read from
 * the pixels: the focused cover has the rim round its frame (the groove stays unlit, no accent); the cover focused as the grid opens has its rim in
 * the first frame; a move cross-fades the rims over [PlumMotion.FOCUS_GROW_MS] / [PlumMotion.FOCUS_LEAVE_MS] and no rim travels; with animations off it
 * switches in one frame; a grid at rest writes no state (so draws nothing); the glow is tight and not cut at the first row.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeCoverFocusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var gridSize by mutableStateOf(GridSize.Medium)
    private var accent = Color.Unspecified
    private var background = Color.Unspecified
    private var dark = true
    private var ink = Color.Unspecified
    private var columns = 0

    private fun open(
        focus: Int,
        animationsOn: Boolean = true,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(TableText.Default.copy(gridSize = gridSize, animationsOn = animationsOn)) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                dark = PlumTheme.colors.isDark
                ink = PlumTheme.colors.text
                columns = PlumTheme.layout.gridColumns(com.plumemu.coreui.theme.CoverShape.Landscape)
                HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, focus), gridIds = setOf("snes"), onState = { state = it })
            }
        }
    }

    private fun move(direction: Direction) = compose.runOnUiThread { checkNotNull(state).dispatch(Action.Move(direction)) }

    private fun frame() = compose.mainClock.advanceTimeByFrame()

    /** The drawn picture of cover [index] (its layout place), in px. */
    private fun box(index: Int): Rect {
        val node =
            compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode().children
                .first { child -> child.config.any { it.value == HomeTags.COVER_PICTURE } }
        return Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    private fun Color.near(other: Color) = abs(red - other.red) < 0.12f && abs(green - other.green) < 0.12f && abs(blue - other.blue) < 0.12f

    /** The groove of cover [index], read across its left side, is the accent: it never is (the focus is the line round the frame). */
    private fun isLit(index: Int): Boolean {
        val b = box(index)
        val d = compose.density.density
        val collarCentreY = (b.top - CollarSize.Cover.topPadding.value * d + b.bottom + CollarSize.Cover.bottomPadding.value * d) / 2f
        val width = CollarDimens.coverGroove.value * d
        val shot = compose.onRoot().captureToImage().toPixelMap()
        return (1..8).any { shot[(b.left - width * it / 4f).roundToInt(), collarCentreY.roundToInt()].near(litGroove(accent)) }
    }

    /** How strong the rim of cover [index] shows on the left side of its frame (0..1): the brightest pixel of the band the rim can stand in, against the page. */
    private fun rimStrength(index: Int): Float {
        val b = box(index)
        val d = compose.density.density
        val y = ((b.top + b.bottom) / 2f).roundToInt()
        val shot = compose.onRoot().captureToImage().toPixelMap()
        val rim = FocusLift.rimColor(dark, ink)
        val full = abs(rim.luminance() - background.luminance()) * rim.alpha
        // From the frame's edge out past where a grown frame's rim ends (an edge cover grows inward, a middle one by half the growth each side).
        val frameEdge = b.left - CollarSize.Cover.sidePadding.value * d
        val reach = (FocusLift.rimWidth.value + 0.0175f * b.width / d + 1f) * d
        var best = 0f
        var x = frameEdge - reach
        while (x <= frameEdge + 0.5f) {
            best = maxOf(best, abs(shot[x.roundToInt(), y].luminance() - background.luminance()) / full)
            x += 1f
        }
        return best.coerceAtMost(1f)
    }

    /** The rim of cover [index] is whole (the rest of the band is page or the cover's own frame, never as bright). */
    private fun hasRim(index: Int) = rimStrength(index) > 0.8f

    /** How much brighter (0..255, the largest channel) the pixel at ([x], [y]) is than the background. */
    private fun brighterAt(
        x: Float,
        y: Float,
    ): Int {
        val p = compose.onRoot().captureToImage().toPixelMap()[x.roundToInt(), y.roundToInt()]
        return (maxOf(p.red - background.red, p.green - background.green, p.blue - background.blue) * 255f).roundToInt()
    }

    @Test
    fun theCoverFocusedAsTheGridOpensHasItsRimInTheFirstFrame() {
        open(focus = 5)
        frame()
        assertTrue("its rim is whole in the first frame (${rimStrength(5)})", hasRim(5))
        assertTrue("the covers beside it have none", !hasRim(4) && !hasRim(7))
        assertTrue("its groove stays unlit", !isLit(5))
    }

    @Test
    fun aFocusMoveCrossFadesTheRimsAndNoneTravels() {
        open(focus = 5)
        compose.mainClock.advanceTimeBy(1000)
        val before = box(6)
        assertTrue("the old cover has its rim at rest", hasRim(5))
        move(Direction.Right)
        frame()
        frame()
        // Part of the way (Decelerate is mostly done by half the time): the old rim is fading out, the new one in, each on its own cover (never a rim between them).
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS / 10L)
        val oldMid = rimStrength(5)
        val newMid = rimStrength(6)
        assertTrue("the old rim is fading (was 1, now $oldMid)", oldMid < 0.97f && oldMid > 0.01f)
        assertTrue("the new rim is arriving ($newMid)", newMid > 0.03f && newMid < 0.97f)
        assertTrue("nothing between the covers: the covers beyond have none", !hasRim(7) && !hasRim(4))
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 40L)
        assertTrue("the new rim has landed after ${PlumMotion.FOCUS_GROW_MS} ms", hasRim(6))
        assertTrue("the one left has none", !hasRim(5))
        assertTrue("no groove is lit", !isLit(6) && !isLit(5))
        assertEquals("the layout did not move or resize: the cover grows only in the draw", before, box(6))
    }

    @Test
    fun withAnimationsOffTheRimSwitchesInOneFrame() {
        open(focus = 5, animationsOn = false)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        assertTrue("the rim is on the new cover at once (${rimStrength(6)})", hasRim(6))
        assertTrue("and gone from the old one", !hasRim(5))
    }

    @Test
    fun aGridAtRestWritesNoState() {
        open(focus = 5)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 500L)
        // The move's own writes are announced lazily: flush them so only what happens while idle is counted.
        repeat(2) {
            compose.runOnUiThread { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeBy(500)
        }
        val writes = mutableListOf<Set<Any>>()
        val observer = Snapshot.registerApplyObserver { changed, _ -> writes += changed }
        try {
            compose.mainClock.advanceTimeBy(IDLE_MS)
            frame()
            assertTrue("no state changes while the focus rests (a timer would): ${writes.flatten().map { it.javaClass.name }.distinct()} ${writes.flatten().take(5)}", writes.isEmpty())
        } finally {
            observer.dispose()
        }
    }

    /** The glow reaches [FocusLift.reach] and no further: nothing glows past it above the first row. */
    @Test
    fun aFocusedCoversGlowIsTightAndNotCutAtTheFirstRow() {
        open(focus = 0)
        compose.mainClock.advanceTimeBy(1000)
        val first = box(0)
        val past = (CollarSize.Cover.topPadding.value + FocusLift.reach.value + 7f) * compose.density.density
        val glow = brighterAt(first.center.x, first.top - past)
        assertTrue("nothing glows above a first-row cover (was $glow)", glow <= 1)
    }

    private companion object {
        const val IDLE_MS = 5_000L
    }
}
