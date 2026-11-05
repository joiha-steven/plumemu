package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A grid does not re-pack when art lands (a shape stored with the game): every tile keeps its bounds to the pixel from the first
 * frame, while its result is still being read, through the picture landing. Without a stored shape it does re-pack, which is
 * what shows this test can see one ([HomePackedGridFlowTest] keeps that path in step).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridStoredAspectTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** A result that lands when [land] says: until then every slot is [ArtSlot.Unknown]. */
    private class LateSource : HomeArtSource {
        private val results = MutableStateFlow<Map<String, GameArt>?>(null)

        fun land(art: Map<String, GameArt>) {
            results.value = art
        }

        override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = results.filterNotNull().map { all -> all.filterKeys { it in keys } }

        override suspend fun extras(key: String) = DetailsExtras.None

        override val images: CoverImages = FakeCoverImages()
    }

    private fun rows(stored: Boolean) =
        ASPECTS.mapIndexed { i, aspect ->
            GameRowModel(i.toString(), "Game $i", region = "", scrapeKey = artKey(i.toString()), systemId = "snes", artAspect = aspect.takeIf { stored })
        }

    private fun art(): Map<String, GameArt> =
        ASPECTS.indices.associate { i ->
            val picture = ArtPicture("/art/$i.webp", WIDTHS[i], HEIGHT)
            artKey(i.toString()) to GameArt(picture, picture, GameDetails(1994, "Nintendo", "", "", ""))
        }


    private fun show(rows: List<GameRowModel>, source: LateSource) {
        val shown = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows, CoverShape.Landscape)), hasBoxArt = true)
        val index = shown.entries.indexOfFirst { it.id == "snes" }
        compose.setContent { PlumTheme { HomeWithArt(source, gridActions(index, 0), shown, gridIds = setOf("snes")) } }
        compose.waitForIdle()
    }

    private fun bounds(): Map<Int, DpRect> =
        ASPECTS.indices
            .filter { compose.onAllNodesWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            .associateWith { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).getUnclippedBoundsInRoot() }

    @Test
    fun withStoredShapesEveryTileKeepsItsBoundsWhenTheArtLands() {
        val source = LateSource()
        show(rows(stored = true), source)
        val reading = bounds()
        assertTrue("several tiles are shown: ${reading.size}", reading.size >= SHOWN)
        source.land(art())
        compose.waitForIdle()
        assertEquals(reading, bounds())
    }

    @Test
    fun withoutStoredShapesTheSameArtMovesTiles() {
        val source = LateSource()
        show(rows(stored = false), source)
        val reading = bounds()
        source.land(art())
        compose.waitForIdle()
        assertNotEquals(reading, bounds())
    }

    /** The art was replaced and the pass has not run yet: the picture in hand decides the tile, not the stale value. */
    @Test
    fun aPictureInHandMovesATileWithAStaleStoredShape() {
        val source = LateSource()
        show(rows(stored = true).map { it.copy(artAspect = STALE) }, source)
        val stale = bounds()
        source.land(art())
        compose.waitForIdle()
        assertNotEquals(stale, bounds())
    }

    /** The art was removed and the pass has not run yet: a game known to have no picture packs at its system's shape. */
    @Test
    fun aStoredShapeIsNotUsedOnceTheGameIsKnownToHaveNoPicture() {
        val source = LateSource()
        show(rows(stored = true), source)
        val stored = bounds()
        source.land(emptyMap())
        compose.waitForIdle()
        assertNotEquals(stored, bounds())
    }

    private companion object {
        const val HEIGHT = 400
        val WIDTHS = listOf(560, 280, 400, 560, 280, 400, 560, 280, 560, 400, 280, 560)
        val ASPECTS = WIDTHS.map { it.toFloat() / HEIGHT }
        const val SHOWN = 6
        const val STALE = 2f
    }
}
