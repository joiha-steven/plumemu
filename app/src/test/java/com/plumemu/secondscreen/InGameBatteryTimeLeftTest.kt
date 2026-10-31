package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpOffset
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BatteryReading
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.launcher.play.battery.BatteryEstimate
import com.plumemu.launcher.play.battery.BatterySnapshot
import com.plumemu.launcher.play.battery.LevelPoint
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The time to full as words, from the meter's estimate through the model to the drawn text (Android's time to full is exact, "1 h 03 to full"; the counter's
 * estimate is rounded like the time left). An estimate that rounding leaves whole is still an
 * estimate: "1 h 30 min to full", never Android's clock style.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryTimeLeftTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var part by mutableStateOf<InGameBattery?>(null)

    /** 22 October 2026, 21:47 UTC, 47 minutes into a play charging since its start. */
    private val now = 29_878_427L * 60_000L
    private val elapsedNow = 4_000_000L

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w538dp-h468dp-land-369dpi")
        compose.setContent {
            PlumTheme {
                InGameCard(time = "21:47", battery = BatteryReading(58, true), shift = { DpOffset.Zero }, part = { part }, formatTime = InGameBatterySamples.time24)
            }
        }
    }

    private fun timeToFull(
        minutes: Double,
        androids: Boolean,
    ): String {
        val estimate =
            BatteryEstimate(
                isCharging = true, drawMicroamps = -2_500_000, minutesLeft = minutes, measuredMs = 0,
                fullChargeMicroampHours = 6_254_000.0, isMinutesLeftExact = androids,
            )
        val snapshot = BatterySnapshot(estimate, null, elapsedNow, listOf(LevelPoint(elapsedNow - 47 * 60_000L, 40, true)))
        part = inGameBatteryOf(snapshot, BatteryReading(58, true), now, driftNote = false)
        compose.waitForIdle()
        val node = compose.onNodeWithTag(InGameBatteryTags.TIME_LEFT, useUnmergedTree = true).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString("") { it.text }
    }

    @Test
    fun androidsOwnTimeToFullReadsLikeAClock() {
        assertEquals("1 h 30 to full", timeToFull(90.0, androids = true))
        assertEquals("1 h 03 to full", timeToFull(63.0, androids = true))
    }

    @Test
    fun theCountersEstimateLeftWholeByRoundingKeepsItsMinutesWord() {
        assertEquals("1 h 30 min to full", timeToFull(90.0, androids = false))
    }

    @Test
    fun theCountersEstimateRoundedSaysAbout() {
        assertEquals("About 1 h 30 min to full", timeToFull(91.0, androids = false))
    }
}
