package com.plumemu.language

import android.app.Application
import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.display.DisplayManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plumemu.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import java.util.Locale

/** Settings › Advanced › Language applied on every API level (AppLocale, LanguageSync; translation.md › Choosing a language). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class AppLocaleTest {
    private val planned =
        listOf(
            AppLanguage("en", "values"),
            AppLanguage("es", "values-es"),
            AppLanguage("vi", "values-vi"),
            AppLanguage("zh-CN", "values-zh"),
            AppLanguage("zh-TW", "values-zh-rTW"),
            AppLanguage("ko", "values-ko"),
            AppLanguage("ja", "values-ja"),
        )
    private val m = Build.VERSION_CODES.M
    private val n = Build.VERSION_CODES.N
    private val sv2 = Build.VERSION_CODES.S_V2
    private val t = Build.VERSION_CODES.TIRAMISU

    private fun apply(
        stored: String?,
        system: String,
        sdk: Int,
    ): String? = AppLocale.localeToApply(stored, Locale.forLanguageTag(system), sdk, planned)?.toLanguageTag()

    @Test
    fun aPickedLanguageIsAppliedBeforeAndroid13AndLeftToAndroidFrom13() {
        assertEquals("vi", apply("vi", "en-US", m))
        assertEquals("zh-TW", apply("zh-TW", "en-US", sv2))
        assertNull("Android 13 applies its own per-app language", apply("vi", "en-US", t))
        assertNull("System default leaves Android's", apply(null, "ja-JP", sv2))
        assertNull("a tag no longer shipped is System default", apply("fr", "en-US", m))
    }

    @Test
    fun onAndroid6ATraditionalChineseSystemOutsideTaiwanGetsTheTraditionalFolder() {
        // Before Android 7 resources match the exact region only: zh-HK would fall to values-zh (Simplified).
        assertEquals("zh-TW", apply(null, "zh-HK", m))
        assertEquals("zh-TW", apply(null, "zh-MO", m))
        assertNull("Taiwan already matches values-zh-rTW", apply(null, "zh-TW", m))
        assertNull("Simplified regions match values-zh", apply(null, "zh-CN", m))
        assertNull(apply(null, "zh-SG", m))
        assertNull("Android 7 matches by script itself", apply(null, "zh-HK", n))
        assertNull("no Traditional folder yet: nothing to point at", AppLocale.localeToApply(null, Locale.forLanguageTag("zh-HK"), m, planned.take(4)))
    }

    @Test
    fun androidsPerAppPageMatchesTheShippedChineseByScript() {
        assertEquals("zh-TW", AppLanguages.matching(Locale.forLanguageTag("zh-Hant-TW"), planned)?.tag)
        assertEquals("zh-TW", AppLanguages.matching(Locale.forLanguageTag("zh-HK"), planned)?.tag)
        assertEquals("zh-CN", AppLanguages.matching(Locale.forLanguageTag("zh-Hans-CN"), planned)?.tag)
        assertEquals("zh-CN", AppLanguages.matching(Locale.forLanguageTag("zh-SG"), planned)?.tag)
        assertEquals("es", AppLanguages.matching(Locale.forLanguageTag("es-MX"), planned)?.tag)
    }

    @Test
    fun beforeAndroid13AChangedLanguageRecreatesTheActivity() {
        assertEquals(LanguageStep.None, LanguageSync.decide(sv2, stored = null, applied = null, system = null, synced = null))
        assertEquals(LanguageStep.Recreate("en"), LanguageSync.decide(m, stored = "en", applied = null, system = null, synced = null))
        assertEquals(LanguageStep.Recreate(null), LanguageSync.decide(m, stored = null, applied = "en", system = null, synced = "en"))
        assertEquals(LanguageStep.None, LanguageSync.decide(m, stored = "en", applied = "en", system = null, synced = "en"))
        assertEquals("a tag no longer shipped is System default", LanguageStep.None, LanguageSync.decide(m, stored = "xx", applied = null, system = null, synced = null))
    }

    @Test
    fun fromAndroid13TheNewerSideWins() {
        assertEquals("Plumemu's row changed it", LanguageStep.ApplyToSystem("en"), LanguageSync.decide(t, stored = "en", applied = null, system = null, synced = null))
        assertEquals("Android's page changed it", LanguageStep.StoreSystems(null), LanguageSync.decide(t, stored = "en", applied = null, system = null, synced = "en"))
        assertEquals("Android's page picked it", LanguageStep.StoreSystems("en"), LanguageSync.decide(t, stored = null, applied = null, system = "en", synced = null))
        assertEquals("a restore set System default", LanguageStep.ApplyToSystem(null), LanguageSync.decide(t, stored = null, applied = null, system = "en", synced = "en"))
        assertEquals(LanguageStep.None, LanguageSync.decide(t, stored = "en", applied = null, system = "en", synced = "en"))
    }

    @Test
    fun wrapPutsTheCachedLanguageInTheConfigurationAndNothingElse() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        LanguageCache(app).write(null)
        assertSame("System default keeps the context", app, AppLocale.wrap(app))
        LanguageCache(app).write("en")
        val wrapped = AppLocale.wrap(app)
        assertEquals(Locale.forLanguageTag("en"), wrapped.resources.configuration.locales[0])
        assertEquals("English", wrapped.getString(R.string.language_name))
        assertEquals("Compose reads a text's locale from the process default", Locale.forLanguageTag("en"), Locale.getDefault())
        LanguageCache(app).write(null)
        AppLocale.wrap(app)
        assertEquals("System default puts the system's back", Resources.getSystem().configuration.locales[0], Locale.getDefault())
    }

    @Test
    fun anUpgradeFrom12To13HandsThePickedLanguageToAndroidRatherThanWipingIt() {
        // The pre-13 cache was read as "last agreed with Android", so Android's
        // empty per-app language looked like a change in its page and Vietnamese became System default.
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cache = LanguageCache(app)
        cache.write("vi") // what Android 12 left behind
        assertNull("nothing was ever agreed with Android", cache.readSynced())
        assertEquals(
            LanguageStep.ApplyToSystem("vi"),
            LanguageSync.decide(t, stored = "vi", applied = null, system = null, synced = cache.readSynced(), languages = planned),
        )
        cache.writeSynced("vi")
        assertEquals("the two tags are kept apart", "vi", cache.read())
        cache.write(null)
        assertEquals("vi", cache.readSynced())
    }

    @Test
    fun theWrappedApplicationContextStillFollowsDarkModeAndTheScreen() {
        // A copy of the whole configuration as the override froze uiMode,
        // orientation, size and density for the Application's and the workers' contexts.
        val app = ApplicationProvider.getApplicationContext<Application>()
        LanguageCache(app).write("en")
        val wrapped = AppLocale.wrap(app)
        RuntimeEnvironment.setQualifiers("+land-night")
        val config = wrapped.resources.configuration
        assertEquals(Configuration.UI_MODE_NIGHT_YES, config.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, config.orientation)
        assertEquals("the language stays", Locale.forLanguageTag("en"), config.locales[0])
    }

    @Test
    fun aDisplayContextGetsThePickedLanguageBackAndKeepsItsDisplay() {
        // Presentation makes its context with createDisplayContext, which drops
        // the override below Android 13, so the second screen stayed in the system's language.
        val app = ApplicationProvider.getApplicationContext<Application>()
        LanguageCache(app).write("vi")
        val displays = app.getSystemService(DisplayManager::class.java)
        val display = displays.getDisplay(ShadowDisplayManager.addDisplay("w640dp-h480dp-mdpi"))
        val displayContext = AppLocale.wrap(app).createDisplayContext(display)
        val localized = AppLocale.localized(displayContext, planned)
        assertEquals(Locale.forLanguageTag("vi"), localized.resources.configuration.locales[0])
        assertEquals(displayContext.resources.displayMetrics.widthPixels, localized.resources.displayMetrics.widthPixels)
        assertEquals(displayContext.resources.displayMetrics.densityDpi, localized.resources.displayMetrics.densityDpi)
        LanguageCache(app).write(null)
        assertSame("System default keeps the context", displayContext, AppLocale.localized(displayContext, planned))
    }

    @Test
    fun aLanguageIsListedInItsOwnName() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals("English", ownName(app, AppLanguages.all.first()))
    }
}
