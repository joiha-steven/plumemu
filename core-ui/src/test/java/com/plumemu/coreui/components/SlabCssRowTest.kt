package com.plumemu.coreui.components

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorMath
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A focused slab ("lifted card") drawn at 1.5 px per dp, checked against what its look says: one flat face at the top, middle and bottom
 * ([SlabLook.fill]: no gradient, no inset shadow, no foot band), no line of any kind round it (every pixel of its top, sides and foot is the face), the face clearly lighter than the page
 * on Banana, Paper and Graphite, and the soft drop shadow under it darker than the page, longer and stronger below than above.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabCssRowTest {
    private val density = 1.5f
    private val left = 60
    private val top = 120
    private val width = 630
    private val height = 70

    private class Shot(val page: IntArray, val look: SlabLook, val px: (Int, Int) -> IntArray)

    private fun shot(settings: PlumThemeSettings, glow: Boolean = true): Shot {
        val c = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))
        val page = Color(settings.backgroundColor.toArgb() or 0xFF000000.toInt())
        val image = ImageBitmap(760, 320)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(760f, 320f)) {
            drawRect(page)
            val outline = Outline.Rounded(RoundRect(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat(), 20f, 20f))
            drawSlabOutside(outline, c.slab, if (glow) c.slabGlow else Color.Transparent, under = c.bg)
            drawSlabFill(outline, c.slab, onCard = false)
            drawSlabFace(outline, c)
        }
        val map = image.toPixelMap()
        return Shot(rgb(page), c.slab) { x, y -> rgb(map[x, y]) }
    }

    private fun rgb(c: Color) = IntArray(3) { (c.toArgb() shr (16 - 8 * it)) and 0xFF }

    /** The look's flat face: opaque, so the page does not show through, the same on every row. */
    private fun faceOf(look: SlabLook): DoubleArray = doubleArrayOf(look.fill.red * 255.0, look.fill.green * 255.0, look.fill.blue * 255.0)

    private fun assertNear(
        what: String,
        expected: DoubleArray,
        actual: IntArray,
        tolerance: Double = 2.0,
    ) {
        for (ch in 0..2) assertEquals("$what channel $ch: ${actual.toList()} vs ${expected.map { Math.round(it) }}", expected[ch], actual[ch].toDouble(), tolerance)
    }

    private fun checkFill(settings: PlumThemeSettings) {
        val s = shot(settings)
        val x = left + width / 2
        // The foot included: glass has no inset shadow darkening it.
        for (row in listOf(5, 35, height - 5)) assertNear("row $row", faceOf(s.look), s.px(x, top + row))
    }

    @Test
    fun graphiteFillIsTheLooksFace() = checkFill(PlumThemeSettings.Graphite)

    @Test
    fun paperFillIsTheLooksFace() = checkFill(PlumThemeSettings.Paper)

    @Test
    fun bananaFillIsTheLooksFace() = checkFill(banana)

    private val banana: PlumThemeSettings get() = ColorSet.Banana.let { PlumThemeSettings(background = it.background, text = it.textMode.color, accent = it.accent) }

    @Test
    fun theSlabHasNoOutlineOnGraphite() = checkNoOutline(PlumThemeSettings.Graphite)

    @Test
    fun theSlabHasNoOutlineOnPaper() = checkNoOutline(PlumThemeSettings.Paper)

    @Test
    fun theSlabHasNoOutlineOnBanana() = checkNoOutline(banana)

    /**
     * No ring along the top, the sides or the foot: every pixel there, an inch inside the rounded corners, is the face (not a lit line, not a
     * dark foot, not a white side). Below the foot only the shadow is, and it is darker than the page.
     */
    private fun checkNoOutline(settings: PlumThemeSettings) {
        val s = shot(settings)
        val face = faceOf(s.look)
        val mid = top + height / 2
        val cx = left + width / 2
        for (y in top..top + 3) assertNear("top rows at $y", face, s.px(cx, y))
        for (y in top + height - 4 until top + height) assertNear("foot rows at $y", face, s.px(cx, y))
        for (y in top + 25..top + height - 25) {
            for (x in listOf(left, left + 1, left + width - 2, left + width - 1)) assertNear("side at ($x, $y)", face, s.px(x, y))
        }
        // The accent glow lights the page a little round a dark slab; the shadow alone is darker than the page.
        val shadowRow = shot(settings, glow = false).px(cx, top + height + 3)
        assertTrue("the shadow under the foot is darker than the page: ${shadowRow.toList()} vs ${s.page.toList()}", lightness(shadowRow) < lightness(s.page) - 0.2)
        assertTrue("mid row is the face", s.px(cx, mid).let { p -> (0..2).all { abs(p[it] - face[it]) <= 2.0 } })
    }

    private fun lightness(p: IntArray) = ColorMath.lightness(Rgb(p[0] shl 16 or (p[1] shl 8) or p[2]))

    /** The face reads at a glance, as drawn: 12 L* over a dark page, 8 over the yellow one, 5 over Paper (a page at L* 92 has only 8 L* below white; 70 % white gives it 5.5). */
    @Test
    fun theFaceStandsClearOfThePageOnBananaPaperAndGraphite() {
        for ((name, settings, min) in listOf(Triple("banana", banana, 8.0), Triple("paper", PlumThemeSettings.Paper, 5.0), Triple("graphite", PlumThemeSettings.Graphite, 12.0))) {
            val s = shot(settings)
            val gap = lightness(s.px(left + width / 2, top + height / 2)) - lightness(s.page)
            assertTrue("$name: the face is $gap L* over the page, wanted at least $min", gap >= min)
        }
    }

    /** The shadow (the glow left out, which lights a dark page) moves the page toward black: darker (in L*) 1, 3 and 6 dp under the foot, and below more than above the slab. */
    @Test
    fun theShadowIsDarkerThanThePageBelowMoreThanAbove() {
        for (settings in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper, banana)) {
            val s = shot(settings, glow = false)
            val x = left + width / 2
            val page = lightness(s.page)
            for (dp in listOf(1, 3, 6)) {
                val d = Math.round(dp * density)
                val below = page - lightness(s.px(x, top + height + d))
                val above = page - lightness(s.px(x, top - 1 - d))
                assertTrue("${settings.backgroundColor} $dp dp: shadow below ($below L*) darkens the page", below > 0.3)
                assertTrue("${settings.backgroundColor} $dp dp: below $below stronger than above $above", below > above + 0.2)
            }
        }
    }
}
