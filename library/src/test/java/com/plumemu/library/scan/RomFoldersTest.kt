package com.plumemu.library.scan

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RomFoldersTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java).build()
    private val permissions = FakeFolderPermissions()
    private val folders = RomFolders(db.scanDao(), permissions, clock = { 7 })

    @After
    fun tearDown() = db.close()

    @Test
    fun pickedFolderIsRecordedOnceWithItsGrantKept() = runTest {
        val added = folders.addPickedFolder("content://t/tree/primary%3AROMs", "ROMs")
        assertTrue(added is AddFolderResult.Added)
        assertTrue(permissions.isHeld("content://t/tree/primary%3AROMs"))
        val again = folders.addPickedFolder("content://t/tree/primary%3AROMs", "ROMs")
        assertEquals(AddFolderResult.AlreadyAdded((added as AddFolderResult.Added).folderId), again)
        assertEquals(1, db.scanDao().folders().size)
    }

    @Test
    fun folderWithoutALastingGrantIsNotRecorded() = runTest {
        permissions.grantsOnTake = false
        assertEquals(AddFolderResult.PermissionNotGranted, folders.addPickedFolder("content://t/tree/x", "x"))
        assertEquals(0, db.scanDao().folders().size)
    }

    @Test
    fun folderTheDatabaseDoesNotStoreIsReportedAndItsGrantReleased() = runTest {
        // The database drops the row without an error, as an IGNORE conflict would.
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER drop_folders BEFORE INSERT ON rom_folder BEGIN SELECT RAISE(IGNORE); END",
        )
        assertEquals(AddFolderResult.NotRecorded, folders.addPickedFolder("content://t/tree/y", "y"))
        assertTrue(!permissions.isHeld("content://t/tree/y"))
    }

    @Test
    fun removingAFolderReleasesTheGrant() = runTest {
        folders.addPickedFolder("content://t/tree/x", "x")
        folders.removeFolder(db.scanDao().folders().single())
        assertEquals(0, db.scanDao().folders().size)
        assertTrue(!permissions.isHeld("content://t/tree/x"))
    }
}
