package com.plumemu.achievements.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Readers that take each field on its own. RetroAchievements writes some numbers as text and some
 * as numbers, and writes an empty object as `[]`; a strict reader would fail the whole answer on
 * one field of an unexpected shape. Only the named fields are ever read: anything else in the
 * answer (trigger logic included) is never looked at nor kept.
 */

internal val lenientJson: Json = Json { ignoreUnknownKeys = true }

/** Thrown by the parsers for an answer that is not the documented shape; the client maps it to BadAnswer. */
internal class UnexpectedAnswer(why: String) : Exception(why)

/** The whole body as JSON; a blank or malformed body is an [UnexpectedAnswer]. */
internal fun parseBody(body: String): JsonElement {
    if (body.isBlank()) throw UnexpectedAnswer("empty body")
    return try {
        lenientJson.parseToJsonElement(body)
    } catch (e: IllegalArgumentException) {
        // SerializationException is one.
        throw UnexpectedAnswer("malformed JSON")
    }
}

/** A string, number or boolean as its text; null for null, absent, an object or a list. */
internal fun JsonObject.text(name: String): String? {
    val element = this[name]
    return if (element is JsonPrimitive && element !is JsonNull) element.content else null
}

/** A whole number written as a number or as text ("12", 12, 12.0); null otherwise. */
internal fun JsonObject.int(name: String): Int? {
    val raw = text(name) ?: return null
    return raw.toIntOrNull() ?: raw.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 && it in INT_RANGE }?.toInt()
}

/** The objects of a list, or of an object keyed by id (RetroAchievements writes both); others are dropped. */
internal fun JsonElement?.objects(): List<JsonObject> = when (this) {
    is JsonArray -> filterIsInstance<JsonObject>()
    is JsonObject -> values.filterIsInstance<JsonObject>()
    else -> emptyList()
}

/** The texts of a list of strings; anything else in it is dropped. */
internal fun JsonElement?.texts(): List<String> =
    (this as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()

private val INT_RANGE = Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
