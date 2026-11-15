package com.plumemu.settings.screen

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The switches of Library, Appearance, Controls and Home screen write their own key, computed from
 * the stored value inside the write, by A and by a tap; Use as home screen asks Android.
 */
internal class SettingsSwitchesTest : SettingsModelHarness() {
    private val switches: Map<SettingsOption, (PlumSettings) -> Boolean> =
        mapOf(
            SettingsOption.PushRightToPlay to { s -> s.controls.pushRightToPlay },
            SettingsOption.ShowApps to { s -> s.homeScreen.showApps },
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
        focusOption(SettingsOption.PushRightToPlay)
        // Neither write has landed when the second press comes (the screen still shows "off").
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(false, actions.settings.controls.pushRightToPlay)
        press(Action.Enter)
        assertEquals(true, actions.settings.controls.pushRightToPlay)
    }

    @Test
    fun aTapFlipsASwitchAtOnce() {
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapOption(SettingsSection.HomeScreen.options.indexOf(SettingsOption.ShowApps), actions.settings, wide)
        assertEquals(false, actions.settings.homeScreen.showApps)
        model.onTapValue(SettingsSection.HomeScreen.options.indexOf(SettingsOption.ShowApps), actions.settings, wide)
        assertEquals(true, actions.settings.homeScreen.showApps)
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
    fun useAsHomeScreenSaysSoWhenAndroidClosedAndPlumemuIsStillNotHome() {
        openSection(SettingsSection.HomeScreen)
        actions.notHomeAfterAsking = true
        press(Action.Enter)
        assertEquals(SettingsOption.UseAsHome to SettingsNotice.HomeRoleNotYet, model.work.notice)
        // A later press that worked clears the line.
        actions.notHomeAfterAsking = false
        model.onTapValue(0, actions.settings, wide)
        assertNull(model.work.notice)
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
