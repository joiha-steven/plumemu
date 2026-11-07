package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
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
 * The focus of a cover in the cover grid (docs/design/rules.md › Grid), driven by the compose test clock and read from
 * the pixels: the focused cover's collar has its groove lit in the accent and the halo around it, the cover does not grow, lift
 * or sweep; the cover focused as the grid opens is lit in the first frame; a move lights the new one in [PlumMotion.FOCUS_GROW_MS]
 * and darkens the old; with animations off it snaps; a grid at rest writes no state; the halo is not cut at the first row or past
 * the last column.
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

    /**
     * The groove of cover [index], read across its left side, is the accent. [lifted]: the cover is the focused one, drawn
     * [PlumDimens.FOCUSED_COVER_SCALE] larger about its collar's centre, so its groove is read there.
     */
    private fun isLit(
        index: Int,
        lifted: Boolean = false,
    ): Boolean {
        val b = box(index)
        val d = compose.density.density
        val collarCentreY = (b.top - CollarSize.Cover.topPadding.value * d + b.bottom + CollarSize.Cover.bottomPadding.value * d) / 2f
        val collarCentreX = b.center.x
        val s = if (lifted) PlumDimens.FOCUSED_COVER_SCALE else 1f
        val left = collarCentreX + (b.left - collarCentreX) * s
        val y = (collarCentreY + (b.center.y - collarCentreY) * s).roundToInt()
        val width = CollarDimens.coverGroove.value * d * s
        val shot = compose.onRoot().captureToImage().toPixelMap()
        return (1..8).any { shot[(left - width * it / 4f).roundToInt(), y].near(litGroove(accent)) }
    }

    /** How much brighter (0..255, the largest channel) the pixel at ([x], [y]) is than the background. */
    private fun brighterAt(
        x: Float,
        y: Float,
    ): Int {
        val p = compose.onRoot().captureToImage().toPixelMap()[x.roundToInt(), y.roundToInt()]
        return (maxOf(p.red - background.red, p.green - background.green, p.blue - background.blue) * 255f).roundToInt()
    }

    @Test
    fun theCoverFocusedAsTheGridOpensIsLitInTheFirstFrame() {
        open(focus = 5)
        frame()
        assertTrue("its groove is lit in the first frame", isLit(5, lifted = true))
        assertTrue("the covers beside it are not (its ring lies over its next neighbours)", !isLit(4) && !isLit(7))
    }

    @Test
    fun aFocusMoveLightsTheNewGrooveInTheGrowTimeAndDarkensTheOld() {
        open(focus = 5)
        compose.mainClock.advanceTimeBy(1000)
        val before = box(6)
        move(Direction.Right)
        frame()
        frame()
        assertTrue("two frames in, the groove is still coming on", !isLit(6))
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 40L)
        assertTrue("lit after ${PlumMotion.FOCUS_GROW_MS} ms", isLit(6, lifted = true))
        assertTrue("the one left is dark again", !isLit(5))
        assertEquals("the cover itself did not move or grow", before, box(6))
    }

    @Test
    fun withAnimationsOffTheGrooveSnapsOn() {
        open(focus = 5, animationsOn = false)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        assertTrue("lit at once", isLit(6, lifted = true))
        assertTrue("the old one dark at once", !isLit(5))
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

    /** The ring and its bands fill the gaps: a halo could only show where no neighbour stands, so a cover has none, on any side. */
    @Test
    fun aFocusedCoverHasNoHaloSoItsMarkIsEvenOnEverySide() {
        open(focus = 0)
        compose.mainClock.advanceTimeBy(1000)
        val first = box(0)
        val past = (CollarSize.Cover.topPadding.value + PlumDimens.focusRingOutset.value + PlumDimens.focusRingKeyline.value + 7f) * compose.density.density
        val glow = brighterAt(first.center.x, first.top - past)
        assertTrue("nothing glows above a first-row cover (was $glow)", glow <= 1)
    }

    private companion object {
        const val IDLE_MS = 5_000L
    }
}
