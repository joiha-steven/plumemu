package com.plumemu.home

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The drawn box front: its title wraps to two lines and ends in "…", no letter is cut or clipped, shapes keep off the text, and a title always wears the same look. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoxFrontLayoutTest {
    private val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>())

    /** The six text sizes: the font scales the app's text sizes map to. */
    private val textScales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 2f)

    private fun density(scale: Float = 1f) = Density(2f, scale)

    private fun layout(
        title: String,
        widthDp: Float,
        heightDp: Float,
        scale: Float = 1f,
        line: String = "",
        short: String = "SNES",
        system: String = "Super Nintendo",
    ): BoxFrontLayout {
        val d = density(scale)
        return BoxFrontLayout.of(TextMeasurer(resolver, d, LayoutDirection.Ltr), d, title, system, short, line, null, Size(widthDp * 2f, heightDp * 2f))
    }

    private val vietnameseHome = text(0x110, 0x1b0, 0x1edd, 0x6e, 0x67, 0x20, 0x56, 0x1ec1, 0x20, 0x4e, 0x68, 0xe0, 0x20, 0x43, 0x1ee7, 0x61, 0x20, 0x42, 0xe9, 0x20, 0x4d, 0xe8, 0x6f)

    private fun rectOf(
        offset: Offset,
        layout: androidx.compose.ui.text.TextLayoutResult,
    ) = Rect(offset, Size(layout.size.width.toFloat(), layout.size.height.toFloat()))

    @Test
    fun theSameTitleWearsTheSameLook() {
        for (title in PosterEdgeCases.titles) {
            val a = layout(title, 100f, 141f)
            val b = layout(title, 100f, 141f)
            assertEquals(a.look, b.look)
            assertEquals(a.shapes.map { it.rect to it.circle }, b.shapes.map { it.rect to it.circle })
        }
        assertEquals("case and punctuation do not change it", layout("Mega Man 2", 100f, 141f).look, layout("mega-man 2!", 100f, 141f).look)
    }

    @Test
    fun theBandShowsTheShortNameAndTheLineAsGiven() {
        val l = layout("Tetris", 100f, 141f, line = "Nintendo · 1989")
        assertEquals("SNES", l.system!!.layoutInput.text.text)
        assertEquals("NINTENDO · 1989", l.caps!!.layoutInput.text.text)
        assertEquals("the full name stands in without a short one", "SUPER NINTENDO", layout("Tetris", 128f, 91f, short = "").system!!.layoutInput.text.text)
        assertNull("no line, no small text", layout("Tetris", 100f, 141f).caps)
    }

    @Test
    fun theSmallLineIsBuiltFromWhatIsKnown() {
        assertEquals("Nintendo · 1990", boxLine(details("Nintendo", 1990)))
        assertEquals("1990", boxLine(details("", 1990)))
        assertEquals("Nintendo", boxLine(details("Nintendo", null)))
        assertEquals("", boxLine(details("", null)))
        assertEquals("", boxLine(null))
    }

    private fun details(
        publisher: String,
        year: Int?,
    ) = GameDetails(year, publisher, "", "", "")

    @Test
    fun aLongTitleWrapsToTwoLinesAndEndsInAnEllipsis() {
        for (scale in textScales) {
            for ((w, h) in PosterEdgeCases.sizes) {
                val title = layout(PosterEdgeCases.LONG, w, h, scale).title ?: continue
                assertTrue("$w x $h x$scale has ${title.lineCount} lines", title.lineCount <= 2)
                assertTrue("$w x $h x$scale ends in an ellipsis: ${title.layoutInput.text.text}", title.layoutInput.text.text.endsWith("…"))
            }
        }
    }

    @Test
    fun aTitleThatFitsIsNotCut() {
        for (title in listOf("Tetris", "Super Metroid", "Contra III")) {
            val shown = layout(title, 100f, 141f).title!!
            assertEquals(title, shown.layoutInput.text.text)
        }
    }

    /** No line ends inside a word, at every text size and cover size; the title stays inside the cover below the band. */
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
                    assertTrue("'$title' x$scale at ${w}x$h leaves the cover: $box", box.left >= 0f && box.right <= w * 2f && box.top >= l.bandHeight && box.bottom <= h * 2f)
                }
            }
        }
    }

    @Test
    fun aVeryNarrowCoverShowsNoTextButStillDrawsShapes() {
        val tiny = layout("Tetris", 20f, 14f, line = "Publisher · 1990")
        assertNull(tiny.title)
        assertNull(tiny.caps)
        assertNull(tiny.system)
        val roomy = layout("Tetris", 74f, 104f)
        assertNotNull(roomy.title)
    }

    /** Rendered into a bitmap: the topmost and lowest ink of the title in the cover is what the title draws alone (nothing clips a mark), and clear of the band and the small line. */
    @Test
    fun vietnameseMarksAreNotClippedAndStayInsideTheCover() {
        for (title in listOf(vietnameseHome, PosterEdgeCases.VIETNAMESE)) {
            for ((w, h) in listOf(100f to 141f, 128f to 91f, 74f to 104f)) {
                val l = layout(title, w, h, line = "Publisher · 1994")
                val laid = l.title!!
                val (aloneTop, aloneBottom) = inkRows(w, h) { drawText(laid, topLeft = l.titleTopLeft) }
                val (coverTop, coverBottom) = coverInkRows(l, w, h)
                val where = "'$title' at ${w}x$h"
                assertTrue("$where: top of the marks $aloneTop vs ${l.capsTopLeft.y + l.caps!!.size.height}", aloneTop >= l.capsTopLeft.y + l.caps!!.size.height)
                assertTrue("$where: marks reach under the cover: $aloneBottom", aloneBottom < h * 2f)
                assertTrue("$where: top clipped ($coverTop vs $aloneTop)", coverTop <= aloneTop + 2)
                assertTrue("$where: bottom clipped ($coverBottom vs $aloneBottom)", coverBottom >= aloneBottom - 2)
            }
        }
    }

    private fun bitmap(
        w: Float,
        h: Float,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ): ImageBitmap {
        val image = ImageBitmap((w * 2).toInt(), (h * 2).toInt())
        CanvasDrawScope().draw(density(), LayoutDirection.Ltr, Canvas(image), Size(w * 2, h * 2), draw)
        return image
    }

    /** First and last row holding any painted pixel, for a draw on a clear bitmap. */
    private fun inkRows(
        w: Float,
        h: Float,
        draw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
    ): Pair<Int, Int> {
        val pixels = bitmap(w, h, draw).toPixelMap()
        val rows = (0 until pixels.height).filter { y -> (0 until pixels.width).any { x -> pixels[x, y].alpha > 0.1f } }
        return rows.first() to rows.last()
    }

    /** First and last row below the band holding a pixel of the title's ink colour (within 6/255) in the whole cover. */
    private fun coverInkRows(
        l: BoxFrontLayout,
        w: Float,
        h: Float,
    ): Pair<Int, Int> {
        val pixels = bitmap(w, h) { l.draw(this) }.toPixelMap()
        val ink = l.look.ink
        fun near(c: androidx.compose.ui.graphics.Color) = abs(c.red - ink.red) < 0.025f && abs(c.green - ink.green) < 0.025f && abs(c.blue - ink.blue) < 0.025f
        val from = (l.capsTopLeft.y + l.caps!!.size.height).toInt()
        val rows = (from until pixels.height).filter { y -> (0 until pixels.width).any { x -> near(pixels[x, y]) } }
        return rows.first() to rows.last()
    }

    /** No shape touches the title or the small line, whatever the title and the cover's shape. */
    @Test
    fun shapesNeverCoverTheText() {
        for (title in PosterEdgeCases.sampleTitles + PosterEdgeCases.titles) {
            for ((w, h) in PosterEdgeCases.sizes) {
                val l = layout(title, w, h, line = "Publisher · 1994")
                val texts = listOfNotNull(l.title?.let { rectOf(l.titleTopLeft, it) }, l.caps?.let { rectOf(l.capsTopLeft, it) })
                for (shape in l.shapes) for (text in texts) {
                    val c = shape.rect.center
                    val dx = c.x - c.x.coerceIn(text.left, text.right)
                    val dy = c.y - c.y.coerceIn(text.top, text.bottom)
                    val touches = if (shape.circle) hypot(dx, dy) < shape.rect.width / 2f else shape.rect.overlaps(text)
                    assertFalse("'$title' at ${w}x$h: ${if (shape.circle) "circle" else "square"} ${shape.rect} covers $text", touches)
                }
            }
        }
    }

    @Test
    fun everyCoverHasOneToThreeShapesAndTitlesDiffer() {
        val counts = HashMap<Int, Int>()
        val kinds = HashSet<Boolean>()
        for (i in 0 until 120) {
            val l = layout("Title number $i", 100f, 141f)
            assertTrue("${l.shapes.size} shapes", l.shapes.size in 0..3)
            counts.merge(l.shapes.size, 1, Int::plus)
            l.shapes.forEach { kinds += it.circle }
        }
        assertTrue("counts $counts", (1..3).all { (counts[it] ?: 0) > 5 })
        assertEquals("circles and squares both appear", setOf(true, false), kinds)
    }

    @Test
    fun capsAreAlwaysUpperCaseAndOneLine() {
        val l = layout("Tetris", 100f, 141f, line = "A very long publisher name that cannot fit · 1993")
        assertEquals(1, l.caps!!.lineCount)
        assertTrue(l.caps!!.layoutInput.text.text.endsWith("…"))
        assertEquals(l.caps!!.layoutInput.text.text, l.caps!!.layoutInput.text.text.uppercase(Locale.ROOT))
    }

    /** A cover measured before it has a size (0 wide or high) makes 0/0 for its shape, an unbounded one infinite sizes: it must come back at once with no text. */
    @Test(timeout = 10_000)
    fun aCoverWithNoSizeYetHasNoTextAndReturns() {
        val inf = Float.POSITIVE_INFINITY
        for ((w, h) in listOf(0f to 0f, 0f to 141f, 100f to 0f, inf to inf, 100f to inf, inf to 141f)) {
            val l = layout("The Legend of Zelda", w, h, line = "Nintendo · 1991")
            assertEquals("no title at $w x $h", null, l.title)
            assertEquals("no small line at $w x $h", null, l.caps)
            assertEquals("no band text at $w x $h", null, l.system)
        }
    }
}
