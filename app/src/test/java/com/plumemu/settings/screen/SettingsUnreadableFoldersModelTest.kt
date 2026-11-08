package com.plumemu.settings.screen

import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.setup.SetupFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Library › Folder could not be read, by gamepad and by touch: listed right under Rescan library only while a folder's last
 * scan could not list a sub-folder, gone when none; Scan again starts the rescan and puts the cursor on its row; A on a
 * folder skips that folder (once, even pressed twice) and it leaves the list; skipped or removed folders are not listed.
 */
internal class SettingsUnreadableFoldersModelTest : SettingsModelHarness() {
    private val roms = folder(1, "ROMs", listOf("psx"))
    private val games = folder(2, "Games", listOf("gba/hacks", "snes"))

    private fun libraryRows() = SettingsSection.Library.rowsIn(model.content)

    private fun openPanel(vararg folders: SetupFolder) {
        model.content = SettingsContent(folders = folders.toList())
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.UnreadableFolders)
        press(Action.Enter)
        assertEquals(PanelPage.UnreadableFolders, model.focus.page)
    }

    @Test
    fun theRowIsListedUnderRescanOnlyWhileAFolderHasUnreadablePaths() {
        model.content = SettingsContent(folders = listOf(folder(1, "ROMs", emptyList())))
        assertFalse(SettingsOption.UnreadableFolders in libraryRows())
        model.content = SettingsContent(folders = listOf(roms))
        val rows = libraryRows()
        assertEquals(rows.indexOf(SettingsOption.Rescan) + 1, rows.indexOf(SettingsOption.UnreadableFolders))
        model.content = SettingsContent(folders = listOf(roms.copy(unreadablePaths = emptyList())))
        assertFalse("a scan that read everything takes the row away", SettingsOption.UnreadableFolders in libraryRows())
    }

    @Test
    fun thePanelListsScanAgainThenEveryFolderOfEveryPickedFolder() {
        model.content = SettingsContent(folders = listOf(roms, games))
        assertEquals(
            listOf(
                UnreadablePanelItem.ScanAgain,
                UnreadablePanelItem.Skip(UnreadableFolder(1, "ROMs", "psx")),
                UnreadablePanelItem.Skip(UnreadableFolder(2, "Games", "gba/hacks")),
                UnreadablePanelItem.Skip(UnreadableFolder(2, "Games", "snes")),
            ),
            unreadablePanelItems(model.content),
        )
    }

    @Test
    fun skippedAndRemovedFoldersAreNotListed() {
        model.content =
            SettingsContent(
                folders = listOf(roms, games),
                skippedSubfolders = mapOf(2L to listOf("GBA")),
                removingFolderIds = setOf(1L),
            )
        assertEquals(listOf(UnreadableFolder(2, "Games", "snes")), unreadableFolders(model.content))
        // Still being saved: listed until stored, so a failed save is said on its panel.
        model.content = model.content.copy(skippingFolders = setOf(FolderKey(2, "snes")))
        assertEquals(listOf(UnreadableFolder(2, "Games", "snes")), unreadableFolders(model.content))
    }

    @Test
    fun scanAgainStartsTheRescanAndPutsTheCursorOnItsRow() {
        openPanel(roms)
        assertEquals(0, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(1, actions.rescans)
        assertNull(model.focus.page)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Rescan, model.focusedOption)
    }

    @Test
    fun aOnAFolderSkipsItOnceAndItLeaves() {
        openPanel(roms, games)
        move(Direction.Down)
        move(Direction.Down)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals("a second press while it is stored does nothing", listOf(Triple(2L, "gba/hacks", true)), actions.skips)
        actions.answer(true)
        model.content = model.content.copy(skippedSubfolders = mapOf(2L to listOf("gba/hacks")))
        assertEquals(PanelPage.UnreadableFolders, model.focus.page)
        assertEquals("the cursor stays inside the list, on the next folder", 2, model.focus.panelIndex)
        assertEquals(UnreadablePanelItem.Skip(UnreadableFolder(2, "Games", "snes")), unreadablePanelItems(model.content)[2])
    }

    @Test
    fun theLastFolderSkippedClosesThePanel() {
        openPanel(roms)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf(Triple(1L, "psx", true)), actions.skips)
        actions.answer(true)
        model.content = model.content.copy(skippedSubfolders = mapOf(1L to listOf("psx")), folders = listOf(roms.copy(unreadablePaths = listOf("psx"))))
        assertFalse(SettingsOption.UnreadableFolders in libraryRows())
        assertNull("the row left, so did its panel", model.focus.page)
    }

    @Test
    fun theHintSaysWhatAdoesOnEachItem() {
        openPanel(roms)
        assertEquals(R.string.settings_hint_choose, confirmHint(model))
        move(Direction.Down)
        assertEquals("A skips the folder at once", R.string.settings_hint_skip, confirmHint(model))
    }

    @Test
    fun aFailedSkipSaysSo() {
        openPanel(roms)
        move(Direction.Down)
        press(Action.Enter)
        actions.answer(false)
        assertEquals("said on this panel, where the person acted", SettingsOption.UnreadableFolders to SettingsNotice.SkipFailed, model.work.notice)
        assertEquals(PanelPage.UnreadableFolders, model.focus.page)
    }

    @Test
    fun touchTapsFocusThenActOnBothItems() {
        model.content = SettingsContent(folders = listOf(roms))
        openSection(SettingsSection.Library)
        val row = libraryRows().indexOf(SettingsOption.UnreadableFolders)
        model.onTapValue(row, actions.settings, wide)
        assertEquals(PanelPage.UnreadableFolders, model.focus.page)
        model.onTapPanelItem(1, actions.settings, wide)
        assertEquals(1, model.focus.panelIndex)
        assertTrue("the first tap only focuses", actions.skips.isEmpty())
        model.onTapPanelItem(1, actions.settings, wide)
        assertEquals(listOf(Triple(1L, "psx", true)), actions.skips)
        repeat(2) { model.onTapPanelItem(0, actions.settings, wide) }
        assertEquals(1, actions.rescans)
        assertEquals(SettingsOption.Rescan, model.focusedOption)
    }

    @Test
    fun onANarrowScreenScanAgainReturnsToTheListOnRescan() {
        model.content = SettingsContent(folders = listOf(roms))
        openSection(SettingsSection.Library)
        focusOption(SettingsOption.UnreadableFolders)
        press(Action.Enter, narrow)
        assertEquals(PanelPage.UnreadableFolders, model.focus.page)
        press(Action.Enter, narrow)
        assertEquals(1, actions.rescans)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertEquals(SettingsOption.Rescan, model.focusedOption)
    }

    private fun folder(
        id: Long,
        name: String,
        unreadable: List<String>,
    ) = SetupFolder(id, "content://tree/$id", displayName = name, gameCount = 3, systemCount = 1, isCounted = true, unreadablePaths = unreadable)
}
