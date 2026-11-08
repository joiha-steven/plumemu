package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The focused cover's size, pivot, rim colour and glow, and that its neighbours stay as they are (helpers: [GridCoverFocusBase]). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = SIZE_640)
class GridCoverFocusTest : GridCoverFocusBase() {
    @Test
    fun aNeighbourStaysWholeWhicheverSideTheFocusIsOn() {
        show(PlumThemeSettings.Graphite)
        focus(0)
        val middle = collar(1)
        assertEquals("three covers in the first row", cell(0).top, cell(2).top)
        val focusLeft = pixels()
        focus(2)
        val focusRight = pixels()
        val corner = (CollarDimens.coverBoxCorner.value + CollarSize.Cover.sidePadding.value) * d
        var differing = 0
        var first = ""
        // One pixel in: the collar's outermost is anti-aliased, the page or the halo under it shows through.
        for (x in middle.left.roundToInt() + 1 until middle.right.roundToInt() - 1) {
            for (y in middle.top.roundToInt() + 1 until middle.bottom.roundToInt() - 1) {
                val inCornerX = x < middle.left + corner || x > middle.right - corner
                val inCornerY = y < middle.top + corner || y > middle.bottom - corner
                if (inCornerX && inCornerY) continue
                if (!near(focusLeft[x, y], focusRight[x, y], 8f / 255f)) {
                    if (differing == 0) first = "${x - middle.left}, ${y - middle.top} of ${middle.width} x ${middle.height}: ${focusLeft[x, y]} ${focusRight[x, y]}"
                    differing++
                }
            }
        }
        assertEquals("pixels of the neighbour that the focus changes, first at $first", 0, differing)
    }

    @Test
    fun focusedCoverGrowsAboutItsPivotAndNothingMoves() {
        show(TableText.Graphite)
        assertOwnSize("640x480 snes", 1, 0)
    }

    @Test
    fun mixedSystemCoverGrowsAboutItsPivotToo() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertOwnSize("640x480 recent", 1, 0)
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun focusedCoverGrowsAboutItsPivotAt832() {
        show(TableText.Graphite)
        assertOwnSize("832x468 snes", 1, 0)
    }

    @Test
    fun rimContrastGraphite() {
        show(TableText.Graphite)
        assertRimContrast("graphite")
    }

    @Test
    fun rimContrastPaper() {
        show(TableText.Paper)
        assertRimContrast("paper")
    }

    @Test
    fun rimContrastPaperWithDarkTealIsStillTheInk() {
        show(TableText.Paper.copy(accent = AccentChoice.Custom(Rgb(0x0F5C5C))))
        assertRimContrast("paper teal")
    }

    @Test
    fun tightGlowGraphite() {
        show(TableText.Graphite)
        assertTightGlow("graphite")
    }

    @Test
    fun tightGlowPaper() {
        show(TableText.Paper)
        assertTightGlow("paper")
    }

    @Test
    fun tightGlowPaperWithDarkTealIsStillTheInk() {
        show(TableText.Paper.copy(accent = AccentChoice.Custom(Rgb(0x0F5C5C))))
        assertTightGlow("paper teal")
    }
}
