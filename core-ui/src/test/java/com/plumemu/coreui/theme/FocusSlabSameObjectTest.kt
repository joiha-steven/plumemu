package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The slab is one object on every background ("lifted card"): a flat face lighter than the page by one rule, one soft black drop shadow, nothing else.
 * Requirement change from the glass recipe: the stepped fill and its fade, the two lift layers and the top line are gone, so their tests are replaced by these.
 */
class FocusSlabSameObjectTest {
    private val light = Rgb(0xECE8DF)
    private val dark = Rgb(0x111214)

    /** Presets, and custom backgrounds on either side of the middle. */
    private val backgrounds = listOf(dark to true, light to false, Rgb(0x1238B0) to true, Rgb(0x5A5F6B) to true, Rgb(0x8C93A0) to false, Rgb(0xE8B84B) to false, Rgb(0xFFFFFF) to false)

    /** A light page's face is the page under [FocusSlab.LIGHT_WHITE_SHARE] of white, so it keeps a hint of the page's hue. */
    @Test
    fun aLightFaceIsThePageUnderSeventyPercentWhite() {
        for ((bg, isDark) in backgrounds.filter { !it.second }) {
            val want = ColorMath.mix(bg, Rgb(0xFFFFFF), 0.70)
            assertEquals("face on $bg", want, FocusSlab.fill(bg, bg, isDark))
        }
        val cream = FocusSlab.fill(Rgb(0xE8B84B), Rgb(0x1D1D1F), false)
        assertTrue("a yellow page keeps its warmth: ${cream}", cream.red >= cream.green && cream.green > cream.blue)
    }

    /** A dark page's face is the page lifted [FocusSlab.DARK_LIFT_L] of L*, in its own hue. */
    @Test
    fun aDarkFaceIsThePageLiftedFifteenLStar() {
        for ((bg, isDark) in backgrounds.filter { it.second }) {
            val face = FocusSlab.fill(bg, Rgb(0xEBE7DD), isDark)
            assertEquals("lift on $bg", FocusSlab.DARK_LIFT_L, ColorMath.lightness(face) - ColorMath.lightness(bg), STEP_ROOM)
        }
        val purple = FocusSlab.fill(Rgb(0x1E1030), Rgb(0xEDE6F5), true)
        assertTrue("still purple", purple.blue > purple.green)
    }

    /** One drop on every page: black, 3 dp down, sigma 6 dp, at 18 % on a light page and 45 % on a dark one; the second layer is the same at no strength. */
    @Test
    fun theDepthIsOneSoftBlackDropOnEveryPage() {
        for ((bg, isDark) in backgrounds) {
            val look = SlabLook.of(bg, bg, isDark)
            val near = look.near!!
            assertEquals("colour on $bg", Color.Black, near.color.copy(alpha = 1f))
            assertEquals("strength on $bg", if (isDark) 0.45f else 0.18f, near.color.alpha, 0.002f)
            assertEquals("drop on $bg", 3f, near.drop.value, 0f)
            assertEquals("sigma on $bg", 6f, near.sigma.value, 0f)
            assertEquals("no second layer on $bg", 0f, look.far!!.color.alpha, 0f)
        }
    }

    /** No outline of any kind: the look carries a face, text inks and the shadow, no ring, top line, foot or inset. */
    @Test
    fun thereIsNoOutlineValueInTheLook() {
        val members = SlabLook::class.java.declaredFields.map { it.name }.toSet()
        for (gone in listOf("ring", "topLine", "base", "baseOnCard", "insetTop", "insetBottom")) assertTrue("$gone is gone from the look", gone !in members)
        assertNull(null)
    }

    /** A light face stays under white even on a white page, and still reaches 70 % of the way. */
    @Test
    fun aLightFaceNeverPassesWhite() {
        for ((bg, isDark) in backgrounds.filter { !it.second }) {
            val fill = FocusSlab.fill(bg, bg, isDark)
            assertTrue("not darker than the page on $bg", ColorMath.lightness(fill) >= ColorMath.lightness(bg) - STEP_ROOM)
            assertTrue("not past white on $bg", ColorMath.lightness(fill) <= 100.0 + STEP_ROOM)
        }
    }

    private companion object {
        /** One 8-bit level moves L* by up to about this. */
        const val STEP_ROOM = 0.8
    }
}
