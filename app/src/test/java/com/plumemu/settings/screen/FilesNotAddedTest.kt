package com.plumemu.settings.screen

import com.plumemu.coredata.db.LeftOutFileEntity
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings › Library › Files not added: the stored rows grouped by reason (unknown folders as one
 * line each, a "more" line past the cap), the empty page, the row's count, and A on a folder line
 * going to that folder's row.
 */
internal class FilesNotAddedTest : SettingsModelHarness() {
    private val xbox = SettingsFolderRow(FolderKey(1, "Xbox 360"), 4, FolderAnswer.Unanswered)
    private val halo = SettingsFolderRow(FolderKey(1, "Halo"), 0, FolderAnswer.OfSystem("x360", "Xbox 360"))

    private fun row(
        path: String?,
        reason: String,
        system: String? = null,
        directory: String = path?.substringBeforeLast('/', "") ?: "",
        folder: Long = 1,
        more: Int = 0,
    ) = LeftOutFileEntity(folderId = folder, directoryPath = directory, relativePath = path, reason = reason, systemId = system, more = more)

    private val rows =
        listOf(
            row("psx/tool.exe", "unmatched", "psx"),
            row("Stuff/data.xyz", "unmatched"),
            row("snes/readme.txt", "junk", "snes"),
            row("notes.txt", "junk"),
            row("psx/scph1001.bin", "bios", "psx"),
            row("psx/Game (USA).srm", "save", "psx"),
            row("Mystery.iso", "no_system"),
            row("Xbox 360/Halo 3.iso", "no_system"),
            row("Xbox 360/Gears/Gears.iso", "no_system"),
            row(null, "no_system", directory = "Xbox 360/Gears", more = 2),
            row("Halo/Halo 2.iso", "no_system"),
            row(null, "junk", directory = "snes", more = 340),
            row("Old/thing.zzz", "gone_reason"),
        )

    @Test
    fun groupsInPageOrderWithUnknownFoldersFirstAndOneMoreLine() {
        val items = notAddedItems(rows, listOf(xbox, halo))
        assertEquals(
            listOf(
                NotAddedItem.Heading(NotAddedGroup.NoSystem, null),
                NotAddedItem.Folder(xbox.key, 4),
                NotAddedItem.File(1, "Halo", "Halo 2.iso"),
                NotAddedItem.File(1, "", "Mystery.iso"),
                NotAddedItem.Heading(NotAddedGroup.Unmatched, "psx"),
                NotAddedItem.File(1, "psx", "tool.exe"),
                NotAddedItem.Heading(NotAddedGroup.Unmatched, null),
                NotAddedItem.File(1, "Stuff", "data.xyz"),
                NotAddedItem.Heading(NotAddedGroup.Bios, null),
                NotAddedItem.File(1, "psx", "scph1001.bin"),
                NotAddedItem.Heading(NotAddedGroup.Saves, null),
                NotAddedItem.File(1, "psx", "Game (USA).srm"),
                NotAddedItem.Heading(NotAddedGroup.Junk, null),
                NotAddedItem.File(1, "", "notes.txt"),
                NotAddedItem.File(1, "snes", "readme.txt"),
                NotAddedItem.More(NotAddedGroup.Junk, null, 340),
            ),
            items,
        )
        assertEquals("every file once: 8 lines, 4 in the folder, 340 more", 8 + 4 + 340, notAddedCount(items))
        assertEquals("keys are unique (the lazy list needs it)", items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun anAnsweredFolderOrOneWithNothingLeftOutHasNoFolderLine() {
        val items = notAddedItems(rows.filter { !it.directoryPath.startsWith("Xbox 360") }, listOf(xbox, halo))
        assertTrue(items.none { it is NotAddedItem.Folder })
        val answered = notAddedItems(rows, listOf(xbox.copy(answer = FolderAnswer.Ignored)))
        assertTrue("an ignored folder's files are listed one by one", answered.none { it is NotAddedItem.Folder })
        assertTrue(NotAddedItem.File(1, "Xbox 360", "Halo 3.iso") in answered)
    }

    @Test
    fun aFolderOfTheSameNameInAnotherPickedFolderIsNotAbsorbed() {
        val items = notAddedItems(listOf(row("Xbox 360/a.iso", "no_system", folder = 2), row("Xbox 3600/b.iso", "no_system")), listOf(xbox))
        assertTrue(items.none { it is NotAddedItem.Folder })
        assertEquals(2, items.count { it is NotAddedItem.File })
    }

    @Test
    fun nothingLeftOutIsAnEmptyPageAndAnEmptyRowValue() {
        assertEquals(emptyList<NotAddedItem>(), notAddedItems(emptyList(), listOf(xbox)))
        assertEquals(0, notAddedCount(emptyList()))
        model.content = SettingsContent()
        assertTrue("the row stays: it says every file is in the library", SettingsOption.FilesNotAdded in SettingsSection.Library.rowsIn(model.content))
        openSection(SettingsSection.Library)
        repeat(SettingsSection.Library.rowsIn(model.content).size) { if (model.focusedOption != SettingsOption.FilesNotAdded) move(Direction.Down) }
        press(Action.Enter)
        assertEquals("the empty page opens", PanelPage.FilesNotAdded, model.focus.page)
        move(Direction.Down)
        assertEquals(0, model.focus.panelIndex)
    }

    private fun openPage() {
        model.content = SettingsContent(unknownFolders = listOf(xbox, halo), notAdded = notAddedItems(rows, listOf(xbox, halo)))
        openSection(SettingsSection.Library)
        repeat(SettingsSection.Library.rowsIn(model.content).size) { if (model.focusedOption != SettingsOption.FilesNotAdded) move(Direction.Down) }
        assertEquals(SettingsOption.FilesNotAdded, model.focusedOption)
        press(Action.Enter)
        assertEquals(PanelPage.FilesNotAdded, model.focus.page)
        assertEquals(0, model.focus.panelIndex)
    }

    @Test
    fun aOnAFolderLineGoesToThatFoldersRow() {
        openPage()
        move(Direction.Down)
        assertEquals(1, model.focus.panelIndex)
        press(Action.Enter)
        assertEquals(SettingsColumn.Options, model.focus.column)
        assertNull(model.focus.page)
        assertEquals(SettingsOption.UnknownFolder, model.focusedOption)
        assertEquals(xbox, model.content.unknownFolderAtRow(model.optionIndex))
    }

    @Test
    fun aOnAFileDoesNothingAndTheCursorStopsAtTheLastLine() {
        openPage()
        move(Direction.Down)
        move(Direction.Down)
        val before = model.focus
        press(Action.Enter)
        assertEquals(before, model.focus)
        repeat(30) { move(Direction.Down) }
        assertEquals(model.content.notAdded.lastIndex, model.focus.panelIndex)
    }
}
