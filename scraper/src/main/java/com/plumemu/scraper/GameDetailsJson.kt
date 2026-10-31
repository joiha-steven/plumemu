package com.plumemu.scraper

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The stored JSON of [GameDetails]: `{"format":<version>,"details":{...}}`, fields at their
 * default (null, empty, false) left out. Read leniently, since what the library stored today is
 * read by every later version of the app, and a later version's JSON by this one.
 */
internal object GameDetailsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Serializable
    private class Stored(
        @SerialName("format") val format: Int = 0,
        @SerialName("details") val details: GameDetails? = null,
    )

    fun encode(details: GameDetails): String =
        json.encodeToString(Stored.serializer(), Stored(GameDetails.JSON_FORMAT, details))

    fun format(text: String): Int? = (root(text)?.get("format") as? JsonPrimitive)?.intOrNull

    /**
     * The details of [text], whatever its `format`: fields this version knows are read, others
     * skipped. A field that does not read (a wrong type) takes its default alone; in a list, an
     * entry that does not read (a media reference of an unknown kind or without a URL, a tag with
     * a wrong-typed field) is dropped alone. Null only when [text] has no `details` object.
     */
    fun decode(text: String): GameDetails? {
        val details = root(text)?.get("details") as? JsonObject ?: return null
        return read(details) ?: read(JsonObject(details.mapNotNull { (name, value) -> usable(name, value)?.let { name to it } }.toMap()))
            ?: GameDetails()
    }

    /**
     * [value] if field [name] reads with it; for a list, the entries that read alone (checked
     * again as a whole: a list given to a field that is not one reads with no entry at all).
     */
    private fun usable(name: String, value: JsonElement): JsonElement? {
        if (reads(name, value)) return value
        if (value !is JsonArray) return null
        return JsonArray(value.filter { entry -> reads(name, JsonArray(listOf(entry))) }).takeIf { reads(name, it) }
    }

    private fun reads(name: String, value: JsonElement): Boolean = read(JsonObject(mapOf(name to value))) != null

    private fun read(details: JsonObject): GameDetails? = try {
        json.decodeFromJsonElement(GameDetails.serializer(), details)
    } catch (e: IllegalArgumentException) {
        // SerializationException is one: a field of the wrong type.
        null
    }

    private fun root(text: String): JsonObject? = try {
        json.parseToJsonElement(text) as? JsonObject
    } catch (e: IllegalArgumentException) {
        null
    }
}
