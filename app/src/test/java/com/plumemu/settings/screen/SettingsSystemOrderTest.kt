package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SystemOrderSetting
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Library › Systems › System order, the panel's first item: Left and Right step it, A wraps, a tap steps it once focused, a
 * tap on its value at once. Picking a system up still turns the order Manual; the two never fight over the rows shown.
 */
internal class SettingsSystemOrderTest : SettingsModelHarness() {
    private val library =
        listOf(
            LibrarySystem("snes", "Super Nintendo", 120, "Nintendo", 1990),
            LibrarySystem("psx", "PlayStation", 80, "Sony", 1994),
            LibrarySystem("gba", "Game Boy Advance", 60, "Nintendo", 2001),
            LibrarySystem("megadrive", "Mega Drive", 40, "Sega", 1988),
        )

    private val stored: PlumSettings get() = actions.settings

    private fun shownIds(): List<String> = model.systems.rows(model.content, stored).map { it.id }

    @Before
    fun openTheLibrary() {
        model.systems.locale = Locale.ENGLISH
        model.content = SettingsContent(librarySystems = library)
    }

    private fun toSystemsRow() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Systems)
    }

    private fun openSystems() {
        toSystemsRow()
        press(Action.Enter)
        assertEquals("the panel opens on the first system", FIRST_SYSTEM_ITEM, model.focus.panelIndex)
        move(Direction.Up)
        assertEquals("System order is one Up away", SYSTEM_ORDER_ITEM, model.focus.panelIndex)
    }

    @Test
    fun theKeyThatOpensThePanelNeverChangesTheOrder() {
        toSystemsRow()
        move(Direction.Right)
        assertEquals(PanelPage.Systems, model.focus.page)
        move(Direction.Right) // pressed twice, or held
        press(Action.Move(Direction.Right, isRepeat = true))
        assertEquals("Right past the row steps nothing", FIRST_SYSTEM_ITEM, model.focus.panelIndex)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
    }

    @Test
    fun aQuickSecondAOnTheRowDoesNotStepTheOrder() {
        toSystemsRow()
        press(Action.Enter)
        assertEquals(FIRST_SYSTEM_ITEM, model.focus.panelIndex)
        press(Action.Enter)
        assertTrue("it lands on a system, which it picks up", model.systems.isCarrying)
        press(Action.Back)
        assertEquals(FIRST_SYSTEM_ITEM, model.focus.panelIndex)
    }

    @Test
    fun aHeldKeyOnSystemOrderStepsNothing() {
        openSystems()
        press(Action.Move(Direction.Right, isRepeat = true))
        press(Action.Move(Direction.Left, isRepeat = true))
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        move(Direction.Right)
        assertEquals("a press of its own still steps", SystemOrderSetting.entries[1], stored.library.systemOrder)
    }

    @Test
    fun itIsNoLongerARowOfLibraryAndSystemsFollowsSortGames() {
        val rows = SettingsSection.Library.rowsIn(model.content)
        assertFalse(SettingsOption.SystemOrder in rows)
        assertEquals(SettingsOption.Systems, rows[rows.indexOf(SettingsOption.SortGames) + 1])
        assertFalse("no systems, nothing to arrange", SettingsOption.Systems in SettingsSection.Library.rowsIn(SettingsContent()))
    }

    @Test
    fun leftAndRightStopAtTheEndsAndAWraps() {
        openSystems()
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        press(Action.Enter)
        assertEquals(SystemOrderSetting.Maker, stored.library.systemOrder)
        move(Direction.Right)
        assertEquals(SystemOrderSetting.Manual, stored.library.systemOrder)
        move(Direction.Right)
        assertEquals("right stops at the end", SystemOrderSetting.Manual, stored.library.systemOrder)
        press(Action.Enter)
        assertEquals("A wraps", SystemOrderSetting.Name, stored.library.systemOrder)
        move(Direction.Left)
        assertEquals("left stops at the start", SystemOrderSetting.Name, stored.library.systemOrder)
        assertEquals("stepping stays in the panel", PanelPage.Systems, model.focus.page)
    }

    @Test
    fun theRowsFollowTheOrderAtOnce() {
        openSystems()
        assertEquals(listOf("gba", "megadrive", "psx", "snes"), shownIds())
        move(Direction.Right)
        assertEquals(listOf("snes", "gba", "megadrive", "psx"), shownIds())
    }

    @Test
    fun aTapFocusesItAndTheNextOneSteps() {
        openSystems()
        move(Direction.Down)
        model.onTapSystemOrder(actions.shown, wide)
        assertEquals("a first tap only focuses", SYSTEM_ORDER_ITEM, model.focus.panelIndex)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        model.onTapSystemOrder(actions.shown, wide)
        assertEquals(SystemOrderSetting.Maker, stored.library.systemOrder)
    }

    @Test
    fun aTapOnTheValueStepsAtOnceFromAnySystemRow() {
        openSystems()
        move(Direction.Down)
        model.onTapSystemOrderValue(actions.shown, wide)
        assertEquals(SYSTEM_ORDER_ITEM, model.focus.panelIndex)
        assertEquals(SystemOrderSetting.Maker, stored.library.systemOrder)
    }

    @Test
    fun aTapOnThePreviewedPanelOnlyEntersIt() {
        toSystemsRow()
        model.onTapSystemOrder(actions.shown, wide)
        assertEquals(PanelPage.Systems, model.focus.page)
        assertEquals("the preview's tap changes nothing", SystemOrderSetting.Name, stored.library.systemOrder)
    }

    @Test
    fun onANarrowScreenItWorksTheSame() {
        toSystemsRow()
        press(Action.Enter, narrow)
        assertEquals(PanelPage.Systems, model.focus.page)
        move(Direction.Up, narrow)
        press(Action.Enter, narrow)
        assertEquals(SystemOrderSetting.Maker, stored.library.systemOrder)
    }

    @Test
    fun aUnderAToZOnASystemChangesNothingUntilItMoves() {
        openSystems()
        move(Direction.Down)
        move(Direction.Down) // megadrive
        press(Action.Enter)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        assertNull(model.work.notice)
        move(Direction.Up)
        press(Action.Enter)
        assertEquals(SystemOrderSetting.Manual, stored.library.systemOrder)
        assertEquals(listOf("megadrive", "gba", "psx", "snes"), stored.library.manualSystemOrder)
        assertEquals(SettingsOption.Systems to SettingsNotice.SystemOrderNowManual, model.work.notice)
    }

    @Test
    fun bAfterPickingUpKeepsAToZAndSaysNothing() {
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        press(Action.Back)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        assertNull(model.work.notice)
        assertEquals(listOf("gba", "megadrive", "psx", "snes"), shownIds())
    }

    @Test
    fun bAfterAMoveRestoresTheOrderAndTheMode() {
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        move(Direction.Down)
        press(Action.Back)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        assertNull(model.work.notice)
        assertEquals(listOf("gba", "megadrive", "psx", "snes"), shownIds())
    }

    @Test
    fun afterBAScanAddedSystemFollowsAToZ() {
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        press(Action.Back)
        model.content = SettingsContent(librarySystems = library + LibrarySystem("atari", "Atari 2600", 5, "Atari", 1977))
        assertEquals(listOf("atari", "gba", "megadrive", "psx", "snes"), shownIds())
    }

    @Test
    fun aDropWithNoMoveLeavesAToZ() {
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        assertNull(model.work.notice)
    }

    @Test
    fun aMoveThenADropTurnsManualAndSaysSo() {
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(SystemOrderSetting.Manual, stored.library.systemOrder)
        assertEquals(SettingsOption.Systems to SettingsNotice.SystemOrderNowManual, model.work.notice)
    }

    @Test
    fun aDragUnderMakerAndYearStillTurnsManual() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Maker))
        openSystems()
        model.onSystemDragStart(1, actions.shown, wide)
        model.onSystemDragStep(1)
        model.onSystemDragEnd()
        assertEquals(SystemOrderSetting.Manual, stored.library.systemOrder)
        assertEquals(SettingsOption.Systems to SettingsNotice.SystemOrderNowManual, model.work.notice)
    }

    @Test
    fun steppingAfterADropShowsTheNewOrderAndDropsTheLineThatNoLongerHolds() {
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        move(Direction.Down)
        press(Action.Enter) // gba below megadrive, Manual
        assertEquals(listOf("megadrive", "gba", "psx", "snes"), shownIds())
        move(Direction.Up)
        move(Direction.Up)
        assertEquals(SYSTEM_ORDER_ITEM, model.focus.panelIndex)
        move(Direction.Left)
        assertEquals(SystemOrderSetting.Maker, stored.library.systemOrder)
        assertEquals("the order held after the drop gives way", listOf("snes", "gba", "megadrive", "psx"), shownIds())
        assertNull("\"Order set to Manual\" is no longer true", model.work.notice)
    }

    @Test
    fun manualToAToZAndBackKeepsTheManualList() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("psx", "snes", "gba", "megadrive")))
        openSystems()
        move(Direction.Left)
        move(Direction.Left)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        assertEquals(listOf("gba", "megadrive", "psx", "snes"), shownIds())
        move(Direction.Right)
        move(Direction.Right)
        assertEquals(listOf("psx", "snes", "gba", "megadrive"), shownIds())
    }

    @Test
    fun aCarriedSystemNeverLandsOnTheOrderItem() {
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        assertTrue(model.systems.isCarrying)
        move(Direction.Up)
        assertEquals(FIRST_SYSTEM_ITEM, model.focus.panelIndex)
        press(Action.Back)
        assertFalse(model.systems.isCarrying)
        assertEquals(FIRST_SYSTEM_ITEM, model.focus.panelIndex)
    }

    @Test
    fun yDoesNothingOnTheOrderItemAndTheHintSaysChange() {
        openSystems()
        val writes = actions.writes
        press(Action.ToggleFavorite)
        assertEquals(writes, actions.writes)
        assertEquals(R.string.settings_hint_change, confirmHint(model))
        move(Direction.Down)
        assertEquals(R.string.settings_hint_move, confirmHint(model))
    }

    @Test
    fun theCursorStaysInsideWhenTheLastSystemsLeave() {
        openSystems()
        repeat(4) { move(Direction.Down) }
        assertEquals(library.size, model.focus.panelIndex)
        model.content = SettingsContent(librarySystems = library.take(1))
        assertEquals(FIRST_SYSTEM_ITEM, model.focus.panelIndex)
        move(Direction.Up)
        assertEquals(SYSTEM_ORDER_ITEM, model.focus.panelIndex)
    }
}
