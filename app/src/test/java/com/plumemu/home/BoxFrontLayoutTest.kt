package com.plumemu.home

import android.content.Context
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import com.plumemu.coreui.theme.boxFieldFor
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The drawn box front: a code and a title only (no band, no dots, no shapes); the title wraps to three lines at most and ends
 * in "...", no letter is cut or clipped; the code goes under 4 sp; the card is drawn once into an image and only that is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoxFrontLayoutTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>())

    /** The six text sizes: the font scales the app's text sizes map to. */
    private val textScales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 2f)

    private val look = boxFieldFor(Color(0xFF7B5EA7), "Tetris")

    @After
    fun clearProbe() {
        boxFrontProbe = null
    }

    private fun density(scale: Float = 1f) = Density(2f, scale)

    private fun layout(
        title: String,
        widthDp: Float,
        heightDp: Float,
        scale: Float = 1f,
        short: String = "SNES",
        system: String = "Super Nintendo",
    ): BoxFrontLayout {
        val d = density(scale)
        return BoxFrontLayout.of(TextMeasurer(resolver, d, LayoutDirection.Ltr), d, title, system, short, look, Size(widthDp * 2f, heightDp * 2f))
    }

    private val vietnameseHome = text(0x110, 0x1b0, 0x1edd, 0x6e, 0x67, 0x20, 0x56, 0x1ec1, 0x20, 0x4e, 0x68, 0xe0, 0x20, 0x43, 0x1ee7, 0x61, 0x20, 0x42, 0xe9, 0x20, 0x4d, 0xe8, 0x6f)

    private fun rectOf(
        offset: Offset,
        layout: androidx.compose.ui.text.TextLayoutResult,
    ) = Rect(offset, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))

    @Test
    fun theCardHasACodeAndATitleAndNothingElse() {
        val l = layout("Tetris", 100f, 141f)
        assertEquals("SNES", l.code!!.layoutInput.text.text)
        assertEquals("Tetris", l.title!!.layoutInput.text.text)
        assertEquals("the full name stands in without a short one", "SUPER NINTENDO", layout("Tetris", 128f, 91f, short = "").code!!.layoutInput.text.text)
        // The drawn pixels: the field and the texts. No band: the rows under the top are the dark field, not a light strip.
        val pixels = l.render(density())!!.toPixelMap()
        val x = pixels.width - 3
        for (y in listOf(2, 8, 20, 30)) assertTrue("row $y has no band", pixels[x, y].luminance() < 0.3f)
    }

    @Test
    fun theCodeIsMonoMediumAndTheTitleJostBold() {
        val l = layout("Tetris", 100f, 141f)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Medium, l.code!!.layoutInput.style.fontWeight)
        assertEquals(androidx.compose.ui.text.font.FontWeight.Bold, l.title!!.layoutInput.style.fontWeight)
        assertEquals(look.code, l.code!!.layoutInput.style.color)
        assertEquals(look.title, l.title!!.layoutInput.style.color)
    }

    @Test
    fun aLongTitleWrapsToThreeLinesAtMostAndEndsInAnEllipsis() {
        for (scale in textScales) {
            for ((w, h) in PosterEdgeCases.sizes) {
                val title = layout(PosterEdgeCases.LONG, w, h, scale).title ?: continue
                assertTrue("$w x $h x$scale has ${title.lineCount} lines", title.lineCount <= 3)
                val shown = title.layoutInput.text.text
                assertTrue("$w x $h x$scale is whole or ends in an ellipsis: $shown", shown.replace("\n", " ") == PosterEdgeCases.LONG || shown.endsWith("…"))
            }
        }
        val narrow = layout(PosterEdgeCases.LONG + " " + PosterEdgeCases.LONG, 74f, 104f).title!!
        assertEquals("a very long title uses three lines", 3, narrow.lineCount)
        assertTrue(narrow.layoutInput.text.text.endsWith("…"))
    }

    @Test
    fun theCodeIsAboutSixTenthsOfTheTitle() {
        for ((w, h) in PosterEdgeCases.sizes) {
            val l = layout("Tetris", w, h)
            val ratio = l.code!!.layoutInput.style.fontSize.value / l.title!!.layoutInput.style.fontSize.value
            assertEquals("$w x $h", 0.62f, ratio, 0.01f)
        }
    }

    /** The card's grain is monochrome (all three channels move together), fine, the same for the same card, and the field runs light to dark. */
    @Test
    fun theFieldHasMonochromeGrainAndRunsLightToDark() {
        val a = layout("Tetris", 100f, 141f).render(density())!!.toPixelMap()
        val b = layout("Tetris", 100f, 141f).render(density())!!.toPixelMap()
        assertEquals("the same card is the same pixels", a[40, 120], b[40, 120])
        // Grain: neighbours differ but never in hue.
        var different = 0
        for (x in 20 until 60) {
            val p = a[x, 100]
            val q = a[x + 1, 100]
            if (p != q) different++
            val dr = (p.red - q.red) * 255
            val dg = (p.green - q.green) * 255
            val db = (p.blue - q.blue) * 255
            assertTrue("grain is one grey step: $dr $dg $db", kotlin.math.abs(dr - dg) < 4f && kotlin.math.abs(dg - db) < 4f)
        }
        assertTrue("grained: $different of 40 neighbours differ", different > 20)
        // Light to dark: the top left corner area against the bottom right, away from the text and the letter (averaged over the grain).
        fun mean(x0: Int, y0: Int): Float = (0 until 8).sumOf { dx -> (0 until 8).sumOf { dy -> a[x0 + dx, y0 + dy].luminance().toDouble() } }.toFloat() / 64f
        assertTrue("lighter at the top left than the bottom right", mean(2, 40) > mean(a.width - 10, a.height - 10) + 0.004f)
    }

    @Test
    fun theBigLetterIsTheFirstLetterOrDigitAfterTheArticle() {
        assertEquals("Z", BoxFrontLayout.markOf("Zelda"))
        assertEquals("Z", BoxFrontLayout.markOf("The Zelda"))
        assertEquals("L", BoxFrontLayout.markOf("The Legend of Zelda"))
        assertEquals("T", BoxFrontLayout.markOf("The"))
        assertEquals("7", BoxFrontLayout.markOf("7th Saga"))
        assertEquals("S", BoxFrontLayout.markOf("  'Sonic'"))
        assertNull(BoxFrontLayout.markOf("!!!"))
    }

    @Test
    fun theBigLetterBleedsOffTheBottomRightAndOnlyThere() {
        val l = layout("Tetris", 100f, 141f)
        val mark = l.mark!!
        assertEquals("T", mark.layoutInput.text.text)
        val box = rectOf(l.markTopLeft, mark)
        assertTrue("reaches past the right edge: $box", box.right > l.size.width)
        assertTrue("reaches past the bottom edge: ${l.markTopLeft.y + mark.firstBaseline}", l.markTopLeft.y + mark.firstBaseline > l.size.height)
        assertTrue("starts inside the card: $box", box.left > 0f && box.left < l.size.width)
        assertNull("no letter, no mark", layout("!!!", 100f, 141f).mark)
    }

    /** "Donkey Kong Country 2" breaks "Donkey Kong / Country 2", never "Donkey Kong Country / 2". */
    @Test
    fun aLastLineIsNeverTwoCharactersOrFewer() {
        val d = density()
        val measurer = TextMeasurer(resolver, d, LayoutDirection.Ltr)
        val style = androidx.compose.ui.text.TextStyle(fontFamily = com.plumemu.coreui.theme.PlumFonts.Jost, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = with(d) { 40f.toSp() })
        val three = measurer.measure(androidx.compose.ui.text.AnnotatedString("Donkey Kong Country"), style).size.width
        assertEquals("Donkey Kong\nCountry 2", BoxFrontLayout.balance(measurer, "Donkey Kong Country 2", style, three + 2, 2))
        for (title in listOf("Donkey Kong Country 2", "Super Mario World 2", "Mega Man X 3", "Street Fighter II", "Final Fantasy VI")) {
            for (w in listOf(74f, 90f, 100f, 128f, 160f, 214f)) {
                val laid = layout(title, w, w * 1.4f).title ?: continue
                if (laid.lineCount < 2 || laid.layoutInput.text.text.endsWith("…")) continue
                val last = laid.layoutInput.text.text.substring(laid.getLineStart(laid.lineCount - 1)).trim().length
                assertTrue("'$title' at $w wide ends on a line of $last characters", last > 2)
            }
        }
    }

    @Test
    fun brokenLinesAreEvenAmongTheSameCount() {
        val d = density()
        val measurer = TextMeasurer(resolver, d, LayoutDirection.Ltr)
        val style = androidx.compose.ui.text.TextStyle(fontFamily = com.plumemu.coreui.theme.PlumFonts.Jost, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = with(d) { 40f.toSp() })
        // "Super Mario World": greedy would fill the first line ("Super Mario / World"); both choices are 2 lines, the even one wins.
        val wide = measurer.measure(androidx.compose.ui.text.AnnotatedString("Super Mario World"), style).size.width
        val broken = BoxFrontLayout.balance(measurer, "Super Mario World", style, wide - 4, 2)!!
        val widths = broken.split("\n").map { measurer.measure(androidx.compose.ui.text.AnnotatedString(it), style).size.width }
        for (other in listOf("Super\nMario World", "Super Mario\nWorld")) {
            val ow = other.split("\n").map { measurer.measure(androidx.compose.ui.text.AnnotatedString(it), style).size.width }
            assertTrue("$broken (${widths.max()}) is no wider than $other (${ow.max()})", widths.max() <= ow.max())
        }
    }

    @Test
    fun aTitleThatFitsIsNotCut() {
        for (title in listOf("Tetris", "Super Metroid", "Contra III")) {
            val shown = layout(title, 100f, 141f).title!!
            assertEquals(title, shown.layoutInput.text.text)
        }
    }

    /** No line ends inside a word, at every text size and cover size; the title stays inside the cover below the code. */
    @Test
    fun lettersAreNeverCut() {
        val words = listOf("Championship", "Ghostbusters", "Castlevania", "Pneumonoultramicroscopic", "Bomberman Championship", PosterEdgeCases.LONG)
        for (scale in textScales) {
            for ((w, h) in PosterEdgeCases.sizes) {
                for (title in words + PosterEdgeCases.titles) {
                    val l = layout(title, w, h, scale)
                    val laid = l.title ?: continue
                    val shown = laid.layoutInput.text.text
                    assertFalse("'$title' x$scale at ${w}x$h breaks a word", BoxFrontLayout.breaksWord(laid, shown))
                    val box = rectOf(l.titleTopLeft, laid)
                    val codeBottom = l.code?.let { l.codeTopLeft.y + it.size.height } ?: 0f
                    assertTrue("'$title' x$scale at ${w}x$h leaves the cover: $box", box.left >= 0f && box.right <= w * 2f && box.top >= codeBottom && box.bottom <= h * 2f)
                }
            }
        }
    }

    @Test
    fun aVeryNarrowCoverShowsNoCodeOrTitle() {
        val tiny = layout("Tetris", 20f, 14f)
        assertNull(tiny.title)
        assertNull(tiny.code)
        assertNotNull(layout("Tetris", 74f, 104f).title)
        // The code is left out under 4 sp (8 px here), the title under 6 sp.
        val d = density()
        for (h in listOf(30f, 40f, 50f, 60f, 80f, 120f)) {
            val l = layout("Tetris", h * 0.72f, h)
            l.code?.let { assertTrue("code ${it.layoutInput.style.fontSize} at h=$h", it.layoutInput.style.fontSize.value * d.fontScale * d.density >= 8f - 0.01f) }
        }
    }

    /** Rendered into the card's image: the topmost and lowest ink of the title in the card is the title's own (nothing clips a mark), clear of the code. */
    @Test
    fun vietnameseMarksAreNotClippedAndStayInsideTheCover() {
        for (title in listOf(vietnameseHome, PosterEdgeCases.VIETNAMESE)) {
            for ((w, h) in listOf(100f to 141f, 128f to 91f, 74f to 104f)) {
                val l = layout(title, w, h)
                val laid = l.title!!
                val pixels = l.render(density())!!.toPixelMap()
                val rows = (0 until pixels.height).filter { y -> (0 until pixels.width).any { x -> close(pixels[x, y], look.title) } }
                val where = "'$title' at ${w}x$h"
                val titleTop = l.titleTopLeft.y
                val titleBottom = titleTop + laid.size.height
                assertTrue("$where: ink above the title box: ${rows.first()} vs $titleTop", rows.first() >= titleTop - 2)
                assertTrue("$where: ink under the cover: ${rows.last()}", rows.last() < h * 2f)
                assertTrue("$where: ink below the title box: ${rows.last()} vs $titleBottom", rows.last() <= titleBottom + 2)
                assertTrue("$where: title overlaps the code", titleTop >= l.codeTopLeft.y + (l.code?.size?.height ?: 0))
            }
        }
    }

    private fun close(
        a: Color,
        b: Color,
    ) = kotlin.math.abs(a.red - b.red) < 0.025f && kotlin.math.abs(a.green - b.green) < 0.025f && kotlin.math.abs(a.blue - b.blue) < 0.025f

    /** A cover measured before it has a size (0 wide or high) makes 0/0 for its shape, an unbounded one infinite sizes: it must come back at once with no text and no image. */
    @Test(timeout = 10_000)
    fun aCoverWithNoSizeYetHasNoTextAndReturns() {
        val inf = Float.POSITIVE_INFINITY
        for ((w, h) in listOf(0f to 0f, 0f to 141f, 100f to 0f, inf to inf, 100f to inf, inf to 141f)) {
            val l = layout("The Legend of Zelda", w, h)
            assertEquals("no title at $w x $h", null, l.title)
            assertEquals("no code at $w x $h", null, l.code)
            assertNull("no image at $w x $h", l.render(density()))
        }
    }

    /**
     * The card is drawn once: after the first frame a second frame of the same size builds no image (no path, no text layout)
     * and draws exactly one image; a new size builds one more.
     */
    @Test
    fun aSecondFrameBuildsNothingAndDrawsOneImage() {
        val probe = BoxFrontProbe().also { boxFrontProbe = it }
        val frame = mutableIntOf()
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                PosterCover("Super Metroid", "Super Nintendo", "SNES", Modifier.drawBehind { frame.intValue }.size(100.dp, 141.dp))
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureToImage()
        assertEquals("one image built", 1, probe.builds)
        assertEquals("one image drawn", 1, probe.imageDraws)
        for (n in 1..3) {
            frame.intValue = n
            compose.waitForIdle()
            compose.onRoot().captureToImage()
            assertEquals("frame $n builds nothing", 1, probe.builds)
            assertEquals("frame $n draws one more image", 1 + n, probe.imageDraws)
        }
    }

    private fun mutableIntOf() = mutableIntStateOf(0)
}
