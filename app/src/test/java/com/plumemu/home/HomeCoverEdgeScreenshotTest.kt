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
import com.plumemu.coreui.theme.CollarDimens
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
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The grid cover's collar (covers with a black border melted into a dark
 * background; the collar's lip and groove frame them). The Super Nintendo grid on the AYN Thor's top screen (1920x1080, 837x471 dp at
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
                groove = PlumTheme.colors.collar.seat.well
                seat = PlumTheme.colors.collar
                key(settings) {
                    HomeWithArt(
                        rememberSource { FakeArtSource(withOneVeryWideBox(coverArtSource().results.value), images = BlackBorderedCovers()) },
                        gridActions(FakeHomeContent.BOARD_SNES_INDEX, FOCUSED_CELL),
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

    /** One picture far wider than the grid: its box takes a row of its own at the grid's width, and the row is only as tall as the picture. */
    private fun withOneVeryWideBox(results: Map<String, GameArt>): Map<String, GameArt> {
        val row = FakeHomeContent.withArtwork().entries[FakeHomeContent.BOARD_SNES_INDEX].rows[WIDE_CELL]
        val art = results.getValue(artKey(row.id))
        return results + (artKey(row.id) to art.copy(grid = checkNotNull(art.grid).let { ArtPicture(it.path, it.height * WIDE_ASPECT, it.height) }))
    }

    private var groove = Color.Unspecified
    private var seat: com.plumemu.coreui.theme.CollarLook? = null

    /**
     * The groove's colour [dx] px left of a picture's edge at mid height, as the board's CSS draws it: the well, under the art's
     * `0 3px 4px` drop (outer, so only past the art's edge) and the well's own inset from the lip's side. Density [d].
     */
    private fun grooveBeside(dx: Float, d: Float): Color {
        val look = seat ?: return groove
        fun phi(z: Float): Float {
            val t = 1f / (1f + 0.3275911f * abs(z) / 1.4142135f)
            val e = 1f - t * (0.2548296f + t * (-0.28449672f + t * (1.4214137f + t * (-1.4531521f + t * 1.0614054f)))) * kotlin.math.exp(-z * z / 2f)
            return if (z >= 0f) (1f + e) / 2f else (1f - e) / 2f
        }
        val drop = 0.4f * phi(-dx / (CollarDimens.boxShadowSoftSigma.value * d))
        val ws = look.seat.wellShade
        val fromLip = CollarDimens.coverGroove.value * d - dx
        val inset = ws.color.alpha * (1f - phi(fromLip / (ws.blur.value / 2f * d)))
        return look.shadow.copy(alpha = drop).compositeOver(ws.color.copy(alpha = inset).compositeOver(groove))
    }
    private var background = Color.Unspecified

    /**
     * The collar's groove hugs each drawn picture's own edge (the lip and groove frame a black-bordered box on a dark background), not
     * the cell's: for a picture shorter than its cell (letterboxed, standing on the shelf) the cell space above the collar stays background.
     * The focused cell (its groove is lit) is left out.
     */
    private fun assertTheEdgeHugsEachPicture(look: String) {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // The focused cover is drawn larger over its neighbours with its ring and halo: a cell within that reach is partly covered, so it is left out too.
        val focusedBounds = compose.onNodeWithTag(HomeTags.cover(FOCUSED_CELL), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val d = compose.density.density
        val lifted = focusedBounds.inflate(max(focusedBounds.width, focusedBounds.height) * (1f - 1f) / 2f + (PlumDimens.focusRingOutset.value + PlumDimens.focusRingKeyline.value + PlumDimens.focusHaloReach.value) * d)
        val cells = (0 until FIRST_CELLS).filter { it != FOCUSED_CELL }.map { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }.filter { it.width > 0f && it.bottom <= pixels.height && !it.overlaps(lifted) } // a row off the screen has no pixels to read
        var checked = 0
        var shorter = 0
        for (cell in cells) {
            val picture =
                compose.onAllNodesWithTag(HomeTags.COVER_PICTURE, useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
                    .firstOrNull { it.left >= cell.left - 1f && it.right <= cell.right + 1f && it.bottom <= cell.bottom + 1f && it.top >= cell.top - 1f } ?: continue
            // Packed rows: a box is as wide as its picture's shape, so most pictures fill their cell; one that stands shorter keeps its top clear.
            val isShorter = picture.top - cell.top >= 12f
            checked++
            if (isShorter) shorter++
            val y = picture.center.y.roundToInt()
            val bx = (picture.left - 1.5f).roundToInt()
            val besideIt = pixels[bx, y]
            val expected = grooveBeside(picture.left - (bx + 0.5f), compose.density.density)
            assertTrue("$look: the groove hugs the picture's left edge ($besideIt, expected $expected)", besideIt.near(expected))
            if (isShorter) {
                val cellTop = pixels[picture.center.x.roundToInt(), (cell.top + 1f).roundToInt()]
                assertTrue("$look: no frame at the cell's top ($cellTop)", cellTop.near(background))
            }
        }
        assertTrue("$look: some pictures were checked", checked > 0)
        // The requirement changed: a box is exactly its picture's shape and a very wide one gets a row of its own at the grid's width,
        // its height following, so no picture is letterboxed any more (it used to stand short in a cell cut to the grid's width).
        assertEquals("$look: no picture stands shorter than its box", 0, shorter)
    }

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

    private fun Color.near(other: Color): Boolean = abs(red - other.red) < 0.04f && abs(green - other.green) < 0.04f && abs(blue - other.blue) < 0.04f

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
        const val FOCUSED_CELL = 0 // not above the wide box: its halo would reach the letterboxed cell below
        const val WIDE_CELL = 3
        const val WIDE_ASPECT = 12
    }
}
