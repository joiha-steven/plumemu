package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings' columns: three on a wide screen where they fit (768x432, 832x468), two below 16:9 and at the text sizes where
 * three do not fit (docs/design/rules.md › Settings). Appearance with a row whose right column shows swatches, one whose
 * right column shows a description, and Statistics (a page over the options and right columns).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsColumnsScreenshotTest {
    private val sizes = listOf(ReferenceShots.Size.Wide, ReferenceShots.Size.Pocket6, ReferenceShots.Size.Narrow, ReferenceShots.Size.FourThree)

    private fun shots(
        name: String,
        textSize: TextSize,
        focus: SettingsFocus,
    ) = ReferenceShots.capture(name, textSize = textSize, sizes = sizes) { SettingsAt(focus) }

    @Test
    fun swatchesPanel() = shots("settings_columns_swatches", TextSize.Small, optionsAt(SettingsOption.Background))

    @Test
    fun swatchesPanelLargest() = shots("settings_columns_swatches", TextSize.Largest, optionsAt(SettingsOption.Background))

    @Test
    fun descriptionPanel() = shots("settings_columns_description", TextSize.Small, optionsAt(SettingsOption.TextSize))

    @Test
    fun descriptionPanelLargest() = shots("settings_columns_description", TextSize.Largest, optionsAt(SettingsOption.TextSize))

    @Test
    fun statisticsPage() =
        shots("settings_columns_statistics", TextSize.Small, SettingsFocus(column = SettingsColumn.Options, section = SettingsSection.Statistics))
}
