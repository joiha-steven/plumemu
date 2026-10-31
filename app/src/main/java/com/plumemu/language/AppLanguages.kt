package com.plumemu.language

import java.util.Locale

/**
 * One language Plumemu ships (Settings › Advanced › Language; docs/conventions/translation.md).
 * [tag] is what is stored and what Android's per-app language page lists (`res/xml/locales_config.xml`);
 * [folder] the resource folder holding its `strings.xml`. Its name in its own language is that
 * folder's `language_name` string, so it is written by its translator, never in code.
 */
internal data class AppLanguage(
    val tag: String,
    val folder: String,
) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

/**
 * The one list of languages. A translation adds its `values-xx` folder and one line here;
 * `res/xml/locales_config.xml` lists the same tags (`AppLanguagesTest` and the translations guard
 * check the three agree). Folders are explained in translation.md.
 */
internal object AppLanguages {
    val all: List<AppLanguage> =
        listOf(
            AppLanguage("en", "values"),
            AppLanguage("es", "values-es"),
            AppLanguage("fr", "values-fr"),
            AppLanguage("vi", "values-vi"),
            AppLanguage("zh-CN", "values-zh"), // Simplified
            AppLanguage("zh-TW", "values-zh-rTW"), // Traditional
            AppLanguage("ko", "values-ko"),
            AppLanguage("ja", "values-ja"),
        )

    /** The language stored as [tag], or null for "System default" and for a tag no longer shipped. */
    fun byTag(
        tag: String?,
        languages: List<AppLanguage> = all,
    ): AppLanguage? = if (tag == null) null else languages.firstOrNull { it.tag.equals(tag, ignoreCase = true) }

    /**
     * The shipped language Android's per-app page set (Android 13+), matched by its tag, else by
     * language and script ("zh-Hant-TW" is zh-TW), else by language alone; null for none.
     */
    fun matching(
        locale: Locale?,
        languages: List<AppLanguage> = all,
    ): AppLanguage? {
        if (locale == null || locale.language.isEmpty()) return null
        byTag(locale.toLanguageTag(), languages)?.let { return it }
        val traditional = isTraditionalChinese(locale)
        val sameLanguage = languages.filter { it.locale.language == locale.language }
        return sameLanguage.firstOrNull { locale.language != "zh" || isTraditionalChinese(it.locale) == traditional }
            ?: sameLanguage.firstOrNull()
    }

    /** Traditional script: written Hant, or a region that writes it (Taiwan, Hong Kong, Macau). */
    fun isTraditionalChinese(locale: Locale): Boolean =
        locale.language == "zh" && (locale.script == "Hant" || (locale.script.isEmpty() && locale.country in TRADITIONAL_REGIONS))

    private val TRADITIONAL_REGIONS = setOf("TW", "HK", "MO")
}
