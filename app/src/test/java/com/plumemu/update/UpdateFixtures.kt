package com.plumemu.update

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Shared pieces of the update tests: a manifest, a fake server, a fake installer and store. */
internal object UpdateFixtures {
    const val MANIFEST_URL = "https://plumemu-app.stevensdesk.com/update/latest.json"
    const val INSTALLED = 40L
    const val SDK = 23
    const val CERT = "c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00c0ffee00"

    val apkBytes: ByteArray = ByteArray(300_000) { (it * 31 % 251).toByte() }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    /** A good schema-1 manifest for [apkBytes] at versionCode 41; change fields with [edit]. */
    fun manifestJson(edit: JSONObject.() -> Unit = {}): String =
        JSONObject()
            .put("schema", 1)
            .put("versionCode", 41)
            .put("versionName", "0.0.3-alpha")
            .put("apk", "plumemu-0.0.3-alpha.apk")
            .put("size", apkBytes.size)
            .put("sha256", sha256(apkBytes))
            .put("minSdk", 23)
            .put("released", "2026-10-23")
            .put("notes", JSONArray(listOf("Updates install from Settings › About.", "Faster scans.")))
            .put("notesUrl", "https://github.com/joiha-steven/plumemu/blob/main/CHANGELOG.md")
            .apply(edit)
            .toString()

    val apkUrl = "https://plumemu-app.stevensdesk.com/update/plumemu-0.0.3-alpha.apk"

    /** A `.dm` as AGP writes it: a zip of `primary.prof` (and `primary.profm`); [withProfile] false leaves the profile out. */
    fun dexMetadataZip(
        tag: String,
        withProfile: Boolean = true,
    ): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bytes).use { zip ->
            if (withProfile) {
                zip.putNextEntry(java.util.zip.ZipEntry(DexMetadata.PROFILE_ENTRY))
                zip.write("pro\u0000 $tag".toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(java.util.zip.ZipEntry("primary.profm"))
            zip.write("pro\u0000 meta $tag".toByteArray())
            zip.closeEntry()
        }
        return bytes.toByteArray()
    }

    /** Android 9 to 11's profile format (P) and Android 12's onwards (S), as a release manifest lists them. */
    val dmP: ByteArray = dexMetadataZip("010")
    val dmS: ByteArray = dexMetadataZip("015")
    val dmPUrl = "https://plumemu-app.stevensdesk.com/update/plumemu-0.0.3-alpha-41.api28-30.dm"
    val dmSUrl = "https://plumemu-app.stevensdesk.com/update/plumemu-0.0.3-alpha-41.api31-up.dm"

    /** One `dexMetadata` entry of the manifest. */
    fun dexMetadataEntry(
        minSdk: Int,
        maxSdk: Int?,
        file: String,
        bytes: ByteArray,
    ): JSONObject =
        JSONObject().put("minSdk", minSdk).apply { if (maxSdk != null) put("maxSdk", maxSdk) }
            .put("file", file).put("size", bytes.size).put("sha256", sha256(bytes))

    /** The `dexMetadata` field of a release manifest for [dmP] and [dmS]. */
    fun dexMetadataJson(): JSONArray =
        JSONArray()
            .put(dexMetadataEntry(28, 30, "plumemu-0.0.3-alpha-41.api28-30.dm", dmP))
            .put(dexMetadataEntry(31, null, "plumemu-0.0.3-alpha-41.api31-up.dm", dmS))

    val installedFacts = ApkFacts(ApkChecks.PACKAGE, INSTALLED, setOf(CERT))
    val goodArchive = ApkFacts(ApkChecks.PACKAGE, 41, setOf(CERT))
}

/** A server that answers from maps; anything not in them is a 404. Counts the requests. */
internal class FakeUpdateHttp : UpdateHttp {
    val texts = mutableMapOf<String, String>()
    val files = mutableMapOf<String, ByteArray>()
    var textFailure: UpdateNetException? = null
    var fileFailure: UpdateNetException? = null

    /** The download stops after this many bytes with [fileFailure] (a connection lost half-way). */
    var failAfterBytes: Int? = null
    var claimedLength: Long? = null
    val requests = mutableListOf<String>()

    /** Thrown by the next texts instead (a failure that is not the network's). */
    var textCrash: RuntimeException? = null

    /** While set and not complete, a request waits here (a check or a download still running). */
    var textGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    var openGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

    override suspend fun text(
        url: String,
        maxBytes: Int,
    ): String {
        requests += url
        textGate?.await()
        textCrash?.let { throw it }
        textFailure?.let { throw it }
        return texts[url] ?: throw UpdateNetException(NetFailure.Http, 404)
    }

    override suspend fun open(url: String): UpdateBody {
        requests += url
        openGate?.await()
        val bytes = files[url] ?: throw UpdateNetException(NetFailure.Http, 404)
        val stopAt = failAfterBytes
        val stream =
            if (stopAt == null) {
                ByteArrayInputStream(bytes)
            } else {
                object : java.io.InputStream() {
                    private var at = 0

                    override fun read(): Int {
                        if (at >= stopAt) throw fileFailure ?: UpdateNetException(NetFailure.Offline)
                        return bytes[at++].toInt() and 0xff
                    }
                }
            }
        return UpdateBody(claimedLength ?: bytes.size.toLong(), stream) {}
    }
}

internal class MemoryUpdateStore(var record: UpdateRecord = UpdateRecord()) : UpdateStore {
    var writes = 0
    var failWrites = false

    override fun read(): UpdateRecord = record

    override fun write(record: UpdateRecord) {
        if (failWrites) throw IOException("disk full")
        writes++
        this.record = record
    }
}

internal class FakeInspector(
    var archive: (File) -> ApkFacts? = { UpdateFixtures.goodArchive },
    var installed: ApkFacts? = UpdateFixtures.installedFacts,
) : ApkInspector {
    override fun archive(file: File): ApkFacts? = archive.invoke(file)

    override fun installed(): ApkFacts? = installed
}

internal class FakeInstaller : UpdateInstaller {
    var canInstall = true
    var hasPermissionScreen = true
    var permissionScreens = 0
    val committed = mutableListOf<Pair<ByteArray, Long>>()
    var commitFailure: IOException? = null

    /** Like ReopenMarker: written by a commit (after [duringCommit]), cleared by [forgetReopen]. */
    var reopenMarker = false
    var duringCommit: () -> Unit = {}

    /** Runs once, at the end of the next commit, before it returns: Android answering before the commit call is back. */
    var afterCommit: () -> Unit = {}

    override fun forgetReopen() {
        reopenMarker = false
    }

    override fun canRequestInstalls(): Boolean = canInstall

    override fun openInstallPermission(): Boolean {
        permissionScreens++
        return hasPermissionScreen
    }

    /** The dex metadata each commit carried (null: the APK alone), in the order of [committed]. */
    val committedDexMetadata = mutableListOf<ByteArray?>()

    /** Like [SessionUpdateInstaller] when writing the `.dm` failed: the APK goes alone. */
    var dexMetadataWriteFails = false

    override fun commit(
        file: File,
        versionCode: Long,
        dexMetadata: File?,
    ): Boolean {
        commitFailure?.let { throw it }
        duringCommit()
        committed += file.readBytes() to versionCode
        val carried = dexMetadata?.takeUnless { dexMetadataWriteFails }
        committedDexMetadata += carried?.readBytes()
        reopenMarker = true
        afterCommit.also { afterCommit = {} }.invoke()
        return carried != null
    }
}
