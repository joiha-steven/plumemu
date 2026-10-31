package com.plumemu.coredata.backup

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Settings › Advanced › Back up…'s two choices. [Settings]: every setting and
 * the ScreenScraper account. [Everything]: also the library database and the saved box art.
 * [code] is how `manifest.json` spells it.
 */
enum class BackupKind(val code: String) {
    Settings("settings"),
    Everything("everything"),
}

/** One part of a backup, as `manifest.json`'s `includes` names it. */
enum class BackupPart(val code: String) {
    Settings("settings"),
    Account("account"),
    Database("database"),
    Media("media"),
}

/**
 * The ScreenScraper account as the backup carries it: the plain password (a
 * backup must restore on another device, whose key store cannot read this one's ciphertext).
 * [password] is null when it could not be decrypted here; the restore then asks for a new sign-in.
 * [toString] shows neither the name nor the password (the name is a log secret).
 */
class BackupAccount(
    val username: String,
    val password: String?,
    val isRefused: Boolean,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("version", VERSION)
            .put("service", SERVICE)
            .put("username", username)
            .put("password", password ?: JSONObject.NULL)
            .put("refused", isRefused)

    override fun equals(other: Any?): Boolean = other is BackupAccount && other.username == username && other.password == password && other.isRefused == isRefused

    override fun hashCode(): Int = (username.hashCode() * HASH_PRIME + password.hashCode()) * HASH_PRIME + isRefused.hashCode()

    override fun toString(): String = "BackupAccount(username=***, password=***, isRefused=$isRefused)"

    companion object {
        const val VERSION = 1
        const val SERVICE = "screenscraper"
        private const val HASH_PRIME = 31

        /** Reads `account.json`; throws [BackupFormatException] when it is not one. */
        fun fromJson(text: String): BackupAccount =
            try {
                val o = JSONObject(text)
                if (o.optString("service") != SERVICE) throw BackupFormatException("account.json is not a ScreenScraper account")
                val username = o.getString("username")
                if (username.isBlank()) throw BackupFormatException("account.json has no user name")
                BackupAccount(username, if (o.isNull("password")) null else o.getString("password"), o.optBoolean("refused", false))
            } catch (e: JSONException) {
                throw BackupFormatException("account.json could not be read", e)
            }
    }
}

/**
 * `manifest.json`, the backup's first entry: what made the
 * file, what it holds and how much. The counts are those planned when the zip was started; a media
 * file deleted while the backup ran is left out of the zip and counted in nothing, so a restore
 * takes the zip's entries as the truth.
 */
data class BackupManifest(
    val formatVersion: Int = FORMAT_VERSION,
    val kind: BackupKind,
    val createdAtMillis: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val includes: List<BackupPart>,
    /** The database's `user_version` (Room's schema version) in [BackupEntries.DATABASE]; null without a database. */
    val databaseSchemaVersion: Int?,
    /** Rows of `game` in the snapshot; null without a database. */
    val gameCount: Int?,
    val databaseBytes: Long?,
    val mediaFileCount: Int,
    val mediaBytes: Long,
    /**
     * The absolute path of the box-art folder on the device that made the backup. The database's
     * picture paths (`scraped_game.pictures`, `media.path`) start with it; a restore rewrites that
     * start to its own folder. Null without media.
     */
    val mediaRoot: String?,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("format", FORMAT_NAME)
            .put("formatVersion", formatVersion)
            .put("kind", kind.code)
            .put("createdAt", isoUtc(createdAtMillis))
            .put("createdAtMillis", createdAtMillis)
            .put("app", JSONObject().put("versionName", appVersionName).put("versionCode", appVersionCode))
            .put("includes", JSONArray(includes.map { it.code }))
            .put("settings", JSONObject().put("file", BackupEntries.SETTINGS).put("version", SettingsJson.VERSION))
            .put("account", if (BackupPart.Account in includes) JSONObject().put("file", BackupEntries.ACCOUNT).put("version", BackupAccount.VERSION) else JSONObject.NULL)
            .put(
                "database",
                if (databaseSchemaVersion == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("file", BackupEntries.DATABASE)
                        .put("schemaVersion", databaseSchemaVersion)
                        .put("games", gameCount ?: 0)
                        .put("bytes", databaseBytes ?: 0L)
                },
            ).put(
                "media",
                if (mediaRoot == null) {
                    JSONObject.NULL
                } else {
                    JSONObject()
                        .put("folder", BackupEntries.MEDIA_PREFIX)
                        .put("root", mediaRoot)
                        .put("files", mediaFileCount)
                        .put("bytes", mediaBytes)
                },
            )

    companion object {
        const val FORMAT_NAME = "plumemu-backup"

        /** Bump when an entry changes meaning or moves; step 2's restore refuses a newer one. */
        const val FORMAT_VERSION = 1

        /** Reads `manifest.json`; throws [BackupFormatException] when it is not a Plumemu backup's. */
        fun fromJson(text: String): BackupManifest =
            try {
                val o = JSONObject(text)
                if (o.optString("format") != FORMAT_NAME) throw BackupFormatException("not a Plumemu backup")
                val kind = BackupKind.entries.firstOrNull { it.code == o.getString("kind") } ?: throw BackupFormatException("unknown backup kind")
                val app = o.getJSONObject("app")
                val includes = o.getJSONArray("includes").let { a -> List(a.length()) { a.getString(it) } }
                val database = o.optJSONObject("database")
                val media = o.optJSONObject("media")
                BackupManifest(
                    formatVersion = o.getInt("formatVersion"),
                    kind = kind,
                    createdAtMillis = o.getLong("createdAtMillis"),
                    appVersionName = app.getString("versionName"),
                    appVersionCode = app.getLong("versionCode"),
                    // A part this version does not know is left out; the caller compares formatVersion.
                    includes = includes.mapNotNull { code -> BackupPart.entries.firstOrNull { it.code == code } },
                    databaseSchemaVersion = database?.getInt("schemaVersion"),
                    gameCount = database?.getInt("games"),
                    databaseBytes = database?.getLong("bytes"),
                    mediaFileCount = media?.getInt("files") ?: 0,
                    mediaBytes = media?.getLong("bytes") ?: 0L,
                    mediaRoot = media?.getString("root"),
                )
            } catch (e: JSONException) {
                throw BackupFormatException("manifest.json could not be read", e)
            }

        private fun isoUtc(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))
    }
}

/** The zip's entry names. */
object BackupEntries {
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    /** Always the last entry: a torn file (a killed process) is then unlikely to hold the password. */
    const val ACCOUNT = "account.json"
    const val DATABASE = "database/plumemu.db"

    /** Box art: `media/<path under the box-art folder>`, `/` between folders. */
    const val MEDIA_PREFIX = "media/"
}
