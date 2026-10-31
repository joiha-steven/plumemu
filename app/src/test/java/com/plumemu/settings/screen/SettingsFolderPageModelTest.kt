package com.plumemu.settings.screen

import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.setup.SetupFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Settings › ROM folders › a folder (Skipped sub-folders), by gamepad and by touch: the
 * folder's page, Skip a sub-folder… (reading, listed, unreadable), A on a skipped one reads it again,
 * a failure is said, a second press while one is stored does nothing, and a folder removed meanwhile
 * goes back to the list.
 */
internal class SettingsFolderPageModelTest : SettingsModelHarness() {
    private val internal = folder(1, "content://com.android.externalstorage.documents/tree/primary%3AROMs")
    private val sd = folder(2, "content://com.android.externalstorage.documents/tree/1234-ABCD%3AGames")

    /** ROM folders open, A on the SD card's folder: its page, which skips [skipped]. */
    private fun openSdPage(skipped: List<String> = emptyList()) {
        model.content = SettingsContent(folders = listOf(internal, sd), skippedSubfolders = mapOf(sd.id to skipped))
        openSection(SettingsSection.Library)
        press(Action.Enter)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(PanelPage.Folder, model.focus.page)
        assertEquals(sd.id, model.focus.folderId)
    }

    @Test
    fun theFolderPageListsSkipThenTheSkippedThenRemove() {
        openSdPage(listOf("Hacks", "gba/_unsorted"))
        assertEquals(
            listOf(FolderPageItem.SkipOne, FolderPageItem.Skipped("Hacks"), FolderPageItem.Skipped("gba/_unsorted"), FolderPageItem.Remove),
            folderPageItems(model.content, model.focus.folderId),
        )
        assertEquals("another folder's page skips nothing", listOf(FolderPageItem.SkipOne, FolderPageItem.Remove), folderPageItems(model.content, internal.id))
    }

    @Test
    fun skipASubfolderReadsTheFolderThenASkipsTheOnePressed() {
        openSdPage()
        press(Action.Enter)
        assertEquals(PanelPage.SkipSubfolder, model.focus.page)
        assertEquals("the folder pressed is read", listOf(sd.id), actions.listings.map { it.first })
        assertEquals(SubfolderPick(sd.id, SubfolderState.Loading), model.work.subfolders)
        press(Action.Enter)
        assertEquals("nothing to pick while reading", emptyList<Triple<Long, String, Boolean>>(), actions.skips)
        actions.answerListing(listOf("Hacks", "Translations"))
        assertEquals(SubfolderState.Listed(listOf("Hacks", "Translations")), model.work.subfolders?.state)
        move(Direction.Down)
        press(Action.Enter)
        assertEquals(listOf(Triple(sd.id, "Translations", true)), actions.skips)
        assertEquals("back on the folder's page, on Skip a sub-folder…", PanelPage.Folder, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
        assertNull("the listing is forgotten", model.work.subfolders)
        actions.answer(true)
        assertNull(model.work.notice)
    }

    @Test
    fun aSubfolderAlreadySkippedIsNotOfferedAgainInAnyCase() {
        openSdPage(listOf("hacks"))
        press(Action.Enter)
        actions.answerListing(listOf("Hacks", "Translations"))
        assertEquals(listOf("Translations"), pickableSubfolders(model.work.subfolders, model.content))
        press(Action.Enter)
        assertEquals(listOf(Triple(sd.id, "Translations", true)), actions.skips)
    }

    @Test
    fun anUnreadableFolderSaysSoAndOffersNothing() {
        openSdPage()
        press(Action.Enter)
        actions.answerListing(null)
        assertEquals(SubfolderState.Unreadable, model.work.subfolders?.state)
        assertEquals(emptyList<String>(), pickableSubfolders(model.work.subfolders, model.content))
        press(Action.Enter)
        assertEquals(emptyList<Triple<Long, String, Boolean>>(), actions.skips)
        press(Action.Back)
        assertEquals(PanelPage.Folder, model.focus.page)
    }

    @Test
    fun aListingThatArrivesAfterThePageClosedIsDropped() {
        openSdPage()
        press(Action.Enter)
        press(Action.Back)
        actions.answerListing(listOf("Hacks"))
        assertNull(model.work.subfolders)
    }

    @Test
    fun aOnASkippedOneReadsItAgainOnceEvenWhenPressedTwice() {
        openSdPage(listOf("Hacks"))
        move(Direction.Down)
        press(Action.Enter)
        press(Action.Enter)
        assertEquals("a second press while it is stored does nothing", listOf(Triple(sd.id, "Hacks", false)), actions.skips)
        assertEquals(PanelPage.Folder, model.focus.page)
        actions.answer(true)
        model.content = model.content.copy(skippedSubfolders = emptyMap())
        assertEquals("the cursor stays on the page, clamped to what is left", PanelPage.Folder, model.focus.page)
    }

    @Test
    fun aFailedSkipSaysSo() {
        openSdPage(listOf("Hacks"))
        move(Direction.Down)
        press(Action.Enter)
        actions.answer(false)
        assertEquals(SettingsOption.RomFolders to SettingsNotice.SkipFailed, model.work.notice)
    }

    @Test
    fun touchTapsFocusThenAct() {
        openSdPage(listOf("Hacks"))
        model.onTapPanelItem(1, actions.settings, wide)
        assertEquals(1, model.focus.panelIndex)
        assertEquals(emptyList<Triple<Long, String, Boolean>>(), actions.skips)
        model.onTapPanelItem(1, actions.settings, wide)
        assertEquals(listOf(Triple(sd.id, "Hacks", false)), actions.skips)
        repeat(2) { model.onTapPanelItem(0, actions.settings, wide) }
        assertEquals(PanelPage.SkipSubfolder, model.focus.page)
        actions.answerListing(listOf("Translations"))
        repeat(2) { model.onTapPanelItem(0, actions.settings, wide) }
        assertEquals(Triple(sd.id, "Translations", true), actions.skips.last())
    }

    @Test
    fun backWalksOutPageByPage() {
        openSdPage()
        press(Action.Enter)
        assertEquals(PanelPage.SkipSubfolder, model.focus.page)
        press(Action.Back)
        assertEquals(PanelPage.Folder, model.focus.page)
        press(Action.Back)
        assertEquals(PanelPage.Folders, model.focus.page)
        assertEquals("back on the same folder", 1, model.focus.panelIndex)
    }

    @Test
    fun aFolderRemovedMeanwhileGoesBackToTheList() {
        openSdPage()
        press(Action.Enter)
        model.content = model.content.copy(folders = listOf(internal))
        assertEquals(PanelPage.Folders, model.focus.page)
    }

    private fun folder(
        id: Long,
        treeUri: String,
    ) = SetupFolder(id, treeUri, displayName = "", gameCount = 3, systemCount = 1, isCounted = true)
}
