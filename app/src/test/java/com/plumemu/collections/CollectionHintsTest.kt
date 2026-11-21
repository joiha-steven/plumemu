package com.plumemu.collections

import com.plumemu.collections.FolderHint.Back
import com.plumemu.collections.FolderHint.NewCollection
import com.plumemu.collections.FolderHint.Open
import com.plumemu.collections.FolderHint.Search
import com.plumemu.collections.FolderHint.SwitchEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionHintsTest {
    @Test
    fun aFolderRowShowsOpenBackSearchSwitch() {
        for (folder in listOf(Folder.BuiltIn(BuiltInFolder.Recent, 0), Folder.Own(1, "x", 3))) {
            assertEquals(listOf(Open, Back, Search, SwitchEntry), hintsFor(folder, hasOtherEntries = true))
        }
    }

    @Test
    fun theSwitchIsLeftOutWithNoOtherEntry() {
        assertEquals(listOf(Open, Back, Search), hintsFor(Folder.Own(1, "x", 0), hasOtherEntries = false))
    }

    @Test
    fun newCollectionTakesOpensPlace() {
        assertEquals(listOf(NewCollection, Back, Search, SwitchEntry), hintsFor(Folder.NewCollection, true))
        assertEquals(listOf(NewCollection, Back, Search), hintsFor(Folder.NewCollection, false))
    }

    @Test
    fun noFocusStillGivesOnlyFolderHints() {
        assertEquals(listOf(Open, Back, Search), hintsFor(null, false))
    }

    @Test
    fun theHintEnumHasNoFavoriteSortOrGridToggle() {
        assertEquals(listOf("Open", "NewCollection", "Search", "SwitchEntry", "Back"), FolderHint.entries.map { it.name })
    }
}
