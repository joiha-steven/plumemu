package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Where a game wears its poster cover (poster-covers plan, step 3): signed out, and signed in once its
 * result is known to hold no picture; never while the result is still being read (the plain tile stands, no flash of colour).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomePosterCoversTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(content: @Composable () -> Unit) = compose.setContent { PlumTheme(MediumText.Default) { content() } }

    private fun cellsWithPoster(): Set<Int> =
        (0 until CELLS).filter { index ->
            compose.onAllNodes(hasTestTag(HomeTags.POSTER_COVER) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }.toSet()

    private fun cellsWithPicture(): Set<Int> =
        (0 until CELLS).filter { index ->
            compose.onAllNodes(hasTestTag(HomeTags.COVER_PICTURE) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }.toSet()

    private fun plainTiles(): Int =
        compose.onAllNodes(hasTestTag(HomeTags.WIDE_TILE_TITLE) or hasTestTag(HomeTags.PORTRAIT_TILE_TITLE), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun signedOutEveryGridCellWearsAPosterNamedByItsTitle() {
        show { HomeAt(posterSnesBoard(), gridActions(POSTER_SNES_INDEX, 1), gridIds = setOf("snes")) }
        compose.waitForIdle()
        assertEquals("every composed cell is a poster", (0 until CELLS).toSet(), cellsWithPoster())
        assertEquals("no plain tile among them", 0, plainTiles())
        POSTER_SNES_TITLES.take(CELLS).forEachIndexed { index, title ->
            compose.onNode(hasContentDescription(title) and (hasTestTag(HomeTags.cover(index)) or hasAnyAncestor(hasTestTag(HomeTags.cover(index)))), useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun signedInAGameWithoutAResultWearsAPosterAndTheOthersTheirPicture() {
        val content = posterSnesBoardWithDetails()
        val rows = content.entries[POSTER_SNES_INDEX].rows
        val without = setOf(1, 4)
        val source = realCoverSource(content).apply { results.value = results.value.filterKeys { key -> rows.indexOfFirst { artKey(it.id) == key } !in without } }
        show { HomeWithArt(source, gridActions(POSTER_SNES_INDEX, 0), content, gridIds = setOf("snes")) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        assertEquals("the games with no result wear posters", without, cellsWithPoster())
        assertEquals("the others show their pictures", (0 until CELLS).toSet() - without, cellsWithPicture())
    }

    @Test
    fun signedInWhileTheResultIsStillBeingReadTheTileStaysPlain() {
        val never =
            object : HomeArtSource {
                override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = flow { awaitCancellation() }

                override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

                override val images: CoverImages = FakeCoverImages()
            }
        val content = posterSnesBoardWithDetails()
        show { HomeWithArt(never, gridActions(POSTER_SNES_INDEX, 0), content, gridIds = setOf("snes")) }
        compose.waitForIdle()
        assertEquals("no poster before the answer", emptySet<Int>(), cellsWithPoster())
        assertTrue("the plain tiles stand", plainTiles() >= CELLS)
    }

    @Test
    fun aPictureStillDecodingKeepsThePlainTileAndNoPoster() {
        val content = posterSnesBoardWithDetails()
        val images = FakeCoverImages(pending = content.entries[POSTER_SNES_INDEX].rows.map { "/media/${it.id}/box-front-grid.webp" }.toSet())
        val source = FakeArtSource(realCoverSource(content).results.value, images = images)
        show { HomeWithArt(source, gridActions(POSTER_SNES_INDEX, 0), content, gridIds = setOf("snes")) }
        compose.waitForIdle()
        assertEquals("decoding: no poster", emptySet<Int>(), cellsWithPoster())
    }

    @Test
    fun aMixedRecentGridDrawsEachPosterInItsOwnSystemsShape() {
        val content = mixedSignedOutBoard()
        show { HomeAt(content, gridActions(0, 1), gridIds = setOf("recent")) }
        compose.waitForIdle()
        val entry = content.entries[0]
        val ratios = mutableSetOf<Int>()
        for (index in 0 until CELLS) {
            val row = entry.rows[index]
            val poster = compose.onNode(hasContentDescription(row.title) and (hasTestTag(HomeTags.cover(index)) or hasAnyAncestor(hasTestTag(HomeTags.cover(index)))), useUnmergedTree = true).getUnclippedBoundsInRoot()
            val aspect = (poster.right - poster.left) / (poster.bottom - poster.top)
            assertEquals("${row.title} is in ${row.systemId}'s shape", entry.shapeOf(row).widthOverHeight, aspect, 0.03f)
            ratios += (aspect * 100).toInt()
        }
        assertTrue("the shapes differ from game to game: $ratios", ratios.size > 2)
    }

    @Test
    fun theDetailsColumnWearsTheSameGamesPosterAboveItsText() {
        show { HomeAt(posterSnesBoard(), listOf(Action.TapItem(Column.Systems, POSTER_SNES_INDEX), Action.Enter, Action.TapItem(Column.Games, 2))) }
        compose.waitForIdle()
        val cover = compose.onNodeWithTag(HomeTags.DETAILS_COVER, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val title = compose.onNodeWithTag(HomeTags.DETAILS_TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the poster is above the title", cover.bottom <= title.top)
        compose.onNode(hasContentDescription("EarthBound") and hasAnyAncestor(hasTestTag(HomeTags.DETAILS_COVER)), useUnmergedTree = true).assertExists()
    }

    private companion object {
        const val CELLS = 6
        const val SETTLE_MS = 400L
    }
}
