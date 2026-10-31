package com.plumemu.language

import android.app.Activity
import android.os.Build
import com.plumemu.coredata.settings.PlumSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map

/**
 * Keeps [activity] in the stored language for as long as it collects (MainActivity, from onCreate).
 * [applied] is the tag its context was made with ([LanguageCache] at attachBaseContext); [store]
 * writes a tag into the settings (Android's per-app page changed it, Android 13+).
 */
internal suspend fun followLanguage(
    activity: Activity,
    settings: Flow<PlumSettings?>,
    applied: String?,
    store: (String?) -> Unit,
    log: (String) -> Unit,
) {
    val cache = LanguageCache(activity)
    val sdk = Build.VERSION.SDK_INT
    settings.filterNotNull().map { it.appearance.language }.distinctUntilChanged().collect { stored ->
        val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) AppLocale.systemAppLanguage(activity) else null
        when (val step = LanguageSync.decide(sdk, stored, applied, system, cache.readSynced())) {
            LanguageStep.None -> Unit
            is LanguageStep.Recreate -> {
                log("Language: now ${step.tag ?: "system default"}; recreating the screen")
                cache.write(step.tag)
                activity.recreate()
            }
            is LanguageStep.StoreSystems -> {
                log("Language: Android's per-app language is ${step.tag ?: "system default"}; stored")
                cache.writeSynced(step.tag)
                store(step.tag)
            }
            is LanguageStep.ApplyToSystem ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    log("Language: now ${step.tag ?: "system default"}; handed to Android")
                    cache.writeSynced(step.tag)
                    AppLocale.setSystemAppLanguage(activity, step.tag)
                }
        }
    }
}
