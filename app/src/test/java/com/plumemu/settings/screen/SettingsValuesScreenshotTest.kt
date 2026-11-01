package com.plumemu.settings.screen

import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The values at the right of Settings rows: a focused and a resting cycler (chevrons only on the
 * focused one), a sub-page row (one chevron), the signed-in status with and without a name, a very long
 * name and a very long choice, and a switch. 768x432 and 576x432, Graphite and Paper.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SettingsValuesScreenshotTest {
    @Test
    fun values() = ReferenceShots.capture("settings_values") { ValueRows() }
}
