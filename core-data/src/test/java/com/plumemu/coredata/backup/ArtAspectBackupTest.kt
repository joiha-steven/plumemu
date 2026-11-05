package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.TestDetailsRating
import com.plumemu.coredata.settings.PlumSettings
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `game.art_aspect` in an Everything backup: a backup taken at schema 18 restores with its shapes, one taken before the update
 * (schema 17) restores through `MIGRATION_17_18` with every old value and no shape, and the next pass fills them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtAspectBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private fun rows(file: File, sql: String): List<List<String?>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(sql, null).use { c ->
                buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
            }
        }

    private fun backUpAndRestore(database: File, schemaVersion: Int, gameCount: Int): File {
        val manifest =
            BackupManifest(
                kind = BackupKind.Everything,
                createdAtMillis = 1_792_756_800_000L,
                appVersionName = "test",
                appVersionCode = 1,
                includes = listOf(BackupPart.Settings, BackupPart.Database),
                databaseSchemaVersion = schemaVersion,
                gameCount = gameCount,
                databaseBytes = database.length(),
                mediaFileCount = 0,
                mediaBytes = 0,
                mediaRoot = null,
            )
        val zip = File(tmp.root, "backup.zip")
        zip.outputStream().use { runBlocking { BackupZip.write(BackupContents(manifest, PlumSettings(), null, database, emptyList()), it) } }
        val unpacked = runBlocking {
            zip.inputStream().use { BackupUnpack.unpack(it, File(tmp.root, "work"), PlumDatabase.SCHEMA_VERSION, freeBytes = { Long.MAX_VALUE }) }
        }
        val file = requireNotNull(unpacked.database)
        RestoredDatabase.prepare(ApplicationProvider.getApplicationContext(), file, "/old/media", "/new/media", TestDetailsRating)
        return file
    }

    @Test
    fun aBackupTakenAfterTheUpdateRestoresItsShapes() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        val folder = source.addGames(listOf("Zork Quest (USA)", "Mario (USA)"))
        val ids = runBlocking { source.scanDao().gamesInFolder(folder) }.map { it.id }.sorted()
        source.openHelper.writableDatabase.execSQL("UPDATE game SET art_aspect = 0.75 WHERE id = ?", arrayOf<Any>(ids[0]))
        val snapshot = snapshotOffMain(source, File(tmp.root, "snap/plumemu.db"))
        source.close()
        assertEquals(18, snapshot.schemaVersion)

        val file = backUpAndRestore(snapshot.file, snapshot.schemaVersion, snapshot.gameCount)

        assertEquals(listOf(listOf(ids[0].toString(), "0.75"), listOf(ids[1].toString(), null)), rows(file, "SELECT id, art_aspect FROM game ORDER BY id"))
    }

    @Test
    fun aBackupTakenBeforeTheUpdateRestoresWithEveryValueAndNoShape() {
        val name = "restore-v17.db"
        helper.createDatabase(name, 17).use { db ->
            db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
            db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at) VALUES (1, 'content://tree/1', 'ROMs', 10)")
            db.execSQL(
                "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, " +
                    "title, sort_name, regions, disc_count, system_id, is_primary_copy, is_hidden, is_favorite, is_pending, added_at) " +
                    "VALUES (1, 1, 'doc1', 'root', 'Zork.sfc', 'Zork.sfc', 1024, 1700000000000, 'Zork', 'zork', 'USA', 1, 'snes', 1, 0, 1, 0, 1800000000000)",
            )
            db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES (1, 70000, 1500, 0)")
        }
        val source = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(name)

        val file = backUpAndRestore(source, 17, 1)

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(PlumDatabase.SCHEMA_VERSION, it.version) }
        assertEquals(listOf(listOf("Zork", "1", "1800000000000", null)), rows(file, "SELECT title, is_favorite, added_at, art_aspect FROM game"))
        assertEquals(listOf(listOf("1", "1500")), rows(file, "SELECT game_id, total_seconds FROM play"))
    }
}
