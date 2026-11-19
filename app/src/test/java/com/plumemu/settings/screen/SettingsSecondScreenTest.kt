package com.plumemu.settings.screen

import com.plumemu.coredata.settings.AppsScreen
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
 * Settings › Home screen's second screen rows (Screens while one is found):
 * the switch listed only while there is a second screen, In game only while the switch is on; the
 * switch shows the screen kind's default until the person flips it, then stores the other value; In
 * game steps through its two values.
 */
internal class SettingsSecondScreenTest : SettingsModelHarness() {
    private val builtIn = SecondScreen(4, "Screen-2", ScreenKind.BuiltIn)
    private val unsure = SecondScreen(2, "Overlay #1", ScreenKind.Unsure)
    private val section = SettingsSection.HomeScreen

    /** Home screen's own rows, before the second screen's. */
    private val own = listOf(SettingsOption.UseAsHome, SettingsOption.ShowApps)

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
        val both = listOf(SettingsOption.SecondScreen, SettingsOption.SecondScreenInGame, SettingsOption.SecondScreenAppsOn)
        assertEquals("no screen: only Home screen's own rows", own, section.rowsIn(SettingsContent().withSecondScreen(null, undecided)))
        assertEquals("built in, undecided: on", own + both, section.rowsIn(SettingsContent().withSecondScreen(builtIn, undecided)))
        assertEquals(
            "unsure, undecided: off, so no In game and no Open apps on",
            own + SettingsOption.SecondScreen,
            section.rowsIn(SettingsContent().withSecondScreen(unsure, undecided)),
        )
        assertEquals(own + both, section.rowsIn(SettingsContent().withSecondScreen(unsure, SecondScreenSettings(enabled = true))))
        assertEquals(
            own + SettingsOption.SecondScreen,
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
        model.onTapOption(section.rowsIn(model.content).indexOf(SettingsOption.SecondScreen), actions.settings, wide)
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
        model.onTapValue(section.rowsIn(model.content).indexOf(SettingsOption.SecondScreenInGame), actions.settings, wide)
        assertEquals(InGameSetting.Black, actions.settings.secondScreen.inGame)
    }

    @Test
    fun openAppsOnCyclesByAAndStepsByLeftAndRight() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenAppsOn)
        assertEquals("default is the top screen", AppsScreen.Top, actions.settings.secondScreen.appsOn)
        press(Action.Enter)
        assertEquals(AppsScreen.Bottom, actions.settings.secondScreen.appsOn)
        press(Action.Enter)
        assertEquals("wraps", AppsScreen.Top, actions.settings.secondScreen.appsOn)
        move(Direction.Right)
        assertEquals(AppsScreen.Bottom, actions.settings.secondScreen.appsOn)
        move(Direction.Right)
        assertEquals("stops at the end", AppsScreen.Bottom, actions.settings.secondScreen.appsOn)
        move(Direction.Left)
        assertEquals(AppsScreen.Top, actions.settings.secondScreen.appsOn)
        move(Direction.Left)
        assertEquals("Left does not wrap", AppsScreen.Top, actions.settings.secondScreen.appsOn)
        assertEquals("During a game is not touched", InGameSetting.Clock, actions.settings.secondScreen.inGame)
    }

    @Test
    fun aTapOnOpenAppsOnsValueStepsIt() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenAppsOn)
        model.onTapValue(section.rowsIn(model.content).indexOf(SettingsOption.SecondScreenAppsOn), actions.settings, wide)
        assertEquals(AppsScreen.Bottom, actions.settings.secondScreen.appsOn)
    }

    @Test
    fun openAppsOnFollowsDuringAGameAndGoesWithTheSwitch() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenInGame)
        move(Direction.Down)
        assertEquals(SettingsOption.SecondScreenAppsOn, model.focusedOption)
        withScreen(unsure)
        assertFalse("off: gone", SettingsOption.SecondScreenAppsOn in section.rowsIn(model.content))
        withScreen(null)
        assertFalse("no screen: gone", SettingsOption.SecondScreenAppsOn in section.rowsIn(model.content))
    }

    @Test
    fun theScreenGoingAwayWhileItsRowIsFocusedKeepsTheFocusOnARowOfHomeScreen() {
        withScreen(builtIn)
        focusSecondScreenRow(SettingsOption.SecondScreenInGame)
        withScreen(null)
        assertEquals(SettingsSection.HomeScreen, model.focus.section)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertTrue(model.focusedOption in own)
    }

    /** From Home screen's rows, R1 goes to Controls whether or not a screen is found. */
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
    fun r1FromHomeScreenWithoutAScreenOrGamesGoesToControls() = assertEquals(SettingsSection.Controls, r1FromHomeScreen(null))

    @Test
    fun r1FromHomeScreenWithAScreenGoesToControlsToo() = assertEquals(SettingsSection.Controls, r1FromHomeScreen(builtIn))

    @Test
    fun downFromHomeScreenWithoutAScreenOrGamesGoesToControls() = assertEquals(SettingsSection.Controls, downFromHomeScreen(null))

    @Test
    fun downFromHomeScreenWithAScreenGoesToControlsToo() = assertEquals(SettingsSection.Controls, downFromHomeScreen(builtIn))
}
