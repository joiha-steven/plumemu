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
 * The details card (layout C, a game with a ScreenScraper result):
 * `second_screen_details_<case>_<roomy|compact>_<black|paper>.png`. The covers are drawn in memory
 * ([RealLookingCoverImages]); no real box art is in the repository.
 */
abstract class SecondScreenBoxArtScreenshotBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var settings by mutableStateOf(PlumThemeSettings.Default)
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)

    private val covers =
        RealLookingCoverImages(
            mapOf(
                LANDSCAPE_FRONT.path to ("Super Metroid" to "Super Nintendo"),
                PORTRAIT_FRONT.path to ("Persona 3 Portable" to "PSP"),
            ),
        )

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { PlumTheme(settings) { SecondScreenCard(content, images = covers) } }
    }

    @Test
    fun theDetailsCard() {
        val mismatches = mutableListOf<String>()
        val label = size.name.lowercase()
        val portrait = PORTRAIT_GAME.copy(art = PORTRAIT_FRONT)
        val cases =
            listOf(
                Triple("portrait_box", portrait, TextSize.Medium),
                Triple("landscape_box", DETAILS_GAME.copy(art = LANDSCAPE_FRONT, details = SHORT_DETAILS), TextSize.Medium),
                Triple("no_art", DETAILS_GAME, TextSize.Medium),
                Triple("long_title", GAME_LONG_TITLE.withDetails(FULL_DETAILS, LANDSCAPE_FRONT).copy(fileName = "Final Fantasy VII International (Japan) (Disc 1).cue"), TextSize.Medium),
                Triple("long_file_name", DETAILS_GAME.copy(art = LANDSCAPE_FRONT, fileName = LONG_FILE_NAME), TextSize.Medium),
                Triple("long_file_name_no_art", DETAILS_GAME.copy(fileName = LONG_FILE_NAME), TextSize.Medium),
                Triple("largest_text", portrait, TextSize.Largest),
            )
        for ((look, base) in listOf("black" to PlumThemeSettings.Default, "paper" to PlumThemeSettings.Paper)) {
            for ((name, each, textSize) in cases) {
                shoot("second_screen_details_${name}_${label}_$look", base.copy(textSize = textSize), each, mismatches)
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }

    private fun shoot(
        name: String,
        withSettings: PlumThemeSettings,
        showing: SecondScreenContent,
        mismatches: MutableList<String>,
    ) {
        // Each card as it first appears. Shown right after another card, a title came out a pixel
        // or two taller per line in Robolectric, depending on what the card before had drawn (see
        // the star gap); the shots must not depend on their order.
        content = SecondScreenContent.Calm
        compose.waitForIdle()
        settings = withSettings
        content = showing
        compose.waitForIdle()
        try {
            compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
        } catch (e: AssertionError) {
            mismatches += "$name: ${e.message}"
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBoxArtRoomyScreenshotTest : SecondScreenBoxArtScreenshotBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenBoxArtCompactScreenshotTest : SecondScreenBoxArtScreenshotBase(SecondSize.Compact)
