package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.TestDetailsRating
import com.plumemu.coredata.settings.PlumSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Collections travel with an Everything backup (the database is copied whole): a restored file
 * keeps every collection in its order, every membership with its keys, and the unbound ones too,
 * so the restoring device's next scan can bind them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectionBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun rows(file: File, sql: String): List<List<String?>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(sql, null).use { c ->
                buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
            }
        }

    private fun backUpAndRestore(snapshot: DatabaseSnapshot.Snapshot): File {
        val manifest =
            BackupManifest(
                kind = BackupKind.Everything,
                createdAtMillis = 1_792_756_800_000L,
                appVersionName = "0.1.7",
                appVersionCode = 1,
                includes = listOf(BackupPart.Settings, BackupPart.Database),
                databaseSchemaVersion = snapshot.schemaVersion,
                gameCount = snapshot.gameCount,
                databaseBytes = snapshot.file.length(),
                mediaFileCount = 0,
                mediaBytes = 0,
                mediaRoot = null,
            )
        val zip = File(tmp.root, "backup.zip")
        zip.outputStream().use { runBlocking { BackupZip.write(BackupContents(manifest, PlumSettings(), null, snapshot.file, emptyList()), it) } }
        val unpacked = runBlocking {
            zip.inputStream().use { BackupUnpack.unpack(it, File(tmp.root, "work"), PlumDatabase.SCHEMA_VERSION, freeBytes = { Long.MAX_VALUE }) }
        }
        val file = requireNotNull(unpacked.database)
        RestoredDatabase.prepare(ApplicationProvider.getApplicationContext(), file, "/old/media", "/new/media", TestDetailsRating)
        return file
    }

    @Test
    fun aRestoredDatabaseKeepsCollectionsAndMembershipsBoundOrNot() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        val folder = source.addGames(listOf("Zork Quest (USA)", "Mario (USA)", "Gone (USA)"))
        val (rpgs, hacks) = runBlocking {
            source.scanDao().finishScan(com.plumemu.coredata.db.ScanFinish(folder))
            val ids = source.scanDao().gamesInFolder(folder).associate { it.title to it.id }
            val dao = source.collectionDao()
            val rpgs = dao.create("RPGs", now = 1)
            val hacks = dao.create("Hacks", now = 2)
            dao.add(rpgs, ids.getValue("Zork Quest (USA)"), now = 3)
            dao.add(rpgs, ids.getValue("Gone (USA)"), now = 4)
            dao.add(hacks, ids.getValue("Mario (USA)"), now = 5)
            dao.move(hacks, 0)
            // Its file was removed before the backup: the membership is unbound and must travel too.
            source.scanDao().finishScan(com.plumemu.coredata.db.ScanFinish(folder, removedGameIds = listOf(ids.getValue("Gone (USA)"))))
            rpgs to hacks
        }
        val snapshot = snapshotOffMain(source, File(tmp.root, "snap/plumemu.db"))
        source.close()

        val file = backUpAndRestore(snapshot)

        assertEquals(
            listOf(listOf("$hacks", "Hacks", "0", "2"), listOf("$rpgs", "RPGs", "1", "1")),
            rows(file, "SELECT id, name, position, created_at FROM collection ORDER BY position"),
        )
        assertEquals(
            listOf(
                listOf("$rpgs", "Zork Quest (USA)", "content://tree/roms\nZork Quest (USA)", "3"),
                listOf("$rpgs", null, "content://tree/roms\nGone (USA)", "4"),
                listOf("$hacks", "Mario (USA)", "content://tree/roms\nMario (USA)", "5"),
            ),
            rows(
                file,
                "SELECT collection_game.collection_id, game.title, collection_game.path_key, collection_game.added_at " +
                    "FROM collection_game LEFT JOIN game ON game.id = collection_game.game_id ORDER BY collection_game.id",
            ),
        )
        val restored = fileDatabase(file)
        runBlocking {
            assertEquals(listOf("Hacks" to 1, "RPGs" to 1), restored.collectionDao().observeCollections().first().map { it.name to it.gameCount })
            assertEquals(listOf("Zork Quest (USA)"), restored.collectionDao().observeCollectionGames(rpgs).first().map { it.title })
        }
        restored.close()
    }
}
