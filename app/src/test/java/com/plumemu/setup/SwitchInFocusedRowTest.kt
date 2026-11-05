package com.plumemu.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.OptionLook
import com.plumemu.settings.screen.OptionRow
import com.plumemu.settings.screen.OptionValue
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The focused row is what has focus (its slab): the switch inside it keeps its resting collar, with no lit groove or ring in the
 * accent. The switch is off, so no accent pixel is left in it but a focus light.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SwitchInFocusedRowTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun accentPixels(
        tag: String,
        accent: Color,
    ): Int {
        val map = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return (0 until map.height).sumOf { y ->
            (0 until map.width).count { x ->
                val c = map[x, y]
                abs(c.red - accent.red) < NEAR && abs(c.green - accent.green) < NEAR && abs(c.blue - accent.blue) < NEAR
            }
        }
    }

    private fun settingsRow(settings: PlumThemeSettings) {
        var accent = Color.Unspecified
        compose.setContent {
            PlumTheme(settings) {
                accent = PlumTheme.colors.accent
                Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg).padding(PlumDimens.gameRowPadding)) {
                    OptionRow(SettingsOption.Animations, OptionValue.Switch(false), OptionLook.Focused, Modifier.width(PlumTheme.layout.settingsOptionsWidth))
                }
            }
        }
        compose.waitForIdle()
        assertEquals("a Settings switch in a focused row draws no accent light", 0, accentPixels(SettingsTags.value(SettingsOption.Animations), accent))
    }

    private fun statisticsCard(settings: PlumThemeSettings) {
        var accent = Color.Unspecified
        compose.setContent {
            PlumTheme(settings) {
                accent = PlumTheme.colors.accent
                SetupAt(SetupFakes.ready.copy(usageStatistics = false, focus = SetupControl.UsageStatistics))
            }
        }
        compose.waitForIdle()
        assertEquals("the focused statistics card's switch draws no accent light", 0, accentPixels(SetupTags.USAGE_SWITCH, accent))
    }

    @Test fun graphiteSettingsRow() = settingsRow(PlumThemeSettings.Graphite)

    @Test fun paperSettingsRow() = settingsRow(PlumThemeSettings.Paper)

    @Test fun graphiteStatisticsCard() = statisticsCard(PlumThemeSettings.Graphite)

    @Test fun paperStatisticsCard() = statisticsCard(PlumThemeSettings.Paper)

    private companion object {
        const val NEAR = 0.06f
    }
}
