package com.plumemu.settings.screen

import com.plumemu.coredata.settings.InGameSetting
import com.plumemu.coredata.settings.SecondScreenSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.secondscreen.SecondScreen
import com.plumemu.secondscreen.isSecondScreenOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Home screen › Second screen and In game:
 * the switch listed only while there is a second screen, In game only while the switch is on; the
 * switch shows the screen kind's default until the person flips it, then stores the other value; In
 * game steps through its two values.
 */
internal class SettingsSecondScreenTest : SettingsModelHarness() {
    private val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
    private val unsure = SecondScreen(2, "Overlay #1", ScreenKind.Unsure)
    private val homeOptions = SettingsSection.HomeScreen.options

    private var screen: SecondScreen? = null

    /** The content the graph builds for [found] and the stored settings (reading them lands the writes). */
    private fun withScreen(found: SecondScreen?) {
        screen = found
        syncContent()
    }

    private fun syncContent() {
        model.content = SettingsContent().withSecondScreen(screen, actions.settings.secondScreen)
    }

    private fun focusSecondScreenRow(option: SettingsOption) {
        openSection(SettingsSection.HomeScreen)
        focusOption(option)
    }

    @Test
    fun theRowsAreListedOnlyWhileThereIsASecondScreenAndInGameOnlyWhileItIsOn() {
        val undecided = SecondScreenSettings()
        val both = homeOptions + listOf(SettingsOption.SecondScreen, SettingsOption.SecondScreenInGame)
        assertEquals(homeOptions, SettingsSection.HomeScreen.rowsIn(SettingsContent().withSecondScreen(null, undecided)))
        assertEquals("built in, undecided: on", both, SettingsSection.HomeScreen.rowsIn(SettingsContent().withSecondScreen(builtIn, undecided)))
        assertEquals(
            "unsure, undecided: off, so no In game",
            homeOptions + SettingsOption.SecondScreen,
            SettingsSection.HomeScreen.rowsIn(SettingsContent().withSecondScreen(unsure, undecided)),
        )
        assertEquals(both, SettingsSection.HomeScreen.rowsIn(SettingsContent().withSecondScreen(unsure, SecondScreenSettings(enabled = true))))
        assertEquals(
            homeOptions + SettingsOption.SecondScreen,
            SettingsSection.HomeScreen.rowsIn(SettingsContent().withSecondScreen(builtIn, SecondScreenSettings(enabled = false))),
        )
        assertTrue(
            "no other section lists them",
            SettingsSection.entries.filter { it != SettingsSection.HomeScreen }.none { SettingsOption.SecondScreen in it.rowsIn(SettingsContent(secondScreen = builtIn)) },
        )
    }

    @Test
    fun aBuiltInScreenShowsOnAndAStoresOffThenOn() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreen)
        assertTrue("undecided, built in: on", isSecondScreenOn(actions.settings.secondScreen, builtIn))
        press(Action.Enter)
        assertEquals(false, actions.settings.secondScreen.enabled)
        press(Action.Enter)
        assertEquals(true, actions.settings.secondScreen.enabled)
    }

    @Test
    fun anUnsureScreenShowsOffAndAStoresOn() {
        withScreen(unsure)
        focusSecondScreenRow(SettingsOption.SecondScreen)
        assertFalse(isSecondScreenOn(actions.settings.secondScreen, unsure))
        press(Action.Enter)
        assertEquals(true, actions.settings.secondScreen.enabled)
    }

    @Test
    fun twoQuickPressesCountTwiceFromTheStoredValue() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreen)
        // Neither write has landed when the second press comes.
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(true, actions.settings.secondScreen.enabled)
    }

    @Test
    fun aTapOnTheSwitchFlipsItAtOnceAndChangesNothingElse() {
        withScreen(builtIn)
        val before = actions.settings
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapSection(SettingsSection.HomeScreen)
        model.onTapOption(homeOptions.size, actions.settings, wide)
        val after = actions.settings
        assertEquals(false, after.secondScreen.enabled)
        assertEquals("only the second screen changed", before.copy(secondScreen = after.secondScreen), after)
    }

    @Test
    fun inGameCyclesByAAndStepsByLeftAndRight() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenInGame)
        assertEquals(InGameSetting.Clock, actions.settings.secondScreen.inGame)
        press(Action.Enter)
        assertEquals(InGameSetting.Black, actions.settings.secondScreen.inGame)
        press(Action.Enter)
        assertEquals("wraps", InGameSetting.Clock, actions.settings.secondScreen.inGame)
        move(Direction.Right)
        assertEquals(InGameSetting.Black, actions.settings.secondScreen.inGame)
        move(Direction.Right)
        assertEquals("stops at the end", InGameSetting.Black, actions.settings.secondScreen.inGame)
        move(Direction.Left)
        assertEquals(InGameSetting.Clock, actions.settings.secondScreen.inGame)
        assertNull("the switch stays undecided", actions.settings.secondScreen.enabled)
    }

    @Test
    fun turningTheSwitchOffHidesInGameAndOnBringsItBackWithTheFocusKeptOnTheSwitch() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreen)
        assertTrue(SettingsOption.SecondScreenInGame in SettingsSection.HomeScreen.rowsIn(model.content))

        press(Action.Enter) // off
        syncContent()
        assertFalse("off: In game is gone", SettingsOption.SecondScreenInGame in SettingsSection.HomeScreen.rowsIn(model.content))
        assertEquals(SettingsOption.SecondScreen, model.focusedOption)

        press(Action.Enter) // on
        syncContent()
        assertTrue("on: In game is back", SettingsOption.SecondScreenInGame in SettingsSection.HomeScreen.rowsIn(model.content))
        assertEquals(SettingsOption.SecondScreen, model.focusedOption)
        move(Direction.Down)
        assertEquals(SettingsOption.SecondScreenInGame, model.focusedOption)
    }

    @Test
    fun aTapOnInGamesValueStepsIt() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenInGame)
        model.onTapValue(homeOptions.size + 1, actions.settings, wide)
        assertEquals(InGameSetting.Black, actions.settings.secondScreen.inGame)
    }

    @Test
    fun theScreenGoingAwayWhileItsRowIsFocusedLeavesTheFocusOnARowThatExists() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenInGame)
        withScreen(null)
        assertEquals(SettingsOption.SystemInfo, model.focusedOption)
        // Accepted behaviour of rows that vanish (Settings keeps the row index in range), not a
        // requirement: A then acts on the row the focus fell to.
        press(Action.Enter)
        assertEquals("the row now focused acts", false, actions.settings.homeScreen.isSystemInfoShown)
    }
}
