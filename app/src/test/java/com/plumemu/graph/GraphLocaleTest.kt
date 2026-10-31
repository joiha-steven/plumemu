package com.plumemu.graph

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plumemu.language.AppLanguage
import com.plumemu.language.LanguageCache
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The A–Z locale keeps the picked language through configuration changes before Android 13
 * (`onConfigurationChanged` hands over the system's configuration, so a
 * rotation, a controller or dark mode flipped the sort back to the system's language).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class GraphLocaleTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val languages = listOf(AppLanguage("en", "values"), AppLanguage("vi", "values-vi"))

    private fun systemChangesTo(tag: String) {
        val config = Configuration(app.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        config.orientation = Configuration.ORIENTATION_LANDSCAPE
        app.onConfigurationChanged(config)
    }

    @Test
    fun aPickedLanguageStaysAfterAConfigurationChange() {
        LanguageCache(app).write("vi")
        val followed = followedLocale(app, languages)
        assertEquals(Locale.forLanguageTag("vi"), followed.value)
        systemChangesTo("fr-FR")
        assertEquals(Locale.forLanguageTag("vi"), followed.value)
    }

    @Test
    fun systemDefaultFollowsTheSystemsLanguage() {
        LanguageCache(app).write(null)
        val followed = followedLocale(app, languages)
        systemChangesTo("fr-FR")
        assertEquals(Locale.forLanguageTag("fr-FR"), followed.value)
    }
}
