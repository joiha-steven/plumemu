package com.plumemu.coredata.backup

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** manifest.json and account.json: what step 2's restore reads first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManifestTest {
    private val everything =
        BackupManifest(
            kind = BackupKind.Everything,
            createdAtMillis = 1_792_756_800_000L, // noon UTC on a fixed day
            appVersionName = "0.0.3-alpha",
            appVersionCode = 31,
            includes = listOf(BackupPart.Settings, BackupPart.Account, BackupPart.Database, BackupPart.Media),
            databaseSchemaVersion = 4,
            gameCount = 2_054,
            databaseBytes = 4_096_000,
            mediaFileCount = 5_000,
            mediaBytes = 412_000_000,
            mediaRoot = "/data/user/0/com.plumemu/no_backup/screenscraper-media",
        )

    @Test
    fun theManifestNamesTheFormatTheAppTheSchemaAndTheCounts() {
        val json = everything.toJson()
        assertEquals("plumemu-backup", json.getString("format"))
        assertEquals(BackupManifest.FORMAT_VERSION, json.getInt("formatVersion"))
        assertEquals("everything", json.getString("kind"))
        assertEquals("2026-10-23T12:00:00Z", json.getString("createdAt"))
        assertEquals("0.0.3-alpha", json.getJSONObject("app").getString("versionName"))
        assertEquals(31L, json.getJSONObject("app").getLong("versionCode"))
        assertEquals(listOf("settings", "account", "database", "media"), json.getJSONArray("includes").let { a -> List(a.length()) { a.getString(it) } })
        val database = json.getJSONObject("database")
        assertEquals("database/plumemu.db", database.getString("file"))
        assertEquals(4, database.getInt("schemaVersion"))
        assertEquals(2_054, database.getInt("games"))
        val media = json.getJSONObject("media")
        assertEquals("media/", media.getString("folder"))
        assertEquals(5_000, media.getInt("files"))
        assertEquals(412_000_000L, media.getLong("bytes"))
        assertEquals(SettingsJson.VERSION, json.getJSONObject("settings").getInt("version"))
        assertEquals("account.json", json.getJSONObject("account").getString("file"))
    }

    @Test
    fun aSettingsBackupHasNoDatabaseNoMediaAndNoAccountWhenSignedOut() {
        val settings =
            everything.copy(
                kind = BackupKind.Settings,
                includes = listOf(BackupPart.Settings),
                databaseSchemaVersion = null,
                gameCount = null,
                databaseBytes = null,
                mediaFileCount = 0,
                mediaBytes = 0,
                mediaRoot = null,
            )
        val json = settings.toJson()
        assertTrue(json.isNull("database"))
        assertTrue(json.isNull("media"))
        assertTrue(json.isNull("account"))
        assertEquals(settings, BackupManifest.fromJson(json.toString()))
    }

    @Test
    fun theManifestReadsBackEqual() {
        assertEquals(everything, BackupManifest.fromJson(everything.toJson().toString()))
    }

    @Test(expected = BackupFormatException::class)
    fun anotherFormatIsRefused() {
        BackupManifest.fromJson(everything.toJson().put("format", "something-else").toString())
    }

    @Test
    fun theAccountCarriesThePlainPasswordAndNeverShowsItInText() {
        val account = BackupAccount("plumtester", "s3cret pass", isRefused = false)
        val json = account.toJson()
        assertEquals("screenscraper", json.getString("service"))
        assertEquals("s3cret pass", json.getString("password"))
        assertEquals(account, BackupAccount.fromJson(json.toString()))
        assertFalse(account.toString().contains("s3cret"))
        assertFalse(account.toString().contains("plumtester"))
    }

    @Test
    fun anAccountWhosePasswordCouldNotBeDecryptedKeepsTheName() {
        val account = BackupAccount("plumtester", null, isRefused = true)
        val read = BackupAccount.fromJson(account.toJson().toString())
        assertEquals(null, read.password)
        assertTrue(read.isRefused)
        assertTrue(JSONObject(account.toJson().toString()).isNull("password"))
    }
}
