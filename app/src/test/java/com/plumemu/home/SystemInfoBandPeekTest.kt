package com.plumemu.home

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The band's line is whole or absent: at the narrow details stop only 56 dp of the list shows, and the right end of the facts line
 * ("-2003") must not show there as a cut fragment. At the games stop the line is drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class SystemInfoBandPeekTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** How many different colours the picture has in the band's facts line, over [onlyLeftOf] dp from the screen's left edge. */
    private fun coloursInFacts(
        actions: List<Action>,
        onlyLeftOf: Int?,
    ): Int {
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { HomeAt(FakeHomeContent.withArtwork().withSystemInfo(), actions) } }
        compose.waitForIdle()
        val facts = compose.onNodeWithTag(HomeTags.SYSTEM_BAND_FACTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val image: ImageBitmap = compose.onRoot().captureToImage()
        val density = compose.density.density
        val map = image.toPixelMap()
        val left = maxOf(0, (facts.left.value * density).toInt())
        val right = minOf(map.width, ((onlyLeftOf?.dp ?: facts.right).value * density).toInt())
        val colours = HashSet<Int>()
        for (x in left until right) for (y in (facts.top.value * density).toInt() until (facts.bottom.value * density).toInt()) colours += map[x, y].hashCode()
        return colours.size
    }

    @Test
    fun theDetailsStopShowsNoPieceOfTheFactsLine() {
        val atDetails = BoardGamesFocused + Action.Move(Direction.Right)
        assertEquals("only the background in the 56 dp the list leaves", 1, coloursInFacts(atDetails, onlyLeftOf = 56))
    }

    @Test
    fun theGamesStopDrawsTheFactsLine() {
        assertTrue("the line is drawn", coloursInFacts(BoardGamesFocused, onlyLeftOf = null) > 1)
    }
}
