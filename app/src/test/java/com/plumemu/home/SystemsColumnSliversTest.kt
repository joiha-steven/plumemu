package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The systems column runs under both bars (design/rules.md › Lists that scroll). At rest a row cut at a bar that shows under half of itself is left out whole, as its one
 * centred line would show only as glyph tops or feet (with the fade setting on or off, in the open column and in the numbers beside a list or a grid), a row showing half or more
 * is drawn, and the focused row is never cut.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SystemsColumnSliversTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var size by mutableStateOf(TextSize.Small)

    /** What one walk over the sizes and focus positions found. */
    private class Found {
        var slivers = 0
        var shownParts = 0
        val problems = mutableListOf<String>()
    }

    private fun walk(
        settings: PlumThemeSettings,
        beside: Boolean = false,
        gridIds: Set<String> = emptySet(),
    ): Found {
        val content = FakeHomeContent.board()
        val found = Found()
        compose.setContent { PlumTheme(settings.copy(textSize = size)) { HomeAt(content, listOf(Action.TapItem(Column.Systems, 0)), gridIds = gridIds, onState = { state = it }) } }
        val d = compose.density.density
        for (text in TextSize.entries) {
            size = text
            // Beside the games: walked row by row down and back up, as the column's rest depends on the way it came.
            val steps = if (beside) List(content.entries.size) { Direction.Down } + List(content.entries.size) { Direction.Up } else listOf(null, null, null, null)
            val tapped = listOf(0, content.entries.size / 2, content.entries.lastIndex, 3)
            for ((step, way) in steps.withIndex()) {
                val focus = tapped.getOrElse(step) { 0 }
                compose.runOnIdle {
                    checkNotNull(state).dispatch(Action.Back)
                    if (way != null) checkNotNull(state).dispatch(Action.Move(way)) else checkNotNull(state).dispatch(Action.TapItem(Column.Systems, focus))
                    if (beside) checkNotNull(state).dispatch(Action.Enter)
                }
                compose.waitForIdle()
                compose.mainClock.advanceTimeBy(1_000)
                compose.waitForIdle()
                val label = "$text focus $focus"
                val window = compose.onNodeWithTag(HomeTags.SYSTEMS, useUnmergedTree = true).getUnclippedBoundsInRoot()
                val (wTop, wBottom, wLeft, wRight) = listOf(window.top.value * d, window.bottom.value * d, window.left.value * d, window.right.value * d)
                val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                val background = bitmap.getPixel(2, bitmap.height / 2)
                val nodes = content.entries.indices.mapNotNull { i -> compose.onAllNodesWithTag(HomeTags.system(i), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { i to it } }
                val rowPx = nodes.maxOf { it.second.boundsInRoot.height }
                // What the bars draw themselves (a key's collar reaching up into the column's foot) is not a row's ink.
                val own = compose.barsOwnBounds().map { it.inflate(KEY_SHADOW_DP * d) }
                for ((index, node) in nodes) {
                    val top = node.boundsInRoot.top
                    val bottom = node.boundsInRoot.bottom
                    if (bottom <= wTop || top >= wBottom) continue
                    val shown = (bottom - top) / rowPx
                    var ink = false
                    for (y in maxOf(top, wTop).toInt() until minOf(bottom, wBottom).toInt()) for (x in maxOf(wLeft.toInt(), 0) until wRight.toInt()) if (bitmap.getPixel(x, y) != background && own.none { it.contains(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())) }) { ink = true; break }
                    if (index == checkNotNull(state).focus.systemIndex) {
                        if (!beside && shown < 1f - 1f / rowPx) found.problems += "$label: the focused system $index shows only $shown of itself"
                        continue
                    }
                    if (shown >= 1f - 1f / rowPx) continue
                    if (shown < 1f / 2f - 1f / rowPx) {
                        found.slivers++
                        if (ink) found.problems += "$label: system $index shows $shown of itself at a bar, under half, and leaves ink"
                    } else if (ink) {
                        found.shownParts++
                    }
                }
            }
        }
        return found
    }

    private fun assertRule(found: Found) {
        assertTrue(found.problems.joinToString("\n"), found.problems.isEmpty())
        assertTrue("no row under half was met, so nothing proves it is left out", found.slivers > 0)
        assertTrue("no row of half or more was met cut at a bar", found.shownParts > 0)
    }

    @Test
    fun aRowUnderHalfIsLeftOutAndTheFocusedRowIsNeverCut() = assertRule(walk(PlumThemeSettings.Graphite))

    @Test
    fun theSameWithTheFadeSettingOff() = assertRule(walk(PlumThemeSettings.Graphite.copy(edgeFadeOn = false)))

    @Test
    @Config(qualifiers = THOR)
    fun theNumbersBesideAListFollowIt() = assertNoInk(walk(PlumThemeSettings.Graphite, beside = true))

    @Test
    @Config(qualifiers = THOR)
    fun theNumbersBesideAGridFollowIt() = assertNoInk(walk(PlumThemeSettings.Graphite, beside = true, gridIds = setOf("snes", "gba", "psx")))

    private fun assertNoInk(found: Found) {
        assertTrue(found.problems.joinToString("\n"), found.problems.isEmpty())
        assertTrue("no row under half was met beside the games", found.slivers > 0)
    }
}

private const val THOR = "w837dp-h471dp-land-xhdpi"

/** A hint key's soft shadow reaches this far past its collar, into the column's foot. */
private const val KEY_SHADOW_DP = 8f
