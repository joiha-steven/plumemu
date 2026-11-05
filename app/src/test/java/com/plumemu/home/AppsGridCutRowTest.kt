package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.EdgeFadesKey
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
 * The Apps grid shows a row cut at its top or bottom edge only when most of it shows (and then faded); a row mostly cut is left
 * out, so no sliver of icon tops or names stands above the hint bar (design/rules.md › Lists that scroll). Checked on the pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AppsGridCutRowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var text by mutableStateOf(TextSize.Large)

    private fun cell(index: Int): Rect? {
        if (compose.onAllNodesWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) return null
        val b = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val d = compose.density.density
        return Rect(b.left.value * d, b.top.value * d, b.right.value * d, b.bottom.value * d)
    }

    private fun assertNoSlivers(size: TextSize) {
        text = size
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = text)) { AppsGridBoard(focused = 0, onState = { state = it }) } }
        compose.runOnIdle { checkNotNull(state).dispatch(Action.TapItem(Column.Games, 0)) }
        compose.waitForIdle()
        var leftOut = 0
        val problems = mutableListOf<String>()
        for (step in 0 until AppsGridApps.size) {
            val grid = compose.onAllNodes(SemanticsMatcher.keyIsDefined(EdgeFadesKey), useUnmergedTree = true).fetchSemanticsNodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == HomeTags.GRID }
            val box = grid.boundsInRoot
            val cells = (0 until AppsGridApps.size).mapNotNull { cell(it) }
            val shot = compose.onRoot().captureToImage().toPixelMap()
            for (c in cells) {
                val shown = if (c.top < box.bottom - 1f && c.bottom > box.bottom + 1f) (box.bottom - c.top) / c.height else if (c.top < box.top - 1f && c.bottom > box.top + 1f) (c.bottom - box.top) / c.height else continue
                if (shown >= MOST) continue
                leftOut++
                // Nothing of the row stands inside the window: every pixel there is the page.
                val from = if (c.top < box.top) box.top else c.top
                val to = if (c.top < box.top) c.bottom else box.bottom
                val page = shot[2, 2]
                var odd = 0
                for (y in from.toInt() until to.toInt()) for (x in c.left.toInt() until c.right.toInt()) {
                    val p = shot[x, y]
                    if (kotlin.math.abs(p.red - page.red) > 3f / 255f || kotlin.math.abs(p.green - page.green) > 3f / 255f || kotlin.math.abs(p.blue - page.blue) > 3f / 255f) odd++
                }
                if (odd > 0) problems += "$size step $step: a row $shown shown has $odd pixels of itself in the window ($from..$to)"
            }
            compose.runOnIdle { checkNotNull(state).dispatch(Action.Move(Direction.Down, isRepeat = true)) }
            compose.waitForIdle()
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("$size: a row is mostly cut at some step", leftOut > 0)
    }

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun largeAt832() = assertNoSlivers(TextSize.Large)

    @Test
    @Config(qualifiers = "w832dp-h468dp-land-xhdpi")
    fun largestAt832() = assertNoSlivers(TextSize.Largest)

    private companion object {
        /** The share of a cut row that must show for it to stay (faded); less and it is left out (`GridEdgeFades.minShown`). */
        const val MOST = 0.75f
    }
}
