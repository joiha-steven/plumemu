package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.db.game
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** A library database in a file with a WAL, as on a device (the snapshot needs a file). */
internal fun fileDatabase(file: File): PlumDatabase =
    Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PlumDatabase::class.java, file.path)
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .build()

/** One folder and [titles] as SNES games in one transaction; returns the folder's id. */
internal fun PlumDatabase.addGames(
    titles: List<String>,
    folderId: Long? = null,
): Long =
    runBlocking {
        val folder = folderId ?: scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
        scanDao().applyBatch(ScanBatch(folder, systems = listOf(SystemEntity("snes", "Super Nintendo")), newGames = titles.map { game(folder, it, title = it) }))
        folder
    }

/** The titles of the games in a database file, opened on its own, A to Z. */
internal fun titlesIn(file: File): List<String> {
    val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    try {
        return db.rawQuery("SELECT title FROM game ORDER BY title", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    } finally {
        db.close()
    }
}

/** [DatabaseSnapshot.take] off the main thread, where the app takes it too (Room refuses the main thread). */
internal fun snapshotOffMain(
    database: PlumDatabase,
    into: File,
    checkpointFirst: Boolean = true,
): DatabaseSnapshot.Snapshot = runBlocking(Dispatchers.IO) { DatabaseSnapshot.take(database, into, checkpointFirst = checkpointFirst) }
