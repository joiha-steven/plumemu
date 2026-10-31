package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.BatteryReading
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The in-game card (Clock and battery) and Black at the Thor's bottom screen (538x468 dp) and a
 * 4:3 one (480x360): `second_screen_in_game_<state>_<roomy|compact>.png`. Also checks that the
 * block stays inside the window at every corner of the pixel shift with the widest texts and the largest font scale (2.0).
 */
abstract class InGameCardScreenshotBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var time by mutableStateOf(TIME_24)
    private var battery by mutableStateOf<BatteryReading?>(NORMAL)
    private var shift by mutableStateOf(DpOffset.Zero)
    private var black by mutableStateOf(false)
    private var wifi by mutableStateOf(false)
    private var fontScale by mutableStateOf(1f)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                if (black) InGameBlack() else InGameCard(time = time, battery = battery, wifi = wifi, shift = { shift })
            }
        }
    }

    @Test
    fun everyState() {
        val mismatches = mutableListOf<String>()
        val label = size.name.lowercase()
        shoot("second_screen_in_game_${label}", mismatches)
        battery = NORMAL.copy(isCharging = true)
        shoot("second_screen_in_game_charging_${label}", mismatches)
        battery = BatteryReading(percent = 12, isCharging = false)
        shoot("second_screen_in_game_low_battery_${label}", mismatches)
        battery = NORMAL
        shift = PixelShift.offsetOf(GridPoint(0, 0))
        shoot("second_screen_in_game_shifted_${label}", mismatches)
        shift = DpOffset.Zero
        time = TIME_12
        shoot("second_screen_in_game_12_hour_${label}", mismatches)
        wifi = true
        shoot("second_screen_in_game_wifi_${label}", mismatches)
        battery = BatteryReading(percent = 12, isCharging = false)
        shoot("second_screen_in_game_wifi_low_battery_${label}", mismatches)
        wifi = false
        battery = null
        shoot("second_screen_in_game_no_battery_${label}", mismatches)
        black = true
        shoot("second_screen_in_game_black_${label}", mismatches)
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }

    /** Android's font scale (Settings › Display › Font size) leaves the card as measured for burn-in. */
    @Test
    fun theFontScaleDoesNotChangeTheSizes() {
        compose.waitForIdle()
        val normal = compose.onNodeWithTag(InGameTags.BLOCK).fetchSemanticsNode().size
        for (scale in listOf(1.3f, 2f)) {
            fontScale = scale
            compose.waitForIdle()
            val scaled = compose.onNodeWithTag(InGameTags.BLOCK).fetchSemanticsNode().size
            assertEquals("the block's size at font scale $scale", normal, scaled)
        }
    }

    /** The widest texts at the four corners of the shift: nothing leaves the window. */
    @Test
    fun theBlockStaysInsideTheWindowAtEveryCornerOfTheShift() = cornersWith(BatteryReading(percent = 100, isCharging = true), wifi = true)

    /** Show battery off: the time alone, a smaller block, also inside at every corner. */
    @Test
    fun theClockOnlyBlockStaysInsideTheWindowAtEveryCornerOfTheShift() = cornersWith(null, wifi = false)

    private fun cornersWith(
        reading: BatteryReading?,
        wifi: Boolean,
    ) {
        time = WIDEST_12
        // The largest Android font scale: the sizes are fixed dp, so the block must still fit.
        fontScale = 2f
        this.wifi = wifi
        battery = reading
        val window = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val corners =
            listOf(
                GridPoint(0, 0),
                GridPoint(PixelShift.COLUMNS - 1, 0),
                GridPoint(0, PixelShift.ROWS - 1),
                GridPoint(PixelShift.COLUMNS - 1, PixelShift.ROWS - 1),
            )
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        var closest = Float.MAX_VALUE
        for (corner in corners) {
            shift = PixelShift.offsetOf(corner)
            compose.waitForIdle()
            // boundsInRoot leaves out the graphicsLayer translation (measured: it reads the same at every
            // point), so the shift is added here.
            val block = compose.onNodeWithTag(InGameTags.BLOCK).fetchSemanticsNode().boundsInRoot.translate(shift.x.value * density, shift.y.value * density)
            val room = minOf(block.left - window.left, window.right - block.right, block.top - window.top, window.bottom - block.bottom) / density
            closest = minOf(closest, room)
            assertTrue("$corner: block $block inside $window", room >= 0f)
        }
        println("In-game card ${size.name}: closest the block comes to an edge over the shift: $closest dp")
    }

    private fun shoot(
        name: String,
        mismatches: MutableList<String>,
    ) {
        compose.waitForIdle()
        try {
            compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
        } catch (e: AssertionError) {
            // Collected, not swallowed: failed together with the others.
            mismatches += "$name: ${e.message}"
        }
    }

    private companion object {
        val NORMAL = BatteryReading(percent = 82, isCharging = false)

        private fun format(
            pattern: String,
            epochMinute: Long,
        ): String = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(epochMinute * 60_000L))

        /** 22 October 2026, 21:47 UTC, as Android formats it in 24 and 12 hours (en-US). */
        val TIME_24 = format("H:mm", 29_878_427L)
        val TIME_12 = format("h:mm a", 29_878_427L)

        /** The widest 12-hour time: 12:58 PM. */
        val WIDEST_12 = format("h:mm a", 29_877_898L)
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameCardRoomyScreenshotTest : InGameCardScreenshotBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameCardCompactScreenshotTest : InGameCardScreenshotBase(SecondSize.Compact)
