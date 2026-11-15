package com.plumemu.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A library without box art draws its poster in the wide details column as a real box front is drawn: in the glass collar,
 * at the system's shape fitted in the slot, standing on the slot's bottom edge. Not filling the column with no frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class PosterFrameCases {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun theWidePosterStandsInTheCollarAtTheSystemsShapeLikeARealCover() {
        val board = posterSnesBoard()
        compose.setContent {
            PlumTheme(TableText.Default) {
                CompositionLocalProvider(LocalHomePlayFacts provides FakePlayFacts()) { HomeAt(board, gridActions(POSTER_SNES_INDEX, 6)) }
            }
        }
        compose.waitForIdle()
        val shape = board.entries[POSTER_SNES_INDEX].coverShape.widthOverHeight
        val slot = bounds(HomeTags.DETAILS_COVER)
        val box = bounds(HomeTags.DETAILS_COVER_BOX)
        assertEquals("slot width", PlumDimens.detailsColumnWidth.value, (slot.right - slot.left).value, TOLERANCE)
        assertEquals("slot height", PlumDimens.detailsCoverHeightWide.value, (slot.bottom - slot.top).value, TOLERANCE)
        val expected = fitInside(shape, coverInner(DpSize(slot.right - slot.left, slot.bottom - slot.top)))
        assertEquals("box width", expected.width.value, (box.right - box.left).value, TOLERANCE)
        assertEquals("box height", expected.height.value, (box.bottom - box.top).value, TOLERANCE)
        assertEquals("box left: the collar's side padding in", slot.left.value + CollarSize.Cover.sidePadding.value, box.left.value, TOLERANCE)
        assertEquals("box bottom: the collar's bottom padding up from the slot", slot.bottom.value - CollarSize.Cover.bottomPadding.value, box.bottom.value, TOLERANCE)
        assertNotEquals("not the whole column", slot.right.value - slot.left.value, box.right.value - box.left.value, 4f)
        // The glass is drawn in the padding: just left of the box differs from the slot's corner, which lies outside the collar.
        val image = compose.onRoot().captureToImage().toPixelMap()
        val density = compose.density.density
        val inGlass = image[((box.left.value - 1f) * density).toInt(), (((box.top.value + box.bottom.value) / 2f) * density).toInt()]
        val outside = image[(slot.left.value * density).toInt(), (slot.top.value * density).toInt()]
        assertNotEquals("the collar's glass is drawn around the poster", outside, inGlass)
    }

    private companion object {
        const val TOLERANCE = 0.5f
    }
}

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeDetailsPosterFrame768Test : PosterFrameCases()

@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class HomeDetailsPosterFrame837Test : PosterFrameCases()
