package com.plumemu.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.plumemu.content.CoilCoverImages
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Seen on an AYN Thor: "some covers disappear when the cursor moves onto them". The
 * focused cover moved its drawing out of the grid into the grid's focus layer; the renderer then
 * drew it blank (every move on the low-end emulator, FocusedCoverOnDeviceTest). Here: covers
 * focused while their picture decodes, and real files of every kind decoded by Coil, keep a tile
 * or their picture drawn once focused. Both were red before the cover was also drawn in place.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class HomeFocusedCoverDecodeTest {
    private val compose = appComposeRule()
    private val folder = TemporaryFolder()

    @get:Rule
    val rules = rulesAround(compose).around(folder)

    private lateinit var state: StripState
    private var background = Color.Unspecified
    private var tile = Color.Unspecified

    private fun show(source: HomeArtSource) {
        compose.setContent {
            PlumTheme(PlumThemeSettings.Default) {
                background = PlumTheme.colors.bg
                tile = PlumTheme.colors.sel
                HomeWithArt(source, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it })
            }
        }
        compose.waitForIdle()
    }

    private fun focus(index: Int) {
        compose.runOnIdle { state.dispatch(Action.TapItem(Column.Games, index)) }
        compose.waitForIdle()
    }

    @Test
    fun aCoverFocusedWhileItsPictureDecodesKeepsItsTileThenItsPicture() {
        val images = GatedImages()
        show(FakeArtSource(coverArtSource().results.value, images = images))
        assertTrue("cover 1 shows its tile while it decodes, unfocused (${centre(1)})", centre(1).near(tile))

        focus(1)
        assertTrue("focused while decoding: its tile is drawn (${centre(1)})", centre(1).near(tile))

        compose.runOnIdle { images.landAll() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        val picture = centre(1)
        assertTrue("its picture landed while focused and is drawn ($picture)", !picture.near(tile) && !picture.near(background))

        focus(2)
        assertTrue("unfocused again: still its picture (${centre(1)})", centre(1).near(picture))
        focus(1)
        assertTrue("focused again: still its picture (${centre(1)})", centre(1).near(picture))
    }

    /**
     * A box front with transparent parts (MediaSaver keeps alpha in WebP): the focused cover's larger
     * copy shows the background through them, never the cover's own copy left in place (its hairline
     * or picture). Read on the resting picture's top edge, inside the larger copy's transparent border.
     */
    @Test
    fun throughAFocusedCoversTransparentBorderOnlyTheBackgroundShows() {
        show(FakeArtSource(coverArtSource().results.value, images = TransparentBorderImages()))
        focus(1)
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        val picture =
            compose.onNode(hasTestTag(HomeTags.COVER_PICTURE) and hasAnyAncestor(hasTestTag(HomeTags.cover(1))), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val x = picture.center.x.roundToInt()
        for (y in listOf(picture.top, picture.top + 1f, picture.top + picture.height * 0.03f)) {
            val seen = pixels[x, y.roundToInt()]
            assertTrue("at y=$y px only the background shows through ($seen)", seen.near(background))
        }
        assertTrue("the larger copy itself is drawn (${centre(1)})", centre(1).near(Color(PICTURE)))
    }

    /** Pictures with a transparent border (8 % of the width) round a solid middle. */
    private class TransparentBorderImages : CoverImages {
        override fun cached(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val border = widthPx * TRANSPARENT_BORDER
            Canvas(bitmap).drawRect(border, border, widthPx - border, heightPx - border, Paint().apply { color = PICTURE })
            return bitmap.asImageBitmap()
        }

        override suspend fun load(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap = cached(picture, widthPx, heightPx)
    }

    /** One saved picture: how it is written, and the size the database says it has. */
    private class Kind(
        val name: String,
        val width: Int,
        val height: Int,
        val format: Bitmap.CompressFormat,
        val alphaBorder: Boolean = false,
        val storedWidth: Int = width,
        val storedHeight: Int = height,
    )

    /** Every kind of file a box front may be: JPEG, PNG and WebP, with alpha, very large, very wide, tiny, or not the size stored. */
    private val kinds =
        listOf(
            Kind("jpeg", 560, 400, Bitmap.CompressFormat.JPEG),
            Kind("png-alpha", 560, 400, Bitmap.CompressFormat.PNG, alphaBorder = true),
            Kind("png-large-tall-alpha", 1400, 2000, Bitmap.CompressFormat.PNG, alphaBorder = true),
            Kind("png-very-wide", 900, 300, Bitmap.CompressFormat.PNG),
            Kind("webp", 560, 400, Bitmap.CompressFormat.WEBP),
            Kind("webp-alpha", 560, 400, Bitmap.CompressFormat.WEBP, alphaBorder = true),
            Kind("tiny", 40, 30, Bitmap.CompressFormat.PNG),
            Kind("stored-size-wrong", 400, 560, Bitmap.CompressFormat.JPEG, storedWidth = 560, storedHeight = 400),
        )

    private fun write(kind: Kind): File {
        val extension = if (kind.format == Bitmap.CompressFormat.JPEG) "jpg" else kind.format.name.lowercase()
        val file = File(folder.root, "box-front-${kind.name}.$extension")
        val bitmap = Bitmap.createBitmap(kind.width, kind.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (kind.alphaBorder) {
            val inset = kind.width * 0.05f
            canvas.drawRect(inset, inset, kind.width - inset, kind.height - inset, Paint().apply { color = PICTURE })
        } else {
            canvas.drawColor(PICTURE)
        }
        file.outputStream().use { bitmap.compress(kind.format, QUALITY, it) }
        return file
    }

    /**
     * Real files decoded by Coil, as on a device, each focused as soon as it is shown (its decode
     * may still be running): every kind is drawn once focused, and nothing failed to decode.
     */
    @Test
    fun everyKindOfSavedFileIsDrawnWhenFocused() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val logs = mutableListOf<String>()
        val images = CoilCoverImages(context, { CoilCoverImages.imageLoader(context, isLowRam = false, allowHardware = false) }) { logs += it }
        val rows = CoverArtLibrary.entries[FakeHomeContent.BOARD_SNES_INDEX].rows
        val art =
            rows.mapIndexed { index, row ->
                val kind = kinds[index % kinds.size]
                val picture = ArtPicture(write(kind).path, kind.storedWidth, kind.storedHeight, version = 1)
                artKey(row.id) to GameArt(grid = picture, large = picture, details = GameDetails(year = YEAR, publisher = "", genre = "", players = "", description = ""))
            }.toMap()
        show(FakeArtSource(art, images = images))
        val notDrawn = mutableListOf<String>()
        for (index in 0 until minOf(kinds.size, rows.size)) {
            focus(index)
            val drawn = runCatching { compose.waitUntil(LOAD_MS) { centre(index).near(Color(PICTURE)) } }.isSuccess
            if (!drawn) notDrawn += "${kinds[index].name} (${centre(index)})"
        }
        assertEquals("every kind is drawn when focused", emptyList<String>(), notDrawn)
        assertEquals("nothing failed to decode", emptyList<String>(), logs)
    }

    private fun centre(index: Int): Color {
        val cell = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val density = compose.density.density
        // A quarter up from the bottom: every fitted picture stands on the cell's bottom.
        val point = Offset((cell.left.value + cell.right.value) / 2f * density, (cell.bottom.value - (cell.bottom - cell.top).value * 0.25f) * density)
        return compose.onRoot().captureToImage().toPixelMap().at(point)
    }

    private fun PixelMap.at(point: Offset): Color = this[point.x.roundToInt().coerceIn(0, width - 1), point.y.roundToInt().coerceIn(0, height - 1)]

    private fun Color.near(other: Color): Boolean = abs(red - other.red) < 0.06f && abs(green - other.green) < 0.06f && abs(blue - other.blue) < 0.06f

    /** Pictures that land only when the test says so, as a slow decode does. */
    private class GatedImages(
        private val pictures: FakeCoverImages = FakeCoverImages(),
    ) : CoverImages {
        private val gate = CompletableDeferred<Unit>()
        private var hasLanded = false

        fun landAll() {
            hasLanded = true
            gate.complete(Unit)
        }

        override fun cached(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? = if (hasLanded) pictures.cached(picture, widthPx, heightPx) else null

        override suspend fun load(
            picture: ArtPicture,
            widthPx: Int,
            heightPx: Int,
        ): ImageBitmap? {
            gate.await()
            return pictures.cached(picture, widthPx, heightPx)
        }
    }

    private companion object {
        const val PICTURE = 0xFFE0218A.toInt()
        const val QUALITY = 95
        const val YEAR = 1994
        const val LOAD_MS = 5_000L
        const val SETTLE_MS = 400L
        const val TRANSPARENT_BORDER = 0.08f
    }
}
