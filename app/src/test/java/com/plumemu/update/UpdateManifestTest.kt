package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.INSTALLED
import com.plumemu.update.UpdateFixtures.MANIFEST_URL
import com.plumemu.update.UpdateFixtures.SDK
import com.plumemu.update.UpdateFixtures.manifestJson
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `latest.json`, schema 1: what is offered and what is refused. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class UpdateManifestTest {
    private fun read(
        text: String,
        installed: Long = INSTALLED,
        sdk: Int = SDK,
        url: String = MANIFEST_URL,
    ) = UpdateManifests.read(text, url, installed, sdk)

    private fun rejected(edit: JSONObject.() -> Unit): String {
        val verdict = read(manifestJson(edit))
        assertTrue("expected a refusal, got $verdict", verdict is ManifestVerdict.Rejected)
        return verdict.reason
    }

    @Test
    fun aGoodManifestOffersTheReleaseWithItsApkResolvedAgainstTheManifest() {
        val verdict = read(manifestJson()) as ManifestVerdict.Newer
        val m = verdict.manifest
        assertEquals(41L, m.versionCode)
        assertEquals("0.0.3-alpha", m.versionName)
        assertEquals(UpdateFixtures.apkUrl, m.apkUrl)
        assertEquals(UpdateFixtures.apkBytes.size.toLong(), m.size)
        assertEquals(UpdateFixtures.sha256(UpdateFixtures.apkBytes), m.sha256)
        assertEquals(23, m.minSdk)
        assertEquals("2026-10-23", m.released)
        assertEquals(listOf("Updates install from Settings › About.", "Faster scans."), m.notes)
        assertEquals("https://github.com/joiha-steven/plumemu/blob/main/CHANGELOG.md", m.notesUrl)
    }

    @Test
    fun notesUrlIsOptionalAndAnUpperCaseHashIsKeptInLowerCase() {
        val hash = UpdateFixtures.sha256(UpdateFixtures.apkBytes)
        val m = (read(manifestJson { remove("notesUrl"); put("sha256", hash.uppercase()) }) as ManifestVerdict.Newer).manifest
        assertNull(m.notesUrl)
        assertEquals(hash, m.sha256)
    }

    @Test
    fun theSameOrAnOlderVersionIsUpToDate() {
        assertTrue(read(manifestJson(), installed = 41) is ManifestVerdict.UpToDate)
        assertTrue(read(manifestJson(), installed = 50) is ManifestVerdict.UpToDate)
    }

    @Test
    fun aMinSdkAboveTheDeviceIsNotOffered() {
        val verdict = read(manifestJson { put("minSdk", 26) }, sdk = 23)
        assertEquals(26, (verdict as ManifestVerdict.NeedsNewerAndroid).minSdk)
        assertTrue(read(manifestJson { put("minSdk", 26) }, sdk = 26) is ManifestVerdict.Newer)
    }

    @Test
    fun anUnknownOrMissingSchemaIsRefusedBeforeAnyField() {
        assertTrue(rejected { put("schema", 2); remove("versionCode") }.contains("schema 2"))
        assertTrue(rejected { put("schema", "1") }.contains("schema"))
        assertTrue(rejected { remove("schema") }.contains("no schema"))
    }

    @Test
    fun notJsonIsRefused() {
        assertTrue(read("<html>Not found</html>") is ManifestVerdict.Rejected)
        assertTrue(read("[1, 2]") is ManifestVerdict.Rejected)
        assertTrue(read("") is ManifestVerdict.Rejected)
    }

    @Test
    fun eachBadFieldIsRefusedAndNamed() {
        val cases: List<Pair<String, JSONObject.() -> Unit>> =
            listOf(
                "versionCode" to { remove("versionCode") },
                "versionCode" to { put("versionCode", "41") },
                "versionCode" to { put("versionCode", 41.5) },
                "versionCode" to { put("versionCode", 0) },
                "versionName" to { remove("versionName") },
                "versionName" to { put("versionName", "  ") },
                "versionName" to { put("versionName", "x".repeat(41)) },
                "apk" to { remove("apk") },
                "apk" to { put("apk", "https://elsewhere.example/plumemu.apk") },
                "apk" to { put("apk", "//elsewhere.example/plumemu.apk") },
                "apk" to { put("apk", "http://plumemu-app.stevensdesk.com/update/plumemu.apk") },
                "apk" to { put("apk", "plumemu.zip") },
                "size" to { remove("size") },
                "size" to { put("size", -1) },
                "size" to { put("size", UpdateManifests.MAX_APK_BYTES + 1) },
                "sha256" to { remove("sha256") },
                "sha256" to { put("sha256", "abc") },
                "sha256" to { put("sha256", "z".repeat(64)) },
                "minSdk" to { remove("minSdk") },
                "minSdk" to { put("minSdk", 0) },
                "released" to { remove("released") },
                "released" to { put("released", "yesterday") },
                "notes" to { remove("notes") },
                "notes" to { put("notes", "one line") },
                "notes" to { put("notes", org.json.JSONArray(listOf("ok", 3))) },
                "notes" to { put("notes", org.json.JSONArray(listOf(""))) },
                "notes" to { put("notes", org.json.JSONArray(List(UpdateManifests.MAX_NOTES + 1) { "line" })) },
                "notesUrl" to { put("notesUrl", "http://insecure.example/changes") },
                "notesUrl" to { put("notesUrl", 7) },
            )
        for ((field, edit) in cases) {
            val reason = rejected(edit)
            assertTrue("$field: $reason", reason.startsWith("manifest field $field"))
        }
    }

    @Test
    fun anApkInASubfolderOfTheSameServerIsFine() {
        val m = (read(manifestJson { put("apk", "builds/plumemu-0.0.3-alpha.apk") }) as ManifestVerdict.Newer).manifest
        assertEquals("https://plumemu-app.stevensdesk.com/update/builds/plumemu-0.0.3-alpha.apk", m.apkUrl)
    }
}
