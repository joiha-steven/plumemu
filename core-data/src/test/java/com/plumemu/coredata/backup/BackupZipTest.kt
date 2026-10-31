package com.plumemu.coredata.backup

import com.plumemu.coredata.settings.AdvancedSettings
import com.plumemu.coredata.settings.PlumSettings
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The zip itself: what each kind holds, streamed, and stopped by Cancel. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupZipTest {
    @get:Rule val tmp = TemporaryFolder()

    private val settings = PlumSettings(advanced = AdvancedSettings(animations = false))
    private val account = BackupAccount("plumtester", "s3cret", isRefused = false)

    private fun mediaRoot(): File {
        val root = File(tmp.root, "screenscraper-media")
        File(root, "1234").mkdirs()
        File(root, "1234/box-front-small-120x168.webp").writeBytes(ByteArray(3_000) { it.toByte() })
        File(root, "1234/support-small-120x120.webp").writeBytes(ByteArray(10) { 7 })
        File(root, "98/box-front-large-600x840.webp").also { it.parentFile?.mkdirs() }.writeBytes(ByteArray(200_000) { (it % 251).toByte() })
        // A run's unfinished downloads are never in a backup.
        File(root, "download-1.part").writeBytes(ByteArray(5))
        File(root, "1234/box-front-small-120x168.webp.tmp").writeBytes(ByteArray(5))
        return root
    }

    private fun manifest(
        kind: BackupKind,
        media: List<MediaFile>,
        root: File?,
        schema: Int?,
    ) = BackupManifest(
        kind = kind,
        createdAtMillis = 0,
        appVersionName = "test",
        appVersionCode = 1,
        includes = listOf(BackupPart.Settings, BackupPart.Account),
        databaseSchemaVersion = schema,
        gameCount = null,
        databaseBytes = null,
        mediaFileCount = media.size,
        mediaBytes = media.sumOf { it.size },
        mediaRoot = root?.absolutePath,
    )

    /** Every entry of a zip, by name, in order. */
    private fun entries(zip: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(zip.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                out[entry.name] = input.readBytes()
            }
        }
        return out
    }

    @Test
    fun mediaIsListedByPathWithoutUnfinishedDownloads() {
        val listed = BackupZip.listMedia(mediaRoot())
        assertEquals(listOf("1234/box-front-small-120x168.webp", "1234/support-small-120x120.webp", "98/box-front-large-600x840.webp"), listed.map { it.relativePath })
        assertEquals(listOf(3_000L, 10L, 200_000L), listed.map { it.size })
        assertEquals(emptyList<MediaFile>(), BackupZip.listMedia(File(tmp.root, "none")))
    }

    @Test
    fun everythingHoldsTheManifestFirstThenSettingsDatabaseMediaAndTheAccountLast() {
        val db = fileDatabase(File(tmp.root, "plumemu.db"))
        db.addGames(listOf("Zelda", "Mario"))
        val snapshot = snapshotOffMain(db, File(tmp.root, "snapshot.db"))
        db.close()
        val root = mediaRoot()
        val media = BackupZip.listMedia(root)
        val out = ByteArrayOutputStream()
        val progress = mutableListOf<BackupProgress>()
        val written =
            runBlocking {
                BackupZip.write(BackupContents(manifest(BackupKind.Everything, media, root, snapshot.schemaVersion), settings, account, snapshot.file, media), out) { progress += it }
            }
        assertEquals(3 to 0, written.mediaWritten to written.mediaMissing)
        val zip = entries(out.toByteArray())
        assertEquals(
            listOf(
                "manifest.json",
                "settings.json",
                "database/plumemu.db",
                "media/1234/box-front-small-120x168.webp",
                "media/1234/support-small-120x120.webp",
                "media/98/box-front-large-600x840.webp",
                // Last: a torn file is unlikely to hold the password.
                "account.json",
            ),
            zip.keys.toList(),
        )
        assertEquals(BackupKind.Everything, BackupManifest.fromJson(zip.getValue("manifest.json").decodeToString()).kind)
        assertEquals(settings, SettingsJson.decode(zip.getValue("settings.json").decodeToString()).settings)
        assertEquals(account, BackupAccount.fromJson(zip.getValue("account.json").decodeToString()))
        val restored = File(tmp.root, "restored.db").apply { writeBytes(zip.getValue("database/plumemu.db")) }
        assertEquals(listOf("Mario", "Zelda"), titlesIn(restored))
        assertArrayEquals(File(root, "98/box-front-large-600x840.webp").readBytes(), zip.getValue("media/98/box-front-large-600x840.webp"))
        assertEquals(BackupProgress(3, 3), progress.last())
    }

    @Test
    fun settingsHoldNoDatabaseAndNoMedia() {
        val out = ByteArrayOutputStream()
        runBlocking { BackupZip.write(BackupContents(manifest(BackupKind.Settings, emptyList(), null, null), settings, account, null, emptyList()), out) }
        val names = entries(out.toByteArray()).keys
        assertEquals(listOf("manifest.json", "settings.json", "account.json"), names.toList())
        assertFalse(names.any { it.startsWith("database/") || it.startsWith("media/") })
    }

    @Test
    fun withoutAnAccountThereIsNoAccountEntry() {
        val out = ByteArrayOutputStream()
        runBlocking { BackupZip.write(BackupContents(manifest(BackupKind.Settings, emptyList(), null, null), settings, null, null, emptyList()), out) }
        assertEquals(setOf("manifest.json", "settings.json"), entries(out.toByteArray()).keys)
    }

    @Test
    fun aFileDeletedAfterItWasListedIsLeftOutAndCounted() {
        val root = mediaRoot()
        val media = BackupZip.listMedia(root)
        File(root, "1234/support-small-120x120.webp").delete()
        val out = ByteArrayOutputStream()
        val written = runBlocking { BackupZip.write(BackupContents(manifest(BackupKind.Everything, media, root, null), settings, null, null, media), out) }
        assertEquals(2 to 1, written.mediaWritten to written.mediaMissing)
        assertFalse(entries(out.toByteArray()).containsKey("media/1234/support-small-120x120.webp"))
    }

    @Test
    fun afterAFailureNothingMoreReachesTheStreamAndItIsLeftOpen() {
        val root = mediaRoot()
        val media = BackupZip.listMedia(root)
        var closed = false
        var failNow = false
        var triesAfterFailure = 0
        val out =
            object : java.io.OutputStream() {
                override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) {
                    if (failNow) {
                        triesAfterFailure++
                        throw java.io.IOException("no space left")
                    }
                    // The first buffer that reaches the document is the last that may.
                    failNow = true
                }

                override fun close() {
                    closed = true
                }
            }
        val failed = runCatching { runBlocking { BackupZip.write(BackupContents(manifest(BackupKind.Everything, media, root, null), settings, account, null, media), out) } }
        assertTrue(failed.exceptionOrNull() is java.io.IOException)
        assertFalse("the caller closes its stream, not the zip", closed)
        assertEquals("only the failing write: closing the zip wrote no end of zip after it", 1, triesAfterFailure)
    }

    @Test
    fun cancelStopsTheZipBeforeTheNextFile() {
        val root = mediaRoot()
        val media = BackupZip.listMedia(root)
        val out = ByteArrayOutputStream()
        var cancelled = false
        runBlocking {
            val job =
                async {
                    BackupZip.write(BackupContents(manifest(BackupKind.Everything, media, root, null), settings, null, null, media), out) {
                        // Cancel as the first file is done, as the person's Cancel would.
                        if (it.filesDone == 1) coroutineContext[Job]?.cancel()
                    }
                }
            try {
                job.await()
            } catch (e: CancellationException) {
                cancelled = true
            }
        }
        assertTrue(cancelled)
        assertTrue("the second and third files were not written", out.size() < 200_000)
    }
    /** An Everything backup of a two-game library and [mediaRoot]'s box art, written through [newZip]. */
    private fun writeEverything(newZip: (java.io.OutputStream) -> java.util.zip.ZipOutputStream = { java.util.zip.ZipOutputStream(it) }): Pair<ByteArray, BackupWritten> {
        val db = fileDatabase(File(tmp.root, "plumemu.db"))
        db.addGames(listOf("Zelda", "Mario"))
        val snapshot = snapshotOffMain(db, File(tmp.root, "snapshot.db"))
        db.close()
        val root = mediaRoot()
        val media = BackupZip.listMedia(root)
        val out = ByteArrayOutputStream()
        val contents = BackupContents(manifest(BackupKind.Everything, media, root, snapshot.schemaVersion), settings, account, snapshot.file, media)
        val written = runBlocking { BackupZip.write(contents, out, {}, newZip) }
        return out.toByteArray() to written
    }

    private fun verify(
        zip: ByteArray,
        written: BackupWritten,
    ) = runBlocking { BackupZip.verify(zip.inputStream(), written) }

    /** The check's message for a damaged [zip]; fails the test when it passes. */
    private fun damageOf(
        zip: ByteArray,
        written: BackupWritten,
    ): String {
        try {
            verify(zip, written)
        } catch (e: BackupDamagedException) {
            return e.message.orEmpty()
        }
        fail("the damaged file passed the check")
        throw AssertionError()
    }

    private fun assertDamaged(
        zip: ByteArray,
        written: BackupWritten,
    ) {
        damageOf(zip, written)
    }

    /**
     * The Thor's damaged backup (build 380): the first box-art entry after the database
     * did not inflate. Its bytes were the level-6 deflate of the picture written twice with the
     * first 512 bytes missing: the level change between entries (`setLevel(NO_COMPRESSION)` after
     * the database) went wrong in the device's Deflater. [ThorLikeZipOutputStream] damages an entry
     * that way after a level change, as the JVM's Deflater does not. The writer must never ask for
     * one, and every entry must then read back whole.
     */
    @Test
    fun theCompressionLevelIsNeverChangedBetweenEntriesSoNoEntryIsDamaged() {
        var zipStream: ThorLikeZipOutputStream? = null
        val (zip, written) = writeEverything { ThorLikeZipOutputStream(it).also { made -> zipStream = made } }
        assertEquals("level changes asked after the first entry", 0, requireNotNull(zipStream).levelChangesAfterAnEntry)
        verify(zip, written)
        val read = entries(zip)
        assertArrayEquals(File(tmp.root, "screenscraper-media/1234/box-front-small-120x168.webp").readBytes(), read.getValue("media/1234/box-front-small-120x168.webp"))
    }

    /** Box art is STORED (no Deflater at all); the JSON entries and the database are deflated. */
    @Test
    fun boxArtIsStoredAndTheRestIsDeflated() {
        val (zip, written) = writeEverything()
        val methods = LinkedHashMap<String, Int>()
        ZipInputStream(zip.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                input.readBytes()
                methods[entry.name] = entry.method
            }
        }
        for ((name, method) in methods) {
            val expected = if (name.startsWith("media/")) ZipEntry.STORED else ZipEntry.DEFLATED
            assertEquals(name, expected, method)
        }
        assertEquals(methods.keys.toList(), written.entries.map { it.name })
        val picture = File(tmp.root, "screenscraper-media/98/box-front-large-600x840.webp").readBytes()
        val entry = written.entries.single { it.name == "media/98/box-front-large-600x840.webp" }
        assertEquals(picture.size.toLong(), entry.size)
        assertEquals(CRC32().apply { update(picture) }.value, entry.crc)
    }

    @Test
    fun aWholeBackupPassesTheCheck() {
        val (zip, written) = writeEverything()
        verify(zip, written)
    }

    /** The check reads back as Restore does: any entry that does not read back whole fails it. */
    @Test
    fun aDamagedBackupFailsTheCheck() {
        val (zip, written) = writeEverything()
        fun damagedAt(name: String, offset: Int): ByteArray {
            val copy = zip.copyOf()
            val at = indexOf(copy, name.toByteArray()) + name.length + offset
            copy[at] = (copy[at].toInt() xor 0x55).toByte()
            return copy
        }
        // A byte of a stored picture: its CRC fails.
        assertDamaged(damagedAt("media/98/box-front-large-600x840.webp", 1_000), written)
        // A byte of the deflated database.
        assertDamaged(damagedAt("database/plumemu.db", 200), written)
        // The Thor's damage: the first media entry after the database does not inflate.
        val thorError = damageOf(thorLike(entries(zip)), written)
        assertTrue(thorError, thorError.startsWith("entry 3 does not read back"))
        // Cut short, or ending before the account.
        assertDamaged(zip.copyOf(zip.size / 2), written)
        assertDamaged(zip.copyOf(indexOf(zip, "account.json".toByteArray()) - 30), written)
        // An entry that was not written.
        assertDamaged(zip, written.copy(entries = written.entries.dropLast(1)))
        // Not a zip at all.
        assertDamaged(ByteArray(100), written)
    }

    /**
     * [entries] zipped as build 380 did on the Thor: the level set to no compression after the
     * database, through a stream that damages the next entry the way the Thor's Deflater did.
     */
    private fun thorLike(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ThorLikeZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                if (name.startsWith("media/") && zip.levelChangesAfterAnEntry == 0) zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
                if (name == "account.json") zip.setLevel(java.util.zip.Deflater.DEFAULT_COMPRESSION)
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

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
}
