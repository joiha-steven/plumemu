package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.testing.ReferenceShots.Size
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings with a section focused fills the page: the Pocket 6 at 1920x1080 (853x480 dp), 768x432 and 576x432. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsFillsScreenshotTest {
    @Test
    fun sectionFocused() =
        ReferenceShots.capture("settings_fills_library", sizes = listOf(Size.Pocket6Full, Size.Wide, Size.Narrow)) {
            SettingsAt(SettingsFocus(column = SettingsColumn.Sections, section = SettingsSection.Library), content = ShotContent.library)
        }
}
