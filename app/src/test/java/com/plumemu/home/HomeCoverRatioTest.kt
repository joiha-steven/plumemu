package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A box front whose shape is not its slot's (ScreenScraper's art does not always match the system's
 * shape: a portrait front in a wide slot, a wide one in a tall slot) stands on its slot's bottom:
 * centred in a grid cell (the shelf), on the text's left edge in the wide details column; its corner
 * follows its own shape (docs/design/rules.md › Real covers). Measured in dp, not by eye.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeCoverRatioTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(content: @Composable () -> Unit) = compose.setContent { PlumTheme(MediumText.Default) { content() } }

    private fun pictureIn(tag: String): Rect {
        val density = compose.density.density
        val b = compose.onAllNodes(hasTestTag(HomeTags.COVER_PICTURE) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
        return Rect(b.left / density, b.top / density, b.right / density, b.bottom / density)
    }

    private fun assertCentredOnBottom(
        label: String,
        slot: DpRect,
        picture: Rect,
    ) {
        assertEquals("$label: centred across the slot", (slot.left.value + slot.right.value) / 2f, (picture.left + picture.right) / 2f, EPSILON)
        assertEquals("$label: on the slot's bottom", slot.bottom.value, picture.bottom, EPSILON)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideDetailsKeepAPortraitASquareAndAWideBoxOnTheTextsLeftEdge() {
        val source = realCoverSource()
        show { RealCoverDetails("psp", PERSONA_3_PORTABLE, source) }
        for (aspect in listOf(0.58f, 1.0f, 1.6f)) {
            compose.runOnIdle { source.withLargeAspect("psp", PERSONA_3_PORTABLE, aspect) }
            compose.waitForIdle()
            val slot = compose.onNodeWithTag(HomeTags.DETAILS_COVER, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val picture = pictureIn(HomeTags.DETAILS_COVER)
            // One left edge for the cover and the text under it (design/rules.md: the title never moves).
            val title = compose.onNodeWithTag(HomeTags.DETAILS_TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals("details, box $aspect: cover and title share a left edge", title.left.value, picture.left, EPSILON)
            assertEquals("details, box $aspect: slot and title share a left edge", title.left.value, slot.left.value, EPSILON)
            assertEquals("details, box $aspect: on the slot's bottom", slot.bottom.value, picture.bottom, EPSILON)
        }
    }

    @Test
    fun aCoversCornerFollowsItsOwnShape() {
        assertEquals(PlumDimens.cornerCoverPortrait, pictureCorner(0.58f))
        assertEquals("square: the grid's 6", PlumDimens.cornerCoverPortrait, pictureCorner(1.0f))
        assertEquals(PlumDimens.cornerCoverPortrait, pictureCorner(1.19f))
        assertEquals(PlumDimens.cornerCoverLandscape, pictureCorner(1.2f))
        assertEquals(PlumDimens.cornerCoverLandscape, pictureCorner(1.6f))
    }

    private fun assertGridCentred(
        system: String,
        aspect: Float,
    ) {
        show { RealCoverGrid(system, 0, GridSize.Large) { _, _ -> aspect } }
        for (i in 0 until 3) {
            val cell = compose.onNodeWithTag(HomeTags.cover(i), useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertCentredOnBottom("$system grid cell $i, box $aspect", cell, pictureIn(HomeTags.cover(i)))
        }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aPortraitBoxInAWideGridCellIsCentredOnItsShelf() = assertGridCentred("snes", 0.58f)

    @Test
    @Config(qualifiers = WIDE)
    fun aWideBoxInATallGridCellIsCentredOnItsShelf() = assertGridCentred("megadrive", 1.6f)

    @Test
    @Config(qualifiers = WIDE)
    fun aPortraitBoxInASquareGridCellIsCentredOnItsShelf() = assertGridCentred("psx", 0.58f)

    private companion object {
        const val EPSILON = 0.6f
        const val WIDE = "w768dp-h432dp-land-xhdpi"
    }
}
