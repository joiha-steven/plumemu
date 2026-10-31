package com.plumemu.identify.name

import java.text.Normalizer
import java.util.Locale

/**
 * What a file name's tag says about which release of a game this is, in the words a person uses:
 * the part of [CleanTitle.variantKey] that has a plain name. A tag this does not know has no
 * [VariantTag]; the app shows nothing for it.
 */
sealed interface VariantTag {
    /** `(Rev 1)`, `(Rev A)`: [text] as written after "Rev". */
    data class Revision(val text: String) : VariantTag

    data object Beta : VariantTag

    data object Prototype : VariantTag

    data object Demo : VariantTag

    data object Hack : VariantTag

    /** `[T+Eng]`: [language] is the two-letter code ("en"), null for a translation whose language is not known. */
    data class Translation(val language: String?) : VariantTag

    data object Unlicensed : VariantTag

    data object Pirate : VariantTag
}

/** Reads the [VariantTag]s of a file name. Pure; the label words are the app's (strings, translated). */
object VariantTags {
    private val revision = Regex("""rev(?:ision)?(?:\s*\.\s*|\s+|(?=\d))([0-9a-z][0-9a-z.]*)""", RegexOption.IGNORE_CASE)
    private val beta = Regex("""beta(?:\s*\d+)?""", RegexOption.IGNORE_CASE)
    private val prototype = Regex("""proto(?:type)?(?:\s*\d+)?""", RegexOption.IGNORE_CASE)
    private val demo = Regex("""demo(?:\s*\d+)?""", RegexOption.IGNORE_CASE)
    private val hack = Regex("""hack|h\d+[a-z]{0,3}""", RegexOption.IGNORE_CASE)
    private val translation = Regex("""t([+-])([a-z]{2,3})(?:[\s_].*)?""", RegexOption.IGNORE_CASE)
    private val translated = Regex("""translat(?:ed|ion)\b.*""", RegexOption.IGNORE_CASE)
    private val unlicensed = Regex("""unl(?:icensed)?""", RegexOption.IGNORE_CASE)
    private val pirate = Regex("""pirate|p\d+""", RegexOption.IGNORE_CASE)

    /** GoodTools and No-Intro language codes (three-letter and two-letter) and the two-letter code each stands for. */
    private val LANGUAGES: Map<String, String> =
        mapOf(
            "eng" to "en", "en" to "en", "spa" to "es", "es" to "es", "fre" to "fr", "fra" to "fr", "fr" to "fr",
            "ger" to "de", "deu" to "de", "de" to "de", "ita" to "it", "it" to "it", "por" to "pt", "pt" to "pt",
            "rus" to "ru", "ru" to "ru", "jpn" to "ja", "jap" to "ja", "ja" to "ja", "kor" to "ko", "ko" to "ko",
            "chi" to "zh", "zho" to "zh", "zh" to "zh", "dut" to "nl", "nld" to "nl", "nl" to "nl",
            "swe" to "sv", "sv" to "sv", "pol" to "pl", "pl" to "pl", "tur" to "tr", "tr" to "tr",
            "ara" to "ar", "ar" to "ar", "cze" to "cs", "ces" to "cs", "cs" to "cs", "fin" to "fi", "fi" to "fi",
            "hun" to "hu", "hu" to "hu", "vie" to "vi", "vi" to "vi", "ind" to "id", "id" to "id",
        )

    /** The tags of [baseName] (the file name without its extension), in the order they are written, each once. */
    fun of(baseName: String): List<VariantTag> =
        TitleCleaner.variantGroups(Normalizer.normalize(baseName, Normalizer.Form.NFKC)).mapNotNull(::tagOf).distinct()

    private fun tagOf(group: String): VariantTag? {
        val text = group.trim()
        revision.matchEntire(text)?.let { return VariantTag.Revision(it.groupValues[1].uppercase(Locale.ROOT)) }
        translation.matchEntire(text)?.let { return VariantTag.Translation(LANGUAGES[it.groupValues[2].lowercase(Locale.ROOT)]) }
        return when {
            beta.matches(text) -> VariantTag.Beta
            prototype.matches(text) -> VariantTag.Prototype
            demo.matches(text) -> VariantTag.Demo
            hack.matches(text) -> VariantTag.Hack
            translated.matches(text) -> VariantTag.Translation(null)
            unlicensed.matches(text) -> VariantTag.Unlicensed
            pirate.matches(text) -> VariantTag.Pirate
            else -> null
        }
    }
}
