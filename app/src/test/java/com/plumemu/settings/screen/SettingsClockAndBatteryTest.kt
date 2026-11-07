package com.plumemu.settings.screen

import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import org.junit.Assert.assertEquals
import org.junit.Test

/** One switch over two stored keys: on while either is, a flip writes both to the new value, and nothing changes before a flip. */
internal class SettingsClockAndBatteryTest : SettingsModelHarness() {
    private fun stored(
        clock: Boolean,
        battery: Boolean,
    ) = PlumSettings().let { it.copy(homeScreen = it.homeScreen.copy(showClock = clock, showBattery = battery)) }

    private fun flipFrom(
        clock: Boolean,
        battery: Boolean,
        expected: Boolean,
    ) {
        actions.settings = stored(clock, battery)
        assertEquals("the row before a flip", !expected, SettingsOption.ClockAndBattery.switchValue(actions.settings))
        assertEquals("nothing written before a flip", stored(clock, battery), actions.settings)
        openSection(SettingsSection.HomeScreen)
        focusOption(SettingsOption.ClockAndBattery)
        press(Action.Enter)
        assertEquals(expected, actions.settings.homeScreen.showClock)
        assertEquals(expected, actions.settings.homeScreen.showBattery)
        assertEquals(expected, SettingsOption.ClockAndBattery.switchValue(actions.settings))
    }

    @Test fun bothOnGoesBothOff() = flipFrom(clock = true, battery = true, expected = false)

    @Test fun onlyTheClockOnReadsOnAndGoesBothOff() = flipFrom(clock = true, battery = false, expected = false)

    @Test fun onlyTheBatteryOnReadsOnAndGoesBothOff() = flipFrom(clock = false, battery = true, expected = false)

    @Test fun bothOffGoesBothOn() = flipFrom(clock = false, battery = false, expected = true)

    @Test
    fun twoQuickPressesCountFromTheStoredValue() {
        actions.settings = stored(clock = true, battery = false)
        openSection(SettingsSection.HomeScreen)
        focusOption(SettingsOption.ClockAndBattery)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(true, actions.settings.homeScreen.showClock)
        assertEquals(true, actions.settings.homeScreen.showBattery)
    }
}
