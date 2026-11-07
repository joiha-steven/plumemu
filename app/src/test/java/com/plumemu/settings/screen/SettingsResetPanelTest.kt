package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Advanced › Reset…: two resets in a list, each asking first with the cursor on Cancel, by keys and by touch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
internal class SettingsResetPanelTest : SettingsModelHarness() {
    private fun openList() {
        model.content = SettingsContent()
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Reset)
        press(Action.Enter)
        assertEquals(PanelPage.Reset, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
    }

    private fun assertAsks(page: PanelPage) {
        assertEquals(page, model.focus.page)
        assertEquals("Cancel has the focus", SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
    }

    @Test
    fun eachResetAsksOnCancelAndBAndCancelGoBackOnItsItem() {
        openList()
        press(Action.Enter)
        assertAsks(PanelPage.ResetAllConfirm)
        assertEquals(SettingsOption.ResetAll, model.panelOption)
        press(Action.Enter)
        assertEquals(PanelPage.Reset, model.focus.page)
        assertEquals(ResetPanelItem.All.ordinal, model.focus.panelIndex)
        move(Direction.Down)
        press(Action.Enter)
        assertAsks(PanelPage.ResetLibraryConfirm)
        assertEquals(SettingsOption.ResetLibrary, model.panelOption)
        move(Direction.Right)
        press(Action.Back)
        assertEquals(PanelPage.Reset, model.focus.page)
        assertEquals(ResetPanelItem.Library.ordinal, model.focus.panelIndex)
        assertEquals(0, actions.resetsAll)
        assertEquals(0, actions.resetsLibrary)
    }

    @Test
    fun threeQuickAsResetNothing() {
        openList()
        repeat(3) { press(Action.Enter) }
        assertEquals(0, actions.resetsAll)
    }

    @Test
    fun theResetRunsOnlyFromItsOwnConfirmAndTheListSaysItRan() {
        openList()
        move(Direction.Down)
        press(Action.Enter)
        move(Direction.Right)
        press(Action.Enter)
        assertEquals(1, actions.resetsLibrary)
        assertEquals(0, actions.resetsAll)
        assertEquals(PanelPage.Reset, model.focus.page)
        assertEquals(ResetPanelItem.Library.ordinal, model.focus.panelIndex)
        assertEquals(SettingsOption.ResetLibrary, model.doneOption)
        press(Action.Enter)
        assertNull("a new ask drops the old word", model.doneOption)
        assertAsks(PanelPage.ResetLibraryConfirm)
    }

    @Test
    fun byTouchTheResetTakesAFocusingTapAndAPress() {
        model.content = SettingsContent()
        openSection(SettingsSection.Advanced)
        focusOption(SettingsOption.Reset)
        val settings = actions.shown
        model.onTapPanelItem(ResetPanelItem.Library.ordinal, settings, wide)
        assertEquals(PanelPage.Reset, model.focus.page)
        model.onTapPanelItem(ResetPanelItem.Library.ordinal, settings, wide)
        assertAsks(PanelPage.ResetLibraryConfirm)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, settings, wide)
        assertEquals("the first tap on Reset only focuses it", 0, actions.resetsLibrary)
        model.onTapPanelItem(SettingsModel.CONFIRM_RESET, settings, wide)
        assertEquals(1, actions.resetsLibrary)
    }

    @Test
    fun theRowsStayReachableWhenTheLibraryCannotBeRead() {
        // Nothing of the library arrives: the section still lists them.
        val rows = SettingsSection.Advanced.rowsIn(SettingsContent())
        assertEquals(true, SettingsOption.Reset in rows)
        assertEquals(true, SettingsOption.BackupAndRestore in rows)
        assertEquals(ResetPanelItem.entries.size, PanelGrid.of(PanelPage.Reset, wide, SettingsContent(), SettingsFocus(), actions.shown)?.count)
    }

    @Test
    fun downAndUpWalkAConfirmsTwoButtonsBecauseTheyMayStack() {
        openSection(SettingsSection.Library)
        repeat(SettingsSection.Library.rowsIn(model.content).size) {
            if (model.focusedOption != SettingsOption.ClearRecent) move(Direction.Down)
        }
        press(Action.Enter)
        assertEquals(PanelPage.Confirm, model.focus.page)
        assertEquals(SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
        move(Direction.Down)
        assertEquals(SettingsModel.CONFIRM_CANCEL + 1, model.focus.panelIndex)
        move(Direction.Up)
        assertEquals(SettingsModel.CONFIRM_CANCEL, model.focus.panelIndex)
    }
}
