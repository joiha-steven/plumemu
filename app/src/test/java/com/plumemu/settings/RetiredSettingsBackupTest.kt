package com.plumemu.settings

import com.plumemu.coredata.backup.SettingsJson
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.RecentSizeSetting
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A retired option's stored value still goes through a backup both ways, and its fixed behaviour applies after a restore. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class RetiredSettingsBackupTest {
    @Test
    fun aBackupWithTheRetiredValuesOffRestoresThemAndTheBehaviourStaysOn() {
        val written =
            PlumSettings(
                appearance = AppearanceSettings(edgeFade = false),
                controls = ControlsSettings(holdToScrollFaster = false),
                library = LibrarySettings(scanOnStartup = false, recentSize = RecentSizeSetting.Ten),
            )
        val restored = SettingsJson.decode(SettingsJson.encode(written).toString()).settings
        assertFalse("still read back", restored.appearance.edgeFade)
        assertFalse("still read back", restored.controls.holdToScrollFaster)
        assertFalse("still read back", restored.library.scanOnStartup)
        assertEquals("still read back", RecentSizeSetting.Ten, restored.library.recentSize)
        assertTrue(restored.toThemeSettings().edgeFadeOn)
        assertTrue(restored.controls.toInputSettings().holdToScrollFaster)
    }
}
