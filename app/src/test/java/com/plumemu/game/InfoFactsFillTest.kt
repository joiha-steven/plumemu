package com.plumemu.game

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BoardGamesFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Info's facts window fills the card down to Back's gap; when the facts fit, none is clipped or faded. */
abstract class InfoFactsFillBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theWindowEndsOneBlockGapAboveBackAndAFittingTableIsNeitherClippedNorFaded() {
        val facts = AchievementsSamples.facts(null)
        val panel = OpenPanel(GameRef("1", "Super Metroid"), PanelScreen.Info(facts))
        compose.setContent { PlumTheme(TableText.Graphite) { HomeAt(FakeHomeContent.board(), BoardGamesFocused, panel = panel) } }
        compose.waitForIdle()
        val window = compose.onNodeWithTag(GamePanelTags.FACTS_SCROLL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val back = compose.onNodeWithTag(GamePanelTags.row(INFO_BACK_KEY), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val table = compose.onNodeWithTag(GamePanelTags.FACTS, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("the window ends the board's gap above Back", back.top.value - PlumDimens.panelBlockGap.value, window.bottom.value, 0.5f)
        assertTrue("the whole table (${table.top}..${table.bottom}) is inside the window (${window.top}..${window.bottom})", table.top >= window.top && table.bottom <= window.bottom)
        assertEquals("no fade at the bottom", 0, compose.onAllNodesWithTag(GamePanelTags.FADE_BOTTOM, useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals("no fade at the top", 0, compose.onAllNodesWithTag(GamePanelTags.FADE_TOP, useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class InfoFactsFill768Test : InfoFactsFillBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class InfoFactsFill576Test : InfoFactsFillBase()
