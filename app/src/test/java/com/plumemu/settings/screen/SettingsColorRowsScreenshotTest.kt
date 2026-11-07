package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.WidthClass
import com.plumemu.platforms.PlatformDataVersion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Colors' panel with its Background and Text color rows, and About › Version with the platform data's lines. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsColorRowsScreenshotTest {
    @Composable
    private fun onTile() = SettingsAt(panelAt(SettingsOption.Colors, PanelPage.ColorSets, 0))

    @Test
    fun tiles() = ReferenceShots.capture("settings_color_rows_tiles") { onTile() }

    @Test
    fun tilesLargest() = ReferenceShots.capture("settings_color_rows_tiles", textSize = TextSize.Largest) { onTile() }

    @Test
    fun backgroundRow() = ReferenceShots.capture("settings_color_rows_background") { SettingsAt(optionsAt(SettingsOption.Background)) }

    @Test
    fun textRow() = ReferenceShots.capture("settings_color_rows_text") { SettingsAt(optionsAt(SettingsOption.TextColor)) }

    @Test
    fun textRowLargest() = ReferenceShots.capture("settings_color_rows_text", textSize = TextSize.Largest) { SettingsAt(optionsAt(SettingsOption.TextColor)) }

    @Test
    fun textRowOwnColours() =
        ReferenceShots.capture("settings_color_rows_own") {
            val shot = settingsForShot()
            val own = shot.copy(appearance = shot.appearance.copy(background = com.plumemu.coredata.settings.ColorSetting.Custom(0x2C1111), textColor = com.plumemu.coredata.settings.TextColorSetting.Custom(0xF5EFD6)))
            SettingsAt(optionsAt(SettingsOption.TextColor), own)
        }

    @Test
    fun versionLargest() =
        ReferenceShots.capture("settings_about_platform_data", textSize = TextSize.Largest) {
            val focus = if (PlumTheme.layout.widthClass == WidthClass.Wide) optionsAt(SettingsOption.Version) else panelAt(SettingsOption.Version, PanelPage.ReadOnly, 0)
            SettingsAt(focus, content = SettingsContent(platformData = PlatformDataVersion(6, "0".repeat(64), "2026-11-01")))
        }
}
