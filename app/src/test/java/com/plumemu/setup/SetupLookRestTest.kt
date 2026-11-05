package com.plumemu.setup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pick a look keeps its title in view on a 4:3 handheld, with every tile focused, on arriving and after Back from box art. A Nova at
 * 640 x 480 with the Larger text Setup applies there scrolled the page by the whole title for a few dp of overflow, and left that room empty below.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SetupLookRestTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state by mutableStateOf(SetupFakes.look)

    private fun assertTheTitleStaysForEveryTile(size: TextSize) {
        compose.setContent { PlumTheme(PlumThemeSettings.Default.copy(textSize = size)) { Box(Modifier.fillMaxSize()) { SetupAt(state) } } }
        for (index in SetupLooks.sets.indices) {
            val focused = SetupFakes.look.copy(focus = SetupControl.lookTile(index))
            state = focused
            compose.waitForIdle()
            assertTheTitleIsWhole("tile $index")
            assertTileAboveTheHints(index)
            state = SetupFakes.boxArtQuestion
            compose.waitForIdle()
            state = focused
            compose.waitForIdle()
            assertTheTitleIsWhole("tile $index after Back")
            assertTileAboveTheHints(index)
        }
    }

    private fun assertTheTitleIsWhole(what: String) {
        val frame = compose.onNodeWithTag(SetupTags.SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        val windowTop = frame + PlumDimens.focusRingOutset.value + SlabDimens.listEndRoom.value
        val title = compose.onNodeWithTag(SetupTags.TITLE, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value
        assertTrue("$what: the title starts at $title, above the window's top $windowTop", title >= windowTop - TOLERANCE)
    }

    private fun assertTileAboveTheHints(index: Int) {
        val tile = compose.onNodeWithTag(SetupTags.control(SetupControl.lookTile(index)!!), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hints = compose.onNodeWithTag(SetupTags.HINTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("Tile $index ends at ${tile.bottom}, the hints start at ${hints.top}", hints.top.value - tile.bottom.value >= PlumDimens.focusRingOutset.value)
    }

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun nova640KeepsItsTitle() = assertTheTitleStaysForEveryTile(TextSize.Default)

    @Test
    @Config(qualifiers = "w640dp-h480dp-land-xhdpi")
    fun nova640AtLargerTextKeepsItsTitle() = assertTheTitleStaysForEveryTile(TextSize.Larger)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun nova576KeepsItsTitle() = assertTheTitleStaysForEveryTile(TextSize.Default)

    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun nova576AtLargerTextKeepsItsTitle() = assertTheTitleStaysForEveryTile(TextSize.Larger)

    private companion object {
        const val TOLERANCE = 0.6f
    }
}
