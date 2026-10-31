package com.plumemu.platforms

import java.io.InputStream
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reads platforms.json (the shipped copy, or a downloaded one) into a [PlatformCatalog].
 * A bad file gives [PlatformLoadResult.Failed] and one log line; it never throws and never
 * returns an empty catalog: no systems or no emulators is itself a failure.
 */
object PlatformLoader {
    private val json = Json { ignoreUnknownKeys = true }

    /** [source] names the file in messages, for example "assets/platforms.json". */
    fun load(text: String, source: String): PlatformLoadResult = load(text, source, AndroidPlatformLog)

    fun load(stream: InputStream, source: String): PlatformLoadResult {
        val text = try {
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: java.io.IOException) {
            return fail(PlatformFailure.NotJson(source, "cannot read: ${e.message}"), AndroidPlatformLog)
        }
        return load(text, source, AndroidPlatformLog)
    }

    internal fun load(text: String, source: String, log: PlatformLog): PlatformLoadResult {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
                ?: return fail(PlatformFailure.NotJson(source, "top level is not an object"), log)
        } catch (e: SerializationException) {
            return fail(PlatformFailure.NotJson(source, e.message ?: "unreadable"), log)
        }
        val version = (root["formatVersion"] as? JsonPrimitive)?.intOrNull
        if (version != SUPPORTED_FORMAT_VERSION) return fail(PlatformFailure.UnsupportedVersion(source, version), log)
        val failure = try {
            val raw = RawCatalog(
                version,
                decodeList(source, root, "systems", RawSystem.serializer()),
                decodeList(source, root, "emulators", RawEmulator.serializer()),
                decodeKnownApps(source, root),
            )
            return PlatformLoadResult.Loaded(CatalogBuilder(source).build(raw))
        } catch (e: RejectedException) {
            e.failure
        }
        return fail(failure, log)
    }

    /** The optional `knownApps` object: package name to system id, both strings. */
    private fun decodeKnownApps(source: String, root: JsonObject): Map<String, String> {
        val element = root["knownApps"] ?: return emptyMap()
        val obj = element as? JsonObject
            ?: throw RejectedException(PlatformFailure.WrongType(source, "$.knownApps", null, "expected an object"))
        return obj.mapValues { (name, value) ->
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw RejectedException(PlatformFailure.WrongType(source, "$.knownApps.$name", null, "expected a system id string"))
        }
    }

    /**
     * Decodes one list of the file element by element, so an error can name `$.systems[12].folders`
     * and the id of the element; the decoder alone only knows the path inside the element.
     */
    private fun <T> decodeList(source: String, root: JsonObject, name: String, serializer: KSerializer<T>): List<T> {
        val list = root[name] as? JsonArray
            ?: throw RejectedException(
                if (name in root) PlatformFailure.WrongType(source, "$.$name", null, "expected a list")
                else PlatformFailure.MissingField(source, name, "$", null),
            )
        return list.mapIndexed { index, element ->
            val base = "$.$name[$index]"
            val id = ((element as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
            try {
                json.decodeFromJsonElement(serializer, element)
            } catch (e: MissingFieldException) {
                throw RejectedException(
                    PlatformFailure.MissingField(source, e.missingFields.joinToString(), base + pathIn(e.message), id),
                )
            } catch (e: SerializationException) {
                throw RejectedException(PlatformFailure.WrongType(source, base + pathIn(e.message), id, firstLine(e)))
            } catch (e: IllegalArgumentException) {
                throw RejectedException(PlatformFailure.WrongType(source, base + pathIn(e.message), id, firstLine(e)))
            }
        }
    }

    private fun firstLine(e: Exception) = e.message?.lineSequence()?.first() ?: "unreadable"

    /** The path inside the element, from kotlinx.serialization's message: `$.folders` becomes `.folders`. */
    private fun pathIn(message: String?): String =
        Regex("""\$([A-Za-z0-9_.\[\]']*)""").find(message.orEmpty())?.groupValues?.get(1).orEmpty()

    private fun fail(failure: PlatformFailure, log: PlatformLog): PlatformLoadResult {
        log.error("Platform data rejected: ${failure.describe()}")
        return PlatformLoadResult.Failed(failure)
    }
}
