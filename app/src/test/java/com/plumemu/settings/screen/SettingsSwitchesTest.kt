package com.plumemu.settings.screen

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The switches of Library, Controls and Home screen write their own key, computed from
 * the stored value inside the write, by A and by a tap; Use as home screen asks Android.
 */
internal class SettingsSwitchesTest : SettingsModelHarness() {
    private val switches: Map<SettingsOption, (PlumSettings) -> Boolean> =
        mapOf(
            SettingsOption.ScanOnStartup to { s -> s.library.scanOnStartup },
            SettingsOption.HoldToScrollFaster to { s -> s.controls.holdToScrollFaster },
            SettingsOption.PushRightToPlay to { s -> s.controls.pushRightToPlay },
            SettingsOption.ShowButtonHints to { s -> s.controls.showButtonHints },
            SettingsOption.ShowApps to { s -> s.homeScreen.showApps },
            SettingsOption.ShowClock to { s -> s.homeScreen.showClock },
            SettingsOption.ShowBattery to { s -> s.homeScreen.showBattery },
            SettingsOption.SystemInfo to { s -> s.homeScreen.isSystemInfoShown },
        )

    @Test
    fun eachSwitchWritesOnlyItsOwnKey() {
        for ((option, read) in switches) {
            val before = actions.settings
            val section = SettingsSection.entries.first { option in it.options }
            val focus = SettingsFocus(column = SettingsColumn.Options, section = section, rememberedOption = mapOf(section to section.options.indexOf(option)))
            val model = SettingsModel(actions, focus)
            model.onAction(Action.Enter, actions.shown, wide)
            val after = actions.settings
            assertEquals("$option flips", !read(before), read(after))
            assertEquals("$option changes nothing else", option.toggled(before), after)
            assertEquals(read(after), option.switchValue(after))
        }
    }

    @Test
    fun twoQuickPressesCountTwiceFromTheStoredValue() {
        openSection(SettingsSection.Controls)
        focusOption(SettingsOption.ShowButtonHints)
        // Neither write has landed when the second press comes (the screen still shows "on").
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(true, actions.settings.controls.showButtonHints)
        press(Action.Enter)
        assertEquals(false, actions.settings.controls.showButtonHints)
    }

    @Test
    fun aTapFlipsASwitchAtOnce() {
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapOption(SettingsSection.HomeScreen.options.indexOf(SettingsOption.ShowClock), actions.settings, wide)
        assertEquals(false, actions.settings.homeScreen.showClock)
        model.onTapValue(SettingsSection.HomeScreen.options.indexOf(SettingsOption.ShowBattery), actions.settings, wide)
        assertEquals(false, actions.settings.homeScreen.showBattery)
        // System info is on until switched off, and a tap on its value flips it (touch).
        assertEquals(true, actions.settings.homeScreen.isSystemInfoShown)
        model.onTapValue(SettingsSection.HomeScreen.options.indexOf(SettingsOption.SystemInfo), actions.settings, wide)
        assertEquals(false, actions.settings.homeScreen.isSystemInfoShown)
    }

    @Test
    fun oneTapOnUseAsHomeScreenActsLikeASwitch() {
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapOption(SettingsSection.HomeScreen.options.indexOf(SettingsOption.ShowApps), actions.settings, wide)
        model.onTapOption(SettingsSection.HomeScreen.options.indexOf(SettingsOption.UseAsHome), actions.settings, wide)
        assertEquals("the first tap asks Android", 1, actions.homeOpened)
    }

    @Test
    fun useAsHomeScreenAsksAndroidOrSaysWhereToLook() {
        openSection(SettingsSection.HomeScreen)
        assertEquals(SettingsOption.UseAsHome, model.focusedOption)
        press(Action.Enter)
        assertEquals(1, actions.homeOpened)
        assertNull(model.work.notice)
        actions.canOpenHome = false
        model.onTapValue(0, actions.settings, wide)
        assertEquals(2, actions.homeOpened)
        assertEquals(SettingsOption.UseAsHome to SettingsNotice.HomeRoleUnavailable, model.work.notice)
        assertEquals("nothing is stored for it", PlumSettings().homeScreen, actions.settings.homeScreen)
    }
}
