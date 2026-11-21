package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.WidthClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings against the design mockups (sections and Appearance, your own colour, narrow), plus
 * what the mockups do not draw: Advanced as it is in the alpha and the licences list.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsScreenshotTest {
    @Test
    fun sections() = ReferenceShots.capture("settings_sections", sizes = ReferenceShots.Size.entries) { SettingsAt(SettingsFocus(section = SettingsSection.Appearance)) }

    /** At Largest the sections do not all fit: the column scrolls and fades its cut row. */
    @Test
    fun sectionsLargest() =
        ReferenceShots.capture("settings_sections", textSize = com.plumemu.coreui.theme.TextSize.Largest) {
            SettingsAt(SettingsFocus(section = SettingsSection.Controls))
        }

    /** Wide: Background focused with its panel beside. Narrow: its panel opened as the further stop. */
    @Test
    fun appearanceWithAnOptionFocused() =
        ReferenceShots.capture("settings_appearance", sizes = ReferenceShots.Size.entries) {
            val settings = settingsForShot()
            // The page opens on the current colour (Graphite or Paper).
            SettingsAt(colorPageAt(SettingsOption.Background, settings), settings)
        }

    @Test
    fun customColour() =
        ReferenceShots.capture("settings_custom_colour") {
            // A dark blue with light text, a pale blue with dark text: stored as the person's own colour.
            val base = settingsForShot()
            val rgb = if (base.appearance.textColor == TextColorSetting.Dark) 0xD3EFFE else 0x0F2B7A
            val settings = base.copy(appearance = base.appearance.copy(background = ColorSetting.Custom(rgb)))
            // An own colour opens the page on its nearest shade.
            SettingsAt(colorPageAt(SettingsOption.Background, settings), settings)
        }

    @Test
    fun advanced() = ReferenceShots.capture("settings_advanced", sizes = ReferenceShots.Size.entries) { SettingsAt(optionsAt(SettingsOption.UsageStatistics)) }

    /** The two-line Confirm with B row focused: its text clear of the ring (audit B5). */
    @Test
    fun advancedConfirmWithB() = ReferenceShots.capture("settings_advanced_confirm_b", sizes = ReferenceShots.Size.entries) { SettingsAt(optionsAt(SettingsOption.ConfirmBottom)) }

    @Test
    fun aboutLicenses() = ReferenceShots.capture("settings_about_licenses") { SettingsAt(panelAt(SettingsOption.Licenses, PanelPage.Licenses, 0)) }
}
