package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pass does not move what a person is looking at: a grid packs at the stored shapes of the moment it was entered, deep in a
 * long list a change above and below moves no tile on screen, and the next visit of the system uses them. A picture in hand still
 * decides its own game.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridHeldAspectsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private class Source : HomeArtSource {
        val results = MutableStateFlow<Map<String, GameArt>>(emptyMap())

        // Nothing is known until a test lands a result: every slot stays Unknown.
        override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = kotlinx.coroutines.flow.flow { results.collect { if (it.isNotEmpty()) emit(it) } }

        override suspend fun extras(key: String) = DetailsExtras.None

        override val images: CoverImages = FakeCoverImages()
    }

    private fun rows(aspect: (Int) -> Float?) =
        (0 until COUNT).map { i -> GameRowModel(i.toString(), "Game $i", region = "", scrapeKey = artKey(i.toString()), systemId = "snes", artAspect = aspect(i)) }

    private fun library(rows: List<GameRowModel>): HomeContent {
        val other = listOf(GameRowModel("n", "Other", region = "", scrapeKey = artKey("n"), systemId = "nes"))
        return HomeContent.of(
            emptyList(),
            emptyList(),
            emptyList(),
            listOf(SystemGames("snes", "Super Nintendo", rows, CoverShape.Landscape), SystemGames("nes", "Nintendo", other, CoverShape.Landscape)),
            hasBoxArt = true,
        )
    }

    private var shown by mutableStateOf<HomeContent?>(null)
    private lateinit var strip: StripState

    private fun open(rows: List<GameRowModel>, source: Source) {
        shown = library(rows)
        val index = checkNotNull(shown).entries.indexOfFirst { it.id == "snes" }
        compose.setContent {
            PlumTheme { HomeWithArt(source, gridActions(index, DEEP), checkNotNull(shown), gridIds = setOf("snes", "nes"), onState = { strip = it }) }
        }
        compose.waitForIdle()
    }

    private fun bounds(): Map<Int, DpRect> =
        (0 until COUNT)
            .filter { compose.onAllNodesWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            .associateWith { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).getUnclippedBoundsInRoot() }

    /** Every game's shape reaches the rows; [aspect] differs by game, so the packing would break elsewhere. */
    private fun passWrites(aspect: (Int) -> Float?) {
        compose.runOnIdle { shown = library(rows(aspect)) }
        compose.waitForIdle()
    }

    private fun visitAnotherSystemAndComeBack() {
        compose.runOnIdle { strip.dispatch(Action.NextSystem) }
        compose.waitForIdle()
        compose.runOnIdle { strip.dispatch(Action.PreviousSystem) }
        compose.waitForIdle()
    }

    private fun widths(tiles: Map<Int, DpRect>) = tiles.values.map { Math.round((it.right - it.left).value) }.distinct()

    @Test
    fun aPassMovesNoTileOnScreenAndTheNextVisitUsesIt() {
        open(rows { null }, Source())
        val before = bounds()
        assertTrue("deep in the list: ${before.keys}", before.keys.none { it < ABOVE } && before.size >= SHOWN)
        assertEquals("one shape before the pass", 1, widths(before).size)
        passWrites(::mixed)
        assertEquals("nothing on screen moved", before, bounds())
        visitAnotherSystemAndComeBack()
        assertTrue("the next visit packs at the stored shapes: ${widths(bounds())}", widths(bounds()).size > 1)
    }

    /** Stored shapes the grid opened with, changed by a pass to others: held too, not only a first fill. */
    @Test
    fun aPassOverwritingShapesMovesNoTile() {
        open(rows(::mixed), Source())
        val before = bounds()
        passWrites { i -> mixed(i + 1) }
        assertEquals(before, bounds())
    }

    /** A picture a person asked for (replaced art, Fix Box Art) is in hand: its game follows it during the visit. */
    @Test
    fun aPictureInHandStillDecidesItsOwnGameDuringTheVisit() {
        val source = Source()
        open(rows { null }, source)
        val before = bounds()
        passWrites(::mixed)
        val picture = ArtPicture("/art/new.webp", 280, 400)
        compose.runOnIdle { source.results.value = mapOf(artKey(DEEP.toString()) to GameArt(picture, picture, GameDetails(1994, "Nintendo", "", "", ""))) }
        compose.waitForIdle()
        assertNotEquals(before[DEEP], bounds()[DEEP])
    }

    /** A rescan adds a game during the visit; the shapes known so far stay held and the list still moves for the new row. */
    @Test
    fun aRowAddedDuringTheVisitKeepsTheOthersHeld() {
        open(rows { null }, Source())
        val before = bounds()
        compose.runOnIdle {
            val added = GameRowModel("new", "Zzz", region = "", scrapeKey = artKey("new"), systemId = "snes")
            shown = library(rows(::mixed) + added)
        }
        compose.waitForIdle()
        assertEquals(before, bounds())
    }

    private fun mixed(i: Int): Float? = SHAPES[i % SHAPES.size]

    private companion object {
        const val COUNT = 120
        const val DEEP = 60
        const val ABOVE = 20
        const val SHOWN = 4
        val SHAPES = listOf(1.4f, 0.7f, 1f, 1.4f, 0.7f)
    }
}
