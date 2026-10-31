package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.CopyChoice
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
 * Other versions' choice (`copy_choice`, schema 7) travels in an Everything backup: the snapshot
 * holds it, the restore reads and prepares it, and the restored file plays the chosen copy.
 * An older backup (schema 6) restores with no choice: RestoredDatabaseTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyChoiceBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun aChosenCopySurvivesABackupAndARestore() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        source.addGames(listOf("Zork Quest (USA)", "Zork Quest (Europe)"))
        val ids = source.openHelper.readableDatabase.query("SELECT id FROM game ORDER BY title DESC").use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }
        runBlocking { source.copyChoiceDao().choose(shownId = ids[0], chosenId = ids[1], copies = ids, at = 1_792_756_800_000L) }
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

        val unpacked = runBlocking { zip.inputStream().use { BackupUnpack.unpack(it, File(tmp.root, "work"), PlumDatabase.SCHEMA_VERSION, freeBytes = { Long.MAX_VALUE }) } }
        val file = requireNotNull(unpacked.database)
        RestoredDatabase.prepare(ApplicationProvider.getApplicationContext(), file, "/old/media", "/new/media")

        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT game_id, chosen_at FROM copy_choice", null).use { c ->
                val rows = buildList { while (c.moveToNext()) add(CopyChoice(c.getLong(0), c.getLong(1))) }
                assertEquals(listOf(CopyChoice(ids[1], 1_792_756_800_000L)), rows)
            }
        }
    }
}
