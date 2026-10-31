package com.plumemu.language

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Applies Settings › Advanced › Language (docs/conventions/translation.md › Choosing a language).
 *
 * - Android 6 to 12: every Activity and the Application wrap their base context in a configuration
 *   with the picked locale ([wrap]). The pick is read from [LanguageCache], a tiny synchronous file,
 *   because the settings store answers only after the first frame.
 * - Android 13 and later: Android's own per-app language ([LocaleManager]) does it, so the system's
 *   per-app language page and Plumemu's row always agree ([LanguageSync]).
 */
internal object AppLocale {
    /** [base] with the stored language applied; [base] itself when nothing needs changing. */
    fun wrap(base: Context): Context {
        val sdk = Build.VERSION.SDK_INT
        val locale = localeToApply(LanguageCache(base).read(), systemLocaleOf(base.resources.configuration), sdk)
        // Compose picks a text's locale from the process default, not from the context: without
        // this, Japanese picked on a Chinese system would draw Chinese forms of the shared Han
        // characters. From Android 13 Android sets the default itself.
        if (sdk < Build.VERSION_CODES.TIRAMISU) setProcessDefault(locale)
        return if (locale == null) base else withLocale(base, locale)
    }

    /**
     * [context] with the stored language and the process default left alone: for a context made
     * from an already wrapped one that lost the language on the way, such as a `Presentation`'s
     * (`createDisplayContext` drops a configuration override before Android 13).
     */
    fun localized(
        context: Context,
        languages: List<AppLanguage> = AppLanguages.all,
    ): Context {
        val locale =
            localeToApply(LanguageCache(context).read(), systemLocaleOf(context.resources.configuration), Build.VERSION.SDK_INT, languages)
                ?: return context
        return withLocale(context, locale)
    }

    /**
     * An override that holds the locale only. A copy of [base]'s whole configuration would freeze
     * its night mode, orientation, size and density for the life of the context (the Application's
     * and the workers' contexts live as long as the process); unset fields follow [base].
     */
    private fun withLocale(
        base: Context,
        locale: Locale,
    ): Context {
        val override = Configuration()
        override.setLocale(locale)
        return base.createConfigurationContext(override)
    }

    /** [locale] as the process default, or the system's own list again for null (System default). */
    private fun setProcessDefault(locale: Locale?) {
        val system = Resources.getSystem().configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList.setDefault(if (locale != null) LocaleList(locale) else system.locales)
        } else {
            Locale.setDefault(locale ?: systemLocaleOf(system))
        }
    }

    /**
     * The locale to put in the configuration, or null to leave Android's. [stored] is the picked
     * tag (null: System default). Before Android 7, resources match a locale only by its exact
     * region, so a Traditional Chinese system other than Taiwan (Hong Kong, Macau) would get the
     * Simplified folder (`values-zh`): it is pointed at Taiwan's, the one Traditional folder.
     */
    fun localeToApply(
        stored: String?,
        system: Locale,
        sdk: Int,
        languages: List<AppLanguage> = AppLanguages.all,
    ): Locale? {
        if (sdk >= Build.VERSION_CODES.TIRAMISU) return null
        AppLanguages.byTag(stored, languages)?.let { return it.locale }
        if (sdk < Build.VERSION_CODES.N && AppLanguages.isTraditionalChinese(system) && system.country != TAIWAN) {
            return languages.firstOrNull { it.locale.language == "zh" && it.locale.country == TAIWAN }?.locale
        }
        return null
    }

    @Suppress("DEPRECATION") // Configuration.locale is the only field before Android 7.
    private fun systemLocaleOf(config: Configuration): Locale =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.locales[0] else config.locale

    /** The per-app language Android holds (Android 13+), as a shipped language's tag; null for System default. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun systemAppLanguage(context: Context): String? {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales ?: return null
        return if (locales.isEmpty) null else AppLanguages.matching(locales[0])?.tag
    }

    /** Hands [tag] to Android's per-app language (Android 13+); Android recreates the Activities itself. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun setSystemAppLanguage(
        context: Context,
        tag: String?,
    ) {
        val locales = AppLanguages.byTag(tag)?.let { LocaleList(it.locale) } ?: LocaleList.getEmptyLocaleList()
        context.getSystemService(LocaleManager::class.java)?.setApplicationLocales(locales)
    }

    private const val TAIWAN = "TW"
}

/**
 * Two tags beside the settings, in a SharedPreferences file because [AppLocale.wrap] runs in
 * `attachBaseContext`, before anything else:
 * - [read] / [write]: before Android 13, the language [AppLocale.wrap] puts in every context;
 * - [readSynced] / [writeSynced]: from 13, the language last agreed with Android's per-app language.
 * They are kept apart because a phone upgraded from 12 to 13 still has the first: read as "last
 * agreed with Android", it made Android's empty per-app language look like a change made in
 * Android's page, and the picked language was wiped to System default.
 */
internal class LanguageCache(context: Context) {
    // The application context may not exist yet in Application.attachBaseContext: use the one given.
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): String? = prefs.getString(KEY_APPLIED, null)

    /** Written at once (commit): the Activity is recreated right after and reads it in attachBaseContext. */
    fun write(tag: String?) = put(KEY_APPLIED, tag)

    fun readSynced(): String? = prefs.getString(KEY_SYNCED, null)

    fun writeSynced(tag: String?) = put(KEY_SYNCED, tag)

    @SuppressLint("ApplySharedPref") // commit(), not apply(): the recreated Activity reads the value at once.
    private fun put(
        key: String,
        tag: String?,
    ) {
        if (tag == prefs.getString(key, null)) return
        prefs.edit().apply { if (tag == null) remove(key) else putString(key, tag) }.commit()
    }

    private companion object {
        const val FILE_NAME = "language_cache"
        const val KEY_APPLIED = "tag"
        const val KEY_SYNCED = "synced_with_android"
    }
}

/** What to do when the stored language, Android's per-app language and the cache may differ. */
internal sealed interface LanguageStep {
    data object None : LanguageStep

    /** Before Android 13: the Activity runs in another language than the stored one; cache [tag] and recreate it. */
    data class Recreate(val tag: String?) : LanguageStep

    /** Android 13+: the person changed it in Android's page; store [tag] in the settings. */
    data class StoreSystems(val tag: String?) : LanguageStep

    /** Android 13+: the settings changed (Plumemu's row, a restore, Reset all settings); hand [tag] to Android. */
    data class ApplyToSystem(val tag: String?) : LanguageStep
}

internal object LanguageSync {
    /**
     * [stored] is the settings' tag, [applied] the tag this Activity was made with (before 13),
     * [system] Android's per-app language and [synced] the tag last agreed with it (13+,
     * [LanguageCache.readSynced], never the pre-13 [LanguageCache.read]). Tags no longer shipped read
     * as System default.
     */
    fun decide(
        sdk: Int,
        stored: String?,
        applied: String?,
        system: String?,
        synced: String?,
        languages: List<AppLanguage> = AppLanguages.all,
    ): LanguageStep {
        val want = AppLanguages.byTag(stored, languages)?.tag
        if (sdk < Build.VERSION_CODES.TIRAMISU) {
            return if (want != AppLanguages.byTag(applied, languages)?.tag) LanguageStep.Recreate(want) else LanguageStep.None
        }
        val systems = AppLanguages.byTag(system, languages)?.tag
        return when {
            systems != AppLanguages.byTag(synced, languages)?.tag -> LanguageStep.StoreSystems(systems)
            want != systems -> LanguageStep.ApplyToSystem(want)
            else -> LanguageStep.None
        }
    }
}
