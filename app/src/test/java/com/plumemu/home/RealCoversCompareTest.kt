package com.plumemu.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Local only (V2COMPARE=1): the Super Nintendo grid and a mixed Favorites grid drawn with real cover scans from
 * `<repo>/build/v2board/real` (jpg files) (the file name starts with the system: snes, md, gba, n64, ps), written to
 * `<repo>/build/v2compare` (real_ PNGs) at 768x432 in Graphite and Paper, at each grid size. Nothing here reads or writes git-tracked files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class RealCoversCompareTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** The scans by system id; the Super Nintendo's have the Japanese and European ones mixed in among the US boxes. */
    private fun scans(): Map<String, List<File>> {
        val files = File("../build/v2board/real").listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name }.orEmpty()
        fun systemOf(name: String) = listOf("snes" to "snes", "md" to "megadrive", "gba" to "gba", "n64" to "n64", "ps" to "psx").firstOrNull { name.startsWith(it.first) }?.second
        val bySystem = files.groupBy { systemOf(it.name) }.filterKeys { it != null }.mapKeys { checkNotNull(it.key) }.toMutableMap()
        bySystem["snes"]?.let { all ->
            val main = all.filter { it.name.matches(Regex("snes\\d+\\.jpg")) }.toMutableList()
            val others = all - main.toSet()
            others.forEachIndexed { i, f -> main.add(minOf(2 + i * 5, main.size), f) }
            bySystem["snes"] = main
        }
        return bySystem
    }

    /** Decodes a scan to fit the asked size. */
    private class JpgImages : CoverImages {
        override fun cached(picture: ArtPicture, widthPx: Int, heightPx: Int): ImageBitmap? {
            val source = BitmapFactory.decodeFile(picture.path) ?: return null
            val scale = minOf(widthPx.toFloat() / source.width, heightPx.toFloat() / source.height)
            return Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true).asImageBitmap()
        }

        override suspend fun load(picture: ArtPicture, widthPx: Int, heightPx: Int): ImageBitmap? = cached(picture, widthPx, heightPx)
    }

    private fun artOf(rows: List<GameRowModel>, file: (Int) -> File?): Map<String, GameArt> {
        val art = HashMap<String, GameArt>()
        rows.forEachIndexed { index, row ->
            val scan = file(index) ?: return@forEachIndexed
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(scan.path, it) }
            val picture = ArtPicture(scan.path, bounds.outWidth, bounds.outHeight)
            art[artKey(row.id)] = GameArt(picture, picture, (row.details ?: ArtworkFakes.detailsFor(row.title, index)).copy(description = "", play = null))
        }
        return art
    }

    private fun shotsOf(name: String, source: () -> FakeArtSource, content: HomeContent, entryIndex: Int, id: String) {
        var settings by mutableStateOf(PlumThemeSettings.Graphite)
        compose.setContent { PlumTheme(settings) { HomeWithArt(rememberSource(source), gridActions(entryIndex, 0), content, gridIds = setOf(id)) } }
        for ((look, base) in listOf("graphite" to PlumThemeSettings.Graphite, "paper" to PlumThemeSettings.Paper)) {
            for (size in GridSize.entries) {
                settings = base.copy(gridSize = size)
                compose.waitForIdle()
                compose.onRoot().captureRoboImage("../build/v2compare/real_${name}_${look}_768x432_${size.name.lowercase()}.png")
            }
        }
    }

    @Test
    fun superNintendoWithRealScans() {
        assumeTrue(System.getenv("V2COMPARE") != null)
        val snes = checkNotNull(scans()["snes"]) { "no snes*.jpg in build/v2board/real" }.take(18)
        val board = CoverArtLibrary
        val at = board.entries.indexOfFirst { it.id == "snes" }
        shotsOf("snes", { FakeArtSource(artOf(board.entries[at].rows) { snes.getOrNull(it) }, images = JpgImages()) }, board, at, "snes")
    }

    @Test
    fun favoritesMixingSystemsWithRealScans() {
        assumeTrue(System.getenv("V2COMPARE") != null)
        val all = scans()
        val favorites = PackedFavoritesLibrary.entries[1].rows
        // Each favourite takes the next scan of its own system; a system with none (Game Boy) stays a poster.
        val used = HashMap<String, Int>()
        val picks = favorites.indices.map { FAVORITE_PICKS[it].first }
        val files = picks.map { system -> all[system]?.let { list -> list[used.merge(system, 1, Int::plus)!!.minus(1) % list.size] } }
        shotsOf("favorites", { FakeArtSource(artOf(favorites) { files[it] }, images = JpgImages()) }, PackedFavoritesLibrary, 1, HomeContent.FAVORITES_ID)
    }
}
