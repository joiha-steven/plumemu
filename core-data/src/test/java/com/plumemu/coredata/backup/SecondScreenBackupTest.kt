package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RaAchievementEntity
import com.plumemu.coredata.db.RaProgressEntity
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
 * Schema 17's values (`game.added_at`, the progress points, `ra_achievement`) travel in an
 * Everything backup with the rest of the database; a schema 16 backup restores through
 * `MIGRATION_16_17` with every old value and the new ones empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecondScreenBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PlumDatabase::class.java)

    private fun rows(file: File, sql: String): List<List<String?>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery(sql, null).use { c ->
                buildList { while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) }) }
            }
        }

    /** Zips [database] as an Everything backup, unpacks and prepares it as a restore does; returns the prepared file. */
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
    fun addedTimesPointsAndAchievementsSurviveABackupAndARestore() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        val folder = source.addGames(listOf("Zork Quest (USA)"))
        runBlocking {
            source.raDao().saveGameProgress(
                RaProgressEntity(1446, 2, 1, 1, null, 6, pointsEarned = 10, pointsTotal = 15),
                listOf(
                    RaAchievementEntity(1446, 7, "Win", "250336", 10, 1_714_557_600_000, 1_714_557_600_000),
                    RaAchievementEntity(1446, 8, "Lose", null, 5, null, null),
                ),
            )
        }
        val addedAt = runBlocking { source.scanDao().gamesInFolder(folder).single().addedAt }
        val snapshot = snapshotOffMain(source, File(tmp.root, "snap/plumemu.db"))
        source.close()
        assertEquals(PlumDatabase.SCHEMA_VERSION, snapshot.schemaVersion)

        val file = backUpAndRestore(snapshot.file, snapshot.schemaVersion, snapshot.gameCount)

        assertEquals(listOf(listOf(addedAt?.toString())), rows(file, "SELECT added_at FROM game"))
        assertEquals(listOf(listOf("1446", "10", "15")), rows(file, "SELECT ra_game_id, points_earned, points_total FROM ra_progress"))
        assertEquals(
            listOf(
                listOf("1446", "7", "Win", "250336", "10", "1714557600000", "1714557600000"),
                listOf("1446", "8", "Lose", null, "5", null, null),
            ),
            rows(file, "SELECT ra_game_id, achievement_id, title, badge_name, points, earned_at, earned_hardcore_at FROM ra_achievement ORDER BY achievement_id"),
        )
    }

    @Test
    fun aVersion16BackupRestoresWithEveryValueAndNoNewOne() {
        val name = "restore-v16.db"
        helper.createDatabase(name, 16).use { db ->
            db.execSQL("INSERT INTO system (id, name) VALUES ('snes', 'Super Nintendo')")
            db.execSQL("INSERT INTO rom_folder (id, tree_uri, display_name, added_at) VALUES (1, 'content://tree/1', 'ROMs', 10)")
            db.execSQL(
                "INSERT INTO game (id, folder_id, document_id, parent_document_id, relative_path, file_name, size_bytes, modified_at, " +
                    "title, sort_name, regions, disc_count, system_id, is_primary_copy, is_hidden, is_favorite, is_pending) " +
                    "VALUES (1, 1, 'doc1', 'root', 'Zork.sfc', 'Zork.sfc', 1024, 1700000000000, 'Zork', 'zork', 'USA', 1, 'snes', 1, 0, 1, 0)",
            )
            db.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES (1, 70000, 1500, 0)")
            db.execSQL(
                "INSERT INTO ra_progress (ra_game_id, achievements_total, awarded_softcore, awarded_hardcore, highest_award, updated_at) " +
                    "VALUES (1446, 10, 10, 10, 'mastered', 6)",
            )
        }
        val source = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath(name)

        val file = backUpAndRestore(source, 16, 1)

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { assertEquals(PlumDatabase.SCHEMA_VERSION, it.version) }
        assertEquals(
            listOf(listOf("1", "Zork", "1700000000000", "1", null)),
            rows(file, "SELECT id, title, modified_at, is_favorite, added_at FROM game"),
        )
        assertEquals(listOf(listOf("1", "70000", "1500")), rows(file, "SELECT game_id, last_played_at, total_seconds FROM play"))
        assertEquals(
            listOf(listOf("1446", "10", "10", "10", "mastered", "6", null, null)),
            rows(
                file,
                "SELECT ra_game_id, achievements_total, awarded_softcore, awarded_hardcore, highest_award, updated_at, points_earned, points_total FROM ra_progress",
            ),
        )
        assertEquals(listOf(listOf("0")), rows(file, "SELECT COUNT(*) FROM ra_achievement"))
    }
}
