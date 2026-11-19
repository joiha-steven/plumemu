package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Library › Emulators: the row opens a list of the systems with games, a system opens its page of emulators, a
 * choice is stored and goes back to the list on that system, B walks back the same way. By gamepad and by touch.
 */
internal class SettingsEmulatorsPanelTest : SettingsModelHarness() {
    private val library = ShotContent.library
    private val psx = library.systems.first { it.systemId == "psx" }
    private val psxAt = library.systems.indexOf(psx)

    /** The same library with PlayStation's SwanStation chosen, so its current choice is not the first item. */
    private val chosenSwan = library.copy(systems = library.systems.map { if (it.systemId == "psx") it.copy(chosen = it.installed[1]) else it })

    private fun onEmulatorsRow(content: SettingsContent = library) {
        model.content = content
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Emulators)
    }

    private fun openList(content: SettingsContent = library) {
        onEmulatorsRow(content)
        press(Action.Enter)
    }

    private fun openPsxPage(content: SettingsContent = library) {
        openList(content)
        repeat(psxAt) { move(Direction.Down) }
        press(Action.Enter)
    }

    private fun assertOnList(system: String) {
        assertEquals(PanelPage.EmulatorSystems, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertEquals(system, model.content.systems[model.focus.panelIndex].systemId)
    }

    @Test
    fun theRowIsListedWithGamesAndItsValueCountsTheSystems() {
        onEmulatorsRow()
        assertEquals(SettingsOption.Emulators, model.focusedOption)
        assertEquals(7, model.content.systems.size)
    }

    @Test
    fun aOpensTheListOnTheFirstSystem() {
        openList()
        assertOnList(library.systems.first().systemId)
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun rightOpensTheListToo() {
        onEmulatorsRow()
        move(Direction.Right)
        assertOnList(library.systems.first().systemId)
    }

    @Test
    fun downToPlayStationThenAOpensItsPageOnItsCurrentChoice() {
        openPsxPage(chosenSwan)
        assertEquals(PanelPage.Emulators, model.focus.page)
        assertEquals("psx", model.focus.systemId)
        assertEquals("SwanStation is item 2: Automatic, DuckStation, SwanStation", 2, model.focus.panelIndex)
    }

    @Test
    fun aSystemWithNoChoiceOpensOnAutomatic() {
        openPsxPage()
        assertEquals(PanelPage.Emulators, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun bFromThePageReturnsToTheListOnPlayStationAndBAgainLandsOnTheRow() {
        openPsxPage(chosenSwan)
        press(Action.Back)
        assertOnList("psx")
        assertNull("the list is not a system's page", model.focus.systemId)
        press(Action.Back)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(SettingsOption.Emulators, model.focusedOption)
    }

    @Test
    fun aOnAChoiceChoosesItAndReturnsToTheListOnThatSystem() {
        openPsxPage()
        move(Direction.Down) // DuckStation
        press(Action.Enter)
        assertEquals(listOf("psx" to "duckstation"), actions.chosen)
        assertOnList("psx")
    }

    @Test
    fun aOnAutomaticForgetsTheChoice() {
        openPsxPage(chosenSwan)
        move(Direction.Up)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals(listOf("psx" to null), actions.chosen)
        assertOnList("psx")
    }

    @Test
    fun aSystemLeavingWhileItsPageIsOpenLandsOnTheList() {
        openPsxPage(chosenSwan)
        model.content = chosenSwan.copy(systems = chosenSwan.systems.filter { it.systemId != "psx" })
        assertEquals(PanelPage.EmulatorSystems, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
        assertNull(model.focus.systemId)
        assertTrue(model.focus.panelIndex in model.content.systems.indices)
    }

    @Test
    fun aSystemAddedBeforePlayStationKeepsPlayStationUnderTheCursorInTheList() {
        openList()
        repeat(psxAt) { move(Direction.Down) }
        val added = SystemEmulators("atari", "Atari 2600", automatic = null, chosen = null, installed = emptyList())
        model.content = library.copy(systems = listOf(added) + library.systems)
        assertOnList("psx")
        assertEquals(psxAt + 1, model.focus.panelIndex)
    }

    @Test
    fun aTapOnASystemFocusesItAndASecondTapOpensItsPage() {
        openList()
        model.onTapPanelItem(psxAt, actions.settings, wide)
        assertOnList("psx")
        model.onTapPanelItem(psxAt, actions.settings, wide)
        assertEquals(PanelPage.Emulators, model.focus.page)
        assertEquals("psx", model.focus.systemId)
    }

    @Test
    fun aTapOnAChoiceFocusesItAndASecondTapChoosesItAndGoesBackToTheList() {
        openPsxPage()
        model.onTapPanelItem(2, actions.settings, wide) // SwanStation
        assertEquals(PanelPage.Emulators, model.focus.page)
        assertEquals("a first tap only focuses", emptyList<Pair<String, String?>>(), actions.chosen)
        model.onTapPanelItem(2, actions.settings, wide)
        assertEquals(listOf("psx" to "retroarch-swanstation"), actions.chosen)
        assertOnList("psx")
    }
}
