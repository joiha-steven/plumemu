package com.plumemu.secondscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.InGameColors
import com.plumemu.coreui.theme.InGameDimens
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

/** At a play's start now is at the chart's left end: its glow reaches past the chart and must not be cut there. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryChartGlowTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Test
    fun theGlowAtThePlaysStartIsNotCutAtTheChartsLeftEdge() {
        RuntimeEnvironment.setQualifiers("w400dp-h200dp-land-xhdpi")
        val chart =
            BatteryChart(
                startMillis = 0,
                minutes = MIN_CHART_MINUTES,
                played = listOf(ChartPoint(0f, 60f)),
                aheadTo = ChartPoint(MIN_CHART_MINUTES, 52f),
                usualFrom = null,
                usualTo = null,
                hourMinutes = emptyList(),
                aheadEnds = true,
            )
        compose.setContent {
            Box(Modifier.size(400.dp, 200.dp).background(InGameColors.background).padding(CHART_INSET.dp)) {
                Box(Modifier.size(320.dp, 120.dp), propagateMinConstraints = true) {
                    InGameBatteryChart(chart, InGameDimens.Roomy, InGameColors.partText)
                }
            }
        }
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // A column 3 dp left of the chart's own bounds, inside the glow (13 dp round a dot 7 dp in).
        val x = ((CHART_INSET - 3) * density).toInt()
        val lit = (0 until pixels.height).count { pixels[x, it].red > 0f }
        assertTrue("the glow reaches past the chart's left edge: $lit pixels lit in that column", lit > 0)
    }

    private companion object {
        const val CHART_INSET = 40
    }
}
