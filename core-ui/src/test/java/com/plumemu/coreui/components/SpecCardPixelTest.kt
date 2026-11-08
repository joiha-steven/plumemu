package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The game menu card (PANEL) and the empty-state mark drawn at the boards' scale (1.5 px per dp, a pixel is a board px), read against the
 * board's own render: the card's gradient at its top, middle and bottom and its lit top border; the tile's radial fill and lit top edge, the
 * disc's shaded top and the lit lip under it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SpecCardPixelTest {
    private fun shot(
        set: PlumThemeSettings,
        draw: DrawScope.(PlumComposeColors) -> Unit,
    ): (Int, Int) -> IntArray {
        val tokens = PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor)
        val c = PlumComposeColors.from(tokens)
        val image = ImageBitmap(600, 700)
        CanvasDrawScope().draw(Density(1.5f), LayoutDirection.Ltr, Canvas(image), Size(600f, 700f)) {
            drawRect(c.bg)
            draw(c)
        }
        val map = image.toPixelMap()
        return { x, y -> rgb(map[x, y]) }
    }

    private fun rgb(c: Color) = IntArray(3) { (c.toArgb() shr (16 - 8 * it)) and 0xFF }

    private fun near(
        what: String,
        expected: IntArray,
        actual: IntArray,
        tolerance: Int = 3,
    ) = assertTrue("$what: board ${expected.toList()} ours ${actual.toList()}", expected.indices.all { kotlin.math.abs(expected[it] - actual[it]) <= tolerance })

    /** The card's gradient at [y] (card top 40, 600 px tall): the ladder's raised step one L* above the page at the top to one below at the foot. */
    private fun ladderAt(
        settings: PlumThemeSettings,
        y: Int,
    ): IntArray {
        val bg = settings.backgroundColor
        val top = com.plumemu.coreui.theme.ToneLadder.level(bg, com.plumemu.coreui.theme.ToneLadder.RAISED + 1)
        val bottom = com.plumemu.coreui.theme.ToneLadder.level(bg, com.plumemu.coreui.theme.ToneLadder.RAISED - 1)
        val t = (y - 40) / 600.0
        fun mix(
            a: Int,
            b: Int,
        ) = Math.round(a + (b - a) * t).toInt()
        return intArrayOf(mix(top.red, bottom.red), mix(top.green, bottom.green), mix(top.blue, bottom.blue))
    }

    /** The card is the tone ladder's raised step (a design change from the board's greys); the border's top side is white .22. */
    @Test
    fun theGraphiteCardIsTheBoardsBlueGreyGradient() {
        val px = shot(PlumThemeSettings.Graphite) { drawRaisedCard(it.card, 50f, 40f, 440f, 600f) }
        near("near the top", ladderAt(PlumThemeSettings.Graphite, 50), px(270, 50))
        near("near the bottom", ladderAt(PlumThemeSettings.Graphite, 620), px(270, 620))
        val top = px(270, 40)
        val side = px(50, 340)
        assertTrue("the top side is lit: ${top.toList()} vs side ${side.toList()}", top[0] > side[0] + 15)
        val mid = px(270, 340)
        assertTrue("cool: ${mid.toList()}", mid[2] - mid[0] >= 4)
    }

    /** The card is the ladder's raised step of the warm page; its top side is white. */
    @Test
    fun thePaperCardIsTheBoardsWarmWhiteGradient() {
        val px = shot(PlumThemeSettings.Paper) { drawRaisedCard(it.card, 50f, 40f, 440f, 600f) }
        near("near the top", ladderAt(PlumThemeSettings.Paper, 50), px(270, 50))
        near("near the bottom", ladderAt(PlumThemeSettings.Paper, 620), px(270, 620))
        assertEquals(listOf(255, 255, 255), px(270, 40).toList())
    }

    /** Board: body `font-size: 19px; max-width: 420px` (12.67 / 280 dp); ours is the ramp's 13, so the width grows by the same share. */
    @Test
    fun theEmptyBodyKeepsTheBoardsLineBreaksAtTheRampsSize() =
        assertEquals(280f * 13f / (19f / 1.5f), com.plumemu.coreui.theme.EmptyStateDimens.bodyWidth.value, 0.1f)
}
