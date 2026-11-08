package com.plumemu.update

import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.update.UpdateFixtures.INSTALLED
import com.plumemu.update.UpdateFixtures.MANIFEST_URL
import com.plumemu.update.UpdateFixtures.SDK
import com.plumemu.update.UpdateFixtures.manifestJson
import org.json.JSONArray
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

    private fun sectionsOf(value: Any?): List<NoteSection> {
        val verdict = read(manifestJson { put("sections", value) })
        return (verdict as ManifestVerdict.Newer).manifest.sections
    }

    private fun section(
        kind: Any,
        vararg items: Any,
    ) = JSONObject().put("kind", kind).put("items", JSONArray(items.toList()))

    @Test
    fun validSectionsAreKeptInTheFixedOrderWhateverOrderTheyCame() {
        val sections = sectionsOf(JSONArray().put(section("fixed", "Grid cut.")).put(section("new", "Updates.", "Faster scans.")))
        assertEquals(listOf(NoteSection(SectionKind.New, listOf("Updates.", "Faster scans.")), NoteSection(SectionKind.Fixed, listOf("Grid cut."))), sections)
        val m = (read(manifestJson { put("sections", JSONArray().put(section("known", "x"))) }) as ManifestVerdict.Newer).manifest
        assertEquals("notes stay as they were", listOf("Updates install from Settings › About.", "Faster scans."), m.notes)
    }

    @Test
    fun noSectionsFieldMeansNoSections() {
        assertEquals(emptyList<NoteSection>(), (read(manifestJson()) as ManifestVerdict.Newer).manifest.sections)
    }

    @Test
    fun anUnknownKindDropsOnlyThatSection() {
        val sections = sectionsOf(JSONArray().put(section("new", "A.")).put(section("secret", "B.")).put(section("fixed", "C.")))
        assertEquals(listOf(SectionKind.New, SectionKind.Fixed), sections.map { it.kind })
    }

    @Test
    fun moreThanTwentyItemsInAllDropsTheSections() {
        val many = JSONArray().put(section("new", *Array(11) { "n$it" })).put(section("fixed", *Array(10) { "f$it" }))
        assertEquals(emptyList<NoteSection>(), sectionsOf(many))
        assertEquals(20, sectionsOf(JSONArray().put(section("new", *Array(20) { "n$it" }))).single().items.size)
    }

    @Test
    fun wrongTypesAndBadItemsDropTheSectionsButTheManifestIsStillOffered() {
        assertEquals(emptyList<NoteSection>(), sectionsOf("new"))
        assertEquals(emptyList<NoteSection>(), sectionsOf(JSONArray().put("new")))
        assertEquals(emptyList<NoteSection>(), sectionsOf(JSONArray().put(JSONObject().put("kind", 3).put("items", JSONArray(listOf("a"))))))
        assertEquals(emptyList<NoteSection>(), sectionsOf(JSONArray().put(JSONObject().put("kind", "new").put("items", "a"))))
        assertEquals(emptyList<NoteSection>(), sectionsOf(JSONArray().put(section("new"))))
        assertEquals(emptyList<NoteSection>(), sectionsOf(JSONArray().put(section("new", 5))))
        assertEquals(emptyList<NoteSection>(), sectionsOf(JSONArray().put(section("new", "x".repeat(301)))))
        assertEquals(emptyList<NoteSection>(), sectionsOf(JSONArray().put(section("new", " "))))
    }
    /**
     * Apps already installed read `latest.json` with a parser that knows no `dexMetadata`: any shape of it,
     * and any other field this version does not know, must leave the release offered and unchanged.
     */
    @Test
    fun fieldsThisVersionDoesNotKnowNeverStopAnUpdate() {
        val plain = (read(manifestJson()) as ManifestVerdict.Newer).manifest
        val shapes: List<Any> =
            listOf(
                JSONArray().put(
                    JSONObject().put("minSdk", 28).put("maxSdk", 30).put("file", "plumemu-0.0.3-alpha-41.api28-30.dm")
                        .put("size", 17764).put("sha256", "ab".repeat(32)),
                ),
                "not a list",
                42,
                JSONObject().put("nested", JSONArray(listOf(1, 2))),
            )
        for (shape in shapes) {
            val verdict = read(manifestJson { put("dexMetadata", shape); put("someLaterField", JSONObject().put("x", 1)) })
            assertTrue("$shape: $verdict", verdict is ManifestVerdict.Newer)
            assertEquals(plain, (verdict as ManifestVerdict.Newer).manifest.copy(dexMetadata = emptyList()))
        }
    }
    private fun dexMetadataOf(value: Any?): List<DexMetadataFile> {
        val verdict = read(manifestJson { put("dexMetadata", value) })
        assertTrue("a dexMetadata problem never refuses the manifest: $verdict", verdict is ManifestVerdict.Newer)
        return (verdict as ManifestVerdict.Newer).manifest.dexMetadata
    }

    @Test
    fun dexMetadataIsReadWithItsRangesAndResolvedAgainstTheManifest() {
        val entries = dexMetadataOf(UpdateFixtures.dexMetadataJson())
        assertEquals(
            listOf(
                DexMetadataFile(28, 30, UpdateFixtures.dmPUrl, UpdateFixtures.dmP.size.toLong(), UpdateFixtures.sha256(UpdateFixtures.dmP)),
                DexMetadataFile(31, null, UpdateFixtures.dmSUrl, UpdateFixtures.dmS.size.toLong(), UpdateFixtures.sha256(UpdateFixtures.dmS)),
            ),
            entries,
        )
    }

    @Test
    fun noDexMetadataFieldMeansNone() {
        assertEquals(emptyList<DexMetadataFile>(), (read(manifestJson()) as ManifestVerdict.Newer).manifest.dexMetadata)
        assertEquals(emptyList<DexMetadataFile>(), dexMetadataOf(JSONObject.NULL))
    }

    @Test
    fun aBadDexMetadataEntryIsDroppedAloneAndTheManifestStillOffered() {
        val good = UpdateFixtures.dexMetadataEntry(31, null, "plumemu-0.0.3-alpha-41.api31-up.dm", UpdateFixtures.dmS)
        val bad: List<JSONObject.() -> Unit> =
            listOf(
                { put("file", "https://elsewhere.example/update/x.dm") },
                { put("file", "//elsewhere.example/update/x.dm") },
                { put("file", "http://plumemu-app.stevensdesk.com/update/x.dm") },
                { put("file", "x.apk") },
                { remove("file") },
                { put("minSdk", 27) },
                { put("minSdk", "28") },
                { remove("minSdk") },
                { put("maxSdk", 27) },
                { put("maxSdk", 29.5) },
                { put("size", 0) },
                { put("size", DexMetadata.MAX_BYTES + 1) },
                { put("sha256", "abc") },
                { remove("sha256") },
            )
        for (edit in bad) {
            val broken = JSONObject(UpdateFixtures.dexMetadataEntry(28, 30, "plumemu-0.0.3-alpha-41.api28-30.dm", UpdateFixtures.dmP).toString()).apply(edit)
            val entries = dexMetadataOf(JSONArray().put(broken).put(good))
            assertEquals("$broken", listOf(31), entries.map { it.minSdk })
        }
        assertEquals(listOf(31), dexMetadataOf(JSONArray().put("x").put(good)).map { it.minSdk })
    }

    @Test
    fun aDexMetadataFieldOfTheWrongShapeOrWithOverlappingRangesIsDroppedWhole() {
        assertEquals(emptyList<DexMetadataFile>(), dexMetadataOf("plumemu.dm"))
        assertEquals(emptyList<DexMetadataFile>(), dexMetadataOf(JSONObject().put("minSdk", 28)))
        val overlapping =
            JSONArray()
                .put(UpdateFixtures.dexMetadataEntry(28, 31, "a.dm", UpdateFixtures.dmP))
                .put(UpdateFixtures.dexMetadataEntry(31, null, "b.dm", UpdateFixtures.dmS))
        assertEquals(emptyList<DexMetadataFile>(), dexMetadataOf(overlapping))
        val tooMany = JSONArray().apply { repeat(DexMetadata.MAX_ENTRIES + 1) { put(UpdateFixtures.dexMetadataEntry(28 + it, 28 + it, "d$it.dm", UpdateFixtures.dmP)) } }
        assertEquals(emptyList<DexMetadataFile>(), dexMetadataOf(tooMany))
    }
}
