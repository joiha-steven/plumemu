package com.plumemu.home

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.captureRoboImage
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The band over a system's games list at 576x432, 768x432 and
 * 837x471, Graphite and Paper, Medium and Largest text; plus a system with no facts, one still in
 * production, and the setting off (today's layout).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeSystemInfoScreenshotTest {
    private val content = FakeHomeContent.board().withSystemInfo()

    private fun focus(id: String) = listOf(Action.TapItem(Column.Systems, content.entries.indexOfFirst { it.id == id }))

    /** The three sizes: the two of [ReferenceShots] and the Thor-like 837x471 it does not take. */
    private fun shoot(
        screen: String,
        textSize: TextSize = TextSize.Medium,
        screenContent: @Composable () -> Unit,
    ) {
        ReferenceShots.capture(screen, textSize = textSize, content = screenContent)
        RuntimeEnvironment.setQualifiers("w837dp-h471dp-land-xhdpi")
        for (preset in ReferenceShots.Preset.entries) {
            val suffix = if (textSize == TextSize.Medium) "" else "_${textSize.name.lowercase()}"
            captureRoboImage(filePath = "${ReferenceShots.OUTPUT_DIR}/${screen}_837x471_${preset.label}$suffix.png") {
                PlumTheme(preset.settings.copy(textSize = textSize)) { screenContent() }
            }
        }
    }

    @Test
    fun snesMedium() = shoot("home_systems_info") { HomeAt(content, BoardSystemsFocused) }

    @Test
    fun snesLargest() = shoot("home_systems_info", TextSize.Largest) { HomeAt(content, BoardSystemsFocused) }

    @Test
    fun snesAboutMedium() = ReferenceShots.capture("home_systems_info_about") { HomeAt(content.withSnesAbout(), BoardSystemsFocused) }

    @Test
    fun snesAboutLargest() = ReferenceShots.capture("home_systems_info_about", textSize = TextSize.Largest) { HomeAt(content.withSnesAbout(), BoardSystemsFocused) }

    @Test
    fun arcadeAbout() = ReferenceShots.capture("home_systems_info_about_arcade") { HomeAt(content.withArcadeAbout(), focus(BOARD_ARCADE_ID)) }

    @Test
    fun noFacts() = shoot("home_systems_info_none") { HomeAt(content, focus(BOARD_ARCADE_ID)) }

    @Test
    fun stillInProduction() = shoot("home_systems_info_switch") { HomeAt(content, focus(BOARD_SWITCH_ID)) }

    @Test
    fun settingOff() = shoot("home_systems_info_off") { HomeAt(content, BoardSystemsFocused, showSystemInfo = false) }
}
