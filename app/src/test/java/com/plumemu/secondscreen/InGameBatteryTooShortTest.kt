package com.plumemu.secondscreen

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.DpOffset
import com.plumemu.coreui.testing.ReferenceShots
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
 * A window too short for the chart even once everything else has given way (Compact width, Plum
 * Serif at Largest, the widest figures): no chart at all, never one squeezed to nothing and drawn
 * over the time left.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryTooShortTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun noRoomForTheChartDrawsNoChart() {
        RuntimeEnvironment.setQualifiers("w${SecondSize.Compact.widthDp}dp-h${SHORT_HEIGHT_DP}dp-land-xhdpi")
        val s = InGameBatterySamples.widest.first()
        compose.setContent {
            PlumTheme(PlumThemeSettings(textSize = TextSize.Largest)) {
                InGameCard(time = "20:08", battery = s.reading, wifi = s.wifi, shift = { DpOffset.Zero }, part = { s.part }, formatTime = InGameBatterySamples.time24)
            }
        }
        val chart =
            compose.onAllNodes(hasTestTag(InGameBatteryTags.CHART), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .filter { it.layoutInfo.isPlaced }
        assertTrue("no chart placed: ${chart.map { it.boundsInRoot }}", chart.isEmpty())
        val timeLeft =
            compose.onAllNodes(hasTestTag(InGameBatteryTags.TIME_LEFT), useUnmergedTree = true)
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
        assertTrue("the time left still shows", timeLeft.any { it.layoutInfo.isPlaced })
    }

    private companion object {
        /** Under the clock card, the head and the chart's minimum at Largest. */
        const val SHORT_HEIGHT_DP = 240
    }
}
