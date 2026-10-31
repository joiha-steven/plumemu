package com.plumemu.secondscreen

import com.plumemu.coreui.testing.MediumText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The in-game card with its battery part, every mockup state at the Thor's bottom
 * screen (538x468 dp, Roomy) and a 4:3 one (480x360, Compact), at each state's own point of the
 * drift as the mockups draw it: `second_screen_in_game_battery_<state>_<roomy|compact>.png`.
 */
abstract class InGameBatteryScreenshotBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var sample by mutableStateOf(InGameBatterySamples.heavy)

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent {
            PlumTheme(MediumText.Default) {
                val s = sample
                InGameCard(
                    time = InGameBatterySamples.format("H:mm", InGameBatterySamples.at(s.minute)),
                    battery = s.reading,
                    wifi = s.wifi,
                    shift = { s.shift },
                    part = { s.part },
                    formatTime = InGameBatterySamples.time24,
                )
            }
        }
    }

    @Test
    fun everyMockupState() {
        val mismatches = mutableListOf<String>()
        for (s in InGameBatterySamples.mockups + InGameBatterySamples.extras) {
            sample = s
            compose.waitForIdle()
            val name = "second_screen_in_game_battery_${s.name}_${size.name.lowercase()}"
            try {
                compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
            } catch (e: AssertionError) {
                // Collected, not swallowed: failed together with the others.
                mismatches += "$name: ${e.message}"
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryRoomyScreenshotTest : InGameBatteryScreenshotBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InGameBatteryCompactScreenshotTest : InGameBatteryScreenshotBase(SecondSize.Compact)
