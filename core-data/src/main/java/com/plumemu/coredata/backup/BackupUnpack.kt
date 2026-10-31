package com.plumemu.coredata.backup

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.plumemu.coredata.settings.PlumSettings
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONException
import org.json.JSONObject

/** Why a file cannot be restored. Nothing on the device has changed. */
enum class RestoreRefusal {
    /** Not a zip, no `manifest.json` first, another `format`, or entries that do not fit the manifest. */
    NotABackup,

    /** `formatVersion` above [BackupManifest.FORMAT_VERSION]: made by a newer Plumemu. */
    NewerFormat,

    /** The database's schema is above the app's: a newer database cannot be migrated down. */
    NewerDatabase,

    /** The zip ends early or an entry is damaged (its CRC does not match), or a listed part is missing. */
    Torn,

    /** The database does not open or fails `quick_check`. */
    BadDatabase,

    /** `settings.json` does not parse. */
    BadSettings,

    /** `account.json` does not parse. */
    BadAccount,

    /** Not enough free space for what the manifest says the file holds. */
    NoSpace,
}

/** A file [BackupUnpack] refused, with the [refusal] the person is told. The message is for the log and names no path. */
class RestoreRefusedException(
    val refusal: RestoreRefusal,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** A backup extracted into a private folder and checked; nothing on the device has changed yet. */
class UnpackedBackup(
    val manifest: BackupManifest,
    val settings: PlumSettings,
    /** Setting keys (`group.key`) the file had with values this version does not know; read as defaults. */
    val unknownSettingKeys: List<String>,
    /** Null when the file holds none (the device's own account then stays). */
    val account: BackupAccount?,
    /** The checked database ([BackupUnpack.DATABASE_PATH] in the folder); null for a settings backup. */
    val database: File?,
    /** The `user_version` of [database]; null without one. */
    val databaseSchemaVersion: Int?,
    /** The box art ([BackupUnpack.MEDIA_FOLDER] in the folder, maybe empty); null for a settings backup. */
    val media: File?,
    /** Box-art files extracted: the zip's entries are the truth, not the manifest's count. */
    val mediaFiles: Int,
    /** Box-art entries that were damaged in the file and left out; the restored database still names them. */
    val damagedMediaFiles: Int = 0,
)

/**
 * Reads a backup zip into a private folder, streamed: one 64 KB
 * buffer, whatever the size of the box art. The manifest is read first and refuses the file before
 * anything else is read; then every entry is extracted, and the settings, the account and the
 * database are checked. Zip64 files (written by newer devices for very large backups) are read as
 * any other: [ZipInputStream] reads the local headers, which carry the sizes either way.
 *
 * A damaged box-art entry (its bytes do not inflate, or its CRC fails) is left out and counted:
 * box art is downloaded, not the person's own data, and one bad picture must not cost the
 * library. A damaged manifest, settings, account or database entry still refuses the file (Torn).
 *
 * Cancellation is checked per buffer. Throws [RestoreRefusedException] for a file that cannot be
 * restored, an [IOException] when the folder cannot be written; either way the caller deletes the folder.
 */
object BackupUnpack {
    /** Where [unpack] puts the database, relative to its folder. */
    const val DATABASE_PATH = "database/plumemu.db"

    /** Where [unpack] puts the box art, relative to its folder. */
    const val MEDIA_FOLDER = "media"

    /** Room keeps one `user_version` per schema; a file above the app's cannot be opened by it. */
    suspend fun unpack(
        input: InputStream,
        into: File,
        appSchemaVersion: Int,
        freeBytes: (File) -> Long = { it.usableSpace },
        log: (String) -> Unit = {},
        onManifest: (BackupManifest) -> Unit = {},
        onProgress: (BackupProgress) -> Unit = {},
    ): UnpackedBackup {
        into.deleteRecursively()
        if (!into.mkdirs()) throw IOException("the restore's work folder could not be made")
        val source = ResumableSource(BufferedInputStream(input, BUFFER_BYTES))
        var tap = RecentBytesTap(source)
        var zip = ZipInputStream(tap)
        val buffer = ByteArray(BUFFER_BYTES)
        val first = nextEntry(zip, isFirst = true) ?: throw RestoreRefusedException(RestoreRefusal.NotABackup, "the file is empty or not a zip")
        if (first.name != BackupEntries.MANIFEST) throw RestoreRefusedException(RestoreRefusal.NotABackup, "the first entry is not the manifest")
        val manifest = manifestOf(readText(zip, buffer), appSchemaVersion)
        onManifest(manifest)
        val needed = (manifest.databaseBytes ?: 0L) + manifest.mediaBytes + SPACE_MARGIN_BYTES
        if (freeBytes(into) < needed) throw RestoreRefusedException(RestoreRefusal.NoSpace, "not enough free space (${needed / BYTES_PER_MB} MB needed)")

        val budget = Budget(into, needed, freeBytes)
        val wantsLibrary = manifest.kind == BackupKind.Everything
        val mediaRoot = File(into, MEDIA_FOLDER)
        val databaseFile = File(into, DATABASE_PATH)
        var settings: SettingsJson.Decoded? = null
        var account: BackupAccount? = null
        var hasDatabase = false
        var mediaFiles = 0
        var damagedMedia = 0
        var skipped = 0
        if (wantsLibrary) mediaRoot.mkdirs()
        onProgress(BackupProgress(0, manifest.mediaFileCount))
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = nextEntry(zip, isFirst = false) ?: break
            val name = entry.name
            checkName(name)
            when {
                name == BackupEntries.SETTINGS ->
                    settings =
                        try {
                            SettingsJson.decode(readText(zip, buffer))
                        } catch (e: BackupFormatException) {
                            throw RestoreRefusedException(RestoreRefusal.BadSettings, "settings.json could not be read", e)
                        }
                name == BackupEntries.ACCOUNT ->
                    account =
                        try {
                            BackupAccount.fromJson(readText(zip, buffer))
                        } catch (e: BackupFormatException) {
                            // The message names no value: BackupAccount.fromJson never puts one in.
                            throw RestoreRefusedException(RestoreRefusal.BadAccount, "account.json could not be read", e)
                        }
                name == BackupEntries.DATABASE && wantsLibrary -> {
                    copyEntry(zip, databaseFile, buffer, budget)
                    hasDatabase = true
                }
                name.startsWith(BackupEntries.MEDIA_PREFIX) && wantsLibrary -> {
                    if (!entry.isDirectory && name.length > BackupEntries.MEDIA_PREFIX.length) {
                        val target = inside(mediaRoot, name.removePrefix(BackupEntries.MEDIA_PREFIX))
                        when (extractMedia(zip, entry, target, buffer, budget, tap)) {
                            MediaOutcome.Written -> mediaFiles++
                            MediaOutcome.Damaged -> damagedMedia++
                            MediaOutcome.DamagedReadOnFromSource -> {
                                damagedMedia++
                                tap = RecentBytesTap(source)
                                zip = ZipInputStream(tap)
                            }
                        }
                        val done = mediaFiles + damagedMedia
                        onProgress(BackupProgress(done, maxOf(done, manifest.mediaFileCount)))
                    }
                }
                // A part a newer step 1 may add within the same format version: left out, counted for the log.
                else -> skipped++
            }
        }
        if (skipped > 0) log("Restore: $skipped entries of the file are not read by this version")
        if (damagedMedia > 0) log("Restore: $damagedMedia damaged box-art entries were left out")
        val decoded = settings ?: throw RestoreRefusedException(RestoreRefusal.Torn, "the file holds no settings.json")
        if (BackupPart.Account in manifest.includes && account == null) throw RestoreRefusedException(RestoreRefusal.Torn, "the file lists an account it does not hold")
        if (wantsLibrary && !hasDatabase) throw RestoreRefusedException(RestoreRefusal.Torn, "the file holds no database")
        val schema = if (wantsLibrary) checkDatabase(databaseFile, appSchemaVersion) else null
        return UnpackedBackup(
            manifest = manifest,
            settings = decoded.settings,
            unknownSettingKeys = decoded.unknownKeys,
            account = account,
            database = databaseFile.takeIf { wantsLibrary },
            databaseSchemaVersion = schema,
            media = mediaRoot.takeIf { wantsLibrary },
            mediaFiles = mediaFiles,
            damagedMediaFiles = damagedMedia,
        )
    }

    private enum class MediaOutcome {
        Written,

        /** Left out; the same zip stream reads on. */
        Damaged,

        /** Left out; [ZipResync] put the source back at the next entry, so a new zip stream must read on. */
        DamagedReadOnFromSource,
    }

    /** One box-art [entry] into [target], or left out (deleted) when it is damaged. */
    private suspend fun extractMedia(
        zip: ZipInputStream,
        entry: ZipEntry,
        target: File,
        buffer: ByteArray,
        budget: Budget,
        tap: RecentBytesTap,
    ): MediaOutcome {
        // Read now: ZipInputStream fills the sizes in from the data descriptor once it reaches it.
        val hasSizeInHeader = entry.compressedSize >= 0
        return try {
            copyEntry(zip, target, buffer, budget, isMedia = true)
            MediaOutcome.Written
        } catch (_: DamagedMediaException) {
            target.delete()
            if (skipDamagedMedia(entry, hasSizeInHeader, tap)) MediaOutcome.DamagedReadOnFromSource else MediaOutcome.Damaged
        }
    }

    /**
     * Gets past the damaged box-art [entry]: false when the same zip stream can go on, true when a
     * new one must read on from the source, which [ZipResync] put back at the next entry. A STORED entry has its size in its header,
     * so ZipInputStream has read exactly its bytes before the CRC failed. A deflated one written as
     * a stream has no size there: its end is found by its data descriptor ([ZipResync]). Anything
     * else (sizes in the header but deflated, from another zip tool) is not skipped: Torn.
     */
    private fun skipDamagedMedia(
        entry: ZipEntry,
        hasSizeInHeader: Boolean,
        tap: RecentBytesTap,
    ): Boolean {
        if (entry.method == ZipEntry.STORED) return false
        if (hasSizeInHeader) throw RestoreRefusedException(RestoreRefusal.Torn, "a damaged box-art entry whose end cannot be found")
        if (!ZipResync.skipDamagedEntry(tap, entry.name)) throw RestoreRefusedException(RestoreRefusal.Torn, "the file ends inside a damaged box-art entry")
        return true
    }

    /** A box-art entry that does not read (bad deflate data or CRC); [cause] is the zip's exception. */
    private class DamagedMediaException(
        cause: ZipException,
    ) : IOException("a box-art entry is damaged", cause)

    /**
     * The manifest, refused before anything else is read: another format, a newer format version
     * (checked before the fields, which a newer format may have changed), a database schema above
     * the app's, or an Everything file without a database.
     */
    private fun manifestOf(
        text: String,
        appSchemaVersion: Int,
    ): BackupManifest {
        val raw =
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw RestoreRefusedException(RestoreRefusal.NotABackup, "manifest.json is not JSON", e)
            }
        if (raw.optString("format") != BackupManifest.FORMAT_NAME) throw RestoreRefusedException(RestoreRefusal.NotABackup, "not a Plumemu backup")
        val formatVersion = raw.optInt("formatVersion", -1)
        if (formatVersion > BackupManifest.FORMAT_VERSION) throw RestoreRefusedException(RestoreRefusal.NewerFormat, "format version $formatVersion is newer than ${BackupManifest.FORMAT_VERSION}")
        val manifest =
            try {
                BackupManifest.fromJson(text)
            } catch (e: BackupFormatException) {
                throw RestoreRefusedException(RestoreRefusal.NotABackup, "manifest.json could not be read", e)
            }
        if (manifest.formatVersion < 1) throw RestoreRefusedException(RestoreRefusal.NotABackup, "format version ${manifest.formatVersion}")
        val schema = manifest.databaseSchemaVersion
        if (schema != null && schema > appSchemaVersion) throw RestoreRefusedException(RestoreRefusal.NewerDatabase, "database schema $schema is newer than $appSchemaVersion")
        if (manifest.kind == BackupKind.Everything && (schema == null || BackupPart.Database !in manifest.includes)) {
            throw RestoreRefusedException(RestoreRefusal.NotABackup, "an Everything file without a database")
        }
        return manifest
    }

    /**
     * Opens the extracted copy on its own: it must open, pass `quick_check`, be no newer than the
     * app (the manifest could say otherwise) and hold the library's `game` table. Returns its schema.
     */
    private fun checkDatabase(
        file: File,
        appSchemaVersion: Int,
    ): Int {
        val db =
            try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
            } catch (e: SQLiteException) {
                throw RestoreRefusedException(RestoreRefusal.BadDatabase, "the database does not open (${e.javaClass.simpleName})", e)
            }
        try {
            val check = db.rawQuery("PRAGMA quick_check", null).use { if (it.moveToFirst()) it.getString(0) else null }
            if (check != "ok") throw RestoreRefusedException(RestoreRefusal.BadDatabase, "the database did not pass quick_check")
            if (db.version > appSchemaVersion) throw RestoreRefusedException(RestoreRefusal.NewerDatabase, "database schema ${db.version} is newer than $appSchemaVersion")
            val hasGames = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'game'", null) > 0
            if (!hasGames) throw RestoreRefusedException(RestoreRefusal.BadDatabase, "the database has no game table")
            return db.version
        } catch (e: SQLiteException) {
            throw RestoreRefusedException(RestoreRefusal.BadDatabase, "the database could not be checked (${e.javaClass.simpleName})", e)
        } finally {
            db.close()
        }
    }

    /** The next entry; a zip that cannot be read is not a backup at the first entry, and torn after it. */
    private fun nextEntry(
        zip: ZipInputStream,
        isFirst: Boolean,
    ): ZipEntry? =
        try {
            zip.nextEntry
        } catch (e: IOException) {
            val refusal = if (isFirst) RestoreRefusal.NotABackup else RestoreRefusal.Torn
            throw RestoreRefusedException(refusal, "the zip could not be read (${e.javaClass.simpleName})", e)
        }

    /** Entry names come from the file: one that leaves the folder (`..`, a leading `/`, a `\`) is refused, never written. */
    private fun checkName(name: String) {
        val parts = name.split('/')
        if (name.startsWith("/") || name.contains('\\') || name.contains('\u0000') || parts.any { it == ".." }) {
            throw RestoreRefusedException(RestoreRefusal.NotABackup, "an entry name leaves the folder")
        }
    }

    /** [relative] under [root], checked again on the canonical paths. */
    private fun inside(
        root: File,
        relative: String,
    ): File {
        val file = File(root, relative)
        val rootPath = root.canonicalPath + File.separator
        if (!file.canonicalPath.startsWith(rootPath)) throw RestoreRefusedException(RestoreRefusal.NotABackup, "an entry name leaves the folder")
        return file
    }

    /** The entry's bytes as text, at most [MAX_TEXT_BYTES]: the JSON entries are small, a huge one is not ours. */
    private suspend fun readText(
        zip: ZipInputStream,
        buffer: ByteArray,
    ): String {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val read = readSome(zip, buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_TEXT_BYTES) throw RestoreRefusedException(RestoreRefusal.NotABackup, "a JSON entry is too large")
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private suspend fun copyEntry(
        zip: ZipInputStream,
        to: File,
        buffer: ByteArray,
        budget: Budget,
        isMedia: Boolean = false,
    ) {
        to.parentFile?.mkdirs()
        FileOutputStream(to).use { out ->
            while (true) {
                val read = readSome(zip, buffer, isMedia)
                if (read < 0) break
                budget.spend(read)
                out.write(buffer, 0, read)
            }
        }
    }

    /**
     * What the extraction may write: the manifest's database and box-art bytes plus [SPACE_MARGIN_BYTES]
     * (a file that holds more than it says is not ours, and could fill the device), and the free
     * space checked again every [RECHECK_BYTES] written (something else may fill it meanwhile).
     */
    private class Budget(
        private val folder: File,
        private val allowed: Long,
        private val freeBytes: (File) -> Long,
    ) {
        private var written = 0L
        private var sinceCheck = 0L

        fun spend(bytes: Int) {
            written += bytes
            sinceCheck += bytes
            if (written > allowed) throw RestoreRefusedException(RestoreRefusal.NotABackup, "the file holds more than its manifest says")
            if (sinceCheck >= RECHECK_BYTES) {
                sinceCheck = 0
                if (freeBytes(folder) < SPACE_MARGIN_BYTES) throw RestoreRefusedException(RestoreRefusal.NoSpace, "the free space ran out while extracting")
            }
        }
    }

    /**
     * A read error of the zip (an early end, a bad CRC) is a torn file; a write error is the caller's
     * I/O failure. In a box-art entry ([isMedia]) a zip error (bad data, bad CRC) is a damaged
     * entry the caller leaves out; an early end of the file stays torn.
     */
    private suspend fun readSome(
        zip: ZipInputStream,
        buffer: ByteArray,
        isMedia: Boolean = false,
    ): Int {
        currentCoroutineContext().ensureActive()
        return try {
            zip.read(buffer)
        } catch (e: ZipException) {
            // A STORED entry cut short by the file's end throws this too; the next entry then fails to read: Torn.
            if (isMedia) throw DamagedMediaException(e)
            throw RestoreRefusedException(RestoreRefusal.Torn, "the zip ends early or is damaged (${e.javaClass.simpleName})", e)
        } catch (e: IOException) {
            throw RestoreRefusedException(RestoreRefusal.Torn, "the zip ends early or is damaged (${e.javaClass.simpleName})", e)
        }
    }

    private const val BUFFER_BYTES = 64 * 1024

    private const val MAX_TEXT_BYTES = 1024 * 1024
    private const val BYTES_PER_MB = 1024 * 1024L

    /** Room for the database's migration and the swap's small files. */
    private const val SPACE_MARGIN_BYTES = 16 * BYTES_PER_MB

    private const val RECHECK_BYTES = 8 * BYTES_PER_MB
}
