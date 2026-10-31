package com.plumemu.graph

import com.plumemu.coredata.db.FolderGameCount
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.setup.SetupFolder
import org.junit.Assert.assertEquals
import org.junit.Test

/** Setup's folder list: Android's grants decide which recorded folders show (also after process death). */
class GrantedFoldersTest {
    private val roms = RomFolderEntity(id = 1, treeUri = "tree-roms", displayName = "ROMs", addedAt = 0, lastScanFinishedAt = 50)
    private val sd = RomFolderEntity(id = 2, treeUri = "tree-sd", displayName = "Games", addedAt = 0)
    private val revoked = RomFolderEntity(id = 3, treeUri = "tree-revoked", displayName = "Old", addedAt = 0, lastScanFinishedAt = 10)

    @Test
    fun onlyFoldersStillGrantedShowWithTheirCounts() {
        val held = setOf("tree-roms", "tree-sd")
        val shown = grantedFolders(listOf(roms, sd, revoked), listOf(FolderGameCount(1, 1031, 14), FolderGameCount(3, 5, 1)), held::contains)
        assertEquals(
            listOf(
                SetupFolder(1, "tree-roms", "ROMs", gameCount = 1031, systemCount = 14, isCounted = true),
                SetupFolder(2, "tree-sd", "Games", gameCount = 0, systemCount = 0, isCounted = false),
            ),
            shown,
        )
    }

    @Test
    fun noGrantsNoFolders() {
        assertEquals(emptyList<SetupFolder>(), grantedFolders(listOf(roms, sd), emptyList()) { false })
    }
}
