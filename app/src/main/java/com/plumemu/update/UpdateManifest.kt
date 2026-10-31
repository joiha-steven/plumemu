package com.plumemu.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/**
 * One release as the update server describes it in `latest.json` (schema 1). [apkUrl] is already resolved against the manifest's own address.
 */
data class UpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val size: Long,
    /** Lower-case hex, 64 characters. */
    val sha256: String,
    val minSdk: Int,
    /** ISO date (`YYYY-MM-DD`), as written; shown, never computed with. */
    val released: String,
    val notes: List<String>,
    val notesUrl: String?,
)

/** What reading a manifest decided; [reason] is the one log line. */
sealed interface ManifestVerdict {
    val reason: String

    /** A newer release this device can run. */
    data class Newer(val manifest: UpdateManifest) : ManifestVerdict {
        override val reason: String get() = "update ${manifest.versionName} (${manifest.versionCode}) offered"
    }

    /** The release is the installed one or older. */
    data class UpToDate(override val reason: String) : ManifestVerdict

    /** The release needs a newer Android than this device has. */
    data class NeedsNewerAndroid(val minSdk: Int, override val reason: String) : ManifestVerdict

    /** Not a manifest this version understands: bad JSON, a bad field, or another schema. */
    data class Rejected(override val reason: String) : ManifestVerdict
}

/** Reads `latest.json`; nothing here touches the network or the disk. */
object UpdateManifests {
    const val SCHEMA = 1

    /** Larger than any Plumemu APK will be (under 10 MB without the name data); a bigger size is a broken manifest. */
    const val MAX_APK_BYTES = 200L * 1024 * 1024

    /** A manifest is a few hundred bytes; anything past this is not one. */
    const val MAX_MANIFEST_BYTES = 64 * 1024

    const val MAX_NOTES = 20
    const val MAX_NOTE_LENGTH = 300
    const val MAX_VERSION_NAME_LENGTH = 40

    private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}([T ].*)?$")

    /**
     * Reads [text], fetched from [manifestUrl], for a device on [deviceSdk] running [installedVersionCode].
     * Checked in this order: JSON, schema, every field, minSdk, versionCode.
     */
    fun read(
        text: String,
        manifestUrl: String,
        installedVersionCode: Long,
        deviceSdk: Int,
    ): ManifestVerdict {
        val root =
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                return ManifestVerdict.Rejected("manifest is not a JSON object")
            }
        if (!root.has("schema")) return ManifestVerdict.Rejected("manifest has no schema")
        val schema = root.opt("schema")
        if (schema !is Int || schema != SCHEMA) return ManifestVerdict.Rejected("unknown manifest schema $schema")
        val manifest =
            try {
                fields(root, manifestUrl)
            } catch (e: BadField) {
                return ManifestVerdict.Rejected("manifest field ${e.field}: ${e.problem}")
            }
        if (manifest.minSdk > deviceSdk) {
            return ManifestVerdict.NeedsNewerAndroid(manifest.minSdk, "update ${manifest.versionName} needs API ${manifest.minSdk}, device has $deviceSdk")
        }
        if (manifest.versionCode <= installedVersionCode) {
            return ManifestVerdict.UpToDate("latest is ${manifest.versionCode}, installed is $installedVersionCode")
        }
        return ManifestVerdict.Newer(manifest)
    }

    private class BadField(val field: String, val problem: String) : Exception("$field: $problem")

    private fun fields(
        root: JSONObject,
        manifestUrl: String,
    ): UpdateManifest {
        val versionCode = long(root, "versionCode").also { if (it <= 0) throw BadField("versionCode", "not positive") }
        val versionName = string(root, "versionName").also { if (it.length > MAX_VERSION_NAME_LENGTH) throw BadField("versionName", "too long") }
        val apkUrl = apkUrl(string(root, "apk"), manifestUrl)
        val size = long(root, "size").also { if (it <= 0 || it > MAX_APK_BYTES) throw BadField("size", "out of range") }
        val sha256 = string(root, "sha256").also { if (!HEX_64.matches(it)) throw BadField("sha256", "not 64 hex digits") }.lowercase(Locale.ROOT)
        val minSdk = long(root, "minSdk").also { if (it < 1 || it > Int.MAX_VALUE) throw BadField("minSdk", "out of range") }.toInt()
        val released = string(root, "released").also { if (!ISO_DATE.matches(it)) throw BadField("released", "not an ISO date") }
        val notes = notes(root)
        val notesUrl = if (root.has("notesUrl") && !root.isNull("notesUrl")) httpsUrl(string(root, "notesUrl")) else null
        return UpdateManifest(versionCode, versionName, apkUrl, size, sha256, minSdk, released, notes, notesUrl)
    }

    private fun string(
        root: JSONObject,
        key: String,
    ): String {
        val value = root.opt(key) ?: throw BadField(key, "missing")
        if (value !is String) throw BadField(key, "not a string")
        if (value.isBlank()) throw BadField(key, "empty")
        return value.trim()
    }

    /** A whole number; JSON gives an Int or a Long for one, a Double for anything with a point. */
    private fun long(
        root: JSONObject,
        key: String,
    ): Long =
        when (val value = root.opt(key) ?: throw BadField(key, "missing")) {
            is Int -> value.toLong()
            is Long -> value
            else -> throw BadField(key, "not a whole number")
        }

    private fun notes(root: JSONObject): List<String> {
        val array = root.opt("notes") as? JSONArray ?: throw BadField("notes", "not a list")
        if (array.length() > MAX_NOTES) throw BadField("notes", "more than $MAX_NOTES lines")
        return List(array.length()) { i ->
            val line = array.opt(i) as? String ?: throw BadField("notes", "line ${i + 1} is not a string")
            if (line.isBlank() || line.length > MAX_NOTE_LENGTH) throw BadField("notes", "line ${i + 1} is empty or too long")
            line.trim()
        }
    }

    /**
     * The APK's address, relative to the manifest. It must stay on the manifest's own server (scheme,
     * host and port): the privacy notes promise requests go to that one host.
     */
    private fun apkUrl(
        relative: String,
        manifestUrl: String,
    ): String {
        val base = uri(manifestUrl) ?: throw BadField("apk", "the manifest's own address is not a URL")
        val resolved = uri(relative)?.let { base.resolve(it) } ?: throw BadField("apk", "not a URL")
        val sameOrigin = resolved.scheme.equals(base.scheme, ignoreCase = true) && resolved.host.equals(base.host, ignoreCase = true) && resolved.port == base.port
        if (!sameOrigin) throw BadField("apk", "not on the manifest's server")
        if (!resolved.path.orEmpty().endsWith(".apk")) throw BadField("apk", "not an .apk")
        return resolved.toString()
    }

    private fun httpsUrl(text: String): String {
        val parsed = uri(text)
        if (parsed == null || !parsed.scheme.equals("https", ignoreCase = true) || parsed.host.isNullOrEmpty()) throw BadField("notesUrl", "not an https URL")
        return text
    }

    private fun uri(text: String): URI? =
        try {
            URI(text)
        } catch (e: URISyntaxException) {
            null
        }
}
