package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings › Library with its last row (Clear Recent…) focused: the pill's shadow fades out under it instead of ending in a line above the hint keys. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsListEndsScreenshotTest {
    private val lastRow =
        SettingsFocus(
            column = SettingsColumn.Options,
            section = SettingsSection.Library,
            rememberedOption = mapOf(SettingsSection.Library to SettingsSection.Library.rowsIn(ShotContent.library).lastIndex),
        )

    @Test
    fun libraryLastRow() =
        ReferenceShots.capture("settings_library_last_row", sizes = listOf(ReferenceShots.Size.Pocket6, ReferenceShots.Size.Narrow)) {
            SettingsAt(lastRow, settingsForShot(), ShotContent.library)
        }
}
