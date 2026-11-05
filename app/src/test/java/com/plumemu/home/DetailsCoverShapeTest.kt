package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The details cover keeps its box when the result lands: while the slot is [ArtSlot.Unknown] the box takes the stored shape, else
 * the system's, never the whole slot, so a cover never shows big and then shrinks. Wide (collared) and narrow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class DetailsCoverShapeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val wideSlot get() = DpSize(PlumDimens.detailsColumnWidth, PlumDimens.detailsCoverHeightWide)
    private val narrowSlot get() = DpSize(PlumDimens.detailsStopCoverWidth, PlumDimens.detailsStopCoverHeight)
    private val poster = PosterSpec("EarthBound", "Super Nintendo", "SNES")

    private fun found(aspect: Float): ArtSlot {
        val picture = ArtPicture("/art/box.png", (400 * aspect).toInt(), 400)
        return ArtSlot.Found(GameArt(picture, picture, DETAILS))
    }

    private data class Cover(val isWide: Boolean, val slot: ArtSlot, val stored: Float?, val poster: PosterSpec?)

    private var cover by mutableStateOf<Cover?>(null)

    /** One composition per test: the same cover moves from state to state, as on the screen. */
    private fun boxOf(next: Cover): DpRect {
        if (cover == null) {
            cover = next
            compose.setContent {
                PlumTheme(TableText.Default) {
                    val shown = checkNotNull(cover)
                    if (shown.isWide) {
                        CollaredDetailsCover(shown.slot, wideSlot, shown.poster, SYSTEM, null, shown.stored)
                    } else {
                        DetailsCover(shown.slot, narrowSlot, Alignment.TopStart, isBoxFitted = true, poster = shown.poster, posterAspect = SYSTEM, storedAspect = shown.stored)
                    }
                }
            }
        } else {
            compose.runOnIdle { cover = next }
        }
        compose.waitForIdle()
        return compose.onNodeWithTag(if (next.isWide) HomeTags.DETAILS_COVER_BOX else HomeTags.DETAILS_COVER, useUnmergedTree = true).getUnclippedBoundsInRoot()
    }

    private fun wide(slot: ArtSlot, stored: Float?, poster: PosterSpec? = this.poster) = boxOf(Cover(true, slot, stored, poster))

    private fun narrow(slot: ArtSlot, stored: Float?, poster: PosterSpec? = this.poster) = boxOf(Cover(false, slot, stored, poster))

    private fun assertSameBox(label: String, expected: DpRect, actual: DpRect) {
        assertEquals("$label left", expected.left.value, actual.left.value, SAME)
        assertEquals("$label top", expected.top.value, actual.top.value, SAME)
        assertEquals("$label right", expected.right.value, actual.right.value, SAME)
        assertEquals("$label bottom", expected.bottom.value, actual.bottom.value, SAME)
    }

    private fun assertSize(label: String, expected: DpSize, actual: DpRect) {
        assertEquals("$label width", expected.width.value, (actual.right - actual.left).value, EPSILON)
        assertEquals("$label height", expected.height.value, (actual.bottom - actual.top).value, EPSILON)
    }

    @Test
    fun wideCoverKeepsItsBoxWhenTheArtLandsWithAStoredShape() {
        for (aspect in listOf(TALL, SYSTEM)) assertSameBox("aspect $aspect", wide(ArtSlot.Unknown, aspect), wide(found(aspect), aspect))
    }

    @Test
    fun narrowCoverKeepsItsBoxWhenTheArtLandsWithAStoredShape() {
        for (aspect in listOf(TALL, SYSTEM)) assertSameBox("aspect $aspect", narrow(ArtSlot.Unknown, aspect), narrow(found(aspect), aspect))
    }

    @Test
    fun wideCoverWithNoStoredShapeReadsAtTheSystemsShapeNotTheWholeSlot() {
        val box = wide(ArtSlot.Unknown, stored = null)
        val inner = coverInner(wideSlot)
        assertSize("the system's shape", fitInside(SYSTEM, inner), box)
        assertNotEquals("not the whole slot", inner.width.value, (box.right - box.left).value, EPSILON)
    }

    @Test
    fun narrowCoverWithNoStoredShapeReadsAtTheSystemsShapeNotTheWholeSlot() {
        assertSize("the system's shape", fitInside(SYSTEM, narrowSlot), narrow(ArtSlot.Unknown, stored = null))
    }

    /** A picture in hand beats a stored value the pass has not caught up with yet: replaced art takes its new shape at once. */
    @Test
    fun thePictureGoesBeforeAStaleStoredShape() {
        assertSize("wide", fitInside(SYSTEM, coverInner(wideSlot)), wide(found(SYSTEM), stored = TALL))
        assertSize("narrow", fitInside(SYSTEM, narrowSlot), narrow(found(SYSTEM), stored = TALL))
    }

    /** The large file rounds to another shape than the grid file: within 2% the stored shape stands, so the cover does not nudge a pixel. */
    @Test
    fun aStoredShapeWithinTwoPercentOfThePictureStands() {
        val large = ArtPicture("/art/large.png", 1000, 1414)
        val stored = large.aspect * 1.019f
        assertEquals(stored, coverBoxAspect(ArtSlot.Found(GameArt(large, large, DETAILS)), large, stored, SYSTEM))
        assertEquals("the other side too", large.aspect * 0.981f, coverBoxAspect(ArtSlot.Found(GameArt(large, large, DETAILS)), large, large.aspect * 0.981f, SYSTEM))
        assertEquals("past 2% the picture wins", large.aspect, coverBoxAspect(ArtSlot.Found(GameArt(large, large, DETAILS)), large, large.aspect * 1.021f, SYSTEM))
    }

    @Test
    fun theOrderWithNoPicture() {
        assertEquals("unknown: stored", TALL, coverBoxAspect(ArtSlot.Unknown, null, TALL, SYSTEM))
        assertEquals("unknown: else the system's", SYSTEM, coverBoxAspect(ArtSlot.Unknown, null, null, SYSTEM))
        assertEquals("absent: nothing", null, coverBoxAspect(ArtSlot.Absent, null, TALL, SYSTEM))
    }

    /** A game known to have no picture: a poster at the system's shape as before, a stored shape left from removed art is not used. */
    @Test
    fun anAbsentSlotKeepsToday() {
        val inner = coverInner(wideSlot)
        assertSize("no poster: the whole slot", inner, wide(ArtSlot.Absent, stored = null, poster = null))
        assertSize("no poster, narrow: the whole slot", narrowSlot, narrow(ArtSlot.Absent, stored = null, poster = null))
        assertSize("poster: the system's shape", fitInside(SYSTEM, inner), wide(ArtSlot.Absent, stored = TALL))
    }

    /** The real screen: the row's stored shape reaches the cover while the result is still being read. */
    @Test
    fun theRowsStoredShapeReachesTheCoverOnTheHomeScreen() {
        val rows = listOf("Zork", "Mario").map { GameRowModel(it, it, region = "", scrapeKey = it, systemId = "snes", artAspect = TALL) }
        val content = HomeContent.of(emptyList(), emptyList(), emptyList(), listOf(SystemGames("snes", "Super Nintendo", rows)), hasBoxArt = true)
        val index = content.entries.indexOfFirst { it.id == "snes" }
        val reading = object : HomeArtSource {
            override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = flow { awaitCancellation() }

            override suspend fun extras(key: String) = DetailsExtras.None

            override val images: CoverImages = CoverImages.None
        }
        compose.setContent { PlumTheme(TableText.Default) { HomeWithArt(reading, gridActions(index, 0), content) } }
        compose.waitForIdle()
        val box = compose.onNodeWithTag(HomeTags.DETAILS_COVER_BOX, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(TALL, (box.right - box.left) / (box.bottom - box.top), ASPECT_TOLERANCE)
    }

    private companion object {
        val DETAILS = GameDetails(1994, "Nintendo", "", "", "")
        const val TALL = 0.7f
        const val SYSTEM = 1f

        /** Half a pixel at xhdpi: a box is placed on whole pixels. */
        const val EPSILON = 0.51f
        const val SAME = 0.001f
        const val ASPECT_TOLERANCE = 0.02f
    }
}
