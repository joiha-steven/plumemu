package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sections list scrolled to its last row (About) at Medium and Largest text, at the Pocket 6's size too: the row clears the hint bar (`SettingsSectionsBarGapTest`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsSectionsGapScreenshotTest {
    @Test
    fun sectionsAtTheEndMedium() =
        ReferenceShots.capture("settings_sections_end", sizes = ReferenceShots.Size.WithPocket6) {
            SettingsAt(SettingsFocus(section = SettingsSection.shownIn(SettingsContent()).last()))
        }

    @Test
    fun sectionsAtTheEnd() =
        ReferenceShots.capture("settings_sections_end", textSize = TextSize.Largest, sizes = ReferenceShots.Size.WithPocket6) {
            SettingsAt(SettingsFocus(section = SettingsSection.shownIn(SettingsContent()).last()))
        }
}
