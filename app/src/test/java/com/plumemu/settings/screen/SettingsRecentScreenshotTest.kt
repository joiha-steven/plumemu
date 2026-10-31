package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library › Recent size and Clear Recent…'s confirm step, at the reference sizes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsRecentScreenshotTest {
    private fun focusOn(
        option: SettingsOption,
        page: PanelPage? = null,
    ): SettingsFocus {
        val row = SettingsSection.Library.rowsIn(ShotContent.library).indexOf(option)
        return SettingsFocus(
            column = if (page == null) SettingsColumn.Options else SettingsColumn.Panel,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to row),
            page = page,
            panelIndex = SettingsModel.CONFIRM_CANCEL,
        )
    }

    @Test
    fun recentSize() = ReferenceShots.capture("settings_library_recent_size") { SettingsAt(focusOn(SettingsOption.RecentSize), settingsForShot(), ShotContent.library) }

    @Test
    fun clearRecentConfirm() =
        ReferenceShots.capture("settings_clear_recent_confirm") { SettingsAt(focusOn(SettingsOption.ClearRecent, PanelPage.Confirm), settingsForShot(), ShotContent.library) }
}
