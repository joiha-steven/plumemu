package com.plumemu.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.WidthClass
import kotlin.random.Random

/*
 * Box fronts that look like real ones for the review shots (`home_art_real_*`), drawn in memory:
 * a two-tone gradient, large shapes, a logo band with the game's title, a system band, film grain.
 * Nothing of a publisher's artwork: real ScreenScraper pictures must never enter the repository,
 * not even as test fixtures. The shapes are the real boxes' (width over height, by system).
 */

/** A box front's width over its height, by system, as the boxes are. */
internal fun realAspect(
    systemId: String,
    index: Int,
): Float =
    when (systemId) {
        // The Super Nintendo's US box, with a Super Famicom-like one every fourth game.
        "snes" -> if (index % 4 == 3) 1.33f else 1.40f
        "n64" -> 1.40f
        "megadrive", "psp", "ps2", "gc", "nes", "mastersystem" -> if (systemId == "psp") 0.58f else 0.70f
        "psx", "saturn", "dreamcast" -> 1.0f
        "gb", "gbc", "gba" -> 0.98f
        "nds" -> 0.90f
        else -> 1.0f
    }

/** Results for every game of [content] with pictures at the real boxes' shapes, or at [aspectOf]'s (system id, index) when given. */
internal fun realCoverSource(
    content: HomeContent = FakeHomeContent.withArtwork(),
    aspectOf: (String, Int) -> Float = ::realAspect,
): FakeArtSource {
    val titles = HashMap<String, Pair<String, String>>()
    val art = HashMap<String, GameArt>()
    for (entry in content.entries) {
        if (entry.kind != EntryKind.System) continue
        entry.rows.forEachIndexed { index, row ->
            val aspect = aspectOf(entry.id, index)
            val details = (row.details ?: ArtworkFakes.detailsFor(row.title, index)).copy(description = "", play = null)
            fun saved(size: String, boxW: Int, boxH: Int): ArtPicture {
                val fitsWidth = aspect >= boxW.toFloat() / boxH
                val w = if (fitsWidth) boxW else (boxH * aspect).toInt()
                val h = if (fitsWidth) (boxW / aspect).toInt() else boxH
                val path = "/media/${row.id}/box-front-$size.webp"
                titles[path] = row.title to entry.name.orEmpty()
                return ArtPicture(path, w, h)
            }
            art[artKey(row.id)] = GameArt(grid = saved("grid", 560, 400), large = saved("large", 1240, 1080), details = details)
        }
    }
    return FakeArtSource(art, mapOf(artKey(SUPER_METROID_ID) to SUPER_METROID_SYNOPSIS), RealLookingCoverImages(titles))
}

/** Draws [titles]' box fronts (path to title and system name) at the asked size, the same every run. */
internal class RealLookingCoverImages(
    private val titles: Map<String, Pair<String, String>>,
) : CoverImages {
    override fun cached(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap = draw(picture.path, widthPx, heightPx)

    override suspend fun load(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap = draw(picture.path, widthPx, heightPx)

    private fun draw(
        path: String,
        w: Int,
        h: Int,
    ): ImageBitmap {
        val (title, system) = titles[path] ?: ("" to "")
        val random = Random(path.hashCode())
        val (top, bottom) = PALETTES[random.nextInt(PALETTES.size)]
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.shader = LinearGradient(0f, 0f, w * 0.3f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        // A glow and two large shapes: the "key art".
        fill.shader = RadialGradient(w * random.nextFloat(), h * (0.4f + 0.3f * random.nextFloat()), maxOf(w, h) * 0.6f, 0x88FFE0A0.toInt(), 0x00FFE0A0, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        fill.shader = null
        fill.color = 0x66000000
        canvas.drawCircle(w * (0.2f + 0.6f * random.nextFloat()), h * 0.7f, minOf(w, h) * (0.25f + 0.15f * random.nextFloat()), fill)
        fill.color = (random.nextInt(0xFFFFFF) or 0x99000000.toInt())
        val peak = Path().apply {
            moveTo(0f, h.toFloat())
            lineTo(w * (0.3f + 0.4f * random.nextFloat()), h * 0.45f)
            lineTo(w.toFloat(), h * 0.85f)
            lineTo(w.toFloat(), h.toFloat())
            close()
        }
        canvas.drawPath(peak, fill)
        // The logo band with the game's name, as on a box front.
        val bandTop = h * 0.08f
        val bandBottom = h * 0.27f
        fill.color = 0xAA101018.toInt()
        canvas.drawRoundRect(w * 0.07f, bandTop, w * 0.93f, bandBottom, h * 0.02f, h * 0.02f, fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFF4D6.toInt()
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = (bandBottom - bandTop) * 0.42f
        }
        val name = title.uppercase()
        while (text.measureText(name) > w * 0.8f && text.textSize > 4f) text.textSize *= 0.9f
        canvas.drawText(name, w / 2f, (bandTop + bandBottom) / 2f + text.textSize * 0.35f, text)
        // The system band along the bottom.
        fill.color = 0xE6F2F2F2.toInt()
        canvas.drawRect(0f, h * 0.9f, w.toFloat(), h.toFloat(), fill)
        text.apply {
            color = 0xFF202020.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = h * 0.055f
        }
        val band = system.uppercase()
        while (text.measureText(band) > w * 0.9f && text.textSize > 4f) text.textSize *= 0.9f
        canvas.drawText(band, w / 2f, h * 0.965f, text)
        // Film grain.
        val grain = IntArray(w * h)
        bitmap.getPixels(grain, 0, w, 0, 0, w, h)
        for (i in grain.indices) {
            val n = random.nextInt(-14, 15)
            val c = grain[i]
            val r = ((c shr 16 and 0xFF) + n).coerceIn(0, 255)
            val g = ((c shr 8 and 0xFF) + n).coerceIn(0, 255)
            val b = ((c and 0xFF) + n).coerceIn(0, 255)
            grain[i] = (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(grain, 0, w, 0, 0, w, h)
        return bitmap.asImageBitmap()
    }

    private companion object {
        val PALETTES =
            listOf(
                0xFF1B3A6B.toInt() to 0xFF0A1226.toInt(),
                0xFF8C2F1B.toInt() to 0xFF2A0E08.toInt(),
                0xFF2F6B3A.toInt() to 0xFF0C2312.toInt(),
                0xFF5B2A7A.toInt() to 0xFF1A0B26.toInt(),
                0xFFC98A1E.toInt() to 0xFF4A2A06.toInt(),
                0xFF2A6F7A.toInt() to 0xFF0A2226.toInt(),
            )
    }
}

/** Index of system [id] in the board's strip. */
internal fun boardIndexOf(id: String): Int = FakeHomeContent.withArtwork().entries.indexOfFirst { it.id == id }

/** System [id]'s grid with real-looking covers at [gridSize], game [focused] focused. */
@Composable
internal fun RealCoverGrid(
    id: String,
    focused: Int,
    gridSize: GridSize = GridSize.Medium,
    aspectOf: (String, Int) -> Float = ::realAspect,
) = ProvideGridSize(gridSize) { HomeWithArt(rememberSource { realCoverSource(aspectOf = aspectOf) }, gridActions(boardIndexOf(id), focused), gridIds = setOf(id)) }

/** Game [index] of system [id] in the details (beside the list wide; the third stop narrow). */
@Composable
internal fun RealCoverDetails(
    id: String,
    index: Int,
    source: FakeArtSource? = null,
) {
    val narrow = PlumTheme.layout.widthClass == WidthClass.Narrow
    val open = listOf(Action.TapItem(Column.Systems, boardIndexOf(id)), Action.Enter, Action.TapItem(Column.Games, index))
    HomeWithArt(source ?: rememberSource { realCoverSource() }, if (narrow) open + Action.Move(Direction.Right) else open)
}

/** [source] with game [index] of system [id]'s large box front saved again at [aspect]. */
internal fun FakeArtSource.withLargeAspect(
    id: String,
    index: Int,
    aspect: Float,
) {
    val row = FakeHomeContent.withArtwork().entries.first { it.id == id }.rows[index]
    val key = artKey(row.id)
    val art = results.value.getValue(key)
    val large = art.large!!
    val fitsWidth = aspect >= 1240f / 1080f
    val picture = large.copy(width = if (fitsWidth) 1240 else (1080 * aspect).toInt(), height = if (fitsWidth) (1240 / aspect).toInt() else 1080, version = large.version + 1)
    results.value = results.value + (key to art.copy(large = picture))
}
