package com.plumemu.graph

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.plumemu.home.localeOf
import com.plumemu.language.AppLanguage
import com.plumemu.language.AppLanguages
import com.plumemu.language.AppLocale
import com.plumemu.language.LanguageCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * The app's first language, followed for the life of the process ([AppGraph.locale]): the main
 * screen's A–Z sorts by it, as Settings does through `currentLocale()` ([localeOf] for both).
 *
 * Before Android 13 `onConfigurationChanged` hands over the system's configuration, not the
 * Application's wrapped one, so a rotation, a controller or a dark-mode change would flip the
 * sort back to the system's language: the picked language ([LanguageCache]) is put back on top.
 */
internal fun followedLocale(
    app: Context,
    languages: List<AppLanguage> = AppLanguages.all,
): StateFlow<Locale> {
    val cache = LanguageCache(app)

    fun appLocaleOf(config: Configuration): Locale {
        val system = localeOf(config)
        return AppLocale.localeToApply(cache.read(), system, Build.VERSION.SDK_INT, languages) ?: system
    }
    val current = MutableStateFlow(appLocaleOf(app.resources.configuration))
    app.registerComponentCallbacks(
        object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                current.value = appLocaleOf(newConfig)
            }

            @Deprecated("Still abstract in ComponentCallbacks; this listener frees nothing")
            override fun onLowMemory() = Unit
        },
    )
    return current.asStateFlow()
}
