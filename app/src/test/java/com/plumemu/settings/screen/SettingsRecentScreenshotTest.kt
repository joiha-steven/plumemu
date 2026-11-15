package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library › Clear Recent…'s confirm step, at the reference sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsRecentScreenshotTest {
    @Test
    fun clearRecentConfirm() =
        ReferenceShots.capture("settings_clear_recent_confirm") { SettingsAt(panelAt(SettingsOption.ClearRecent, PanelPage.ResetRecentConfirm, SettingsModel.CONFIRM_CANCEL), settingsForShot(), ShotContent.library) }

    /** At Largest the two buttons do not fit a line at 768: the second goes under the first, whole. */
    @Test
    fun clearRecentConfirmLargest() =
        ReferenceShots.capture("settings_clear_recent_confirm", textSize = TextSize.Largest) { SettingsAt(panelAt(SettingsOption.ClearRecent, PanelPage.ResetRecentConfirm, SettingsModel.CONFIRM_CANCEL), settingsForShot(), ShotContent.library) }
}
