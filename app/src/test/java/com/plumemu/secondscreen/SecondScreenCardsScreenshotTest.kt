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
 * The picking cards of the second screen on Black (the default) and Paper, at the Thor's bottom
 * screen (538x468 dp) and a 4:3 one (480x360): `second_screen_<card>_<roomy|compact>_<black|paper>.png`;
 * the game cards also at text size Largest. Run once per size by the two subclasses below.
 */
abstract class SecondScreenCardsScreenshotBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(MediumText.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    @Before
    fun hostTheCard() {
        // xhdpi like the other screenshot tests: twice the dp.
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content) } }
    }

    @Test
    fun everyCardOnBothBackgrounds() {
        val mismatches = mutableListOf<String>()
        val label = size.name.lowercase()
        for ((look, base) in listOf("black" to MediumText.Default, "paper" to MediumText.Paper)) {
            for ((name, each) in ALL_SAMPLES) shoot("second_screen_${name}_${label}_$look", base, each, mismatches)
            val largest = base.copy(textSize = TextSize.Largest)
            shoot("second_screen_game_star_largest_${label}_$look", largest, GAME_WITH_STAR, mismatches)
            shoot("second_screen_game_long_title_largest_${label}_$look", largest, GAME_LONG_TITLE, mismatches)
            shoot("second_screen_system_long_name_largest_${label}_$look", largest, SYSTEM_LONG_NAME, mismatches)
        }
        shoot("second_screen_system_long_last_game_${label}_black", MediumText.Default, SYSTEM_LONG_LAST_GAME, mismatches)
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }

    private fun shoot(
        name: String,
        withSettings: PlumThemeSettings,
        showing: SecondScreenContent,
        mismatches: MutableList<String>,
    ) {
        settings = withSettings
        content = showing
        compose.waitForIdle()
        try {
            compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
        } catch (e: AssertionError) {
            // Collected, not swallowed: failed together with the others.
            mismatches += "$name: ${e.message}"
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardsRoomyScreenshotTest : SecondScreenCardsScreenshotBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenCardsCompactScreenshotTest : SecondScreenCardsScreenshotBase(SecondSize.Compact)
