package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The charging bolt before the battery percentage in the top bar of the main screen and of Settings, with Wi-Fi on,
 * at Medium and at the largest text size. Off the charger the bar is `home_wifi_on` / `settings_wifi_on`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ChargingScreenshotTest {
    @Test
    fun homeCharging() = ReferenceShots.capture("home_charging") { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true }, charging = { true }) }

    @Test
    fun homeChargingLargest() =
        ReferenceShots.capture("home_charging", textSize = TextSize.Largest) {
            HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true }, charging = { true })
        }

    @Test
    fun settingsCharging() =
        ReferenceShots.capture("settings_charging") { SettingsAt(SettingsFocus(section = SettingsSection.Appearance), wifi = true, charging = true) }
}
