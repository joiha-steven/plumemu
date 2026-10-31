package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
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
 * The person's skipped sub-folders (`skipped_subfolder`, schema 13) travel in an
 * Everything backup with the rest of the database. An older backup (schema 12) restores through
 * `MIGRATION_12_13` with nothing skipped (RestoredDatabaseTest, PlumMigration12To13Test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SkippedSubfolderBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun skippedSubfoldersSurviveABackupAndARestore() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        val folder = source.addGames(listOf("Zork Quest (USA)"))
        runBlocking {
            source.skippedSubfolderDao().skip(folder, "Hacks", now = 70)
            source.skippedSubfolderDao().skip(folder, "_unsorted/", now = 71)
        }
        val snapshot = snapshotOffMain(source, File(tmp.root, "snap/plumemu.db"))
        source.close()
        val manifest =
            BackupManifest(
                kind = BackupKind.Everything,
                createdAtMillis = 1_792_756_800_000L,
                appVersionName = "test",
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
        RestoredDatabase.prepare(ApplicationProvider.getApplicationContext(), file, "/old/media", "/new/media")

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT folder_id, relative_path, skipped_at FROM skipped_subfolder ORDER BY relative_path", null).use { c ->
                val rows = buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getLong(2))) }
                assertEquals(listOf(listOf(folder, "Hacks", 70L), listOf(folder, "_unsorted", 71L)), rows)
            }
        }
    }
}
