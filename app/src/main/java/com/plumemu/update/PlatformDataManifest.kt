package com.plumemu.update

import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** One published platform data file as `data/platforms/<channel>.json` describes it (schema 1); [fileUrl] is already resolved. */
data class PlatformDataManifest(
    val dataVersion: Int,
    val formatVersion: Int,
    val minVersionCode: Long,
    val fileUrl: String,
    /** Bytes of the `.json.gz`. */
    val size: Long,
    /** Of the `.json.gz` bytes, lower-case hex. */
    val sha256: String,
    /** Base64 of the signature over [signedText]. */
    val signature: String,
    /** ISO date, shown in Settings › About. */
    val released: String,
) {
    /** The exact text the publisher signed. */
    val signedText: String
        get() = "plumemu-platforms-data\ndataVersion=$dataVersion\nformatVersion=$formatVersion\nminVersionCode=$minVersionCode\nsha256=$sha256\n"
}

/** Reads the platform data manifest; nothing here touches the network or the disk. */
object PlatformDataManifests {
    const val SCHEMA = 1
    const val MAX_MANIFEST_BYTES = 64 * 1024
    const val MAX_FILE_BYTES = 5L * 1024 * 1024
    const val MAX_JSON_BYTES = 20 * 1024 * 1024

    private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}([T ].*)?$")

    /** The manifest, or null with [Rejected.reason] as the one log line. */
    sealed interface Result {
        data class Ok(val manifest: PlatformDataManifest) : Result

        data class Rejected(val reason: String) : Result
    }

    fun read(
        text: String,
        manifestUrl: String,
    ): Result {
        val root =
            try {
                JSONObject(text)
            } catch (e: JSONException) {
                return Result.Rejected("manifest is not a JSON object")
            }
        val schema = root.opt("schema")
        if (schema !is Int || schema != SCHEMA) return Result.Rejected("unknown manifest schema $schema")
        return try {
            Result.Ok(fields(root, manifestUrl))
        } catch (e: BadField) {
            Result.Rejected("manifest field ${e.field}: ${e.problem}")
        }
    }

    private class BadField(val field: String, val problem: String) : Exception("$field: $problem")

    private fun fields(
        root: JSONObject,
        manifestUrl: String,
    ): PlatformDataManifest {
        val dataVersion = long(root, "dataVersion").also { if (it <= 0 || it > Int.MAX_VALUE) throw BadField("dataVersion", "out of range") }.toInt()
        val formatVersion = long(root, "formatVersion").also { if (it <= 0 || it > Int.MAX_VALUE) throw BadField("formatVersion", "out of range") }.toInt()
        val minVersionCode = long(root, "minVersionCode").also { if (it < 0) throw BadField("minVersionCode", "negative") }
        val fileUrl = fileUrl(string(root, "file"), manifestUrl)
        val size = long(root, "size").also { if (it < 1 || it > MAX_FILE_BYTES) throw BadField("size", "out of range") }
        val sha256 = string(root, "sha256").also { if (!HEX_64.matches(it)) throw BadField("sha256", "not 64 hex digits") }.lowercase(Locale.ROOT)
        val signature = string(root, "signature")
        val released = string(root, "released").also { if (!ISO_DATE.matches(it)) throw BadField("released", "not an ISO date") }
        return PlatformDataManifest(dataVersion, formatVersion, minVersionCode, fileUrl, size, sha256, signature, released)
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

    /** The file's address, relative to the manifest; it must stay on the manifest's own scheme, host and port (the privacy notes promise one host). */
    private fun fileUrl(
        relative: String,
        manifestUrl: String,
    ): String {
        val base = uri(manifestUrl) ?: throw BadField("file", "the manifest's own address is not a URL")
        val resolved = uri(relative)?.let { base.resolve(it) } ?: throw BadField("file", "not a URL")
        val sameOrigin = resolved.scheme.equals(base.scheme, ignoreCase = true) && resolved.host.equals(base.host, ignoreCase = true) && resolved.port == base.port
        if (!sameOrigin) throw BadField("file", "not on the manifest's server")
        if (!resolved.path.orEmpty().endsWith(".json.gz")) throw BadField("file", "not a .json.gz")
        return resolved.toString()
    }

    private fun uri(text: String): URI? =
        try {
            URI(text)
        } catch (e: URISyntaxException) {
            null
        }
}
