package com.plumemu.battery

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BatteryReading
import com.plumemu.home.LocalBatteryReading
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.launcher.play.battery.BatteryReader
import com.plumemu.launcher.play.battery.UsualPace
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The picking line's figures follow the battery (review fixes 2): read once with the play
 * facts, then worked out again ([BatteryPaceNow]) when the level or charging the line shows changes, so a
 * game rested on through a charge does not keep the time of the charge before. A 5,000 mAh battery, a
 * game that draws 1 A: 20% an hour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PickingBatteryLineNowTest {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** The device's counter and level as `BatteryManager` gives them, set by the test. */
    private var level = 25
    private val reader =
        object : BatteryReader {
            override fun nowMs() = 0L

            override fun currentNow() = BatteryReader.UNKNOWN

            override fun chargeCounter() = FULL_MICROAMP_HOURS * level / 100

            override fun level() = level

            override fun isCharging() = false

            override fun chargeTimeRemainingMs() = BatteryReader.UNKNOWN
        }
    private val usualPace = UsualPace(reader)
    private val reading = mutableStateOf<BatteryReading?>(BatteryReading(25, isCharging = false))
    private var asked = 0
    private var fails = false
    private val now =
        BatteryPaceNow { draw ->
            asked++
            if (fails) throw IllegalStateException("battery service gone")
            LibraryBatteryPaceNow(usualPace, io = Dispatchers.Unconfined).of(draw)
        }

    /** The pace as the facts read it at 25% (`LibraryBatteryPace`): 75 minutes on this charge, 20% an hour. */
    private fun readWithTheFacts(): BatteryPace = requireNotNull(BatteryPace.of(usualPace.of(DRAW_MICROAMPS), DRAW_MICROAMPS))

    private fun show(pace: BatteryPace) {
        compose.setContent {
            PlumTheme {
                CompositionLocalProvider(LocalBatteryReading provides reading, LocalBatteryPaceNow provides now) {
                    PickingBatteryLine(pace, PlumTheme.type.meta)
                }
            }
        }
        compose.waitForIdle()
    }

    private fun battery(
        percent: Int,
        isCharging: Boolean,
    ) {
        level = percent
        compose.runOnIdle { reading.value = BatteryReading(percent, isCharging) }
        compose.waitForIdle()
    }

    private fun shown(): String? =
        compose.onAllNodes(androidx.compose.ui.test.hasTestTag(PickingBatteryTags.TEXT), useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
            ?.config?.get(SemanticsProperties.Text)?.joinToString("")

    @Test
    fun aChargeWhileRestingGivesTheTimeOfTheNewCharge() {
        show(readWithTheFacts())
        assertTrue("at 25%: ${shown()}", shown().orEmpty().startsWith("1 h 15 min on this charge"))
        assertEquals("the figures just read are not read again", 0, asked)
        battery(60, isCharging = true)
        assertEquals("charging: the pace alone", "20% an hour", shown())
        battery(100, isCharging = false)
        assertTrue("unplugged at 100%: the time of the full charge, not of 25%: ${shown()}", shown().orEmpty().startsWith("5 h on this charge"))
    }

    @Test
    fun aBroadcastThatChangesNeitherLevelNorChargingReadsNothing() {
        show(readWithTheFacts())
        // Voltage or temperature: the system sends the same level and charging again.
        battery(25, isCharging = false)
        assertEquals(0, asked)
        battery(24, isCharging = false)
        assertEquals("one percent less: read again", 1, asked)
        assertTrue(shown().orEmpty().startsWith("About 1 h 15 min on this charge"))
    }

    @Test
    fun aReadThatFailsKeepsTheFiguresShown() {
        show(readWithTheFacts())
        fails = true
        battery(20, isCharging = false)
        assertEquals(1, asked)
        assertTrue("kept: ${shown()}", shown().orEmpty().startsWith("1 h 15 min on this charge"))
    }

    @Test
    fun aPaceWithoutItsDrawKeepsItsFigures() {
        show(BatteryPace(minutesOnCharge = 45.0, percentPerHour = null))
        battery(100, isCharging = false)
        assertEquals(0, asked)
        assertEquals("45 min on this charge", shown())
    }

    private companion object {
        const val FULL_MICROAMP_HOURS = 5_000_000L
        const val DRAW_MICROAMPS = 1_000_000L
    }
}
