package com.plumemu.secondscreen

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.DpOffset
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The clock card learns from the part's layout whether the ring shows, a frame later. On the first
 * frame the level must already show somewhere: here the ring gives way (Plum Mono at Largest, the
 * widest figures, Roomy), so the clock card must still carry it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameLevelFirstFrameTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theLevelShowsOnTheFirstFrameWhereTheRingGivesWay() {
        RuntimeEnvironment.setQualifiers("w${SecondSize.Roomy.widthDp}dp-h${SecondSize.Roomy.heightDp}dp-land-xhdpi")
        val s = InGameBatterySamples.widest.first()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme(PlumThemeSettings(font = PlumFont.Mono, textSize = TextSize.Largest)) {
                InGameCard(time = "20:08", battery = s.reading, wifi = s.wifi, shift = { DpOffset.Zero }, part = { s.part }, formatTime = InGameBatterySamples.time24)
            }
        }
        val clock = compose.onAllNodesWithTagUnmerged(InGameTags.BATTERY)
        val ring = compose.onAllNodesWithTagUnmerged(InGameBatteryTags.RING)
        assertTrue("the level on the first frame: clock $clock, ring $ring", clock + ring > 0)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("settled: the ring gave way here", compose.onAllNodesWithTagUnmerged(InGameBatteryTags.RING) == 0)
        assertTrue("settled: the clock carries the level", compose.onAllNodesWithTagUnmerged(InGameTags.BATTERY) == 1)
    }

    private fun ComposeContentTestRule.onAllNodesWithTagUnmerged(tag: String): Int =
        onAllNodes(hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .count { it.layoutInfo.isPlaced }
}
