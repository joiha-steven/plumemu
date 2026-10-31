package com.plumemu.readme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.plumemu.home.ArtPicture
import com.plumemu.home.CoverImages
import com.plumemu.home.EntryKind
import com.plumemu.home.FakeArtSource
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.GameArt
import com.plumemu.home.HomeContent
import com.plumemu.home.ArtworkFakes
import com.plumemu.home.artKey
import com.plumemu.home.realAspect

/*
 * Box fronts for the README's pictures: one strong, saturated two-colour gradient per game, the
 * title large and bold (two lines at most), a thin system band, nothing else: no grain, no clip-art.
 * Drawn in memory, so no publisher's artwork is ever in the repository (RealLookingCovers has the
 * same rule). Box shapes come from `realAspect`, so each system keeps its real box.
 */

/** One game's cover: its text and which of [POSTER_LOOKS] it wears. */
internal data class PosterGame(
    val title: String,
    val system: String,
    val look: Int,
)

/** Top and bottom colours of each look: bright hue to a deeper neighbour hue, never a grey or a brown. */
internal val POSTER_LOOKS: List<Pair<Int, Int>> =
    listOf(
        0xFFFF4D4D.toInt() to 0xFFB3002D.toInt(),
        0xFFFF9A1F.toInt() to 0xFFE0301E.toInt(),
        0xFFFFD21F.toInt() to 0xFFFF7A00.toInt(),
        0xFF7BE04B.toInt() to 0xFF0F9D58.toInt(),
        0xFF00D4B4.toInt() to 0xFF0077B6.toInt(),
        0xFF3DB8FF.toInt() to 0xFF2457E6.toInt(),
        0xFF5B5BFF.toInt() to 0xFF2A1B9E.toInt(),
        0xFFA855F7.toInt() to 0xFF5B21B6.toInt(),
        0xFFFF4FD8.toInt() to 0xFF8E24AA.toInt(),
        0xFFFF6B9D.toInt() to 0xFFD81B60.toInt(),
        0xFF00E5FF.toInt() to 0xFF1565C0.toInt(),
        0xFFC6F432.toInt() to 0xFF2EA44F.toInt(),
        0xFFFF7043.toInt() to 0xFFAD1457.toInt(),
        0xFFFFC13B.toInt() to 0xFFE8431C.toInt(),
        0xFF4DD0E1.toInt() to 0xFF7C4DFF.toInt(),
        0xFF69F0AE.toInt() to 0xFF00897B.toInt(),
        0xFFF50057.toInt() to 0xFF4A148C.toInt(),
        0xFF448AFF.toInt() to 0xFF00BFA5.toInt(),
    )

/**
 * Results for every game of [content] with poster covers at the real boxes' shapes. A game's look
 * follows its place (system, row), so neighbours in a list or a grid never share one.
 */
internal fun posterCoverSource(
    content: HomeContent = FakeHomeContent.withArtwork(),
    aspectOf: (String, Int) -> Float = ::realAspect,
): FakeArtSource {
    val games = HashMap<String, PosterGame>()
    val art = HashMap<String, GameArt>()
    val synopses = HashMap<String, String>()
    var systemNumber = 0
    for (entry in content.entries) {
        if (entry.kind != EntryKind.System) continue
        systemNumber++
        entry.rows.forEachIndexed { index, row ->
            val aspect = aspectOf(entry.id, index)
            val details = (readmeDetails(row.title) ?: row.details ?: ArtworkFakes.detailsFor(row.title, index)).copy(description = "", play = null)
            val look = (systemNumber * 5 + index * 7) % POSTER_LOOKS.size

            fun saved(
                size: String,
                boxW: Int,
                boxH: Int,
            ): ArtPicture {
                val fitsWidth = aspect >= boxW.toFloat() / boxH
                val w = if (fitsWidth) boxW else (boxH * aspect).toInt()
                val h = if (fitsWidth) (boxW / aspect).toInt() else boxH
                val path = "/media/${row.id}/box-front-$size.webp"
                games[path] = PosterGame(row.title, entry.name.orEmpty(), look)
                return ArtPicture(path, w, h)
            }
            READMEDESCRIPTIONS[row.title]?.let { synopses[artKey(row.id)] = it }
            art[artKey(row.id)] = GameArt(grid = saved("grid", 560, 400), large = saved("large", 1240, 1080), details = details)
        }
    }
    return FakeArtSource(art, synopses, PosterCoverImages(games))
}

internal class PosterCoverImages(
    private val games: Map<String, PosterGame>,
) : CoverImages {
    override fun cached(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap = draw(games[picture.path] ?: PosterGame("", "", 0), widthPx, heightPx)

    override suspend fun load(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap = cached(picture, widthPx, heightPx)

    private fun draw(
        game: PosterGame,
        w: Int,
        h: Int,
    ): ImageBitmap {
        val (top, bottom) = POSTER_LOOKS[game.look % POSTER_LOOKS.size]
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.shader = LinearGradient(0f, 0f, w * 0.4f, h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), fill)
        fill.shader = null
        val short = minOf(w, h).toFloat()

        // A thin inset frame, as a printed box has.
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, short * 0.012f)
            color = 0x59FFFFFF
        }
        val inset = short * 0.045f
        canvas.drawRect(inset, inset, w - inset, h - inset, frame)

        // The system band along the bottom.
        val bandHeight = h * 0.11f
        val bandTop = h - bandHeight
        fill.color = 0x59000000
        canvas.drawRect(0f, bandTop, w.toFloat(), h.toFloat(), fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xF2FFFFFF.toInt()
            typeface = Typeface.create(Typeface.DEFAULT, 800, false)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
            textSize = bandHeight * 0.42f
        }
        val band = game.system.uppercase()
        while (text.measureText(band) > w * 0.86f && text.textSize > 4f) text.textSize *= 0.92f
        canvas.drawText(band, w / 2f, bandTop + bandHeight / 2f + text.textSize * 0.36f, text)

        // The title: large, bold, two lines at most, centred in the space above the band.
        val ink = if (isLight(top, bottom)) 0xFF14142B.toInt() else 0xFFFFFFFF.toInt()
        text.apply {
            color = ink
            letterSpacing = 0.01f
            typeface = Typeface.create(Typeface.DEFAULT, 900, false)
            setShadowLayer(short * 0.02f, 0f, short * 0.012f, if (ink == 0xFFFFFFFF.toInt()) 0x66000000 else 0x00000000)
        }
        val maxWidth = w * 0.80f
        text.textSize = minOf(h * 0.2f, w * 0.17f)
        var lines = wrap(game.title.uppercase(), text, maxWidth)
        while ((lines.size > 2 || lines.any { text.measureText(it) > maxWidth }) && text.textSize > 5f) {
            text.textSize *= 0.93f
            lines = wrap(game.title.uppercase(), text, maxWidth)
        }
        val lineHeight = text.textSize * 1.08f
        val blockHeight = lineHeight * (lines.size - 1) + text.textSize * 0.72f
        val centre = bandTop / 2f
        var baseline = centre - blockHeight / 2f + text.textSize * 0.72f
        for (line in lines) {
            canvas.drawText(line, w / 2f, baseline, text)
            baseline += lineHeight
        }
        return bitmap.asImageBitmap()
    }

    private fun wrap(
        title: String,
        paint: Paint,
        maxWidth: Float,
    ): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        for (word in title.split(' ').filter { it.isNotEmpty() }) {
            val next = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(next) > maxWidth) {
                lines += current
                current = word
            } else {
                current = next
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    /** Dark ink where the colours are light (the yellows and limes). */
    private fun isLight(
        top: Int,
        bottom: Int,
    ): Boolean {
        fun lum(c: Int) = 0.2126 * (c shr 16 and 0xFF) + 0.7152 * (c shr 8 and 0xFF) + 0.0722 * (c and 0xFF)
        return (lum(top) + lum(bottom)) / 2 > 175
    }
}
