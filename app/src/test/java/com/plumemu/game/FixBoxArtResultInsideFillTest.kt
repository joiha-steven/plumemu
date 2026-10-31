package com.plumemu.game

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.BoxArtPanelFixtures
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Every Fix box art result sits wholly inside its own row (the fill): the year and publisher are in the value line, not under it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w768dp-h432dp-land-xhdpi")
class FixBoxArtResultInsideFillTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theFocusedResultsTextEndsInsideItsFill() {
        val state = BoxArtPanelFixtures.results().also { repeat(2) { _ -> it.onAction(Action.Move(Direction.Down)) } }
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite) { BoxArtPanelFixtures.Screen(BoxArtPanelFixtures.panel(state)) } }
        compose.waitForIdle()
        val hit = state.hitAt(state.cursor)!!
        val fill = compose.onNodeWithTag(FixBoxArtTags.fill(boxArtHitKey(hit)), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row = compose.onNodeWithTag(GamePanelTags.row(boxArtHitKey(hit)), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("fill ${fill.top}..${fill.bottom} covers the whole row ${row.top}..${row.bottom}", fill.top <= row.top && fill.bottom >= row.bottom)
    }
}
