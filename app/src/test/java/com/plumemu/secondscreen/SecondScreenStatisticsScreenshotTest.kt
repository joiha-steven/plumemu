package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
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
 * The statistics card while Settings is open:
 * `second_screen_statistics_<case>_<roomy|compact>_<black|paper>.png`. Roomy at the Thor's bottom
 * screen (538x468 dp at 369 dpi, 1240x1079 px), Compact at the 4:3 size of the other second-screen
 * shots (480x360 dp). The covers are drawn in memory; no real box art is in the repository.
 */
abstract class SecondScreenStatisticsScreenshotBase(
    private val qualifiers: String,
    private val label: String,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val covers = SecondScreenStatisticsSamples.covers()

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers(qualifiers)
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = covers) } }
    }

    @Test
    fun theStatisticsCard() {
        val mismatches = mutableListOf<String>()
        val cases =
            listOf(
                Triple("with_covers", SecondScreenStatisticsSamples.withCovers, TextSize.Medium),
                Triple("signed_out", SecondScreenStatisticsSamples.signedOut, TextSize.Medium),
                Triple("nothing_played", SecondScreenStatisticsSamples.nothingPlayed, TextSize.Medium),
                Triple("largest_text", SecondScreenStatisticsSamples.withCovers, TextSize.Largest),
            )
        for ((look, base) in listOf("black" to PlumThemeSettings.Default, "paper" to PlumThemeSettings.Paper)) {
            for ((name, figures, textSize) in cases) {
                val shot = "second_screen_statistics_${name}_${label}_$look"
                // Each card as it first appears, after the calm mark (as on opening Settings), so the shots do not depend on their order.
                content = SecondScreenContent.Calm
                compose.waitForIdle()
                settings = base.copy(textSize = textSize)
                content = SecondScreenContent.Statistics(figures)
                compose.waitForIdle()
                try {
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$shot.png")
                } catch (e: AssertionError) {
                    mismatches += "$shot: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenStatisticsRoomyScreenshotTest : SecondScreenStatisticsScreenshotBase("w538dp-h468dp-land-369dpi", "roomy")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenStatisticsCompactScreenshotTest : SecondScreenStatisticsScreenshotBase("w480dp-h360dp-land-xhdpi", "compact")
