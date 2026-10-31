package com.plumemu.home

import com.plumemu.keyboard.KeyboardChoice
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.game.BoxArtHit
import com.plumemu.game.BoxArtSearchOutcome
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.GameRef
import com.plumemu.game.LocalThumbnailDispatcher
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/*
 * Fix box art's tests: ScreenScraper's games with thumbnail files of real shapes (portrait,
 * landscape, square; one with no file), drawn by the same in-memory covers the home shots use.
 * Nothing of a publisher's artwork: the files are plain bitmaps written for the test.
 */

internal object BoxArtPanelFixtures {
    val game = GameRef("snes-6", "Super Metroid")

    /** A fixed place: the covers are drawn from a hash of the file's path, so shots must see the same path every run. */
    private val dir: File by lazy { File(System.getProperty("java.io.tmpdir"), "plumemu-box-art-thumbs").also { it.mkdirs() } }

    /** A thumbnail file [width] x [height] px for game [id]; its path is what the covers are drawn from. */
    fun thumbnail(
        id: String,
        width: Int,
        height: Int,
    ): File =
        File(dir, "$id.png").also { file ->
            // Test classes run in parallel JVMs: written aside and moved in whole, so no one reads half a file.
            val aside = File.createTempFile("thumb", ".png", dir)
            aside.outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            Files.move(aside.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }

    val portrait = BoxArtHit("1", "Super Metroid", 1994, "Nintendo", "Super Nintendo", null)
    val landscape = BoxArtHit("2", "Super Metroid (Japan)", 1994, "Nintendo", "Super Famicom", null)
    val square = BoxArtHit("3", "Metroid Prime", 2002, "Nintendo", "GameCube", null)
    val missing = BoxArtHit("4", "Metroid Fusion", 2002, "Nintendo", "Game Boy Advance", null)
    val hits = listOf(portrait, landscape, square, missing)

    val longName =
        BoxArtHit(
            "5",
            "Super Metroid: The Adventure of Samus Aran Against the Space Pirates of Zebes, Special Collector's Edition",
            1994,
            "Nintendo Co., Ltd. and its Publishing Partners Worldwide",
            "Nintendo Entertainment System / Famicom",
            null,
        )

    /** Files by hit id, at the shapes above (portrait 0.7, landscape 1.4, square). */
    private fun thumbnailsFor(shown: List<BoxArtHit>): Map<BoxArtHit, File?> =
        shown.associateWith {
            when (it.id) {
                "1" -> thumbnail("1", 140, 200)
                "2" -> thumbnail("2", 200, 143)
                "3", "5" -> thumbnail(it.id, 160, 160)
                else -> null
            }
        }

    /** The state with [shown] listed (search for "metroid" answered) and their thumbnails landed. */
    fun results(
        shown: List<BoxArtHit> = hits,
        query: String = "super metroid",
        keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
    ): FixBoxArtState =
        FixBoxArtState(query, "Super Nintendo", keyboard).also { state ->
            state.searching(query)
            state.showSearch(query, BoxArtSearchOutcome.Found(shown))
            thumbnailsFor(shown).forEach { (hit, file) -> if (file != null) state.thumbnailReady(hit.id, file) }
        }

    fun panel(state: FixBoxArtState) = OpenPanel(game, PanelScreen.FixBoxArt(state))

    /** Covers drawn from the thumbnail files, and the size of each read at once (no waiting for another thread). */
    @Composable
    fun WithCovers(
        reading: CoroutineDispatcher = Dispatchers.Unconfined,
        content: @Composable () -> Unit,
    ) {
        val titles = (hits + longName).associate { hit -> File(dir, "${hit.id}.png").path to ((hit.name ?: "") to hit.systemName.orEmpty()) }
        val source = FakeArtSource(emptyMap(), images = RealLookingCoverImages(titles))
        CompositionLocalProvider(LocalThumbnailDispatcher provides reading) {
            ProvideHomeArt(source, hasBoxArt = false, content)
        }
    }

    /** The home board with [panel] over it, thumbnails drawn. */
    @Composable
    fun Screen(panel: OpenPanel) = WithCovers { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) }
}
