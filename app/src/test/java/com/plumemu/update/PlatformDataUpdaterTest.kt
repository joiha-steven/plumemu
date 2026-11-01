package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.PlatformDataFixtures.FILE_URL
import com.plumemu.update.PlatformDataFixtures.MANIFEST_URL
import com.plumemu.update.PlatformDataFixtures.bundled
import com.plumemu.update.PlatformDataFixtures.published
import com.plumemu.update.PlatformDataFixtures.serve
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** The platform data check against a fake server: every right and wrong path of the contract's "Order of checks". */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class PlatformDataUpdaterTest {
    @get:Rule val folder = TemporaryFolder()

    private val http = FakeUpdateHttp()
    private val infos = mutableListOf<String>()
    private val logs = mutableListOf<String>()
    private val dir by lazy { File(folder.root, "platform-data") }
    private val store by lazy { PlatformDataStore(dir) { logs += it } }

    private fun TestScope.updater(
        manifestUrl: String = MANIFEST_URL,
        installed: Long = PlatformDataFixtures.INSTALLED,
        trusted: java.security.PublicKey = PlatformDataFixtures.appKey.public,
    ) = PlatformDataUpdater(
        http = http,
        manifestUrl = manifestUrl,
        installedVersionCode = { installed },
        bundled = { bundled },
        store = store,
        verifier = CertificateSignatureVerifier { listOf(trusted) },
        io = StandardTestDispatcher(testScheduler),
        info = { infos += it },
        log = { logs += it },
    )

    private fun fileRequests() = http.requests.count { it != MANIFEST_URL }

    private fun assertRefused(reasonPart: String) {
        assertNull("nothing may be stored", store.read())
        assertEquals("one log line: $logs", 1, logs.size)
        assertTrue("'$reasonPart' not in ${logs[0]}", logs[0].contains(reasonPart))
    }

    @Test
    fun aNewerSignedFileIsStoredAndUsedAtTheNextStart() =
        runTest {
            serve(http, published())
            updater().check("daily")
            assertEquals(emptyList<String>(), logs)
            val stored = store.read()!!
            assertEquals(6, stored.dataVersion)
            assertEquals("2026-11-01", stored.date)
            assertEquals(PlatformDataFixtures.json, stored.json)
            // The next start: a new store object over the same folder, as a new process would have.
            val next = selectPlatformData(bundled, { PlatformDataFixtures.json.byteInputStream() }, PlatformDataStore(dir) { logs += it }, {}, { logs += it })
            assertEquals(6, next?.version?.dataVersion)
            assertEquals(emptyList<String>(), logs)
        }

    @Test
    fun aFileWhoseSha256DiffersFromTheManifestIsRefused() =
        runTest {
            serve(http, published(edit = { put("sha256", "ab".repeat(32)) }))
            updater().check("daily")
            assertRefused("SHA-256")
        }

    @Test
    fun aBadSignatureIsRefused() =
        runTest {
            serve(http, published(edit = { put("signature", java.util.Base64.getEncoder().encodeToString(ByteArray(256) { 7 })) }))
            updater().check("daily")
            assertRefused("signature")
        }

    @Test
    fun aSignatureThatIsNotBase64IsRefused() =
        runTest {
            serve(http, published(edit = { put("signature", "***not base64***") }))
            updater().check("daily")
            assertRefused("signature")
        }

    @Test
    fun dataSignedWithTheOtherKeyIsRefused() =
        runTest {
            // The release key's data on a debug build, or the other way round.
            serve(http, published(signWith = PlatformDataFixtures.otherKey))
            updater().check("daily")
            assertRefused("signature")
        }

    @Test
    fun aVersionNumberChangedAfterSigningIsRefused() =
        runTest {
            // An old signed file served under a higher version: the signature covers the version.
            serve(http, published(dataVersion = 5, edit = { put("dataVersion", 9); put("file", "platforms-6.json.gz") }))
            updater().check("daily")
            assertRefused("signature")
        }

    @Test
    fun aVersionNotNewerThanTheBundledOneIsSkippedWithoutDownloading() =
        runTest {
            serve(http, published(dataVersion = 1))
            updater().check("daily")
            assertEquals(0, fileRequests())
            assertEquals(emptyList<String>(), logs)
            assertNull(store.read())
        }

    @Test
    fun aVersionNotNewerThanTheStoredOneIsSkippedWithoutDownloading() =
        runTest {
            serve(http, published(dataVersion = 6))
            updater().check("daily")
            assertEquals(6, store.read()?.dataVersion)
            http.requests.clear()
            for (older in listOf(6, 5)) {
                serve(http, published(dataVersion = older))
                updater().check("daily")
            }
            assertEquals(0, fileRequests())
            assertEquals(6, store.read()?.dataVersion)
            assertEquals(emptyList<String>(), logs)
        }

    @Test
    fun aHigherVersionReplacesTheStoredOne() =
        runTest {
            serve(http, published(dataVersion = 6))
            updater().check("daily")
            val seven = published(dataVersion = 7)
            http.texts[MANIFEST_URL] = seven.manifest
            http.files["https://plumemu-app.stevensdesk.com/data/platforms/platforms-7.json.gz"] = seven.gz
            updater().check("manual")
            assertEquals(7, store.read()?.dataVersion)
        }

    @Test
    fun anUnknownFormatVersionIsSkippedWithoutDownloading() =
        runTest {
            serve(http, published(formatVersion = 3))
            updater().check("daily")
            assertEquals(0, fileRequests())
            assertEquals(1, logs.size)
            assertTrue(logs[0].contains("format 3"))
        }

    @Test
    fun aMinVersionCodeAboveTheInstalledAppIsSkippedWithoutDownloading() =
        runTest {
            serve(http, published(minVersionCode = 901))
            updater().check("daily")
            assertEquals(0, fileRequests())
            assertEquals(1, logs.size)
            assertTrue(logs[0].contains("901"))
            // The same data is taken by an app that has that build.
            logs.clear()
            updater(installed = 901).check("daily")
            assertEquals(6, store.read()?.dataVersion)
        }

    @Test
    fun aManifestWithAnotherSchemaOrNoJsonIsRefusedBeforeItsFieldsAreRead() =
        runTest {
            serve(http, published(edit = { put("schema", 2); remove("dataVersion") }))
            updater().check("daily")
            assertEquals(1, logs.size)
            assertTrue(logs[0].contains("schema"))
            http.texts[MANIFEST_URL] = "<html>captive portal</html>"
            updater().check("daily")
            assertEquals(2, logs.size)
            assertEquals(0, fileRequests())
        }

    @Test
    fun badManifestFieldsAreRefused() =
        runTest {
            val bad =
                listOf<JSONObject.() -> Unit>(
                    { put("dataVersion", 0) },
                    { put("dataVersion", "6") },
                    { put("minVersionCode", -1) },
                    { put("sha256", "nothex") },
                    { put("size", 0) },
                    { put("file", "platforms-6.json") },
                    { remove("signature") },
                    { put("released", "yesterday") },
                )
            for (edit in bad) {
                logs.clear()
                serve(http, published(edit = edit))
                updater().check("daily")
                assertEquals("$logs", 1, logs.size)
            }
            assertEquals(0, fileRequests())
        }

    @Test
    fun aSizeOverFiveMegabytesIsRefusedWithoutDownloading() =
        runTest {
            serve(http, published(edit = { put("size", 5L * 1024 * 1024 + 1) }))
            updater().check("daily")
            assertEquals(0, fileRequests())
            assertEquals(1, logs.size)
        }

    @Test
    fun aFileLongerOrShorterThanTheManifestSaysIsRefused() =
        runTest {
            val p = published()
            for (claimed in listOf(p.gz.size - 10, p.gz.size + 10)) {
                logs.clear()
                http.texts[MANIFEST_URL] = JSONObject(p.manifest).put("size", claimed).toString()
                http.files[FILE_URL] = p.gz
                // The server also says its length, as OkHttp would.
                http.claimedLength = null
                updater().check("daily")
                assertRefused("bytes")
            }
        }

    @Test
    fun aServerThatSendsMoreThanItSaidIsCutAtTheManifestSize() =
        runTest {
            val p = published()
            // Says no length (chunked) and sends more than the manifest's size.
            http.texts[MANIFEST_URL] = JSONObject(p.manifest).put("size", p.gz.size - 10).toString()
            http.files[FILE_URL] = p.gz
            http.claimedLength = -1
            updater().check("daily")
            assertRefused("bytes")
        }

    @Test
    fun aFileThatUnpacksOverTwentyMegabytesIsRefused() =
        runTest {
            val big = ByteArray(PlatformDataManifests.MAX_JSON_BYTES + 1)
            serve(http, published(json = big))
            updater().check("daily")
            assertRefused("unpacked")
        }

    @Test
    fun aCorruptGzipIsRefused() =
        runTest {
            // Right SHA-256 and signature for bytes that are not gzip at all.
            serve(http, published(gz = "this is not gzip".toByteArray()))
            updater().check("daily")
            assertRefused("gzip")
        }

    @Test
    fun aGzipCutShortIsRefused() =
        runTest {
            val whole = PlatformDataFixtures.gzip(PlatformDataFixtures.json.toByteArray())
            serve(http, published(gz = whole.copyOf(whole.size / 2)))
            updater().check("daily")
            assertRefused("gzip")
        }

    @Test
    fun jsonThatIsNotJsonIsRefusedByTheLoader() =
        runTest {
            serve(http, published(json = "{ not json".toByteArray()))
            updater().check("daily")
            assertRefused("loader")
        }

    @Test
    fun jsonTheLoaderRejectsIsNeverStored() =
        runTest {
            for (text in listOf("""{"formatVersion":2,"systems":[],"emulators":[]}""", """{"formatVersion":1,"systems":[],"emulators":[]}""", "[]")) {
                logs.clear()
                serve(http, published(json = text.toByteArray()))
                updater().check("daily")
                assertRefused("loader")
            }
        }

    @Test
    fun aNetworkFailureOnTheManifestKeepsTheDataAndLogsOneLine() =
        runTest {
            for (failure in listOf(UpdateNetException(NetFailure.Offline), UpdateNetException(NetFailure.Timeout), UpdateNetException(NetFailure.Http, 503))) {
                logs.clear()
                http.textFailure = failure
                updater().check("daily")
                assertRefused(failure.message.orEmpty())
            }
            http.textFailure = null
            http.texts.clear() // 404
            logs.clear()
            updater().check("daily")
            assertRefused("Http 404")
        }

    @Test
    fun aFailureWhileDownloadingKeepsTheDataAndLogsOneLine() =
        runTest {
            val p = published()
            serve(http, p)
            http.failAfterBytes = p.gz.size / 2
            http.fileFailure = UpdateNetException(NetFailure.Timeout)
            updater().check("daily")
            assertRefused("Timeout")
            logs.clear()
            http.files.clear() // 404 on the file
            updater().check("daily")
            assertRefused("Http 404")
        }

    @Test
    fun aFileOnAnotherHostIsNeverRequested() =
        runTest {
            for (file in listOf("https://evil.example/platforms-6.json.gz", "http://plumemu-app.stevensdesk.com/data/platforms/platforms-6.json.gz", "https://plumemu-app.stevensdesk.com:8443/platforms-6.json.gz")) {
                logs.clear()
                val p = published(edit = { put("file", file) })
                http.texts[MANIFEST_URL] = p.manifest
                http.files[file] = p.gz
                updater().check("daily")
                assertRefused("not on the manifest's server")
            }
            assertEquals(0, fileRequests())
        }

    @Test
    fun aBuildWithoutAnAddressAsksNobody() =
        runTest {
            updater(manifestUrl = "").check("daily")
            assertEquals(emptyList<String>(), http.requests)
            assertEquals(emptyList<String>(), logs)
        }

    @Test
    fun aFailedWriteLeavesTheOldCopy() =
        runTest {
            serve(http, published(dataVersion = 6))
            updater().check("daily")
            // The folder is replaced by a plain file: the next write cannot create its files.
            dir.deleteRecursively()
            dir.writeText("in the way")
            val seven = published(dataVersion = 7)
            http.texts[MANIFEST_URL] = seven.manifest
            http.files["https://plumemu-app.stevensdesk.com/data/platforms/platforms-7.json.gz"] = seven.gz
            updater().check("daily")
            assertEquals(1, logs.size)
            assertTrue(logs[0].contains("could not write"))
        }

    @Test
    fun aDamagedStoredCopyIsDeletedAtStartAndItsVersionIsDownloadedAgain() =
        runTest {
            serve(http, published(dataVersion = 6))
            updater().check("daily")
            File(dir, "platforms.json").writeText("damaged")
            // Start: the bundled data is used and the damaged copy is forgotten.
            val loaded = selectPlatformData(bundled, { PlatformDataFixtures.json.byteInputStream() }, store, {}, { logs += it })
            assertEquals(1, loaded?.version?.dataVersion)
            assertNull(store.readVersion())
            logs.clear()
            // The next check takes version 6 again.
            updater().check("daily")
            assertEquals(6, store.read()?.dataVersion)
            assertEquals(emptyList<String>(), logs)
        }
}
