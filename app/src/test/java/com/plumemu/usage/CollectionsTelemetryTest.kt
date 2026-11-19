package com.plumemu.usage

import com.plumemu.collections.BuiltInFolder
import com.plumemu.collections.Folder
import com.plumemu.coreinput.Column
import com.plumemu.home.EntryKind
import com.plumemu.home.FolderRows
import com.plumemu.home.HomeContent
import com.plumemu.home.GameRowModel
import com.plumemu.telemetry.LaunchFrom
import com.plumemu.telemetry.ScreenId
import org.junit.Assert.assertEquals
import org.junit.Test

/** What telemetry says for the Collections entry: `collections`, and `recent` inside the Recent folder; `game_launched.from` stays `recent` there. */
class CollectionsTelemetryTest {
    private val rows = listOf(GameRowModel("g1", "Game", "USA"))
    private val content =
        HomeContent.of(rows, emptyList(), emptyList(), emptyList(), otherFolders = listOf(FolderRows(Folder.BuiltIn(BuiltInFolder.MostPlayed, 1), rows), FolderRows(Folder.BuiltIn(BuiltInFolder.RecentlyAdded, 1), rows), FolderRows(Folder.Own(3, "RPG", 1), rows)))

    @Test
    fun theFolderListAndEveryFolderButRecentReportCollections() {
        assertEquals("collections", ScreenId.Collections.wire)
        assertEquals(ScreenId.Collections, homeScreenIdOf(Column.Games, EntryKind.Collections, false, false))
        content.folderEntries.filter { it.kind == EntryKind.Collections }.forEach {
            assertEquals(ScreenId.Collections, homeScreenIdOf(Column.Games, it.kind, false, false))
        }
    }

    @Test
    fun insideTheRecentFolderTheScreenIsStillRecentWithItsOldName() {
        val recent = content.folderEntries.first { it.id == HomeContent.RECENT_ID }
        assertEquals(ScreenId.Recent, homeScreenIdOf(Column.Games, recent.kind, false, false))
        assertEquals("recent", ScreenId.Recent.wire)
    }

    @Test
    fun theSystemColumnOnCollectionsIsHomeAndAPlayFromRecentKeepsItsName() {
        assertEquals(ScreenId.Home, homeScreenIdOf(Column.Systems, EntryKind.Collections, false, false))
        assertEquals(LaunchFrom.Recent, launchFromOf(content.folderEntries.first { it.id == HomeContent.RECENT_ID }, isGrid = true))
        assertEquals("collections", LaunchFrom.Collections.wire)
        assertEquals(LaunchFrom.Collections, launchFromOf(content.folderEntries.first { it.id == "most_played" }, isGrid = true))
        assertEquals(LaunchFrom.Collections, launchFromOf(content.folderEntries.first { it.id == "recently_added" }, isGrid = true))
        assertEquals(LaunchFrom.Collections, launchFromOf(content.folderEntries.first { it.id == "collection:3" }, isGrid = false))
    }
}
