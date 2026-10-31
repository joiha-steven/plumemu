package com.plumemu.language

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.plumemu.PlumApplication
import kotlinx.coroutines.launch

/**
 * An Activity in the language of Settings › Advanced › Language ([AppLocale]). MainActivity follows the
 * stored language while it lives ([followsLanguage]: a change recreates it, or goes to Android's per-app
 * language from Android 13); PinShortcutActivity, a short confirm screen, only starts in it.
 */
abstract class LocalizedActivity(private val followsLanguage: Boolean) : ComponentActivity() {
    /** The tag this Activity's context was made with ([LanguageCache] at attach); null: System default. */
    private var appliedLanguage: String? = null

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = LanguageCache(newBase).read()
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!followsLanguage) return
        val graph = (application as PlumApplication).graph
        lifecycleScope.launch {
            followLanguage(
                this@LocalizedActivity,
                graph.settings,
                appliedLanguage,
                store = { tag -> graph.settingsWriter.submit { it.copy(appearance = it.appearance.copy(language = tag)) } },
                log = { Log.i(TAG, it) },
            )
        }
    }

    private companion object {
        const val TAG = "Plumemu"
    }
}
