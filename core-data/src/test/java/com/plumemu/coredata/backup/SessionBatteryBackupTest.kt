package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.SessionBattery
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
 * A play's battery figures (`play_session`'s battery columns, schema 8) travel in an
 * Everything backup like the session they belong to: the snapshot holds them, the restore reads
 * and prepares the file, and the game's usual pace is the same after it. An older backup (schema
 * 7) restores through `MIGRATION_7_8` with its sessions not measured (PlumMigration7To8Test).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionBatteryBackupTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun aPlaysBatteryUseSurvivesABackupAndARestore() {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        source.addGames(listOf("Zork Quest (USA)"))
        val id = source.openHelper.readableDatabase.query("SELECT id FROM game").use { c -> c.moveToFirst(); c.getLong(0) }
        val measured = SessionBattery(measuredSeconds = 1_140, chargeStartMicroampHours = 2_060_000, chargeEndMicroampHours = 1_940_000, drawMicroamps = 379_000)
        runBlocking { source.playDao().addSession(id, endedAt = 1_792_756_800_000L, seconds = 1_200, battery = measured) }
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
            db.rawQuery("SELECT game_id, battery_seconds, charge_start_uah, charge_end_uah, battery_draw_ua FROM play_session", null).use { c ->
                val rows = buildList { while (c.moveToNext()) add(SessionBattery(c.getLong(1), c.getLong(2), c.getLong(3), c.getLong(4)) to c.getLong(0)) }
                assertEquals(listOf(measured to id), rows)
            }
        }
    }
}
