package com.plumemu.settings.screen

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

/** Settings › Library › System order and Systems, by gamepad and touch. */
internal class SettingsSystemsTest : SettingsModelHarness() {
    private val library =
        listOf(
            LibrarySystem("snes", "Super Nintendo", 248, "Nintendo", 1990),
            LibrarySystem("psx", "PlayStation", 120, "Sony", 1994),
            LibrarySystem("gba", "Game Boy Advance", 64, "Nintendo", 2001),
            LibrarySystem("megadrive", "Mega Drive", 40, "Sega", 1988),
        )

    private val stored: PlumSettings get() = actions.settings

    /** The panel's rows as the model arranges them now, after the store applied every write. */
    private fun shownIds(): List<String> = model.systems.rows(model.content, stored).map { it.id }

    /** The system row under the cursor; the panel's first item is System order. */
    private val cursor: Int get() = model.focus.panelIndex - FIRST_SYSTEM_ITEM

    private fun focusedId(): String = model.systems.rows(model.content, actions.shown).map { it.id }[cursor]

    @Before
    fun openTheLibrary() {
        model.systems.locale = Locale.ENGLISH
        model.content = SettingsContent(librarySystems = library)
    }

    private fun openSystems() {
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Systems)
        press(Action.Enter)
        assertEquals(PanelPage.Systems, model.focus.page)
        assertEquals(SettingsColumn.Panel, model.focus.column)
    }

    @Test
    fun thePanelListsEverySystemInTheOrderInForce() {
        assertEquals(listOf("gba", "megadrive", "psx", "snes"), shownIds())
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Maker))
        assertEquals(listOf("snes", "gba", "megadrive", "psx"), shownIds())
    }

    @Test
    fun aPicksUpUpAndDownCarryAndADropsAsTheManualOrder() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive")))
        openSystems()
        move(Direction.Down) // psx
        press(Action.Enter)
        assertTrue(model.systems.isCarrying)
        assertEquals("psx", model.systems.carry?.id)
        move(Direction.Down)
        move(Direction.Down)
        assertEquals("the cursor rides with it", 3, cursor)
        move(Direction.Down)
        assertEquals("at the bottom it stays", 3, cursor)
        assertEquals("nothing stored while carried", listOf("snes", "psx", "gba", "megadrive"), actions.shown.library.manualSystemOrder)
        press(Action.Enter)
        assertFalse(model.systems.isCarrying)
        assertEquals(listOf("snes", "gba", "megadrive", "psx"), stored.library.manualSystemOrder)
        assertEquals(SystemOrderSetting.Manual, stored.library.systemOrder)
        assertEquals("psx", focusedId())
    }

    @Test
    fun aHeldDirectionCarriesOnePlacePerRepeat() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive")))
        openSystems()
        press(Action.Enter)
        // The router turns a held Down into repeated moves; each repeat is one place.
        repeat(10) { move(Direction.Down) }
        press(Action.Enter)
        assertEquals(listOf("psx", "gba", "megadrive", "snes"), stored.library.manualSystemOrder)
    }

    @Test
    fun l1AndR1TakeTheCarriedSystemToTheTopAndBottom() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive")))
        openSystems()
        move(Direction.Down)
        move(Direction.Down) // gba
        press(Action.Enter)
        press(Action.PreviousSystem)
        assertEquals(0, cursor)
        press(Action.NextSystem)
        assertEquals(3, cursor)
        press(Action.PreviousSystem)
        press(Action.Enter)
        assertEquals(listOf("gba", "snes", "psx", "megadrive"), stored.library.manualSystemOrder)
    }

    @Test
    fun bPutsTheCarriedSystemBackAndStoresNothing() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive")))
        openSystems()
        move(Direction.Down)
        press(Action.Enter)
        move(Direction.Down)
        move(Direction.Down)
        val writes = actions.writes
        press(Action.Back)
        assertFalse(model.systems.isCarrying)
        assertEquals("back where it was picked up", 1, cursor)
        assertEquals(PanelPage.Systems, model.focus.page)
        assertEquals("B cancels; it does not leave the panel", SettingsColumn.Panel, model.focus.column)
        assertEquals(writes, actions.writes)
        assertEquals(listOf("snes", "psx", "gba", "megadrive"), shownIds())
        press(Action.Back)
        assertEquals("a second B leaves the panel", SettingsColumn.Options, model.focus.column)
    }

    @Test
    fun movingUnderAToZSwitchesToManualFromTheOrderOnScreenAndSaysSoOnDrop() {
        openSystems()
        assertEquals(listOf("gba", "megadrive", "psx", "snes"), shownIds())
        move(Direction.Down)
        move(Direction.Down) // psx
        press(Action.Enter)
        assertEquals("picking up stores nothing", SystemOrderSetting.Name, stored.library.systemOrder)
        assertEquals(emptyList<String>(), stored.library.manualSystemOrder)
        assertNull(model.work.notice)
        press(Action.PreviousSystem)
        press(Action.Enter)
        assertEquals(SystemOrderSetting.Manual, stored.library.systemOrder)
        assertEquals(listOf("psx", "gba", "megadrive", "snes"), stored.library.manualSystemOrder)
        assertEquals("the line stays while the panel is open", SettingsNotice.SystemOrderNowManual, model.work.notice?.second)
    }

    @Test
    fun movingUnderMakerAndYearStartsFromThatOrder() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Maker))
        openSystems()
        press(Action.Enter)
        press(Action.NextSystem)
        press(Action.Enter)
        assertEquals(listOf("gba", "megadrive", "psx", "snes"), stored.library.manualSystemOrder)
        assertEquals(SystemOrderSetting.Manual, stored.library.systemOrder)
    }

    @Test
    fun yHidesAndShowsTheFocusedSystemAndTheLastShownOneStays() {
        openSystems()
        press(Action.ToggleFavorite) // gba
        assertEquals(setOf("gba"), stored.library.hiddenSystems)
        assertEquals("hidden systems stay in the list, in place", listOf("gba", "megadrive", "psx", "snes"), shownIds())
        press(Action.ToggleFavorite)
        assertEquals(emptySet<String>(), stored.library.hiddenSystems)
        repeat(3) {
            press(Action.ToggleFavorite)
            move(Direction.Down)
        }
        assertEquals(setOf("gba", "megadrive", "psx"), stored.library.hiddenSystems)
        assertEquals("snes", focusedId())
        val writes = actions.writes
        press(Action.ToggleFavorite)
        assertEquals("the last one shown is refused", setOf("gba", "megadrive", "psx"), stored.library.hiddenSystems)
        assertEquals(writes, actions.writes)
        assertEquals(SettingsOption.Systems to SettingsNotice.LastSystemShown, model.work.notice)
        move(Direction.Up)
        press(Action.ToggleFavorite)
        assertEquals(setOf("gba", "megadrive"), stored.library.hiddenSystems)
        assertNull("showing one makes the refusal stale", model.work.notice)
    }

    @Test
    fun twoQuickYPressesNeverHideTheLastTwoShown() {
        model.content = SettingsContent(librarySystems = library.take(2))
        openSystems()
        // Both presses decide on the same settings shown (the store has not answered yet).
        press(Action.ToggleFavorite)
        move(Direction.Down)
        model.onAction(Action.ToggleFavorite, actions.shown, wide)
        assertEquals(1, stored.library.hiddenSystems.size)
    }

    @Test
    fun yDoesNothingWhileCarrying() {
        openSystems()
        press(Action.Enter)
        val writes = actions.writes
        press(Action.ToggleFavorite)
        assertEquals(writes, actions.writes)
        assertEquals(emptySet<String>(), stored.library.hiddenSystems)
    }

    @Test
    fun aScanThatAddsASystemWhileCarryingKeepsTheCursorOnTheCarriedOne() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive")))
        openSystems()
        move(Direction.Down)
        move(Direction.Down) // gba
        press(Action.Enter)
        model.content = SettingsContent(librarySystems = listOf(LibrarySystem("nes", "NES", 3, "Nintendo", 1983)) + library.filter { it.id != "snes" })
        assertEquals("gba", model.systems.carry?.id)
        assertEquals("snes left: gba is now second", 1, cursor)
        move(Direction.Down)
        assertEquals("a press carries it past the next row on screen", 2, cursor)
        assertEquals("gba", focusedId())
        move(Direction.Up)
        assertEquals("gba", focusedId())
        press(Action.Enter)
        assertEquals("the new system joins at the end; the gone one keeps its slot", listOf("snes", "psx", "gba", "megadrive", "nes"), stored.library.manualSystemOrder)
    }

    @Test
    fun aSystemRemovedWhileCarryingLeavesTheMovesOnTheRowsOnScreen() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive")))
        openSystems()
        move(Direction.Down)
        move(Direction.Down) // gba
        press(Action.Enter)
        model.content = SettingsContent(librarySystems = library.filter { it.id != "snes" })
        assertEquals(listOf("psx", "gba", "megadrive"), shownIds())
        assertEquals(1, cursor)
        assertEquals("gba", focusedId())

        move(Direction.Down)
        assertEquals("Down moves it below Mega Drive", listOf("psx", "megadrive", "gba"), shownIds())
        assertEquals(2, cursor)
        assertEquals("the highlight stays on the carried system", "gba", focusedId())
        move(Direction.Up)
        move(Direction.Up)
        assertEquals(listOf("gba", "psx", "megadrive"), shownIds())
        assertEquals(0, cursor)
        assertEquals("gba", focusedId())

        // The same by touch: one row's travel is one place on screen.
        model.onSystemDragStep(+1)
        assertEquals(listOf("psx", "gba", "megadrive"), shownIds())
        assertEquals("gba", focusedId())
        press(Action.Enter)
        assertEquals("the gone system keeps its slot", listOf("snes", "psx", "gba", "megadrive"), stored.library.manualSystemOrder)
    }

    @Test
    fun theCarriedSystemLeavingWhileCarriedEndsTheCarry() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive")))
        openSystems()
        move(Direction.Down)
        move(Direction.Down) // gba
        press(Action.Enter)
        model.content = SettingsContent(librarySystems = library.filter { it.id != "gba" })
        assertFalse("nothing is lifted any more, so the panel is not left carrying", model.systems.isCarrying)
        assertEquals(listOf("snes", "psx", "megadrive"), shownIds())
        assertEquals("megadrive", focusedId())
        assertEquals("nothing was stored", listOf("snes", "psx", "gba", "megadrive"), stored.library.manualSystemOrder)
    }

    @Test
    fun touchFocusesWithATapShowsWithATapOnHiddenAndDragsByTheHandle() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("snes", "psx", "gba", "megadrive"), hiddenSystems = setOf("gba")))
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.Systems)
        model.onTapSystem(2, actions.shown, wide)
        assertEquals("a tap in the previewed panel enters it on that row", PanelPage.Systems, model.focus.page)
        assertEquals(2, cursor)
        assertFalse("a tap never lifts a row", model.systems.isCarrying)
        model.onTapSystemHidden(2, actions.shown, wide)
        assertEquals(emptySet<String>(), stored.library.hiddenSystems)

        model.onSystemDragStart(3, actions.shown, wide)
        assertTrue(model.systems.isCarrying)
        model.onSystemDragStep(-1)
        model.onSystemDragStep(-1)
        model.onSystemDragEnd()
        assertEquals(listOf("snes", "megadrive", "psx", "gba"), stored.library.manualSystemOrder)

        model.onSystemDragStart(0, actions.shown, wide)
        model.onSystemDragStep(+1)
        model.onSystemDragCancel()
        assertEquals("a cancelled drag stores nothing", listOf("snes", "megadrive", "psx", "gba"), stored.library.manualSystemOrder)
        assertEquals(0, cursor)
    }

    @Test
    fun resetAllSettingsGoesBackToAToZWithNothingHidden() {
        actions.settings = stored.copy(library = stored.library.copy(systemOrder = SystemOrderSetting.Manual, manualSystemOrder = listOf("psx"), hiddenSystems = setOf("gba")))
        actions.resetAllSettings()
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        assertEquals(emptySet<String>(), stored.library.hiddenSystems)
        assertEquals(emptyList<String>(), stored.library.manualSystemOrder)
    }

    @Test
    fun leavingThePanelWhileCarryingPutsTheSystemBack() {
        openSystems()
        press(Action.Enter)
        move(Direction.Down)
        model.onTapSection(SettingsSection.Appearance)
        assertFalse(model.systems.isCarrying)
        assertEquals(SystemOrderSetting.Name, stored.library.systemOrder)
        assertEquals(emptyList<String>(), stored.library.manualSystemOrder)
    }
}
