package com.plumemu.home

import com.plumemu.coredata.settings.TextSizeSetting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.toComposeColor
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.ViewedSystem
import com.plumemu.settings.systemColors
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Accent color › By system on the home screen: the focus dot, the current system and the
 * rings are the colour of the system in view. Super Nintendo (purple) and Nintendo 64 (green), with
 * the home screen drawn through [SettingsTheme] as the app does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeAccentBySystemScreenshotTest {
    @Test
    fun superNintendo() = shot("home_accent_by_system_snes", "snes", FakeHomeContent.BOARD_SNES_INDEX)

    @Test
    fun nintendo64() = shot("home_accent_by_system_n64", "n64", FakeHomeContent.BOARD_SNES_INDEX + N64_AFTER_SNES)

    private fun shot(
        name: String,
        system: String,
        index: Int,
    ) = ReferenceShots.capture(name) {
        BySystemTheme(system) { HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, index))) }
    }

    /** The preset the shot is taken in (Paper has a light background), with the accent By system and [system] in view. */
    @Composable
    private fun BySystemTheme(
        system: String,
        content: @Composable () -> Unit,
    ) {
        val isPaper = PlumTheme.colors.bg.toArgb() == BackgroundPreset.Paper.color.toComposeColor().toArgb()
        val settings =
            remember(isPaper) {
                PlumSettings(
                    appearance =
                        AppearanceSettings(
                            textSize = TextSizeSetting.Medium,
                            background = ColorSetting.Preset(if (isPaper) "Paper" else "Graphite"),
                            textColor = if (isPaper) TextColorSetting.Dark else TextColorSetting.Light,
                            accent = ColorSetting.BySystem(ColorSetting.Preset("Red")),
                        ),
                )
            }
        val viewed = remember(system) { ViewedSystem(MutableStateFlow(system), { shippedCatalog.systemColors() }) }
        SettingsTheme(settings, viewed, content)
    }

    private companion object {
        const val N64_AFTER_SNES = 7
    }
}
