package com.plumemu.collections

import com.plumemu.coredata.db.CollectionRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionFoldersTest {
    private fun row(id: Long, name: String, position: Int, count: Int = 0) = CollectionRow(id, name, position, count)

    @Test
    fun everythingEmptyLeavesTheThreeBuiltInsAndNewCollection() {
        val folders = foldersOf(0, 0, 0, emptyList())
        assertEquals(
            listOf(
                Folder.BuiltIn(BuiltInFolder.Recent, 0),
                Folder.BuiltIn(BuiltInFolder.MostPlayed, 0),
                Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 0),
                Folder.NewCollection,
            ),
            folders,
        )
        assertEquals("empty folders are never hidden; New collection is not counted", 3, collectionCount(folders))
    }

    @Test
    fun builtInsThenOwnInPositionOrderThenNewCollection() {
        val folders = foldersOf(4, 5, 6, listOf(row(9, "B", 1, 2), row(3, "A", 0, 7)))
        assertEquals(
            listOf(
                Folder.BuiltIn(BuiltInFolder.Recent, 4),
                Folder.BuiltIn(BuiltInFolder.MostPlayed, 5),
                Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 6),
                Folder.Own(3, "A", 7),
                Folder.Own(9, "B", 2),
                Folder.NewCollection,
            ),
            folders,
        )
        assertEquals(5, collectionCount(folders))
    }

    @Test
    fun aLongNameIsKeptWhole() {
        val name = "x".repeat(40)
        assertEquals(Folder.Own(1, name, 0), foldersOf(0, 0, 0, listOf(row(1, name, 0)))[3])
    }

    @Test
    fun entryIdsAreStableAndNewCollectionHasNone() {
        assertEquals("recent", entryIdOf(Folder.BuiltIn(BuiltInFolder.Recent, 1)))
        assertEquals("most_played", entryIdOf(Folder.BuiltIn(BuiltInFolder.MostPlayed, 1)))
        assertEquals("recently_added", entryIdOf(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 1)))
        assertEquals("collection:12", entryIdOf(Folder.Own(12, "Any name", 0)))
        assertNull(entryIdOf(Folder.NewCollection))
    }

    @Test
    fun everyFolderOpensAsAGridUnlessAListWasChosen() {
        assertTrue(opensAsGrid("recent", emptySet()))
        assertFalse(opensAsGrid("recent", setOf("recent")))
        assertTrue(opensAsGrid("collection:2", setOf("recent")))
    }

    @Test
    fun theSwitchAddsAndRemovesWithoutTouchingOtherIds() {
        val list = toggledView("collection:2", setOf("recent"))
        assertEquals(setOf("recent", "collection:2"), list)
        assertEquals(setOf("recent"), toggledView("collection:2", list))
    }

    @Test
    fun eachFolderHasItsEmptyLine() {
        assertEquals(FolderEmptyLine.Recent, emptyLineOf(Folder.BuiltIn(BuiltInFolder.Recent, 0)))
        assertEquals(FolderEmptyLine.MostPlayed, emptyLineOf(Folder.BuiltIn(BuiltInFolder.MostPlayed, 0)))
        assertEquals(FolderEmptyLine.RecentlyAdded, emptyLineOf(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 0)))
        assertEquals(FolderEmptyLine.Own, emptyLineOf(Folder.Own(1, "x", 0)))
        assertNull(emptyLineOf(Folder.NewCollection))
    }

    @Test
    fun onlyOwnCollectionsCanBeRenamedDeletedOrMoved() {
        val own = Folder.Own(1, "x", 0)
        assertTrue(canRename(own) && canDelete(own) && canMove(own))
        for (other in listOf(Folder.BuiltIn(BuiltInFolder.Recent, 0), Folder.NewCollection)) {
            assertFalse(canRename(other) || canDelete(other) || canMove(other))
        }
    }
}
