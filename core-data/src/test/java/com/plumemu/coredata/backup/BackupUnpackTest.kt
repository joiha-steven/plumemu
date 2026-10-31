package com.plumemu.coredata.backup

import android.database.sqlite.SQLiteDatabase
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SoundLevel
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restore's reading and checking: a whole file unpacks with its
 * parts checked; every file that must be refused is refused with the right reason, before any of
 * the device's data could change (the unpack only ever writes into its own folder).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupUnpackTest {
    @get:Rule val tmp = TemporaryFolder()

    private val settings = PlumSettings(advanced = AdvancedSettings(sounds = SoundLevel.Off))
    private val account = BackupAccount("plumtester", "s3cret-pass", isRefused = false)
    private val into: File get() = File(tmp.root, "work")

    /** A real Everything backup of a two-game library with two box-art files, as Back up… writes it; made once per test. */
    private val everything: File by lazy { everythingZip() }

    private fun everythingZip(): File {
        val source = fileDatabase(File(tmp.root, "source/plumemu.db"))
        source.addGames(listOf("Zelda", "Mario"))
        val snapshot = snapshotOffMain(source, File(tmp.root, "snap/plumemu.db"))
        source.close()
        val root = File(tmp.root, "source-media")
        File(root, "1234").mkdirs()
        File(root, "1234/box-front-small-120x168.webp").writeBytes(ByteArray(3_000) { it.toByte() })
        File(root, "98/box-front-large-600x840.webp").also { it.parentFile?.mkdirs() }.writeBytes(ByteArray(70_000) { (it % 251).toByte() })
        val media = BackupZip.listMedia(root)
        val manifest =
            BackupManifest(
                kind = BackupKind.Everything,
                createdAtMillis = 1_792_756_800_000L,
                appVersionName = "test",
                appVersionCode = 1,
                includes = listOf(BackupPart.Settings, BackupPart.Account, BackupPart.Database, BackupPart.Media),
                databaseSchemaVersion = snapshot.schemaVersion,
                gameCount = snapshot.gameCount,
                databaseBytes = snapshot.file.length(),
                mediaFileCount = media.size,
                mediaBytes = media.sumOf { it.size },
                mediaRoot = root.absolutePath,
            )
        val out = File(tmp.root, "backup.zip")
        out.outputStream().use { runBlocking { BackupZip.write(BackupContents(manifest, settings, account, snapshot.file, media), it) } }
        return out
    }

    private fun settingsManifest(formatVersion: Int = BackupManifest.FORMAT_VERSION) =
        BackupManifest(
            formatVersion = formatVersion,
            kind = BackupKind.Settings,
            createdAtMillis = 0,
            appVersionName = "test",
            appVersionCode = 1,
            includes = listOf(BackupPart.Settings, BackupPart.Account),
            databaseSchemaVersion = null,
            gameCount = null,
            databaseBytes = null,
            mediaFileCount = 0,
            mediaBytes = 0,
            mediaRoot = null,
        )

    /** A zip of [entries] in that order, deflated, as any zip tool would make it. */
    private fun zipOf(vararg entries: Pair<String, ByteArray>): File {
        val out = File(tmp.root, "crafted-${System.nanoTime()}.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out
    }

    private fun json(o: JSONObject) = o.toString().toByteArray()

    private fun settingsEntry() = BackupEntries.SETTINGS to json(SettingsJson.encode(settings))

    private fun accountEntry() = BackupEntries.ACCOUNT to json(account.toJson())

    private fun unpack(
        file: File,
        freeBytes: (File) -> Long = { Long.MAX_VALUE },
    ): UnpackedBackup = runBlocking { file.inputStream().use { BackupUnpack.unpack(it, into, PlumDatabase.SCHEMA_VERSION, freeBytes) } }

    private fun refusalOf(file: File, freeBytes: (File) -> Long = { Long.MAX_VALUE }): RestoreRefusal {
        try {
            unpack(file, freeBytes)
        } catch (e: RestoreRefusedException) {
            return e.refusal
        }
        fail("the file was not refused")
        throw AssertionError()
    }

    @Test
    fun anEverythingFileUnpacksWithItsCheckedDatabaseTheBoxArtTheSettingsAndTheAccount() {
        val unpacked = unpack(everything)
        assertEquals(BackupKind.Everything, unpacked.manifest.kind)
        assertEquals(settings, unpacked.settings)
        assertEquals(account, unpacked.account)
        assertEquals(listOf("Mario", "Zelda"), titlesIn(requireNotNull(unpacked.database)))
        assertEquals(13, unpacked.databaseSchemaVersion)
        assertEquals(2, unpacked.mediaFiles)
        val media = requireNotNull(unpacked.media)
        assertArrayEquals(ByteArray(3_000) { it.toByte() }, File(media, "1234/box-front-small-120x168.webp").readBytes())
        assertEquals(70_000L, File(media, "98/box-front-large-600x840.webp").length())
    }

    @Test
    fun aSettingsFileUnpacksWithoutALibrary() {
        val unpacked = unpack(zipOf(BackupEntries.MANIFEST to json(settingsManifest().toJson()), settingsEntry(), accountEntry()))
        assertEquals(BackupKind.Settings, unpacked.manifest.kind)
        assertNull(unpacked.database)
        assertNull(unpacked.media)
        assertEquals(account, unpacked.account)
    }

    @Test
    fun aFileFromANewerPlumemuIsRefusedBeforeItsFieldsAreRead() {
        // A newer format may have changed any field: only "format" and "formatVersion" are read.
        val newer = JSONObject().put("format", BackupManifest.FORMAT_NAME).put("formatVersion", BackupManifest.FORMAT_VERSION + 1).put("kind", "something-new")
        assertEquals(RestoreRefusal.NewerFormat, refusalOf(zipOf(BackupEntries.MANIFEST to json(newer), settingsEntry())))
    }

    @Test
    fun aDatabaseFromANewerSchemaIsRefusedByTheManifestAndByTheFileItself() {
        val manifest = JSONObject(everythingZipManifestText()).apply { getJSONObject("database").put("schemaVersion", PlumDatabase.SCHEMA_VERSION + 1) }
        assertEquals(RestoreRefusal.NewerDatabase, refusalOf(zipOf(BackupEntries.MANIFEST to json(manifest), settingsEntry())))

        // The manifest says 6 but the file is a newer one: the file's own user_version is checked too.
        val newerFile = File(tmp.root, "newer.db")
        SQLiteDatabase.openOrCreateDatabase(newerFile, null).use { db ->
            db.execSQL("CREATE TABLE game (id INTEGER PRIMARY KEY)")
            db.version = PlumDatabase.SCHEMA_VERSION + 1
        }
        val lying = JSONObject(everythingZipManifestText())
        assertEquals(
            RestoreRefusal.NewerDatabase,
            refusalOf(zipOf(BackupEntries.MANIFEST to json(lying), settingsEntry(), BackupEntries.DATABASE to newerFile.readBytes(), accountEntry())),
        )
    }

    @Test
    fun aTornFileIsRefused() {
        val whole = everything
        val bytes = whole.readBytes()
        // Cut in the middle of the box art: the entry being read ends early.
        val torn = File(tmp.root, "torn.zip").apply { writeBytes(bytes.copyOf(bytes.size / 2)) }
        assertEquals(RestoreRefusal.Torn, refusalOf(torn))
        // Cut just before the account (the last entry): every entry read is whole, but the listed account is missing.
        val withoutAccount = zipOf(BackupEntries.MANIFEST to json(JSONObject(everythingZipManifestText())), settingsEntry(), BackupEntries.DATABASE to snapshotBytes())
        assertEquals(RestoreRefusal.Torn, refusalOf(withoutAccount))
    }

    @Test
    fun aDamagedEntryIsRefusedByItsChecksum() {
        val bytes = everything.readBytes()
        // Flip a byte inside the settings entry's deflated data (after the manifest): its CRC no longer matches.
        val at = indexOf(bytes, BackupEntries.SETTINGS.toByteArray()) + BackupEntries.SETTINGS.length + 40
        bytes[at] = (bytes[at].toInt() xor 0x55).toByte()
        val damaged = File(tmp.root, "damaged.zip").apply { writeBytes(bytes) }
        val refusal = refusalOf(damaged)
        assertTrue("a damaged entry is torn or unreadable settings, never accepted: $refusal", refusal == RestoreRefusal.Torn || refusal == RestoreRefusal.BadSettings)
    }

    @Test
    fun settingsOrAnAccountThatDoNotParseAreRefused() {
        val manifest = BackupEntries.MANIFEST to json(settingsManifest().toJson())
        assertEquals(RestoreRefusal.BadSettings, refusalOf(zipOf(manifest, BackupEntries.SETTINGS to "not json".toByteArray(), accountEntry())))
        assertEquals(RestoreRefusal.BadSettings, refusalOf(zipOf(manifest, BackupEntries.SETTINGS to "{}".toByteArray(), accountEntry())))
        assertEquals(RestoreRefusal.BadAccount, refusalOf(zipOf(manifest, settingsEntry(), BackupEntries.ACCOUNT to "{\"service\":\"screenscraper\"".toByteArray())))
        assertEquals(RestoreRefusal.BadAccount, refusalOf(zipOf(manifest, settingsEntry(), BackupEntries.ACCOUNT to "{\"service\":\"other\",\"username\":\"a\"}".toByteArray())))
    }

    @Test
    fun aDatabaseThatFailsItsCheckIsRefused() {
        val good = snapshotBytes()
        // Overwrite the second page (a table's b-tree) with noise: the file still opens, quick_check does not pass.
        val broken = good.copyOf()
        for (i in 4096 until minOf(broken.size, 8192)) broken[i] = (i * 31).toByte()
        val zip = zipOf(BackupEntries.MANIFEST to json(JSONObject(everythingZipManifestText())), settingsEntry(), BackupEntries.DATABASE to broken, accountEntry())
        assertEquals(RestoreRefusal.BadDatabase, refusalOf(zip))
        // Not a database at all.
        val noise = zipOf(BackupEntries.MANIFEST to json(JSONObject(everythingZipManifestText())), settingsEntry(), BackupEntries.DATABASE to ByteArray(8192) { 7 }, accountEntry())
        assertEquals(RestoreRefusal.BadDatabase, refusalOf(noise))
    }

    @Test
    fun anythingButAPlumemuBackupIsRefused() {
        assertEquals("not a zip", RestoreRefusal.NotABackup, refusalOf(File(tmp.root, "plain.txt").apply { writeText("hello") }))
        assertEquals("manifest not first", RestoreRefusal.NotABackup, refusalOf(zipOf(settingsEntry(), BackupEntries.MANIFEST to json(settingsManifest().toJson()))))
        val other = JSONObject(settingsManifest().toJson().toString()).put("format", "someone-else")
        assertEquals("another format", RestoreRefusal.NotABackup, refusalOf(zipOf(BackupEntries.MANIFEST to json(other), settingsEntry())))
    }

    @Test
    fun anEntryThatWouldLeaveTheFolderIsRefusedAndNothingIsWrittenOutsideIt() {
        val outside = File(tmp.root, "evil.txt")
        val manifest = JSONObject(everythingZipManifestText())
        assertEquals(RestoreRefusal.NotABackup, refusalOf(zipOf(BackupEntries.MANIFEST to json(manifest), settingsEntry(), "media/../../evil.txt" to "x".toByteArray())))
        assertFalse(outside.exists())
        assertEquals(RestoreRefusal.NotABackup, refusalOf(zipOf(BackupEntries.MANIFEST to json(manifest), settingsEntry(), "/etc/evil.txt" to "x".toByteArray())))
    }

    @Test
    fun notEnoughFreeSpaceIsRefusedBeforeExtracting() {
        assertEquals(RestoreRefusal.NoSpace, refusalOf(everything) { 1_000L })
    }

    /** A file whose manifest lies about its size is stopped once it writes more than it said (plus 16 MB), not when the disk is full. */
    @Test
    fun aFileHoldingMoreThanItsManifestSaysIsRefusedWhileExtracting() {
        val manifest = JSONObject(everythingZipManifestText()).apply { getJSONObject("media").put("files", 1).put("bytes", 10) }
        val big = zipOf(BackupEntries.MANIFEST to json(manifest), settingsEntry(), BackupEntries.DATABASE to snapshotBytes(), "media/1/huge.webp" to ByteArray(40 * 1024 * 1024), accountEntry())
        assertEquals(RestoreRefusal.NotABackup, refusalOf(big))
    }

    @Test
    fun freeSpaceRunningOutWhileExtractingIsRefused() {
        val manifest = JSONObject(everythingZipManifestText()).apply { getJSONObject("media").put("files", 1).put("bytes", 30L * 1024 * 1024) }
        val big = zipOf(BackupEntries.MANIFEST to json(manifest), settingsEntry(), BackupEntries.DATABASE to snapshotBytes(), "media/1/big.webp" to ByteArray(20 * 1024 * 1024), accountEntry())
        var asks = 0
        // Enough at the start, then something else fills the disk.
        assertEquals(RestoreRefusal.NoSpace, refusalOf(big) { if (asks++ == 0) Long.MAX_VALUE else 1_000L })
    }

    /**
     * A Zip64 file (65,536 entries or more need Zip64's end records; newer devices write them for
     * very large backups) is read like any other.
     */
    @Test
    fun aZip64FileIsAccepted() {
        val count = ZIP64_ENTRIES
        val manifest = JSONObject(everythingZipManifestText()).apply { getJSONObject("media").put("files", count).put("bytes", count) }
        val db = snapshotBytes()
        val out = File(tmp.root, "zip64.zip")
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(BackupEntries.MANIFEST, json(manifest))
            put(BackupEntries.SETTINGS, json(SettingsJson.encode(settings)))
            put(BackupEntries.DATABASE, db)
            val one = byteArrayOf(1)
            for (i in 0 until count) put("media/${i / 1000}/$i.webp", one)
            put(BackupEntries.ACCOUNT, json(account.toJson()))
        }
        assertTrue("the file really has Zip64's end record", indexOf(out.readBytes().let { it.copyOfRange(maxOf(0, it.size - 200), it.size) }, byteArrayOf(0x50, 0x4b, 0x06, 0x06)) >= 0)
        val unpacked = unpack(out)
        assertEquals(count, unpacked.mediaFiles)
        assertEquals(account, unpacked.account)
    }

    /** Box art for the damaged-entry tests: noise does not deflate, like WebP. Sizes from the Thor's damaged backup. */
    private fun picture(
        seed: Int,
        size: Int,
    ): ByteArray = ByteArray(size).also { java.util.Random(seed.toLong()).nextBytes(it) }

    private val thorPictures =
        linkedMapOf(
            "media/107882/box-front-grid-560x400.webp" to picture(1, 39_418),
            "media/107882/box-front-large-1240x1080.webp" to picture(2, 94_250),
            "media/107882/support-grid-560x400.webp" to picture(3, 33_556),
            "media/107882/support-large-1240x1080.webp" to picture(4, 40_204),
        )

    private fun manifestFor(pictures: Map<String, ByteArray>): JSONObject =
        JSONObject(everythingZipManifestText()).apply { getJSONObject("media").put("files", pictures.size).put("bytes", pictures.values.sumOf { it.size.toLong() }) }

    /**
     * An Everything file as build 380 wrote it on the Thor: every entry deflated with a
     * data descriptor, the level changed to no compression after the database, and the first box-art
     * entry damaged by it ([ThorLikeZipOutputStream]).
     */
    private fun thorBackup(pictures: Map<String, ByteArray>): File {
        val out = File(tmp.root, "thor-${System.nanoTime()}.zip")
        ThorLikeZipOutputStream(out.outputStream().buffered()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put(BackupEntries.MANIFEST, json(manifestFor(pictures)))
            put(BackupEntries.SETTINGS, json(SettingsJson.encode(settings)))
            put(BackupEntries.DATABASE, snapshotBytes())
            zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
            for ((name, bytes) in pictures) put(name, bytes)
            zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
            put(BackupEntries.ACCOUNT, json(account.toJson()))
        }
        return out
    }

    /** [file] with the byte [offset] bytes after the local header's name of [entry] changed. */
    private fun damaged(
        file: File,
        entry: String,
        offset: Int,
    ): File {
        val bytes = file.readBytes()
        val at = indexOf(bytes, entry.toByteArray()) + entry.length + offset
        bytes[at] = (bytes[at].toInt() xor 0x55).toByte()
        return File(tmp.root, "damaged-${System.nanoTime()}.zip").apply { writeBytes(bytes) }
    }

    private fun assertRestoredWithout(
        unpacked: UnpackedBackup,
        pictures: Map<String, ByteArray>,
        left: Set<String>,
    ) {
        assertEquals(settings, unpacked.settings)
        assertEquals(account, unpacked.account)
        assertEquals(listOf("Mario", "Zelda"), titlesIn(requireNotNull(unpacked.database)))
        assertEquals(pictures.size - left.size, unpacked.mediaFiles)
        assertEquals(left.size, unpacked.damagedMediaFiles)
        val media = requireNotNull(unpacked.media)
        for ((name, bytes) in pictures) {
            val file = File(media, name.removePrefix(BackupEntries.MEDIA_PREFIX))
            if (name in left) assertFalse("$name left out, no half file", file.exists()) else assertArrayEquals(name, bytes, file.readBytes())
        }
    }

    /**
     * The Thor's own case: the first box-art entry after the database does not inflate. Box art is a
     * cache, so Restore leaves that picture out and restores the library, the settings, the account
     * and every other picture; it does not refuse the file.
     */
    @Test
    fun aBackupFromTheThorWithItsDamagedBoxArtEntryRestoresEverythingElse() {
        val file = thorBackup(thorPictures)
        val damagedName = thorPictures.keys.first()
        // The file really holds the Thor's damage: that entry does not inflate.
        val unreadable =
            runCatching {
                java.util.zip.ZipFile(file).use { z -> z.getInputStream(z.getEntry(damagedName)).readBytes() }
            }.exceptionOrNull()
        assertTrue("the first box-art entry is damaged: $unreadable", unreadable is java.util.zip.ZipException)
        val logged = mutableListOf<String>()
        val unpacked = runBlocking { file.inputStream().use { BackupUnpack.unpack(it, into, PlumDatabase.SCHEMA_VERSION, { Long.MAX_VALUE }, { line -> logged += line }) } }
        assertRestoredWithout(unpacked, thorPictures, setOf(damagedName))
        assertTrue(logged.toString(), logged.any { it.contains("1 damaged box-art entries were left out") })
    }

    /**
     * Damage anywhere in a deflated box-art entry of an older file: at its start, deep inside an entry
     * longer than what is kept of the read-ahead (64 KB), and right before the account.
     */
    @Test
    fun damagedBoxArtAnywhereInAnOlderFileIsLeftOut() {
        val pictures =
            linkedMapOf(
                "media/1/a.webp" to picture(5, 3_000),
                "media/1/b.webp" to picture(6, 200_000),
                "media/2/c.webp" to picture(7, 33_556),
                "media/3/d.webp" to picture(8, 40_204),
            )
        // Every entry deflated with a data descriptor, as build 380 wrote them, nothing damaged yet.
        val whole = zipOf(BackupEntries.MANIFEST to json(manifestFor(pictures)), settingsEntry(), BackupEntries.DATABASE to snapshotBytes(), *pictures.toList().toTypedArray(), accountEntry())
        val file = damaged(damaged(damaged(whole, "media/1/a.webp", 2), "media/1/b.webp", 150_000), "media/3/d.webp", 2)
        assertRestoredWithout(unpack(file), pictures, setOf("media/1/a.webp", "media/1/b.webp", "media/3/d.webp"))
    }

    /**
     * An older backup made while signed out has no account entry: its last picture is followed by
     * the central directory, not another entry. A damaged last picture is left out there too.
     */
    @Test
    fun aDamagedLastPictureOfAnOlderFileWithoutAnAccountIsLeftOut() {
        val pictures = linkedMapOf("media/1/a.webp" to picture(9, 20_000), "media/2/b.webp" to picture(10, 30_000))
        val manifest =
            manifestFor(pictures).apply {
                put("includes", org.json.JSONArray(listOf("settings", "database", "media")))
                put("account", JSONObject.NULL)
            }
        val whole = zipOf(BackupEntries.MANIFEST to json(manifest), settingsEntry(), BackupEntries.DATABASE to snapshotBytes(), *pictures.toList().toTypedArray())
        val unpacked = unpack(damaged(whole, "media/2/b.webp", 2))
        assertNull(unpacked.account)
        assertEquals(1, unpacked.mediaFiles)
        assertEquals(1, unpacked.damagedMediaFiles)
        assertArrayEquals(pictures.getValue("media/1/a.webp"), File(requireNotNull(unpacked.media), "1/a.webp").readBytes())
        assertFalse(File(unpacked.media, "2/b.webp").exists())
    }

    /**
     * Many damaged pictures in one older file: each is skipped from the same source stream, never
     * through a chain of streams that grows with every skip (the stack under the file's reads would
     * grow with it, and every read would walk it).
     */
    @Test
    fun thousandsOfDamagedPicturesAreSkippedWithoutGrowingTheReadChain() {
        val count = 2_000
        val manifestPictures = (0 until count).associate { "media/$it/p.webp" to ByteArray(2_000) }
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun put(name: String, bytes: ByteArray): Int {
                zip.putNextEntry(ZipEntry(name))
                val dataStart = out.size()
                zip.write(bytes)
                zip.closeEntry()
                return dataStart
            }
            put(BackupEntries.MANIFEST, json(manifestFor(manifestPictures)))
            put(BackupEntries.SETTINGS, json(SettingsJson.encode(settings)))
            put(BackupEntries.DATABASE, snapshotBytes())
            val starts = (0 until count).map { put("media/$it/p.webp", picture(it, 2_000)) }
            put(BackupEntries.ACCOUNT, json(account.toJson()))
            zip.finish()
            val bytes = out.toByteArray()
            // Every picture but each hundredth: its first deflate byte made an invalid block type, as on the Thor.
            for ((i, at) in starts.withIndex()) if (i % 100 != 0) bytes[at] = 0xb7.toByte()
            out.reset()
            out.write(bytes)
        }
        var firstDepth = -1
        var deepest = 0
        val source =
            object : java.io.FilterInputStream(out.toByteArray().inputStream()) {
                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int {
                    val depth = Thread.currentThread().stackTrace.size
                    if (firstDepth < 0) firstDepth = depth
                    deepest = maxOf(deepest, depth)
                    return super.read(b, off, len)
                }
            }
        val started = System.nanoTime()
        val unpacked = runBlocking { BackupUnpack.unpack(source, into, PlumDatabase.SCHEMA_VERSION, { Long.MAX_VALUE }) }
        val seconds = (System.nanoTime() - started) / 1e9
        assertEquals(count / 100, unpacked.mediaFiles)
        assertEquals(count - count / 100, unpacked.damagedMediaFiles)
        assertEquals(account, unpacked.account)
        assertTrue("the reads went ${deepest - firstDepth} frames deeper than the first", deepest - firstDepth < 20)
        org.junit.Assume.assumeTrue("absolute time budgets are checked on the development machine, not on shared CI runners", System.getProperty("plumemu.timingBudgets") != "false")
        assertTrue("took $seconds s", seconds < 60)
    }

    /** A file Back up… writes now (box art STORED): a picture whose CRC fails is left out, the rest restored. */
    @Test
    fun aStoredBoxArtEntryWhoseChecksumFailsIsLeftOut() {
        val file = damaged(everything, "98/box-front-large-600x840.webp", 1_000)
        val unpacked = unpack(file)
        assertEquals(1, unpacked.mediaFiles)
        assertEquals(1, unpacked.damagedMediaFiles)
        assertFalse(File(requireNotNull(unpacked.media), "98/box-front-large-600x840.webp").exists())
        assertArrayEquals(ByteArray(3_000) { it.toByte() }, File(unpacked.media, "1234/box-front-small-120x168.webp").readBytes())
        assertEquals(account, unpacked.account)
        assertEquals(listOf("Mario", "Zelda"), titlesIn(requireNotNull(unpacked.database)))
    }

    /** Only box art may be left out: a damaged database, settings or account still refuses the whole file. */
    @Test
    fun aDamagedDatabaseSettingsOrAccountEntryStillRefusesTheFile() {
        assertEquals("database", RestoreRefusal.Torn, refusalOf(damaged(everything, BackupEntries.DATABASE, 200)))
        val settingsRefusal = refusalOf(damaged(everything, BackupEntries.SETTINGS, 40))
        assertTrue("settings: $settingsRefusal", settingsRefusal == RestoreRefusal.Torn || settingsRefusal == RestoreRefusal.BadSettings)
        val accountRefusal = refusalOf(damaged(everything, BackupEntries.ACCOUNT, 20))
        assertTrue("account: $accountRefusal", accountRefusal == RestoreRefusal.Torn || accountRefusal == RestoreRefusal.BadAccount)
        // The same in an older file, where the database follows the settings and box art follows it.
        val old = thorBackup(thorPictures)
        assertEquals("older file's database", RestoreRefusal.Torn, refusalOf(damaged(old, BackupEntries.DATABASE, 200)))
    }

    private fun everythingZipManifestText(): String =
        java.util.zip.ZipFile(everything).use { z -> z.getInputStream(z.getEntry(BackupEntries.MANIFEST)).readBytes().toString(Charsets.UTF_8) }

    /** The checked database copy inside [everything]. */
    private fun snapshotBytes(): ByteArray = java.util.zip.ZipFile(everything).use { z -> z.getInputStream(z.getEntry(BackupEntries.DATABASE)).readBytes() }

    private fun indexOf(
        haystack: ByteArray,
        needle: ByteArray,
    ): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    private companion object {
        const val ZIP64_ENTRIES = 65_600
    }
}
