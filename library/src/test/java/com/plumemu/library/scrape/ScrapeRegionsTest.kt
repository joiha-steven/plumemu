package com.plumemu.library.scrape

import com.plumemu.identify.name.Region
import org.junit.Assert.assertEquals
import org.junit.Test

/** ScreenScraper region codes for Plumemu's regions, and the order a request asks them in. */
class ScrapeRegionsTest {
    private val table = mapOf(
        Region.USA to listOf("us"),
        Region.EUROPE to listOf("eu"),
        Region.JAPAN to listOf("jp"),
        Region.WORLD to listOf("wor"),
        Region.KOREA to listOf("kr"),
        Region.CHINA to listOf("cn"),
        Region.TAIWAN to listOf("tw", "asi"),
        Region.HONG_KONG to listOf("asi"),
        Region.ASIA to listOf("asi"),
        Region.BRAZIL to listOf("br"),
        Region.AUSTRALIA to listOf("au", "eu"),
        Region.CANADA to listOf("ca", "us"),
        Region.UK to listOf("uk", "eu"),
        Region.FRANCE to listOf("fr", "eu"),
        Region.GERMANY to listOf("de", "eu"),
        Region.SPAIN to listOf("sp", "eu"),
        Region.ITALY to listOf("it", "eu"),
        Region.NETHERLANDS to listOf("nl", "eu"),
        Region.SWEDEN to listOf("se", "eu"),
        Region.SCANDINAVIA to listOf("se", "dk", "no", "fi", "eu"),
        Region.RUSSIA to listOf("ru"),
    )

    @Test
    fun everyRegionHasItsScreenScraperCodes() {
        assertEquals("the table covers every region", Region.entries.toSet(), table.keys)
        for ((region, codes) in table) assertEquals(region.name, codes, ScrapeRegions.codesOf(region))
    }

    /** ScreenScraper's region codes (`regionsListe.php`). */
    private val screenScraperRegions = (
        "ae afr ame asi au bg br ca cl cn cus cz de dk eu fi fr gr hu il it jp kr kw mex mor nl no nz oce pe pl pt ru sa se " +
            "sk sp ss tr tw uk us wor za"
        ).split(' ').toSet()

    @Test
    fun everyCodeARequestCanCarryIsAScreenScraperRegion() {
        val emitted = Region.entries.flatMap(ScrapeRegions::codesOf) + ScrapeRegions.order("", "") + ScrapeRegions.DEFAULT_PREFERRED
        assertEquals(emptySet<String>(), emitted.toSet() - screenScraperRegions)
    }

    @Test
    fun theFilesOwnRegionsComeFirstThenTheChosenOneThenWorldAndScreenScraper() {
        assertEquals(listOf("us", "wor", "ss"), ScrapeRegions.order("USA", "us"))
        assertEquals(listOf("jp", "us", "wor", "ss"), ScrapeRegions.order("JPN", "us"))
        assertEquals(listOf("us", "eu", "jp", "wor", "ss"), ScrapeRegions.order("USA,EUR", "jp"))
        assertEquals(listOf("de", "eu", "us", "wor", "ss"), ScrapeRegions.order("DEU", "us"))
        assertEquals(listOf("wor", "eu", "ss"), ScrapeRegions.order("WLD", "EU"))
    }

    @Test
    fun noRegionAnUnknownCodeOrABlankChoiceFallBackToUsa() {
        assertEquals(listOf("us", "wor", "ss"), ScrapeRegions.order("", ""))
        assertEquals(listOf("eu", "us", "wor", "ss"), ScrapeRegions.order("XYZ, EUR", "  "))
    }

    @Test
    fun theDeviceLanguageThenEnglish() {
        assertEquals(listOf("fr", "en"), ScrapeRegions.languages("FR"))
        assertEquals(listOf("en"), ScrapeRegions.languages("en"))
        assertEquals(listOf("en"), ScrapeRegions.languages(""))
    }
}
