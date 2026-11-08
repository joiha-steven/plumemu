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
 * `rom_folder.unreadable_paths` and Everything backups: a backup made by 0.1.3 (schema 18) still restores, through
 * `MIGRATION_18_19`, with every folder, game, favourite and play time; a backup taken at 19 restores with the paths cleared
 * (scan state of the device that made it), everything else kept.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnreadablePathsBackupTest {
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
                appVersionName = "0.1.3",
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
    fun aBackupMadeBy013RestoresWithEveryFolderGameFavouriteAndPlayTime() {
        val name = "restore-v18.db"
        helper.createDatabase(name, 18).use { db ->
            db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
            for (folder in 1..2) {
                db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at, last_scan_finished_at) VALUES ($folder, 'content://tree/$folder', 'ROMs $folder', 10, 20)")
            }
            for (id in 1..4) {
                db.execSQL(
                    "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, " +
                        "title, sort_name, regions, disc_count, system_id, is_primary_copy, is_hidden, is_favorite, is_pending, added_at, art_aspect) " +
                        "VALUES ($id, ${id % 2 + 1}, 'doc$id', 'root', 'g$id.sfc', 'g$id.sfc', 1024, 1700000000000, 'Game $id', 'game $id', 'USA', 1, " +
                        "'snes', 1, 0, ${id % 2}, 0, 1800000000000, 0.75)",
                )
                db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES ($id, 70000, ${id * 100}, 0)")
            }
        }
        val source = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(name)

        val file = backUpAndRestore(source, 18, 4)

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(PlumDatabase.SCHEMA_VERSION, it.version) }
        assertEquals(
            listOf(listOf("1", "ROMs 1", "20", null), listOf("2", "ROMs 2", "20", null)),
            rows(file, "SELECT id, display_name, last_scan_finished_at, unreadable_paths FROM rom_folder ORDER BY id"),
        )
        assertEquals(
            listOf(listOf("Game 1", "2", "1", "0.75"), listOf("Game 2", "1", "0", "0.75"), listOf("Game 3", "2", "1", "0.75"), listOf("Game 4", "1", "0", "0.75")),
            rows(file, "SELECT title, folder_id, is_favorite, art_aspect FROM game ORDER BY id"),
        )
        assertEquals(listOf("100", "200", "300", "400"), rows(file, "SELECT total_seconds FROM play ORDER BY game_id").map { it.single() })
    }

    @Test
    fun aBackupTakenWithUnreadablePathsRestoresWithoutThem() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        val folder = source.addGames(listOf("Zork Quest (USA)", "Mario (USA)"))
        runBlocking { source.scanDao().setUnreadablePaths(folder, "psx\nsnes/hacks") }
        val snapshot = snapshotOffMain(source, File(tmp.root, "snap/plumemu.db"))
        source.close()
        assertEquals(listOf(listOf("psx\nsnes/hacks")), rows(snapshot.file, "SELECT unreadable_paths FROM rom_folder"))

        val file = backUpAndRestore(snapshot.file, snapshot.schemaVersion, snapshot.gameCount)

        assertEquals(listOf(listOf("ROMs", null)), rows(file, "SELECT display_name, unreadable_paths FROM rom_folder"))
        assertEquals(listOf("Mario (USA)", "Zork Quest (USA)"), titlesIn(file))
    }
}
