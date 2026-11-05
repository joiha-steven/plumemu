package com.plumemu.secondscreen

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.game.LocalBadgeImages
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import org.junit.Assert.fail
import android.content.Intent
import android.os.BatteryManager
import com.plumemu.battery.BatteryPace
import org.junit.Before
import org.robolectric.RuntimeEnvironment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The game card next to its boards: `second_game_<case>_<graphite|paper>.png`, `second_game_compact_<case>.png`. */
abstract class SecondScreenGameCardShots(
    private val prefix: String,
    private val looks: List<Pair<String, PlumThemeSettings>>,
) {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var content by mutableStateOf<SecondScreenContent>(BOARD_GAME)

    private val cases =
        listOf(
            "board" to BOARD_GAME,
            "no_ra" to BOARD_GAME_NO_RA,
            "no_points" to BOARD_GAME.copy(ra = com.plumemu.graph.StoredAchievements(earned = 3, total = 12)),
            "never_played" to BOARD_GAME_NO_RA.copy(lastPlayedDaysAgo = null, weeks = PlayWeeks(List(8) { 0L }, 0, 0)),
            "long_title" to BOARD_GAME.copy(title = LONG_TITLE),
            "no_description" to BOARD_GAME.copy(details = null),
            // The battery line in the foot, above the file name (the battery at 60 %, 30 % an hour).
            "battery" to BOARD_GAME.copy(batteryPace = BatteryPace(minutesOnCharge = 120.0, percentPerHour = 30.0)),
        )

    // The system's battery broadcast is sticky; a test can only send one with this deprecated call.
    @Suppress("DEPRECATION")
    @Before
    fun sendTheBattery() {
        RuntimeEnvironment.getApplication().sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED).putExtra(BatteryManager.EXTRA_LEVEL, 60).putExtra(BatteryManager.EXTRA_SCALE, 100),
        )
    }

    @Test
    fun theCards() {
        compose.setContent {
            PlumTheme(settings) { CompositionLocalProvider(LocalBadgeImages provides CacheOnlyBadges()) { SecondScreenCard(content) } }
        }
        val mismatches = mutableListOf<String>()
        for ((look, base) in looks) {
            for ((case, each) in cases) {
                settings = base
                content = each
                compose.waitForIdle()
                val name = "${prefix}_${case}_$look"
                try {
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
                } catch (e: AssertionError) {
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}

/** The Thor's bottom screen, 538x468 dp at 369 dpi, Graphite and Paper. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w538dp-h468dp-land-369dpi")
class SecondScreenGameCardScreenshotTest :
    SecondScreenGameCardShots("second_game", listOf("graphite" to PlumThemeSettings.Graphite.copy(font = PlumFont.Jost), "paper" to PlumThemeSettings.Paper.copy(font = PlumFont.Jost)))

/** A 4:3 bottom screen, 480x360 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w480dp-h360dp-land-369dpi")
class SecondScreenGameCardCompactScreenshotTest : SecondScreenGameCardShots("second_game_compact", listOf("graphite" to PlumThemeSettings.Graphite.copy(font = PlumFont.Jost)))
