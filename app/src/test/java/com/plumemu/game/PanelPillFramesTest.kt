package com.plumemu.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.BoxArtPanelFixtures
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.appComposeRule
import com.plumemu.home.slabOverhangPx
import com.plumemu.home.slabTopRow
import com.plumemu.home.rulesAround
import com.plumemu.home.search.SearchIndex
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchResults
import com.plumemu.home.search.SearchTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The focus pill of the game menu, Fix name, Fix box art and Search's results glides from the row it leaves to the row it
 * lands on, in the list glide's own 200 ms on its decelerating curve (the PS Vita's way); with animations
 * off it jumps. The pill is a draw, not a node, so it is found in the pixels the screen draws: the first pixel row of the list
 * whose width is mostly the slab's fill is just under the pill's top edge. Frame by frame on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PanelPillFramesBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** One list under test: what to show, how to move one row down, and where to find its parts. */
    private class Scene(
        val listTag: String,
        val rowTag: (Int) -> String,
        /** The row the focus starts on, and how many rows the list has. */
        val first: Int,
        val down: () -> Unit,
        /** Under each row, inside its box (a game row's tap gap). */
        val gap: Dp = 0.dp,
        /** False when a field sits right above the list: its pixels look like the slab, so the scan starts at the list's top (the focused row is then not the first). */
        val scanAbove: Boolean = true,
        /** Steps taken after the list is shown, before the test starts, to leave the first row. */
        val lead: Int = 0,
        val content: @androidx.compose.runtime.Composable () -> Unit,
    )

    private val game = GameRef("snes-1", "Super Metroid")

    private fun menu(): Scene {
        val rows = List(30) { PanelItem.Row(PanelRow("r$it", UiText.Plain("Row $it"), PanelCommand.Back)) }
        var panel by mutableStateOf(OpenPanel(game, PanelScreen.Message(PanelContent(UiText.Plain("Menu"), items = rows)), 0))
        return Scene(GamePanelTags.ROWS, { GamePanelTags.row("r$it") }, 0, { panel = panel.copy(focus = panel.focus + 1) }) {
            Box { GamePanelOverlay(panel, {}) }
        }
    }

    private fun search(): Scene {
        val content = FakeHomeContent.large(300)
        val state =
            SearchState().apply {
                open()
                onQueryChange("title")
                showResults("title", SearchIndex.of(content).find("title"))
                toResults()
            }
        return Scene(SearchTags.RESULTS, SearchTags::result, 0, { state.onAction(Action.Move(Direction.Down)) }, gap = PlumDimens.listRowGap) {
            // Room above the list: its first row's slab is drawn past the row.
            Box(Modifier.padding(vertical = 16.dp)) { SearchResults(state, {}, isPanelOpen = false) }
        }
    }

    private fun fixName(): Scene {
        val finder = TitleFinder(List(60) { TitleChoice("Title $it", "USA", 1990, null) })
        val state = FixNameState("title", finder).also { it.showResults("title", finder.find("title")) }
        state.onAction(Action.Move(Direction.Down))
        // One more step on to the second result: a field sits right above the list, so the first row's slab is not scanned for.
        return Scene(FixNameTags.RESULTS, { GamePanelTags.row(resultKey(it)) }, 0, { state.onAction(Action.Move(Direction.Down)) }, scanAbove = false, lead = 1) {
            Column { FixNameBody(state, {}) }
        }
    }

    private fun fixBoxArt(): Scene {
        val hits = List(30) { BoxArtPanelFixtures.portrait.copy(id = "h$it", name = "Game $it") }
        val state = BoxArtPanelFixtures.results(hits)
        repeat(2) { state.onAction(Action.Move(Direction.Down)) }
        val tag = { i: Int -> GamePanelTags.row(if (i == 0) BOX_ART_SEARCH_KEY else boxArtHitKey(hits[i - 1])) }
        return Scene(FixBoxArtTags.ROWS, tag, 1, { state.onAction(Action.Move(Direction.Down)) }, scanAbove = false, lead = 1) {
            Column { FixBoxArtBody(state, {}) }
        }
    }

    private fun show(
        scene: Scene,
        animationsOn: Boolean,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalGlideClock provides { compose.mainClock.currentTime * 1_000_000L }) {
                PlumTheme(TableText.Default.copy(animationsOn = animationsOn)) { scene.content() }
            }
        }
        compose.waitForIdle()
    }

    private fun bounds(tag: String): Rect? =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun listBounds(scene: Scene): Rect = compose.onNodeWithTag(scene.listTag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun Color.isNear(other: Color) = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < 0.03f

    /** How far the slab is drawn above its row's box: [pillTop] reports the box, so the glide is checked in row positions. */
    private var overhang = 0f

    /** The slab fill's colour on the row [rowTag] at rest; also takes that row's overhang. */
    private fun ringColour(scene: Scene, rowTag: String): Color {
        val list = listBounds(scene)
        overhang = compose.slabOverhangPx(rowTag, scene.gap)
        return compose.onRoot().captureToImage().toPixelMap()[((list.left + list.right) / 2).toInt(), bounds(rowTag)!!.top.toInt() + EDGE_PX + 1]
    }

    /** The top of the pill's row box now (the slab's drawn top border plus its overhang); -1 when none. [ring] only marks that a row was measured. */
    @Suppress("UNUSED_PARAMETER")
    private fun pillTop(
        scene: Scene,
        ring: Color,
    ): Int {
        val list = listBounds(scene)
        // A middle band, clear of the slab's rounded ends.
        val xs = (list.left + list.width * 0.45f).toInt() until (list.left + list.width * 0.65f).toInt()
        // The slab of a first row is drawn above the list's top, inside the list's clip bleed.
        val from = list.top.toInt() - (if (scene.scanAbove) overhang.toInt() + 1 else 0)
        val y = compose.onRoot().captureToImage().slabTopRow(xs, from, list.bottom.toInt())
        return if (y < 0) -1 else Math.round(y + overhang)
    }

    /** One drawn frame after a move: the pill's top and the focused row's drawn top (null while it is not composed). */
    private data class Frame(
        val pill: Int,
        val row: Float?,
        val listTop: Float,
        val listBottom: Float,
    )

    private fun move(
        scene: Scene,
        animationsOn: Boolean,
        preMoves: Int,
        frames: Int = PlumMotion.GLIDE_MS / FRAME_MS + 4,
    ): Pair<Int, List<Frame>> {
        show(scene, animationsOn)
        repeat(preMoves + scene.lead) {
            compose.runOnIdle { scene.down() }
            compose.waitForIdle()
        }
        val at = scene.first + preMoves + scene.lead
        val oldTop = bounds(scene.rowTag(at))!!.top
        val ring = ringColour(scene, scene.rowTag(at))
        assertEquals("at rest the pill is on the focused row", oldTop.toInt(), pillTop(scene, ring))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { scene.down() }
        val list = listBounds(scene)
        val out =
            (0 until frames).map {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                Frame(pillTop(scene, ring), bounds(scene.rowTag(at + 1))?.top, list.top, list.bottom)
            }
        return oldTop.toInt() to out
    }

    private fun frameCount(ms: Int) = ms / FRAME_MS

    /** A step with no scrolling: from the old row's top to the new row's, in the glide, decelerating. */
    private fun assertStepGlides(
        what: String,
        scene: Scene,
        preMoves: Int = 0,
    ) {
        val (old, frames) = move(scene, true, preMoves)
        val new = frames.last().row!!.toInt()
        val tops = frames.map { it.pill }.dropWhile { it == old }
        println("$what pill tops (old $old, new $new): $tops")
        assertTrue("$what: the pill moved on a one-row step: $tops", new > old && tops.isNotEmpty())
        assertTrue("$what: within the old and new rows, only down: $tops", tops.all { it in old..new } && tops.zipWithNext().all { (a, b) -> b >= a })
        assertTrue("$what: the first frame covers no more than 30%: $tops", tops.first() - old <= (new - old) * FIRST_FRAME_SHARE)
        assertTrue("$what: still travelling after a third of the glide: $tops", tops[frameCount(PlumMotion.GLIDE_MS) / 3] < new)
        assertEquals("$what: on the new row within the glide and a frame: $tops", new, tops[frameCount(PlumMotion.GLIDE_MS) + 1])
        assertEquals("$what: ends exactly on the row: $tops", new, tops.last())
    }

    /** A step that scrolls the list: the pill stays within a stride of the row it follows, closes the gap and ends on it. */
    private fun assertScrollStepGlidesWithTheList(
        what: String,
        scene: Scene,
        preMoves: Int,
    ) {
        val (_, frames) = move(scene, true, preMoves)
        val lags = frames.mapNotNull { f -> f.row?.let { f.pill - it.toInt() } }
        println("$what pill - row lag per frame: $lags")
        val end = frames.last()
        assertTrue("$what: the list scrolled (the row is composed and the pill moved): $lags", lags.size > frameCount(PlumMotion.GLIDE_MS) / 2)
        assertEquals("$what: ends on the focused row: $lags", 0, lags.last())
        assertTrue("$what: the pill never leaves the list: ${frames.map { it.pill }}", frames.all { it.pill >= it.listTop.toInt() - 1 && it.pill <= it.listBottom.toInt() })
        assertTrue("$what: the gap to the row only closes: $lags", lags.zipWithNext().all { (a, b) -> abs(b) <= abs(a) + 2 })
        assertTrue("$what: the gap is still open after a third of the glide, and closed by its end: $lags", abs(lags[frameCount(PlumMotion.GLIDE_MS) / 3]) > 0 && abs(lags[frameCount(PlumMotion.GLIDE_MS) + 1]) <= 1 && end.row != null)
    }

    private fun assertJumpsWithAnimationsOff(
        what: String,
        scene: Scene,
    ) {
        val (_, frames) = move(scene, false, 0)
        val new = frames.last().row!!.toInt()
        val tops = frames.map { it.pill }
        assertTrue("$what, animations off: the pill is on the new row at once: $tops", tops.all { it == new })
    }

    @Test fun gameMenuPillGlidesOnAStep() = assertStepGlides("menu", menu())

    @Test fun gameMenuPillGlidesWithTheListWhenItScrolls() = assertScrollStepGlidesWithTheList("menu", menu(), preMoves = 14)

    @Test fun gameMenuPillJumpsWithAnimationsOff() = assertJumpsWithAnimationsOff("menu", menu())

    @Test fun searchPillGlidesOnAStep() = assertStepGlides("search", search())

    @Test fun searchPillGlidesWithTheListWhenItScrolls() = assertScrollStepGlidesWithTheList("search", search(), preMoves = 14)

    @Test fun searchPillJumpsWithAnimationsOff() = assertJumpsWithAnimationsOff("search", search())

    @Test fun fixNamePillGlidesOnAStep() = assertStepGlides("fix name", fixName())

    @Test fun fixNamePillJumpsWithAnimationsOff() = assertJumpsWithAnimationsOff("fix name", fixName())

    @Test fun fixBoxArtPillGlidesOnAStep() = assertStepGlides("fix box art", fixBoxArt())

    @Test fun fixBoxArtPillJumpsWithAnimationsOff() = assertJumpsWithAnimationsOff("fix box art", fixBoxArt())

    private companion object {
        const val FRAME_MS = 16

        /** The slab's 1 dp hairline in px at the test's density (2x): the fill starts this far below the slab's top. */
        const val EDGE_PX = 2
        const val FIRST_FRAME_SHARE = 0.3f
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PanelPillFramesTest : PanelPillFramesBase()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class PanelPillFrames576Test : PanelPillFramesBase()
