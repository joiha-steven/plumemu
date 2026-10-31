package com.plumemu.coreui.components

import android.graphics.Bitmap
import android.util.Log
import org.robolectric.shadows.ShadowLog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import com.plumemu.coreui.theme.PlumDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A cover's glow colour (docs/design/rules.md › Grid › Cover rim and glow): its average, lifted when dark. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CoverGlowColorTest {
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.01f)

    @Test
    fun aSolidImageAveragesToItsColour() {
        val c = CoverGlowColor.average(IntArray(64) { argb(255, 200, 100, 50) })!!
        near(200 / 255f, c.red); near(100 / 255f, c.green); near(50 / 255f, c.blue)
    }

    @Test
    fun twoColoursAverageToTheirMiddle() {
        val pixels = IntArray(64) { if (it < 32) argb(255, 255, 0, 0) else argb(255, 0, 0, 255) }
        val c = CoverGlowColor.average(pixels)!!
        near(0.5f, c.red); near(0f, c.green); near(0.5f, c.blue)
    }

    @Test
    fun transparentPixelsAreIgnored() {
        val pixels = IntArray(64) { if (it < 8) argb(255, 0, 255, 0) else argb(0, 255, 0, 0) }
        val c = CoverGlowColor.average(pixels)!!
        near(0f, c.red); near(1f, c.green); near(0f, c.blue)
        assertNull("nothing opaque: no colour", CoverGlowColor.average(IntArray(64) { argb(0, 255, 255, 255) }))
    }

    @Test
    fun aVeryDarkColourIsLiftedKeepingItsHue() {
        val lifted = CoverGlowColor.lifted(Color(0.05f, 0.02f, 0.01f))
        near(PlumDimens.COVER_GLOW_MIN_VALUE, maxOf(lifted.red, lifted.green, lifted.blue))
        near(lifted.red / 5f, lifted.green / 2f)
        near(PlumDimens.COVER_GLOW_MIN_VALUE, CoverGlowColor.lifted(Color.Black).red)
        assertEquals("a bright colour is left alone", Color(0.9f, 0.5f, 0.1f), CoverGlowColor.lifted(Color(0.9f, 0.5f, 0.1f)))
    }

    @Test
    fun aDarkBitmapGivesALiftedColour() {
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(argb(255, 10, 10, 30)) }
        val c = CoverGlowColor.of(bitmap.asImageBitmap())!!
        assertTrue("lifted to the minimum brightness (${c.blue})", c.blue >= PlumDimens.COVER_GLOW_MIN_VALUE - 0.01f)
        assertTrue("still bluish", c.blue > c.red)
    }

    private fun solid() = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(argb(255, 10, 10, 30)) }

    @Test
    fun aHardwareBitmapIsCopiedToArgbBeforeItsPixelsAreRead() {
        // Robolectric cannot make a HARDWARE bitmap, so both branches are taken on an ARGB one: on API 28+ it is scaled and then
        // copied, before 28 copied and then scaled; what must hold is 16 x 16 readable pixels either way.
        for (sdk in listOf(26, 27, 28, 34)) {
            val pixels = CoverGlowColor.samplePixels(solid(), isHardware = true, sdk = sdk)
            assertEquals("sdk $sdk", 16 * 16, pixels.size)
            assertEquals("sdk $sdk", 30, pixels[0] and 0xFF)
        }
    }

    @Test
    fun aBitmapThatCannotBeReadGivesNoColourAndIsLogged() {
        ShadowLog.clear()
        val colour = CoverGlowColor.of(solid().asImageBitmap(), read = { throw IllegalStateException("Software rendering doesn't support hardware bitmaps") })
        assertNull(colour)
        assertTrue("logged under the tag", ShadowLog.getLogs().any { it.tag == CoverGlowColor.TAG && it.type == Log.WARN && it.throwable is IllegalStateException })
    }
}
