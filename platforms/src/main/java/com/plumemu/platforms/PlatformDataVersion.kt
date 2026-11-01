package com.plumemu.platforms

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Which version of the platform data a copy is: the number only grows, the date is shown in Settings › About.
 * The bundled copy's is `assets/platforms-version.json` ([PlatformDataVersions.parse]);
 * the same numbering is used by the files the app downloads.
 */
data class PlatformDataVersion(
    val dataVersion: Int,
    /** SHA-256 of the platforms.json bytes, 64 lower-case hex digits. */
    val sha256: String,
    /** ISO date (`YYYY-MM-DD`). */
    val date: String,
)

object PlatformDataVersions {
    /** The `formatVersion` the loader reads; a published file with another is skipped. */
    const val FORMAT_VERSION = SUPPORTED_FORMAT_VERSION

    /** Name of the bundled version file in the assets. */
    const val BUNDLED_VERSION_ASSET = "platforms-version.json"

    private val HEX_64 = Regex("^[0-9a-f]{64}$")

    /** Null when [text] is not a version file (the caller logs it). */
    fun parse(text: String): PlatformDataVersion? {
        val root =
            try {
                Json.parseToJsonElement(text) as? JsonObject
            } catch (e: SerializationException) {
                null
            } ?: return null
        val version = (root["dataVersion"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: return null
        val sha256 = (root["sha256"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
        val date = (root["date"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
        if (version < 1 || !HEX_64.matches(sha256)) return null
        return PlatformDataVersion(version, sha256, date)
    }
}
