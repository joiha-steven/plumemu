package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Appearance with the look preview in the right column (docs/design/rules.md › Settings), at 768x432 and 832x468 in both themes, at
 * the default size and the largest: the options whose preview shows (text size, text weight, fade, text colour), and those that
 * give way to their swatches (accent, background, font).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsPreviewScreenshotTest {
    private val sizes = listOf(ReferenceShots.Size.Wide, ReferenceShots.Size.Pocket6)

    private fun shots(
        name: String,
        option: SettingsOption,
        textSize: TextSize,
    ) = ReferenceShots.capture(name, textSize = textSize, sizes = sizes) { SettingsAt(panelShownAt(option)) }

    @Test fun textSize() = shots("settings_preview_text_size", SettingsOption.TextSize, TextSize.Small)

    @Test fun textSizeLargest() = shots("settings_preview_text_size", SettingsOption.TextSize, TextSize.Largest)

    @Test fun textColor() = shots("settings_preview_text_color", SettingsOption.TextColor, TextSize.Small)

    @Test fun accent() = shots("settings_preview_accent", SettingsOption.Accent, TextSize.Small)

    @Test fun accentLargest() = shots("settings_preview_accent", SettingsOption.Accent, TextSize.Largest)

    @Test fun background() = shots("settings_preview_background", SettingsOption.Background, TextSize.Small)

    @Test fun backgroundLargest() = shots("settings_preview_background", SettingsOption.Background, TextSize.Largest)
}
