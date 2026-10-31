package com.plumemu.home

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.TextSize
import com.plumemu.home.search.SearchState
import com.plumemu.setup.SetupAt
import com.plumemu.setup.SetupFakes
import com.plumemu.settings.screen.SettingsAt
import com.plumemu.settings.screen.SettingsFocus
import com.plumemu.settings.screen.SettingsSection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Wi-Fi glyph left of the battery in the top bar of the main screen and of Settings, with
 * Wi-Fi connected and not (the glyph is gone), at Medium and at the largest text size.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class WifiScreenshotTest {
    @Test
    fun homeWifiOn() = ReferenceShots.capture("home_wifi_on") { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true }) }

    @Test
    fun homeWifiOff() = ReferenceShots.capture("home_wifi_off") { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { false }) }

    @Test
    fun homeWifiOnLargest() =
        ReferenceShots.capture("home_wifi_on", textSize = TextSize.Largest) { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, wifi = { true }) }

    @Test
    fun settingsWifiOn() = ReferenceShots.capture("settings_wifi_on") { SettingsAt(SettingsFocus(section = SettingsSection.Appearance), wifi = true) }

    @Test
    fun settingsWifiOff() = ReferenceShots.capture("settings_wifi_off") { SettingsAt(SettingsFocus(section = SettingsSection.Appearance), wifi = false) }

    @Test
    fun settingsWifiOnLargest() =
        ReferenceShots.capture("settings_wifi_on", textSize = TextSize.Largest) { SettingsAt(SettingsFocus(section = SettingsSection.Appearance), wifi = true) }

    @Test
    fun searchWifiOn() =
        ReferenceShots.capture("search_wifi_on") { HomeAt(FakeHomeContent.board(), BoardSystemsFocused, search = SearchState().apply { open() }, wifi = { true }) }

    @Test
    fun setupWifiOn() = ReferenceShots.capture("setup_wifi_on") { SetupAt(SetupFakes.chooseFolder, wifi = true) }
}
