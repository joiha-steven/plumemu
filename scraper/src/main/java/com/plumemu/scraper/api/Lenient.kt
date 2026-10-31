package com.plumemu.scraper.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Readers for the fields added for the details. A details field must never cost a
 * match: a strict reader fails the whole answer on one field of an unexpected shape, and in a name
 * search one odd game among 30 would lose the search. Seen live: `jeuRecherche.php`
 * writes `"couleurs"` as a one-letter string where `jeuInfos.php` writes a list of objects.
 */

/** A string, number or boolean as its text; anything else (an object, a list) is null. */
internal object LenientText : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.plumemu.scraper.api.LenientText", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = json.decodeJsonElement()
        return if (element is JsonPrimitive && element !is JsonNull) element.content else null
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }
}

/**
 * A list of objects that is empty when ScreenScraper writes something else; an entry that is not
 * an object, or does not read, is dropped alone.
 */
internal abstract class LenientList<T>(private val element: KSerializer<T>) : KSerializer<List<T>> {
    private val strict = ListSerializer(element)
    override val descriptor: SerialDescriptor = strict.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        val json = decoder as? JsonDecoder ?: return strict.deserialize(decoder)
        val array = json.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            if (item !is JsonObject) return@mapNotNull null
            try {
                json.json.decodeFromJsonElement(element, item)
            } catch (e: IllegalArgumentException) {
                // SerializationException is one.
                null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>) = strict.serialize(encoder, value)
}

internal object LenientTagList : LenientList<ApiGenre>(ApiGenre.serializer())

internal object LenientLanguageTextList : LenientList<LanguageText>(LanguageText.serializer())

internal object LenientClassificationList : LenientList<ApiClassification>(ApiClassification.serializer())

internal object LenientColourList : LenientList<ApiColour>(ApiColour.serializer())
