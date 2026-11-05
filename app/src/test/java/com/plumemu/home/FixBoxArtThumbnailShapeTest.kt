package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.game.FixBoxArtTags
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.CoroutineContext

/**
 * Fix box art's thumbnail keeps one outer box: the placeholder's shape fitted in its square slot. A picture of another
 * shape (the landscape hit here) is fitted inside that box when its size is read, so the tile never changes shape under the eye.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class FixBoxArtThumbnailShapeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(reading: CoroutineDispatcher) {
        val state = BoxArtPanelFixtures.results(BoxArtPanelFixtures.hits)
        compose.setContent { PlumTheme { BoxArtPanelFixtures.WithCovers(reading) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = BoxArtPanelFixtures.panel(state)) } } }
        compose.waitForIdle()
    }

    private fun inSlot(tag: String) = hasTestTag(tag) and hasAnyAncestor(hasTestTag(FixBoxArtTags.thumbnail(BoxArtPanelFixtures.landscape.id)))

    private fun slot() = compose.onNodeWithTag(FixBoxArtTags.thumbnail(BoxArtPanelFixtures.landscape.id), useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** The placeholder's box, centred in the slot: 0.7 wide for one high, as tall as the slot. */
    private fun box(): Pair<Dp, Dp> = slot().let { it.height * PLACEHOLDER to it.height }

    @Test
    fun theTileBeforeTheSizeIsReadIsThePlaceholderBox() {
        show(Never)
        val tile = compose.onNode(inSlot(FixBoxArtTags.TILE), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val (w, h) = box()
        assertEquals("tile width", w.value, tile.width.value, 0.5f)
        assertEquals("tile height", h.value, tile.height.value, 0.5f)
    }

    // The requirement changed: a landscape picture is fitted inside the fixed square slot, not inside the portrait placeholder's box (it lost 30% of its width there).
    @Test
    fun aLandscapePictureIsFittedInsideTheSquareSlot() {
        show(Dispatchers.Unconfined)
        val picture = compose.onNode(inSlot(FixBoxArtTags.PICTURE), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val slot = slot()
        assertEquals("the slot stays square", slot.width.value, slot.height.value, 0.5f)
        assertEquals("the picture fills the slot's width", slot.width.value, picture.width.value, 0.5f)
        assertTrue("the picture ${picture.width} x ${picture.height} lies in the slot ${slot.width} x ${slot.height}", picture.height.value <= slot.height.value + 0.5f)
        assertEquals("fitted by its own shape (1.4 wide for one high)", 1.4f, picture.width.value / picture.height.value, 0.05f)
    }

    private object Never : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
    }

    private companion object {
        const val PLACEHOLDER = 0.7f
    }
}
