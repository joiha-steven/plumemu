package com.plumemu.settings.screen

import com.plumemu.coredata.settings.ColorSetting
import com.plumemu.coredata.settings.TextColorSetting
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CustomPalette
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

    /** Wide: Background focused with its panel beside. Narrow: its panel opened as the further stop. */
    @Test
    fun appearanceWithAnOptionFocused() =
        ReferenceShots.capture("settings_appearance", sizes = ReferenceShots.Size.entries) {
            val isWide = PlumTheme.layout.widthClass == WidthClass.Wide
            val settings = settingsForShot()
            // The panel opens on the current colour (Graphite or Paper).
            val (page, index) = ColorPages.openingFor(SettingsOption.Background, settings)
            SettingsAt(if (isWide) optionsAt(SettingsOption.Background) else panelAt(SettingsOption.Background, page, index), settings)
        }

    /** Text weight focused, showing the default ‹ Medium ›, with its explanation beside (wide). */
    @Test
    fun appearanceTextWeight() = ReferenceShots.capture("settings_appearance_text_weight") { SettingsAt(optionsAt(SettingsOption.TextWeight), settingsForShot()) }

    /** Left edge focused, showing the default ‹ Game counts ›, with its explanation beside (wide). */
    @Test
    fun appearanceLeftEdge() = ReferenceShots.capture("settings_appearance_left_edge") { SettingsAt(optionsAt(SettingsOption.LeftEdge), settingsForShot()) }

    @Test
    fun customColour() =
        ReferenceShots.capture("settings_custom_colour") {
            // A dark blue with light text, a pale blue with dark text: stored as the person's own colour.
            val base = settingsForShot()
            val index = if (base.appearance.textColor == TextColorSetting.Dark) CUSTOM_LIGHT else CUSTOM_DARK
            val settings = base.copy(appearance = base.appearance.copy(background = ColorSetting.Custom(backgroundGrid[index].value)))
            SettingsAt(panelAt(SettingsOption.Background, PanelPage.BackgroundCustom, index), settings)
        }

    @Test
    fun advanced() = ReferenceShots.capture("settings_advanced", sizes = ReferenceShots.Size.entries) { SettingsAt(optionsAt(SettingsOption.UsageStatistics)) }

    /** The two-line Confirm with B row focused: its text clear of the ring (audit B5). */
    @Test
    fun advancedConfirmWithB() = ReferenceShots.capture("settings_advanced_confirm_b", sizes = ReferenceShots.Size.entries) { SettingsAt(optionsAt(SettingsOption.ConfirmBottom)) }

    @Test
    fun aboutLicenses() = ReferenceShots.capture("settings_about_licenses") { SettingsAt(panelAt(SettingsOption.Licenses, PanelPage.Licenses, 0)) }

    private companion object {
        // A dark and a pale blue, found by nearest colour in the curated grid so a reshuffle of it
        // cannot pair a text colour with the wrong background.

        /** A darker blue, which carries light text. */
        val CUSTOM_DARK = ColorPages.indexOfNearest(backgroundGrid, 0x0F2B7A)

        /** A pale blue, which carries dark text. */
        val CUSTOM_LIGHT = ColorPages.indexOfNearest(backgroundGrid, 0xD3EFFE)
    }
}
