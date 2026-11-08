package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.plumemu.coreui.components.LocalHeldKeys
import com.plumemu.coreui.components.pressFeelTouchDelayMs
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The press look on the home screen's grids and system column (design/rules.md > Shapes and motion > Pressed):
 * a finger on a cover or tile dips it (a scroll does not), and the confirm button dips only the focused item.
 * Read from the pixels of the item's own bounds: a dipped item leaves its old edge pixels as background.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PressFeelFingerTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val held = mutableStateOf<Set<String>>(emptySet())

    /** The node's pixels; [skipTop] leaves out the top band the focused row above overhangs with its slab (it dips with that row, not with this one). */
    private fun pixels(
        tag: String,
        skipTop: Int = 0,
    ): List<Int> {
        val map = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return buildList { for (y in skipTop until map.height) for (x in 0 until map.width) add(map[x, y].hashCode()) }
    }

    /** A node's pixels as [PixelGrid]: width, height and ARGB ints, to compare away from antialiased edges. */
    private class PixelGrid(val width: Int, val height: Int, val argb: List<Int>)

    private fun grid(tag: String): PixelGrid {
        val map = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return PixelGrid(map.width, map.height, buildList { for (y in 0 until map.height) for (x in 0 until map.width) add(map[x, y].toArgb()) })
    }

    private fun channelGap(a: Int, b: Int): Int =
        (0..24 step 8).maxOf { abs(((a shr it) and 0xFF) - ((b shr it) and 0xFF)) }

    /**
     * Pixels of [now] that differ from [rest] other than by 8-bit blending at an antialiased edge of [rest]: a pixel
     * that differs by more than [BLEND_NOISE], or that sits in a flat run (equal to all four neighbours at rest).
     * A real dip moves whole edges and fills, so it leaves many; a snapped layer's corner blend leaves none.
     */
    private fun realChanges(rest: PixelGrid, now: PixelGrid): List<Pair<Int, Int>> {
        assertEquals("same size", rest.width to rest.height, now.width to now.height)
        fun at(g: PixelGrid, x: Int, y: Int) = g.argb[y * g.width + x]
        return buildList {
            for (y in 0 until rest.height) for (x in 0 until rest.width) {
                val a = at(rest, x, y)
                val b = at(now, x, y)
                if (a == b) continue
                val flat = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).all { (dx, dy) ->
                    val nx = x + dx
                    val ny = y + dy
                    nx !in 0 until rest.width || ny !in 0 until rest.height || at(rest, nx, ny) == a
                }
                if (flat || channelGap(a, b) > BLEND_NOISE) add(x to y)
            }
        }
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(pressFeelTouchDelayMs() + 300)
    }

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent { PlumTheme(PlumThemeSettings.Default.copy(textSize = TextSize.Small)) { CompositionLocalProvider(LocalHeldKeys provides held) { content() } } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun showGrid() =
        show { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes")) }

    @Test
    fun aFingerOnAnUnfocusedCoverDipsThatCoverUntilItLifts() {
        showGrid()
        val tag = HomeTags.cover(1)
        val resting = pixels(tag)
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { down(center) }
        settle()
        assertNotEquals("finger down: the cover dips", resting, pixels(tag))
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(500)
        assertEquals("finger up: back at rest", resting, pixels(tag))
    }

    @Test
    fun aFingerThatScrollsDipsNothing() {
        showGrid()
        val tag = HomeTags.cover(1)
        val resting = grid(tag)
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(80f, 0f))
        }
        settle()
        val changed = realChanges(resting, grid(tag))
        assertEquals("a scroll began: the cover stays at rest (changed pixels: ${changed.take(5)})", 0, changed.size)
    }

    @Test
    fun aFingerOnAnAppsTileDipsIt() {
        show { AppsGridBoard(focused = 0) }
        val tag = HomeTags.cover(3)
        val resting = pixels(tag)
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { down(center) }
        settle()
        assertNotEquals("finger down: the tile dips", resting, pixels(tag))
    }

    @Test
    fun theConfirmButtonDipsTheFocusedAppsTileAndNotItsNeighbour() {
        show { AppsGridBoard(focused = 0) }
        compose.mainClock.advanceTimeBy(1000) // the icons land
        val restingFocused = pixels(HomeTags.cover(0))
        val restingOther = pixels(HomeTags.cover(2))
        held.value = setOf("A")
        settle()
        assertNotEquals("the focused tile dips", restingFocused, pixels(HomeTags.cover(0)))
        assertEquals("another tile does not", restingOther, pixels(HomeTags.cover(2)))
    }

    @Test
    fun theConfirmButtonDipsTheFocusedSystemRowOnly() {
        show { HomeAt(FakeHomeContent.board(), emptyList()) }
        val focused = HomeTags.system(0)
        val other = HomeTags.system(1)
        val restingFocused = pixels(focused)
        val skip = compose.slabOverhangPx(other).toInt() + 1
        val restingOther = pixels(other, skip)
        held.value = setOf("A")
        settle()
        assertNotEquals("the focused system row dips", restingFocused, pixels(focused))
        assertEquals("an unfocused one does not", restingOther, pixels(other, skip))
    }

    @Test
    fun aFingerOnAnUnfocusedSystemRowDipsIt() {
        show { HomeAt(FakeHomeContent.board(), emptyList()) }
        val other = HomeTags.system(1)
        val resting = pixels(other)
        compose.onNodeWithTag(other, useUnmergedTree = true).performTouchInput { down(center) }
        settle()
        assertNotEquals(resting, pixels(other))
    }

    @Test
    fun aQuickTapThenAScrollOnAnotherCoverDipsNeitherTheScrolledCover() {
        showGrid()
        val first = HomeTags.cover(1)
        val second = HomeTags.cover(2)
        val resting = grid(second)
        compose.onNodeWithTag(first, useUnmergedTree = true).performTouchInput {
            down(center)
            up()
        }
        compose.mainClock.advanceTimeBy(40)
        compose.onNodeWithTag(second, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(80f, 0f))
        }
        settle()
        // Compared away from 8-bit blending at an edge: on a page that is not pure black a soft layer can round one level differently.
        assertEquals("the scrolled cover shows no part of the first tap's dip", emptyList<Pair<Int, Int>>(), realChanges(resting, grid(second)))
    }

    @Test
    fun aFingerHeldOnTheFocusedCoverDipsItAndItsRingAsTheButtonDoes() {
        showGrid()
        val rest = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).captureToImage().toPixelMap().let { m -> List(m.width * m.height) { m.buffer[it] } }
        // The button first; the finger stays down at the end (a lift would tap the focused cover).
        held.value = setOf("A")
        settle()
        val button = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).captureToImage().toPixelMap().let { m -> List(m.width * m.height) { m.buffer[it] } }
        assertNotEquals("the cover and ring dipped", rest, button)
        held.value = emptySet()
        Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag(HomeTags.cover(0), useUnmergedTree = true).performTouchInput { down(center) }
        settle()
        val finger = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).captureToImage().toPixelMap().let { m -> List(m.width * m.height) { m.buffer[it] } }
        assertEquals("the finger dips exactly as the button does", button, finger)
    }

    @Test
    fun aFingerScrollOnTheAppsGridDipsNothing() {
        show { AppsGridBoard(focused = 0) }
        compose.mainClock.advanceTimeBy(1000)
        val tag = HomeTags.cover(3)
        val resting = pixels(tag)
        compose.onNodeWithTag(tag, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(80f, 0f))
        }
        settle()
        assertEquals(resting, pixels(tag))
    }}

/** Largest channel gap (of 255) 8-bit blending at an antialiased edge may leave; the checked move left 23. */
private const val BLEND_NOISE = 24
