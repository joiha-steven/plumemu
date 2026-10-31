package com.plumemu.home

import android.content.res.Resources
import com.plumemu.R
import com.plumemu.identify.name.VariantTag
import com.plumemu.identify.name.VariantTags
import java.util.Locale

/*
 * Rows of one system that show the same title ("Pokemon Emerald" and "Pokemon Emerald (Rev 1)" are kept
 * apart by the scan) get the words that tell them apart after the title. The
 * tags are read from the file names once per list, here, never while drawing.
 */

/**
 * [rows] with [GameRowModel.variantTags] set on every row whose title another row of the same system
 * shares; a row with a unique title is returned as it is, so a list with no collision costs one pass
 * over the titles and no file name is parsed.
 */
fun withVariantTags(rows: List<GameRowModel>): List<GameRowModel> {
    val seen = HashMap<Pair<String, String>, Int>()
    for (row in rows) if (row.fileName.isNotEmpty()) seen[row.systemId to row.title] = (seen[row.systemId to row.title] ?: 0) + 1
    if (seen.values.none { it >= 2 }) return rows
    val tagged = rows.map { row ->
        if (row.fileName.isEmpty() || seen.getValue(row.systemId to row.title) < 2) {
            row
        } else {
            row.copy(variantTags = VariantTags.of(row.fileName.substringBeforeLast('.')))
        }
    }
    // Tags that do not tell two rows apart (both empty, say): the region does, when the row has one.
    val sameTags = HashMap<Triple<String, String, List<VariantTag>>, Int>()
    for (row in tagged) {
        if (row.fileName.isEmpty()) continue
        val key = Triple(row.systemId, row.title, row.variantTags)
        sameTags[key] = (sameTags[key] ?: 0) + 1
    }
    return tagged.map { row ->
        if (row.fileName.isNotEmpty() && sameTags.getValue(Triple(row.systemId, row.title, row.variantTags)) >= 2) row.copy(variantRegion = row.region) else row
    }
}

/** The words for [tags], joined by commas ("Rev 1, Hack"); empty when there is no tag. The language is named in the person's language. */
internal fun variantLabel(
    resources: Resources,
    tags: List<VariantTag>,
    locale: Locale = Locale.getDefault(), // the app sets the process default to the picked language (language/AppLocale)
): String =
    tags.map { variantWord(resources, it, locale) }.reduceOrNull { all, next -> resources.getString(R.string.variant_join, all, next) }.orEmpty()

private fun variantWord(
    resources: Resources,
    tag: VariantTag,
    locale: Locale,
): String =
    when (tag) {
        is VariantTag.Revision -> resources.getString(R.string.variant_revision, tag.text)
        VariantTag.Beta -> resources.getString(R.string.variant_beta)
        VariantTag.Prototype -> resources.getString(R.string.variant_prototype)
        VariantTag.Demo -> resources.getString(R.string.variant_demo)
        VariantTag.Hack -> resources.getString(R.string.variant_hack)
        VariantTag.Unlicensed -> resources.getString(R.string.variant_unlicensed)
        VariantTag.Pirate -> resources.getString(R.string.variant_pirate)
        is VariantTag.Translation -> {
            val language = tag.language?.let { languageName(it, locale) }
            if (language == null) resources.getString(R.string.variant_translation) else resources.getString(R.string.variant_translation_in, language)
        }
    }

/** Locales whose language names are lower case inside a sentence ("Ban dich tieng Anh" in Vietnamese), though the platform capitalises them. */
private val LOWER_CASE_NAMES = setOf("vi", "es", "fr")

internal fun languageName(
    code: String,
    locale: Locale,
): String? {
    val name = Locale.forLanguageTag(code).getDisplayLanguage(locale).takeIf { it.isNotEmpty() } ?: return null
    return if (locale.language in LOWER_CASE_NAMES) name.replaceFirstChar { it.lowercase(locale) } else name
}
