package com.plumemu.secondscreen

import android.content.Intent
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.battery.BatteryPace
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.RealLookingCoverImages
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
 * The picking line on the second screen's cards, with a usual pace (30% an hour, the
 * battery at 60%) and without, on both sizes, and at Largest in Plum Serif beside a cover, where only one
 * line fits: `second_screen_battery_<case>_<roomy|compact>_<black|paper>.png`.
 */
abstract class SecondScreenBatteryLineScreenshotBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val covers = RealLookingCoverImages(mapOf(LANDSCAPE_FRONT.path to ("Super Metroid" to "Super Nintendo")))

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 60).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = covers) } }
    }

    @Test
    fun theCards() {
        val mismatches = mutableListOf<String>()
        val label = size.name.lowercase()
        val pace = BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)
        val art = DETAILS_GAME.copy(art = LANDSCAPE_FRONT, details = SHORT_DETAILS)
        val cases =
            listOf(
                Shot("details_pace", art.copy(batteryPace = pace)),
                Shot("details_no_pace", art),
                Shot("details_no_art_pace", DETAILS_GAME.copy(batteryPace = pace)),
                Shot("picking_pace", GAME_WITH_STAR.copy(batteryPace = pace)),
                Shot("picking_no_pace", GAME_WITH_STAR),
                Shot("details_pace_largest", art.copy(batteryPace = pace), TextSize.Largest),
                // Room for one line beside the cover: "About 2 h 45 min · 30% an hour", the words gone, the numbers kept.
                Shot("details_pace_largest_serif", art.copy(batteryPace = BatteryPace(minutesOnCharge = 166.0, percentPerHour = 30.0)), TextSize.Largest, PlumFont.Serif),
            )
        for ((look, base) in listOf("black" to PlumThemeSettings.Default, "paper" to PlumThemeSettings.Paper)) {
            for ((name, each, textSize, font) in cases) {
                // Each card as it first appears (the shots must not depend on their order).
                content = SecondScreenContent.Calm
                compose.waitForIdle()
                settings = base.copy(textSize = textSize, font = font)
                content = each
                compose.waitForIdle()
                val file = "second_screen_battery_${name}_${label}_$look"
                try {
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$file.png")
                } catch (e: AssertionError) {
                    mismatches += "$file: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBatteryLineRoomyScreenshotTest : SecondScreenBatteryLineScreenshotBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBatteryLineCompactScreenshotTest : SecondScreenBatteryLineScreenshotBase(SecondSize.Compact)

private data class Shot(
    val name: String,
    val content: SecondScreenContent,
    val textSize: TextSize = TextSize.Medium,
    val font: PlumFont = PlumFont.Sans,
)
