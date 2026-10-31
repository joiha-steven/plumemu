package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.plumemu.graph.ScrapeCounts
import com.plumemu.graph.ScrapeStatus
import androidx.compose.ui.unit.dp
import com.plumemu.account.UtcTimeZone
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The account facts may arrive after the panel opened (the one `ssuserInfos` answer, speed step 4):
 * the buttons move down, and the focused Sign out must stay whole inside the panel, its focus ring
 * too, at the largest text size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsAccountFactsLateTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @get:Rule
    val utc = UtcTimeZone()

    @Test
    fun theFocusedSignOutStaysWholeWhenTheFactsArriveLate() {
        var content by mutableStateOf(BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.dailyLimit))
        val last = accountPageItems(content).lastIndex
        val focus = AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, PanelPage.ScraperAccount).copy(panelIndex = last)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { SettingsAt(focus, content = content) } }
        compose.waitForIdle()
        assertWhole(last, "before the facts")

        content = content.copy(accountLimits = BoxArtShots.limitsUsedUp)
        compose.waitForIdle()
        assertWhole(last, "after the facts arrived")
    }

    @Test
    fun aCountThatChangesInTheSameLinesLeavesAScrolledPanelAlone() {
        var content by mutableStateOf(BoxArtShots.content(BoxArtShots.SIGNED_IN, BoxArtShots.running).copy(accountLimits = BoxArtShots.limits))
        // Focus on Sign out, at the bottom; the person scrolls the panel back up by touch, so it is out of view.
        val last = accountPageItems(content).lastIndex
        val focus = AccountShotContent.focusOn(content, SettingsOption.ScraperAccount, PanelPage.ScraperAccount).copy(panelIndex = last)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = TextSize.Largest)) { SettingsAt(focus, content = content) } }
        compose.waitForIdle()
        val start = compose.onNodeWithTag(SettingsTags.panelItem(last), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        compose.onNodeWithTag(SettingsTags.ACCOUNT_PANEL, useUnmergedTree = true).performTouchInput { swipeDown() }
        compose.waitForIdle()
        val before = compose.onNodeWithTag(SettingsTags.panelItem(last), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        assertTrue("the swipe did not scroll the panel", before > start)

        // One more game delivered: the count in the status line changes, its lines do not.
        content = content.copy(boxArt = ScrapeStatus.Running(ScrapeCounts(withBoxFront = 301, listed = 2_054, pending = 1_713), gamesPerMinute = 5.7))
        compose.waitForIdle()
        val after = compose.onNodeWithTag(SettingsTags.panelItem(last), useUnmergedTree = true).getUnclippedBoundsInRoot().top
        assertTrue("the panel was pulled back: Sign out moved from $before to $after", after == before)
    }

    private fun assertWhole(
        index: Int,
        at: String,
    ) {
        val panel = compose.onNodeWithTag(SettingsTags.ACCOUNT_PANEL, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val item = compose.onNodeWithTag(SettingsTags.panelItem(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val ring = PlumDimens.focusRingOutset
        assertTrue("$at: Sign out's ring starts at ${item.top - ring}, the panel at ${panel.top}", item.top - ring >= panel.top - EPSILON)
        assertTrue("$at: Sign out's ring ends at ${item.bottom + ring}, the panel at ${panel.bottom}", item.bottom + ring <= panel.bottom + EPSILON)
    }

    private companion object {
        val EPSILON = 0.5.dp
    }
}
