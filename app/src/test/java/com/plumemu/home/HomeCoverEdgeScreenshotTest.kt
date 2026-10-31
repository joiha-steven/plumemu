package com.plumemu.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import androidx.compose.ui.graphics.luminance
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The grid cover's hairline (covers with a black border melted into a dark
 * background). The Super Nintendo grid on the AYN Thor's top screen (1920x1080, 837x471 dp at
 * 367 dpi), every other cover with a wide black border and one all dark, on Black and on Paper:
 * `home_grid_cover_edge_thor_{black,paper}.png`. Placeholder pictures drawn in memory, never real
 * covers.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-367dpi")
class HomeCoverEdgeScreenshotTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun blackBorderedCoversOnBlackAndPaper() {
        var settings by mutableStateOf(PlumThemeSettings.Default)
        compose.setContent {
            PlumTheme(settings) {
                background = PlumTheme.colors.bg
                edge = PlumTheme.colors.coverEdge
                rim = if (background.luminance() < PlumDimens.COVER_SHADOW_LIGHT_BACKGROUND) PlumDimens.coverRimColor.copy(alpha = PlumDimens.COVER_RIM_ALPHA_DARK) else null
                key(settings) {
                    HomeWithArt(
                        rememberSource { FakeArtSource(coverArtSource().results.value, images = BlackBorderedCovers()) },
                        gridActions(FakeHomeContent.BOARD_SNES_INDEX, 1),
                        gridIds = setOf("snes"),
                    )
                }
            }
        }
        val mismatches = mutableListOf<String>()
        val cells = mutableMapOf<String, List<String>>()
        for ((look, base) in listOf("black" to PlumThemeSettings.Default, "paper" to PlumThemeSettings.Paper)) {
            settings = base
            compose.waitForIdle()
            cells[look] = coverRects()
            println("COVER_RECTS $look ${cells[look]}")
            val name = "home_grid_cover_edge_thor_$look"
            try {
                compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
            } catch (e: AssertionError) {
                mismatches += "$name: ${e.message}"
            }
            assertTheEdgeHugsEachPicture(look)
        }
        // The hairline changes no layout: the same rects on both backgrounds here; HomeArtBoundsTest
        // pins every grid's cells and pictures, and the line itself is checked on each picture above.
        assertTrue(cells.getValue("black").isNotEmpty())
        assertEquals("the same cells on both backgrounds", cells["black"], cells["paper"])
        assertEquals("shots match their references", emptyList<String>(), mismatches)
    }

    private var edge = Color.Unspecified

    /** The rim light on a dark background (white at its opacity, over the picture); null on a light one, where the hairline [edge] stands. */
    private var rim: Color? = null
    private var background = Color.Unspecified

    /**
     * The hairline lies on each drawn picture's own top edge, not the cell's: for a picture shorter
     * than its cell (letterboxed, standing on the shelf) the cell space above it stays background.
     */
    private fun assertTheEdgeHugsEachPicture(look: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val cells = (0 until FIRST_CELLS).map { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }
        val shadowed = shadowRegions()
        var letterboxed = 0
        for (cell in cells) {
            val picture =
                compose.onAllNodesWithTag(HomeTags.COVER_PICTURE, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
                    .firstOrNull { it.left >= cell.left - 1f && it.right <= cell.right + 1f && it.bottom <= cell.bottom + 1f && it.top >= cell.top - 1f } ?: continue
            if (picture.top - cell.top < 4f) continue
            letterboxed++
            val x = picture.center.x.roundToInt()
            val onEdge = pixels[x, picture.top.roundToInt()]
            val aboveIt = pixels[x, (picture.top - 2f).roundToInt()]
            val cellTop = pixels[x, (cell.top + 1f).roundToInt()]
            // Dark: the rim is see-through, so the line is the picture's own pixel just inside it, lightened; light: the opaque hairline.
            val line = rim?.compositeOver(pixels[x, (picture.top + 3f * compose.density.density).roundToInt()]) ?: edge
            assertTrue("$look: the line on the picture's top ($onEdge, expected $line)", onEdge.near(line))
            assertTrue("$look: background just above the picture ($aboveIt)", aboveIt.nearOrShadowed(background, Offset(x.toFloat(), picture.top - 2f), shadowed))
            assertTrue("$look: no frame at the cell's top ($cellTop)", cellTop.nearOrShadowed(background, Offset(x.toFloat(), cell.top + 1f), shadowed))
        }
        assertTrue("$look: some covers are shorter than their cells", letterboxed > 0)
    }

    /**
     * Where a cover's shadow can fall, in px: each drawn picture's bounds grown by the widest blur on every side and
     * shifted down by the deepest offset below (the focused cover's lifted shadow, drawn under its grown copy,
     * reaches furthest; the focused cover also grows, which the extra [LIFT_GROWTH] dp covers).
     */
    private fun shadowRegions(): List<Rect> {
        val density = compose.density.density
        val blur = (maxOf(PlumDimens.coverShadowBlur, PlumDimens.coverShadowLiftedBlur).value + LIFT_GROWTH) * density
        val down = maxOf(PlumDimens.coverShadowOffsetY, PlumDimens.coverShadowLiftedOffsetY).value * density
        return compose.onAllNodesWithTag(HomeTags.COVER_PICTURE, useUnmergedTree = true).fetchSemanticsNodes().map {
            val r = it.boundsInRoot
            Rect(r.left - blur, r.top - blur, r.right + blur, r.bottom + blur + down)
        }
    }

    /**
     * This pixel (at [at], px) matches [other]: inside a cover's [shadowed] region it may also be [other] darkened by black
     * at most the shadow's own opacity (never lighter); outside every such region it must match the
     * background as before, so a black frame or plate there still fails, on Graphite too.
     */
    private fun Color.nearOrShadowed(
        other: Color,
        at: Offset,
        shadowed: List<Rect>,
    ): Boolean {
        if (near(other)) return true
        if (shadowed.none { it.contains(at) }) return false
        val most = if (other.luminance() < 0.5f) PlumDimens.COVER_SHADOW_ALPHA_DARK else PlumDimens.COVER_SHADOW_ALPHA_LIGHT
        fun channel(mine: Float, theirs: Float) = mine <= theirs + 0.03f && mine >= theirs * (1f - most) - 0.03f
        return channel(red, other.red) && channel(green, other.green) && channel(blue, other.blue)
    }

    private fun Color.near(other: Color): Boolean = abs(red - other.red) < 0.03f && abs(green - other.green) < 0.03f && abs(blue - other.blue) < 0.03f

    /** Every cover cell's and drawn picture's bounds, in dp. */
    private fun coverRects(): List<String> {
        val pictures = compose.onAllNodesWithTag(HomeTags.COVER_PICTURE, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot.toString() }
        val first = (0 until FIRST_CELLS).map { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).getUnclippedBoundsInRoot().toString() }
        return first + pictures
    }

    /**
     * Box fronts with a wide black border round a coloured middle (every other one), plain coloured
     * ones, and one all near-black: the kind that melts into a black background.
     */
    private class BlackBorderedCovers(
        private val plain: FakeCoverImages = FakeCoverImages(),
    ) : CoverImages {
        override fun cached(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap {
            val index = picture.path.hashCode().let { Math.floorMod(it, KINDS) }
            if (index == 0) return plain.cached(picture, widthPx, heightPx)!!
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(BLACK)
            if (index == 1) {
                val border = widthPx * BORDER
                canvas.drawRect(border, border, widthPx - border, heightPx - border, Paint().apply { color = MIDDLE })
                canvas.drawRect(border * 2, heightPx * 0.15f, widthPx - border * 2, heightPx * 0.32f, Paint().apply { color = BAND })
            } else {
                canvas.drawRect(widthPx * 0.2f, heightPx * 0.4f, widthPx * 0.8f, heightPx * 0.55f, Paint().apply { color = DARK_LOGO })
            }
            return bitmap.asImageBitmap()
        }

        override suspend fun load(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap = cached(picture, widthPx, heightPx)

        private companion object {
            const val KINDS = 3
            const val BORDER = 0.07f
            const val BLACK = 0xFF000000.toInt()
            const val MIDDLE = 0xFF2E5AAC.toInt()
            const val BAND = 0xFFD9A441.toInt()
            const val DARK_LOGO = 0xFF3A2A4A.toInt()
        }
    }

    private companion object {
        const val FIRST_CELLS = 10
        const val LIFT_GROWTH = 6
    }
}
