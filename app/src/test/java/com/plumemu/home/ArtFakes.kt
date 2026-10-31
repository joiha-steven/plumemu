package com.plumemu.home

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/*
 * ScreenScraper results for the home screen's tests: a source over a map that can change while a
 * test runs, and pictures drawn in memory (two-tone "covers" of the saved size), so shots show
 * real cover shapes without image files.
 */

/** A [HomeArtSource] over [art] (by scrape key); records every key list a list or grid asked for. */
internal class FakeArtSource(
    art: Map<String, GameArt>,
    private val synopses: Map<String, String> = emptyMap(),
    override val images: CoverImages = FakeCoverImages(),
    private val ratings: Map<String, Int> = emptyMap(),
) : HomeArtSource {
    // Inline, so a shot never races the glow colour being worked out.
    override val glowDispatcher: CoroutineDispatcher = Dispatchers.Unconfined
    val results = MutableStateFlow(art)
    val observed = mutableListOf<List<String>>()
    val synopsisReads = mutableListOf<String>()

    override fun observe(keys: List<String>): Flow<Map<String, GameArt>> {
        observed += keys
        return results.map { all -> all.filterKeys { it in keys } }
    }

    override suspend fun extras(key: String): DetailsExtras {
        synopsisReads += key
        return DetailsExtras(synopses[key].orEmpty(), ratings[key])
    }
}

/**
 * Pictures drawn in memory at the asked size. Paths in [pending] never land (their placeholder
 * stays, as while a slow card decodes); every other path is "in memory" at once, so shots need no
 * waiting. [decoded] lists each size asked for.
 */
internal class FakeCoverImages(
    private val pending: Set<String> = emptySet(),
    /** Paths whose file is gone or damaged: the load answers null at once. */
    private val unreadable: Set<String> = emptySet(),
) : CoverImages {
    val decoded = mutableListOf<Triple<String, Int, Int>>()

    override fun cached(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? = if (picture.path in pending || picture.path in unreadable) null else draw(picture.path, widthPx, heightPx)

    override suspend fun load(
        picture: ArtPicture,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap? {
        if (picture.path in unreadable) return null
        if (picture.path in pending) awaitCancellation()
        return draw(picture.path, widthPx, heightPx)
    }

    private fun draw(
        path: String,
        widthPx: Int,
        heightPx: Int,
    ): ImageBitmap {
        decoded += Triple(path, widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (top, bottom) = COVER_COLOURS[Math.floorMod(path.hashCode(), COVER_COLOURS.size)]
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, heightPx.toFloat(), top, bottom, Shader.TileMode.CLAMP) }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), paint)
        // A logo band across the top third, like a box front's title.
        val band = Paint().apply { color = 0x55FFFFFF }
        canvas.drawRect(widthPx * 0.1f, heightPx * 0.12f, widthPx * 0.9f, heightPx * 0.3f, band)
        return bitmap.asImageBitmap()
    }

    private companion object {
        val COVER_COLOURS =
            listOf(
                0xFF2E5AAC.toInt() to 0xFF132A57.toInt(),
                0xFFB8432E.toInt() to 0xFF5A1D12.toInt(),
                0xFF3E8E5A.toInt() to 0xFF173D25.toInt(),
                0xFF8C5BB8.toInt() to 0xFF3B2155.toInt(),
                0xFFD9A441.toInt() to 0xFF6B4A12.toInt(),
            )
    }
}

/** The fakes' rows as the library gives them: a scrape key each, no fixed details (they come from the source). */
internal fun HomeContent.asLibrary(): HomeContent =
    withEntries(entries.map { entry -> entry.copy(rows = entry.rows.map { it.copy(details = null, scrapeKey = it.scrapeKey ?: artKey(it.id)) }) })

internal fun artKey(rowId: String) = "crc:$rowId"

/**
 * Results for every game of [content] (its `withArtwork` facts), with saved pictures at the
 * shapes real box fronts have: Super Nintendo boxes 1.30-1.78 wide for one high, PSP cases about
 * 0.58. [noArt] games have a result without pictures; [absent] games have none at all.
 */
internal fun artFor(
    content: HomeContent,
    noArt: Set<String> = emptySet(),
    absent: Set<String> = emptySet(),
): Map<String, GameArt> {
    val art = HashMap<String, GameArt>()
    for (entry in content.entries) {
        if (entry.kind != EntryKind.System) continue
        val portrait = entry.id in ArtworkFakes.portraitSystems
        entry.rows.forEachIndexed { index, row ->
            if (row.title in absent) return@forEachIndexed
            val details = (row.details ?: ArtworkFakes.detailsFor(row.title, index)).copy(description = "", play = null, rating = null)
            val aspect = if (portrait) PORTRAIT_ASPECTS[index % PORTRAIT_ASPECTS.size] else LANDSCAPE_ASPECTS[index % LANDSCAPE_ASPECTS.size]
            val pictures = row.title !in noArt
            art[artKey(row.id)] =
                GameArt(
                    grid = if (pictures) saved(row.id, "grid", aspect, GRID_W, GRID_H) else null,
                    large = if (pictures) saved(row.id, "large", aspect, LARGE_W, LARGE_H) else null,
                    details = details,
                )
        }
    }
    return art
}

/** A picture saved fitted in [boxW] x [boxH] at [aspect], as the engine saves it. */
private fun saved(
    id: String,
    size: String,
    aspect: Float,
    boxW: Int,
    boxH: Int,
): ArtPicture {
    val fitsWidth = aspect >= boxW.toFloat() / boxH
    val w = if (fitsWidth) boxW else (boxH * aspect).toInt()
    val h = if (fitsWidth) (boxW / aspect).toInt() else boxH
    return ArtPicture("/media/$id/box-front-$size.webp", w, h)
}

private val LANDSCAPE_ASPECTS = listOf(1.40f, 1.36f, 1.78f, 1.42f, 1.30f)
private val PORTRAIT_ASPECTS = listOf(0.58f, 0.60f, 0.57f)
private const val GRID_W = 560
private const val GRID_H = 400
private const val LARGE_W = 1240
private const val LARGE_H = 1080
