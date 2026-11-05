package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * With the systems column focused the strip rests at its start and the details column lies past the screen's right edge: box art is
 * shown whole or not at all, so a cover there ends inside the page margin and no text is cut. With the games focused the cover shows.
 */
abstract class HomeSystemsCoverBoundsBase(
    private val screenWidth: Dp,
    /** Wide screens have the details column beside the list, so their cover shows once the games are focused. */
    private val isWide: Boolean = false,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun show(actions: List<Action>) {
        compose.setContent { PlumTheme(TableText.Default) { HomeAt(FakeHomeContent.board(), actions) } }
        compose.waitForIdle()
    }

    private fun covers() = compose.onAllNodesWithTag(HomeTags.DETAILS_COVER, useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun theCoverIsWholeInsideTheMarginOrLeftOut() {
        show(BoardSystemsFocused)
        if (covers() > 0) {
            val cover = compose.onNodeWithTag(HomeTags.DETAILS_COVER, useUnmergedTree = true)
            val right = cover.getUnclippedBoundsInRoot().right
            assertNoTextCut(cover.fetchSemanticsNode(), "cover at $screenWidth")
            assertTrue("cover right $right is past the margin ${screenWidth - PlumDimens.stripMarginSide}", right <= screenWidth - PlumDimens.stripMarginSide + 0.5.dp)
        }
    }

    @Test
    fun theCoverShowsWithTheGamesFocused() {
        if (!isWide) return
        show(BoardGamesFocused)
        assertTrue("the details cover is there with the games focused", covers() == 1)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class HomeSystemsCover768Test : HomeSystemsCoverBoundsBase(768.dp, isWide = true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class HomeSystemsCover576Test : HomeSystemsCoverBoundsBase(576.dp)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class HomeSystemsCover640Test : HomeSystemsCoverBoundsBase(640.dp)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class HomeSystemsCover832Test : HomeSystemsCoverBoundsBase(832.dp, isWide = true)

/** The Retroid Pocket 6's 1920x1080 at density 2.25. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w853dp-h480dp-land-360dpi")
class HomeSystemsCover1920Test : HomeSystemsCoverBoundsBase(853.dp, isWide = true)
