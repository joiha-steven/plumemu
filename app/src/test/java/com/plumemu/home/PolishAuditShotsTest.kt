package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.game.EmulatorToStart
import com.plumemu.settings.toThemeSettings
import com.plumemu.settings.withColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Audit frames of the games list, the grid and the details column in their real states, in a dark and a light set, with a dump of
 * every text's bounds and first baseline (px) beside each PNG for measuring. Written only when `PLUM_SHOTS_DIR` is set; without it
 * the test only checks that the screens build. Not compared with goldens.
 */
abstract class PolishAuditShotsBase(
    private val label: String,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun settingsOf(set: ColorSet) = PlumSettings(appearance = AppearanceSettings().withColors(set), flags = SetupFlags(setupCompleted = true))

    private val sets = listOf("plumemu" to ColorSet.Plumemu, "daylight" to ColorSet.Daylight, "banana" to ColorSet.Banana)
    private var set by mutableStateOf(ColorSet.Plumemu)

    private fun texts(node: SemanticsNode): List<SemanticsNode> = (if (SemanticsProperties.Text in node.config || SemanticsProperties.TestTag in node.config) listOf(node) else emptyList()) + node.children.flatMap(::texts)

    private fun dump(file: File) {
        val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val lines =
            texts(root).filter { it.boundsInRoot.width > 0f }.map { n ->
                val b = n.boundsInRoot
                val text = if (SemanticsProperties.Text in n.config) n.config[SemanticsProperties.Text].joinToString() else ""
                val tag = if (SemanticsProperties.TestTag in n.config) n.config[SemanticsProperties.TestTag] else ""
                val layouts = mutableListOf<TextLayoutResult>()
                if (text.isNotEmpty()) if (SemanticsActions.GetTextLayoutResult in n.config) n.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                val l = layouts.firstOrNull()
                val base = l?.let { b.top + it.firstBaseline }
                val ell = l?.let { r -> (0 until r.lineCount).any { r.isLineEllipsized(it) } } ?: false
                val lines = l?.lineCount ?: 0
                "${b.left}\t${b.top}\t${b.right}\t${b.bottom}\t${base ?: ""}\t$lines\t$ell\t$tag\t${text.replace('\t', ' ').replace('\n', ' ')}"
            }
        file.writeText("density\t${compose.density.density}\n" + lines.joinToString("\n"))
    }

    private fun write(name: String) {
        val dir = System.getenv("PLUM_SHOTS_DIR") ?: return
        File(dir).mkdirs()
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        dump(File(dir, "$name.tsv"))
    }

    private fun shoot(
        scene: String,
        body: @Composable () -> Unit,
    ) {
        compose.setContent { PlumTheme(settingsOf(set).toThemeSettings()) { body() } }
        for ((setName, s) in sets) {
            compose.runOnIdle { set = s }
            compose.waitForIdle()
            write("${scene}_${label}_$setName")
        }
    }

    private val narrow @Composable get() = PlumTheme.layout.widthClass == WidthClass.Narrow

    // ---- Games list ----

    /** Real-looking covers: the list itself (wide: with the details beside it). */
    @Test
    fun listReal() = shoot("list_real") { HomeWithArt(rememberSource { realCoverSource() }, gridActions(boardIndexOf("snes"), 4)) }

    /** No box art at all (no scraper, no posters): text rows. */
    @Test
    fun listNoArt() = shoot("list_noart") { HomeAt(FakeHomeContent.board(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 4)) }

    /** Drawn posters for every game. */
    @Test
    fun listPosters() = shoot("list_posters") { CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(posterSnesBoard(), gridActions(POSTER_SNES_INDEX, 6)) } }

    /** A long title focused. */
    @Test
    fun listLongTitle() =
        shoot("list_long") {
            HomeWithArt(rememberSource { coverArtSource(content = withSuperMetroidTitled(LONG)) }, BoardGamesFocused, withSuperMetroidTitled(LONG))
        }

    /** A system with two games. */
    @Test
    fun listFew() =
        shoot("list_few") {
            val base = FakeHomeContent.board()
            val few = base.withEntries(base.entries.map { e -> if (e.id == "snes") e.copy(rows = e.rows.take(2)) else e })
            HomeAt(few, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 1))
        }

    /** Many games, scrolled to the middle (both edge fades). */
    @Test
    fun listMany() =
        shoot("list_many") {
            val base = FakeHomeContent.board()
            val many = base.withEntries(base.entries.map { e -> if (e.id == "snes") e.copy(rows = e.rows + e.rows.map { it.copy(id = it.id + "-b") } + e.rows.map { it.copy(id = it.id + "-c") }) else e })
            HomeAt(many, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 14))
        }

    // ---- Grid ----

    @Test
    fun gridLandscapeSmall() = shoot("grid_land_small") { RealCoverGrid("snes", 4, GridSize.Small) }

    @Test
    fun gridLandscapeMedium() = shoot("grid_land_medium") { RealCoverGrid("snes", 4, GridSize.Medium) }

    @Test
    fun gridLandscapeLarge() = shoot("grid_land_large") { RealCoverGrid("snes", 4, GridSize.Large) }

    @Test
    fun gridPortraitSmall() = shoot("grid_port_small") { RealCoverGrid("psp", 7, GridSize.Small) }

    @Test
    fun gridPortraitMedium() = shoot("grid_port_medium") { RealCoverGrid("psp", 7, GridSize.Medium) }

    @Test
    fun gridPortraitLarge() = shoot("grid_port_large") { RealCoverGrid("psp", 7, GridSize.Large) }

    @Test
    fun gridFirst() = shoot("grid_first") { RealCoverGrid("snes", 0, GridSize.Medium) }

    @Test
    fun gridLast() =
        shoot("grid_last") {
            val last = FakeHomeContent.withArtwork().entries.first { it.id == "snes" }.rows.lastIndex
            RealCoverGrid("snes", last, GridSize.Medium)
        }

    @Test
    fun gridPosters() = shoot("grid_posters") { ProvideGridSize(GridSize.Medium) { HomeAt(posterSnesBoard(), gridActions(POSTER_SNES_INDEX, 1), gridIds = setOf("snes")) } }

    // ---- Details ----

    private class Plays(private val play: GamePlay?) : HomePlayFactsSource {
        override suspend fun read(gameId: Long) = play ?: GamePlay(lastPlayedDaysAgo = null, playTimeMinutes = 0, emulator = null, hasOwnEmulator = true)
    }

    @Composable
    private fun Details(
        play: GamePlay?,
        genre: String = "Action",
        rating: Boolean = true,
        synopsis: Boolean = true,
        title: String = "Super Metroid",
    ) {
        val metroid = LibraryLikeContent.SUPER_METROID
        val id = LibraryLikeContent.idAt(metroid).toString()
        val details = GameDetails(year = 1994, publisher = "Nintendo", genre = genre, players = "1", description = if (synopsis) SUPER_METROID_SYNOPSIS else "", rating = if (rating) SUPER_METROID_RATING else null)
        val base = LibraryLikeContent.of(hasBoxArt = true, metroidDetails = details, metroidTitle = title)
        val source =
            rememberSource {
                coverArtSource(
                    content = base,
                    synopses = if (synopsis) mapOf(artKey(id) to SUPER_METROID_SYNOPSIS) else emptyMap(),
                    ratings = if (rating) mapOf(artKey(id) to SUPER_METROID_RATING) else emptyMap(),
                )
            }
        val open = LibraryLikeContent.gamesFocused(metroid)
        val actions = if (narrow) open + Action.Move(Direction.Right) else open
        CompositionLocalProvider(LocalHomePlayFacts provides Plays(play)) { HomeWithArt(source, actions, base) }
    }

    private val played = GamePlay(lastPlayedDaysAgo = 2, playTimeMinutes = 252, emulator = EmulatorToStart("RetroArch: Snes9x", isInstalled = true), hasOwnEmulator = true)

    @Test
    fun detailsMany() = shoot("details_many") { Details(played) }

    @Test
    fun detailsFew() = shoot("details_few") { Details(null, genre = "", rating = false) }

    @Test
    fun detailsLongTitle() = shoot("details_long") { Details(played, title = LONG) }

    /** A title with a subtitle mark: the main title, then its subtitle on a line of its own. */
    @Test
    fun detailsSplitTitle() = shoot("details_split") { Details(played, title = SPLIT) }

    @Test
    fun detailsNoSynopsis() = shoot("details_nosynopsis") { Details(played, synopsis = false) }

    @Test
    fun detailsRealCover() = shoot("details_real") { RealCoverDetails("snes", 4) }

    @Test
    fun detailsRealPortrait() = shoot("details_real_portrait") { RealCoverDetails("psp", 7) }

    // ---- Facts above the fade, tall boxes ----

    private fun wideOnly(run: () -> Unit) {
        if (label != "576x432") run()
    }

    /** Frames for the Emulator rule: the same game with and without an emulator of its own. */
    @Test
    fun detailsOwnEmulator() = shoot("details_own_emulator") { Details(played) }

    @Test
    fun detailsSystemEmulator() = shoot("details_system_emulator") { Details(played.copy(hasOwnEmulator = false)) }

    /** The wide column ends above the bottom bar fade. */
    @Test
    fun fadeAbove() = wideOnly { shoot("fade_b_above") { Details(played) } }

    private fun tall(
        system: String,
        index: Int,
        emulator: String,
    ) = wideOnly {
        shoot("tall_${system}_beside") {
            val content =
                remember {
                    val base = FakeHomeContent.withArtwork()
                    base.withEntries(
                        base.entries.map { e ->
                            if (e.id != system) {
                                e
                            } else {
                                e.copy(rows = e.rows.mapIndexed { i, r -> (r.details ?: ArtworkFakes.detailsFor(r.title, i)).let { d -> r.copy(scrapeKey = artKey(r.id), details = d.copy(description = "", play = PlayFacts(3, 95, emulator))) } })
                            }
                        },
                    )
                }
            HomeWithArt(rememberSource { realCoverSource(content) }, gridActions(boardIndexOf(system), index), content)
        }
    }

    @Test
    fun tallPsp() = tall("psp", 7, "PPSSPP")

    @Test
    fun tallNes() = tall("nes", 2, "RetroArch: Mesen")

    private companion object {
        const val LONG = "The Legend of Zelda: A Link to the Past Four Swords Anniversary Edition"
        const val SPLIT = "Zone of the Enders - The Fist of Mars"
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class PolishAuditShots768Test : PolishAuditShotsBase("768x432")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class PolishAuditShots576Test : PolishAuditShotsBase("576x432")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class PolishAuditShotsThorTest : PolishAuditShotsBase("837x471")
