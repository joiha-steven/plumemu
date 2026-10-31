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
 * The person's system for an unknown folder (`folder_system`, schema 9) travels in an Everything
 * backup with the rest of the database. An older backup (schema 8) restores through
 * `MIGRATION_8_9` with no folder's system chosen (RestoredDatabaseTest, PlumMigration8To9Test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderSystemBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun aFoldersChosenSystemAndIgnoreSurviveABackupAndARestore() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        val folder = source.addGames(listOf("Zork Quest (USA)"))
        runBlocking {
            source.folderSystemDao().choose(folder, "Xbox 360", "xbox360", now = 70)
            source.folderSystemDao().choose(folder, "Saves", null, now = 71)
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
            db.rawQuery("SELECT folder_id, relative_path, system_id, chosen_at FROM folder_system ORDER BY relative_path", null).use { c ->
                val rows = buildList { while (c.moveToNext()) add(listOf(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))) }
                assertEquals(listOf(listOf(folder, "Saves", null, 71L), listOf(folder, "Xbox 360", "xbox360", 70L)), rows)
            }
        }
    }
}
