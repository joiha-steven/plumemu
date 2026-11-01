package com.plumemu.home

import android.content.Context
import androidx.compose.ui.geometry.Size
import com.plumemu.coreui.theme.POSTER_PALETTE
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The title of a poster cover stays inside the area above the band, whatever its length, script or the cover's shape and size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PosterCoverLayoutTest {
    private val density = Density(2f)
    private val measurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>()), density, LayoutDirection.Ltr)

    private fun layout(
        title: String,
        widthDp: Float,
        heightDp: Float,
        system: String = "Super Nintendo",
        short: String = "SNES",
        measuring: TextMeasurer = measurer,
        density: Density = this.density,
    ) = PosterLayout.of(measuring, density, title, system, short, POSTER_PALETTE[0], null, Size(widthDp * density.density, heightDp * density.density))

    @Test
    fun titleNeverOverflowsItsArea() {
        val titles = PosterEdgeCases.titles
        for (title in titles) {
            for ((w, h) in PosterEdgeCases.sizes) {
                val laid = layout(title, w, h)
                val text = laid.title ?: continue
                val message = "'$title' at ${w}x$h dp: height ${text.size.height} of ${laid.titleArea}, ${text.lineCount} lines"
                assertTrue(message, text.size.height <= laid.titleArea)
                assertTrue(message, text.lineCount <= if (w < h) 3 else 2)
                assertFalse(message, text.didOverflowWidth)
            }
        }
    }

    @Test
    fun aShortTitleIsNotEllipsized() {
        val text = layout("Tetris", 100f, 140f).title!!
        assertEquals(1, text.lineCount)
        assertFalse(text.isLineEllipsized(0))
    }

    @Test
    fun aVeryLongTitleOnATinyCoverEndsInAnEllipsis() {
        val text = layout(PosterEdgeCases.LONG, 40f, 28f).title!!
        assertTrue(text.isLineEllipsized(text.lineCount - 1))
    }

    @Test
    fun aTitleThatFitsAtASmallerSizeIsNotEllipsized() {
        val text = layout(PosterEdgeCases.LONG, 160f, 224f).title!!
        assertFalse(text.isLineEllipsized(text.lineCount - 1))
    }

    private val oneWordTitles = listOf("Championship", "Ghostbusters", "Castlevania", "Pneumonoultramicroscopic", "Bomberman Championship")

    /** Every line but the last ends at a space, or after / before a non-letter: no word is cut in two. */
    private fun assertNoWordCut(
        title: String,
        scale: Float,
        widthDp: Float,
        heightDp: Float,
    ) {
        val scaled = Density(2f, scale)
        val scaledMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>()), scaled, LayoutDirection.Ltr)
        val text = layout(title, widthDp, heightDp, measuring = scaledMeasurer, density = scaled).title ?: return
        val shown = title.uppercase(java.util.Locale.ROOT)
        for (line in 0 until text.lineCount - 1) {
            val end = text.getLineEnd(line)
            val cut = end in 1 until shown.length && shown[end - 1].isLetterOrDigit() && shown[end].isLetterOrDigit()
            assertFalse("'$title' x$scale at ${widthDp}x$heightDp breaks a word at $end", cut)
        }
        assertFalse("'$title' x$scale overflows its area", text.size.height > layout(title, widthDp, heightDp, measuring = scaledMeasurer, density = scaled).titleArea)
    }

    @Test
    fun longSingleWordsAreNeverCutInTwo() {
        for (title in oneWordTitles) {
            for (scale in listOf(1f, 1.3f, 2f)) {
                for ((w, h) in PosterEdgeCases.sizes) assertNoWordCut(title, scale, w, h)
            }
        }
    }

    @Test
    fun aWordWiderThanTheLineIsOneEllipsizedLine() {
        val text = layout("Pneumonoultramicroscopic", 74f, 53f).title!!
        assertEquals(1, text.lineCount)
        assertTrue(text.isLineEllipsized(0))
    }

    @Test
    fun noTitleIsDrawnWhenNoLineFits() {
        val big = Density(2f, 2f)
        val bigMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>()), big, LayoutDirection.Ltr)
        assertEquals(null, layout("Tetris", 30f, 20f, measuring = bigMeasurer, density = big).title)
    }

    @Test
    fun theSystemNameStaysInsideTheCover() {
        for ((w, h) in PosterEdgeCases.sizes) {
            for (scale in listOf(1f, 2f)) {
                val scaled = Density(2f, scale)
                val scaledMeasurer = TextMeasurer(createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>()), scaled, LayoutDirection.Ltr)
                val laid = layout("Tetris", w, h, system = "Super Nintendo Entertainment System", short = "SNES", measuring = scaledMeasurer, density = scaled)
                val text = laid.system ?: continue
                assertTrue("band text ${text.size.width} wider than ${w * 2} at x$scale", text.size.width <= w * 2 * 0.86f + 1)
                assertTrue("band text ${text.size.height} taller than the band ${h * 2 * PosterLayout.BAND_HEIGHT} at $w x $h x$scale", text.size.height <= h * 2 * PosterLayout.BAND_HEIGHT)
            }
        }
    }

    @Test
    fun theShortNameStandsInWhenTheFullOneDoesNotFit() {
        val full = layout("Tetris", 74f, 104f, system = "Super Nintendo Entertainment System", short = "SNES").system!!
        assertEquals("SNES", full.layoutInput.text.text)
        val fits = layout("Tetris", 128f, 91f, system = "Super Nintendo", short = "SNES").system!!
        assertEquals("SUPER NINTENDO", fits.layoutInput.text.text)
    }

    /** On the smallest cover the band is 5.8 dp tall: the 6 sp floor cannot fit in it, so no band text is drawn (never one spilling over the cover). */
    @Test
    fun aBandTooShortForTheSmallestTextHoldsNoText() {
        val tiny = layout("Tetris", 74f, 53f)
        assertEquals(null, tiny.system)
        assertTrue("the title is still drawn", tiny.title != null)
        val roomy = layout("Tetris", 74f, 104f)
        assertTrue("a taller cover keeps its band text", roomy.system != null)
    }
}
