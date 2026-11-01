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
 * Settings › Second screen and In game:
 * the switch listed only while there is a second screen, In game only while the switch is on; the
 * switch shows the screen kind's default until the person flips it, then stores the other value; In
 * game steps through its two values.
 */
internal class SettingsSecondScreenTest : SettingsModelHarness() {
    private val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
    private val unsure = SecondScreen(2, "Overlay #1", ScreenKind.Unsure)
    private val section = SettingsSection.SecondScreen

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
        openSection(section)
        focusOption(option)
    }

    @Test
    fun theRowsAreListedOnlyWhileThereIsASecondScreenAndInGameOnlyWhileItIsOn() {
        val undecided = SecondScreenSettings()
        val both = listOf(SettingsOption.SecondScreen, SettingsOption.SecondScreenInGame)
        assertEquals(emptyList<SettingsOption>(), section.rowsIn(SettingsContent().withSecondScreen(null, undecided)))
        assertFalse("no section without a screen", section in SettingsSection.shownIn(SettingsContent().withSecondScreen(null, undecided)))
        assertTrue(section in SettingsSection.shownIn(SettingsContent().withSecondScreen(unsure, undecided)))
        assertEquals("built in, undecided: on", both, section.rowsIn(SettingsContent().withSecondScreen(builtIn, undecided)))
        assertEquals(
            "unsure, undecided: off, so no In game",
            listOf(SettingsOption.SecondScreen),
            section.rowsIn(SettingsContent().withSecondScreen(unsure, undecided)),
        )
        assertEquals(both, section.rowsIn(SettingsContent().withSecondScreen(unsure, SecondScreenSettings(enabled = true))))
        assertEquals(
            listOf(SettingsOption.SecondScreen),
            section.rowsIn(SettingsContent().withSecondScreen(builtIn, SecondScreenSettings(enabled = false))),
        )
        assertTrue(
            "no other section lists them",
            SettingsSection.entries.filter { it != section }.none { SettingsOption.SecondScreen in it.rowsIn(SettingsContent(secondScreen = builtIn)) },
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
        model.onTapSection(section)
        model.onTapSection(section)
        model.onTapOption(0, actions.settings, wide)
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
        assertTrue(SettingsOption.SecondScreenInGame in section.rowsIn(model.content))

        press(Action.Enter) // off
        syncContent()
        assertFalse("off: In game is gone", SettingsOption.SecondScreenInGame in section.rowsIn(model.content))
        assertEquals(SettingsOption.SecondScreen, model.focusedOption)

        press(Action.Enter) // on
        syncContent()
        assertTrue("on: In game is back", SettingsOption.SecondScreenInGame in section.rowsIn(model.content))
        assertEquals(SettingsOption.SecondScreen, model.focusedOption)
        move(Direction.Down)
        assertEquals(SettingsOption.SecondScreenInGame, model.focusedOption)
    }

    @Test
    fun aTapOnInGamesValueStepsIt() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenInGame)
        model.onTapValue(1, actions.settings, wide)
        assertEquals(InGameSetting.Black, actions.settings.secondScreen.inGame)
    }

    @Test
    fun theScreenGoingAwayWhileItsRowIsFocusedTakesTheFocusToTheSectionAbove() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenInGame)
        withScreen(null)
        assertEquals(SettingsSection.HomeScreen, model.focus.section)
        assertEquals(SettingsColumn.Sections, model.focus.column)
        val before = actions.settings
        press(Action.Enter)
        assertEquals("A opens Home screen's rows and changes nothing", before, actions.settings)
        assertEquals(SettingsColumn.Options, model.focus.column)
    }

    /** From Home screen's rows, R1 goes to the next section shown: Second screen only while a screen is found. */
    private fun r1FromHomeScreen(found: SecondScreen?): SettingsSection {
        withScreen(found)
        openSection(SettingsSection.HomeScreen)
        press(Action.NextSystem)
        assertEquals(SettingsColumn.Options, model.focus.column)
        return model.focus.section
    }

    /** The same with Down in the sections column. */
    private fun downFromHomeScreen(found: SecondScreen?): SettingsSection {
        withScreen(found)
        openSection(SettingsSection.HomeScreen)
        press(Action.Back)
        assertEquals(SettingsColumn.Sections, model.focus.column)
        move(Direction.Down)
        return model.focus.section
    }

    @Test
    fun r1FromHomeScreenWithoutAScreenGoesToEmulators() = assertEquals(SettingsSection.Emulators, r1FromHomeScreen(null))

    @Test
    fun r1FromHomeScreenWithAScreenGoesToSecondScreen() = assertEquals(SettingsSection.SecondScreen, r1FromHomeScreen(builtIn))

    @Test
    fun downFromHomeScreenWithoutAScreenGoesToEmulators() = assertEquals(SettingsSection.Emulators, downFromHomeScreen(null))

    @Test
    fun downFromHomeScreenWithAScreenGoesToSecondScreen() = assertEquals(SettingsSection.SecondScreen, downFromHomeScreen(builtIn))
}
