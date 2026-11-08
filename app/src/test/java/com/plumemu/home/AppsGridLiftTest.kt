package com.plumemu.home

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.layout.boundsInRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.AppsGridDimens
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
 * The focus in the Apps grid (docs/design/rules.md > Apps grid; `FocusLift`), driven by the compose test clock and read from the pixels:
 * the icon grows 3.5 % about its centre and wears the cover's 2 dp rim and tight glow, whole in the first frame the grid opens, cross-fading on a
 * move and switching in one frame with animations off. Every name keeps its row's line at every frame, a press dips the icon alone, the
 * glow stays off the names, and a grid at rest writes no state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppsGridLiftTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var accent = Color.Unspecified
    private var background = Color.Unspecified
    private var dark = true
    private var ink = Color.Unspecified

    private fun open(
        focus: Int,
        animationsOn: Boolean = true,
        theme: PlumThemeSettings = TableText.Default,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(theme.copy(animationsOn = animationsOn)) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                dark = PlumTheme.colors.isDark
                ink = PlumTheme.colors.text
                AppsGridBoard(focused = focus, onState = { state = it })
            }
        }
    }

    private fun move(direction: Direction) = compose.runOnUiThread { checkNotNull(state).dispatch(Action.Move(direction)) }

    private fun frame() = compose.mainClock.advanceTimeByFrame()

    /** The tile [index] at rest (its layout place), in px. */
    private fun resting(index: Int): Rect = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    /** The icon of the tile at [rest], in px. */
    private fun iconOf(rest: Rect): Rect {
        val density = compose.density.density
        val side = AppsGridDimens.appsGridTileIcon.value * density
        val top = rest.top + AppsGridDimens.appsGridTilePadding.value * density
        return Rect(rest.center.x - side / 2f, top, rest.center.x + side / 2f, top + side)
    }

    /** The rim's colour as it shows on the page. */
    private fun rimOnPage(): Color = FocusLift.rimColor(dark, ink).compositeOver(background)

    /** How far the lifted icon's edge stands past its rest edge (dp): half the growth. */
    private val growthDp get() = AppsGridDimens.appsGridTileIcon.value * (FocusLift.SCALE - 1f) / 2f

    /** The rim stands on the top of the grown icon at [rest]: the pixel a rim-width band above the grown icon's edge is the rim colour. */
    private fun rimOn(rest: Rect): Boolean {
        val density = compose.density.density
        val icon = iconOf(rest)
        val y = icon.top - (growthDp + FocusLift.rimWidth.value / 2f) * density
        val p = compose.onRoot().captureToImage().toPixelMap()[icon.center.x.roundToInt(), y.roundToInt()]
        val want = rimOnPage()
        return abs(p.red - want.red) < 0.12f && abs(p.green - want.green) < 0.12f && abs(p.blue - want.blue) < 0.12f
    }

    /** The pixels of the tile's neighbourhood, to compare frame with frame. */
    private fun around(rest: Rect): List<Int> = pixelsIn(rest.inflate(24f * compose.density.density))

    private fun pixelsIn(area: Rect): List<Int> {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val out = ArrayList<Int>()
        for (y in area.top.roundToInt()..area.bottom.roundToInt()) {
            for (x in area.left.roundToInt()..area.right.roundToInt()) out += pixels[x.coerceIn(0, pixels.width - 1), y.coerceIn(0, pixels.height - 1)].hashCode()
        }
        return out
    }

    /** How far (0..255, the largest channel) the pixel at ([x], [y]) is from the background. */
    private fun level(
        pixels: PixelMap,
        x: Float,
        y: Float,
    ): Int {
        val p = pixels[x.roundToInt(), y.roundToInt()]
        return (maxOf(abs(p.red - background.red), abs(p.green - background.green), abs(p.blue - background.blue)) * 255f).roundToInt()
    }

    /** The band of [rest] from the name's top down: only the name stands there, the glow is cut above it. */
    private fun nameBand(rest: Rect): Rect {
        val under = iconOf(rest).bottom + AppsGridDimens.appsGridNameGap.value * compose.density.density + 1f
        return Rect(rest.left, under, rest.right, rest.bottom)
    }

    /** The last pixel row of the name's ink in the tile at [rest]. */
    private fun nameBottom(rest: Rect): Int {
        val band = nameBand(rest)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return (band.bottom.toInt() downTo band.top.toInt()).first { y -> (band.left.toInt()..band.right.toInt()).any { x -> level(pixels, x.toFloat(), y.toFloat()) > 60 } }
    }

    @Test
    fun theTileFocusedAsTheGridOpensHasItsRimInTheFirstFrame() {
        open(focus = 8)
        frame()
        val rest = resting(8)
        assertTrue("the rim is whole in the first frame", rimOn(rest))
        val first = around(rest)
        compose.mainClock.advanceTimeBy(1000)
        assertEquals("nothing moved after the first frame, and nothing plays at rest", first, around(rest))
    }

    @Test
    fun aFocusMoveCrossFadesTheRimToTheNewTile() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        val next = resting(9)
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_GROW_MS + 40L)
        assertTrue("the rim has landed", rimOn(next))
        assertTrue("the old tile's rim is gone", !rimOn(resting(8)))
    }

    @Test
    fun withAnimationsOffTheRimSnaps() {
        open(focus = 8, animationsOn = false)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        frame()
        frame()
        assertTrue("it snaps", rimOn(resting(9)))
    }

    @Test
    fun everyNameKeepsItsLineAtEveryFrameOfAMove() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        val next = resting(9)
        val line = nameBottom(next)
        move(Direction.Right)
        repeat(14) { step ->
            frame()
            assertTrue("frame $step: the name stays on its line", abs(nameBottom(next) - line) <= 1)
        }
    }

    @Test
    fun theFocusedIconGrowsAboutItsCentreAndTheRimStandsOnItsEdge() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        val density = compose.density.density
        val icon = iconOf(resting(8))
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val want = rimOnPage()
        fun isRim(dy: Float): Boolean {
            val p = pixels[icon.center.x.roundToInt(), (icon.top - dy * density).roundToInt()]
            return abs(p.red - want.red) < 0.12f && abs(p.green - want.green) < 0.12f && abs(p.blue - want.blue) < 0.12f
        }
        val inner = growthDp
        val outer = growthDp + FocusLift.rimWidth.value
        assertTrue("the rim's middle stands ${inner + 1f} dp above the rest icon", isRim(inner + 1f))
        assertTrue("nothing of the rim past its outer edge (${outer + 1.5f} dp)", !isRim(outer + 1.5f))
        // Symmetric about the centre: the same on the left side.
        val left = pixels[(icon.left - (inner + 1f) * density).roundToInt(), icon.center.y.roundToInt()]
        assertTrue("the left side is the rim too", abs(left.red - want.red) < 0.12f && abs(left.green - want.green) < 0.12f)
    }

    private fun assertAPressDipsTheIconNotTheName(index: Int) {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        val rest = resting(index)
        val name = pixelsIn(nameBand(rest))
        val icon = pixelsIn(iconOf(rest))
        compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(PRESS_MS)
        assertNotEquals("the icon dips", icon, pixelsIn(iconOf(rest)))
        assertEquals("the name stays", name, pixelsIn(nameBand(rest)))
    }

    @Test
    fun aPressOnTheFocusedTileDipsTheIconNotTheName() = assertAPressDipsTheIconNotTheName(8)

    @Test
    fun aPressOnARestingTileDipsTheIconNotTheName() = assertAPressDipsTheIconNotTheName(9)

    @Test
    fun theGlowClearsTheName() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        val rest = resting(8)
        val band = nameBand(rest)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        for (x in band.left.toInt()..band.right.toInt()) assertTrue("nothing of the glow reaches the name's top", level(pixels, x.toFloat(), band.top) <= 1)
    }

    @Test
    fun aGridAtRestWritesNoState() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        move(Direction.Right)
        compose.mainClock.advanceTimeBy(PlumMotion.GLIDE_MS + 500L)
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
            assertTrue("no state changes while the focus rests (a sweep, a grow or a timer would): ${writes.flatten().map { it.javaClass.name }.distinct()} ${writes.flatten().take(5)}", writes.isEmpty())
        } finally {
            observer.dispose()
        }
    }

    /** The glow is there next to the rim and is gone before the name's text area: no tint, whatever the page. */
    private fun assertTheGlowDoesNotTintTheName(theme: PlumThemeSettings) {
        open(focus = 8, theme = theme)
        compose.mainClock.advanceTimeBy(1000)
        val density = compose.density.density
        val rest = resting(8)
        val icon = iconOf(rest)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val beyondRim = growthDp + FocusLift.rimWidth.value + 1f
        val control = level(pixels, icon.center.x, icon.top - beyondRim * density)
        assertTrue("the glow is there to be measured (level $control)", control > 3)
        val top = icon.bottom + AppsGridDimens.appsGridNameGap.value * density
        var worst = 0
        // The name's own ink is not page: look at the row just above its box and the first row of it, across the whole tile.
        for (y in listOf(top.roundToInt() - 2, top.roundToInt() + 1)) {
            for (x in rest.left.roundToInt() until rest.right.roundToInt()) {
                if (y == top.roundToInt() + 1) continue
                worst = maxOf(worst, level(pixels, x.toFloat(), y.toFloat()))
            }
        }
        assertTrue("the glow tints the gap above the name by $worst levels", worst <= 3)
    }

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theGlowDoesNotTintTheNameAt768Graphite() = assertTheGlowDoesNotTintTheName(TableText.Graphite)

    @Test
    @Config(qualifiers = "w768dp-h432dp-land-xhdpi")
    fun theGlowDoesNotTintTheNameAt768Paper() = assertTheGlowDoesNotTintTheName(TableText.Paper)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theGlowDoesNotTintTheNameAt576Graphite() = assertTheGlowDoesNotTintTheName(TableText.Graphite)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theGlowDoesNotTintTheNameAt576Paper() = assertTheGlowDoesNotTintTheName(TableText.Paper)

    private fun assertNoGlowOrHalo(theme: PlumThemeSettings) {
        open(focus = 8, theme = theme)
        compose.mainClock.advanceTimeBy(1000)
        val density = compose.density.density
        val rest = resting(8)
        val icon = iconOf(rest)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val beside = level(pixels, icon.left - (growthDp + FocusLift.reach.value + HALO_PROBE_DP) * density, icon.center.y)
        assertTrue("beside the rim, past the glow: nothing (was $beside)", beside <= 1)
        val under = level(pixels, rest.center.x, rest.bottom + density)
        assertTrue("under the tile: no glow (was $under)", under <= 1)
        val plate = level(pixels, rest.left + 2f * density, rest.bottom - 2f * density)
        assertTrue("no plate behind the name (was $plate)", plate <= 1)
    }

    @Test
    fun theFocusedTileHasNoGlowOrHaloOnGraphite() = assertNoGlowOrHalo(TableText.Graphite)

    @Test
    fun theFocusedTileHasNoGlowOrHaloOnPaper() = assertNoGlowOrHalo(TableText.Paper)

    @Test
    fun noTileIsFramedAtRest() {
        open(focus = 8)
        compose.mainClock.advanceTimeBy(1000)
        // Its edge and the gap around it are the background.
        val tile = resting(0)
        val density = compose.density.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        fun level(x: Float, y: Float): Int {
            val p = pixels[x.roundToInt(), y.roundToInt()]
            return (maxOf(abs(p.red - background.red), abs(p.green - background.green), abs(p.blue - background.blue)) * 255f).roundToInt()
        }
        for (dy in listOf(-4f, -1f, 0.5f)) assertTrue("no rim or shadow at the tile's top edge ($dy dp)", level(tile.left + 2 * density, tile.top + dy * density) <= 1)
        for (dx in listOf(-4f, -1f, 0.5f)) assertTrue("no rim or shadow at the tile's left edge ($dx dp)", level(tile.left + dx * density, tile.center.y) <= 1)
        assertTrue("no shadow under the tile", level(tile.left + 2 * density, tile.bottom + 3 * density) <= 1)
    }

    private companion object {
        const val IDLE_MS = 5_000L
        const val HALO_PROBE_DP = 3f
        const val PRESS_MS = 500L
    }
}
