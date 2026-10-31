package com.plumemu.secondscreen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.FontSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.withColors
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * The second screen's picking cards in ready-made sets, drawn through `SettingsTheme`
 * from the stored settings exactly as the window draws them: Super (Plum Sans, Medium), Dot matrix
 * (Plum Mono, Large) and Paper (Plum Serif, Largest), so colours, font and text size all show:
 * `second_screen_colors_<card>_<roomy|compact>_<set>.png`. The in-game card stays black by design
 * and is not drawn here. `SecondScreenFollowsLookTest` checks the same through the real window.
 */
abstract class SecondScreenColorSetsScreenshotBase(
    private val size: SecondSize,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var stored by mutableStateOf(PlumSettings())
    private var content by mutableStateOf<SecondScreenContent>(SecondScreenContent.Calm)
    private val covers = SecondScreenStatisticsSamples.covers()

    @Before
    fun hostTheCard() {
        RuntimeEnvironment.setQualifiers("w${size.widthDp}dp-h${size.heightDp}dp-land-xhdpi")
        compose.setContent { SettingsTheme(stored) { SecondScreenCard(content, images = covers) } }
    }

    @Test
    fun cardsInSuperDotMatrixAndPaper() {
        val mismatches = mutableListOf<String>()
        val looks =
            listOf(
                Triple(ColorSet.Super, FontSetting.Sans, TextSizeSetting.Medium),
                Triple(ColorSet.DotMatrix, FontSetting.Mono, TextSizeSetting.Large),
                Triple(ColorSet.Paper, FontSetting.Serif, TextSizeSetting.Largest),
            )
        val cards =
            listOf(
                "game" to GAME_WITH_STAR,
                "system" to SYSTEM_WITH_LAST,
                "statistics" to SecondScreenContent.Statistics(SecondScreenStatisticsSamples.withCovers),
            )
        for ((set, font, textSize) in looks) {
            for ((card, each) in cards) {
                val name = "second_screen_colors_${card}_${size.name.lowercase()}_${set.name.lowercase()}"
                // Each card as it first appears, after the calm mark, so the shots do not depend on their order.
                content = SecondScreenContent.Calm
                compose.waitForIdle()
                stored = PlumSettings(appearance = AppearanceSettings().withColors(set).copy(font = font, textSize = textSize, ownColors = null))
                content = each
                compose.waitForIdle()
                try {
                    compose.onRoot().captureRoboImage("${ReferenceShots.OUTPUT_DIR}/$name.png")
                } catch (e: AssertionError) {
                    // Collected, not swallowed: failed together with the others.
                    mismatches += "$name: ${e.message}"
                }
            }
        }
        if (mismatches.isNotEmpty()) fail("${mismatches.size} screenshot(s) differ:\n" + mismatches.joinToString("\n"))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenColorSetsRoomyScreenshotTest : SecondScreenColorSetsScreenshotBase(SecondSize.Roomy)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenColorSetsCompactScreenshotTest : SecondScreenColorSetsScreenshotBase(SecondSize.Compact)
