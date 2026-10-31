package com.plumemu.textsize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coreui.components.LocalScanPulsePhase
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.BOARD_BATTERY
import com.plumemu.home.BOARD_CLOCK
import com.plumemu.home.appComposeRule
import com.plumemu.home.rulesAround
import com.plumemu.settings.screen.ColorPages
import com.plumemu.settings.screen.NoActions
import com.plumemu.settings.screen.PanelPage
import com.plumemu.settings.screen.NoTouch
import com.plumemu.settings.screen.panelAt
import com.plumemu.settings.screen.SettingsLayout
import com.plumemu.settings.screen.SettingsModel
import com.plumemu.settings.screen.SettingsOption
import com.plumemu.settings.screen.SettingsTags
import com.plumemu.settings.screen.TEST_VERSION
import com.plumemu.settings.screen.settingsForShot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › Accent color with the By system tile at every text size, the way
 * `SettingsStatisticsFitTest` checks Statistics: no text is ellipsized, clipped, too short or on top
 * of another, and what the cursor is on is fully on screen: the presets panel, the own-colour page,
 * a refused hex code with its "Use" row, and the notice that a background moved the text.
 */
abstract class SettingsAccentBySystemFitBase(
    private val widthDp: Int,
    private val heightDp: Int,
) {
    private val compose: ComposeContentTestRule = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    @Composable
    private fun Panel() {
        val base = settingsForShot()
        val settings = base.copy(appearance = base.appearance.copy(accent = ColorSetting.BySystem(ColorSetting.Preset("Red"))))
        val model = remember { SettingsModel(NoActions, panelAt(SettingsOption.Accent, PanelPage.AccentPresets, ColorPages.bySystemIndex)) }
        CompositionLocalProvider(LocalScanPulsePhase provides 0.5f) {
            SettingsLayout(settings, model, NoTouch, TEST_VERSION, clock = { BOARD_CLOCK }, battery = { BOARD_BATTERY })
        }
    }

    @Test
    fun tileFocusedFitsAtEverySize() {
        var size by mutableStateOf(TextSize.Largest)
        var font by mutableStateOf(PlumFont.Sans)
        compose.setContent { PlumTheme(PlumThemeSettings.Graphite.copy(textSize = size, font = font)) { Panel() } }
        val combinations =
            TextSize.entries.map { PlumFont.Sans to it } +
                listOf(PlumFont.Serif, PlumFont.Mono).flatMap { listOf(it to TextSize.Large, it to TextSize.Largest) }
        val failures = mutableListOf<String>()
        for ((eachFont, each) in combinations) {
            font = eachFont
            size = each
            compose.waitForIdle()
            val root = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
            val label = "accent_by_system at ${each.name} ${eachFont.name} on ${widthDp}x$heightDp"
            textProblems(root, FitAllowance(setOf(SettingsTags.OPTION_LIST, SettingsTags.SECTIONS, SettingsTags.COLOR_SCROLL), isLargest = each == TextSize.Largest)).mapTo(failures) { "$label: $it" }
            val focused = findTagged(root, SettingsTags.panelItem(ColorPages.bySystemIndex))
            if (focused == null) failures += "$label: the focused tile is not drawn" else notFullyShown(focused, root)?.let { failures += "$label: the focused tile $it" }
        }
        assertTrue(failures.size.toString() + " problems:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SettingsAccentBySystemFit768Test : SettingsAccentBySystemFitBase(768, 432)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w837dp-h471dp-land-xhdpi")
class SettingsAccentBySystemFit837Test : SettingsAccentBySystemFitBase(837, 471)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w667dp-h375dp-land-xhdpi")
class SettingsAccentBySystemFit667Test : SettingsAccentBySystemFitBase(667, 375)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class SettingsAccentBySystemFit576Test : SettingsAccentBySystemFitBase(576, 432)
