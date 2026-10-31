package com.plumemu.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * The one list of languages agrees with what ships (docs/conventions/translation.md › Adding a
 * language): `res/xml/locales_config.xml` has the same tags in the same order, every language's
 * folder exists with a `strings.xml`, and no translation folder is missing from the list.
 */
class AppLanguagesTest {
    private val res = File(System.getProperty("user.dir"), "src/main/res")

    @Test
    fun localesConfigListsTheSameTagsInTheSameOrder() {
        val config = File(res, "xml/locales_config.xml")
        assertTrue("no $config", config.isFile)
        val tags = Regex("""<locale\s+android:name="([^"]+)"""").findAll(config.readText()).map { it.groupValues[1] }.toList()
        assertEquals(AppLanguages.all.map { it.tag }, tags)
    }

    @Test
    fun everyLanguageHasItsFolderAndEveryTranslationFolderIsListed() {
        assertTrue("no $res (unit tests run in the module's folder)", res.isDirectory)
        for (language in AppLanguages.all) {
            assertTrue("${language.tag}: no ${language.folder}/strings.xml", File(res, "${language.folder}/strings.xml").isFile)
        }
        val translated = res.listFiles().orEmpty().filter { it.name.startsWith("values-") && File(it, "strings.xml").isFile }.map { it.name }.sorted()
        assertEquals("translation folders not in AppLanguages", emptyList<String>(), translated - AppLanguages.all.map { it.folder }.toSet())
    }

    @Test
    fun englishComesFirstAndTagsAreUnique() {
        assertEquals("en", AppLanguages.all.first().tag)
        assertEquals("values", AppLanguages.all.first().folder)
        assertEquals(AppLanguages.all.size, AppLanguages.all.map { it.tag.lowercase(Locale.ROOT) }.toSet().size)
        assertEquals(AppLanguages.all.size, AppLanguages.all.map { it.folder }.toSet().size)
    }

    @Test
    fun byTagIgnoresCaseAndUnknownTagsAreSystemDefault() {
        assertEquals("en", AppLanguages.byTag("EN")?.tag)
        assertNull(AppLanguages.byTag(null))
        assertNull(AppLanguages.byTag("xx"))
    }

    @Test
    fun androidsPerAppLocaleMatchesByTagThenLanguage() {
        assertEquals("en", AppLanguages.matching(Locale.forLanguageTag("en"))?.tag)
        assertEquals("a region English has no folder for is still English", "en", AppLanguages.matching(Locale.forLanguageTag("en-GB"))?.tag)
        assertEquals("a region French has no folder for is still French", "fr", AppLanguages.matching(Locale.forLanguageTag("fr-CA"))?.tag)
        assertNull(AppLanguages.matching(Locale.forLanguageTag("de")))
        assertNull(AppLanguages.matching(null))
    }

    @Test
    fun traditionalChineseIsTheHantScriptOrATraditionalRegion() {
        assertTrue(AppLanguages.isTraditionalChinese(Locale.forLanguageTag("zh-TW")))
        assertTrue(AppLanguages.isTraditionalChinese(Locale.forLanguageTag("zh-HK")))
        assertTrue(AppLanguages.isTraditionalChinese(Locale.forLanguageTag("zh-MO")))
        assertTrue(AppLanguages.isTraditionalChinese(Locale.forLanguageTag("zh-Hant")))
        assertFalse(AppLanguages.isTraditionalChinese(Locale.forLanguageTag("zh-CN")))
        assertFalse(AppLanguages.isTraditionalChinese(Locale.forLanguageTag("zh-SG")))
        assertFalse("a script beats the region", AppLanguages.isTraditionalChinese(Locale.forLanguageTag("zh-Hans-HK")))
        assertFalse(AppLanguages.isTraditionalChinese(Locale.forLanguageTag("ja-TW")))
    }
}
